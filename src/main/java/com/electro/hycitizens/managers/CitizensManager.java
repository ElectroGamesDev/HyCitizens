package com.electro.hycitizens.managers;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenInteractEvent;
import com.electro.hycitizens.events.CitizenInteractListener;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.CommandAction;
import com.electro.hycitizens.util.ConfigManager;
import com.electro.hycitizens.util.SkinUtilities;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
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

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class CitizensManager {
    private final HyCitizensPlugin plugin;
    private final ConfigManager config;
    private final Map<String, CitizenData> citizens;
    private final List<CitizenInteractListener> interactListeners = new ArrayList<>();
    private ScheduledFuture<?> skinUpdateTask;
    private ScheduledFuture<?> rotateTask;
    private final Map<UUID, List<CitizenData>> citizensByWorld = new HashMap<>();

    public CitizensManager(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.citizens = new ConcurrentHashMap<>();

        loadAllCitizens();
        startSkinUpdateScheduler();
        startRotateScheduler();
        startCitizensByWorldScheduler();
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
                        if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null)
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

                            if ((dx * dx + dz * dz) > maxDistanceSq)
                                continue;

                            rotateCitizenToPlayer(citizen, playerRef);
                        }
                    }
                });
            }
        }, 0, 60, TimeUnit.MILLISECONDS);
    }

    private void startCitizensByWorldScheduler() {
        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            Map<UUID, List<CitizenData>> tmp = new HashMap<>();

            for (CitizenData citizen : citizens.values()) {
                if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null)
                    continue;

                if (!citizen.getRotateTowardsPlayer())
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
    }

    private void loadAllCitizens() {
        citizens.clear();

        Map<String, Object> allData = config.getAll();

        for (String key : allData.keySet()) {
            if (!key.startsWith("citizens.")) {
                continue;
            }

            // citizens.<id>.<field>
            String[] parts = key.split("\\.");

            if (parts.length < 3) {
                continue;
            }

            String citizenId = parts[1];

            // only load once per citizen
            if (citizens.containsKey(citizenId)) {
                continue;
            }

            CitizenData citizen = loadCitizen(citizenId);
            if (citizen != null) {
                citizens.put(citizenId, citizen);
            }
        }
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
        UUID hologramUUID = config.getUUID(basePath + ".hologram-uuid");

        boolean rotateTowardsPlayer = config.getBoolean(basePath + ".rotate-towards-player", false);

        // Load skin data
        boolean isPlayerModel = config.getBoolean(basePath + ".is-player-model", false);
        boolean useLiveSkin = config.getBoolean(basePath + ".use-live-skin", false);
        String skinUsername = config.getString(basePath + ".skin-username", "");
        PlayerSkin cachedSkin = config.getPlayerSkin(basePath + ".cached-skin");
        long lastSkinUpdate = config.getLong(basePath + ".last-skin-update", 0L);

        CitizenData citizenData = new CitizenData(citizenId, name, modelId, worldUUID, position, rotation, scale, npcUUID, hologramUUID,
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
        citizenData.setNametagOffset(config.getFloat(basePath + ".nametag-offset", 0));

        return citizenData;
    }

    public void saveCitizen(@Nonnull CitizenData citizen) {
        String basePath = "citizens." + citizen.getId();

        config.set(basePath + ".name", citizen.getName());
        config.set(basePath + ".model-id", citizen.getModelId());
        config.set(basePath + ".model-world-uuid", citizen.getWorldUUID().toString());
        config.setVector3d(basePath + ".position", citizen.getPosition());
        config.setVector3f(basePath + ".rotation", citizen.getRotation());
        config.set(basePath + ".scale", citizen.getScale());
        config.set(basePath + ".permission", citizen.getRequiredPermission());
        config.set(basePath + ".permission-message", citizen.getNoPermissionMessage());
        config.setUUID(basePath + ".npc-uuid", citizen.getSpawnedUUID());
        config.setUUID(basePath + ".hologram-uuid", citizen.getHologramUUID());
        config.set(basePath + ".rotate-towards-player", citizen.getRotateTowardsPlayer());

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
        config.set(basePath + ".nametag-offset", citizen.getNametagOffset());
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

        if (save)
            saveCitizen(citizen);

        updateSpawnedCitizenHologram(citizen, save);
    }

    public void updateCitizenNPCItems(CitizenData citizen) {
        if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null) {
            return;
        }

        NPCEntity npcEntity = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), NPCEntity.getComponentType());
        if (npcEntity == null) {
            return;
        }

        // Item in hand
        if (citizen.getNpcHand() == null) {
            npcEntity.getInventory().getHotbar().setItemStackForSlot((short) 0, null);
        }
        else {
            npcEntity.getInventory().getHotbar().setItemStackForSlot((short) 0, new ItemStack(citizen.getNpcHand()));
        }

        // Item in offhand
        // Off hand is not supported by NPCs
//        if (citizen.getNpcOffHand() == null) {
//            npcEntity.getInventory().getUtility().setItemStackForSlot((short) 0, null);
//        }
//        else {
//            npcEntity.getInventory().getUtility().setItemStackForSlot((short) 0, new ItemStack(citizen.getNpcHand()));
//            npcEntity.getInventory().setActiveUtilitySlot((byte) 0); // Todo: This likely isnt needed
//        }

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
    }

    public void removeCitizen(@Nonnull String citizenId) {
        CitizenData citizen = citizens.remove(citizenId);

        String basePath = "citizens." + citizenId;

        config.set(basePath + ".name", null);
        config.set(basePath + ".model-id", null);
        config.set(basePath + ".model-world-uuid", null);
        config.set(basePath + ".position", null);
        config.set(basePath + ".rotation", null);
        config.set(basePath + ".scale", null);
        config.set(basePath + ".is-player-model", null);
        config.set(basePath + ".use-live-skin", null);
        config.set(basePath + ".skin-username", null);
        config.set(basePath + ".last-skin-update", null);
        config.set(basePath + ".cached-skin", null);
        config.set(basePath + ".npc-uuid", null);
        config.set(basePath + ".hologram-uuid", null);
        config.set(basePath + ".permission", null);
        config.set(basePath + ".permission-message", null);


        // Remove commands
        int commandCount = config.getInt(basePath + ".commands.count", 0);
        for (int i = 0; i < commandCount; i++) {
            String commandPath = basePath + ".commands." + i;
            config.set(commandPath + ".command", null);
            config.set(commandPath + ".run-as-server", null);
        }
        config.set(basePath + ".commands.count", null);

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

                spawnCitizenNPC(citizen, save);
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

        Pair<Ref<EntityStore>, NPCEntity> npc = NPCPlugin.get().spawnEntity(
                world.getEntityStore().getStore(),
                NPCPlugin.get().getIndex("Citizen_Role"),
                citizen.getPosition(),
                citizen.getRotation(),
                citizenModel,
                null,
                null
        );

        if (npc == null)
            return;

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
    }

    public void spawnPlayerModelNPC(CitizenData citizen, World world, boolean save) {
        PlayerSkin skinToUse = determineSkin(citizen);

        if (skinToUse == null) {
            skinToUse = SkinUtilities.createDefaultSkin();
        }

        float scale = Math.max((float)0.01, citizen.getScale());
        Model playerModel = CosmeticsModule.get().createModel(skinToUse, scale);

        if (playerModel == null) {
            getLogger().atWarning().log("Failed to create player model for citizen: " + citizen.getName());
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(citizen.getPosition().x, citizen.getPosition().z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            getLogger().atInfo().log("Failed to spawn player model for citizen NPC: " + citizen.getName() + ". The world chunk is unloaded.");
        }

        Pair<Ref<EntityStore>, NPCEntity> npc = NPCPlugin.get().spawnEntity(
                world.getEntityStore().getStore(),
                NPCPlugin.get().getIndex("Citizen_Role"),
                citizen.getPosition(),
                citizen.getRotation(),
                playerModel,
                null,
                null
        );

        if (npc == null)
            return;

        // Apply skin component
        PlayerSkinComponent skinComponent = new PlayerSkinComponent(skinToUse);
        npc.first().getStore().putComponent(npc.second().getReference(), PlayerSkinComponent.getComponentType(), skinComponent);

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
                            float scale = Math.max((float)0.01, citizen.getScale());
                            Model newModel = CosmeticsModule.get().createModel(skin, scale);
                            if (newModel != null) {
                                ModelComponent modelComponent = new ModelComponent(newModel);
                                npcRef.getStore().putComponent(npcRef, ModelComponent.getComponentType(), modelComponent);
                            }
                        });
                    }
                }
            }
        });
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
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            getLogger().atWarning().log("Failed to spawn citizen hologram: " + citizen.getName() + ". Failed to find world. Try updating the citizen's position.");
            return;
        }

        double scale = Math.max(0.01, citizen.getScale() + citizen.getNametagOffset());

        double baseOffset = 1.65;
        double extraPerScale = 0.40;

        double yOffset = baseOffset * scale + (scale - 1.0) * extraPerScale;

        Vector3d hologramPos = new Vector3d(
                citizen.getPosition().x,
                citizen.getPosition().y + yOffset,
                citizen.getPosition().z
        );

        Vector3f hologramRot = new Vector3f(citizen.getRotation());

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");
        holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);

        holder.putComponent(TransformComponent.getComponentType(), new TransformComponent(hologramPos, hologramRot));
        holder.ensureComponent(UUIDComponent.getComponentType());

        if (projectileComponent.getProjectile() == null) {
            projectileComponent.initialize();
            if (projectileComponent.getProjectile() == null) {
                return;
            }
        }

        holder.addComponent(
                NetworkId.getComponentType(),
                new NetworkId(world.getEntityStore().getStore().getExternalData().takeNextNetworkId())
        );

        UUIDComponent hologramUUIDComponent = holder.getComponent(UUIDComponent.getComponentType());
        if (hologramUUIDComponent != null) {
            citizen.setHologramUUID(hologramUUIDComponent.getUuid());

            if (save)
                saveCitizen(citizen);
        }

        holder.addComponent(Nameplate.getComponentType(), new Nameplate(citizen.getName()));
        world.getEntityStore().getStore().addEntity(holder, AddReason.SPAWN);
    }

    public void despawnCitizen(CitizenData citizen) {
        despawnCitizenNPC(citizen);
        despawnCitizenHologram(citizen);
    }

    public void despawnCitizenNPC(CitizenData citizen) {
        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

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
            }
        }
    }

    public void despawnCitizenHologram(CitizenData citizen) {
        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        UUID hologramUUID = citizen.getHologramUUID();
        if (hologramUUID != null) {
            if (world.getEntityRef(hologramUUID) != null) {
                world.execute(() -> {
                    Ref<EntityStore> hologram = world.getEntityRef(hologramUUID);
                    if (hologram == null) {
                        return;
                    }

                    world.getEntityStore().getStore().removeEntity(hologram, RemoveReason.REMOVE);
                });

                citizen.setHologramUUID(null);
            }
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
        despawnCitizenHologram(citizen);
        spawnCitizenHologram(citizen, save);
    }

    public void rotateCitizenToPlayer(CitizenData citizen, PlayerRef playerRef) {
        if (citizen == null || citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null) {
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
}
