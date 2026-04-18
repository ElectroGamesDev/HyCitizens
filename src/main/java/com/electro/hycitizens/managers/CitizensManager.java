package com.electro.hycitizens.managers;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.components.CitizenNametagComponent;
import com.electro.hycitizens.events.CitizenAddedEvent;
import com.electro.hycitizens.events.CitizenAddedListener;
import com.electro.hycitizens.events.CitizenDeathEvent;
import com.electro.hycitizens.events.CitizenDeathListener;
import com.electro.hycitizens.events.CitizenInteractEvent;
import com.electro.hycitizens.events.CitizenInteractListener;
import com.electro.hycitizens.events.CitizenRemovedEvent;
import com.electro.hycitizens.events.CitizenRemovedListener;
import com.electro.hycitizens.models.*;
import com.electro.hycitizens.roles.RoleGenerator;
import com.electro.hycitizens.util.ConfigManager;
import com.electro.hycitizens.util.SkinUtilities;
import com.electro.hycitizens.util.ThreadedScheduler;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
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
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
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
    private static final double NAMETAG_LINE_SPACING = 0.25;
    private static final long FOLLOW_TICK_INTERVAL_MS = 250L;
    private static final long WANDER_UNSTICK_TICK_INTERVAL_MS = 1_000L;
    private static final long WANDER_STUCK_TIMEOUT_MS = 35_000L;
    private static final long WANDER_RECOVERY_COOLDOWN_MS = 20_000L;
    private static final long LOOK_RESET_DELAY_MS = 3_000L;
    private static final double WANDER_PROGRESS_DISTANCE_SQUARED = 0.64;
    private static final long NPC_SPAWN_RETRY_INTERVAL_MS = 50L;
    private static final int MAX_PENDING_NPC_SPAWN_RETRIES = 120;

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

    private static final class FollowSession {
        private Vector3d lastLeaderPosition;
        private Vector3d lastTargetPosition;
        private double anchorAngleRadians = Double.NaN;
    }

    private static final class WanderRecoveryState {
        private Vector3d lastProgressPosition;
        private long lastProgressAtMs = System.currentTimeMillis();
        private long lastRecoveryAtMs = 0L;
    }

    private final HyCitizensPlugin plugin;
    private final ConfigManager config;
    private final Map<String, CitizenData> citizens;
    private final List<CitizenAddedListener> addedListeners = new ArrayList<>();
    private final List<CitizenRemovedListener> removedListeners = new ArrayList<>();
    private final List<CitizenInteractListener> interactListeners = new ArrayList<>();
    private final List<CitizenDeathListener> deathListeners = new ArrayList<>();
    private ThreadedScheduler skinUpdateTask = new ThreadedScheduler();
    private ThreadedScheduler rotateTask = new ThreadedScheduler();
    private ThreadedScheduler nametagMoveTask = new ThreadedScheduler();
    private ThreadedScheduler animationTask = new ThreadedScheduler();
    private ThreadedScheduler healthRegenTask = new ThreadedScheduler();
    private ThreadedScheduler npcRefReconcileTask = new ThreadedScheduler();
    private ThreadedScheduler citizensByWorldTask = new ThreadedScheduler();
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
    private final Map<String, FollowSession> standaloneFollowSessions = new ConcurrentHashMap<>();
    private final Map<String, WanderRecoveryState> wanderRecoveryStates = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingNpcSpawnRetryTasks = new ConcurrentHashMap<>();
    private PatrolManager patrolManager;
    private ScheduleManager scheduleManager;
    private ThreadedScheduler followCitizenTask = new ThreadedScheduler();
    private ThreadedScheduler movementUnstickTask = new ThreadedScheduler();

    public CitizensManager(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.citizens = new ConcurrentHashMap<>();
        this.roleGenerator = new RoleGenerator(plugin.getGeneratedRolesPath());

        loadAllCitizens();
        startSkinUpdateScheduler();
        startRotateScheduler();
        startCitizensByWorldScheduler();
        startNpcRefReconcileScheduler();
        startAnimationScheduler();
        startHealthRegenScheduler();
        startNametagMoveScheduler();
        startPositionSaveScheduler();
        this.patrolManager = new PatrolManager(plugin.getConfigManager(), this);
        startFollowCitizenScheduler();
        this.scheduleManager = new ScheduleManager(this);
        startMovementUnstickScheduler();
    }

    private void startSkinUpdateScheduler() {
        skinUpdateTask.scheduleAtFixedRate("citizens-skin-update", () -> {
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
        rotateTask.scheduleAtFixedRate("citizens-rotate", () -> {
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

                        Vector3d citizenPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();

                        long chunkIndex = ChunkUtil.indexChunkFromBlock(citizenPos.x, citizenPos.z);
                        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                        if (chunk == null)
                            continue;

                        for (PlayerRef playerRef : players) {
                            if (!isPlayerRefValid(playerRef)) {
                                continue;
                            }

                            float maxDistance = Math.max(0.0f, citizen.getLookAtDistance());
                            float maxDistanceSq = maxDistance * maxDistance;

                            double dx = playerRef.getTransform().getPosition().x - citizenPos.x;
                            double dz = playerRef.getTransform().getPosition().z - citizenPos.z;

                            double distSq = dx * dx + dz * dz;
                            double lookDistanceSq = distanceSquared(playerRef.getTransform().getPosition(), citizenPos);
                            boolean withinLookDistance = lookDistanceSq <= maxDistanceSq;

                            if (withinLookDistance && !citizen.getAnimationBehaviors().isEmpty()) {
                                checkProximityAnimations(citizen, playerRef, distSq);
                            }

                            boolean shouldRotateCitizen = withinLookDistance
                                    && citizen.getRotateTowardsPlayer()
                                    && citizen.getMovementBehavior().getType().equals("IDLE");
                            if (shouldRotateCitizen) {
                                cancelPendingLookReset(citizen, playerRef.getUuid());
                                rotateCitizenToPlayer(citizen, playerRef);
                            } else {
                                scheduleLookResetIfNeeded(citizen, playerRef.getUuid());
                            }

                            if (withinLookDistance && shouldRotateModelNametagTowardsPlayer(citizen, playerRef)) {
                                cancelPendingNametagLookReset(citizen, playerRef.getUuid());
                                rotateModelNametagToPlayer(citizen, playerRef);
                            } else {
                                scheduleNametagLookResetIfNeeded(citizen, playerRef.getUuid());
                            }
                        }
                    }
                });
            }
        }, 60, TimeUnit.MILLISECONDS);
    }

    private void startNametagMoveScheduler() {
        nametagMoveTask.scheduleAtFixedRate("citizens-nametag-move", () -> {
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

                        if (citizen.getMovementBehavior().getType().equals("IDLE")) {
                            continue;
                        }

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
                            nametagTransformComponent.setRotation(npcTransformComponent.getRotation());
                        }
                    }
                });
            }
        }, 0, 15, TimeUnit.MILLISECONDS);
    }

    private void startCitizensByWorldScheduler() {
        citizensByWorldTask.scheduleAtFixedRate("citizens-by-world", () -> {
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

    private void startNpcRefReconcileScheduler() {
        npcRefReconcileTask.scheduleAtFixedRate("citizens-npc-ref-reconcile", () -> {
            Map<UUID, List<CitizenData>> unresolvedByWorld = new HashMap<>();

            for (CitizenData citizen : citizens.values()) {
                if (citizen.getSpawnedUUID() == null) {
                    continue;
                }

                Ref<EntityStore> npcRef = citizen.getNpcRef();
                if (npcRef != null && npcRef.isValid()) {
                    continue;
                }

                unresolvedByWorld.computeIfAbsent(citizen.getWorldUUID(), k -> new ArrayList<>()).add(citizen);
            }

            for (Map.Entry<UUID, List<CitizenData>> entry : unresolvedByWorld.entrySet()) {
                UUID worldUUID = entry.getKey();
                List<CitizenData> unresolvedCitizens = entry.getValue();
                World world = Universe.get().getWorld(worldUUID);
                if (world == null) {
                    continue;
                }

                world.execute(() -> {
                    for (CitizenData citizen : unresolvedCitizens) {
                        UUID npcUuid = citizen.getSpawnedUUID();
                        if (npcUuid == null) {
                            continue;
                        }

                        Ref<EntityStore> resolvedRef = world.getEntityRef(npcUuid);
                        if (resolvedRef == null || !resolvedRef.isValid()) {
                            resolvedRef = findExistingCitizenNpcRef(world.getEntityStore().getStore(), citizen);
                        }

                        if (resolvedRef == null || !resolvedRef.isValid()) {
                            if (citizen.isAwaitingRespawn() || isCitizenSpawning(citizen.getId())) {
                                continue;
                            }

                            if (!isCitizenChunkLoaded(world, citizen)) {
                                continue;
                            }

                            spawnCitizenNPC(citizen, true);
                            continue;
                        }

                        bindCitizenEntityBinding(citizen, resolvedRef);

                        if (!refreshSpawnedCitizenAppearance(citizen) && citizen.isPlayerModel()) {
                            updateCitizenSkin(citizen, true);
                        }

                        setInteractionComponent(resolvedRef.getStore(), resolvedRef, citizen);
                        applyNpcNameplateComponent(resolvedRef.getStore(), resolvedRef, citizen);
                        updateCitizenNPCItems(citizen);
                        triggerAnimations(citizen, "DEFAULT");

                        if (scheduleManager != null) {
                            scheduleManager.refreshCitizen(citizen);
                        }

                        if (patrolManager != null && shouldAutoStartPluginPatrol(citizen) && !patrolManager.isPatrolling(citizen.getId())) {
                            patrolManager.startPatrol(citizen.getId(), citizen.getPathConfig().getPluginPatrolPath());
                        }
                    }
                });
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private boolean isCitizenChunkLoaded(@Nonnull World world, @Nonnull CitizenData citizen) {
        Vector3d currentPosition = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        long currentChunkIndex = ChunkUtil.indexChunkFromBlock(currentPosition.x, currentPosition.z);
        if (world.getChunkIfLoaded(currentChunkIndex) != null) {
            return true;
        }

        Vector3d basePosition = citizen.getPosition();
        long baseChunkIndex = ChunkUtil.indexChunkFromBlock(basePosition.x, basePosition.z);
        return world.getChunkIfLoaded(baseChunkIndex) != null;
    }

    @Nullable
    private Ref<EntityStore> findExistingCitizenNpcRef(@Nonnull Store<EntityStore> store, @Nonnull CitizenData citizen) {
        String rolePrefix = "HyCitizens_" + citizen.getId() + "_";
        Query<EntityStore> query = NPCEntity.getComponentType();
        Ref<EntityStore>[] foundRef = new Ref[] { null };

        store.forEachEntityParallel(query, (index, archetypeChunk, cb) -> {
            if (foundRef[0] != null && foundRef[0].isValid()) {
                return;
            }

            NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
            if (npc == null || npc.getRole() == null) {
                return;
            }

            String roleName = npc.getRole().getRoleName();
            if (roleName == null || !roleName.startsWith(rolePrefix)) {
                return;
            }

            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }

            foundRef[0] = ref;
        });

        return foundRef[0] != null && foundRef[0].isValid() ? foundRef[0] : null;
    }

    // Todo: move position saving to chunk unload event when it becomes available
    private void startPositionSaveScheduler() {
        positionSaveTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            config.beginBatch();
            try {
                for (CitizenData citizen : citizens.values()) {
                    if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid())
                        continue;

                    String basePath = "citizens." + citizen.getId();
                    config.setVector3d(basePath + ".current-position", citizen.getCurrentPosition());
                }
            } finally {
                config.endBatch();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void startFollowCitizenScheduler() {
        followCitizenTask.scheduleAtFixedRate("citizens-follow", () -> {
            for (CitizenData citizen : citizens.values()) {
                try {
                    tickStandaloneFollowCitizen(citizen);
                } catch (Exception e) {
                    getLogger().atWarning().log("Standalone follow tick error for citizen " + citizen.getId() + ": " + e.getMessage());
                }
            }
        }, FOLLOW_TICK_INTERVAL_MS, FOLLOW_TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startMovementUnstickScheduler() {
        movementUnstickTask.scheduleAtFixedRate("citizens-unstick", () -> {
            for (CitizenData citizen : citizens.values()) {
                try {
                    tickWanderUnstick(citizen);
                } catch (Exception e) {
                    getLogger().atWarning().log("Wander unstuck tick error for citizen " + citizen.getId() + ": " + e.getMessage());
                }
            }
        }, WANDER_UNSTICK_TICK_INTERVAL_MS, WANDER_UNSTICK_TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startHealthRegenScheduler() {
        healthRegenTask.scheduleAtFixedRate("citizens-health-regen", () -> {
            long now = System.currentTimeMillis();

            for (CitizenData citizen : citizens.values()) {
                if (!citizen.isHealthRegenEnabled()) {
                    continue;
                }
                if (citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
                    continue;
                }
                if (citizen.getHealthRegenAmount() <= 0.0f || citizen.getHealthRegenIntervalSeconds() <= 0.0f) {
                    continue;
                }

                long sinceLastDamage = now - citizen.getLastDamageTakenAt();
                long delayMs = (long) (citizen.getHealthRegenDelayAfterDamageSeconds() * 1000L);
                if (sinceLastDamage < delayMs) {
                    continue;
                }

                long sinceLastRegen = now - citizen.getLastHealthRegenAt();
                long intervalMs = (long) (citizen.getHealthRegenIntervalSeconds() * 1000L);
                if (sinceLastRegen < intervalMs) {
                    continue;
                }

                World world = Universe.get().getWorld(citizen.getWorldUUID());
                if (world == null) {
                    continue;
                }

                world.execute(() -> {
                    Ref<EntityStore> npcRef = citizen.getNpcRef();
                    if (npcRef == null || !npcRef.isValid()) {
                        return;
                    }

                    EntityStatMap statMap = npcRef.getStore().getComponent(npcRef, EntityStatsModule.get().getEntityStatMapComponentType());
                    if (statMap == null) {
                        return;
                    }

                    EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
                    if (healthStat == null) {
                        return;
                    }

                    float currentHealth = healthStat.get();
                    float maxHealth = healthStat.getMax();
                    if (currentHealth >= maxHealth) {
                        return;
                    }

                    float nextHealth = Math.min(maxHealth, currentHealth + citizen.getHealthRegenAmount());
                    setHealthValueClamped(statMap, nextHealth);
                    citizen.setLastHealthRegenAt(System.currentTimeMillis());
                });
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void shutdown() {
        skinUpdateTask.stop();
        rotateTask.stop();
        animationTask.stop();
        healthRegenTask.stop();
        nametagMoveTask.stop();

        followCitizenTask.stop();
        movementUnstickTask.stop();
        npcRefReconcileTask.stop();

        if (positionSaveTask != null && !positionSaveTask.isCancelled()) {
            positionSaveTask.cancel(false);
        }

        if (patrolManager != null) {
            patrolManager.shutdown();
        }

        if (scheduleManager != null) {
            scheduleManager.shutdown();
        }

        for (ScheduledFuture<?> pendingRetry : pendingNpcSpawnRetryTasks.values()) {
            if (pendingRetry != null && !pendingRetry.isCancelled()) {
                pendingRetry.cancel(false);
            }
        }
        pendingNpcSpawnRetryTasks.clear();

        for (CitizenData citizen : citizens.values()) {
            cancelAllPendingLookResets(citizen);
            saveCitizen(citizen);
        }

        pendingHologramRemovals.clear();
        pendingNpcRemovals.clear();
        pendingNpcRemovalTasks.clear();
        standaloneFollowSessions.clear();
        wanderRecoveryStates.clear();
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
        String commandSelectionMode = config.getString(basePath + ".commands.mode", "ALL");

        for (int i = 0; i < commandCount; i++) {
            String commandPath = basePath + ".commands." + i;
            String command = config.getString(commandPath + ".command");
            boolean runAsServer = config.getBoolean(commandPath + ".run-as-server", false);
            float delay = config.getFloat(commandPath + ".delay", 0.0f);
            float chancePercent = config.getFloat(commandPath + ".chance", 100.0f);
            // null = not yet set, will be migrated below
            String trigger = config.getString(commandPath + ".interaction-trigger", null);

            if (command != null) {
                actions.add(new CommandAction(command, runAsServer, delay, trigger, chancePercent));
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
        float lookAtDistance = config.getFloat(basePath + ".look-at-distance", 25.0f);

        // Load skin data
        boolean isPlayerModel = config.getBoolean(basePath + ".is-player-model", false);
        boolean useLiveSkin = config.getBoolean(basePath + ".use-live-skin", false);
        String skinUsername = config.getString(basePath + ".skin-username", "");
        PlayerSkin cachedSkin = config.getPlayerSkin(basePath + ".cached-skin");
        long lastSkinUpdate = config.getLong(basePath + ".last-skin-update", 0L);

        CitizenData citizenData = new CitizenData(citizenId, name, modelId, worldUUID, position, rotation, scale, npcUUID, hologramUuids,
                permission, permMessage, actions, isPlayerModel, useLiveSkin, skinUsername, cachedSkin, lastSkinUpdate, rotateTowardsPlayer);
        citizenData.setCreatedAt(0); // Mark as loaded from config, not newly created
        citizenData.setCommandSelectionMode(commandSelectionMode);
        citizenData.setLookAtDistance(lookAtDistance);

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
        citizenData.setModelNametagEnabled(config.getBoolean(basePath + ".nametag.model.enabled", false));
        citizenData.setNametagModelId(config.getString(basePath + ".nametag.model.id", ""));
        citizenData.setNametagModelScale(config.getFloat(basePath + ".nametag.model.scale", 1.0f));
        citizenData.setRotateNametagTowardsPlayer(config.getBoolean(basePath + ".nametag.model.rotate-towards-player", true));

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
        citizenData.setFollowCitizenEnabled(config.getBoolean(basePath + ".follow-citizen.enabled", false));
        citizenData.setFollowCitizenId(config.getString(basePath + ".follow-citizen.id", ""));
        citizenData.setFollowDistance(config.getFloat(basePath + ".follow-citizen.distance", 2.0f));
        citizenData.setScheduleConfig(loadScheduleConfig(basePath));

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
            float msgChance = config.getFloat(msgPath + ".chance", 100.0f);
            messages.add(new CitizenMessage(msg, msgTrigger, msgDelay, msgChance));
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
        deathConfig.setDropCountMin(config.getInt(basePath + ".death.drop-count-min", 0));
        deathConfig.setDropCountMax(config.getInt(basePath + ".death.drop-count-max", 0));
        deathConfig.setCommandCountMin(config.getInt(basePath + ".death.command-count-min", 0));
        deathConfig.setCommandCountMax(config.getInt(basePath + ".death.command-count-max", 0));
        deathConfig.setMessageCountMin(config.getInt(basePath + ".death.message-count-min", 0));
        deathConfig.setMessageCountMax(config.getInt(basePath + ".death.message-count-max", 0));

        List<DeathDropItem> drops = new ArrayList<>();

        for (String key : config.getKeys(basePath + ".death.drops")) {
            String dropPath = basePath + ".death.drops." + key;

            String itemId = config.getString(dropPath + ".item-id", "");
            int quantity = config.getInt(dropPath + ".quantity", 1);
            float chancePercent = config.getFloat(dropPath + ".chance", 100.0f);

            if (!itemId.isEmpty()) {
                drops.add(new DeathDropItem(itemId, quantity, chancePercent));
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
            float chance = config.getFloat(cmdPath + ".chance", 100.0f);

            if (!cmd.isEmpty()) {
                deathCommands.add(new CommandAction(cmd, runAsServer, delay, chance));
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
            float chance = config.getFloat(msgPath + ".chance", 100.0f);

            if (!msg.isEmpty()) {
                deathMessages.add(new CitizenMessage(msg, null, delay, chance));
            }
        }

        if (!deathMessages.isEmpty()) {
            deathConfig.setDeathMessages(deathMessages);
        }

        citizenData.setDeathConfig(deathConfig);

        // Load first interaction config
        citizenData.setFirstInteractionEnabled(config.getBoolean(basePath + ".first-interaction.enabled", false));
        String firstCommandMode = config.getString(basePath + ".first-interaction.commands.mode", "ALL");
        if (!"ALL".equalsIgnoreCase(firstCommandMode) && !"RANDOM".equalsIgnoreCase(firstCommandMode)) {
            firstCommandMode = "RANDOM";
        } else {
            firstCommandMode = firstCommandMode.toUpperCase(Locale.ROOT);
        }
        citizenData.setFirstInteractionCommandSelectionMode(firstCommandMode);
        citizenData.setPostFirstInteractionBehavior(config.getString(basePath + ".first-interaction.post-behavior", "NORMAL"));
        citizenData.setRunNormalOnFirstInteraction(config.getBoolean(basePath + ".first-interaction.run-normal-on-first", false));

        List<CommandAction> firstCommands = new ArrayList<>();
        int firstCommandCount = config.getInt(basePath + ".first-interaction.commands.count", 0);
        for (int i = 0; i < firstCommandCount; i++) {
            String cmdPath = basePath + ".first-interaction.commands." + i;
            String cmd = config.getString(cmdPath + ".command", "");
            boolean runAsServer = config.getBoolean(cmdPath + ".run-as-server", false);
            float delay = config.getFloat(cmdPath + ".delay", 0.0f);
            String trigger = config.getString(cmdPath + ".interaction-trigger", "BOTH");
            float chance = config.getFloat(cmdPath + ".chance", 100.0f);
            if (!cmd.isEmpty()) {
                firstCommands.add(new CommandAction(cmd, runAsServer, delay, trigger, chance));
            }
        }
        citizenData.setFirstInteractionCommandActions(firstCommands);

        int firstMsgCount = config.getInt(basePath + ".first-interaction.messages.count", 0);
        String firstMsgModeRaw = config.getString(basePath + ".first-interaction.messages.mode", "RANDOM");
        String firstMsgMode = "ALL".equalsIgnoreCase(firstMsgModeRaw) ? "ALL" : "RANDOM";
        boolean firstMsgEnabled = config.getBoolean(basePath + ".first-interaction.messages.enabled", true);
        List<CitizenMessage> firstMessages = new ArrayList<>();
        for (int i = 0; i < firstMsgCount; i++) {
            String msgPath = basePath + ".first-interaction.messages." + i;
            String msg = config.getString(msgPath + ".message", "");
            String trigger = config.getString(msgPath + ".interaction-trigger", "BOTH");
            float delay = config.getFloat(msgPath + ".delay", 0.0f);
            float chance = config.getFloat(msgPath + ".chance", 100.0f);
            firstMessages.add(new CitizenMessage(msg, trigger, delay, chance));
        }
        citizenData.setFirstInteractionMessagesConfig(new MessagesConfig(firstMessages, firstMsgMode, firstMsgEnabled));

        List<UUID> firstPlayers = config.getUUIDList(basePath + ".first-interaction.completed-players");
        if (firstPlayers != null && !firstPlayers.isEmpty()) {
            citizenData.setPlayersWhoCompletedFirstInteraction(new HashSet<>(firstPlayers));
        }

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
        citizenData.setHealthRegenEnabled(config.getBoolean(basePath + ".health-regen.enabled", false));
        citizenData.setHealthRegenAmount(config.getFloat(basePath + ".health-regen.amount", 1.0f));
        citizenData.setHealthRegenIntervalSeconds(config.getFloat(basePath + ".health-regen.interval-seconds", 5.0f));
        citizenData.setHealthRegenDelayAfterDamageSeconds(config.getFloat(basePath + ".health-regen.delay-after-damage-seconds", 5.0f));
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

    @Nonnull
    private ScheduleConfig loadScheduleConfig(@Nonnull String basePath) {
        ScheduleConfig scheduleConfig = new ScheduleConfig();
        scheduleConfig.setEnabled(config.getBoolean(basePath + ".schedule.enabled", false));

        String fallbackModeName = config.getString(basePath + ".schedule.fallback-mode",
                ScheduleFallbackMode.USE_BASE_BEHAVIOR.name());
        try {
            scheduleConfig.setFallbackMode(ScheduleFallbackMode.valueOf(fallbackModeName));
        } catch (IllegalArgumentException ignored) {
            scheduleConfig.setFallbackMode(ScheduleFallbackMode.USE_BASE_BEHAVIOR);
        }

        scheduleConfig.setDefaultLocationId(config.getString(basePath + ".schedule.default-location-id", ""));

        int locationCount = config.getInt(basePath + ".schedule.locations.count", 0);
        List<ScheduleLocation> locations = new ArrayList<>();
        for (int i = 0; i < locationCount; i++) {
            String locationPath = basePath + ".schedule.locations." + i;
            String id = config.getString(locationPath + ".id", "");
            String name = config.getString(locationPath + ".name", "Location");
            UUID worldUuid = config.getUUID(locationPath + ".world-uuid");
            Vector3d position = config.getVector3d(locationPath + ".position");
            Vector3f rotation = config.getVector3f(locationPath + ".rotation");
            if (id.isEmpty() || worldUuid == null || position == null || rotation == null) {
                continue;
            }
            locations.add(new ScheduleLocation(id, name, worldUuid, position, rotation));
        }
        scheduleConfig.setLocations(locations);

        int entryCount = config.getInt(basePath + ".schedule.entries.count", 0);
        List<ScheduleEntry> entries = new ArrayList<>();
        for (int i = 0; i < entryCount; i++) {
            String entryPath = basePath + ".schedule.entries." + i;
            ScheduleEntry entry = new ScheduleEntry();
            entry.setId(config.getString(entryPath + ".id", ""));
            entry.setName(config.getString(entryPath + ".name", "Entry"));
            entry.setEnabled(config.getBoolean(entryPath + ".enabled", true));
            entry.setStartTime24(config.getDouble(entryPath + ".start-time-24", 8.0));
            entry.setEndTime24(config.getDouble(entryPath + ".end-time-24", 12.0));
            entry.setLocationId(config.getString(entryPath + ".location-id", ""));
            try {
                entry.setActivityType(ScheduleActivityType.valueOf(
                        config.getString(entryPath + ".activity-type", ScheduleActivityType.IDLE.name())));
            } catch (IllegalArgumentException ignored) {
                entry.setActivityType(ScheduleActivityType.IDLE);
            }
            entry.setArrivalRadius(config.getFloat(entryPath + ".arrival-radius", 1.5f));
            entry.setTravelSpeed(config.getFloat(entryPath + ".travel-speed", 10.0f));
            entry.setWanderRadius(config.getFloat(entryPath + ".wander-radius", 5.0f));
            entry.setPatrolPathName(config.getString(entryPath + ".patrol-path-name", ""));
            entry.setFollowCitizenId(config.getString(entryPath + ".follow-citizen-id", ""));
            entry.setFollowDistance(config.getFloat(entryPath + ".follow-distance", 2.0f));
            entry.setArrivalAnimationName(config.getString(entryPath + ".arrival-animation-name", ""));
            entry.setArrivalAnimationSlot(config.getInt(entryPath + ".arrival-animation-slot", 0));
            entry.setPriority(config.getInt(entryPath + ".priority", 0));
            if (!entry.getId().isEmpty()) {
                entries.add(entry);
            }
        }
        scheduleConfig.setEntries(entries);
        return scheduleConfig;
    }

    private void saveScheduleConfig(@Nonnull String basePath, @Nonnull ScheduleConfig scheduleConfig) {
        config.set(basePath + ".schedule.enabled", scheduleConfig.isEnabled());
        config.set(basePath + ".schedule.fallback-mode", scheduleConfig.getFallbackMode().name());
        config.set(basePath + ".schedule.default-location-id", scheduleConfig.getDefaultLocationId());

        List<ScheduleLocation> locations = scheduleConfig.getLocations();
        config.set(basePath + ".schedule.locations.count", locations.size());
        for (int i = 0; i < locations.size(); i++) {
            ScheduleLocation location = locations.get(i);
            String locationPath = basePath + ".schedule.locations." + i;
            config.set(locationPath + ".id", location.getId());
            config.set(locationPath + ".name", location.getName());
            config.setUUID(locationPath + ".world-uuid", location.getWorldUUID());
            config.setVector3d(locationPath + ".position", location.getPosition());
            config.setVector3f(locationPath + ".rotation", location.getRotation());
        }

        List<ScheduleEntry> entries = scheduleConfig.getEntries();
        config.set(basePath + ".schedule.entries.count", entries.size());
        for (int i = 0; i < entries.size(); i++) {
            ScheduleEntry entry = entries.get(i);
            String entryPath = basePath + ".schedule.entries." + i;
            config.set(entryPath + ".id", entry.getId());
            config.set(entryPath + ".name", entry.getName());
            config.set(entryPath + ".enabled", entry.isEnabled());
            config.set(entryPath + ".start-time-24", entry.getStartTime24());
            config.set(entryPath + ".end-time-24", entry.getEndTime24());
            config.set(entryPath + ".location-id", entry.getLocationId());
            config.set(entryPath + ".activity-type", entry.getActivityType().name());
            config.set(entryPath + ".arrival-radius", entry.getArrivalRadius());
            config.set(entryPath + ".travel-speed", entry.getTravelSpeed());
            config.set(entryPath + ".wander-radius", entry.getWanderRadius());
            config.set(entryPath + ".patrol-path-name", entry.getPatrolPathName());
            config.set(entryPath + ".follow-citizen-id", entry.getFollowCitizenId());
            config.set(entryPath + ".follow-distance", entry.getFollowDistance());
            config.set(entryPath + ".arrival-animation-name", entry.getArrivalAnimationName());
            config.set(entryPath + ".arrival-animation-slot", entry.getArrivalAnimationSlot());
            config.set(entryPath + ".priority", entry.getPriority());
        }
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
            config.set(basePath + ".look-at-distance", citizen.getLookAtDistance());
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
            config.set(basePath + ".commands.mode", citizen.getCommandSelectionMode());

            for (int i = 0; i < actions.size(); i++) {
                CommandAction action = actions.get(i);
                String commandPath = basePath + ".commands." + i;

                config.set(commandPath + ".command", action.getCommand());
                config.set(commandPath + ".run-as-server", action.isRunAsServer());
                config.set(commandPath + ".delay", action.getDelaySeconds());
                config.set(commandPath + ".interaction-trigger",
                        action.getInteractionTrigger() != null ? action.getInteractionTrigger() : "BOTH");
                config.set(commandPath + ".chance", action.getChancePercent());
            }

            config.set(basePath + ".force-f-key-interaction-text", citizen.getForceFKeyInteractionText());

            // Misc
            config.set(basePath + ".hide-nametag", citizen.isHideNametag());
            config.set(basePath + ".hide-npc", citizen.isHideNpc());
            config.set(basePath + ".nametag-offset", citizen.getNametagOffset());
            config.set(basePath + ".nametag.model.enabled", citizen.isModelNametagEnabled());
            config.set(basePath + ".nametag.model.id", citizen.getNametagModelId());
            config.set(basePath + ".nametag.model.scale", citizen.getNametagModelScale());
            config.set(basePath + ".nametag.model.rotate-towards-player", citizen.isRotateNametagTowardsPlayer());

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
            config.set(basePath + ".follow-citizen.enabled", citizen.isFollowCitizenEnabled());
            config.set(basePath + ".follow-citizen.id", citizen.getFollowCitizenId());
            config.set(basePath + ".follow-citizen.distance", citizen.getFollowDistance());

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
                config.set(msgPath + ".chance", cm.getChancePercent());
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
            saveScheduleConfig(basePath, citizen.getScheduleConfig());

            // Save death config
            DeathConfig dc = citizen.getDeathConfig();
            config.set(basePath + ".death.command-mode", dc.getCommandSelectionMode());
            config.set(basePath + ".death.message-mode", dc.getMessageSelectionMode());
            config.set(basePath + ".death.drop-count-min", dc.getDropCountMin());
            config.set(basePath + ".death.drop-count-max", dc.getDropCountMax());
            config.set(basePath + ".death.command-count-min", dc.getCommandCountMin());
            config.set(basePath + ".death.command-count-max", dc.getCommandCountMax());
            config.set(basePath + ".death.message-count-min", dc.getMessageCountMin());
            config.set(basePath + ".death.message-count-max", dc.getMessageCountMax());

            List<DeathDropItem> drops = dc.getDropItems();
            config.set(basePath + ".death.drops", new ArrayList<>());
            for (int i = 0; i < drops.size(); i++) {
                String dropPath = basePath + ".death.drops." + i;
                config.set(dropPath + ".item-id", drops.get(i).getItemId());
                config.set(dropPath + ".quantity", drops.get(i).getQuantity());
                config.set(dropPath + ".chance", drops.get(i).getChancePercent());
            }

            List<CommandAction> deathCmds = dc.getDeathCommands();
            config.set(basePath + ".death.commands", new ArrayList<>());
            for (int i = 0; i < deathCmds.size(); i++) {
                String cmdPath = basePath + ".death.commands." + i;
                CommandAction cmd = deathCmds.get(i);
                config.set(cmdPath + ".command", cmd.getCommand());
                config.set(cmdPath + ".run-as-server", cmd.isRunAsServer());
                config.set(cmdPath + ".delay", cmd.getDelaySeconds());
                config.set(cmdPath + ".chance", cmd.getChancePercent());
            }

            List<CitizenMessage> deathMsgs = dc.getDeathMessages();
            config.set(basePath + ".death.messages", new ArrayList<>());
            for (int i = 0; i < deathMsgs.size(); i++) {
                String msgPath = basePath + ".death.messages." + i;
                CitizenMessage msg = deathMsgs.get(i);
                config.set(msgPath + ".message", msg.getMessage());
                config.set(msgPath + ".delay", msg.getDelaySeconds());
                config.set(msgPath + ".chance", msg.getChancePercent());
            }

            // Save first interaction config
            config.set(basePath + ".first-interaction.enabled", citizen.isFirstInteractionEnabled());
            config.set(basePath + ".first-interaction.commands.mode", citizen.getFirstInteractionCommandSelectionMode());
            config.set(basePath + ".first-interaction.post-behavior", citizen.getPostFirstInteractionBehavior());
            config.set(basePath + ".first-interaction.run-normal-on-first", citizen.isRunNormalOnFirstInteraction());
            config.setUUIDList(basePath + ".first-interaction.completed-players",
                    new ArrayList<>(citizen.getPlayersWhoCompletedFirstInteraction()));

            List<CommandAction> firstCommands = citizen.getFirstInteractionCommandActions();
            config.set(basePath + ".first-interaction.commands.count", firstCommands.size());
            for (int i = 0; i < firstCommands.size(); i++) {
                CommandAction cmd = firstCommands.get(i);
                String cmdPath = basePath + ".first-interaction.commands." + i;
                config.set(cmdPath + ".command", cmd.getCommand());
                config.set(cmdPath + ".run-as-server", cmd.isRunAsServer());
                config.set(cmdPath + ".delay", cmd.getDelaySeconds());
                config.set(cmdPath + ".interaction-trigger", cmd.getInteractionTrigger() != null ? cmd.getInteractionTrigger() : "BOTH");
                config.set(cmdPath + ".chance", cmd.getChancePercent());
            }

            MessagesConfig firstMessagesConfig = citizen.getFirstInteractionMessagesConfig();
            List<CitizenMessage> firstMessages = firstMessagesConfig.getMessages();
            config.set(basePath + ".first-interaction.messages.count", firstMessages.size());
            config.set(basePath + ".first-interaction.messages.mode", firstMessagesConfig.getSelectionMode());
            config.set(basePath + ".first-interaction.messages.enabled", firstMessagesConfig.isEnabled());
            for (int i = 0; i < firstMessages.size(); i++) {
                CitizenMessage msg = firstMessages.get(i);
                String msgPath = basePath + ".first-interaction.messages." + i;
                config.set(msgPath + ".message", msg.getMessage());
                config.set(msgPath + ".interaction-trigger", msg.getInteractionTrigger() != null ? msg.getInteractionTrigger() : "BOTH");
                config.set(msgPath + ".delay", msg.getDelaySeconds());
                config.set(msgPath + ".chance", msg.getChancePercent());
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
            config.set(basePath + ".health-regen.enabled", citizen.isHealthRegenEnabled());
            config.set(basePath + ".health-regen.amount", citizen.getHealthRegenAmount());
            config.set(basePath + ".health-regen.interval-seconds", citizen.getHealthRegenIntervalSeconds());
            config.set(basePath + ".health-regen.delay-after-damage-seconds", citizen.getHealthRegenDelayAfterDamageSeconds());
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
                UUID expectedNpcUuid = citizen.getSpawnedUUID();
                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    if (expectedNpcUuid == null || !expectedNpcUuid.equals(citizen.getSpawnedUUID())) {
                        return;
                    }

                    if (isCitizenSpawning(citizen.getId())) {
                        return;
                    }

                    Ref<EntityStore> npcRef = citizen.getNpcRef();
                    if (npcRef == null || !npcRef.isValid()) {
                        return;
                    }

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
        fireCitizenAddedEvent(new CitizenAddedEvent(citizen));

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
        // Todo: Re-add
//        if (citizen.getNpcOffHand() == null) {
//            npcEntity.getInventory().getUtility().setItemStackForSlot((short) 0, null);
//        }
//        else {
//            npcEntity.getInventory().getUtility().setItemStackForSlot((short) 0, new ItemStack(citizen.getNpcOffHand()));
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
                    setHealthValueClamped(finalStatMap, healthValue.get() + healthDifference);
                }
            }
        }, 200, TimeUnit.MILLISECONDS);
    }

    public void removeCitizen(@Nonnull String citizenId) {
        CitizenData citizen = citizens.remove(citizenId);

        config.set("citizens." + citizenId, null);
        standaloneFollowSessions.remove(citizenId);

        if (scheduleManager != null) {
            scheduleManager.clearCitizen(citizenId);
        }
        roleGenerator.deleteRoleFile(citizenId);

        if (citizen == null) {
            return;
        }

        cancelAllPendingLookResets(citizen);
        despawnCitizenForDeletion(citizen);
        fireCitizenRemovedEvent(new CitizenRemovedEvent(citizen));
    }

    private void despawnCitizenForDeletion(@Nonnull CitizenData citizen) {
        despawnCitizenNPCForDeletion(citizen);
        despawnCitizenHologram(citizen);
    }
    public void spawnCitizen(CitizenData citizen, boolean save) {
        if (citizen.isAwaitingRespawn()) {
            return;
        }

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

    public void bindCitizenEntityBinding(@Nonnull CitizenData citizen, @Nonnull Ref<EntityStore> ref) {
        citizen.setNpcRef(ref);
        cancelPendingNpcSpawnRetry(citizen.getId());

        UUIDComponent uuidComponent = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            citizen.setSpawnedUUID(uuidComponent.getUuid());
        }
    }

    public void clearCitizenEntityBinding(@Nonnull CitizenData citizen) {
        citizen.setSpawnedUUID(null);
        citizen.setNpcRef(null);
    }

    private void cancelPendingNpcSpawnRetry(@Nonnull String citizenId) {
        ScheduledFuture<?> pendingTask = pendingNpcSpawnRetryTasks.remove(citizenId);
        if (pendingTask != null) {
            pendingTask.cancel(false);
        }
    }

    private void schedulePendingNpcSpawnRetry(@Nonnull CitizenData citizen, boolean save, int attempt) {
        if (attempt >= MAX_PENDING_NPC_SPAWN_RETRIES) {
            getLogger().atWarning().log("Timed out waiting for previous NPC entity to clear for citizen '" + citizen.getId() + "'.");
            return;
        }

        String citizenId = citizen.getId();
        ScheduledFuture<?> existingTask = pendingNpcSpawnRetryTasks.get(citizenId);
        if (existingTask != null && !existingTask.isDone()) {
            return;
        }

        final int nextAttempt = attempt + 1;
        ScheduledFuture<?> retryTask = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            pendingNpcSpawnRetryTasks.remove(citizenId);
            spawnCitizenNPCInternal(citizen, save, nextAttempt);
        }, NPC_SPAWN_RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
        pendingNpcSpawnRetryTasks.put(citizenId, retryTask);
    }

    private boolean shouldAutoStartPluginPatrol(@Nonnull CitizenData citizen) {
        String pluginPatrolPath = citizen.getPathConfig().getPluginPatrolPath();
        return "PATROL".equals(citizen.getMovementBehavior().getType()) && !pluginPatrolPath.isEmpty();
    }

    private boolean usesMarkerDrivenRole(@Nonnull CitizenData citizen) {
        if (citizen.getCurrentScheduleRuntimeState() == ScheduleRuntimeState.TRAVELING) {
            return true;
        }

        if (citizen.getCurrentScheduleRuntimeState() == ScheduleRuntimeState.FALLBACK) {
            return true;
        }

        if (citizen.getCurrentScheduleRuntimeState() == ScheduleRuntimeState.ACTIVE) {
            ScheduleConfig scheduleConfig = citizen.getScheduleConfig();
            if (scheduleConfig != null) {
                Optional<ScheduleEntry> activeEntry = scheduleConfig.findEntry(citizen.getCurrentScheduleEntryId());
                if (activeEntry.isPresent()) {
                    ScheduleActivityType activityType = activeEntry.get().getActivityType();
                    return activityType == ScheduleActivityType.PATROL
                            || activityType == ScheduleActivityType.FOLLOW_CITIZEN;
                }
            }
        }

        String movementType = citizen.getMovementBehavior().getType();
        return "PATROL".equals(movementType)
                || ("FOLLOW_CITIZEN".equals(movementType) && !citizen.getFollowCitizenId().trim().isEmpty());
    }

    @Nonnull
    private String resolveSpawnRoleName(@Nonnull CitizenData citizen) {
        if (!usesMarkerDrivenRole(citizen)) {
            return resolveRoleName(citizen);
        }

        roleGenerator.generateRoleIfChanged(citizen);
        String safeRoleName = citizen.getScheduleConfig().isEnabled()
                ? roleGenerator.getScheduleFallbackIdleRoleName(citizen)
                : roleGenerator.getFallbackRoleName(citizen);
        int roleIndex = NPCPlugin.get().getIndex(safeRoleName);
        if (roleIndex != Integer.MIN_VALUE) {
            return safeRoleName;
        }

        return resolveRoleName(citizen);
    }

    private void promoteCitizenToDesiredRoleIfNeeded(@Nonnull CitizenData citizen) {
        if (!usesMarkerDrivenRole(citizen)) {
            return;
        }

        Ref<EntityStore> npcRef = citizen.getNpcRef();
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        String desiredRoleName = scheduleManager != null
                ? scheduleManager.getDesiredRoleName(citizen)
                : roleGenerator.getRoleName(citizen);
        int desiredRoleIndex = NPCPlugin.get().getIndex(desiredRoleName);
        if (desiredRoleIndex == Integer.MIN_VALUE) {
            scheduleRoleRetry(citizen, desiredRoleName);
            return;
        }

        world.execute(() -> {
            Ref<EntityStore> liveRef = citizen.getNpcRef();
            if (liveRef == null || !liveRef.isValid()) {
                return;
            }

            NPCEntity npcEntity = liveRef.getStore().getComponent(liveRef, NPCEntity.getComponentType());
            if (npcEntity == null || npcEntity.getRole() == null) {
                return;
            }

            String currentRoleName = npcEntity.getRole().getRoleName();
            if (desiredRoleName.equals(currentRoleName)) {
                return;
            }

            if (patrolManager != null) {
                Vector3d markerPosition = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
                patrolManager.ensureMoveTargetNow(citizen, world, markerPosition);
            }

            RoleChangeSystem.requestRoleChange(liveRef, npcEntity.getRole(), desiredRoleIndex, true, liveRef.getStore());
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> refreshSpawnedCitizenAppearance(citizen), 50, TimeUnit.MILLISECONDS);
        });
    }

    public void spawnCitizenNPC(CitizenData citizen, boolean save) {
        spawnCitizenNPCInternal(citizen, save, 0);
    }

    private void spawnCitizenNPCInternal(@Nonnull CitizenData citizen, boolean save, int attempt) {
        if (citizen.isAwaitingRespawn()) {
            return;
        }

        if (citizen.isHideNpc()) {
            cancelPendingNpcSpawnRetry(citizen.getId());
            return;
        }

        if (!citizensCurrentlySpawning.add(citizen.getId())) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            citizensCurrentlySpawning.remove(citizen.getId());
            getLogger().atWarning().log("Failed to spawn citizen NPC: " + citizen.getName() + ". Failed to find world. Try updating the citizen's position.");
            return;
        }

        Ref<EntityStore> existingRef = findExistingCitizenNpcRef(world.getEntityStore().getStore(), citizen);
        if (existingRef != null && existingRef.isValid()) {
            citizensCurrentlySpawning.remove(citizen.getId());
            schedulePendingNpcSpawnRetry(citizen, save, attempt);
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

        String roleName = resolveSpawnRoleName(citizen);

        // Pass null for model so the NPC role handles model resolution. This fixes issues with some models like Kweebecs.
        Pair<Ref<EntityStore>, NPCEntity> npc = NPCPlugin.get().spawnEntity(
                world.getEntityStore().getStore(),
                NPCPlugin.get().getIndex(roleName),
                citizen.getPosition(),
                citizen.getRotation(),
                null,
                (npcComponent, holder, store) -> npcComponent.setInitialModelScale(scale),
                null
        );

        if (npc == null) {
            citizensCurrentlySpawning.remove(citizen.getId());
            return;
        }

        npc.second().setLeashPoint(citizen.getPosition());

        //npc.second().setInventorySize(9, 30, 5); // Todo: Fix this

        Ref<EntityStore> ref = npc.first();
        Store<EntityStore> store = ref.getStore();

        // This is required since the "Player" entity's scale resets to 0
        if (citizen.getModelId().equals("Player")) {
            PersistentModel persistentModel = store.getComponent(ref, PersistentModel.getComponentType());
            if (persistentModel != null) {
                persistentModel.setModelReference(new Model.ModelReference(
                        citizen.getModelId(),
                        scale,
                        null,
                        true
                ));
            }
        }

        bindCitizenEntityBinding(citizen, ref);
        if (patrolManager != null) {
            patrolManager.ensureMoveTargetNow(citizen, world, citizen.getPosition());
        }
        promoteCitizenToDesiredRoleIfNeeded(citizen);
        if (save) {
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
        if (shouldAutoStartPluginPatrol(citizen) && patrolManager != null) {
            patrolManager.startPatrol(citizen.getId(), citizen.getPathConfig().getPluginPatrolPath());
        }
        if (scheduleManager != null) {
            scheduleManager.refreshCitizen(citizen);
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

        String roleName = resolveSpawnRoleName(citizen);

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

        //npc.second().setInventorySize(9, 30, 5); // Todo: Fix this

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
                    scale,
                    playerModel.getRandomAttachmentIds(),
                    playerModel.getAnimationSetMap() == null
                    ));
        }

        bindCitizenEntityBinding(citizen, ref);
        if (patrolManager != null) {
            patrolManager.ensureMoveTargetNow(citizen, world, citizen.getPosition());
        }
        promoteCitizenToDesiredRoleIfNeeded(citizen);
        if (save) {
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
        if (shouldAutoStartPluginPatrol(citizen) && patrolManager != null) {
            patrolManager.startPatrol(citizen.getId(), citizen.getPathConfig().getPluginPatrolPath());
        }
        if (scheduleManager != null) {
            scheduleManager.refreshCitizen(citizen);
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
        setHealthValueClamped(statMap, targetHealth);
    }

    private void setHealthValueClamped(@Nonnull EntityStatMap statMap, float targetHealth) {
        EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthValue == null) {
            return;
        }
        float clampedHealth = Math.max(0.0f, Math.min(healthValue.getMax(), targetHealth));
        statMap.setStatValue(DefaultEntityStatTypes.getHealth(), clampedHealth);
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

        if (citizen.isFirstInteractionEnabled()) {
            for (CommandAction cmd : citizen.getFirstInteractionCommandActions()) {
                String trigger = cmd.getInteractionTrigger();
                if (trigger == null || "BOTH".equals(trigger) || "F_KEY".equals(trigger)) {
                    return true;
                }
            }

            for (CitizenMessage msg : citizen.getFirstInteractionMessagesConfig().getMessages()) {
                String trigger = msg.getInteractionTrigger();
                if (trigger == null || "BOTH".equals(trigger) || "F_KEY".equals(trigger)) {
                    return true;
                }
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

    @Nonnull
    private Vector3d getBaseHologramPosition(@Nonnull CitizenData citizen) {
        double scale = Math.max(0.01, citizen.getScale() + citizen.getNametagOffset());
        double baseOffset = 1.65;
        double extraPerScale = 0.40;
        double yOffset = baseOffset * scale + (scale - 1.0) * extraPerScale;

        return new Vector3d(
                citizen.getPosition().x,
                citizen.getPosition().y + yOffset,
                citizen.getPosition().z
        );
    }

    @Nonnull
    private Vector3d getHologramLinePosition(@Nonnull Vector3d baseHologramPos, int totalLines, int lineIndex) {
        return new Vector3d(
                baseHologramPos.x,
                baseHologramPos.y + ((totalLines - 1 - lineIndex) * NAMETAG_LINE_SPACING),
                baseHologramPos.z
        );
    }

    private boolean bindCitizenNametagIdentity(@Nonnull Ref<EntityStore> ref, @Nonnull String citizenId, int lineIndex) {
        CitizenNametagComponent nametagComponent =
                ref.getStore().getComponent(ref, CitizenNametagComponent.getComponentType());
        if (nametagComponent != null) {
            String existingCitizenId = nametagComponent.getCitizenId();
            if (!existingCitizenId.isEmpty() && !citizenId.equals(existingCitizenId)) {
                return false;
            }

            nametagComponent.setCitizenId(citizenId);
            nametagComponent.setLineIndex(lineIndex);
            return true;
        }

        ref.getStore().addComponent(ref, CitizenNametagComponent.getComponentType(),
                new CitizenNametagComponent(citizenId, lineIndex));
        return true;
    }

    @Nullable
    private String getInlineNametagText(@Nonnull CitizenData citizen) {
        List<String> lines = getNonEmptyNametagLines(citizen);
        if (lines.size() != 1) {
            return null;
        }
        return lines.get(0);
    }

    @Nullable
    private ModelAsset getNametagModelAsset(@Nonnull CitizenData citizen) {
        if (!citizen.isModelNametagEnabled()) {
            return null;
        }

        String modelId = citizen.getNametagModelId().trim();
        if (modelId.isEmpty()) {
            return null;
        }

        return ModelAsset.getAssetMap().getAsset(modelId);
    }

    private boolean shouldUseModelNametag(@Nonnull CitizenData citizen) {
        return !citizen.isHideNametag() && getNametagModelAsset(citizen) != null;
    }

    private int getDesiredNametagEntityCount(@Nonnull CitizenData citizen) {
        if (citizen.isHideNametag()) {
            return 0;
        }

        if (shouldUseModelNametag(citizen)) {
            return 1;
        }

        return getNonEmptyNametagLines(citizen).size();
    }

    @Nullable
    private Model createNametagModel(@Nonnull CitizenData citizen) {
        ModelAsset nametagAsset = getNametagModelAsset(citizen);
        if (nametagAsset == null) {
            return null;
        }

        float scale = Math.max(0.01f, citizen.getNametagModelScale());
        return Model.createStaticScaledModel(nametagAsset, scale);
    }

    @Nullable
    private Model.ModelReference createNametagModelReference(@Nonnull CitizenData citizen) {
        ModelAsset nametagAsset = getNametagModelAsset(citizen);
        if (nametagAsset == null) {
            return null;
        }

        float scale = Math.max(0.01f, citizen.getNametagModelScale());
        return new Model.ModelReference(nametagAsset.getId(), scale, null, true);
    }

    private void applyTextNametagDisplay(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String lineText) {
        store.removeComponentIfExists(ref, ModelComponent.getComponentType());
        store.removeComponentIfExists(ref, PersistentModel.getComponentType());
        store.putComponent(ref, Nameplate.getComponentType(), new Nameplate(lineText));
    }

    private void applyModelNametagDisplay(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull CitizenData citizen) {
        Model model = createNametagModel(citizen);
        if (model == null) {
            store.removeComponentIfExists(ref, Nameplate.getComponentType());
            store.removeComponentIfExists(ref, ModelComponent.getComponentType());
            store.removeComponentIfExists(ref, PersistentModel.getComponentType());
            return;
        }

        store.removeComponentIfExists(ref, Nameplate.getComponentType());
        store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
        Model.ModelReference modelReference = createNametagModelReference(citizen);
        if (modelReference != null) {
            store.putComponent(ref, PersistentModel.getComponentType(), new PersistentModel(modelReference));
        }
    }

    @Nullable
    private Holder<EntityStore> createNametagEntityHolder(@Nonnull World world, @Nonnull CitizenData citizen, int lineIndex,
                                                          @Nonnull Vector3d linePos, @Nonnull Vector3f rotation,
                                                          @Nullable String lineText) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        holder.putComponent(TransformComponent.getComponentType(), new TransformComponent(linePos, rotation));
        holder.putComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.ensureComponent(UUIDComponent.getComponentType());

        holder.addComponent(
                NetworkId.getComponentType(),
                new NetworkId(world.getEntityStore().getStore().getExternalData().takeNextNetworkId())
        );
        holder.addComponent(CitizenNametagComponent.getComponentType(),
                new CitizenNametagComponent(citizen.getId(), lineIndex));

        if (shouldUseModelNametag(citizen)) {
            Model model = createNametagModel(citizen);
            Model.ModelReference modelReference = createNametagModelReference(citizen);
            if (model == null) {
                return null;
            }

            holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            if (modelReference != null) {
                holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(modelReference));
            }
        } else {
            ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");
            holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);
            if (projectileComponent.getProjectile() == null) {
                projectileComponent.initialize();
                if (projectileComponent.getProjectile() == null) {
                    return null;
                }
            }
            holder.addComponent(Nameplate.getComponentType(), new Nameplate(lineText == null ? "" : lineText));
        }

        return holder;
    }

    private void applyNametagEntityDisplay(@Nonnull Ref<EntityStore> entity, @Nonnull CitizenData citizen, int lineIndex,
                                           @Nonnull Vector3d linePos, @Nonnull Vector3f rotation, @Nullable String lineText) {
        TransformComponent transform = entity.getStore().getComponent(entity, TransformComponent.getComponentType());
        if (transform != null) {
            transform.setPosition(linePos);
            transform.setRotation(rotation);
        }
        HeadRotation headRotation = entity.getStore().getComponent(entity, HeadRotation.getComponentType());
        if (headRotation != null) {
            headRotation.setRotation(rotation);
        }

        bindCitizenNametagIdentity(entity, citizen.getId(), lineIndex);

        if (shouldUseModelNametag(citizen)) {
            applyModelNametagDisplay(entity.getStore(), entity, citizen);
        } else {
            applyTextNametagDisplay(entity.getStore(), entity, lineText == null ? "" : lineText);
        }
    }

    public boolean shouldUseSeparateNametagEntities(@Nonnull CitizenData citizen) {
        if (citizen.isHideNametag()) {
            return false;
        }

        if (shouldUseModelNametag(citizen)) {
            return true;
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

        if (shouldUseModelNametag(citizen)) {
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

    private void applyPlayerModelAppearance(@Nonnull Ref<EntityStore> npcRef, @Nonnull CitizenData citizen, @Nonnull PlayerSkin skin) {
        float scale = Math.max(0.01f, citizen.getScale());
        Model newModel = CosmeticsModule.get().createModel(skin, scale);
        if (newModel == null) {
            getLogger().atWarning().log("Failed to create skin model while restoring appearance for citizen '" + citizen.getId() + "'.");
            return;
        }

        npcRef.getStore().putComponent(npcRef, PlayerSkinComponent.getComponentType(), new PlayerSkinComponent(skin));
        npcRef.getStore().putComponent(npcRef, ModelComponent.getComponentType(), new ModelComponent(newModel));

        PersistentModel persistentModel = npcRef.getStore().getComponent(npcRef, PersistentModel.getComponentType());
        if (persistentModel != null) {
            persistentModel.setModelReference(new Model.ModelReference(
                    newModel.getModelAssetId(),
                    scale,
                    newModel.getRandomAttachmentIds(),
                    newModel.getAnimationSetMap() == null
            ));
        }
    }

    public boolean refreshSpawnedCitizenAppearance(@Nonnull CitizenData citizen) {
        if (!citizen.isPlayerModel()) {
            return false;
        }

        PlayerSkin cachedSkin = citizen.getCachedSkin();
        if (cachedSkin == null) {
            return false;
        }

        UUID spawnedUuid = citizen.getSpawnedUUID();
        if (spawnedUuid == null) {
            return false;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return false;
        }

        Ref<EntityStore> npcRef = world.getEntityRef(spawnedUuid);
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }

        world.execute(() -> applyPlayerModelAppearance(npcRef, citizen, cachedSkin));
        return true;
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
        if (!citizen.isPlayerModel()) {
            return;
        }

        PlayerSkin cachedSkin = citizen.getCachedSkin();
        String skinUsername = citizen.getSkinUsername().trim();

        if (!skinUsername.isEmpty() && (cachedSkin == null || citizen.isUseLiveSkin())) {
            SkinUtilities.getSkin(skinUsername).thenAccept(skin -> {
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
                                world.execute(() -> applyPlayerModelAppearance(npcRef, citizen, skin));
                            }
                        }
                    }
                }
            });
            return;
        }

        if (cachedSkin != null) {
            if (save) {
                saveCitizen(citizen);
            }
            refreshSpawnedCitizenAppearance(citizen);
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
                                    scale,
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
        int desiredEntityCount = getDesiredNametagEntityCount(citizen);
        if (desiredEntityCount <= 0) {
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

                    Vector3d baseHologramPos = getBaseHologramPosition(citizen);

                    Vector3f hologramRot = new Vector3f(citizen.getRotation());

                    for (int i = 0; i < desiredEntityCount; ++i) {
                        String lineText = i < nametagLines.size() ? nametagLines.get(i) : null;
                        Vector3d linePos = getHologramLinePosition(baseHologramPos, desiredEntityCount, i);
                        Holder<EntityStore> holder = createNametagEntityHolder(world, citizen, i, linePos, hologramRot, lineText);
                        if (holder == null) {
                            continue;
                        }

                        UUIDComponent hologramUUIDComponent = holder.getComponent(UUIDComponent.getComponentType());
                        if (hologramUUIDComponent != null) {
                            citizen.getHologramLineUuids().add(hologramUUIDComponent.getUuid());
                        }
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
        cancelPendingNpcSpawnRetry(citizen.getId());
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

            clearCitizenEntityBinding(citizen);
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

                clearCitizenEntityBinding(citizen);
            }
        }
    }

    private void despawnCitizenNPCForDeletion(@Nonnull CitizenData citizen) {
        if (patrolManager != null) {
            patrolManager.onCitizenDespawned(citizen.getId());
        }
        cancelPendingNpcSpawnRetry(citizen.getId());
        citizensCurrentlySpawning.remove(citizen.getId());
        citizen.setAwaitingRespawn(false);

        UUID npcUUID = citizen.getSpawnedUUID();
        Ref<EntityStore> npcRef = citizen.getNpcRef();
        World world = Universe.get().getWorld(citizen.getWorldUUID());

        if (world == null) {
            if (npcUUID != null) {
                queuePendingNpcRemoval(citizen, npcUUID);
            }
            clearCitizenEntityBinding(citizen);
            return;
        }

        if (npcRef != null && npcRef.isValid()) {
            world.execute(() -> world.getEntityStore().getStore().removeEntity(npcRef, RemoveReason.REMOVE));
            clearCitizenEntityBinding(citizen);
            return;
        }

        if (npcUUID != null) {
            Ref<EntityStore> resolvedNpcRef = world.getEntityRef(npcUUID);
            if (resolvedNpcRef != null && resolvedNpcRef.isValid()) {
                world.execute(() -> world.getEntityStore().getStore().removeEntity(resolvedNpcRef, RemoveReason.REMOVE));
            } else {
                queuePendingNpcRemoval(citizen, npcUUID);
            }

            clearCitizenEntityBinding(citizen);
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
        citizen.setAwaitingRespawn(false);

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
        int desiredEntityCount = getDesiredNametagEntityCount(citizen);
        if (desiredEntityCount <= 0) {
            despawnCitizenHologram(citizen);
            if (save) {
                saveCitizen(citizen);
            }
            return;
        }

        List<UUID> existingUuids = citizen.getHologramLineUuids();
        int existingCount = existingUuids.size();
        int newCount = desiredEntityCount;

        Vector3d baseHologramPos = getBaseHologramPosition(citizen);
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

            if (!existingUuids.isEmpty()) {
                Ref<EntityStore> firstEntity = world.getEntityRef(existingUuids.get(0));
                if (firstEntity != null && firstEntity.isValid()) {
                    boolean entityIsModelHost = firstEntity.getStore().getComponent(firstEntity, PropComponent.getComponentType()) != null;
                    boolean shouldUseModelHost = shouldUseModelNametag(citizen);
                    if (entityIsModelHost != shouldUseModelHost) {
                        despawnCitizenHologram(citizen);
                        spawnCitizenHologram(citizen, save);
                        return;
                    }
                }
            }

            // Update existing lines
            int linesToUpdate = Math.min(existingCount, newCount);
            for (int i = 0; i < linesToUpdate; i++) {
                UUID uuid = existingUuids.get(i);
                String lineText = i < nonEmptyLines.size() ? nonEmptyLines.get(i) : null;

                Ref<EntityStore> entity = world.getEntityRef(uuid);
                if (entity != null) {
                    Vector3d linePos = getHologramLinePosition(baseHologramPos, newCount, i);
                    applyNametagEntityDisplay(entity, citizen, i, linePos, hologramRot, lineText);
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
                    String lineText = i < nonEmptyLines.size() ? nonEmptyLines.get(i) : null;

                    Vector3d linePos = getHologramLinePosition(baseHologramPos, newCount, i);
                    Holder<EntityStore> holder = createNametagEntityHolder(world, citizen, i, linePos, hologramRot, lineText);
                    if (holder == null) {
                        continue;
                    }

                    UUIDComponent hologramUUIDComponent = holder.getComponent(UUIDComponent.getComponentType());
                    if (hologramUUIDComponent != null) {
                        existingUuids.add(hologramUUIDComponent.getUuid());
                    }
                    world.getEntityStore().getStore().addEntity(holder, AddReason.SPAWN);
                }
            }

            if (save) {
                saveCitizen(citizen);
            }
        });
    }

    public boolean rebindCitizenHologramEntities(@Nonnull World world, @Nonnull CitizenData citizen) {
        int desiredEntityCount = getDesiredNametagEntityCount(citizen);
        if (desiredEntityCount <= 0) {
            citizen.getHologramLineUuids().clear();
            return true;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        Query<EntityStore> query = CitizenNametagComponent.getComponentType();
        Map<Integer, Ref<EntityStore>> resolvedRefs = new ConcurrentHashMap<>();

        store.forEachEntityParallel(query, (index, archetypeChunk, cb) -> {
            CitizenNametagComponent nametagComponent =
                    archetypeChunk.getComponent(index, CitizenNametagComponent.getComponentType());
            if (nametagComponent == null || !citizen.getId().equals(nametagComponent.getCitizenId())) {
                return;
            }

            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }

            resolvedRefs.put(nametagComponent.getLineIndex(), ref);
        });

        if (resolvedRefs.isEmpty()) {
            return false;
        }

        List<UUID> reboundUuids = new ArrayList<>();
        for (int i = 0; i < desiredEntityCount; i++) {
            Ref<EntityStore> ref = resolvedRefs.get(i);
            if (ref == null || !ref.isValid()) {
                return false;
            }

            UUIDComponent uuidComponent = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                return false;
            }

            if (!bindCitizenNametagIdentity(ref, citizen.getId(), i)) {
                return false;
            }

            reboundUuids.add(uuidComponent.getUuid());
        }

        citizen.setHologramLineUuids(reboundUuids);
        return true;
    }

    public boolean migrateLegacyCitizenHologramEntities(@Nonnull World world, @Nonnull CitizenData citizen) {
        List<UUID> storedUuids = citizen.getHologramLineUuids();
        if (storedUuids == null || storedUuids.isEmpty()) {
            return false;
        }

        List<UUID> migratedUuids = new ArrayList<>();
        for (int i = 0; i < storedUuids.size(); i++) {
            UUID uuid = storedUuids.get(i);
            if (uuid == null) {
                return false;
            }

            Ref<EntityStore> ref = world.getEntityRef(uuid);
            if (ref == null || !ref.isValid()) {
                return false;
            }

            UUIDComponent uuidComponent = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                return false;
            }

            if (!bindCitizenNametagIdentity(ref, citizen.getId(), i)) {
                return false;
            }

            migratedUuids.add(uuidComponent.getUuid());
        }

        citizen.setHologramLineUuids(migratedUuids);
        return true;
    }

    public void rotateCitizenToPlayer(CitizenData citizen, PlayerRef playerRef) {
        if (!isPlayerRefValid(playerRef)) {
            return;
        }

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
            Direction bodyDirection = new Direction(yaw, 0, 0f);

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

            sendRotationUpdate(citizenNetworkId, playerRef, lookDirection, bodyDirection);
        }
    }

    public void clearPlayerRuntimeState(@Nonnull UUID playerUuid) {
        for (CitizenData citizen : citizens.values()) {
            cancelPendingLookReset(citizen, playerUuid);
            citizen.lastLookDirections.remove(playerUuid);
            cancelPendingNametagLookReset(citizen, playerUuid);
            citizen.lastNametagLookDirections.remove(playerUuid);
            citizen.getSequentialMessageIndex().remove(playerUuid);
            citizen.getSequentialCommandIndex().remove(playerUuid);
            citizen.getSequentialDeathMessageIndex().remove(playerUuid);
            citizen.getSequentialDeathCommandIndex().remove(playerUuid);
            citizen.getSequentialFirstInteractionMessageIndex().remove(playerUuid);
            citizen.getSequentialFirstInteractionCommandIndex().remove(playerUuid);
            citizen.getPlayersInProximity().remove(playerUuid);
        }
    }

    private void scheduleLookResetIfNeeded(@Nonnull CitizenData citizen, @Nonnull UUID playerUuid) {
        if (!citizen.lastLookDirections.containsKey(playerUuid)) {
            cancelPendingLookReset(citizen, playerUuid);
            return;
        }

        ScheduledFuture<?> existingTask = citizen.getPendingLookResetTasks().get(playerUuid);
        if (existingTask != null && !existingTask.isDone()) {
            return;
        }

        final ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];
        futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.schedule(() ->
                        resetCitizenLookForPlayer(citizen, playerUuid, futureRef[0]),
                LOOK_RESET_DELAY_MS,
                TimeUnit.MILLISECONDS);

        citizen.getPendingLookResetTasks().put(playerUuid, futureRef[0]);
    }

    private void resetCitizenLookForPlayer(@Nonnull CitizenData citizen, @Nonnull UUID playerUuid,
                                           @Nonnull ScheduledFuture<?> expectedTask) {
        ScheduledFuture<?> activeTask = citizen.getPendingLookResetTasks().get(playerUuid);
        if (activeTask != expectedTask) {
            return;
        }

        UUID worldUuid = citizen.getWorldUUID();
        World world = Universe.get().getWorld(worldUuid);
        if (world == null) {
            clearPendingLookResetState(citizen, playerUuid, expectedTask);
            return;
        }

        world.execute(() -> {
            try {
                ScheduledFuture<?> worldThreadTask = citizen.getPendingLookResetTasks().get(playerUuid);
                if (worldThreadTask != expectedTask) {
                    return;
                }

                PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
                if (!isPlayerRefValid(playerRef)) {
                    clearPendingLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                if (!worldUuid.equals(playerRef.getWorldUuid())) {
                    clearPendingLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                if (!citizen.lastLookDirections.containsKey(playerUuid)) {
                    clearPendingLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                if (shouldRotateCitizenTowardsPlayer(citizen, playerRef)) {
                    clearPendingLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
                    clearPendingLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                NetworkId citizenNetworkId = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), NetworkId.getComponentType());
                TransformComponent npcTransformComponent = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), TransformComponent.getComponentType());
                if (citizenNetworkId == null || npcTransformComponent == null) {
                    clearPendingLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                Vector3f baseRotation = npcTransformComponent.getRotation();
                Direction baseLookDirection = toPacketDirection(baseRotation, true);
                Direction baseBodyDirection = toPacketDirection(baseRotation, true);
                sendRotationUpdate(citizenNetworkId, playerRef, baseLookDirection, baseBodyDirection);

                citizen.lastLookDirections.remove(playerUuid);
                clearPendingLookResetState(citizen, playerUuid, expectedTask);
            } catch (Exception e) {
                clearPendingLookResetState(citizen, playerUuid, expectedTask);
                getLogger().atWarning().withCause(e)
                        .log("Failed to reset look rotation for citizen " + citizen.getId() + " and player " + playerUuid);
            }
        });
    }

    private void cancelPendingLookReset(@Nonnull CitizenData citizen, @Nonnull UUID playerUuid) {
        ScheduledFuture<?> existingTask = citizen.getPendingLookResetTasks().remove(playerUuid);
        if (existingTask != null) {
            existingTask.cancel(false);
        }
    }

    private void cancelAllPendingLookResets(@Nonnull CitizenData citizen) {
        for (Map.Entry<UUID, ScheduledFuture<?>> entry : citizen.getPendingLookResetTasks().entrySet()) {
            ScheduledFuture<?> task = entry.getValue();
            if (task != null) {
                task.cancel(false);
            }
        }
        citizen.getPendingLookResetTasks().clear();

        for (Map.Entry<UUID, ScheduledFuture<?>> entry : citizen.getPendingNametagLookResetTasks().entrySet()) {
            ScheduledFuture<?> task = entry.getValue();
            if (task != null) {
                task.cancel(false);
            }
        }
        citizen.getPendingNametagLookResetTasks().clear();
    }

    private void clearPendingLookResetState(@Nonnull CitizenData citizen, @Nonnull UUID playerUuid,
                                            @Nonnull ScheduledFuture<?> expectedTask) {
        citizen.getPendingLookResetTasks().remove(playerUuid, expectedTask);
    }

    @Nullable
    private Ref<EntityStore> getPrimaryNametagRef(@Nonnull World world, @Nonnull CitizenData citizen) {
        if (!shouldUseModelNametag(citizen)) {
            return null;
        }

        List<UUID> hologramUuids = citizen.getHologramLineUuids();
        if (hologramUuids.isEmpty() || hologramUuids.get(0) == null) {
            return null;
        }

        Ref<EntityStore> nametagRef = world.getEntityRef(hologramUuids.get(0));
        return nametagRef != null && nametagRef.isValid() ? nametagRef : null;
    }

    private boolean shouldRotateCitizenTowardsPlayer(@Nonnull CitizenData citizen, @Nonnull PlayerRef playerRef) {
        if (!isPlayerRefValid(playerRef)) {
            return false;
        }

        if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
            return false;
        }

        if (!citizen.getRotateTowardsPlayer()) {
            return false;
        }

        if (!"IDLE".equals(citizen.getMovementBehavior().getType())) {
            return false;
        }

        Vector3d citizenPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        float maxDistance = Math.max(0.0f, citizen.getLookAtDistance());
        double maxDistanceSq = maxDistance * maxDistance;
        return distanceSquared(playerRef.getTransform().getPosition(), citizenPos) <= maxDistanceSq;
    }

    private boolean shouldRotateModelNametagTowardsPlayer(@Nonnull CitizenData citizen, @Nonnull PlayerRef playerRef) {
        if (!isPlayerRefValid(playerRef)) {
            return false;
        }

        if (!shouldUseModelNametag(citizen) || !citizen.isRotateNametagTowardsPlayer()) {
            return false;
        }

        UUID worldUuid = citizen.getWorldUUID();
        if (!worldUuid.equals(playerRef.getWorldUuid())) {
            return false;
        }

        World world = Universe.get().getWorld(worldUuid);
        if (world == null || getPrimaryNametagRef(world, citizen) == null) {
            return false;
        }

        Vector3d citizenPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        float maxDistance = Math.max(0.0f, citizen.getLookAtDistance());
        double maxDistanceSq = maxDistance * maxDistance;
        return distanceSquared(playerRef.getTransform().getPosition(), citizenPos) <= maxDistanceSq;
    }

    private double distanceSquared(@Nonnull Vector3d first, @Nonnull Vector3d second) {
        double dx = first.x - second.x;
        double dy = first.y - second.y;
        double dz = first.z - second.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean isPlayerRefValid(@Nullable PlayerRef playerRef) {
        if (playerRef == null || playerRef.getPacketHandler() == null || playerRef.getWorldUuid() == null) {
            return false;
        }

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        return playerEntityRef != null && playerEntityRef.isValid();
    }

    private Direction toPacketDirection(@Nonnull Vector3f rotation, boolean includePitch) {
        // Transform rotations are stored as Euler XYZ, while packet Direction expects yaw/pitch/roll.
        float yaw = rotation.y;
        float pitch = includePitch ? rotation.x : 0.0f;
        return new Direction(yaw, pitch, rotation.z);
    }

    private void sendRotationUpdate(@Nonnull NetworkId citizenNetworkId, @Nonnull PlayerRef playerRef,
                                    @Nonnull Direction lookDirection, @Nonnull Direction bodyDirection) {
        if (!isPlayerRefValid(playerRef)) {
            return;
        }

        ModelTransform transform = new ModelTransform();
        transform.lookOrientation = lookDirection;
        transform.bodyOrientation = bodyDirection;

        TransformUpdate update = new TransformUpdate(transform);
        EntityUpdate entityUpdate = new EntityUpdate(
                citizenNetworkId.getId(),
                null,
                new ComponentUpdate[] { update }
        );

        EntityUpdates packet = new EntityUpdates(null, new EntityUpdate[] { entityUpdate });
        playerRef.getPacketHandler().write(packet);
    }

    private void rotateModelNametagToPlayer(@Nonnull CitizenData citizen, @Nonnull PlayerRef playerRef) {
        if (!isPlayerRefValid(playerRef)) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        Ref<EntityStore> nametagRef = getPrimaryNametagRef(world, citizen);
        if (nametagRef == null || nametagRef.getStore() == null) {
            return;
        }

        NetworkId nametagNetworkId = nametagRef.getStore().getComponent(nametagRef, NetworkId.getComponentType());
        TransformComponent nametagTransform = nametagRef.getStore().getComponent(nametagRef, TransformComponent.getComponentType());
        if (nametagNetworkId == null || nametagTransform == null) {
            return;
        }

        Vector3d entityPos = nametagTransform.getPosition();
        Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());

        double dx = playerPos.x - entityPos.x;
        double dz = playerPos.z - entityPos.z;
        float yaw = (float) (Math.atan2(dx, dz) + Math.PI);

        double dy = playerPos.y - entityPos.y;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) Math.atan2(dy, horizontalDistance);

        Direction lookDirection = new Direction(yaw, pitch, 0f);
        Direction bodyDirection = new Direction(yaw, pitch, 0f);

        UUID playerUuid = playerRef.getUuid();
        Direction lastLook = citizen.lastNametagLookDirections.get(playerUuid);
        if (lastLook != null) {
            float yawDiff = Math.abs(lookDirection.yaw - lastLook.yaw);
            float pitchDiff = Math.abs(lookDirection.pitch - lastLook.pitch);
            if (yawDiff < 0.02f && pitchDiff < 0.02f) {
                return;
            }
        }

        citizen.lastNametagLookDirections.put(playerUuid, lookDirection);
        sendRotationUpdate(nametagNetworkId, playerRef, lookDirection, bodyDirection);
    }

    private void scheduleNametagLookResetIfNeeded(@Nonnull CitizenData citizen, @Nonnull UUID playerUuid) {
        if (!citizen.lastNametagLookDirections.containsKey(playerUuid)) {
            cancelPendingNametagLookReset(citizen, playerUuid);
            return;
        }

        ScheduledFuture<?> existingTask = citizen.getPendingNametagLookResetTasks().get(playerUuid);
        if (existingTask != null && !existingTask.isDone()) {
            return;
        }

        final ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];
        futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.schedule(() ->
                        resetModelNametagLookForPlayer(citizen, playerUuid, futureRef[0]),
                LOOK_RESET_DELAY_MS,
                TimeUnit.MILLISECONDS);

        citizen.getPendingNametagLookResetTasks().put(playerUuid, futureRef[0]);
    }

    private void resetModelNametagLookForPlayer(@Nonnull CitizenData citizen, @Nonnull UUID playerUuid, @Nonnull ScheduledFuture<?> expectedTask) {
        ScheduledFuture<?> activeTask = citizen.getPendingNametagLookResetTasks().get(playerUuid);
        if (activeTask != expectedTask) {
            return;
        }

        UUID worldUuid = citizen.getWorldUUID();
        World world = Universe.get().getWorld(worldUuid);
        if (world == null) {
            clearPendingNametagLookResetState(citizen, playerUuid, expectedTask);
            return;
        }

        world.execute(() -> {
            try {
                ScheduledFuture<?> worldThreadTask = citizen.getPendingNametagLookResetTasks().get(playerUuid);
                if (worldThreadTask != expectedTask) {
                    return;
                }

                PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
                if (!isPlayerRefValid(playerRef) || !worldUuid.equals(playerRef.getWorldUuid())) {
                    clearPendingNametagLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                if (!citizen.lastNametagLookDirections.containsKey(playerUuid)) {
                    clearPendingNametagLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                if (shouldRotateModelNametagTowardsPlayer(citizen, playerRef)) {
                    clearPendingNametagLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                Ref<EntityStore> nametagRef = getPrimaryNametagRef(world, citizen);
                if (nametagRef == null) {
                    clearPendingNametagLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                NetworkId nametagNetworkId = nametagRef.getStore().getComponent(nametagRef, NetworkId.getComponentType());
                TransformComponent nametagTransform = nametagRef.getStore().getComponent(nametagRef, TransformComponent.getComponentType());
                if (nametagNetworkId == null || nametagTransform == null) {
                    clearPendingNametagLookResetState(citizen, playerUuid, expectedTask);
                    return;
                }

                Vector3f baseRotation = nametagTransform.getRotation();
                Direction baseLookDirection = toPacketDirection(baseRotation, true);
                Direction baseBodyDirection = toPacketDirection(baseRotation, true);
                sendRotationUpdate(nametagNetworkId, playerRef, baseLookDirection, baseBodyDirection);

                citizen.lastNametagLookDirections.remove(playerUuid);
                clearPendingNametagLookResetState(citizen, playerUuid, expectedTask);
            } catch (Exception e) {
                clearPendingNametagLookResetState(citizen, playerUuid, expectedTask);
                getLogger().atWarning().withCause(e).log("Failed to reset nametag look rotation for citizen " + citizen.getId() + " and player " + playerUuid);
            }
        });
    }

    private void cancelPendingNametagLookReset(@Nonnull CitizenData citizen, @Nonnull UUID playerUuid) {
        ScheduledFuture<?> existingTask = citizen.getPendingNametagLookResetTasks().remove(playerUuid);
        if (existingTask != null) {
            existingTask.cancel(false);
        }
    }

    private void clearPendingNametagLookResetState(@Nonnull CitizenData citizen, @Nonnull UUID playerUuid, @Nonnull ScheduledFuture<?> expectedTask) {
        citizen.getPendingNametagLookResetTasks().remove(playerUuid, expectedTask);
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
        animationTask.scheduleAtFixedRate("citizens-animations", () -> {
            long now = System.currentTimeMillis();

            for (CitizenData citizen : citizens.values()) {
                if (citizen.getSpawnedUUID() == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid())
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
        roleGenerator.generateRoleIfChanged(citizen);
        String generatedRoleName = scheduleManager != null
                ? scheduleManager.getDesiredRoleName(citizen)
                : roleGenerator.getRoleName(citizen);

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
        UUID expectedNpcUuid = citizen.getSpawnedUUID();
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                int roleIndex = NPCPlugin.get().getIndex(generatedRoleName);
                if (roleIndex == Integer.MIN_VALUE) {
                    getLogger().atWarning().log("Generated role '" + generatedRoleName + "' still not registered after retry. Role may have failed to generate.");
                    return;
                }

                if (expectedNpcUuid == null || !expectedNpcUuid.equals(citizen.getSpawnedUUID())) {
                    return;
                }

                if (isCitizenSpawning(citizen.getId())) {
                    return;
                }

                Ref<EntityStore> npcRef = citizen.getNpcRef();
                if (npcRef == null || !npcRef.isValid()) {
                    getLogger().atWarning().log("Cannot apply role '" + generatedRoleName + "': NPC ref is no longer valid.");
                    return;
                }

                World world = Universe.get().getWorld(citizen.getWorldUUID());
                if (world == null) {
                    return;
                }

                world.execute(() -> {
                    if (expectedNpcUuid == null || !expectedNpcUuid.equals(citizen.getSpawnedUUID())) {
                        return;
                    }

                    Ref<EntityStore> currentRef = citizen.getNpcRef();
                    if (currentRef == null || !currentRef.isValid()) {
                        return;
                    }

                    updateSpawnedCitizenNPC(citizen, false);
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

    @Nonnull
    public ScheduleManager getScheduleManager() {
        return scheduleManager;
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

    public void addCitizenAddedListener(@Nonnull CitizenAddedListener listener) {
        addedListeners.add(listener);
    }

    public void removeCitizenAddedListener(@Nonnull CitizenAddedListener listener) {
        addedListeners.remove(listener);
    }

    public void fireCitizenAddedEvent(@Nonnull CitizenAddedEvent event) {
        for (CitizenAddedListener listener : addedListeners) {
            listener.onCitizenAdded(event);
        }
    }

    public void addCitizenRemovedListener(@Nonnull CitizenRemovedListener listener) {
        removedListeners.add(listener);
    }

    public void removeCitizenRemovedListener(@Nonnull CitizenRemovedListener listener) {
        removedListeners.remove(listener);
    }

    public void fireCitizenRemovedEvent(@Nonnull CitizenRemovedEvent event) {
        for (CitizenRemovedListener listener : removedListeners) {
            listener.onCitizenRemoved(event);
        }
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

    public boolean renameGroup(@Nonnull String oldGroupName, @Nonnull String newGroupName) {
        String oldTrimmed = oldGroupName.trim();
        String newTrimmed = newGroupName.trim();
        if (oldTrimmed.isEmpty() || newTrimmed.isEmpty()) {
            return false;
        }
        if (!groups.contains(oldTrimmed) || groups.contains(newTrimmed)) {
            return false;
        }

        groups.remove(oldTrimmed);
        groups.add(newTrimmed);

        for (CitizenData citizen : citizens.values()) {
            if (oldTrimmed.equals(citizen.getGroup())) {
                citizen.setGroup(newTrimmed);
                saveCitizen(citizen);
            }
        }

        saveGroups();
        return true;
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

    private void tickStandaloneFollowCitizen(@Nonnull CitizenData citizen) {
        if (citizen.getScheduleConfig().isEnabled()
                && citizen.getCurrentScheduleRuntimeState() != ScheduleRuntimeState.INACTIVE) {
            clearStandaloneFollowState(citizen.getId(), standaloneFollowSessions.containsKey(citizen.getId()));
            return;
        }

        boolean shouldFollow = "FOLLOW_CITIZEN".equals(citizen.getMovementBehavior().getType())
                && !citizen.getFollowCitizenId().trim().isEmpty();

        if (!shouldFollow) {
            clearStandaloneFollowState(citizen.getId(), standaloneFollowSessions.containsKey(citizen.getId()));
            return;
        }

        CitizenData leader = citizens.get(citizen.getFollowCitizenId());
        if (leader == null || leader.getId().equals(citizen.getId()) || !citizen.getWorldUUID().equals(leader.getWorldUUID())) {
            clearStandaloneFollowState(citizen.getId(), true);
            return;
        }

        Vector3d leaderPosition = getCitizenPositionSnapshot(leader);
        if (leaderPosition == null) {
            clearStandaloneFollowState(citizen.getId(), true);
            return;
        }

        FollowSession session = standaloneFollowSessions.computeIfAbsent(citizen.getId(), ignored -> new FollowSession());
        Vector3d targetPosition = computeStandaloneFollowTargetPosition(citizen, leader, leaderPosition, session);
        if (targetPosition == null) {
            clearStandaloneFollowState(citizen.getId(), true);
            return;
        }

        double leaderShiftSq = session.lastLeaderPosition == null
                ? Double.MAX_VALUE
                : distanceSq(session.lastLeaderPosition, leaderPosition);
        session.lastLeaderPosition = new Vector3d(leaderPosition.x, leaderPosition.y, leaderPosition.z);

        double targetShiftSq = session.lastTargetPosition == null
                ? Double.MAX_VALUE
                : distanceSq(session.lastTargetPosition, targetPosition);
        Vector3d followerPosition = getCitizenPositionSnapshot(citizen);
        double followerDistanceSq = distanceSq(followerPosition, targetPosition);
        double settleRadius = Math.max(0.35f, Math.min(0.9f, citizen.getFollowDistance() * 0.35f));
        double settleRadiusSq = settleRadius * settleRadius;
        double retargetDistance = Math.max(0.4, citizen.getFollowDistance() * 0.35);
        double retargetDistanceSq = retargetDistance * retargetDistance;
        boolean shouldMoveTarget = targetShiftSq > retargetDistanceSq
                || followerDistanceSq > settleRadiusSq
                || leaderShiftSq > 0.04;

        updateCitizenLeashPoint(citizen, targetPosition);
        if (shouldMoveTarget) {
            updateCitizenMoveTarget(citizen.getId(), targetPosition);
            session.lastTargetPosition = new Vector3d(targetPosition.x, targetPosition.y, targetPosition.z);
        } else if (followerDistanceSq <= settleRadiusSq) {
            stopCitizenMovement(citizen.getId());
        }
    }

    private void clearStandaloneFollowState(@Nonnull String citizenId, boolean stopMovement) {
        standaloneFollowSessions.remove(citizenId);
        if (stopMovement) {
            stopCitizenMovement(citizenId);
        }
    }

    private void tickWanderUnstick(@Nonnull CitizenData citizen) {
        if (!isBaseWanderMovement(citizen)) {
            wanderRecoveryStates.remove(citizen.getId());
            return;
        }

        if (citizen.isAwaitingRespawn() || isCitizenSpawning(citizen.getId())) {
            return;
        }

        Ref<EntityStore> npcRef = citizen.getNpcRef();
        if (npcRef == null || !npcRef.isValid()) {
            wanderRecoveryStates.remove(citizen.getId());
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null || !isCitizenChunkLoaded(world, citizen)) {
            wanderRecoveryStates.remove(citizen.getId());
            return;
        }

        Vector3d currentPosition = getCitizenPositionSnapshot(citizen);
        if (currentPosition == null) {
            wanderRecoveryStates.remove(citizen.getId());
            return;
        }

        long now = System.currentTimeMillis();
        WanderRecoveryState state = wanderRecoveryStates.computeIfAbsent(citizen.getId(), ignored -> new WanderRecoveryState());
        if (state.lastProgressPosition == null
                || distanceSq(state.lastProgressPosition, currentPosition) >= WANDER_PROGRESS_DISTANCE_SQUARED) {
            state.lastProgressPosition = new Vector3d(currentPosition.x, currentPosition.y, currentPosition.z);
            state.lastProgressAtMs = now;
            return;
        }

        if ((now - state.lastProgressAtMs) < WANDER_STUCK_TIMEOUT_MS) {
            return;
        }

        if ((now - state.lastRecoveryAtMs) < WANDER_RECOVERY_COOLDOWN_MS) {
            return;
        }

        long stalledForMs = now - state.lastProgressAtMs;
        state.lastRecoveryAtMs = now;
        state.lastProgressAtMs = now;
        state.lastProgressPosition = new Vector3d(citizen.getPosition().x, citizen.getPosition().y, citizen.getPosition().z);

        //getLogger().atInfo().log("Wander reset for citizen '" + citizen.getId() + "' after " + stalledForMs + "ms without movement. Returning to spawn.");
        teleportCitizenToSpawn(citizen);
    }

    private boolean isBaseWanderMovement(@Nonnull CitizenData citizen) {
        if (citizen.getCurrentScheduleRuntimeState() != ScheduleRuntimeState.INACTIVE) {
            return false;
        }

        String movementType = citizen.getMovementBehavior().getType();
        return "WANDER".equals(movementType)
                || "WANDER_CIRCLE".equals(movementType)
                || "WANDER_RECT".equals(movementType);
    }

    @Nullable
    private Vector3d computeStandaloneFollowTargetPosition(@Nonnull CitizenData follower, @Nonnull CitizenData leader,
                                                           @Nonnull Vector3d leaderPosition, @Nonnull FollowSession session) {
        List<CitizenData> activeFollowers = new ArrayList<>();
        for (CitizenData otherCitizen : citizens.values()) {
            if (!"FOLLOW_CITIZEN".equals(otherCitizen.getMovementBehavior().getType())) {
                continue;
            }
            if (!leader.getId().equals(otherCitizen.getFollowCitizenId())) {
                continue;
            }
            activeFollowers.add(otherCitizen);
        }

        activeFollowers.sort(Comparator.comparing(CitizenData::getId));
        int followerIndex = Math.max(0, activeFollowers.indexOf(follower));
        int slotsPerRing = 6;
        int ringIndex = followerIndex / slotsPerRing;
        int slotIndex = followerIndex % slotsPerRing;
        int followersInRing = Math.min(slotsPerRing, Math.max(1, activeFollowers.size() - (ringIndex * slotsPerRing)));

        double baseAngle = session.anchorAngleRadians;
        if (!Double.isFinite(baseAngle)) {
            baseAngle = Math.PI;
        }

        if (session.lastLeaderPosition != null) {
            double dx = leaderPosition.x - session.lastLeaderPosition.x;
            double dz = leaderPosition.z - session.lastLeaderPosition.z;
            if ((dx * dx) + (dz * dz) > 0.04) {
                baseAngle = Math.atan2(dz, dx) + Math.PI;
            }
        }

        double angle = baseAngle;
        if (followersInRing > 1) {
            angle += (Math.PI * 2.0 * slotIndex) / followersInRing;
        }
        session.anchorAngleRadians = baseAngle;

        double radius = Math.max(0.1f, follower.getFollowDistance()) + (ringIndex * 0.85);
        return new Vector3d(
                leaderPosition.x + (Math.cos(angle) * radius),
                leaderPosition.y,
                leaderPosition.z + (Math.sin(angle) * radius)
        );
    }

    @Nullable
    private Vector3d getCitizenPositionSnapshot(@Nonnull CitizenData citizen) {
        Vector3d currentPosition = citizen.getCurrentPosition();
        if (currentPosition != null) {
            return currentPosition;
        }
        return citizen.getPosition();
    }

    private double distanceSq(@Nullable Vector3d first, @Nullable Vector3d second) {
        if (first == null || second == null) {
            return Double.MAX_VALUE;
        }
        double dx = first.x - second.x;
        double dy = first.y - second.y;
        double dz = first.z - second.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void updateCitizenLeashPoint(@Nonnull CitizenData citizen, @Nonnull Vector3d position) {
        Ref<EntityStore> npcRef = citizen.getNpcRef();
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        world.execute(() -> {
            NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, NPCEntity.getComponentType());
            if (npcEntity != null) {
                npcEntity.setLeashPoint(position);
            }
        });
    }

    public void teleportCitizenToSpawn(@Nonnull CitizenData citizen) {
        teleportCitizen(citizen, citizen.getPosition(), citizen.getRotation());
    }

    public void teleportCitizen(@Nonnull CitizenData citizen, @Nonnull Vector3d position, @Nullable Vector3f rotation) {
        Ref<EntityStore> npcRef = citizen.getNpcRef();
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        Vector3d targetPosition = new Vector3d(position.x, position.y, position.z);
        Vector3f targetRotation = rotation == null ? null : new Vector3f(rotation);
        world.execute(() -> {
            Ref<EntityStore> liveRef = citizen.getNpcRef();
            if (liveRef == null || !liveRef.isValid()) {
                return;
            }

            TransformComponent transformComponent = liveRef.getStore().getComponent(liveRef, TransformComponent.getComponentType());
            if (transformComponent != null) {
                transformComponent.setPosition(targetPosition);
                if (targetRotation != null) {
                    transformComponent.setRotation(targetRotation);
                }
            }

            NPCEntity npcEntity = liveRef.getStore().getComponent(liveRef, NPCEntity.getComponentType());
            if (npcEntity != null) {
                npcEntity.setLeashPoint(targetPosition);
            }

            citizen.setCurrentPosition(new Vector3d(targetPosition.x, targetPosition.y, targetPosition.z));
        });
    }

    @Nonnull
    public PatrolManager getPatrolManager() {
        return patrolManager;
    }

    public void startCitizenPatrol(@Nonnull String citizenId, @Nonnull String pathName) {
        CitizenData citizen = citizens.get(citizenId);
        if (citizen == null) {
            return;
        }
        if (!"PATROL".equals(citizen.getMovementBehavior().getType())) {
            return;
        }
        patrolManager.startPatrol(citizenId, pathName);
    }

    public void stopCitizenPatrol(@Nonnull String citizenId) {
        patrolManager.stopPatrol(citizenId);
    }

    public boolean renamePatrolPath(@Nonnull String oldName, @Nonnull String newName) {
        if (!patrolManager.renamePath(oldName, newName)) {
            return false;
        }

        for (CitizenData citizen : citizens.values()) {
            PathConfig pathConfig = citizen.getPathConfig();
            boolean changed = false;
            if (oldName.equals(pathConfig.getPluginPatrolPath())) {
                pathConfig.setPluginPatrolPath(newName);
                changed = true;
            }
            if (oldName.equals(pathConfig.getPathName())) {
                pathConfig.setPathName(newName);
                changed = true;
            }
            if (changed) {
                saveCitizen(citizen);
            }
        }

        return true;
    }

    public void moveCitizenToPosition(@Nonnull String citizenId, @Nonnull Vector3d position) {
        patrolManager.moveCitizenToPosition(citizenId, position);
    }

    public void updateCitizenMoveTarget(@Nonnull String citizenId, @Nonnull Vector3d position) {
        patrolManager.updateMoveTargetPosition(citizenId, position);
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
