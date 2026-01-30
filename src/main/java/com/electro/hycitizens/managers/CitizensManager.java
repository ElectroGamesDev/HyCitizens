package com.electro.hycitizens.managers;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenInteractEvent;
import com.electro.hycitizens.events.CitizenInteractListener;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.CommandAction;
import com.electro.hycitizens.util.ConfigManager;
import com.electro.hycitizens.util.SkinUtilities;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
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

    public CitizensManager(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.citizens = new ConcurrentHashMap<>();

        loadAllCitizens();
        startSkinUpdateScheduler();
    }

    /**
     * Starts a scheduler that updates live skins every 30 minutes.
     */
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

    /**
     * Stops the skin update scheduler.
     */
    public void shutdown() {
        if (skinUpdateTask != null && !skinUpdateTask.isCancelled()) {
            skinUpdateTask.cancel(false);
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

        // Load skin data
        boolean isPlayerModel = config.getBoolean(basePath + ".is-player-model", false);
        boolean useLiveSkin = config.getBoolean(basePath + ".use-live-skin", false);
        String skinUsername = config.getString(basePath + ".skin-username", "");
        PlayerSkin cachedSkin = config.getPlayerSkin(basePath + ".cached-skin");
        long lastSkinUpdate = config.getLong(basePath + ".last-skin-update", 0L);

        CitizenData citizenData = new CitizenData(citizenId, name, modelId, worldUUID, position, rotation, scale, npcUUID, hologramUUID,
                permission, permMessage, actions, isPlayerModel, useLiveSkin, skinUsername, cachedSkin, lastSkinUpdate);
        citizenData.setCreatedAt(0); // Mark as loaded from config, not newly created
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
                18,
                citizen.getPosition(),
                citizen.getRotation(),
                citizenModel,
                null,
                null
        );

        UUIDComponent uuidComponent = npc.first().getStore().getComponent(
                npc.second().getReference(),
                UUIDComponent.getComponentType()
        );

        if (uuidComponent != null) {
            citizen.setSpawnedUUID(uuidComponent.getUuid());

            if (save)
                saveCitizen(citizen);
        }
    }

    /**
     * Spawns a player model NPC with skin.
     */
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

        Pair<Ref<EntityStore>, NPCEntity> npc = NPCPlugin.get().spawnEntity(
                world.getEntityStore().getStore(),
                18,
                citizen.getPosition(),
                citizen.getRotation(),
                playerModel,
                null,
                null
        );

        // Apply skin component
        PlayerSkinComponent skinComponent = new PlayerSkinComponent(skinToUse);
        npc.first().getStore().putComponent(npc.second().getReference(), PlayerSkinComponent.getComponentType(), skinComponent);

        UUIDComponent uuidComponent = npc.first().getStore().getComponent(
                npc.second().getReference(),
                UUIDComponent.getComponentType()
        );

        if (uuidComponent != null) {
            citizen.setSpawnedUUID(uuidComponent.getUuid());

            if (save)
                saveCitizen(citizen);
        }
    }

    /**
     * Determines which skin to use for the citizen.
     */
    public PlayerSkin determineSkin(CitizenData citizen) {
        if (citizen.isUseLiveSkin() && !citizen.getSkinUsername().isEmpty()) {
            // Fetch live skin asynchronously, but for now return cached or default
            updateCitizenSkin(citizen, true);
            return citizen.getCachedSkin() != null ? citizen.getCachedSkin() : SkinUtilities.createDefaultSkin();
        } else {
            return citizen.getCachedSkin() != null ? citizen.getCachedSkin() : SkinUtilities.createDefaultSkin();
        }
    }

    /**
     * Updates the citizen's skin from the API.
     */
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

    /**
     * Updates skin for a citizen from current player's skin.
     */
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
        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            getLogger().atWarning().log("Failed to spawn citizen hologram: " + citizen.getName() + ". Failed to find world. Try updating the citizen's position.");
            return;
        }

        double scale = Math.max(0.01, citizen.getScale());

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
