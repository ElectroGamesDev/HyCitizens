package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenInteractEvent;
import com.electro.hycitizens.interactions.CitizenInteraction;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.CommandAction;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.awt.Color;
import java.util.List;
import java.util.Map;
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
        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(i);
        UUIDComponent uuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());

        assert uuidComponent != null;
        NPCEntity npcEntity = store.getComponent(targetRef, NPCEntity.getComponentType());

        if (npcEntity == null)
            return;

        Damage.Source source = event.getSource();
        PlayerRef attackerPlayerRef;

        if (source instanceof Damage.ProjectileSource) { // This doesn't work for arrows. Using a workaround
            Damage.ProjectileSource projectileSource = (Damage.ProjectileSource) source;
            Ref<EntityStore> shooterRef = projectileSource.getRef();
            if (shooterRef != null) {
                attackerPlayerRef = store.getComponent(shooterRef, PlayerRef.getComponentType());
            } else {
                attackerPlayerRef = null;
            }
        }
        else if (source instanceof Damage.EntitySource) {
            Damage.EntitySource entitySource = (Damage.EntitySource) source;
            Ref<EntityStore> attackerRef = entitySource.getRef();
            attackerPlayerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
        } else {
            attackerPlayerRef = null;
        }

        if (attackerPlayerRef == null)
            return;

        // Todo: It would be best to give the citizens a custom component. There may be compatibility issues if citizens already exist though
        List<CitizenData> citizens = HyCitizensPlugin.get().getCitizensManager().getAllCitizens();
        for (CitizenData citizen : citizens) {
            if (citizen.getSpawnedUUID() == null)
                continue;

            if (!citizen.getSpawnedUUID().equals(uuidComponent.getUuid()))
                continue;

            event.setCancelled(true);
            event.setAmount(0);
            World world = Universe.get().getWorld(citizen.getWorldUUID());

            // Prevent knockback. This isn't a good solution, but I couldn't find a better way to handle this
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
                }, 1500, TimeUnit.MILLISECONDS);
            }

            CitizenInteraction.handleInteraction(citizen, attackerPlayerRef);

            break;
        }
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
