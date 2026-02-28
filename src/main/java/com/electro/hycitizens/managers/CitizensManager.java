package com.electro.hycitizens.managers;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenDeathEvent;
import com.electro.hycitizens.events.CitizenDeathListener;
import com.electro.hycitizens.events.CitizenInteractEvent;
import com.electro.hycitizens.events.CitizenInteractListener;
import com.electro.hycitizens.models.*;
import com.electro.hycitizens.roles.RoleGenerator;
import com.electro.hycitizens.util.ConfigManager;
import com.electro.hycitizens.util.SkinUtilities;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class CitizensManager {
    private static final int MAX_PENDING_HOLOGRAM_REMOVAL_ATTEMPTS = 20;
    private static final int MAX_PENDING_NPC_REMOVAL_ATTEMPTS = 24;
    private static final float NAMETAG_OFFSET_EPSILON = 0.0001f;

    private static final class PendingHologramRemoval {
        private final long chunkIndex;
        private int attempts;

        private PendingHologramRemoval(long chunkIndex) {
            this.chunkIndex = chunkIndex;
            this.attempts = 0;
        }
    }

    private static final class PendingNpcRemoval {
        private final long chunkIndex;
        private int attempts;

        private PendingNpcRemoval(long chunkIndex) {
            this.chunkIndex = chunkIndex;
            this.attempts = 0;
        }
    }

    private final HyCitizensPlugin plugin;
    private final ConfigManager config;
    private final Map<String, CitizenData> citizens;
    private final List<CitizenInteractListener> interactListeners = new ArrayList<>();
    private final List<CitizenDeathListener> deathListeners = new ArrayList<>();
    private ScheduledFuture<?> skinUpdateTask;
    private ScheduledFuture<?> rotateTask;
    private ScheduledFuture<?> nametagMoveTask;
    private ScheduledFuture<?> animationTask;
    private final Map<UUID, List<CitizenData>> citizensByWorld = new HashMap<>();
    private final Set<String> groups = new HashSet<>();
    private final Set<String> registeredNoLoopAnimations = ConcurrentHashMap.newKeySet();
    private final RoleGenerator roleGenerator;
    private ScheduledFuture<?> positionSaveTask;
    private final Set<String> citizensCurrentlySpawning = ConcurrentHashMap.newKeySet();
    private final Set<String> hologramsCurrentlySpawning = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Map<UUID, PendingHologramRemoval>> pendingHologramRemovals = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, PendingNpcRemoval>> pendingNpcRemovals = new ConcurrentHashMap<>();
    private final Set<String> pendingNpcRemovalTasks = ConcurrentHashMap.newKeySet();
    private PatrolManager patrolManager;

    public CitizensManager(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.citizens = new ConcurrentHashMap<>();
        this.roleGenerator = new RoleGenerator(plugin.getGeneratedRolesPath());

        loadAllCitizens();
        startSkinUpdateScheduler();
        startRotateScheduler();
        startCitizensByWorldScheduler();
        startAnimationScheduler();
        startNametagMoveScheduler();
        startPositionSaveScheduler();
        this.patrolManager = new PatrolManager(plugin.getConfigManager(), this);
    }

    private void startSkinUpdateScheduler() {
        skinUpdateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            long thirtyMinutes = 30 * 60 * 1000;

            for (CitizenData citizen : citizens.values()) {
                if (citizen.isPlayerModel() && citizen.isUseLiveSkin() && !citizen.getSkinUsername().isEmpty()) {
                    long timeSinceLastUpdate = currentTime - citizen.getLastSkinUpdate();

                    if (citizen.getSkinUsername().startsWith("random_") || citizen.getSkinUsername().startsWith("custom_")) {
                        continue;
                    }

                    if (timeSinceLastUpdate >= thirtyMinutes) {
                        updateCitizenSkin(citizen, true);
                    }
                }
            }
        }, 30, 30, TimeUnit.MINUTES);
    }

    private void startRotateScheduler() {
        rotateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            // Group citizens by world
            Map<UUID, List<CitizenData>> snapshot;

            synchronized (citizensByWorld) {
                snapshot = new HashMap<>(citizensByWorld);
            }

            // Process each world once
            for (Map.Entry<UUID, List<CitizenData>> entry : snapshot.entrySet()) {
                UUID worldUUID = entry.getKey();
                List<CitizenData> worldCitizens = entry.getValue();

                World world = Universe.get().getWorld(worldUUID);
                if (world == null)
                    continue;

                Collection<PlayerRef> players = world.getPlayerRefs();
                if (players.isEmpty()) {
                    continue;
                }

                // Execute all rotation logic for this world
                world.execute(() -> {
                    for (CitizenData citizen : worldCitizens) {
                        if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid())
                            continue;

                        Vector3d citizenPos = citizen.getPosition();

                        long chunkIndex = ChunkUtil.indexChunkFromBlock(citizenPos.x, citizenPos.z);
                        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                        if (chunk == null)
                            continue;

                        for (PlayerRef playerRef : players) {
                            float maxDistance = 25.0f;
                            float maxDistanceSq = maxDistance * maxDistance;

                            double dx = playerRef.getTransform().getPosition().x - citizenPos.x;
                            double dz = playerRef.getTransform().getPosition().z - citizenPos.z;

                            double distSq = dx * dx + dz * dz;

                            if (distSq > maxDistanceSq) {
                                continue;
                            }

                            // Check proximity animations
                            if (!citizen.getAnimationBehaviors().isEmpty()) {
                                checkProximityAnimations(citizen, playerRef, distSq);
                            }

                            if (!citizen.getRotateTowardsPlayer())
                                continue;

                            // Only look at player if idle
                            if (!citizen.getMovementBehavior().getType().equals("IDLE"))
                                continue;

                            rotateCitizenToPlayer(citizen, playerRef);
                        }
                    }
                });
            }
        }, 0, 60, TimeUnit.MILLISECONDS);
    }

    private void startNametagMoveScheduler() {
        nametagMoveTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            // Group citizens by world
            Map<UUID, List<CitizenData>> snapshot;

            synchronized (citizensByWorld) {
                snapshot = new HashMap<>(citizensByWorld);
            }

            // Process each world once
            for (Map.Entry<UUID, List<CitizenData>> entry : snapshot.entrySet()) {
                UUID worldUUID = entry.getKey();
                List<CitizenData> worldCitizens = entry.getValue();

                World world = Universe.get().getWorld(worldUUID);
                if (world == null)
                    continue;

                Collection<PlayerRef> players = world.getPlayerRefs();
                if (players.isEmpty()) {
                    continue;
                }

                // Execute all movement logic for this world
                world.execute(() -> {
                    for (CitizenData citizen : worldCitizens) {
                        if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
                            continue;
                        }

                        if (citizen.getMovementBehavior().getType().equals("IDLE")) {
                            continue;
                        }

                        TransformComponent npcTransformComponent = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), TransformComponent.getComponentType());
                        if (npcTransformComponent == null) {
                            continue;
                        }

                        Vector3d npcPosition = npcTransformComponent.getPosition();

                        long chunkIndex = ChunkUtil.indexChunkFromBlock(npcPosition.x, npcPosition.z);
                        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                        if (chunk == null)
                            continue;

                        // Track the NPC's actual position
                        citizen.setCurrentPosition(new Vector3d(npcPosition));

                        // Todo: It would be better to store the nametags as a ref

                        int totalLines = citizen.getHologramLineUuids().size();
                        if (totalLines == 0) {
                            continue;
                        }

                        // Calculate the same offsets as in spawn
                        double scale = Math.max(0.01, citizen.getScale() + citizen.getNametagOffset());
                        double baseOffset = 1.65;
                        double extraPerScale = 0.40;
                        double yOffset = baseOffset * scale + (scale - 1.0) * extraPerScale;
                        double lineSpacing = 0.25;

                        // Update each hologram line
                        for (int i = 0; i < totalLines; i++) {
                            UUID uuid = citizen.getHologramLineUuids().get(i);
                            if (uuid == null) {
                                continue;
                            }

                            Ref<EntityStore> entityRef = world.getEntityRef(uuid);
                            if (entityRef == null || !entityRef.isValid()) {
                                continue;
                            }

                            TransformComponent nametagTransformComponent = entityRef.getStore().getComponent(entityRef, TransformComponent.getComponentType());
                            if (nametagTransformComponent == null) {
                                continue;
                            }

                            // Position this line
                            Vector3d linePos = new Vector3d(
                                    npcPosition.x,
                                    npcPosition.y + yOffset + ((totalLines - 1 - i) * lineSpacing),
                                    npcPosition.z
                            );

                            nametagTransformComponent.setPosition(linePos);
                        }
                    }
                });
            }
        }, 0, 15, TimeUnit.MILLISECONDS);
    }

    private void startCitizensByWorldScheduler() {
        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            Map<UUID, List<CitizenData>> tmp = new HashMap<>();

            for (CitizenData citizen : citizens.values()) {
                if (citizen.getSpawnedUUID() == null) {
                    continue;
                }

                if (citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
                    continue;
                }

                UUID worldUUID = citizen.getWorldUUID();
                tmp.computeIfAbsent(worldUUID, k -> new ArrayList<>()).add(citizen);
            }

            synchronized (citizensByWorld) {
                citizensByWorld.clear();
                citizensByWorld.putAll(tmp);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    // Todo: move position saving to chunk unload event when it becomes available
    private void startPositionSaveScheduler() {
        positionSaveTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            config.beginBatch();
            try {
                for (CitizenData citizen : citizens.values()) {
                    if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid())
                        continue;

                    if (citizen.getMovementBehavior().getType().equals("IDLE"))
                        continue;

                    String basePath = "citizens." + citizen.getId();
                    config.setVector3d(basePath + ".current-position", citizen.getCurrentPosition());
                }
            } finally {
                config.endBatch();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (skinUpdateTask != null && !skinUpdateTask.isCancelled()) {
            skinUpdateTask.cancel(false);
        }

        if (rotateTask != null && !rotateTask.isCancelled()) {
            rotateTask.cancel(false);
        }

        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel(false);
        }

        if (nametagMoveTask != null && !nametagMoveTask.isCancelled()) {
            nametagMoveTask.cancel(false);
        }

        if (positionSaveTask != null && !positionSaveTask.isCancelled()) {
            positionSaveTask.cancel(false);
        }

        if (patrolManager != null) {
            patrolManager.shutdown();
        }

        for (CitizenData citizen : citizens.values()) {
            saveCitizen(citizen);
        }

        pendingHologramRemovals.clear();
        pendingNpcRemovals.clear();
        pendingNpcRemovalTasks.clear();
    }

    private void loadAllCitizens() {
        citizens.clear();
        groups.clear();

        // Load groups
        List<String> groupList = config.getStringList("groups");
        if (groupList != null) {
            groups.addAll(groupList);
        }

        // Get all citizen IDs from the nested "citizens" map
        Set<String> citizenIds = config.getKeys("citizens");

        for (String citizenId : citizenIds) {
            CitizenData citizen = loadCitizen(citizenId);
            if (citizen != null) {
                citizens.put(citizenId, citizen);

                if (!citizen.getGroup().isEmpty()) {
                    groups.add(citizen.getGroup());
                }
            }
        }

        // Save groups list in case new groups were discovered from citizens
        saveGroups();
    }

    @Nullable
    private CitizenData loadCitizen(@Nonnull String citizenId) {
        String basePath = "citizens." + citizenId;

        String name = config.getString(basePath + ".name");
        if (name == null) {
            getLogger().atWarning().log("Failed to load a citizen with the ID: " + citizenId);
            return null;
        }

        String modelId = config.getString(basePath + ".model-id");
        if (modelId == null) {
            getLogger().atWarning().log("Failed to load citizen: " + name + ". Failed to get model ID.");
            return null;
        }

        UUID worldUUID = config.getUUID(basePath + ".model-world-uuid");
        if (worldUUID == null) {
            getLogger().atWarning().log("Failed to load citizen: " + name + ". Failed to get world UUID.");
            return null;
        }

        Vector3d position = config.getVector3d(basePath + ".position");
        Vector3f rotation = config.getVector3f(basePath + ".rotation");

        if (position == null || rotation == null) {
            getLogger().atWarning().log("Failed to load citizen: " + name + ". Failed to get position or rotation.");
            return null;
        }

        float scale = config.getFloat(basePath + ".scale", 1);

        String permission = config.getString(basePath + ".permission", "");
        String permMessage = config.getString(basePath + ".permission-message", "");

        // Load command actions
        List<CommandAction> actions = new ArrayList<>();
        int commandCount = config.getInt(basePath + ".commands.count", 0);

        for (int i = 0; i < commandCount; i++) {
            String commandPath = basePath + ".commands." + i;
            String command = config.getString(commandPath + ".command");
            boolean runAsServer = config.getBoolean(commandPath + ".run-as-server", false);
            float delay = config.getFloat(commandPath + ".delay", 0.0f);
            // null = not yet set, will be migrated below
            String trigger = config.getString(commandPath + ".interaction-trigger", null);

            if (command != null) {
                actions.add(new CommandAction(command, runAsServer, delay, trigger));
            }
        }

        UUID npcUUID = config.getUUID(basePath + ".npc-uuid");

        // Load hologram
        List<UUID> hologramUuids = config.getUUIDList(basePath + ".hologram-uuids");
        if (hologramUuids == null) {
            hologramUuids = new ArrayList<>();
        }

        if (hologramUuids.isEmpty()) {
            // Backwards compatibility
            UUID hologramUUID = config.getUUID(basePath + ".hologram-uuid");
            if (hologramUUID != null) {
                hologramUuids.add(hologramUUID);
                getLogger().atInfo().log("Loaded Hologram UUID: " + hologramUUID);
            }
        }

        boolean rotateTowardsPlayer = config.getBoolean(basePath + ".rotate-towards-player", false);

        // Load skin data
        boolean isPlayerModel = config.getBoolean(basePath + ".is-player-model", false);
        boolean useLiveSkin = config.getBoolean(basePath + ".use-live-skin", false);
        String skinUsername = config.getString(basePath + ".skin-username", "");
        PlayerSkin cachedSkin = config.getPlayerSkin(basePath + ".cached-skin");
        long lastSkinUpdate = config.getLong(basePath + ".last-skin-update", 0L);

        CitizenData citizenData = new CitizenData(citizenId, name, modelId, worldUUID, position, rotation, scale, npcUUID, hologramUuids,
                permission, permMessage, actions, isPlayerModel, useLiveSkin, skinUsername, cachedSkin, lastSkinUpdate, rotateTowardsPlayer);
        citizenData.setCreatedAt(0); // Mark as loaded from config, not newly created

        Vector3d currentPosition = config.getVector3d(basePath + ".current-position");
        if (currentPosition == null) {
            currentPosition = position;
        }
        citizenData.setCurrentPosition(currentPosition);

        // Load item data
        citizenData.setNpcHelmet(config.getString(basePath + ".npc-helmet", null));
        citizenData.setNpcChest(config.getString(basePath + ".npc-chest", null));
        citizenData.setNpcLeggings(config.getString(basePath + ".npc-leggings", null));
        citizenData.setNpcGloves(config.getString(basePath + ".npc-gloves", null));
        citizenData.setNpcHand(config.getString(basePath + ".npc-hand", null));
        citizenData.setNpcOffHand(config.getString(basePath + ".npc-offhand", null));

        // Misc
        citizenData.setHideNametag(config.getBoolean(basePath + ".hide-nametag", false));
        citizenData.setHideNpc(config.getBoolean(basePath + ".hide-npc", false));
        citizenData.setNametagOffset(config.getFloat(basePath + ".nametag-offset", 0));

        // Backwards compatibility
        citizenData.setFKeyInteractionEnabled(config.getBoolean(basePath + ".f-key-interaction", true));

        citizenData.setForceFKeyInteractionText(config.getBoolean(basePath + ".force-f-key-interaction-text", false));

        // Load animation behaviors
        List<AnimationBehavior> animBehaviors = new ArrayList<>();
        int animCount = config.getInt(basePath + ".animations.count", 0);
        for (int i = 0; i < animCount; i++) {
            String animPath = basePath + ".animations." + i;
            String animType = config.getString(animPath + ".type", "DEFAULT");
            String animName = config.getString(animPath + ".animation-name", "");
            int animSlot = config.getInt(animPath + ".animation-slot", 0);
            float interval = config.getFloat(animPath + ".interval-seconds", 5.0f);
            float proxRange = config.getFloat(animPath + ".proximity-range", 8.0f);
            boolean stopAfterTime = config.getBoolean(animPath + ".stop-after-time", false);
            String stopAnimName = config.getString(animPath + ".stop-animation-name", "");
            float stopTime = config.getFloat(animPath + ".stop-time-seconds", 3.0f);
            animBehaviors.add(new AnimationBehavior(animType, animName, animSlot, interval, proxRange, stopAfterTime, stopAnimName, stopTime));
        }
        citizenData.setAnimationBehaviors(animBehaviors);

        // Load movement behavior
        String moveType = config.getString(basePath + ".movement.type", "IDLE");
        float walkSpeed = config.getFloat(basePath + ".movement.walk-speed", 10.0f);
        float wanderRadius = config.getFloat(basePath + ".movement.wander-radius", 10.0f);
        float wanderWidth = config.getFloat(basePath + ".movement.wander-width", 10.0f);
        float wanderDepth = config.getFloat(basePath + ".movement.wander-depth", 10.0f);
        citizenData.setMovementBehavior(new MovementBehavior(moveType, walkSpeed, wanderRadius, wanderWidth, wanderDepth));

        // Load messages config
        int msgCount = config.getInt(basePath + ".messages.count", 0);
        String msgMode = config.getString(basePath + ".messages.mode", "RANDOM");
        boolean msgEnabled = config.getBoolean(basePath + ".messages.enabled", true);
        List<CitizenMessage> messages = new ArrayList<>();
        for (int i = 0; i < msgCount; i++) {
            String msgPath = basePath + ".messages." + i;
            String msg = config.getString(msgPath + ".message", "");
            // null = not yet set, will be migrated below
            String msgTrigger = config.getString(msgPath + ".interaction-trigger", null);
            float msgDelay = config.getFloat(msgPath + ".delay", 0.0f);
            messages.add(new CitizenMessage(msg, msgTrigger, msgDelay));
        }
        citizenData.setMessagesConfig(new MessagesConfig(messages, msgMode, msgEnabled));

        // Backwards compatibility
        boolean hasUnmigratedActions =
                actions.stream().anyMatch(a -> a.getInteractionTrigger() == null)
                || messages.stream().anyMatch(m -> m.getInteractionTrigger() == null);

        if (hasUnmigratedActions) {
            String migratedTrigger = citizenData.getFKeyInteractionEnabled() ? "BOTH" : "LEFT_CLICK";
            for (CommandAction action : actions) {
                if (action.getInteractionTrigger() == null) {
                    action.setInteractionTrigger(migratedTrigger);
                }
            }
            for (CitizenMessage message : messages) {
                if (message.getInteractionTrigger() == null) {
                    message.setInteractionTrigger(migratedTrigger);
                }
            }
            // Re-apply the lists so the citizenData reflects the migration.
            citizenData.setCommandActions(actions);
            MessagesConfig migratedMc = new MessagesConfig(messages, msgMode, msgEnabled);
            citizenData.setMessagesConfig(migratedMc);
        }

        // Load attitude and damage settings
        citizenData.setAttitude(config.getString(basePath + ".attitude", "PASSIVE"));
        citizenData.setTakesDamage(config.getBoolean(basePath + ".takes-damage", false));
        citizenData.setOverrideHealth(config.getBoolean(basePath + ".override-health", false));
        citizenData.setHealthAmount(config.getFloat(basePath + ".health-amount", 100));
        citizenData.setOverrideDamage(config.getBoolean(basePath + ".override-damage", false));
        citizenData.setDamageAmount(config.getFloat(basePath + ".damage-amount", 10));

        // Load respawn settings
        citizenData.setRespawnOnDeath(config.getBoolean(basePath + ".respawn-on-death", true));
        citizenData.setRespawnDelaySeconds(config.getFloat(basePath + ".respawn-delay", 5.0f));

        // Load group (backwards compatible - defaults to empty string)
        citizenData.setGroup(config.getString(basePath + ".group", ""));

        // Load death config
        DeathConfig deathConfig = new DeathConfig();
        deathConfig.setCommandSelectionMode(config.getString(basePath + ".death.command-mode", "ALL"));
        deathConfig.setMessageSelectionMode(config.getString(basePath + ".death.message-mode", "ALL"));

        List<DeathDropItem> drops = new ArrayList<>();

        for (String key : config.getKeys(basePath + ".death.drops")) {
            String dropPath = basePath + ".death.drops." + key;

            String itemId = config.getString(dropPath + ".item-id", "");
            int quantity = config.getInt(dropPath + ".quantity", 1);

            if (!itemId.isEmpty()) {
                drops.add(new DeathDropItem(itemId, quantity));
            }
        }

        if (!drops.isEmpty()) {
            deathConfig.setDropItems(drops);
        }


        List<CommandAction> deathCommands = new ArrayList<>();

        for (String key : config.getKeys(basePath + ".death.commands")) {
            String cmdPath = basePath + ".death.commands." + key;

            String cmd = config.getString(cmdPath + ".command", "");
            boolean runAsServer = config.getBoolean(cmdPath + ".run-as-server", true);
            float delay = config.getFloat(cmdPath + ".delay", 0);

            if (!cmd.isEmpty()) {
                deathCommands.add(new CommandAction(cmd, runAsServer, delay));
            }
        }

        if (!deathCommands.isEmpty()) {
            deathConfig.setDeathCommands(deathCommands);
        }


        List<CitizenMessage> deathMessages = new ArrayList<>();

        for (String key : config.getKeys(basePath + ".death.messages")) {
            String msgPath = basePath + ".death.messages." + key;

            String msg = config.getString(msgPath + ".message", "");
            float delay = config.getFloat(msgPath + ".delay", 0);

            if (!msg.isEmpty()) {
                deathMessages.add(new CitizenMessage(msg, null, delay));
            }
        }

        if (!deathMessages.isEmpty()) {
            deathConfig.setDeathMessages(deathMessages);
        }

        citizenData.setDeathConfig(deathConfig);

        // Load new config fields
        citizenData.setMaxHealth(config.getFloat(basePath + ".max-health", 100));
        citizenData.setLeashDistance(config.getFloat(basePath + ".leash-distance", 45));
        citizenData.setDefaultNpcAttitude(config.getString(basePath + ".default-npc-attitude", "Ignore"));
        citizenData.setApplySeparation(config.getBoolean(basePath + ".apply-separation", true));

        // Load combat config
        CombatConfig combatConfig = new CombatConfig();
        combatConfig.setAttackType(config.getString(basePath + ".combat.attack-type", "Root_NPC_Attack_Melee"));
        combatConfig.setAttackDistance(config.getFloat(basePath + ".combat.attack-distance", 2.0f));
        combatConfig.setChaseSpeed(config.getFloat(basePath + ".combat.chase-speed", 0.67f));
        combatConfig.setCombatBehaviorDistance(config.getFloat(basePath + ".combat.combat-behavior-distance", 5.0f));
        combatConfig.setCombatStrafeWeight(config.getInt(basePath + ".combat.combat-strafe-weight", 10));
        combatConfig.setCombatDirectWeight(config.getInt(basePath + ".combat.combat-direct-weight", 10));
        combatConfig.setBackOffAfterAttack(config.getBoolean(basePath + ".combat.back-off-after-attack", true));
        combatConfig.setBackOffDistance(config.getFloat(basePath + ".combat.back-off-distance", 4.0f));
        combatConfig.setDesiredAttackDistanceMin(config.getFloat(basePath + ".combat.desired-attack-dist-min", 1.5f));
        combatConfig.setDesiredAttackDistanceMax(config.getFloat(basePath + ".combat.desired-attack-dist-max", 1.5f));
        combatConfig.setAttackPauseMin(config.getFloat(basePath + ".combat.attack-pause-min", 1.5f));
        combatConfig.setAttackPauseMax(config.getFloat(basePath + ".combat.attack-pause-max", 2.0f));
        combatConfig.setCombatRelativeTurnSpeed(config.getFloat(basePath + ".combat.combat-relative-turn-speed", 1.5f));
        combatConfig.setCombatAlwaysMovingWeight(config.getInt(basePath + ".combat.combat-always-moving-weight", 0));
        combatConfig.setCombatStrafingDurationMin(config.getFloat(basePath + ".combat.strafing-duration-min", 1.0f));
        combatConfig.setCombatStrafingDurationMax(config.getFloat(basePath + ".combat.strafing-duration-max", 1.0f));
        combatConfig.setCombatStrafingFrequencyMin(config.getFloat(basePath + ".combat.strafing-frequency-min", 2.0f));
        combatConfig.setCombatStrafingFrequencyMax(config.getFloat(basePath + ".combat.strafing-frequency-max", 2.0f));
        combatConfig.setCombatAttackPreDelayMin(config.getFloat(basePath + ".combat.attack-pre-delay-min", 0.2f));
        combatConfig.setCombatAttackPreDelayMax(config.getFloat(basePath + ".combat.attack-pre-delay-max", 0.2f));
        combatConfig.setCombatAttackPostDelayMin(config.getFloat(basePath + ".combat.attack-post-delay-min", 0.2f));
        combatConfig.setCombatAttackPostDelayMax(config.getFloat(basePath + ".combat.attack-post-delay-max", 0.2f));
        combatConfig.setBackOffDurationMin(config.getFloat(basePath + ".combat.back-off-duration-min", 2.0f));
        combatConfig.setBackOffDurationMax(config.getFloat(basePath + ".combat.back-off-duration-max", 3.0f));
        combatConfig.setBlockAbility(config.getString(basePath + ".combat.block-ability", "Shield_Block"));
        combatConfig.setBlockProbability(config.getInt(basePath + ".combat.block-probability", 50));
        combatConfig.setCombatFleeIfTooCloseDistance(config.getFloat(basePath + ".combat.flee-if-too-close", 0f));
        combatConfig.setTargetSwitchTimerMin(config.getFloat(basePath + ".combat.target-switch-min", 5.0f));
        combatConfig.setTargetSwitchTimerMax(config.getFloat(basePath + ".combat.target-switch-max", 5.0f));
        combatConfig.setTargetRange(config.getFloat(basePath + ".combat.target-range", 4.0f));
        combatConfig.setCombatMovingRelativeSpeed(config.getFloat(basePath + ".combat.combat-moving-speed", 0.6f));
        combatConfig.setCombatBackwardsRelativeSpeed(config.getFloat(basePath + ".combat.combat-backwards-speed", 0.3f));
        combatConfig.setUseCombatActionEvaluator(config.getBoolean(basePath + ".combat.use-combat-action-evaluator", false));
        citizenData.setCombatConfig(combatConfig);

        // Load detection config
        DetectionConfig detectionConfig = new DetectionConfig();
        detectionConfig.setViewRange(config.getFloat(basePath + ".detection.view-range", 15));
        detectionConfig.setViewSector(config.getFloat(basePath + ".detection.view-sector", 180));
        detectionConfig.setHearingRange(config.getFloat(basePath + ".detection.hearing-range", 8));
        detectionConfig.setAbsoluteDetectionRange(config.getFloat(basePath + ".detection.absolute-detection-range", 2));
        detectionConfig.setAlertedRange(config.getFloat(basePath + ".detection.alerted-range", 45));
        detectionConfig.setAlertedTimeMin(config.getFloat(basePath + ".detection.alerted-time-min", 1.0f));
        detectionConfig.setAlertedTimeMax(config.getFloat(basePath + ".detection.alerted-time-max", 2.0f));
        detectionConfig.setChanceToBeAlertedWhenReceivingCallForHelp(config.getInt(basePath + ".detection.chance-alerted-call-for-help", 70));
        detectionConfig.setConfusedTimeMin(config.getFloat(basePath + ".detection.confused-time-min", 1.0f));
        detectionConfig.setConfusedTimeMax(config.getFloat(basePath + ".detection.confused-time-max", 2.0f));
        detectionConfig.setSearchTimeMin(config.getFloat(basePath + ".detection.search-time-min", 10.0f));
        detectionConfig.setSearchTimeMax(config.getFloat(basePath + ".detection.search-time-max", 14.0f));
        detectionConfig.setInvestigateRange(config.getFloat(basePath + ".detection.investigate-range", 40.0f));
        citizenData.setDetectionConfig(detectionConfig);

        // Load path config
        PathConfig pathConfig = new PathConfig();
        pathConfig.setFollowPath(config.getBoolean(basePath + ".path.follow-path", false));
        pathConfig.setPathName(config.getString(basePath + ".path.path-name", ""));
        pathConfig.setPatrol(config.getBoolean(basePath + ".path.patrol", false));
        pathConfig.setPatrolWanderDistance(config.getFloat(basePath + ".path.patrol-wander-distance", 25));
        pathConfig.setLoopMode(config.getString(basePath + ".path.loop-mode", "LOOP"));
        pathConfig.setPluginPatrolPath(config.getString(basePath + ".path.plugin-patrol-path", ""));
        citizenData.setPathConfig(pathConfig);

        // Load extended Template_Citizen parameters
        citizenData.setDropList(config.getString(basePath + ".drop-list", "Empty"));
        citizenData.setRunThreshold(config.getFloat(basePath + ".run-threshold", 0.3f));
        citizenData.setWakingIdleBehaviorComponent(config.getString(basePath + ".waking-idle-behavior", "Component_Instruction_Waking_Idle"));
        citizenData.setDayFlavorAnimation(config.getString(basePath + ".day-flavor-animation", ""));
        citizenData.setDayFlavorAnimationLengthMin(config.getFloat(basePath + ".day-flavor-anim-length-min", 3.0f));
        citizenData.setDayFlavorAnimationLengthMax(config.getFloat(basePath + ".day-flavor-anim-length-max", 5.0f));
        citizenData.setAttitudeGroup(config.getString(basePath + ".attitude-group", "Empty"));
        citizenData.setNameTranslationKey(config.getString(basePath + ".name-translation-key", "Citizen"));
        citizenData.setBreathesInWater(config.getBoolean(basePath + ".breathes-in-water", false));
        citizenData.setLeashMinPlayerDistance(config.getFloat(basePath + ".leash-min-player-distance", 4.0f));
        citizenData.setLeashTimerMin(config.getFloat(basePath + ".leash-timer-min", 3.0f));
        citizenData.setLeashTimerMax(config.getFloat(basePath + ".leash-timer-max", 5.0f));
        citizenData.setHardLeashDistance(config.getFloat(basePath + ".hard-leash-distance", 200.0f));
        citizenData.setDefaultHotbarSlot(config.getInt(basePath + ".default-hotbar-slot", 0));
        citizenData.setRandomIdleHotbarSlot(config.getInt(basePath + ".random-idle-hotbar-slot", -1));
        citizenData.setChanceToEquipFromIdleHotbarSlot(config.getInt(basePath + ".chance-equip-idle-hotbar", 5));
        citizenData.setDefaultOffHandSlot(config.getInt(basePath + ".default-offhand-slot", -1));
        citizenData.setNighttimeOffhandSlot(config.getInt(basePath + ".nighttime-offhand-slot", 0));
        citizenData.setKnockbackScale(config.getFloat(basePath + ".knockback-scale", 0.5f));
        List<String> weaponsList = config.getStringList(basePath + ".weapons");
        if (weaponsList != null) citizenData.setWeapons(weaponsList);
        List<String> offHandItemsList = config.getStringList(basePath + ".offhand-items");
        if (offHandItemsList != null) citizenData.setOffHandItems(offHandItemsList);
        List<String> combatTargetGroups = config.getStringList(basePath + ".combat-message-target-groups");
        if (combatTargetGroups != null) citizenData.setCombatMessageTargetGroups(combatTargetGroups);
        List<String> flockArr = config.getStringList(basePath + ".flock-array");
        if (flockArr != null) citizenData.setFlockArray(flockArr);
        List<String> disableDmgGroups = config.getStringList(basePath + ".disable-damage-groups");
        if (disableDmgGroups != null) citizenData.setDisableDamageGroups(disableDmgGroups);

        return citizenData;
    }


    public void saveCitizen(@Nonnull CitizenData citizen) {
        saveCitizen(citizen, false); // False since in some cases, this could cause issues if set to true
    }

    public void saveCitizen(@Nonnull CitizenData citizen, boolean respawnIfRoleChanged) {
        config.beginBatch();

        try {
            String basePath = "citizens." + citizen.getId();

            config.set(basePath + ".name", citizen.getName());
            config.set(basePath + ".model-id", citizen.getModelId());
            config.set(basePath + ".model-world-uuid", citizen.getWorldUUID().toString());
            config.setVector3d(basePath + ".position", citizen.getPosition());
            config.setVector3f(basePath + ".rotation", citizen.getRotation());
            config.setVector3d(basePath + ".current-position", citizen.getCurrentPosition());
            config.set(basePath + ".scale", citizen.getScale());
            config.set(basePath + ".permission", citizen.getRequiredPermission());
            config.set(basePath + ".permission-message", citizen.getNoPermissionMessage());
            config.set(basePath + ".rotate-towards-player", citizen.getRotateTowardsPlayer());
            config.setUUID(basePath + ".npc-uuid", citizen.getSpawnedUUID());
            config.setUUIDList(basePath + ".hologram-uuids", citizen.getHologramLineUuids());

            // Save item data
            config.set(basePath + ".npc-helmet", citizen.getNpcHelmet());
            config.set(basePath + ".npc-chest", citizen.getNpcChest());
            config.set(basePath + ".npc-leggings", citizen.getNpcLeggings());
            config.set(basePath + ".npc-gloves", citizen.getNpcGloves());
            config.set(basePath + ".npc-hand", citizen.getNpcHand());
            config.set(basePath + ".npc-offhand", citizen.getNpcOffHand());

            // Save skin data
            config.set(basePath + ".is-player-model", citizen.isPlayerModel());
            config.set(basePath + ".use-live-skin", citizen.isUseLiveSkin());
            config.set(basePath + ".skin-username", citizen.getSkinUsername());
            config.setPlayerSkin(basePath + ".cached-skin", citizen.getCachedSkin());
            config.set(basePath + ".last-skin-update", citizen.getLastSkinUpdate());

            // Save command actions
            List<CommandAction> actions = citizen.getCommandActions();
            config.set(basePath + ".commands.count", actions.size());

            for (int i = 0; i < actions.size(); i++) {
                CommandAction action = actions.get(i);
                String commandPath = basePath + ".commands." + i;

                config.set(commandPath + ".command", action.getCommand());
                config.set(commandPath + ".run-as-server", action.isRunAsServer());
                config.set(commandPath + ".delay", action.getDelaySeconds());
                config.set(commandPath + ".interaction-trigger",
                        action.getInteractionTrigger() != null ? action.getInteractionTrigger() : "BOTH");
            }

            config.set(basePath + ".force-f-key-interaction-text", citizen.getForceFKeyInteractionText());

            // Misc
            config.set(basePath + ".hide-nametag", citizen.isHideNametag());
            config.set(basePath + ".hide-npc", citizen.isHideNpc());
            config.set(basePath + ".nametag-offset", citizen.getNametagOffset());

            // Save animation behaviors
            List<AnimationBehavior> animBehaviors = citizen.getAnimationBehaviors();
            config.set(basePath + ".animations.count", animBehaviors.size());
            for (int i = 0; i < animBehaviors.size(); i++) {
                AnimationBehavior ab = animBehaviors.get(i);
                String animPath = basePath + ".animations." + i;
                config.set(animPath + ".type", ab.getType());
                config.set(animPath + ".animation-name", ab.getAnimationName());
                config.set(animPath + ".animation-slot", ab.getAnimationSlot());
                config.set(animPath + ".interval-seconds", ab.getIntervalSeconds());
                config.set(animPath + ".proximity-range", ab.getProximityRange());
                config.set(animPath + ".stop-after-time", ab.isStopAfterTime());
                config.set(animPath + ".stop-animation-name", ab.getStopAnimationName());
                config.set(animPath + ".stop-time-seconds", ab.getStopTimeSeconds());
            }

            // Save movement behavior
            MovementBehavior mb = citizen.getMovementBehavior();
            config.set(basePath + ".movement.type", mb.getType());
            config.set(basePath + ".movement.walk-speed", mb.getWalkSpeed());
            config.set(basePath + ".movement.wander-radius", mb.getWanderRadius());
            config.set(basePath + ".movement.wander-width", mb.getWanderWidth());
            config.set(basePath + ".movement.wander-depth", mb.getWanderDepth());

            // Save messages config
            MessagesConfig mc = citizen.getMessagesConfig();
            List<CitizenMessage> msgs = mc.getMessages();
            config.set(basePath + ".messages.count", msgs.size());
            config.set(basePath + ".messages.mode", mc.getSelectionMode());
            config.set(basePath + ".messages.enabled", mc.isEnabled());
            for (int i = 0; i < msgs.size(); i++) {
                String msgPath = basePath + ".messages." + i;
                CitizenMessage cm = msgs.get(i);
                config.set(msgPath + ".message", cm.getMessage());
                config.set(msgPath + ".interaction-trigger",
                        cm.getInteractionTrigger() != null ? cm.getInteractionTrigger() : "BOTH");
                config.set(msgPath + ".delay", cm.getDelaySeconds());
            }

            // Save attitude and damage settings
            config.set(basePath + ".attitude", citizen.getAttitude());
            config.set(basePath + ".takes-damage", citizen.isTakesDamage());
            config.set(basePath + ".override-health", citizen.isOverrideHealth());
            config.set(basePath + ".health-amount", citizen.getHealthAmount());
            config.set(basePath + ".override-damage", citizen.isOverrideDamage());
            config.set(basePath + ".damage-amount", citizen.getDamageAmount());

            // Save respawn settings
            config.set(basePath + ".respawn-on-death", citizen.isRespawnOnDeath());
            config.set(basePath + ".respawn-delay", citizen.getRespawnDelaySeconds());

            // Save group
            config.set(basePath + ".group", citizen.getGroup());

            // Save death config
            DeathConfig dc = citizen.getDeathConfig();
            config.set(basePath + ".death.command-mode", dc.getCommandSelectionMode());
            config.set(basePath + ".death.message-mode", dc.getMessageSelectionMode());

            List<DeathDropItem> drops = dc.getDropItems();
            config.set(basePath + ".death.drops", new ArrayList<>());
            for (int i = 0; i < drops.size(); i++) {
                String dropPath = basePath + ".death.drops." + i;
                config.set(dropPath + ".item-id", drops.get(i).getItemId());
                config.set(dropPath + ".quantity", drops.get(i).getQuantity());
            }

            List<CommandAction> deathCmds = dc.getDeathCommands();
            config.set(basePath + ".death.commands", new ArrayList<>());
            for (int i = 0; i < deathCmds.size(); i++) {
                String cmdPath = basePath + ".death.commands." + i;
                CommandAction cmd = deathCmds.get(i);
                config.set(cmdPath + ".command", cmd.getCommand());
                config.set(cmdPath + ".run-as-server", cmd.isRunAsServer());
                config.set(cmdPath + ".delay", cmd.getDelaySeconds());
            }

            List<CitizenMessage> deathMsgs = dc.getDeathMessages();
            config.set(basePath + ".death.messages", new ArrayList<>());
            for (int i = 0; i < deathMsgs.size(); i++) {
                String msgPath = basePath + ".death.messages." + i;
                CitizenMessage msg = deathMsgs.get(i);
                config.set(msgPath + ".message", msg.getMessage());
                config.set(msgPath + ".delay", msg.getDelaySeconds());
            }

            config.set(basePath + ".max-health", citizen.getMaxHealth());
            config.set(basePath + ".leash-distance", citizen.getLeashDistance());
            config.set(basePath + ".default-npc-attitude", citizen.getDefaultNpcAttitude());
            config.set(basePath + ".apply-separation", citizen.isApplySeparation());

            // Save combat config
            CombatConfig combat = citizen.getCombatConfig();
            config.set(basePath + ".combat.attack-type", combat.getAttackType());
            config.set(basePath + ".combat.attack-distance", combat.getAttackDistance());
            config.set(basePath + ".combat.chase-speed", combat.getChaseSpeed());
            config.set(basePath + ".combat.combat-behavior-distance", combat.getCombatBehaviorDistance());
            config.set(basePath + ".combat.combat-strafe-weight", combat.getCombatStrafeWeight());
            config.set(basePath + ".combat.combat-direct-weight", combat.getCombatDirectWeight());
            config.set(basePath + ".combat.back-off-after-attack", combat.isBackOffAfterAttack());
            config.set(basePath + ".combat.back-off-distance", combat.getBackOffDistance());
            config.set(basePath + ".combat.desired-attack-dist-min", combat.getDesiredAttackDistanceMin());
            config.set(basePath + ".combat.desired-attack-dist-max", combat.getDesiredAttackDistanceMax());
            config.set(basePath + ".combat.attack-pause-min", combat.getAttackPauseMin());
            config.set(basePath + ".combat.attack-pause-max", combat.getAttackPauseMax());
            config.set(basePath + ".combat.combat-relative-turn-speed", combat.getCombatRelativeTurnSpeed());
            config.set(basePath + ".combat.combat-always-moving-weight", combat.getCombatAlwaysMovingWeight());
            config.set(basePath + ".combat.strafing-duration-min", combat.getCombatStrafingDurationMin());
            config.set(basePath + ".combat.strafing-duration-max", combat.getCombatStrafingDurationMax());
            config.set(basePath + ".combat.strafing-frequency-min", combat.getCombatStrafingFrequencyMin());
            config.set(basePath + ".combat.strafing-frequency-max", combat.getCombatStrafingFrequencyMax());
            config.set(basePath + ".combat.attack-pre-delay-min", combat.getCombatAttackPreDelayMin());
            config.set(basePath + ".combat.attack-pre-delay-max", combat.getCombatAttackPreDelayMax());
            config.set(basePath + ".combat.attack-post-delay-min", combat.getCombatAttackPostDelayMin());
            config.set(basePath + ".combat.attack-post-delay-max", combat.getCombatAttackPostDelayMax());
            config.set(basePath + ".combat.back-off-duration-min", combat.getBackOffDurationMin());
            config.set(basePath + ".combat.back-off-duration-max", combat.getBackOffDurationMax());
            config.set(basePath + ".combat.block-ability", combat.getBlockAbility());
            config.set(basePath + ".combat.block-probability", combat.getBlockProbability());
            config.set(basePath + ".combat.flee-if-too-close", combat.getCombatFleeIfTooCloseDistance());
            config.set(basePath + ".combat.target-switch-min", combat.getTargetSwitchTimerMin());
            config.set(basePath + ".combat.target-switch-max", combat.getTargetSwitchTimerMax());
            config.set(basePath + ".combat.target-range", combat.getTargetRange());
            config.set(basePath + ".combat.combat-moving-speed", combat.getCombatMovingRelativeSpeed());
            config.set(basePath + ".combat.combat-backwards-speed", combat.getCombatBackwardsRelativeSpeed());
            config.set(basePath + ".combat.use-combat-action-evaluator", combat.isUseCombatActionEvaluator());

            // Save detection config
            DetectionConfig detection = citizen.getDetectionConfig();
            config.set(basePath + ".detection.view-range", detection.getViewRange());
            config.set(basePath + ".detection.view-sector", detection.getViewSector());
            config.set(basePath + ".detection.hearing-range", detection.getHearingRange());
            config.set(basePath + ".detection.absolute-detection-range", detection.getAbsoluteDetectionRange());
            config.set(basePath + ".detection.alerted-range", detection.getAlertedRange());
            config.set(basePath + ".detection.alerted-time-min", detection.getAlertedTimeMin());
            config.set(basePath + ".detection.alerted-time-max", detection.getAlertedTimeMax());
            config.set(basePath + ".detection.chance-alerted-call-for-help", detection.getChanceToBeAlertedWhenReceivingCallForHelp());
            config.set(basePath + ".detection.confused-time-min", detection.getConfusedTimeMin());
            config.set(basePath + ".detection.confused-time-max", detection.getConfusedTimeMax());
            config.set(basePath + ".detection.search-time-min", detection.getSearchTimeMin());
            config.set(basePath + ".detection.search-time-max", detection.getSearchTimeMax());
            config.set(basePath + ".detection.investigate-range", detection.getInvestigateRange());

            // Save path config
            PathConfig pathCfg = citizen.getPathConfig();
            config.set(basePath + ".path.follow-path", pathCfg.isFollowPath());
            config.set(basePath + ".path.path-name", pathCfg.getPathName());
            config.set(basePath + ".path.patrol", pathCfg.isPatrol());
            config.set(basePath + ".path.patrol-wander-distance", pathCfg.getPatrolWanderDistance());
            config.set(basePath + ".path.loop-mode", pathCfg.getLoopMode());
            config.set(basePath + ".path.plugin-patrol-path", pathCfg.getPluginPatrolPath());

            // Save extended Template_Citizen parameters
            config.set(basePath + ".drop-list", citizen.getDropList());
            config.set(basePath + ".run-threshold", citizen.getRunThreshold());
            config.set(basePath + ".waking-idle-behavior", citizen.getWakingIdleBehaviorComponent());
            config.set(basePath + ".day-flavor-animation", citizen.getDayFlavorAnimation());
            config.set(basePath + ".day-flavor-anim-length-min", citizen.getDayFlavorAnimationLengthMin());
            config.set(basePath + ".day-flavor-anim-length-max", citizen.getDayFlavorAnimationLengthMax());
            config.set(basePath + ".attitude-group", citizen.getAttitudeGroup());
            config.set(basePath + ".name-translation-key", citizen.getNameTranslationKey());
            config.set(basePath + ".breathes-in-water", citizen.isBreathesInWater());
            config.set(basePath + ".leash-min-player-distance", citizen.getLeashMinPlayerDistance());
            config.set(basePath + ".leash-timer-min", citizen.getLeashTimerMin());
            config.set(basePath + ".leash-timer-max", citizen.getLeashTimerMax());
            config.set(basePath + ".hard-leash-distance", citizen.getHardLeashDistance());
            config.set(basePath + ".default-hotbar-slot", citizen.getDefaultHotbarSlot());
            config.set(basePath + ".random-idle-hotbar-slot", citizen.getRandomIdleHotbarSlot());
            config.set(basePath + ".chance-equip-idle-hotbar", citizen.getChanceToEquipFromIdleHotbarSlot());
            config.set(basePath + ".default-offhand-slot", citizen.getDefaultOffHandSlot());
            config.set(basePath + ".nighttime-offhand-slot", citizen.getNighttimeOffhandSlot());
            config.set(basePath + ".knockback-scale", citizen.getKnockbackScale());
            config.setStringList(basePath + ".weapons", citizen.getWeapons());
            config.setStringList(basePath + ".offhand-items", citizen.getOffHandItems());
            config.setStringList(basePath + ".combat-message-target-groups", citizen.getCombatMessageTargetGroups());
            config.setStringList(basePath + ".flock-array", citizen.getFlockArray());
            config.setStringList(basePath + ".disable-damage-groups", citizen.getDisableDamageGroups());

            boolean npcSpawned = citizen.getNpcRef() != null && citizen.getNpcRef().isValid();

            // Only write role file if role-relevant data actually changed
            boolean roleChanged = roleGenerator.generateRoleIfChanged(citizen);

            // Add group to groups set if not empty
            if (!citizen.getGroup().isEmpty()) {
                groups.add(citizen.getGroup());
                saveGroups();
            }

            // This is needed due to a Hytale bug
            // Also, we only respawn sometimes due to a bug that can occur causing double spawning
            if (npcSpawned && roleChanged && respawnIfRoleChanged) {
                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    // This is causing issues
//                    if (citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
//                        return;
//                    }

                    updateSpawnedCitizen(citizen, false);
                }, 5, TimeUnit.SECONDS);
            }
        } finally {
            config.endBatch();
        }
    }

    public void addCitizen(@Nonnull CitizenData citizen, boolean save) {
        citizen.setCreatedAt(System.currentTimeMillis());

        citizens.put(citizen.getId(), citizen);

        if (save)
            saveCitizen(citizen);

        spawnCitizen(citizen, save);
    }

    public void updateCitizen(@Nonnull CitizenData citizen, boolean save) {
        citizens.put(citizen.getId(), citizen);

        if (save)
            saveCitizen(citizen);

        updateSpawnedCitizen(citizen, save);
    }

    public void updateCitizenNPC(@Nonnull CitizenData citizen, boolean save) {
        citizens.put(citizen.getId(), citizen);

        if (save)
            saveCitizen(citizen);

        updateSpawnedCitizenNPC(citizen, save);
    }

    public void updateCitizenHologram(@Nonnull CitizenData citizen, boolean save) {
        citizens.put(citizen.getId(), citizen);

        updateSpawnedCitizenHologram(citizen, save);

        // Must go after
        if (save)
            saveCitizen(citizen);
    }

    public void updateCitizenNPCItems(CitizenData citizen) {
        if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null) {
            return;
        }

        NPCEntity npcEntity = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), NPCEntity.getComponentType());
        if (npcEntity == null) {
            return;
        }

        EntityStatMap statMap = null;

        // Get current max health
        float maxHealth = 100;
        if (npcEntity.getReference() != null) {
            statMap = npcEntity.getReference().getStore().getComponent(npcEntity.getReference(), EntityStatsModule.get().getEntityStatMapComponentType());
            if (statMap != null) {
                EntityStatValue maxHealthValue = statMap.get(DefaultEntityStatTypes.getHealth());
                if (maxHealthValue != null) {
                    maxHealth = maxHealthValue.getMax();
                }
            }
        }


        // Item in hand
        if (citizen.getNpcHand() == null) {
            npcEntity.getInventory().getHotbar().setItemStackForSlot((short) 0, null);
        }
        else {
            npcEntity.getInventory().getHotbar().setItemStackForSlot((short) 0, new ItemStack(citizen.getNpcHand()));
        }

        // Item in offhand
        if (citizen.getNpcOffHand() == null) {
            npcEntity.getInventory().getUtility().setItemStackForSlot((short) 0, null);
        }
        else {
            npcEntity.getInventory().getUtility().setItemStackForSlot((short) 0, new ItemStack(citizen.getNpcOffHand()));
        }

        // Set helmet
        if (citizen.getNpcHelmet() == null) {
            npcEntity.getInventory().getArmor().setItemStackForSlot((short) 0, null);
        }
        else {
            npcEntity.getInventory().getArmor().setItemStackForSlot((short) 0, new ItemStack(citizen.getNpcHelmet()));
        }

        // Set chest
        if (citizen.getNpcChest() == null) {
            npcEntity.getInventory().getArmor().setItemStackForSlot((short) 1, null);
        }
        else {
            npcEntity.getInventory().getArmor().setItemStackForSlot((short) 1, new ItemStack(citizen.getNpcChest()));
        }

        // Set gloves
        if (citizen.getNpcGloves() == null) {
            npcEntity.getInventory().getArmor().setItemStackForSlot((short) 2, null);
        }
        else {
            npcEntity.getInventory().getArmor().setItemStackForSlot((short) 2, new ItemStack(citizen.getNpcGloves()));
        }

        // Set leggings
        if (citizen.getNpcLeggings() == null) {
            npcEntity.getInventory().getArmor().setItemStackForSlot((short) 3, null);
        }
        else {
            npcEntity.getInventory().getArmor().setItemStackForSlot((short) 3, new ItemStack(citizen.getNpcLeggings()));
        }

        EntityStatMap finalStatMap = statMap;
        float finalMaxHealth = maxHealth;

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            World world = Universe.get().getWorld(citizen.getWorldUUID());
            if (world == null) {
                return;
            }

            // Update health after applying armor
            if (npcEntity.getReference() != null && finalStatMap != null) {
                EntityStatValue healthValue = finalStatMap.get(DefaultEntityStatTypes.getHealth());
                if (healthValue != null) {
                    float healthDifference = healthValue.getMax() - finalMaxHealth;
                    finalStatMap.setStatValue(DefaultEntityStatTypes.getHealth(), healthValue.get() + healthDifference);
                }
            }
        }, 200, TimeUnit.MILLISECONDS);
    }

    public void removeCitizen(@Nonnull String citizenId) {
        CitizenData citizen = citizens.remove(citizenId);

        config.set("citizens." + citizenId, null);

        roleGenerator.deleteRoleFile(citizenId);

        if (citizen == null) {
            return;
        }

        despawnCitizenForDeletion(citizen);
    }

    private void despawnCitizenForDeletion(@Nonnull CitizenData citizen) {
        despawnCitizenNPCForDeletion(citizen);
        despawnCitizenHologram(citizen);
    }
    public void spawnCitizen(CitizenData citizen, boolean save) {
        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            getLogger().atWarning().log("Failed to spawn citizen: " + citizen.getName() + ". Failed to find world. Try updating the citizen's position.");
            return;
        }

//        Map<String, String> randomAttachmentIds = new HashMap<>();
//        Model citizenModel = new Model.ModelReference(citizen.getModelId(), citizen.getScale(), randomAttachmentIds).toModel();
//
//        if (citizenModel == null) {
//            getLogger().atWarning().log("Failed to spawn citizen: " + citizen.getName() + ". The model ID is invalid. Try updating the model ID.");
//            return;
//        }

        long start = System.currentTimeMillis();
        final ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];
        boolean[] spawned = { false };
        boolean[] queued = { false };

        futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            if (spawned[0]) {
                futureRef[0].cancel(false);
                return;
            }

            // Timeout
            long elapsedMs = System.currentTimeMillis() - start;
            if (elapsedMs >= 15_000) {
                futureRef[0].cancel(false);
                return;
            }

            if (queued[0]) {
                return;
            }
            queued[0] = true;

            world.execute(() -> {
                long chunkIndex = ChunkUtil.indexChunkFromBlock(citizen.getPosition().x, citizen.getPosition().z);
                WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);

                if (chunk == null) {
                    queued[0] = false;
                    return;
                }

                spawned[0] = true;
                futureRef[0].cancel(false);

                despawnCitizenNPC(citizen);
                despawnCitizenHologram(citizen);

                if (!citizen.isHideNpc()) {
                    spawnCitizenNPC(citizen, save);
                }
                updateSpawnedCitizenHologram(citizen, save);
            });

        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    public boolean isCitizenSpawning(@Nonnull String citizenId) {
        return citizensCurrentlySpawning.contains(citizenId);
    }

    public void spawnCitizenNPC(CitizenData citizen, boolean save) {
        if (!citizensCurrentlySpawning.add(citizen.getId())) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            citizensCurrentlySpawning.remove(citizen.getId());
            getLogger().atWarning().log("Failed to spawn citizen NPC: " + citizen.getName() + ". Failed to find world. Try updating the citizen's position.");
            return;
        }

        if (citizen.getSpawnedUUID() != null || citizen.getNpcRef() != null) {
            despawnCitizenNPC(citizen);
        }

        // Handle player model with skin
        if (citizen.isPlayerModel()) {
            spawnPlayerModelNPC(citizen, world, save);
            return;
        }

        // Regular model spawning
        float scale = Math.max((float)0.01, citizen.getScale());
        Map<String, String> randomAttachmentIds = new HashMap<>();
        Model citizenModel = new Model.ModelReference(citizen.getModelId(), scale, randomAttachmentIds).toModel();

        if (citizenModel == null) {
            citizensCurrentlySpawning.remove(citizen.getId());
            getLogger().atWarning().log("Failed to spawn citizen NPC: " + citizen.getName() + ". The model ID is invalid. Try updating the model ID.");
            return;
        }

        String roleName = resolveRoleName(citizen);

        Pair<Ref<EntityStore>, NPCEntity> npc = NPCPlugin.get().spawnEntity(
                world.getEntityStore().getStore(),
                NPCPlugin.get().getIndex(roleName),
                citizen.getPosition(),
                citizen.getRotation(),
                citizenModel,
                null,
                null
        );

        if (npc == null) {
            citizensCurrentlySpawning.remove(citizen.getId());
            return;
        }

        npc.second().setLeashPoint(citizen.getPosition());

        npc.second().setInventorySize(9, 30, 5);

        Ref<EntityStore> ref = npc.first();
        Store<EntityStore> store = ref.getStore();

        // This is required since the "Player" entity's scale resets to 0
        if (citizen.getModelId().equals("Player")) {
            PersistentModel persistentModel = store.getComponent(ref, PersistentModel.getComponentType());
            if (persistentModel != null) {
                persistentModel.setModelReference(new Model.ModelReference(
                        citizenModel.getModelAssetId(),
                        citizenModel.getScale(),
                        citizenModel.getRandomAttachmentIds(),
                        citizenModel.getAnimationSetMap() == null
                ));
            }
        }

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());

        citizen.setNpcRef(ref);

        if (uuidComponent != null) {
            citizen.setSpawnedUUID(uuidComponent.getUuid());

            if (save)
                saveCitizen(citizen);
        }

        if (!citizen.isTakesDamage() || "PASSIVE".equals(citizen.getAttitude())) {
            store.addComponent(ref, Invulnerable.getComponentType());
        }

        applyHealthOverride(ref, citizen);

        setInteractionComponent(store, ref, citizen);
        applyNpcNameplateComponent(store, ref, citizen);
        updateCitizenNPCItems(citizen);
        triggerAnimations(citizen, "DEFAULT");
        String pluginPatrolPath = citizen.getPathConfig().getPluginPatrolPath();
        if (!pluginPatrolPath.isEmpty() && patrolManager != null) {
            patrolManager.startPatrol(citizen.getId(), pluginPatrolPath);
        }
        citizensCurrentlySpawning.remove(citizen.getId());
    }

    public void spawnPlayerModelNPC(CitizenData citizen, World world, boolean save) {
        if (citizen.getSpawnedUUID() != null || citizen.getNpcRef() != null) {
            despawnCitizenNPC(citizen);
        }

        PlayerSkin skinToUse = determineSkin(citizen);

        float scale = Math.max((float)0.01, citizen.getScale());
        Model playerModel;

        if (skinToUse != null) {
            playerModel = CosmeticsModule.get().createModel(skinToUse, scale);
        } else {
            Map<String, String> randomAttachmentIds = new HashMap<>();
            playerModel = new Model.ModelReference("Player", scale, randomAttachmentIds).toModel();
        }
        //Map<String, String> randomAttachmentIds = new HashMap<>();
        //Model playerModel = new Model.ModelReference("PlayerTestModel_V", scale, randomAttachmentIds).toModel();

        if (playerModel == null) {
            citizensCurrentlySpawning.remove(citizen.getId());
            getLogger().atWarning().log("Failed to create player model for citizen: " + citizen.getName());
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(citizen.getPosition().x, citizen.getPosition().z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            citizensCurrentlySpawning.remove(citizen.getId());
            getLogger().atInfo().log("Failed to spawn player model for citizen NPC: " + citizen.getName() + ". The world chunk is unloaded.");
            return;
        }

        String roleName = resolveRoleName(citizen);

        Pair<Ref<EntityStore>, NPCEntity> npc = NPCPlugin.get().spawnEntity(
                world.getEntityStore().getStore(),
                NPCPlugin.get().getIndex(roleName),
                citizen.getPosition(),
                citizen.getRotation(),
                playerModel,
                null,
                null
        );

        if (npc == null) {
            citizensCurrentlySpawning.remove(citizen.getId());
            return;
        }

        npc.second().setLeashPoint(citizen.getPosition());

        npc.second().setInventorySize(9, 30, 5);

        Ref<EntityStore> ref = npc.first();
        Store<EntityStore> store = ref.getStore();

        if (skinToUse != null) {
            PlayerSkinComponent skinComponent = new PlayerSkinComponent(skinToUse);
            store.putComponent(ref, PlayerSkinComponent.getComponentType(), skinComponent);
        }

        PersistentModel persistentModel = store.getComponent(ref, PersistentModel.getComponentType());
        if (persistentModel != null) {
            persistentModel.setModelReference(new Model.ModelReference(
                    playerModel.getModelAssetId(),
                    playerModel.getScale(),
                    playerModel.getRandomAttachmentIds(),
                    playerModel.getAnimationSetMap() == null
                    ));
        }

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());

        citizen.setNpcRef(ref);

        if (uuidComponent != null) {
            citizen.setSpawnedUUID(uuidComponent.getUuid());

            if (save)
                saveCitizen(citizen);
        }

        if (!citizen.isTakesDamage() || "PASSIVE".equals(citizen.getAttitude())) {
            store.addComponent(ref, Invulnerable.getComponentType());
        }

        applyHealthOverride(ref, citizen);

        setInteractionComponent(store, ref, citizen);
        applyNpcNameplateComponent(store, ref, citizen);
        updateCitizenNPCItems(citizen);
        triggerAnimations(citizen, "DEFAULT");
        String pluginPatrolPath = citizen.getPathConfig().getPluginPatrolPath();
        if (!pluginPatrolPath.isEmpty() && patrolManager != null) {
            patrolManager.startPatrol(citizen.getId(), pluginPatrolPath);
        }
        citizensCurrentlySpawning.remove(citizen.getId());
    }

    public void applyHealthOverride(@Nonnull Ref<EntityStore> entityRef, @Nonnull CitizenData citizen) {
        Store<EntityStore> entityStore = entityRef.getStore();
        EntityStatMap statMap = entityStore.getComponent(entityRef, EntityStatsModule.get().getEntityStatMapComponentType());
        if (statMap == null) {
            return;
        }

        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStat = statMap.get(healthIndex);

        if (!citizen.isOverrideHealth()) {
            if (healthStat != null) {
                statMap.removeModifier(healthIndex, "hycitizens_max_health");
            }
            return;
        }

        if (healthStat == null) {
            return;
        }

        float defaultMaxHealth = healthStat.getMax();
        float targetHealth = citizen.getHealthAmount();
        float difference = targetHealth - defaultMaxHealth;

        StaticModifier modifier = new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, difference);

        statMap.putModifier(healthIndex, "hycitizens_max_health", modifier);
        statMap.setStatValue(DefaultEntityStatTypes.getHealth(), targetHealth);
    }

    public void setInteractionComponent(Store<EntityStore> store, Ref<EntityStore> ref, CitizenData citizenData) {
        if (ref == null || !ref.isValid()) {
            HyCitizensPlugin.get().getLogger().atSevere().log("Unable to executes setInteractionComponent");
            return;
        }

        boolean shouldBeInteractable = citizenData.getForceFKeyInteractionText() || hasFKeyActions(citizenData);
        if (shouldBeInteractable) {
            store.putComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);
        } else {
            store.removeComponentIfExists(ref, Interactable.getComponentType());
        }
    }

    public boolean hasFKeyActions(@Nonnull CitizenData citizen) {
        for (CommandAction cmd : citizen.getCommandActions()) {
            String trigger = cmd.getInteractionTrigger();
            if (trigger == null || "BOTH".equals(trigger) || "F_KEY".equals(trigger)) {
                return true;
            }
        }

        for (CitizenMessage msg : citizen.getMessagesConfig().getMessages()) {
            String trigger = msg.getInteractionTrigger();
            if (trigger == null || "BOTH".equals(trigger) || "F_KEY".equals(trigger)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private List<String> getNonEmptyNametagLines(@Nonnull CitizenData citizen) {
        String name = citizen.getName();
        if (name == null || name.isEmpty()) {
            return Collections.emptyList();
        }

        String[] lines = name.replace("\\n", "\n").split("\\r?\\n");
        List<String> nonEmptyLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                nonEmptyLines.add(trimmed);
            }
        }

        return nonEmptyLines;
    }

    @Nullable
    private String getInlineNametagText(@Nonnull CitizenData citizen) {
        List<String> lines = getNonEmptyNametagLines(citizen);
        if (lines.size() != 1) {
            return null;
        }
        return lines.get(0);
    }

    public boolean shouldUseSeparateNametagEntities(@Nonnull CitizenData citizen) {
        if (citizen.isHideNametag()) {
            return false;
        }

        List<String> lines = getNonEmptyNametagLines(citizen);
        if (lines.isEmpty()) {
            return false;
        }

        if (citizen.isHideNpc()) {
            return true;
        }

        if (Math.abs(citizen.getNametagOffset()) > NAMETAG_OFFSET_EPSILON) {
            return true;
        }

        return lines.size() > 1;
    }

    private boolean shouldUseInlineNpcNameplate(@Nonnull CitizenData citizen) {
        if (citizen.isHideNametag() || citizen.isHideNpc()) {
            return false;
        }

        if (Math.abs(citizen.getNametagOffset()) > NAMETAG_OFFSET_EPSILON) {
            return false;
        }

        return getInlineNametagText(citizen) != null;
    }

    private void applyNpcNameplateComponent(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull CitizenData citizen) {
        String inlineText = shouldUseInlineNpcNameplate(citizen) ? getInlineNametagText(citizen) : null;
        if (inlineText == null) {
            store.removeComponentIfExists(ref, Nameplate.getComponentType());
            return;
        }

        store.putComponent(ref, Nameplate.getComponentType(), new Nameplate(inlineText));
    }

    public void refreshNpcNameplate(@Nonnull CitizenData citizen) {
        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        world.execute(() -> {
            Ref<EntityStore> npcRef = citizen.getNpcRef();
            if (npcRef == null || !npcRef.isValid()) {
                return;
            }

            applyNpcNameplateComponent(npcRef.getStore(), npcRef, citizen);
        });
    }

    public PlayerSkin determineSkin(CitizenData citizen) {
        if (citizen.isUseLiveSkin() && !citizen.getSkinUsername().isEmpty()) {
            updateCitizenSkin(citizen, true);
            return citizen.getCachedSkin();
        } else {
            return citizen.getCachedSkin();
        }
    }

    public void updateCitizenSkin(CitizenData citizen, boolean save) {
        if (!citizen.isPlayerModel() || citizen.getSkinUsername().isEmpty()) {
            return;
        }

        PlayerSkin cachedSkin = citizen.getCachedSkin();

        if (cachedSkin == null || citizen.isUseLiveSkin()) {
            SkinUtilities.getSkin(citizen.getSkinUsername()).thenAccept(skin -> {
                if (skin != null) {
                    citizen.setCachedSkin(skin);
                    citizen.setLastSkinUpdate(System.currentTimeMillis());

                    if (save) {
                        saveCitizen(citizen);
                    }

                    if (citizen.getSpawnedUUID() != null) {
                        World world = Universe.get().getWorld(citizen.getWorldUUID());
                        if (world != null) {
                            Ref<EntityStore> npcRef = world.getEntityRef(citizen.getSpawnedUUID());
                            if (npcRef != null && npcRef.isValid()) {
                                world.execute(() -> {
                                    PlayerSkinComponent skinComponent = new PlayerSkinComponent(skin);
                                    npcRef.getStore().putComponent(npcRef, PlayerSkinComponent.getComponentType(), skinComponent);

                                // Update model
                                float scale = Math.max((float) 0.01, citizen.getScale());
                                Model newModel = CosmeticsModule.get().createModel(skin, scale);
                                if (newModel != null) {
                                    ModelComponent modelComponent = new ModelComponent(newModel);
                                    npcRef.getStore().putComponent(npcRef, ModelComponent.getComponentType(), modelComponent);
                                }

                                PersistentModel persistentModel = npcRef.getStore().getComponent(npcRef, PersistentModel.getComponentType());
                                if (persistentModel != null && newModel != null) {
                                    persistentModel.setModelReference(new Model.ModelReference(
                                            newModel.getModelAssetId(),
                                            newModel.getScale(),
                                            newModel.getRandomAttachmentIds(),
                                            newModel.getAnimationSetMap() == null
                                    ));
                                } else if (newModel == null) {
                                    getLogger().atWarning().log("Failed to create skin model while updating skin for citizen '" + citizen.getId() + "'.");
                                }
                            });
                        }
                    }
                    }
                }
            });
        }
        else {
            if (citizen.getSpawnedUUID() != null) {
                World world = Universe.get().getWorld(citizen.getWorldUUID());
                if (world != null) {
                    Ref<EntityStore> npcRef = world.getEntityRef(citizen.getSpawnedUUID());
                    if (npcRef != null && npcRef.isValid()) {
                        world.execute(() -> {
                            // Update skin component
                            PlayerSkinComponent skinComponent = new PlayerSkinComponent(cachedSkin);
                            npcRef.getStore().putComponent(npcRef, PlayerSkinComponent.getComponentType(), skinComponent);

                            // Update model
                            float scale = Math.max((float) 0.01, citizen.getScale());
                            Model newModel = CosmeticsModule.get().createModel(cachedSkin, scale);
                            if (newModel != null) {
                                ModelComponent modelComponent = new ModelComponent(newModel);
                                npcRef.getStore().putComponent(npcRef, ModelComponent.getComponentType(), modelComponent);
                            }

                            PersistentModel persistentModel = npcRef.getStore().getComponent(npcRef, PersistentModel.getComponentType());
                            if (persistentModel != null && newModel != null) {
                                persistentModel.setModelReference(new Model.ModelReference(
                                        newModel.getModelAssetId(),
                                        newModel.getScale(),
                                        newModel.getRandomAttachmentIds(),
                                        newModel.getAnimationSetMap() == null
                                ));
                            } else if (newModel == null) {
                                getLogger().atWarning().log("Failed to create skin model while applying cached skin for citizen '" + citizen.getId() + "'.");
                            }
                        });
                    }
                }
            }
        }
    }

    public void applySkinPreview(CitizenData citizen, PlayerSkin skin) {
        if (citizen.getSpawnedUUID() != null) {
            World world = Universe.get().getWorld(citizen.getWorldUUID());
            if (world != null) {
                Ref<EntityStore> npcRef = world.getEntityRef(citizen.getSpawnedUUID());
                if (npcRef != null && npcRef.isValid()) {
                    world.execute(() -> {
                        PlayerSkinComponent skinComponent = new PlayerSkinComponent(skin);
                        npcRef.getStore().putComponent(npcRef, PlayerSkinComponent.getComponentType(), skinComponent);

                        float scale = Math.max((float) 0.01, citizen.getScale());
                        Model newModel = CosmeticsModule.get().createModel(skin, scale);
                        if (newModel != null) {
                            ModelComponent modelComponent = new ModelComponent(newModel);
                            npcRef.getStore().putComponent(npcRef, ModelComponent.getComponentType(), modelComponent);
                        }

                        PersistentModel persistentModel = npcRef.getStore().getComponent(npcRef, PersistentModel.getComponentType());
                        if (persistentModel != null && newModel != null) {
                            persistentModel.setModelReference(new Model.ModelReference(
                                    newModel.getModelAssetId(),
                                    newModel.getScale(),
                                    newModel.getRandomAttachmentIds(),
                                    newModel.getAnimationSetMap() == null
                            ));
                        } else if (newModel == null) {
                            getLogger().atWarning().log("Failed to create skin model while previewing skin for citizen '" + citizen.getId() + "'.");
                        }
                    });
                }
            }
        }
    }

    public void updateCitizenSkinFromPlayer(CitizenData citizen, PlayerRef playerRef, boolean save) {
        if (!citizen.isPlayerModel()) {
            return;
        }

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        PlayerSkinComponent playerSkinComp = entityRef.getStore().getComponent(entityRef, PlayerSkinComponent.getComponentType());
        if (playerSkinComp != null && playerSkinComp.getPlayerSkin() != null) {
            citizen.setCachedSkin(playerSkinComp.getPlayerSkin());
            citizen.setSkinUsername(""); // Clear username since we're using a custom skin
            citizen.setUseLiveSkin(false); // Disable live skin
            citizen.setLastSkinUpdate(System.currentTimeMillis());

            if (save) {
                saveCitizen(citizen);
            }

            updateSpawnedCitizenNPC(citizen, save);
        }
    }

    public void spawnCitizenHologram(CitizenData citizen, boolean save) {
        if (citizen.isHideNametag()) {
            refreshNpcNameplate(citizen);
            despawnCitizenHologram(citizen);
            if (save) {
                saveCitizen(citizen);
            }
            return;
        }

        if (!shouldUseSeparateNametagEntities(citizen)) {
            despawnCitizenHologram(citizen);
            refreshNpcNameplate(citizen);
            if (save) {
                saveCitizen(citizen);
            }
            return;
        }

        List<String> nametagLines = getNonEmptyNametagLines(citizen);
        if (nametagLines.isEmpty()) {
            refreshNpcNameplate(citizen);
            despawnCitizenHologram(citizen);
            if (save) {
                saveCitizen(citizen);
            }
            return;
        }

        // Separate nametag mode should never leave an NPC Nameplate component active.
        refreshNpcNameplate(citizen);

        if (!hologramsCurrentlySpawning.add(citizen.getId())) {
            return;
        }

        if (!citizen.getHologramLineUuids().isEmpty()) {
            despawnCitizenHologram(citizen);
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            hologramsCurrentlySpawning.remove(citizen.getId());
            getLogger().atWarning().log("Failed to spawn citizen hologram: " + citizen.getName() + ". Failed to find world. Try updating the citizen's position.");
            return;
        }

        long start = System.currentTimeMillis();
        ScheduledFuture<?>[] futureRef = new ScheduledFuture[1];
        boolean[] spawned = new boolean[]{false};
        boolean[] queued = new boolean[]{false};

        futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            if (spawned[0]) {
                futureRef[0].cancel(false);
                return;
            }

            long elapsedMs = System.currentTimeMillis() - start;
            if (elapsedMs >= 15000L) {
                hologramsCurrentlySpawning.remove(citizen.getId());
                futureRef[0].cancel(false);
                return;
            }

            if (!queued[0]) {
                queued[0] = true;
                world.execute(() -> {
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(citizen.getPosition().x, citizen.getPosition().z);
                    WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                    if (chunk == null) {
                        queued[0] = false;
                        return;
                    }

                    spawned[0] = true;
                    futureRef[0].cancel(false);

                    double scale = Math.max(0.01, citizen.getScale() + citizen.getNametagOffset());

                    double baseOffset = 1.65;
                    double extraPerScale = 0.40;

                    double yOffset = baseOffset * scale + (scale - 1.0) * extraPerScale;

                    Vector3d baseHologramPos = new Vector3d(
                            citizen.getPosition().x,
                            citizen.getPosition().y + yOffset,
                            citizen.getPosition().z
                    );

                    Vector3f hologramRot = new Vector3f(citizen.getRotation());

                    // Controls spacing between each nameplate line
                    double lineSpacing = 0.25;

                    for (int i = 0; i < nametagLines.size(); ++i) {
                        String lineText = nametagLines.get(i);
                        Vector3d linePos = new Vector3d(
                                baseHologramPos.x,
                                baseHologramPos.y + ((nametagLines.size() - 1 - i) * lineSpacing),
                                baseHologramPos.z
                        );

                        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

                        ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");
                        holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);

                        holder.putComponent(TransformComponent.getComponentType(), new TransformComponent(linePos, hologramRot));
                        holder.ensureComponent(UUIDComponent.getComponentType());

                        if (projectileComponent.getProjectile() == null) {
                            projectileComponent.initialize();
                            if (projectileComponent.getProjectile() == null) {
                                continue;
                            }
                        }

                        holder.addComponent(
                                NetworkId.getComponentType(),
                                new NetworkId(world.getEntityStore().getStore().getExternalData().takeNextNetworkId())
                        );

                        UUIDComponent hologramUUIDComponent = holder.getComponent(UUIDComponent.getComponentType());
                        if (hologramUUIDComponent != null) {
                            citizen.getHologramLineUuids().add(hologramUUIDComponent.getUuid());
                        }

                        holder.addComponent(Nameplate.getComponentType(), new Nameplate(lineText));
                        world.getEntityStore().getStore().addEntity(holder, AddReason.SPAWN);
                    }

                    if (save) {
                        saveCitizen(citizen);
                    }

                    hologramsCurrentlySpawning.remove(citizen.getId());
                });
            }
        }, 0L, 250L, TimeUnit.MILLISECONDS);
    }

    public void despawnCitizen(CitizenData citizen) {
        despawnCitizenNPC(citizen);
        despawnCitizenHologram(citizen);
    }

    public void despawnCitizenNPC(CitizenData citizen) {
        if (patrolManager != null) {
            patrolManager.onCitizenDespawned(citizen.getId());
        }
        citizensCurrentlySpawning.remove(citizen.getId());
        citizen.setAwaitingRespawn(false);

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        boolean despawned = false;
        Ref<EntityStore> npcRef = citizen.getNpcRef();
        if (npcRef != null && npcRef.isValid()) {
            despawned = true;
            world.execute(() -> {
                world.getEntityStore().getStore().removeEntity(npcRef, RemoveReason.REMOVE);
            });

            citizen.setSpawnedUUID(null);
            citizen.setNpcRef(null);
        }

        if (!despawned) {
            UUID npcUUID = citizen.getSpawnedUUID();
            if (npcUUID != null) {
                if (world.getEntityRef(npcUUID) != null) {
                    world.execute(() -> {
                        Ref<EntityStore> npc = world.getEntityRef(npcUUID);
                        if (npc == null) {
                            return;
                        }

                        world.getEntityStore().getStore().removeEntity(npc, RemoveReason.REMOVE);
                    });
                }

                citizen.setSpawnedUUID(null);
                citizen.setNpcRef(null);
            }
        }
    }

    private void despawnCitizenNPCForDeletion(@Nonnull CitizenData citizen) {
        if (patrolManager != null) {
            patrolManager.onCitizenDespawned(citizen.getId());
        }
        citizensCurrentlySpawning.remove(citizen.getId());
        citizen.setAwaitingRespawn(false);

        UUID npcUUID = citizen.getSpawnedUUID();
        Ref<EntityStore> npcRef = citizen.getNpcRef();
        World world = Universe.get().getWorld(citizen.getWorldUUID());

        if (world == null) {
            if (npcUUID != null) {
                queuePendingNpcRemoval(citizen, npcUUID);
            }
            citizen.setSpawnedUUID(null);
            citizen.setNpcRef(null);
            return;
        }

        if (npcRef != null && npcRef.isValid()) {
            world.execute(() -> world.getEntityStore().getStore().removeEntity(npcRef, RemoveReason.REMOVE));
            citizen.setSpawnedUUID(null);
            citizen.setNpcRef(null);
            return;
        }

        if (npcUUID != null) {
            Ref<EntityStore> resolvedNpcRef = world.getEntityRef(npcUUID);
            if (resolvedNpcRef != null && resolvedNpcRef.isValid()) {
                world.execute(() -> world.getEntityStore().getStore().removeEntity(resolvedNpcRef, RemoveReason.REMOVE));
            } else {
                queuePendingNpcRemoval(citizen, npcUUID);
            }

            citizen.setSpawnedUUID(null);
            citizen.setNpcRef(null);
        }
    }

    public void despawnCitizenHologram(CitizenData citizen) {
        hologramsCurrentlySpawning.remove(citizen.getId());
        if (citizen.getHologramLineUuids() == null || citizen.getHologramLineUuids().isEmpty()) {
            return;
        }

        List<UUID> hologramUuids = new ArrayList<>(citizen.getHologramLineUuids());
        citizen.getHologramLineUuids().clear();

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            queuePendingHologramRemovals(citizen, hologramUuids);
            return;
        }

        world.execute(() -> {
            List<UUID> unresolvedUuids = new ArrayList<>();
            for (UUID uuid : hologramUuids) {
                if (uuid == null) {
                    continue;
                }

                try {
                    Ref<EntityStore> hologram = world.getEntityRef(uuid);
                    if (hologram == null || !hologram.isValid()) {
                        unresolvedUuids.add(uuid);
                        continue;
                    }

                    world.getEntityStore().getStore().removeEntity(hologram, RemoveReason.REMOVE);
                } catch (Exception ignored) {
                    unresolvedUuids.add(uuid);
                }
            }

            if (!unresolvedUuids.isEmpty()) {
                queuePendingHologramRemovals(citizen, unresolvedUuids);
            }
        });
    }

    private long getCitizenChunkIndex(@Nonnull CitizenData citizen) {
        Vector3d chunkPosition = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        return ChunkUtil.indexChunkFromBlock(chunkPosition.x, chunkPosition.z);
    }

    private void queuePendingNpcRemoval(@Nonnull CitizenData citizen, @Nonnull UUID npcUuid) {
        UUID worldUUID = citizen.getWorldUUID();
        long chunkIndex = getCitizenChunkIndex(citizen);

        Map<UUID, PendingNpcRemoval> worldPending = pendingNpcRemovals.computeIfAbsent(worldUUID, ignored -> new ConcurrentHashMap<>());
        worldPending.put(npcUuid, new PendingNpcRemoval(chunkIndex));
    }

    private void queuePendingHologramRemovals(@Nonnull CitizenData citizen, @Nonnull Collection<UUID> hologramUuids) {
        if (hologramUuids.isEmpty()) {
            return;
        }

        UUID worldUUID = citizen.getWorldUUID();
        long chunkIndex = getCitizenChunkIndex(citizen);

        Map<UUID, PendingHologramRemoval> worldPending = pendingHologramRemovals.computeIfAbsent(worldUUID, ignored -> new ConcurrentHashMap<>());
        for (UUID uuid : hologramUuids) {
            if (uuid == null) {
                continue;
            }
            worldPending.put(uuid, new PendingHologramRemoval(chunkIndex));
        }
    }

    public void processPendingHologramRemovals(@Nonnull World world, long chunkIndex) {
        UUID worldUUID = world.getWorldConfig().getUuid();
        Map<UUID, PendingHologramRemoval> worldPending = pendingHologramRemovals.get(worldUUID);
        if (worldPending == null || worldPending.isEmpty()) {
            return;
        }

        List<UUID> uuidsInChunk = new ArrayList<>();
        for (Map.Entry<UUID, PendingHologramRemoval> entry : worldPending.entrySet()) {
            PendingHologramRemoval pendingRemoval = entry.getValue();
            if (pendingRemoval != null && pendingRemoval.chunkIndex == chunkIndex) {
                uuidsInChunk.add(entry.getKey());
            }
        }

        if (uuidsInChunk.isEmpty()) {
            return;
        }

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> world.execute(() -> {
            Map<UUID, PendingHologramRemoval> currentWorldPending = pendingHologramRemovals.get(worldUUID);
            if (currentWorldPending == null || currentWorldPending.isEmpty()) {
                return;
            }

            for (UUID uuid : uuidsInChunk) {
                PendingHologramRemoval pendingRemoval = currentWorldPending.get(uuid);
                if (pendingRemoval == null || pendingRemoval.chunkIndex != chunkIndex) {
                    continue;
                }

                boolean removed = false;
                try {
                    Ref<EntityStore> hologram = world.getEntityRef(uuid);
                    if (hologram != null && hologram.isValid()) {
                        world.getEntityStore().getStore().removeEntity(hologram, RemoveReason.REMOVE);
                        removed = true;
                    }
                } catch (Exception ignored) {
                }

                if (removed) {
                    currentWorldPending.remove(uuid);
                    continue;
                }

                pendingRemoval.attempts++;
                if (pendingRemoval.attempts >= MAX_PENDING_HOLOGRAM_REMOVAL_ATTEMPTS) {
                    currentWorldPending.remove(uuid);
                    getLogger().atWarning().log("Dropped pending hologram removal UUID '" + uuid + "' in world '" + worldUUID + "' after " + MAX_PENDING_HOLOGRAM_REMOVAL_ATTEMPTS + " attempts.");
                }
            }

            if (currentWorldPending.isEmpty()) {
                pendingHologramRemovals.remove(worldUUID);
            }
        }), 200, TimeUnit.MILLISECONDS);
    }

    public void processPendingNpcRemovals(@Nonnull World world, long chunkIndex) {
        UUID worldUUID = world.getWorldConfig().getUuid();
        Map<UUID, PendingNpcRemoval> worldPending = pendingNpcRemovals.get(worldUUID);
        if (worldPending == null || worldPending.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, PendingNpcRemoval> entry : worldPending.entrySet()) {
            PendingNpcRemoval pendingRemoval = entry.getValue();
            if (pendingRemoval == null || pendingRemoval.chunkIndex != chunkIndex) {
                continue;
            }

            UUID npcUuid = entry.getKey();
            String taskKey = worldUUID + ":" + npcUuid;
            if (!pendingNpcRemovalTasks.add(taskKey)) {
                continue;
            }

            final ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];
            futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> world.execute(() -> {
                Map<UUID, PendingNpcRemoval> currentWorldPending = pendingNpcRemovals.get(worldUUID);
                PendingNpcRemoval currentPending = currentWorldPending != null ? currentWorldPending.get(npcUuid) : null;
                if (currentPending == null || currentPending.chunkIndex != chunkIndex) {
                    if (futureRef[0] != null) {
                        futureRef[0].cancel(false);
                    }
                    pendingNpcRemovalTasks.remove(taskKey);
                    return;
                }

                boolean done = false;
                try {
                    Ref<EntityStore> npcRef = world.getEntityRef(npcUuid);
                    if (npcRef != null && npcRef.isValid()) {
                        world.getEntityStore().getStore().removeEntity(npcRef, RemoveReason.REMOVE);
                        done = true;
                    }
                } catch (Exception ignored) {
                }

                if (!done) {
                    currentPending.attempts++;
                    if (currentPending.attempts >= MAX_PENDING_NPC_REMOVAL_ATTEMPTS) {
                        done = true;
                    }
                }

                if (done) {
                    if (currentWorldPending != null) {
                        currentWorldPending.remove(npcUuid);
                        if (currentWorldPending.isEmpty()) {
                            pendingNpcRemovals.remove(worldUUID);
                        }
                    }

                    if (futureRef[0] != null) {
                        futureRef[0].cancel(false);
                    }
                    pendingNpcRemovalTasks.remove(taskKey);
                }
            }), 100, 250, TimeUnit.MILLISECONDS);
        }
    }

    public void updateSpawnedCitizen(CitizenData citizen, boolean save) {
        despawnCitizen(citizen);
        spawnCitizen(citizen, save);
    }

    public void updateSpawnedCitizenNPC(CitizenData citizen, boolean save) {
        despawnCitizenNPC(citizen);
        spawnCitizenNPC(citizen, save);
    }

    public void updateSpawnedCitizenHologram(CitizenData citizen, boolean save) {
        if (citizen.isHideNametag()) {
            refreshNpcNameplate(citizen);
            despawnCitizenHologram(citizen);
            if (save) {
                saveCitizen(citizen);
            }
            return;
        }

        if (!shouldUseSeparateNametagEntities(citizen)) {
            despawnCitizenHologram(citizen);
            refreshNpcNameplate(citizen);
            if (save) {
                saveCitizen(citizen);
            }
            return;
        }

        // Separate nametag mode should never leave an NPC Nameplate component active.
        refreshNpcNameplate(citizen);

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            getLogger().atWarning().log("Failed to update citizen hologram: " + citizen.getName() + ". Failed to find world.");
            if (save) {
                saveCitizen(citizen);
            }
            return;
        }

        List<String> nonEmptyLines = getNonEmptyNametagLines(citizen);
        if (nonEmptyLines.isEmpty()) {
            despawnCitizenHologram(citizen);
            if (save) {
                saveCitizen(citizen);
            }
            return;
        }

        List<UUID> existingUuids = citizen.getHologramLineUuids();
        int existingCount = existingUuids.size();
        int newCount = nonEmptyLines.size();

        // Calculate position parameters
        double scale = Math.max(0.01, citizen.getScale() + citizen.getNametagOffset());
        double baseOffset = 1.65;
        double extraPerScale = 0.40;
        double yOffset = baseOffset * scale + (scale - 1.0) * extraPerScale;
        double lineSpacing = 0.25;

        Vector3d baseHologramPos = new Vector3d(
                citizen.getPosition().x,
                citizen.getPosition().y + yOffset,
                citizen.getPosition().z
        );
        Vector3f hologramRot = new Vector3f(citizen.getRotation());

        world.execute(() -> {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(citizen.getPosition().x, citizen.getPosition().z);
            WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
            if (chunk == null) {
                // Chunk not loaded, just save it
                if (save) {
                    saveCitizen(citizen);
                }
                return;
            }

            // Update existing lines
            int linesToUpdate = Math.min(existingCount, newCount);
            for (int i = 0; i < linesToUpdate; i++) {
                UUID uuid = existingUuids.get(i);
                String lineText = nonEmptyLines.get(i);

                Ref<EntityStore> entity = world.getEntityRef(uuid);
                if (entity != null) {
                    // Update position
                    Vector3d linePos = new Vector3d(
                            baseHologramPos.x,
                            baseHologramPos.y + ((newCount - 1 - i) * lineSpacing),
                            baseHologramPos.z
                    );

                    TransformComponent transform = entity.getStore().getComponent(entity, TransformComponent.getComponentType());
                    if (transform != null) {
                        transform.setPosition(linePos);
                        transform.setRotation(hologramRot);
                    }

                    // Update text
                    Nameplate nameplate = entity.getStore().getComponent(entity, Nameplate.getComponentType());
                    if (nameplate != null) {
                        nameplate.setText(lineText);
                    }
                }
            }

            // Despawn extra lines if new text has fewer lines
            if (existingCount > newCount) {
                for (int i = newCount; i < existingCount; i++) {
                    UUID uuid = existingUuids.get(i);
                    Ref<EntityStore> entity = world.getEntityRef(uuid);
                    if (entity != null) {
                        world.getEntityStore().getStore().removeEntity(entity, RemoveReason.REMOVE);
                    }
                }
                // Remove the extra UUIDs from the list
                existingUuids.subList(newCount, existingCount).clear();
            }

            // Spawn new lines if needed
            if (newCount > existingCount) {
                for (int i = existingCount; i < newCount; i++) {
                    String lineText = nonEmptyLines.get(i);

                    Vector3d linePos = new Vector3d(
                            baseHologramPos.x,
                            baseHologramPos.y + ((newCount - 1 - i) * lineSpacing),
                            baseHologramPos.z
                    );

                    Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

                    ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");
                    holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);

                    holder.putComponent(TransformComponent.getComponentType(), new TransformComponent(linePos, hologramRot));
                    holder.ensureComponent(UUIDComponent.getComponentType());

                    if (projectileComponent.getProjectile() == null) {
                        projectileComponent.initialize();
                        if (projectileComponent.getProjectile() == null) {
                            continue;
                        }
                    }

                    holder.addComponent(
                            NetworkId.getComponentType(),
                            new NetworkId(world.getEntityStore().getStore().getExternalData().takeNextNetworkId())
                    );

                    UUIDComponent hologramUUIDComponent = holder.getComponent(UUIDComponent.getComponentType());
                    if (hologramUUIDComponent != null) {
                        existingUuids.add(hologramUUIDComponent.getUuid());
                    }

                    holder.addComponent(Nameplate.getComponentType(), new Nameplate(lineText));
                    world.getEntityStore().getStore().addEntity(holder, AddReason.SPAWN);
                }
            }

            if (save) {
                saveCitizen(citizen);
            }
        });
    }

    public void rotateCitizenToPlayer(CitizenData citizen, PlayerRef playerRef) {
        if (citizen == null || citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
            return;
        }

        if (citizen.getNpcRef().getStore() == null) {
            return;
        }

        NetworkId citizenNetworkId = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), NetworkId.getComponentType());
        if (citizenNetworkId != null) {
            TransformComponent npcTransformComponent = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), TransformComponent.getComponentType());
            if (npcTransformComponent == null) {
                return;
            }

            // Calculate rotation to look at player
            Vector3d entityPos = npcTransformComponent.getPosition();
            Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());

            double dx = playerPos.x - entityPos.x;
            double dz = playerPos.z - entityPos.z;

            // Flip the direction 180 degrees
            float yaw = (float) (Math.atan2(dx, dz) + Math.PI);

            double dy = playerPos.y - entityPos.y;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            float pitch = (float) Math.atan2(dy, horizontalDistance);

            // Create directions
            Direction lookDirection = new Direction(yaw, pitch, 0f);
            Direction bodyDirection = new Direction(yaw, 0f, 0f);

            // Don't rotate if the player barely moved
            UUID playerUUID = playerRef.getUuid();
            Direction lastLook = citizen.lastLookDirections.get(playerUUID);
            if (lastLook != null) {
                float yawThreshold = 0.02f;
                float pitchThreshold = 0.02f;
                float yawDiff = Math.abs(lookDirection.yaw - lastLook.yaw);
                float pitchDiff = Math.abs(lookDirection.pitch - lastLook.pitch);

                if (yawDiff < yawThreshold && pitchDiff < pitchThreshold) {
                    return;
                }
            }

            citizen.lastLookDirections.put(playerUUID, lookDirection);

            // Create ModelTransform
            ModelTransform transform = new ModelTransform();
            transform.lookOrientation = lookDirection;
            transform.bodyOrientation = bodyDirection;

            // Create TransformUpdate
            TransformUpdate update = new TransformUpdate(transform);

            // Create EntityUpdate
            EntityUpdate entityUpdate = new EntityUpdate(
                    citizenNetworkId.getId(),
                    null,
                    new ComponentUpdate[] { update }
            );

            // Send the packet
            EntityUpdates packet = new EntityUpdates(null, new EntityUpdate[] { entityUpdate });
            playerRef.getPacketHandler().write(packet);
        }
    }

    @Nullable
    public CitizenData getCitizen(@Nonnull String citizenId) {
        return citizens.get(citizenId);
    }

    @Nonnull
    public List<CitizenData> getAllCitizens() {
        return new ArrayList<>(citizens.values());
    }

    public int getCitizenCount() {
        return citizens.size();
    }

    public boolean citizenExists(@Nonnull String citizenId) {
        return citizens.containsKey(citizenId);
    }

    @Nonnull
    public List<CitizenData> getCitizensNear(@Nonnull Vector3d position, double maxDistance) {
        List<CitizenData> nearby = new ArrayList<>();
        double maxDistSq = maxDistance * maxDistance;

        for (CitizenData citizen : citizens.values()) {
            Vector3d citizenPos = citizen.getPosition();

            double dx = citizenPos.x - position.x;
            double dy = citizenPos.y - position.y;
            double dz = citizenPos.z - position.z;

            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq <= maxDistSq) {
                nearby.add(citizen);
            }
        }

        return nearby;
    }

    private void startAnimationScheduler() {
        animationTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();

            for (CitizenData citizen : citizens.values()) {
                if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null)
                    continue;

                for (AnimationBehavior ab : citizen.getAnimationBehaviors()) {
                    if (!"TIMED".equals(ab.getType()) && !"DEFAULT".equals(ab.getType()))
                        continue;

                    // DEFAULT animations loop every 2 seconds to keep them playing
                    if ("DEFAULT".equals(ab.getType())) {
                        String key = "default_" + ab.getAnimationName() + "_" + ab.getAnimationSlot();
                        long lastPlay = citizen.getLastTimedAnimationPlay().getOrDefault(key, 0L);
                        if (now - lastPlay >= 2000) {
                            citizen.getLastTimedAnimationPlay().put(key, now);
                            World world = Universe.get().getWorld(citizen.getWorldUUID());
                            if (world != null) {
                                world.execute(() -> {
                                    playAnimationForCitizen(citizen, ab.getAnimationName(), ab.getAnimationSlot(), ab);
                                });
                            }
                        }
                        continue;
                    }

                    String key = ab.getAnimationName() + "_" + ab.getAnimationSlot();
                    long lastPlay = citizen.getLastTimedAnimationPlay().getOrDefault(key, 0L);
                    long intervalMs = (long) (ab.getIntervalSeconds() * 1000);

                    if (now - lastPlay >= intervalMs) {
                        citizen.getLastTimedAnimationPlay().put(key, now);

                        World world = Universe.get().getWorld(citizen.getWorldUUID());
                        if (world != null) {
                            world.execute(() -> {
                                playAnimationForCitizen(citizen, ab.getAnimationName(), ab.getAnimationSlot(), ab);
                            });
                        }
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void playAnimationForCitizen(@Nonnull CitizenData citizen, @Nonnull String animName, int slot) {
        playAnimationForCitizen(citizen, animName, slot, null);
    }

    public void playAnimationForCitizen(@Nonnull CitizenData citizen, @Nonnull String animName, int slot, @Nullable AnimationBehavior behavior) {
        if (citizen.getNpcRef() == null || !citizen.getNpcRef().isValid())
            return;

        NPCEntity npc = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), NPCEntity.getComponentType());
        if (npc == null)
            return;

        AnimationSlot[] slots = AnimationSlot.values();
        if (slot < 0 || slot >= slots.length)
            slot = 0;

        AnimationUtils.playAnimation(npc.getReference(), slots[slot], animName, false, npc.getReference().getStore());

        // Handle stop-after-time logic
        String taskKey = animName + "_" + slot;

        // Cancel any existing stop task for this animation
        ScheduledFuture<?> existingTask = citizen.getAnimationStopTasks().get(taskKey);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
        }

        // Determine stop behavior
        boolean shouldStop = false;
        float stopTime = 3.0f;
        String stopAnimName = "Idle";

        if (behavior != null && behavior.isStopAfterTime()) {
            // Use configured stop settings
            shouldStop = true;
            stopTime = behavior.getStopTimeSeconds();

            // Determine which animation to play when stopping
            if (behavior.getStopAnimationName() != null && !behavior.getStopAnimationName().trim().isEmpty()) {
                stopAnimName = behavior.getStopAnimationName();
            } else {
                // Try to find DEFAULT animation
                stopAnimName = findDefaultAnimation(citizen, slot);
                if (stopAnimName == null) {
                    stopAnimName = "Idle";
                }
            }
        } else if (behavior == null || !"DEFAULT".equals(behavior.getType())) {
            // Legacy behavior: stop after 3 seconds for non-DEFAULT animations
            shouldStop = true;
        }

        if (shouldStop) {
            int finalSlot = slot;
            String finalStopAnimName = stopAnimName;
            ScheduledFuture<?> stopTask = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                if (npc.getReference() == null || !npc.getReference().isValid())
                    return;

                World world = Universe.get().getWorld(citizen.getWorldUUID());
                if (world == null)
                    return;

                world.execute(() -> {
                    AnimationUtils.playAnimation(npc.getReference(), slots[finalSlot], finalStopAnimName, false, npc.getReference().getStore());
                });

                // Remove from tracking map
                citizen.getAnimationStopTasks().remove(taskKey);

            }, (long)(stopTime * 1000), TimeUnit.MILLISECONDS);

            // Track the stop task
            citizen.getAnimationStopTasks().put(taskKey, stopTask);
        }
    }

    @Nullable
    private String findDefaultAnimation(@Nonnull CitizenData citizen, int slot) {
        for (AnimationBehavior ab : citizen.getAnimationBehaviors()) {
            if ("DEFAULT".equals(ab.getType()) && ab.getAnimationSlot() == slot) {
                return ab.getAnimationName();
            }
        }
        return null;
    }

    public void triggerAnimations(@Nonnull CitizenData citizen, @Nonnull String type) {
        if (citizen.getNpcRef() == null || !citizen.getNpcRef().isValid())
            return;

        for (AnimationBehavior ab : citizen.getAnimationBehaviors()) {
            if (ab.getType().equals(type)) {
                World world = Universe.get().getWorld(citizen.getWorldUUID());
                if (world != null) {
                    world.execute(() -> {
                        playAnimationForCitizen(citizen, ab.getAnimationName(), ab.getAnimationSlot(), ab);
                    });
                }
            }
        }
    }

    private void checkProximityAnimations(@Nonnull CitizenData citizen, @Nonnull PlayerRef playerRef, double distanceSq) {
        UUID playerUUID = playerRef.getUuid();

        for (AnimationBehavior ab : citizen.getAnimationBehaviors()) {
            if (!"ON_PROXIMITY_ENTER".equals(ab.getType()) && !"ON_PROXIMITY_EXIT".equals(ab.getType()))
                continue;

            float range = ab.getProximityRange();
            double rangeSq = range * range;
            //String key = citizen.getId() + "_" + playerUUID;
            boolean wasInRange = citizen.getPlayersInProximity().getOrDefault(playerUUID, false);
            boolean isInRange = distanceSq <= rangeSq;

            if (isInRange && !wasInRange) {
                citizen.getPlayersInProximity().put(playerUUID, true);
                if ("ON_PROXIMITY_ENTER".equals(ab.getType())) {
                    playAnimationForCitizen(citizen, ab.getAnimationName(), ab.getAnimationSlot(), ab);
                }
            } else if (!isInRange && wasInRange) {
                citizen.getPlayersInProximity().put(playerUUID, false);
                if ("ON_PROXIMITY_EXIT".equals(ab.getType())) {
                    playAnimationForCitizen(citizen, ab.getAnimationName(), ab.getAnimationSlot(), ab);
                }
            }
        }
    }

    @Nonnull
    private String resolveRoleName(@Nonnull CitizenData citizen) {
        // Generate/update the role file
        String generatedRoleName = roleGenerator.generateRole(citizen);

        // With hot-reload, generated roles are indexed automatically
        int roleIndex = NPCPlugin.get().getIndex(generatedRoleName);
        if (roleIndex != Integer.MIN_VALUE) {
            return generatedRoleName;
        }

        // Fall back to static role if not yet registered
        String fallbackName = roleGenerator.getFallbackRoleName(citizen);
        getLogger().atInfo().log("Generated role '" + generatedRoleName + "' not yet registered, using fallback '" + fallbackName + "'. Will retry applying generated role in 5 seconds.");

        // Schedule a delayed retry to apply the generated role once it's been indexed
        scheduleRoleRetry(citizen, generatedRoleName);

        return fallbackName;
    }

    private void scheduleRoleRetry(@Nonnull CitizenData citizen, @Nonnull String generatedRoleName) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                int roleIndex = NPCPlugin.get().getIndex(generatedRoleName);
                if (roleIndex == Integer.MIN_VALUE) {
                    getLogger().atWarning().log("Generated role '" + generatedRoleName + "' still not registered after retry. Role may have failed to generate.");
                    return;
                }

                if (citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
                    getLogger().atWarning().log("Cannot apply role '" + generatedRoleName + "': NPC ref is no longer valid.");
                    return;
                }

                World world = Universe.get().getWorld(citizen.getWorldUUID());
                if (world == null) {
                    return;
                }

                world.execute(() -> {
                    despawnCitizenNPC(citizen);
                    spawnCitizenNPC(citizen, false);
                    getLogger().atInfo().log("Successfully applied generated role '" + generatedRoleName + "' to citizen '" + citizen.getName() + "'.");
                });
            } catch (Exception e) {
                getLogger().atWarning().log("Error during role retry for '" + generatedRoleName + "': " + e.getMessage());
            }
        }, 5, TimeUnit.SECONDS);
    }

    public RoleGenerator getRoleGenerator() {
        return roleGenerator;
    }

    public void autoResolveAttackType(@Nonnull CitizenData citizen) {
        String resolved = RoleGenerator.resolveAttackInteraction(citizen.getModelId());
        citizen.getCombatConfig().setAttackType(resolved);
    }

    public void forceAttackEntity(@Nonnull CitizenData citizen, @Nonnull String attackInteractionId) {
        if (citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) return;

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) return;

        world.execute(() -> {
            try {
                NPCEntity npcEntity = citizen.getNpcRef().getStore().getComponent(
                        citizen.getNpcRef(), NPCEntity.getComponentType());
                if (npcEntity == null || npcEntity.getRole() == null) return;

                var combatSupport = npcEntity.getRole().getCombatSupport();
                if (combatSupport == null) return;

                combatSupport.clearAttackOverrides();
                combatSupport.addAttackOverride(attackInteractionId);
            } catch (Exception e) {
                getLogger().atWarning().log("Failed to force attack for citizen " + citizen.getId() + ": " + e.getMessage());
            }
        });
    }

    public void setCitizenCombatConfig(@Nonnull String citizenId, @Nonnull CombatConfig combatConfig) {
        CitizenData citizen = citizens.get(citizenId);
        if (citizen == null) return;
        citizen.setCombatConfig(combatConfig);
        saveCitizen(citizen);
        updateSpawnedCitizenNPC(citizen, true);
    }

    public void setCitizenDetectionConfig(@Nonnull String citizenId, @Nonnull DetectionConfig detectionConfig) {
        CitizenData citizen = citizens.get(citizenId);
        if (citizen == null) return;
        citizen.setDetectionConfig(detectionConfig);
        saveCitizen(citizen);
        updateSpawnedCitizenNPC(citizen, true);
    }

    public void setCitizenPathConfig(@Nonnull String citizenId, @Nonnull PathConfig pathConfig) {
        CitizenData citizen = citizens.get(citizenId);
        if (citizen == null) return;
        citizen.setPathConfig(pathConfig);
        saveCitizen(citizen);
        updateSpawnedCitizenNPC(citizen, true);
    }

    public void addCitizenInteractListener(CitizenInteractListener listener) {
        interactListeners.add(listener);
    }

    public void removeCitizenInteractListener(CitizenInteractListener listener) {
        interactListeners.remove(listener);
    }

    public void fireCitizenInteractEvent(CitizenInteractEvent event) {
        for (CitizenInteractListener listener : interactListeners) {
            listener.onCitizenInteract(event);
            if (event.isCancelled()) {
                break; // Stop notifying others if canceled
            }
        }
    }

    public void addCitizenDeathListener(CitizenDeathListener listener) {
        deathListeners.add(listener);
    }

    public void removeCitizenDeathListener(CitizenDeathListener listener) {
        deathListeners.remove(listener);
    }

    public void fireCitizenDeathEvent(CitizenDeathEvent event) {
        for (CitizenDeathListener listener : deathListeners) {
            listener.onCitizenDeath(event);
            if (event.isCancelled()) {
                break;
            }
        }
    }

//    public void reload() {
//        config.reload();
//        loadAllCitizens();
//    }

    private void saveGroups() {
        List<String> groupList = new ArrayList<>(groups);
        Collections.sort(groupList);
        config.setStringList("groups", groupList);
    }

    @Nonnull
    public List<String> getAllGroups() {
        List<String> groupList = new ArrayList<>(groups);
        Collections.sort(groupList);
        return groupList;
    }

    public void createGroup(@Nonnull String groupName) {
        if (!groupName.isEmpty()) {
            groups.add(groupName);
            saveGroups();
        }
    }

    public void deleteGroup(@Nonnull String groupName) {
        groups.remove(groupName);

        // Remove group from all citizens that have it
        for (CitizenData citizen : citizens.values()) {
            if (groupName.equals(citizen.getGroup())) {
                citizen.setGroup("");
                saveCitizen(citizen);
            }
        }

        saveGroups();
    }

    public boolean groupExists(@Nonnull String groupName) {
        return groups.contains(groupName);
    }

    @Nonnull
    public List<CitizenData> getCitizensByGroup(@Nullable String groupName) {
        String targetGroup = groupName != null ? groupName : "";
        return citizens.values().stream()
                .filter(c -> targetGroup.equals(c.getGroup()))
                .collect(Collectors.toList());
    }

    @Nonnull
    public PatrolManager getPatrolManager() {
        return patrolManager;
    }

    public void startCitizenPatrol(@Nonnull String citizenId, @Nonnull String pathName) {
        patrolManager.startPatrol(citizenId, pathName);
    }

    public void stopCitizenPatrol(@Nonnull String citizenId) {
        patrolManager.stopPatrol(citizenId);
    }

    public void moveCitizenToPosition(@Nonnull String citizenId, @Nonnull Vector3d position) {
        patrolManager.moveCitizenToPosition(citizenId, position);
    }

    public void stopCitizenMovement(@Nonnull String citizenId) {
        patrolManager.stopMoving(citizenId);
    }

    public boolean isCitizenPatrolling(@Nonnull String citizenId) {
        return patrolManager.isPatrolling(citizenId);
    }

    @Nullable
    public String getCitizenActivePatrolPath(@Nonnull String citizenId) {
        return patrolManager.getActivePatrolPath(citizenId);
    }
}
