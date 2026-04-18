package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenDeathEvent;
import com.electro.hycitizens.interactions.CitizenInteraction;
import com.electro.hycitizens.models.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.awt.Color;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class EntityDamageListener extends DamageEventSystem {
    private final HyCitizensPlugin plugin;

    public EntityDamageListener(HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(int i, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Damage event) {
        try {
            Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(i);
            UUIDComponent uuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                return;
            }

            List<CitizenData> citizens = HyCitizensPlugin.get().getCitizensManager().getAllCitizens();
            Damage.Source source = event.getSource();
            Ref<EntityStore> attackerEntityRef = getAttackerEntityRef(source);
            UUID attackerUuid = getEntityUuid(attackerEntityRef);

            // No longer needed
//            PlayerRef attackerPlayerRef = null;
//            if (attackerEntityRef != null && attackerEntityRef.isValid()) {
//                attackerPlayerRef = attackerEntityRef.getStore().getComponent(attackerEntityRef, PlayerRef.getComponentType());
//            }

            CitizenData attackerCitizen = findCitizenByEntity(citizens, attackerEntityRef, attackerUuid);
            if (attackerCitizen != null && attackerCitizen.isOverrideDamage() && attackerCitizen.getDamageAmount() >= 0) {
                event.setAmount(attackerCitizen.getDamageAmount());
            }

            CitizenData targetCitizen = findCitizenByEntity(citizens, targetRef, uuidComponent.getUuid());
            if (targetCitizen == null) {
                return;
            }

            // Passive citizens always cancel damage - they never enter combat
            boolean cancelDamage = !targetCitizen.isTakesDamage() || "PASSIVE".equals(targetCitizen.getAttitude());

            // Trigger ON_ATTACK animations regardless of damage setting
            HyCitizensPlugin.get().getCitizensManager().triggerAnimations(targetCitizen, "ON_ATTACK");

//            CitizenInteraction.handleInteraction(targetCitizen, attackerPlayerRef); // Handled by new interaction system

            if (cancelDamage) {
                // This is now handled by the Invulnerable component, but we are keeping it for backwards compatibility
                Invulnerable invulnerable = store.getComponent(targetRef, Invulnerable.getComponentType());

                if (invulnerable == null) {
                    event.setCancelled(true);
                    event.setAmount(0);
                    World world = Universe.get().getWorld(targetCitizen.getWorldUUID());
                    // Todo: This does not work
//                if (world != null) {
//                    // Prevent knockback
//                    world.execute(() -> {
//                        store.removeComponentIfExists(targetRef, KnockbackComponent.getComponentType());
//                    });
//                }
                    // Temporary solution to knockback
                    TransformComponent transformComponent = store.getComponent(targetRef, TransformComponent.getComponentType());
                    if (transformComponent != null && world != null) {
                        Vector3d lockedPosition = new Vector3d(transformComponent.getPosition());

                        ScheduledFuture<?> lockTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                            if (!targetRef.isValid()) {
                                return;
                            }

                            Vector3d currentPosition = transformComponent.getPosition();
                            if (!currentPosition.equals(lockedPosition)) {
                                transformComponent.setPosition(lockedPosition);
                            }
                        }, 0, 20, TimeUnit.MILLISECONDS);

                        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                            lockTask.cancel(false);
                        }, 2000, TimeUnit.MILLISECONDS);
                    }
                }
                return;
            }

            targetCitizen.setLastDamageTakenAt(System.currentTimeMillis());

            // Death is now handled in EntityDeathListener. Although now the event can no longer be canceled by HyCitizens API

            // Check if the citizen will die from this damage
//            EntityStatMap statMap = store.getComponent(targetRef, EntityStatsModule.get().getEntityStatMapComponentType());
//            if (statMap == null) {
//                return;
//            }
//
//            EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
//            if (healthValue == null) {
//                return;
//            }
//
//            float currentHealth = healthValue.get();
//            float damageAmount = event.getAmount();
//
//            if (currentHealth - damageAmount <= 0 && !targetCitizen.isAwaitingRespawn()) {
//                long now = System.currentTimeMillis();
//
//                // Fire death event
//                CitizenDeathEvent deathEvent = new CitizenDeathEvent(targetCitizen, attackerPlayerRef);
//                plugin.getCitizensManager().fireCitizenDeathEvent(deathEvent);
//
//                if (deathEvent.isCancelled()) {
//                    event.setCancelled(true);
//                    event.setAmount(0);
//                    return;
//                }
//
//                // Handle death config (drops, commands, messages)
//                DeathConfig dc = targetCitizen.getDeathConfig();
//                handleDeathCommands(targetCitizen, dc, attackerPlayerRef);
//                handleDeathMessages(targetCitizen, dc, attackerPlayerRef);
//
//                TransformComponent npcTransformComponent = store.getComponent(targetRef, TransformComponent.getComponentType());
//                if (npcTransformComponent != null) {
//                    handleDeathDrops(targetCitizen, dc, npcTransformComponent.getPosition());
//                }
//
//                targetCitizen.setLastDeathTime(now);
//
//                if (plugin.getCitizensManager().getPatrolManager() != null) {
//                    plugin.getCitizensManager().getPatrolManager().onCitizenDespawned(targetCitizen.getId());
//                }
//                plugin.getCitizensManager().despawnCitizenHologram(targetCitizen);
//                plugin.getCitizensManager().clearCitizenEntityBinding(targetCitizen);
//
//                // Mark for respawn
//                if (targetCitizen.isRespawnOnDeath()) {
//                    targetCitizen.setAwaitingRespawn(true);
//
//                    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
//                        World world = Universe.get().getWorld(targetCitizen.getWorldUUID());
//                        if (world == null)
//                            return;
//
//                        targetCitizen.setAwaitingRespawn(false);
//                        world.execute(() -> {
//                            plugin.getCitizensManager().spawnCitizen(targetCitizen, true);
//                        });
//                    }, (long)(targetCitizen.getRespawnDelaySeconds() * 1000), TimeUnit.MILLISECONDS);
//                }
//            }

        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] Error in damage handler: " + e.getMessage());
        }
    }

    @Nullable
    private Ref<EntityStore> getAttackerEntityRef(@Nonnull Damage.Source source) {
        if (source instanceof Damage.ProjectileSource) {
            return ((Damage.ProjectileSource) source).getRef();
        }

        if (source instanceof Damage.EntitySource) {
            return ((Damage.EntitySource) source).getRef();
        }

        return null;
    }

    @Nullable
    private UUID getEntityUuid(@Nullable Ref<EntityStore> entityRef) {
        if (entityRef == null || !entityRef.isValid()) {
            return null;
        }

        UUIDComponent uuidComponent = entityRef.getStore().getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return null;
        }
        return uuidComponent.getUuid();
    }

    @Nullable
    private CitizenData findCitizenByEntity(@Nonnull List<CitizenData> citizens,
                                            @Nullable Ref<EntityStore> entityRef,
                                            @Nullable UUID entityUuid) {
        for (CitizenData citizen : citizens) {
            if (entityRef != null && entityRef.isValid()
                    && citizen.getNpcRef() != null
                    && citizen.getNpcRef().isValid()
                    && citizen.getNpcRef().equals(entityRef)) {
                return citizen;
            }

            if (entityUuid != null && entityUuid.equals(citizen.getSpawnedUUID())) {
                return citizen;
            }
        }

        return null;
    }

    @Nullable
    public Query<EntityStore> getQuery() {
        return Query.and(new Query[]{UUIDComponent.getComponentType()});
    }

    @Nullable
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }
}
