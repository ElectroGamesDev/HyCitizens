package com.electro.hycitizens.managers;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenInteractEvent;
import com.electro.hycitizens.events.CitizenInteractListener;
import com.electro.hycitizens.models.*;
import com.electro.hycitizens.util.ConfigManager;
import com.electro.hycitizens.util.SkinUtilities;
import com.hypixel.hytale.assetstore.AssetLoadResult;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.common.util.RandomUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;
import org.bouncycastle.math.raw.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class CitizensManager {
    private final HyCitizensPlugin plugin;
    private final ConfigManager config;
    private final Map<String, CitizenData> citizens;
    private final List<CitizenInteractListener> interactListeners = new ArrayList<>();
    private ScheduledFuture<?> skinUpdateTask;
    private ScheduledFuture<?> rotateTask;
    private ScheduledFuture<?> nametagMoveTask;
    private ScheduledFuture<?> animationTask;
    private final Map<UUID, List<CitizenData>> citizensByWorld = new HashMap<>();
    private final Set<String> groups = new HashSet<>();
    private final Set<String> registeredNoLoopAnimations = ConcurrentHashMap.newKeySet();

    public CitizensManager(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.citizens = new ConcurrentHashMap<>();

        loadAllCitizens();
        startSkinUpdateScheduler();
        startRotateScheduler();
        startCitizensByWorldScheduler();
        startAnimationScheduler();
        startNametagMoveScheduler();
    }

    private void startSkinUpdateScheduler() {
        skinUpdateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            long thirtyMinutes = 30 * 60 * 1000;

            for (CitizenData citizen : citizens.values()) {
                if (citizen.isPlayerModel() && citizen.isUseLiveSkin() && !citizen.getSkinUsername().isEmpty()) {
                    long timeSinceLastUpdate = currentTime - citizen.getLastSkinUpdate();

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

                        if (!citizen.getRotateTowardsPlayer())
                            continue;

                        // Only look at player if idle
                        if (!citizen.getMovementBehavior().getType().equals("IDLE"))
                            continue;

                        long chunkIndex = ChunkUtil.indexChunkFromBlock(citizen.getPosition().x, citizen.getPosition().z);
                        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                        if (chunk == null)
                            continue;

                        for (PlayerRef playerRef : players) {
                            float maxDistance = 25.0f;
                            float maxDistanceSq = maxDistance * maxDistance;

                            double dx = playerRef.getTransform().getPosition().x - citizen.getPosition().x;
                            double dz = playerRef.getTransform().getPosition().z - citizen.getPosition().z;

                            double distSq = dx * dx + dz * dz;

                            if (distSq > maxDistanceSq) {
                                continue;
                            }

                            // Check proximity animations
                            if (!citizen.getAnimationBehaviors().isEmpty()) {
                                checkProximityAnimations(citizen, playerRef, distSq);
                            }

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

                        long chunkIndex = ChunkUtil.indexChunkFromBlock(citizen.getPosition().x, citizen.getPosition().z);
                        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                        if (chunk == null)
                            continue;

                        TransformComponent npcTransformComponent = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), TransformComponent.getComponentType());
                        if (npcTransformComponent == null) {
                            continue;
                        }

                        Vector3d npcPosition = npcTransformComponent.getPosition();

                        // Todo: It would be better to store the nametags as a ref

                        int totalLines = citizen.getHologramLineUuids().size();
                        if (totalLines == 0) {
                            return;
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
                if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid())
                    continue;

                UUID worldUUID = citizen.getWorldUUID();
                tmp.computeIfAbsent(worldUUID, k -> new ArrayList<>()).add(citizen);
            }

            synchronized (citizensByWorld) {
                citizensByWorld.clear();
                citizensByWorld.putAll(tmp);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
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

            if (command != null) {
                actions.add(new CommandAction(command, runAsServer));
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
        citizenData.setFKeyInteractionEnabled(config.getBoolean(basePath + ".f-key-interaction", false));

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
        float walkSpeed = config.getFloat(basePath + ".movement.walk-speed", 1.0f);
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
            String msg = config.getString(basePath + ".messages." + i + ".message", "");
            messages.add(new CitizenMessage(msg));
        }
        citizenData.setMessagesConfig(new MessagesConfig(messages, msgMode, msgEnabled));

        // Load attitude and damage settings
        citizenData.setAttitude(config.getString(basePath + ".attitude", "PASSIVE"));
        citizenData.setTakesDamage(config.getBoolean(basePath + ".takes-damage", false));

        // Load respawn settings
        citizenData.setRespawnOnDeath(config.getBoolean(basePath + ".respawn-on-death", true));
        citizenData.setRespawnDelaySeconds(config.getFloat(basePath + ".respawn-delay", 5.0f));

        // Load group (backwards compatible - defaults to empty string)
        citizenData.setGroup(config.getString(basePath + ".group", ""));

        return citizenData;
    }

    public void saveCitizen(@Nonnull CitizenData citizen) {
        config.beginBatch();

        try {
            String basePath = "citizens." + citizen.getId();

            config.set(basePath + ".name", citizen.getName());
            config.set(basePath + ".model-id", citizen.getModelId());
            config.set(basePath + ".model-world-uuid", citizen.getWorldUUID().toString());
            config.setVector3d(basePath + ".position", citizen.getPosition());
            config.setVector3f(basePath + ".rotation", citizen.getRotation());
            config.set(basePath + ".scale", citizen.getScale());
            config.set(basePath + ".permission", citizen.getRequiredPermission());
            config.set(basePath + ".permission-message", citizen.getNoPermissionMessage());
            config.set(basePath + ".rotate-towards-player", citizen.getRotateTowardsPlayer());
            config.set(basePath + ".f-key-interaction", citizen.getFKeyInteractionEnabled());
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
            }

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
                config.set(basePath + ".messages." + i + ".message", msgs.get(i).getMessage());
            }

            // Save attitude and damage settings
            config.set(basePath + ".attitude", citizen.getAttitude());
            config.set(basePath + ".takes-damage", citizen.isTakesDamage());

            // Save respawn settings
            config.set(basePath + ".respawn-on-death", citizen.isRespawnOnDeath());
            config.set(basePath + ".respawn-delay", citizen.getRespawnDelaySeconds());

            // Save group
            config.set(basePath + ".group", citizen.getGroup());

            // Add group to groups set if not empty
            if (!citizen.getGroup().isEmpty()) {
                groups.add(citizen.getGroup());
                saveGroups();
            }
        } finally {
            // Always end batch, even if an exception occurs
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
            npcEntity.getInventory().getUtility().setItemStackForSlot((short) 0, new ItemStack(citizen.getNpcHand()));
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

        // Update health after applying armor
        if (npcEntity.getReference() != null && statMap != null) {
            EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
            if (healthValue != null) {
                float healthDifference = healthValue.getMax() - maxHealth;
                statMap.setStatValue(DefaultEntityStatTypes.getHealth(), healthValue.get() + healthDifference);
            }
        }
    }

    public void removeCitizen(@Nonnull String citizenId) {
        CitizenData citizen = citizens.remove(citizenId);

        config.set("citizens." + citizenId, null);

        despawnCitizen(citizen);
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
                spawnCitizenHologram(citizen, save);
            });

        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    public void spawnCitizenNPC(CitizenData citizen, boolean save) {
        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
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
            getLogger().atWarning().log("Failed to spawn citizen NPC: " + citizen.getName() + ". The model ID is invalid. Try updating the model ID.");
            return;
        }

        String roleName = getRoleName(citizen);

        Pair<Ref<EntityStore>, NPCEntity> npc = NPCPlugin.get().spawnEntity(
                world.getEntityStore().getStore(),
                NPCPlugin.get().getIndex(roleName),
                citizen.getPosition(),
                citizen.getRotation(),
                citizenModel,
                null,
                null
        );

        if (npc == null)
            return;

        npc.second().setLeashPoint(citizen.getPosition());

        npc.second().setInventorySize(9, 30, 5);

        Ref<EntityStore> ref = npc.second().getReference();
        Store<EntityStore> store = npc.first().getStore();

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());

        citizen.setNpcRef(ref);

        if (uuidComponent != null) {
            citizen.setSpawnedUUID(uuidComponent.getUuid());

            if (save)
                saveCitizen(citizen);
        }

        updateCitizenNPCItems(citizen);
        triggerAnimations(citizen, "DEFAULT");
    }

    public void spawnPlayerModelNPC(CitizenData citizen, World world, boolean save) {
        if (citizen.getSpawnedUUID() != null || citizen.getNpcRef() != null) {
            despawnCitizenNPC(citizen);
        }

        PlayerSkin skinToUse = determineSkin(citizen);

        if (skinToUse == null) {
            skinToUse = SkinUtilities.createDefaultSkin();
        }

        float scale = Math.max((float)0.01, citizen.getScale());
        //Model playerModel = CosmeticsModule.get().createModel(skinToUse, scale);
        Model playerModel = CosmeticsModule.get().createModel(skinToUse, scale);
        //Map<String, String> randomAttachmentIds = new HashMap<>();
        //Model playerModel = new Model.ModelReference("PlayerTestModel_V", scale, randomAttachmentIds).toModel();

        if (playerModel == null) {
            getLogger().atWarning().log("Failed to create player model for citizen: " + citizen.getName());
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(citizen.getPosition().x, citizen.getPosition().z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            getLogger().atInfo().log("Failed to spawn player model for citizen NPC: " + citizen.getName() + ". The world chunk is unloaded.");
        }

        String roleName = getRoleName(citizen);

        Pair<Ref<EntityStore>, NPCEntity> npc = NPCPlugin.get().spawnEntity(
                world.getEntityStore().getStore(),
                NPCPlugin.get().getIndex(roleName),
                citizen.getPosition(),
                citizen.getRotation(),
                playerModel,
                null,
                null
        );

        if (npc == null)
            return;

        npc.second().setLeashPoint(citizen.getPosition());

        npc.second().setInventorySize(9, 30, 5);

        // Apply skin component
        PlayerSkinComponent skinComponent = new PlayerSkinComponent(skinToUse);
        npc.first().getStore().putComponent(npc.second().getReference(), PlayerSkinComponent.getComponentType(), skinComponent);

        PersistentModel persistentModel = npc.first().getStore().getComponent(npc.second().getReference(), PersistentModel.getComponentType());
        if (persistentModel != null) {
            persistentModel.setModelReference(new Model.ModelReference(
                    playerModel.getModelAssetId(),
                    playerModel.getScale(),
                    playerModel.getRandomAttachmentIds(),
                    playerModel.getAnimationSetMap() == null
                    ));
        }

        UUIDComponent uuidComponent = npc.first().getStore().getComponent(
                npc.second().getReference(),
                UUIDComponent.getComponentType()
        );

        citizen.setNpcRef(npc.first());

        if (uuidComponent != null) {
            citizen.setSpawnedUUID(uuidComponent.getUuid());

            if (save)
                saveCitizen(citizen);
        }

        updateCitizenNPCItems(citizen);
        triggerAnimations(citizen, "DEFAULT");
    }

    public PlayerSkin determineSkin(CitizenData citizen) {
        if (citizen.isUseLiveSkin() && !citizen.getSkinUsername().isEmpty()) {
            // Fetch live skin asynchronously, but for now return cached or default
            updateCitizenSkin(citizen, true);
            return citizen.getCachedSkin() != null ? citizen.getCachedSkin() : SkinUtilities.createDefaultSkin();
        } else {
            return citizen.getCachedSkin() != null ? citizen.getCachedSkin() : SkinUtilities.createDefaultSkin();
        }
    }

    public void updateCitizenSkin(CitizenData citizen, boolean save) {
        if (!citizen.isPlayerModel() || citizen.getSkinUsername().isEmpty()) {
            return;
        }

        PlayerSkin cachedSkin = citizen.getCachedSkin();

        if (cachedSkin == null || citizen.isUseLiveSkin()) {
            SkinUtilities.getSkin(citizen.getSkinUsername()).thenAccept(skin -> {
                citizen.setCachedSkin(skin);
                citizen.setLastSkinUpdate(System.currentTimeMillis());

                if (save) {
                    saveCitizen(citizen);
                }

                // Update the spawned NPC if it exists
                if (citizen.getSpawnedUUID() != null) {
                    World world = Universe.get().getWorld(citizen.getWorldUUID());
                    if (world != null) {
                        Ref<EntityStore> npcRef = world.getEntityRef(citizen.getSpawnedUUID());
                        if (npcRef != null && npcRef.isValid()) {
                            world.execute(() -> {
                                // Update skin component
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
                                if (persistentModel != null) {
                                    persistentModel.setModelReference(new Model.ModelReference(
                                            newModel.getModelAssetId(),
                                            newModel.getScale(),
                                            newModel.getRandomAttachmentIds(),
                                            newModel.getAnimationSetMap() == null
                                    ));
                                }
                            });
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
                            if (persistentModel != null) {
                                persistentModel.setModelReference(new Model.ModelReference(
                                        newModel.getModelAssetId(),
                                        newModel.getScale(),
                                        newModel.getRandomAttachmentIds(),
                                        newModel.getAnimationSetMap() == null
                                ));
                            }
                        });
                    }
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
        if (!citizen.getHologramLineUuids().isEmpty()) {
            despawnCitizenHologram(citizen);
        }

        if (citizen.isHideNametag()) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
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

                    // Actual spawning logic
                    String name = citizen.getName();
                    if (name == null || name.isEmpty()) {
                        return;
                    }

                    name = name.replace("\\n", "\n");

                    String[] lines = name.split("\\r?\\n");

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

                    for (int i = 0; i < lines.length; ++i) {
                        String lineText = lines[i].trim();
                        if (lineText.isEmpty()) {
                            continue;
                        }

                        Vector3d linePos = new Vector3d(
                                baseHologramPos.x,
                                baseHologramPos.y + ((lines.length - 1 - i) * lineSpacing),
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
                });
            }
        }, 0L, 250L, TimeUnit.MILLISECONDS);
    }

    public void despawnCitizen(CitizenData citizen) {
        despawnCitizenNPC(citizen);
        despawnCitizenHologram(citizen);
    }

    public void despawnCitizenNPC(CitizenData citizen) {
        // Prevent respawn scheduler from re-spawning this NPC
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

                    citizen.setSpawnedUUID(null);
                    citizen.setNpcRef(null);
                }
            }
        }
    }

    public void despawnCitizenHologram(CitizenData citizen) {
        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        if (citizen.getHologramLineUuids() == null || citizen.getHologramLineUuids().isEmpty()) {
            return;
        }

        List<UUID> hologramUuids = new ArrayList<>(citizen.getHologramLineUuids());

        citizen.getHologramLineUuids().clear();

        world.execute(() -> {
            for (UUID uuid : hologramUuids) {
                try {
                    Ref<EntityStore> hologram = world.getEntityRef(uuid);
                    if (hologram == null) {
                        continue;
                    }

                    world.getEntityStore().getStore().removeEntity(hologram, RemoveReason.REMOVE);
                } catch (Exception ignored) {
                }
            }
        });
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
        despawnCitizenHologram(citizen);
        spawnCitizenHologram(citizen, save);
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

            // Create ComponentUpdate
            ComponentUpdate update = new ComponentUpdate();
            update.type = ComponentUpdateType.Transform;
            update.transform = transform;

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
            String key = citizen.getId() + "_" + playerUUID;
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
    private String getRoleName(@Nonnull CitizenData citizen) {
        String moveType = citizen.getMovementBehavior().getType();
        boolean interactable = citizen.getFKeyInteractionEnabled();
        String attitude = citizen.getAttitude();
        boolean isWander = "WANDER".equals(moveType) || "WANDER_CIRCLE".equals(moveType) || "WANDER_RECT".equals(moveType);

        if (isWander) {
            int radius = getEffectiveRadius(citizen);
            String base = switch (attitude) {
                case "NEUTRAL" -> "Citizen_Wander_Neutral_R" + radius;
                case "AGGRESSIVE" -> "Citizen_Wander_Aggressive_R" + radius;
                default -> "Citizen_Wander_Passive_R" + radius;
            };
            return interactable ? base + "_Interactable_Role" : base + "_Role";
        } else {
            // IDLE movement
//            return switch (attitude) {
//                case "NEUTRAL" -> interactable ? "Citizen_Idle_Neutral_Interactable_Role" : "Citizen_Idle_Neutral_Role";
//                case "AGGRESSIVE" -> interactable ? "Citizen_Idle_Aggressive_Interactable_Role" : "Citizen_Idle_Aggressive_Role";
//                default -> interactable ? "Citizen_Interactable_Role" : "Citizen_Role";
//            };
            return interactable ? "Citizen_Interactable_Role" : "Citizen_Role";
        }
    }

    private int getEffectiveRadius(@Nonnull CitizenData citizen) {
        float radius = citizen.getMovementBehavior().getWanderRadius();
        // Snap to nearest supported radius: 0, 2, 5, 10, 15
        if (radius <= 1) return 0;
        if (radius <= 3) return 2;
        if (radius <= 7) return 5;
        if (radius <= 12) return 10;
        return 15;
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

    public void reload() {
        config.reload();
        loadAllCitizens();
    }

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
}
