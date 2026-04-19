package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenDeathEvent;
import com.electro.hycitizens.interactions.CitizenInteraction;
import com.electro.hycitizens.models.*;
import com.electro.hycitizens.util.UpdateChecker;
import com.hypixel.hytale.builtin.adventure.npcobjectives.resources.KillTrackerResource;
import com.hypixel.hytale.builtin.adventure.npcobjectives.transaction.KillTaskTransaction;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class EntityDeathListener extends DeathSystems.OnDeathSystem {
    private static final Random RANDOM = new Random();
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("\\{PlayerName}", Pattern.CASE_INSENSITIVE);
    private static final Pattern CITIZEN_NAME_PATTERN = Pattern.compile("\\{CitizenName}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC_X_PATTERN = Pattern.compile("\\{NpcX}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC_Y_PATTERN = Pattern.compile("\\{NpcY}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC_Z_PATTERN = Pattern.compile("\\{NpcZ}", Pattern.CASE_INSENSITIVE);
    private static final UUID NON_PLAYER_CONTEXT_UUID = new UUID(0L, 0L);

    private final HyCitizensPlugin plugin;

    public EntityDeathListener(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType());
    }

    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NPCEntity npcEntityComponent = store.getComponent(ref, NPCEntity.getComponentType());
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());

        if (npcEntityComponent == null || uuidComponent == null) {
            return;
        }

        // Todo: Should create and switch to a citizen component

        CitizenData foundCitizen = plugin.getCitizensManager().getAllCitizens().stream()
                .filter(citizen -> citizen.getSpawnedUUID() != null && citizen.getSpawnedUUID().equals(uuidComponent.getUuid()))
                .findFirst()
                .orElse(null);

        if (foundCitizen == null) {
            return;
        }

        long now = System.currentTimeMillis();

        Damage deathInfo = component.getDeathInfo();
        PlayerRef attackerPlayerRef = null;

        if (deathInfo != null) {
            deathInfo.getSource();

            Ref<EntityStore> sourceRef = null;

            if (deathInfo.getSource() instanceof Damage.ProjectileSource) {
                sourceRef = ((Damage.ProjectileSource) deathInfo.getSource()).getRef();
            }
            else if (deathInfo.getSource() instanceof Damage.EntitySource) {
                sourceRef = ((Damage.EntitySource) deathInfo.getSource()).getRef();
            }

            if (sourceRef != null && sourceRef.isValid()) {
                attackerPlayerRef = sourceRef.getStore().getComponent(sourceRef, PlayerRef.getComponentType());
            }
        }

        // Handle death config (drops, commands, messages)
        DeathConfig dc = foundCitizen.getDeathConfig();
        handleDeathCommands(foundCitizen, dc, attackerPlayerRef);
        handleDeathMessages(foundCitizen, dc, attackerPlayerRef);

        TransformComponent npcTransformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (npcTransformComponent != null) {
            handleDeathDrops(foundCitizen, dc, npcTransformComponent.getPosition());
        }

        foundCitizen.setLastDeathTime(now);

        if (plugin.getCitizensManager().getPatrolManager() != null) {
            plugin.getCitizensManager().getPatrolManager().onCitizenDespawned(foundCitizen.getId());
        }
        plugin.getCitizensManager().despawnCitizenHologram(foundCitizen);
        plugin.getCitizensManager().clearCitizenEntityBinding(foundCitizen);

        // Mark for respawn
        if (foundCitizen.isRespawnOnDeath()) {
            plugin.getCitizensManager().scheduleCitizenRespawn(foundCitizen, (long)(foundCitizen.getRespawnDelaySeconds() * 1000));
        }
    }

    private void handleDeathDrops(@Nonnull CitizenData citizen, @Nonnull DeathConfig dc, Vector3d position) {
        List<DeathDropItem> drops = dc.getDropItems();
        if (drops.isEmpty()) {
            return;
        }

        List<DeathDropItem> eligible = drops.stream()
                .filter(drop -> drop.getItemId() != null && !drop.getItemId().isEmpty())
                .filter(drop -> RANDOM.nextFloat() * 100.0f <= drop.getChancePercent())
                .collect(java.util.stream.Collectors.toList());

        if (eligible.isEmpty()) {
            return;
        }

        int desiredCount = resolveDesiredCount(
                dc.getDropCountMin(),
                dc.getDropCountMax(),
                eligible.size(),
                "ALL"
        );

        List<DeathDropItem> toDrop = new ArrayList<>(eligible);
        if (desiredCount < toDrop.size()) {
            Collections.shuffle(toDrop, RANDOM);
            toDrop = new ArrayList<>(toDrop.subList(0, desiredCount));
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        List<DeathDropItem> finalToDrop = toDrop;
        world.execute(() -> {
            ComponentAccessor<EntityStore> accessor = world.getEntityStore().getStore();
            if (accessor == null) {
                return;
            }

            for (DeathDropItem drop : finalToDrop) {
                if (drop.getItemId().isEmpty()) {
                    continue;
                }

                ItemStack itemStack = new ItemStack(drop.getItemId(), drop.getQuantity());

                Holder<EntityStore>[] entities = ItemComponent.generateItemDrops(
                        accessor, new ArrayList<>(List.of(itemStack)), new Vector3d(position), Vector3f.ZERO);

                accessor.addEntities(entities, AddReason.SPAWN);
            }
        });
    }

    private void handleDeathCommands(@Nonnull CitizenData citizen, @Nonnull DeathConfig dc,
                                     @Nullable PlayerRef attackerPlayerRef) {
        List<CommandAction> commands = dc.getDeathCommands();
        if (commands.isEmpty()) {
            return;
        }

        UUID selectionContextUuid = attackerPlayerRef != null ? attackerPlayerRef.getUuid() : NON_PLAYER_CONTEXT_UUID;
        Ref<EntityStore> attackerRef = attackerPlayerRef != null ? attackerPlayerRef.getReference() : null;
        Player player = null;
        if (attackerRef != null && attackerRef.isValid()) {
            player = attackerRef.getStore().getComponent(attackerRef, Player.getComponentType());
        }
        final Player commandPlayer = player;

        List<CommandAction> eligible = commands.stream()
                .filter(cmd -> RANDOM.nextFloat() * 100.0f <= cmd.getChancePercent())
                .filter(cmd -> attackerPlayerRef != null || !containsPlayerPlaceholder(cmd.getCommand()))
                .collect(java.util.stream.Collectors.toList());
        if (eligible.isEmpty()) {
            return;
        }

        List<CommandAction> toRun = selectCommandsByModeAndCount(
                eligible,
                dc.getCommandSelectionMode(),
                citizen.getSequentialDeathCommandIndex(),
                selectionContextUuid,
                dc.getCommandCountMin(),
                dc.getCommandCountMax()
        );

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
                String command = replacePlaceholders(cmd.getCommand(), attackerPlayerRef, citizen);

                if (command.startsWith("{SendMessage}")) {
                    if (attackerPlayerRef == null) {
                        return CompletableFuture.completedFuture(null);
                    }
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
                        if (commandPlayer == null) {
                            getLogger().atWarning().log("[HyCitizens] Skipping death command as player: attacker entity is unavailable.");
                            return CompletableFuture.completedFuture(null);
                        }
                        return CommandManager.get().handleCommand(commandPlayer, command);
                    }
                }
            });
        }
    }

    private void handleDeathMessages(@Nonnull CitizenData citizen, @Nonnull DeathConfig dc,
                                     @Nullable PlayerRef attackerPlayerRef) {
        if (attackerPlayerRef == null) {
            return;
        }

        List<CitizenMessage> messages = dc.getDeathMessages();
        if (messages.isEmpty()) {
            return;
        }

        List<CitizenMessage> eligible = messages.stream()
                .filter(msg -> RANDOM.nextFloat() * 100.0f <= msg.getChancePercent())
                .collect(java.util.stream.Collectors.toList());
        if (eligible.isEmpty()) {
            return;
        }

        List<CitizenMessage> toSend = selectMessagesByModeAndCount(
                eligible,
                dc.getMessageSelectionMode(),
                citizen.getSequentialDeathMessageIndex(),
                attackerPlayerRef.getUuid(),
                dc.getMessageCountMin(),
                dc.getMessageCountMax()
        );

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (CitizenMessage msg : toSend) {
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

    private void dispatchDeathMessage(@Nonnull CitizenData citizen, @Nonnull PlayerRef playerRef,
                                      @Nonnull CitizenMessage cm) {
        String text = replacePlaceholders(cm.getMessage(), playerRef, citizen);

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

    @Nonnull
    private List<CommandAction> selectCommandsByModeAndCount(@Nonnull List<CommandAction> source,
                                                             @Nonnull String mode,
                                                             @Nonnull Map<UUID, Integer> sequentialMap,
                                                             @Nonnull UUID playerUuid,
                                                             int minCount,
                                                             int maxCount) {
        int desiredCount = resolveDesiredCount(minCount, maxCount, source.size(), mode);
        if (desiredCount <= 0) {
            return List.of();
        }

        String normalized = mode.toUpperCase(Locale.ROOT);
        if ("RANDOM".equals(normalized)) {
            List<CommandAction> shuffled = new ArrayList<>(source);
            Collections.shuffle(shuffled, RANDOM);
            return new ArrayList<>(shuffled.subList(0, Math.min(desiredCount, shuffled.size())));
        }

        if ("SEQUENTIAL".equals(normalized)) {
            List<CommandAction> selected = new ArrayList<>();
            int start = sequentialMap.getOrDefault(playerUuid, 0);
            for (int i = 0; i < desiredCount; i++) {
                selected.add(source.get((start + i) % source.size()));
            }
            sequentialMap.put(playerUuid, start + desiredCount);
            return selected;
        }

        return new ArrayList<>(source.subList(0, Math.min(desiredCount, source.size())));
    }

    @Nonnull
    private List<CitizenMessage> selectMessagesByModeAndCount(@Nonnull List<CitizenMessage> source,
                                                              @Nonnull String mode,
                                                              @Nonnull Map<UUID, Integer> sequentialMap,
                                                              @Nonnull UUID playerUuid,
                                                              int minCount,
                                                              int maxCount) {
        int desiredCount = resolveDesiredCount(minCount, maxCount, source.size(), mode);
        if (desiredCount <= 0) {
            return List.of();
        }

        String normalized = mode.toUpperCase(Locale.ROOT);
        if ("RANDOM".equals(normalized)) {
            List<CitizenMessage> shuffled = new ArrayList<>(source);
            Collections.shuffle(shuffled, RANDOM);
            return new ArrayList<>(shuffled.subList(0, Math.min(desiredCount, shuffled.size())));
        }

        if ("SEQUENTIAL".equals(normalized)) {
            List<CitizenMessage> selected = new ArrayList<>();
            int start = sequentialMap.getOrDefault(playerUuid, 0);
            for (int i = 0; i < desiredCount; i++) {
                selected.add(source.get((start + i) % source.size()));
            }
            sequentialMap.put(playerUuid, start + desiredCount);
            return selected;
        }

        return new ArrayList<>(source.subList(0, Math.min(desiredCount, source.size())));
    }

    private int resolveDesiredCount(int minCount, int maxCount, int available, @Nonnull String mode) {
        if (available <= 0) {
            return 0;
        }

        int min = Math.max(0, minCount);
        int max = Math.max(0, maxCount);

        if (min == 0 && max == 0) {
            if ("ALL".equalsIgnoreCase(mode)) {
                return available;
            }
            return 1;
        }

        min = Math.max(1, min);
        max = Math.max(1, max);
        if (max < min) {
            int tmp = max;
            max = min;
            min = tmp;
        }

        int clampedMin = Math.min(min, available);
        int clampedMax = Math.min(max, available);
        if (clampedMax < clampedMin) {
            clampedMax = clampedMin;
        }

        if (clampedMin == clampedMax) {
            return clampedMin;
        }
        return clampedMin + RANDOM.nextInt(clampedMax - clampedMin + 1);
    }

    @Nonnull
    private String replacePlaceholders(@Nonnull String text, @Nullable PlayerRef playerRef, @Nonnull CitizenData citizen) {
        Vector3d npcPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        String npcX = String.format(Locale.ROOT, "%.2f", npcPos.x);
        String npcY = String.format(Locale.ROOT, "%.2f", npcPos.y);
        String npcZ = String.format(Locale.ROOT, "%.2f", npcPos.z);

        if (playerRef != null) {
            text = PLAYER_NAME_PATTERN
                    .matcher(text)
                    .replaceAll(Matcher.quoteReplacement(playerRef.getUsername()));
        }
        text = CITIZEN_NAME_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(formatCitizenNamePlaceholder(citizen)));
        text = NPC_X_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(npcX));
        text = NPC_Y_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(npcY));
        text = NPC_Z_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(npcZ));
        return text;
    }

    private boolean containsPlayerPlaceholder(@Nonnull String text) {
        return PLAYER_NAME_PATTERN.matcher(text).find();
    }

    @Nonnull
    private static String formatCitizenNamePlaceholder(@Nonnull CitizenData citizen) {
        return citizen.getName()
                .replace("\\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ');
    }
}
