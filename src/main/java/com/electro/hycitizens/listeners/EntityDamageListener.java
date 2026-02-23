package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenDeathEvent;
import com.electro.hycitizens.events.CitizenInteractEvent;
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
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(i);
        UUIDComponent uuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());

        assert uuidComponent != null;
        NPCEntity npcEntity = store.getComponent(targetRef, NPCEntity.getComponentType());
        Damage.Source source = event.getSource();

        // Check if citizen is damaging a player
        if (npcEntity == null)
        {
            PlayerRef playerRef = store.getComponent(targetRef, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }

            NPCEntity attackerNpc = null;
            Ref<EntityStore> attackerEntityRef = null;

            if (source instanceof Damage.ProjectileSource) {
                Damage.ProjectileSource projectileSource = (Damage.ProjectileSource) source;
                attackerEntityRef = projectileSource.getRef();

                if (attackerEntityRef != null) {
                    attackerNpc = store.getComponent(attackerEntityRef, NPCEntity.getComponentType());
                }
            }
            else if (source instanceof Damage.EntitySource) {
                Damage.EntitySource entitySource = (Damage.EntitySource) source;
                attackerEntityRef = entitySource.getRef();
                attackerNpc = store.getComponent(attackerEntityRef, NPCEntity.getComponentType());
            }

            if (attackerNpc == null) {
                return;
            }

            // Check which citizen is attacking the player
            List<CitizenData> citizens = HyCitizensPlugin.get().getCitizensManager().getAllCitizens();
            for (CitizenData citizen : citizens) {
                if (citizen.getNpcRef() == null) {
                    continue;
                }

                if (citizen.getNpcRef().getIndex() != attackerEntityRef.getIndex()) {
                    continue;
                }

                if (citizen.isOverrideDamage() && citizen.getDamageAmount() >= 0) {
                    event.setAmount(citizen.getDamageAmount());
                }

                return;
            }

            return;
        }

        // Something is damaging citizen
        PlayerRef attackerPlayerRef;

        if (source instanceof Damage.ProjectileSource) {
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
            if (citizen.getSpawnedUUID() == null) {
                continue;
            }

            if (!citizen.getSpawnedUUID().equals(uuidComponent.getUuid())) {
                continue;
            }

            // Passive citizens always cancel damage - they never enter combat
            boolean cancelDamage = !citizen.isTakesDamage() || "PASSIVE".equals(citizen.getAttitude());

            // Trigger ON_ATTACK animations regardless of damage setting
            HyCitizensPlugin.get().getCitizensManager().triggerAnimations(citizen, "ON_ATTACK");

//            CitizenInteraction.handleInteraction(citizen, attackerPlayerRef); // Handled by new interaction system

            if (cancelDamage) {
                // This is now handled by the Invulnerable component, but we are keeping it for backwards compatibility
                Invulnerable invulnerable = store.getComponent(targetRef, Invulnerable.getComponentType());

                if (invulnerable == null) {
                    event.setCancelled(true);
                    event.setAmount(0);
                    World world = Universe.get().getWorld(citizen.getWorldUUID());
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
            }
            else {
                // Check if the citizen will die from this damage
                EntityStatMap statMap = store.getComponent(targetRef, EntityStatsModule.get().getEntityStatMapComponentType());
                if (statMap == null) {
                    return;
                }

                float currentHealth = statMap.get(DefaultEntityStatTypes.getHealth()).get();
                float damageAmount = event.getAmount();

                if (currentHealth - damageAmount <= 0) {
                    long now = System.currentTimeMillis();

                    if (!citizen.isAwaitingRespawn()) {
                        // Fire death event
                        CitizenDeathEvent deathEvent = new CitizenDeathEvent(citizen, attackerPlayerRef);
                        plugin.getCitizensManager().fireCitizenDeathEvent(deathEvent);

                        if (deathEvent.isCancelled()) {
                            event.setCancelled(true);
                            event.setAmount(0);
                            return;
                        }

                        // Handle death config (drops, commands, messages)
                        DeathConfig dc = citizen.getDeathConfig();
                        handleDeathDrops(citizen, dc);
                        handleDeathCommands(citizen, dc, attackerPlayerRef);
                        handleDeathMessages(citizen, dc, attackerPlayerRef);

                        citizen.setLastDeathTime(now);

                        // Despawn nametag
                        plugin.getCitizensManager().despawnCitizenHologram(citizen);

                        citizen.setSpawnedUUID(null);
                        citizen.setNpcRef(null);

                        // Mark for respawn
                        if (citizen.isRespawnOnDeath()) {
                            citizen.setAwaitingRespawn(true);

                            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                                World world = Universe.get().getWorld(citizen.getWorldUUID());
                                if (world == null)
                                    return;

                                citizen.setAwaitingRespawn(false);
                                world.execute(() -> {
                                    plugin.getCitizensManager().spawnCitizen(citizen, true);
                                });
                            }, (long)(citizen.getRespawnDelaySeconds() * 1000), TimeUnit.MILLISECONDS);
                        }
                    }
                }
            }

            break;
        }
    }

    private static final Random RANDOM = new Random();

    private void handleDeathDrops(@Nonnull CitizenData citizen, @Nonnull DeathConfig dc) {
        List<DeathDropItem> drops = dc.getDropItems();
        if (drops.isEmpty()) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        world.execute(() -> {
            ComponentAccessor<EntityStore> accessor = world.getEntityStore().getStore();
            if (accessor == null) return;

            for (DeathDropItem drop : drops) {
                if (drop.getItemId().isEmpty()) continue;
                ItemStack itemStack = new ItemStack(drop.getItemId(), drop.getQuantity());
                Holder<EntityStore>[] entities = ItemComponent.generateItemDrops(
                        accessor, new ArrayList<>(List.of(itemStack)), citizen.getPosition(), Vector3f.ZERO);
                accessor.addEntities(entities, AddReason.SPAWN);
            }
        });
    }

    private void handleDeathCommands(@Nonnull CitizenData citizen, @Nonnull DeathConfig dc,
                                     @Nonnull PlayerRef attackerPlayerRef) {
        List<CommandAction> commands = dc.getDeathCommands();
        if (commands.isEmpty()) {
            return;
        }

        // Todo: Make it possible for commands to run even if a player doesn't kill the citizen

        Player player = attackerPlayerRef.getReference().getStore().getComponent(attackerPlayerRef.getReference(), Player.getComponentType());

        List<CommandAction> toRun;
        if ("RANDOM".equals(dc.getCommandSelectionMode())) {
            toRun = List.of(commands.get(RANDOM.nextInt(commands.size())));
        } else {
            toRun = commands;
        }

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (CommandAction cmd : toRun) {
            chain = chain.thenCompose(v -> {
                if (cmd.getDelaySeconds() > 0) {
                    CompletableFuture<Void> delayed = new CompletableFuture<>();
                    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> delayed.complete(null),
                            (long) (cmd.getDelaySeconds() * 1000), TimeUnit.MILLISECONDS);
                    return delayed;
                }
                return CompletableFuture.completedFuture(null);
            }).thenCompose(v -> {
                String command = cmd.getCommand();
                command = Pattern.compile("\\{PlayerName}", Pattern.CASE_INSENSITIVE)
                        .matcher(command).replaceAll(attackerPlayerRef.getUsername());
                command = Pattern.compile("\\{CitizenName}", Pattern.CASE_INSENSITIVE)
                        .matcher(command).replaceAll(citizen.getName());

                if (command.startsWith("{SendMessage}")) {
                    String messageContent = command.substring("{SendMessage}".length()).trim();
                    Message msg = CitizenInteraction.parseColoredMessage(messageContent);
                    if (msg != null) {
                        attackerPlayerRef.sendMessage(msg);
                    }
                    return CompletableFuture.completedFuture(null);
                } else {
                    if (cmd.isRunAsServer()) {
                        return CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command);
                    } else {
                        return CommandManager.get().handleCommand(player, command);
                    }
                }
            });
        }
    }

    private void handleDeathMessages(@Nonnull CitizenData citizen, @Nonnull DeathConfig dc,
                                     @Nonnull PlayerRef attackerPlayerRef) {
        List<CitizenMessage> messages = dc.getDeathMessages();
        if (messages.isEmpty()) {
            return;
        }

        if ("RANDOM".equals(dc.getMessageSelectionMode())) {
            CitizenMessage selected = messages.get(RANDOM.nextInt(messages.size()));
            dispatchDeathMessage(citizen, attackerPlayerRef, selected);
        } else {
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (CitizenMessage msg : messages) {
                if (msg.getDelaySeconds() > 0) {
                    chain = chain.thenCompose(v -> {
                        CompletableFuture<Void> delayed = new CompletableFuture<>();
                        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> delayed.complete(null),
                                (long) (msg.getDelaySeconds() * 1000), TimeUnit.MILLISECONDS);
                        return delayed;
                    });
                }
                chain = chain.thenCompose(v -> {
                    dispatchDeathMessage(citizen, attackerPlayerRef, msg);
                    return CompletableFuture.completedFuture(null);
                });
            }
        }
    }

    private void dispatchDeathMessage(@Nonnull CitizenData citizen, @Nonnull PlayerRef playerRef,
                                      @Nonnull CitizenMessage cm) {
        String text = cm.getMessage();
        text = Pattern.compile("\\{PlayerName}", Pattern.CASE_INSENSITIVE)
                .matcher(text).replaceAll(playerRef.getUsername());
        text = Pattern.compile("\\{CitizenName}", Pattern.CASE_INSENSITIVE)
                .matcher(text).replaceAll(citizen.getName());

        Message parsed = CitizenInteraction.parseColoredMessage(text);
        if (parsed == null) {
            return;
        }

        if (cm.getDelaySeconds() > 0) {
            final Message finalMsg = parsed;
            HytaleServer.SCHEDULED_EXECUTOR.schedule(
                    () -> playerRef.sendMessage(finalMsg),
                    (long) (cm.getDelaySeconds() * 1000), TimeUnit.MILLISECONDS);
        } else {
            playerRef.sendMessage(parsed);
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
