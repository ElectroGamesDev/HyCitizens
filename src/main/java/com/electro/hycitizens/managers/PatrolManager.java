package com.electro.hycitizens.managers;

import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.PatrolPath;
import com.electro.hycitizens.models.PatrolWaypoint;
import com.electro.hycitizens.util.ConfigManager;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class PatrolManager {
    private static final String LOCKED_TARGET_SLOT = "LockedTarget";
    private static final String MOVE_TARGET_SLOT = "MoveTarget";
    private static final double ARRIVAL_DISTANCE_SQUARED = 4.0;
    private static final long MONITOR_INTERVAL_MS = 250;
    private static final long PATROL_STUCK_TIMEOUT_MS = 10_000L;
    private static final long PATROL_RECOVERY_COOLDOWN_MS = 4_000L;
    private static final double PATROL_PROGRESS_DISTANCE_SQUARED = 0.64;
    private static final double PATROL_PROGRESS_DISTANCE_DELTA = 0.75;

    private final ConfigManager config;
    private final CitizensManager citizensManager;
    private final Map<String, PatrolPath> paths = new ConcurrentHashMap<>();
    private final Map<String, PatrolSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Ref<EntityStore>> moveTargets = new ConcurrentHashMap<>();
    private ScheduledFuture<?> monitorTask;

    private static final class PatrolSession {
        final String citizenId;
        final String pathName;
        volatile int waypointIndex;
        volatile boolean forward;
        volatile long pauseUntilMs;
        volatile int trackedWaypointIndex;
        volatile Vector3d lastProgressPosition;
        volatile double lastProgressDistance;
        volatile long lastProgressAtMs;
        volatile long lastRecoveryAtMs;
        volatile int recoveryStage;

        PatrolSession(@Nonnull String citizenId, @Nonnull String pathName) {
            this.citizenId = citizenId;
            this.pathName = pathName;
            this.waypointIndex = 0;
            this.forward = true;
            this.pauseUntilMs = 0;
            this.trackedWaypointIndex = -1;
            this.lastProgressPosition = null;
            this.lastProgressDistance = Double.MAX_VALUE;
            this.lastProgressAtMs = System.currentTimeMillis();
            this.lastRecoveryAtMs = 0L;
            this.recoveryStage = 0;
        }
    }

    public PatrolManager(@Nonnull ConfigManager config, @Nonnull CitizensManager citizensManager) {
        this.config = config;
        this.citizensManager = citizensManager;
        loadPaths();
        startMonitor();
    }

    private void startMonitor() {
        monitorTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            for (PatrolSession session : activeSessions.values()) {
                try {
                    tickSession(session);
                } catch (Exception e) {
                    getLogger().atWarning().log("Patrol tick error for citizen " + session.citizenId + ": " + e.getMessage());
                }
            }
        }, MONITOR_INTERVAL_MS, MONITOR_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void tickSession(@Nonnull PatrolSession session) {
        long now = System.currentTimeMillis();
        if (session.pauseUntilMs != 0 && now < session.pauseUntilMs) {
            return;
        }

        boolean resumingFromPause = session.pauseUntilMs != 0;
        if (resumingFromPause) {
            session.pauseUntilMs = 0;
        }

        PatrolPath path = paths.get(session.pathName);
        if (path == null || path.getWaypoints().isEmpty()) {
            stopPatrol(session.citizenId);
            return;
        }

        CitizenData citizen = citizensManager.getCitizen(session.citizenId);
        if (citizen == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
            return;
        }
        if (citizen.isAwaitingRespawn() || citizensManager.isCitizenSpawning(citizen.getId())) {
            return;
        }
        if (citizensManager.isCitizenInAiBusyState(citizen)) {
            session.lastProgressAtMs = System.currentTimeMillis();
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        List<PatrolWaypoint> waypoints = path.getWaypoints();
        int capturedIndex = session.waypointIndex;
        if (capturedIndex < 0 || capturedIndex >= waypoints.size()) {
            session.waypointIndex = 0;
            capturedIndex = 0;
        }
        final int currentIndex = capturedIndex;

        world.execute(() -> {
            Vector3d waypointPosition = waypoints.get(currentIndex).toVector3d();
            Ref<EntityStore> targetRef = ensureMoveTarget(citizen, world, waypointPosition);
            if (targetRef == null || !targetRef.isValid()) {
                return;
            }

            TransformComponent targetTransform = targetRef.getStore().getComponent(targetRef, TransformComponent.getComponentType());
            if (targetTransform == null) {
                return;
            }

            if (resumingFromPause) {
                targetTransform.setPosition(waypointPosition);
                resetProgressTracking(session, citizen.getCurrentPosition(), Double.MAX_VALUE, currentIndex);
                return;
            }

            Ref<EntityStore> npcRef = citizen.getNpcRef();
            if (npcRef == null || !npcRef.isValid()) {
                return;
            }

            TransformComponent npcTransform = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
            if (npcTransform == null) {
                return;
            }

            PatrolWaypoint currentWaypoint = waypoints.get(currentIndex);
            Vector3d npcPos = npcTransform.getPosition();
            Vector3d wp = waypointPosition;
            double dx = wp.x - npcPos.x;
            double dy = wp.y - npcPos.y;
            double dz = wp.z - npcPos.z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            double distance = Math.sqrt(distanceSq);

            if (hasPatrolProgressed(session, npcPos, distance, currentIndex)) {
                markPatrolProgress(session, npcPos, distance, currentIndex, true);
            }

            if (distanceSq > ARRIVAL_DISTANCE_SQUARED) {
                maybeRecoverStuckPatrol(session, citizen, world, path, npcPos, distance, currentIndex);
                return;
            }

            markPatrolProgress(session, npcPos, distance, currentIndex, true);

            int nextIndex = computeNextIndex(currentIndex, session.forward, path);
            session.waypointIndex = nextIndex;
            if (path.getLoopMode() == PatrolPath.LoopMode.PING_PONG) {
                session.forward = computeNextForward(currentIndex, session.forward, waypoints.size());
            }

            float pause = currentWaypoint.getPauseSeconds();
            if (pause > 0) {
                session.pauseUntilMs = System.currentTimeMillis() + (long) (pause * 1000);
            } else {
                targetTransform.setPosition(waypoints.get(nextIndex).toVector3d());
                resetProgressTracking(session, npcPos, distanceTo(waypoints.get(nextIndex).toVector3d(), npcPos), nextIndex);
            }
        });
    }

    private void maybeRecoverStuckPatrol(@Nonnull PatrolSession session, @Nonnull CitizenData citizen,
                                         @Nonnull World world, @Nonnull PatrolPath path,
                                         @Nonnull Vector3d npcPosition, double distanceToWaypoint, int waypointIndex) {
        long now = System.currentTimeMillis();
        if (session.pauseUntilMs != 0 && now < session.pauseUntilMs) {
            return;
        }
        if ((now - session.lastProgressAtMs) < PATROL_STUCK_TIMEOUT_MS) {
            return;
        }
        if ((now - session.lastRecoveryAtMs) < PATROL_RECOVERY_COOLDOWN_MS) {
            return;
        }

        session.lastRecoveryAtMs = now;
        int recoveryStage = Math.min(session.recoveryStage, 2);
        session.recoveryStage = Math.min(recoveryStage + 1, 2);

        switch (recoveryStage) {
            case 0 -> {
                //getLogger().atInfo().log("Patrol soft reset for citizen '" + citizen.getId() + "' at waypoint " + waypointIndex + " after " + (now - session.lastProgressAtMs) + "ms without progress.");
                Ref<EntityStore> targetRef = ensureMoveTarget(citizen, world, path.getWaypoints().get(waypointIndex).toVector3d());
                if (targetRef != null && targetRef.isValid()) {
                    TransformComponent targetTransform = targetRef.getStore().getComponent(targetRef, TransformComponent.getComponentType());
                    if (targetTransform != null) {
                        targetTransform.setPosition(path.getWaypoints().get(waypointIndex).toVector3d());
                    }
                }
            }
            case 1 -> {
                int nearestIndex = findNearestWaypointIndex(path, npcPosition);
                //getLogger().atInfo().log("Patrol route reset for citizen '" + citizen.getId() + "' to waypoint " + nearestIndex + " after repeated stall.");
                restartPatrolInternal(citizen, world, path, nearestIndex, true);
                PatrolSession restartedSession = activeSessions.get(citizen.getId());
                if (restartedSession != null) {
                    restartedSession.recoveryStage = 2;
                    restartedSession.lastRecoveryAtMs = now;
                }
            }
            default -> {
                //getLogger().atWarning().log("Patrol teleport reset for citizen '" + citizen.getId() + "' after repeated stall. Returning to spawn and restarting patrol.");
                citizensManager.teleportCitizenToSpawn(citizen);
                restartPatrolInternal(citizen, world, path, 0, true);
                PatrolSession restartedSession = activeSessions.get(citizen.getId());
                if (restartedSession != null) {
                    restartedSession.recoveryStage = 2;
                    restartedSession.lastRecoveryAtMs = now;
                }
            }
        }

        resetProgressTracking(session, npcPosition, distanceToWaypoint, waypointIndex);
    }

    private boolean hasPatrolProgressed(@Nonnull PatrolSession session, @Nonnull Vector3d npcPosition,
                                        double distanceToWaypoint, int waypointIndex) {
        if (session.trackedWaypointIndex != waypointIndex || session.lastProgressPosition == null) {
            return true;
        }

        double movedSq = distanceSq(session.lastProgressPosition, npcPosition);
        if (movedSq >= PATROL_PROGRESS_DISTANCE_SQUARED) {
            return true;
        }

        return (session.lastProgressDistance - distanceToWaypoint) >= PATROL_PROGRESS_DISTANCE_DELTA;
    }

    private void markPatrolProgress(@Nonnull PatrolSession session, @Nonnull Vector3d npcPosition,
                                    double distanceToWaypoint, int waypointIndex, boolean resetRecoveryStage) {
        session.trackedWaypointIndex = waypointIndex;
        session.lastProgressPosition = new Vector3d(npcPosition.x, npcPosition.y, npcPosition.z);
        session.lastProgressDistance = distanceToWaypoint;
        session.lastProgressAtMs = System.currentTimeMillis();
        if (resetRecoveryStage) {
            session.recoveryStage = 0;
        }
    }

    private void resetProgressTracking(@Nonnull PatrolSession session, @Nullable Vector3d npcPosition,
                                       double distanceToWaypoint, int waypointIndex) {
        session.trackedWaypointIndex = waypointIndex;
        session.lastProgressPosition = npcPosition == null ? null : new Vector3d(npcPosition.x, npcPosition.y, npcPosition.z);
        session.lastProgressDistance = distanceToWaypoint;
        session.lastProgressAtMs = System.currentTimeMillis();
    }

    private int computeNextIndex(int current, boolean forward, @Nonnull PatrolPath path) {
        int count = path.getWaypoints().size();
        if (count <= 1) {
            return 0;
        }
        if (path.getLoopMode() == PatrolPath.LoopMode.LOOP) {
            return (current + 1) % count;
        }
        if (forward) {
            int next = current + 1;
            return next >= count ? count - 2 : next;
        } else {
            int next = current - 1;
            return next < 0 ? 1 : next;
        }
    }

    private boolean computeNextForward(int current, boolean forward, int count) {
        if (forward && current >= count - 1) {
            return false;
        }
        if (!forward && current <= 0) {
            return true;
        }
        return forward;
    }

    private int findNearestWaypointIndex(@Nonnull PatrolPath path, @Nonnull Vector3d position) {
        List<PatrolWaypoint> waypoints = path.getWaypoints();
        int nearestIndex = 0;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (int i = 0; i < waypoints.size(); i++) {
            double candidateDistanceSq = distanceSq(waypoints.get(i).toVector3d(), position);
            if (candidateDistanceSq < nearestDistanceSq) {
                nearestDistanceSq = candidateDistanceSq;
                nearestIndex = i;
            }
        }

        return nearestIndex;
    }

    private double distanceSq(@Nonnull Vector3d first, @Nonnull Vector3d second) {
        double dx = first.x - second.x;
        double dy = first.y - second.y;
        double dz = first.z - second.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private double distanceTo(@Nonnull Vector3d first, @Nonnull Vector3d second) {
        return Math.sqrt(distanceSq(first, second));
    }

    private void spawnMoveTarget(@Nonnull CitizenData citizen, @Nonnull World world, @Nonnull Vector3d position) {
        try {
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

            ProjectileComponent projectile = new ProjectileComponent("Projectile");
            holder.putComponent(ProjectileComponent.getComponentType(), projectile);

            holder.putComponent(TransformComponent.getComponentType(), new TransformComponent(position, new Vector3f(0, 0, 0)));
            holder.ensureComponent(UUIDComponent.getComponentType());
            holder.ensureComponent(Intangible.getComponentType());
            holder.addComponent(NetworkId.getComponentType(),
                    new NetworkId(world.getEntityStore().getStore().getExternalData().takeNextNetworkId()));

            projectile.initialize();

            Ref<EntityStore> targetRef = world.getEntityStore().getStore().addEntity(holder, AddReason.SPAWN);
            if (targetRef == null || !targetRef.isValid()) {
                getLogger().atWarning().log("Failed to spawn move target entity for citizen " + citizen.getId());
                return;
            }

            moveTargets.put(citizen.getId(), targetRef);

            Ref<EntityStore> npcRef = citizen.getNpcRef();
            if (npcRef == null || !npcRef.isValid()) {
                return;
            }

            NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, NPCEntity.getComponentType());
            if (npcEntity == null || npcEntity.getRole() == null) {
                return;
            }

            Role role = npcEntity.getRole();
            role.getMarkedEntitySupport().setMarkedEntity(getMovementTargetSlot(role), targetRef);
        } catch (Exception e) {
            getLogger().atWarning().log("Failed to create move target for citizen " + citizen.getId() + ": " + e.getMessage());
        }
    }

    @Nullable
    private Ref<EntityStore> ensureMoveTarget(@Nonnull CitizenData citizen, @Nonnull World world, @Nonnull Vector3d position) {
        Ref<EntityStore> targetRef = moveTargets.get(citizen.getId());
        if (targetRef != null && targetRef.isValid()) {
            TransformComponent targetTransform = targetRef.getStore().getComponent(targetRef, TransformComponent.getComponentType());
            if (targetTransform != null) {
                targetTransform.setPosition(new Vector3d(position.x, position.y, position.z));
                bindMoveTarget(citizen, targetRef);
                return targetRef;
            }
        }

        spawnMoveTarget(citizen, world, position);
        Ref<EntityStore> spawnedRef = moveTargets.get(citizen.getId());
        if (spawnedRef != null && spawnedRef.isValid()) {
            bindMoveTarget(citizen, spawnedRef);
        }
        return spawnedRef;
    }

    private void bindMoveTarget(@Nonnull CitizenData citizen, @Nonnull Ref<EntityStore> targetRef) {
        Ref<EntityStore> npcRef = citizen.getNpcRef();
        if (npcRef == null || !npcRef.isValid() || !targetRef.isValid()) {
            return;
        }

        NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, NPCEntity.getComponentType());
        if (npcEntity == null || npcEntity.getRole() == null || npcEntity.getRole().getMarkedEntitySupport() == null) {
            return;
        }

        TransformComponent targetTransform = targetRef.getStore().getComponent(targetRef, TransformComponent.getComponentType());
        if (targetTransform == null) {
            return;
        }

        npcEntity.getRole().getMarkedEntitySupport().setMarkedEntity(getMovementTargetSlot(npcEntity.getRole()), targetRef);
        npcEntity.setLeashPoint(targetTransform.getPosition());
    }

    @Nonnull
    private String getMovementTargetSlot(@Nonnull Role role) {
        return role.getStateSupport() != null ? MOVE_TARGET_SLOT : LOCKED_TARGET_SLOT;
    }

    public boolean citizenMayNeedMoveTarget(@Nonnull CitizenData citizen) {
        if ("PATROL".equals(citizen.getMovementBehavior().getType())) {
            return true;
        }
        if ("FOLLOW_CITIZEN".equals(citizen.getMovementBehavior().getType())
                && !citizen.getFollowCitizenId().trim().isEmpty()) {
            return true;
        }
        return citizen.getScheduleConfig().isEnabled();
    }

    public void ensureMoveTargetNow(@Nonnull CitizenData citizen, @Nonnull World world, @Nullable Vector3d position) {
        if (!citizenMayNeedMoveTarget(citizen)) {
            return;
        }
        if (citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
            return;
        }

        Vector3d targetPosition = position;
        if (targetPosition == null) {
            targetPosition = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        }
        ensureMoveTarget(citizen, world, new Vector3d(targetPosition.x, targetPosition.y, targetPosition.z));
    }

    private void cleanupMoveTarget(@Nonnull CitizenData citizen, @Nonnull World world) {
        Ref<EntityStore> targetRef = moveTargets.remove(citizen.getId());
        if (targetRef == null) {
            return;
        }
        try {
            if (targetRef.isValid()) {
                world.getEntityStore().getStore().removeEntity(targetRef, RemoveReason.REMOVE);
            }
        } catch (Exception ignored) {
        }
    }

    public void startPatrol(@Nonnull String citizenId, @Nonnull String pathName) {
        PatrolPath path = paths.get(pathName);
        if (path == null || path.getWaypoints().isEmpty()) {
            getLogger().atWarning().log("Cannot start patrol on unknown or empty path: " + pathName);
            return;
        }

        CitizenData citizen = citizensManager.getCitizen(citizenId);
        if (citizen == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
            return;
        }
        if (citizen.isAwaitingRespawn() || citizensManager.isCitizenSpawning(citizen.getId())) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        activeSessions.remove(citizenId);
        world.execute(() -> {
            cleanupMoveTarget(citizen, world);
            restartPatrolInternal(citizen, world, path, 0, true);
        });
    }

    public void stopPatrol(@Nonnull String citizenId) {
        PatrolSession session = activeSessions.remove(citizenId);
        if (session == null && !moveTargets.containsKey(citizenId)) {
            return;
        }

        CitizenData citizen = citizensManager.getCitizen(citizenId);
        if (citizen == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
            moveTargets.remove(citizenId);
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            moveTargets.remove(citizenId);
            return;
        }

        world.execute(() -> cleanupMoveTarget(citizen, world));
    }

    public void moveCitizenToPosition(@Nonnull String citizenId, @Nonnull Vector3d position) {
        CitizenData citizen = citizensManager.getCitizen(citizenId);
        if (citizen == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
            return;
        }
        if (citizen.isAwaitingRespawn() || citizensManager.isCitizenSpawning(citizen.getId())) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        activeSessions.remove(citizenId);
        world.execute(() -> {
            cleanupMoveTarget(citizen, world);
            spawnMoveTarget(citizen, world, new Vector3d(position.x, position.y, position.z));
        });
    }

    public void updateMoveTargetPosition(@Nonnull String citizenId, @Nonnull Vector3d position) {
        CitizenData citizen = citizensManager.getCitizen(citizenId);
        if (citizen == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
            return;
        }
        if (citizen.isAwaitingRespawn() || citizensManager.isCitizenSpawning(citizen.getId())) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        world.execute(() -> {
            ensureMoveTarget(citizen, world, new Vector3d(position.x, position.y, position.z));
        });
    }

    private void restartPatrolInternal(@Nonnull CitizenData citizen, @Nonnull World world,
                                       @Nonnull PatrolPath path, int startIndex, boolean forward) {
        cleanupMoveTarget(citizen, world);

        PatrolSession session = new PatrolSession(citizen.getId(), path.getName());
        session.waypointIndex = Math.max(0, Math.min(startIndex, path.getWaypoints().size() - 1));
        session.forward = forward;
        activeSessions.put(citizen.getId(), session);

        Vector3d waypointPosition = path.getWaypoints().get(session.waypointIndex).toVector3d();
        Ref<EntityStore> npcRef = citizen.getNpcRef();
        Vector3d npcPosition = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        if (npcRef != null && npcRef.isValid()) {
            TransformComponent npcTransform = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
            if (npcTransform != null) {
                npcPosition = npcTransform.getPosition();
            }
        }

        ensureMoveTarget(citizen, world, waypointPosition);
        resetProgressTracking(session, npcPosition, distanceTo(waypointPosition, npcPosition), session.waypointIndex);
    }

    public boolean isPatrolPaused(@Nonnull String citizenId) {
        PatrolSession session = activeSessions.get(citizenId);
        return session != null && session.pauseUntilMs != 0 && System.currentTimeMillis() < session.pauseUntilMs;
    }

    public void stopMoving(@Nonnull String citizenId) {
        stopPatrol(citizenId);
    }

    public boolean isPatrolling(@Nonnull String citizenId) {
        return activeSessions.containsKey(citizenId);
    }

    @Nullable
    public String getActivePatrolPath(@Nonnull String citizenId) {
        PatrolSession session = activeSessions.get(citizenId);
        return session != null ? session.pathName : null;
    }

    @Nullable
    public PatrolPath getPath(@Nonnull String name) {
        return paths.get(name);
    }

    @Nonnull
    public Collection<PatrolPath> getAllPaths() {
        return Collections.unmodifiableCollection(paths.values());
    }

    @Nonnull
    public List<String> getAllPathNames() {
        List<String> names = new ArrayList<>(paths.keySet());
        Collections.sort(names);
        return names;
    }

    public void savePath(@Nonnull PatrolPath path) {
        paths.put(path.getName(), path);
        writePathToConfig(path);
    }

    public boolean deletePath(@Nonnull String name) {
        if (paths.remove(name) == null) {
            return false;
        }
        config.set("paths." + name, null);
        return true;
    }

    public boolean renamePath(@Nonnull String oldName, @Nonnull String newName) {
        String oldTrimmed = oldName.trim();
        String newTrimmed = newName.trim();
        if (oldTrimmed.isEmpty() || newTrimmed.isEmpty()) {
            return false;
        }
        if (oldTrimmed.equals(newTrimmed)) {
            return true;
        }
        if (!paths.containsKey(oldTrimmed) || paths.containsKey(newTrimmed)) {
            return false;
        }

        PatrolPath path = paths.remove(oldTrimmed);
        if (path == null) {
            return false;
        }

        path.setName(newTrimmed);
        paths.put(newTrimmed, path);

        config.set("paths." + oldTrimmed, null);
        writePathToConfig(path);

        List<String> toRestart = new ArrayList<>();
        for (PatrolSession session : activeSessions.values()) {
            if (oldTrimmed.equals(session.pathName)) {
                toRestart.add(session.citizenId);
            }
        }

        for (String citizenId : toRestart) {
            stopPatrol(citizenId);
            startPatrol(citizenId, newTrimmed);
        }

        return true;
    }

    public boolean addWaypoint(@Nonnull String pathName, @Nonnull PatrolWaypoint waypoint) {
        PatrolPath path = paths.get(pathName);
        if (path == null) {
            return false;
        }
        path.addWaypoint(waypoint);
        writePathToConfig(path);
        return true;
    }

    public boolean removeWaypoint(@Nonnull String pathName, int index) {
        PatrolPath path = paths.get(pathName);
        if (path == null) {
            return false;
        }
        List<PatrolWaypoint> waypoints = path.getWaypoints();
        if (index < 0 || index >= waypoints.size()) {
            return false;
        }
        waypoints.remove(index);
        writePathToConfig(path);
        return true;
    }

    public boolean setWaypointPause(@Nonnull String pathName, int index, float pauseSeconds) {
        PatrolPath path = paths.get(pathName);
        if (path == null) {
            return false;
        }
        List<PatrolWaypoint> waypoints = path.getWaypoints();
        if (index < 0 || index >= waypoints.size()) {
            return false;
        }
        waypoints.get(index).setPauseSeconds(pauseSeconds);
        writePathToConfig(path);
        return true;
    }

    public void onCitizenDespawned(@Nonnull String citizenId) {
        activeSessions.remove(citizenId);
        Ref<EntityStore> targetRef = moveTargets.remove(citizenId);
        if (targetRef == null || !targetRef.isValid()) return;

        CitizenData citizen = citizensManager.getCitizen(citizenId);
        if (citizen == null) return;

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) return;

        world.execute(() -> {
            try {
                if (targetRef.isValid()) {
                    world.getEntityStore().getStore().removeEntity(targetRef, RemoveReason.REMOVE);
                }
            } catch (Exception ignored) {}
        });
    }

    public void shutdown() {
        if (monitorTask != null && !monitorTask.isCancelled()) {
            monitorTask.cancel(false);
        }
    }

    private void loadPaths() {
        Set<String> pathNames = config.getKeys("paths");
        for (String name : pathNames) {
            String basePath = "paths." + name;
            String loopModeStr = config.getString(basePath + ".loop-mode", "LOOP");
            PatrolPath.LoopMode loopMode;
            try {
                loopMode = PatrolPath.LoopMode.valueOf(loopModeStr);
            } catch (IllegalArgumentException e) {
                loopMode = PatrolPath.LoopMode.LOOP;
            }

            PatrolPath path = new PatrolPath(name, loopMode);
            int count = config.getInt(basePath + ".waypoints.count", 0);
            for (int i = 0; i < count; i++) {
                String wpPath = basePath + ".waypoints." + i;
                double x = config.getDouble(wpPath + ".x", 0);
                double y = config.getDouble(wpPath + ".y", 0);
                double z = config.getDouble(wpPath + ".z", 0);
                float pause = config.getFloat(wpPath + ".pause", 0);
                path.addWaypoint(new PatrolWaypoint(x, y, z, pause));
            }

            paths.put(name, path);
        }
    }

    private void writePathToConfig(@Nonnull PatrolPath path) {
        String basePath = "paths." + path.getName();
        config.beginBatch();
        try {
            config.set(basePath + ".loop-mode", path.getLoopMode().name());
            List<PatrolWaypoint> waypoints = path.getWaypoints();
            config.set(basePath + ".waypoints.count", waypoints.size());
            for (int i = 0; i < waypoints.size(); i++) {
                PatrolWaypoint wp = waypoints.get(i);
                String wpPath = basePath + ".waypoints." + i;
                config.set(wpPath + ".x", wp.getX());
                config.set(wpPath + ".y", wp.getY());
                config.set(wpPath + ".z", wp.getZ());
                config.set(wpPath + ".pause", wp.getPauseSeconds());
            }
        } finally {
            config.endBatch();
        }
    }
}
