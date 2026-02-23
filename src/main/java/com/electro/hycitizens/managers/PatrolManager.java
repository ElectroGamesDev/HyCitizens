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
    private static final double ARRIVAL_DISTANCE_SQUARED = 4.0;
    private static final long MONITOR_INTERVAL_MS = 250;

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

        PatrolSession(@Nonnull String citizenId, @Nonnull String pathName) {
            this.citizenId = citizenId;
            this.pathName = pathName;
            this.waypointIndex = 0;
            this.forward = true;
            this.pauseUntilMs = 0;
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

        Ref<EntityStore> targetRef = moveTargets.get(session.citizenId);
        if (targetRef == null) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        List<PatrolWaypoint> waypoints = path.getWaypoints();
        int capturedIndex = session.waypointIndex;

        world.execute(() -> {
            if (!targetRef.isValid()) {
                return;
            }

            TransformComponent targetTransform = targetRef.getStore().getComponent(targetRef, TransformComponent.getComponentType());
            if (targetTransform == null) {
                return;
            }

            if (resumingFromPause) {
                targetTransform.setPosition(waypoints.get(capturedIndex).toVector3d());
                return;
            }

            TransformComponent npcTransform = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), TransformComponent.getComponentType());
            if (npcTransform == null) {
                return;
            }

            PatrolWaypoint currentWaypoint = waypoints.get(capturedIndex);
            Vector3d npcPos = npcTransform.getPosition();
            Vector3d wp = currentWaypoint.toVector3d();
            double dx = wp.x - npcPos.x;
            double dy = wp.y - npcPos.y;
            double dz = wp.z - npcPos.z;

            if (dx * dx + dy * dy + dz * dz > ARRIVAL_DISTANCE_SQUARED) {
                return;
            }

            int nextIndex = computeNextIndex(capturedIndex, session.forward, path);
            session.waypointIndex = nextIndex;
            if (path.getLoopMode() == PatrolPath.LoopMode.PING_PONG) {
                session.forward = computeNextForward(capturedIndex, session.forward, waypoints.size());
            }

            float pause = currentWaypoint.getPauseSeconds();
            if (pause > 0) {
                session.pauseUntilMs = System.currentTimeMillis() + (long) (pause * 1000);
            } else {
                targetTransform.setPosition(waypoints.get(nextIndex).toVector3d());
            }
        });
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

            NPCEntity npcEntity = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(), NPCEntity.getComponentType());
            if (npcEntity == null || npcEntity.getRole() == null) {
                return;
            }

            Role role = npcEntity.getRole();
            role.getMarkedEntitySupport().setMarkedEntity("LockedTarget", targetRef);
        } catch (Exception e) {
            getLogger().atWarning().log("Failed to create move target for citizen " + citizen.getId() + ": " + e.getMessage());
        }
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

        stopPatrol(citizenId);

        CitizenData citizen = citizensManager.getCitizen(citizenId);
        if (citizen == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        PatrolSession session = new PatrolSession(citizenId, pathName);
        activeSessions.put(citizenId, session);

        Vector3d firstWaypoint = path.getWaypoints().get(0).toVector3d();
        world.execute(() -> spawnMoveTarget(citizen, world, new Vector3d(firstWaypoint.x, firstWaypoint.y, firstWaypoint.z)));
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
        stopPatrol(citizenId);

        CitizenData citizen = citizensManager.getCitizen(citizenId);
        if (citizen == null || citizen.getNpcRef() == null || !citizen.getNpcRef().isValid()) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        world.execute(() -> spawnMoveTarget(citizen, world, new Vector3d(position.x, position.y, position.z)));
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
        moveTargets.remove(citizenId);
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
