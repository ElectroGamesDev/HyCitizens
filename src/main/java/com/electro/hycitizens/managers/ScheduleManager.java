package com.electro.hycitizens.managers;

import com.electro.hycitizens.models.*;
import com.electro.hycitizens.roles.RoleGenerator;
import com.electro.hycitizens.util.ThreadedScheduler;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class ScheduleManager {
    private static final long TICK_INTERVAL_MS = 1000L;

    private static final class ScheduleSession {
        private String activeEntryId = "";
        private String currentRoleName = "";
        private String currentLocationId = "";
        private ScheduleRuntimeState state = ScheduleRuntimeState.INACTIVE;
        private boolean arrivalAnimationPlayed = false;
        private String followLeaderCitizenId = "";
        private Vector3d lastFollowLeaderPosition = null;
        private Vector3d lastFollowTargetPosition = null;
        private double followAnchorAngleRadians = Double.NaN;
    }

    private final CitizensManager citizensManager;
    private final Map<String, ScheduleSession> sessions = new ConcurrentHashMap<>();
    private ThreadedScheduler task = new ThreadedScheduler();

    public ScheduleManager(@Nonnull CitizensManager citizensManager) {
        this.citizensManager = citizensManager;
        start();
    }

    private void start() {
        task.scheduleAtFixedRate("citizens-schedule-manager", () -> {
            for (CitizenData citizen : citizensManager.getAllCitizens()) {
                try {
                    tickCitizen(citizen);
                } catch (Exception e) {
                    getLogger().atWarning().log("Schedule tick error for citizen " + citizen.getId() + ": " + e.getMessage());
                }
            }
        }, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        task.stop();
        sessions.clear();
    }

    public void clearCitizen(@Nonnull String citizenId) {
        sessions.remove(citizenId);
    }

    public void refreshCitizen(@Nonnull CitizenData citizen) {
        tickCitizen(citizen);
    }

    @Nonnull
    public String getDesiredRoleName(@Nonnull CitizenData citizen) {
        ScheduleSession session = sessions.get(citizen.getId());
        if (session != null && !session.currentRoleName.isEmpty()) {
            return session.currentRoleName;
        }
        return citizensManager.getRoleGenerator().getRoleName(citizen);
    }

    private void tickCitizen(@Nonnull CitizenData citizen) {
        if (citizen.isAwaitingRespawn() || citizensManager.isCitizenSpawning(citizen.getId())) {
            return;
        }

        ScheduleConfig scheduleConfig = citizen.getScheduleConfig();
        if (!scheduleConfig.isEnabled() || scheduleConfig.getEntries().isEmpty()) {
            applyBaseBehavior(citizen, "Schedule inactive");
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            setBlockedState(citizen, "Schedule blocked: world missing");
            return;
        }

        WorldTimeResource worldTimeResource = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        if (worldTimeResource == null) {
            setBlockedState(citizen, "Schedule blocked: time resource missing");
            return;
        }

        LocalDateTime gameDateTime = worldTimeResource.getGameDateTime();
        double time24 = gameDateTime.getHour() + (gameDateTime.getMinute() / 60.0);

        ScheduleEntry activeEntry = selectActiveEntry(scheduleConfig.getEntries(), time24);
        if (activeEntry == null) {
            applyFallback(citizen, scheduleConfig);
            return;
        }

        Optional<ScheduleLocation> targetLocationOpt = scheduleConfig.findLocation(activeEntry.getLocationId());
        if (targetLocationOpt.isEmpty()) {
            setBlockedState(citizen, "Schedule blocked: location missing");
            return;
        }

        ScheduleLocation targetLocation = targetLocationOpt.get();
        if (!citizen.getWorldUUID().equals(targetLocation.getWorldUUID())) {
            setBlockedState(citizen, "Schedule blocked: location world mismatch");
            return;
        }

        ScheduleSession session = sessions.computeIfAbsent(citizen.getId(), ignored -> new ScheduleSession());
        boolean arrived = isCitizenWithinRadius(citizen, targetLocation.getPosition(), activeEntry.getArrivalRadius());

        if (!activeEntry.getId().equals(session.activeEntryId) || !targetLocation.getId().equals(session.currentLocationId)) {
            session.arrivalAnimationPlayed = false;
            if (!arrived) {
                startTravel(citizen, activeEntry, targetLocation, session);
                return;
            }
            activateEntry(citizen, activeEntry, targetLocation, session);
            return;
        }

        if (session.state == ScheduleRuntimeState.TRAVELING) {
            if (arrived) {
                activateEntry(citizen, activeEntry, targetLocation, session);
            } else {
                maintainTravel(citizen, activeEntry, targetLocation, session);
            }
            return;
        }

        maintainActiveEntry(citizen, activeEntry, targetLocation, session);
    }

    @Nullable
    private ScheduleEntry selectActiveEntry(@Nonnull List<ScheduleEntry> entries, double time24) {
        return entries.stream()
                .filter(ScheduleEntry::isEnabled)
                .filter(entry -> entry.isActiveAt(time24))
                .sorted(Comparator.comparingInt(ScheduleEntry::getPriority).reversed())
                .findFirst()
                .orElse(null);
    }

    private void startTravel(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry,
                             @Nonnull ScheduleLocation location, @Nonnull ScheduleSession session) {
        RoleGenerator roleGenerator = citizensManager.getRoleGenerator();
        String travelRoleName = roleGenerator.getScheduleTravelRoleName(citizen, entry);
        updateCitizenLeashPoint(citizen, location.getPosition());
        citizensManager.stopCitizenPatrol(citizen.getId());
        citizensManager.moveCitizenToPosition(citizen.getId(), location.getPosition());
        switchCitizenRole(citizen, travelRoleName, true);

        session.activeEntryId = entry.getId();
        session.currentLocationId = location.getId();
        session.currentRoleName = travelRoleName;
        session.state = ScheduleRuntimeState.TRAVELING;
        resetFollowSession(session);

        citizen.setCurrentScheduleEntryId(entry.getId());
        citizen.setCurrentScheduleRoleName(travelRoleName);
        citizen.setCurrentScheduleRuntimeState(ScheduleRuntimeState.TRAVELING);
        citizen.setCurrentScheduleStatusText("Traveling to " + location.getName() + " for " + entry.getName());
    }

    private void maintainTravel(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry,
                                @Nonnull ScheduleLocation location, @Nonnull ScheduleSession session) {
        String travelRoleName = citizensManager.getRoleGenerator().getScheduleTravelRoleName(citizen, entry);
        if (!travelRoleName.equals(session.currentRoleName)) {
            switchCitizenRole(citizen, travelRoleName, true);
            session.currentRoleName = travelRoleName;
            citizen.setCurrentScheduleRoleName(travelRoleName);
        }
        citizen.setCurrentScheduleRuntimeState(ScheduleRuntimeState.TRAVELING);
        citizen.setCurrentScheduleStatusText("Traveling to " + location.getName() + " for " + entry.getName());
    }

    private void activateEntry(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry,
                               @Nonnull ScheduleLocation location, @Nonnull ScheduleSession session) {
        citizensManager.stopCitizenMovement(citizen.getId());
        updateCitizenLeashPoint(citizen, location.getPosition());
        if (entry.getActivityType() != ScheduleActivityType.FOLLOW_CITIZEN) {
            applyCitizenRotation(citizen, location.getRotation());
        }

        String entryRoleName = citizensManager.getRoleGenerator().getScheduleEntryRoleName(citizen, entry);
        boolean roleNeedsMoveTarget = entry.getActivityType() == ScheduleActivityType.PATROL
                || entry.getActivityType() == ScheduleActivityType.FOLLOW_CITIZEN;
        switchCitizenRole(citizen, entryRoleName, roleNeedsMoveTarget);

        if (entry.getActivityType() == ScheduleActivityType.PATROL) {
            citizensManager.stopCitizenPatrol(citizen.getId());
            if (!entry.getPatrolPathName().isEmpty()) {
                citizensManager.getPatrolManager().startPatrol(citizen.getId(), entry.getPatrolPathName());
            }
        } else if (entry.getActivityType() == ScheduleActivityType.FOLLOW_CITIZEN) {
            citizensManager.stopCitizenPatrol(citizen.getId());
        } else {
            citizensManager.stopCitizenPatrol(citizen.getId());
        }

        if (!session.arrivalAnimationPlayed && !entry.getArrivalAnimationName().isEmpty()) {
            citizensManager.playAnimationForCitizen(citizen, entry.getArrivalAnimationName(), entry.getArrivalAnimationSlot());
            session.arrivalAnimationPlayed = true;
        }

        session.activeEntryId = entry.getId();
        session.currentLocationId = location.getId();
        session.currentRoleName = entryRoleName;
        session.state = ScheduleRuntimeState.ACTIVE;

        citizen.setCurrentScheduleEntryId(entry.getId());
        citizen.setCurrentScheduleRoleName(entryRoleName);
        citizen.setCurrentScheduleRuntimeState(ScheduleRuntimeState.ACTIVE);

        if (entry.getActivityType() == ScheduleActivityType.FOLLOW_CITIZEN) {
            if (!updateFollowTarget(citizen, entry, session)) {
                return;
            }
        } else {
            resetFollowSession(session);
        }

        citizen.setCurrentScheduleStatusText(describeActiveState(citizen, entry, location));
    }

    private void maintainActiveEntry(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry,
                                     @Nonnull ScheduleLocation location, @Nonnull ScheduleSession session) {
        String desiredRoleName = citizensManager.getRoleGenerator().getScheduleEntryRoleName(citizen, entry);
        if (!desiredRoleName.equals(session.currentRoleName)) {
            boolean roleNeedsMoveTarget = entry.getActivityType() == ScheduleActivityType.PATROL
                    || entry.getActivityType() == ScheduleActivityType.FOLLOW_CITIZEN;
            switchCitizenRole(citizen, desiredRoleName, roleNeedsMoveTarget);
            session.currentRoleName = desiredRoleName;
            citizen.setCurrentScheduleRoleName(desiredRoleName);
        }

        if (entry.getActivityType() == ScheduleActivityType.PATROL) {
            String activePatrol = citizensManager.getCitizenActivePatrolPath(citizen.getId());
            if (activePatrol == null || !activePatrol.equals(entry.getPatrolPathName())) {
                citizensManager.stopCitizenPatrol(citizen.getId());
                if (!entry.getPatrolPathName().isEmpty()) {
                    citizensManager.getPatrolManager().startPatrol(citizen.getId(), entry.getPatrolPathName());
                }
            }
        } else if (entry.getActivityType() == ScheduleActivityType.FOLLOW_CITIZEN) {
            citizensManager.stopCitizenPatrol(citizen.getId());
            if (!updateFollowTarget(citizen, entry, session)) {
                return;
            }
        } else {
            citizensManager.stopCitizenPatrol(citizen.getId());
            resetFollowSession(session);
        }

        if (entry.getActivityType() != ScheduleActivityType.FOLLOW_CITIZEN) {
            updateCitizenLeashPoint(citizen, location.getPosition());
        }
        citizen.setCurrentScheduleRuntimeState(ScheduleRuntimeState.ACTIVE);
        citizen.setCurrentScheduleStatusText(describeActiveState(citizen, entry, location));
    }

    private void applyFallback(@Nonnull CitizenData citizen, @Nonnull ScheduleConfig scheduleConfig) {
        if (scheduleConfig.getFallbackMode() == ScheduleFallbackMode.HOLD_LAST_SCHEDULE_STATE) {
            ScheduleSession session = sessions.computeIfAbsent(citizen.getId(), ignored -> new ScheduleSession());
            citizen.setCurrentScheduleRuntimeState(session.state);
            citizen.setCurrentScheduleStatusText("Holding last schedule state");
            return;
        }

        if (scheduleConfig.getFallbackMode() == ScheduleFallbackMode.GO_TO_DEFAULT_LOCATION_IDLE
                && !scheduleConfig.getDefaultLocationId().isEmpty()) {
            Optional<ScheduleLocation> defaultLocationOpt = scheduleConfig.findLocation(scheduleConfig.getDefaultLocationId());
            if (defaultLocationOpt.isPresent()) {
                applyDefaultLocationFallback(citizen, defaultLocationOpt.get());
                return;
            }
        }

        applyBaseBehavior(citizen, "Using base behavior");
    }

    private void applyDefaultLocationFallback(@Nonnull CitizenData citizen, @Nonnull ScheduleLocation location) {
        ScheduleSession session = sessions.computeIfAbsent(citizen.getId(), ignored -> new ScheduleSession());
        boolean arrived = isCitizenWithinRadius(citizen, location.getPosition(), 1.5f);

        if (!arrived) {
            String travelRoleName = citizensManager.getRoleGenerator().getScheduleFallbackTravelRoleName(citizen);
            boolean roleChanged = !travelRoleName.equals(session.currentRoleName);
            boolean locationChanged = !location.getId().equals(session.currentLocationId);
            if (roleChanged) {
                switchCitizenRole(citizen, travelRoleName, true);
            }
            updateCitizenLeashPoint(citizen, location.getPosition());
            citizensManager.stopCitizenPatrol(citizen.getId());
            if (roleChanged || locationChanged) {
                citizensManager.moveCitizenToPosition(citizen.getId(), location.getPosition());
            } else {
                citizensManager.updateCitizenMoveTarget(citizen.getId(), location.getPosition());
            }
            session.currentRoleName = travelRoleName;
            session.currentLocationId = location.getId();
            session.activeEntryId = "";
            session.state = ScheduleRuntimeState.FALLBACK;
            resetFollowSession(session);
            citizen.setCurrentScheduleRuntimeState(ScheduleRuntimeState.FALLBACK);
            citizen.setCurrentScheduleRoleName(travelRoleName);
            citizen.setCurrentScheduleStatusText("Traveling to default location " + location.getName());
            return;
        }

        citizensManager.stopCitizenMovement(citizen.getId());
        citizensManager.stopCitizenPatrol(citizen.getId());
        String idleRoleName = citizensManager.getRoleGenerator().getScheduleFallbackIdleRoleName(citizen);
        if (!idleRoleName.equals(session.currentRoleName)) {
            switchCitizenRole(citizen, idleRoleName, false);
        }
        updateCitizenLeashPoint(citizen, location.getPosition());
        applyCitizenRotation(citizen, location.getRotation());
        session.currentRoleName = idleRoleName;
        session.currentLocationId = location.getId();
        session.activeEntryId = "";
        session.state = ScheduleRuntimeState.FALLBACK;
        resetFollowSession(session);
        citizen.setCurrentScheduleRuntimeState(ScheduleRuntimeState.FALLBACK);
        citizen.setCurrentScheduleRoleName(idleRoleName);
        citizen.setCurrentScheduleStatusText("Idle at default location " + location.getName());
    }

    private void applyBaseBehavior(@Nonnull CitizenData citizen, @Nonnull String status) {
        ScheduleSession session = sessions.computeIfAbsent(citizen.getId(), ignored -> new ScheduleSession());
        String baseRoleName = citizensManager.getRoleGenerator().getRoleName(citizen);
        boolean patrolShouldBeRunning = "PATROL".equals(citizen.getMovementBehavior().getType())
                && !citizen.getPathConfig().getPluginPatrolPath().isEmpty();

        if (session.state == ScheduleRuntimeState.INACTIVE && baseRoleName.equals(session.currentRoleName)) {
            if (patrolShouldBeRunning) {
                String activePatrol = citizensManager.getCitizenActivePatrolPath(citizen.getId());
                if (activePatrol == null || !activePatrol.equals(citizen.getPathConfig().getPluginPatrolPath())) {
                    citizensManager.getPatrolManager().startPatrol(citizen.getId(), citizen.getPathConfig().getPluginPatrolPath());
                }
            }
            citizen.setCurrentScheduleEntryId("");
            citizen.setCurrentScheduleRoleName(baseRoleName);
            citizen.setCurrentScheduleRuntimeState(ScheduleRuntimeState.INACTIVE);
            citizen.setCurrentScheduleStatusText(status);
            return;
        }

        citizensManager.stopCitizenMovement(citizen.getId());
        citizensManager.stopCitizenPatrol(citizen.getId());
        boolean roleNeedsMoveTarget = "PATROL".equals(citizen.getMovementBehavior().getType())
                || "FOLLOW_CITIZEN".equals(citizen.getMovementBehavior().getType());
        switchCitizenRole(citizen, baseRoleName, roleNeedsMoveTarget);

        if (patrolShouldBeRunning) {
            citizensManager.getPatrolManager().startPatrol(citizen.getId(), citizen.getPathConfig().getPluginPatrolPath());
        }

        session.activeEntryId = "";
        session.currentLocationId = "";
        session.currentRoleName = baseRoleName;
        session.state = ScheduleRuntimeState.INACTIVE;
        session.arrivalAnimationPlayed = false;
        resetFollowSession(session);

        citizen.setCurrentScheduleEntryId("");
        citizen.setCurrentScheduleRoleName(baseRoleName);
        citizen.setCurrentScheduleRuntimeState(ScheduleRuntimeState.INACTIVE);
        citizen.setCurrentScheduleStatusText(status);
    }

    private void setBlockedState(@Nonnull CitizenData citizen, @Nonnull String message) {
        ScheduleSession session = sessions.computeIfAbsent(citizen.getId(), ignored -> new ScheduleSession());
        citizensManager.stopCitizenMovement(citizen.getId());
        citizensManager.stopCitizenPatrol(citizen.getId());
        if (session.state != ScheduleRuntimeState.BLOCKED || !session.currentRoleName.isEmpty() || !session.activeEntryId.isEmpty()) {
            switchCitizenRole(citizen, citizensManager.getRoleGenerator().getRoleName(citizen), false);
        }
        session.activeEntryId = "";
        session.currentLocationId = "";
        session.currentRoleName = "";
        session.state = ScheduleRuntimeState.BLOCKED;
        session.arrivalAnimationPlayed = false;
        resetFollowSession(session);
        citizen.setCurrentScheduleEntryId("");
        citizen.setCurrentScheduleRoleName("");
        citizen.setCurrentScheduleRuntimeState(ScheduleRuntimeState.BLOCKED);
        citizen.setCurrentScheduleStatusText(message);
    }

    private void resetFollowSession(@Nonnull ScheduleSession session) {
        session.followLeaderCitizenId = "";
        session.lastFollowLeaderPosition = null;
        session.lastFollowTargetPosition = null;
        session.followAnchorAngleRadians = Double.NaN;
    }

    private boolean updateFollowTarget(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry,
                                       @Nonnull ScheduleSession session) {
        CitizenData leader = citizensManager.getCitizen(entry.getFollowCitizenId());
        if (leader == null) {
            setBlockedState(citizen, "Schedule blocked: follow target missing");
            return false;
        }
        if (leader.getId().equals(citizen.getId())) {
            setBlockedState(citizen, "Schedule blocked: citizen cannot follow itself");
            return false;
        }
        if (!citizen.getWorldUUID().equals(leader.getWorldUUID())) {
            setBlockedState(citizen, "Schedule blocked: follow target world mismatch");
            return false;
        }

        Vector3d leaderPosition = getSpawnedCitizenPosition(leader);
        if (leaderPosition == null) {
            setBlockedState(citizen, "Schedule blocked: follow target unavailable");
            return false;
        }

        Vector3d targetPosition = computeFollowTargetPosition(citizen, entry, leader, leaderPosition, session);
        if (targetPosition == null) {
            setBlockedState(citizen, "Schedule blocked: could not compute follow target");
            return false;
        }

        session.followLeaderCitizenId = leader.getId();
        double leaderShiftSq = session.lastFollowLeaderPosition == null
                ? Double.MAX_VALUE
                : distanceSq(session.lastFollowLeaderPosition, leaderPosition);
        session.lastFollowLeaderPosition = new Vector3d(leaderPosition.x, leaderPosition.y, leaderPosition.z);

        double targetShiftSq = session.lastFollowTargetPosition == null
                ? Double.MAX_VALUE
                : distanceSq(session.lastFollowTargetPosition, targetPosition);
        double followerDistanceSq = distanceSq(getCitizenPosition(citizen), targetPosition);
        double settleRadius = Math.max(0.35f, Math.min(0.9f, entry.getFollowDistance() * 0.35f));
        double settleRadiusSq = settleRadius * settleRadius;
        double retargetDistance = Math.max(0.4, entry.getFollowDistance() * 0.35);
        double retargetDistanceSq = retargetDistance * retargetDistance;
        boolean shouldMoveTarget = targetShiftSq > retargetDistanceSq
                || followerDistanceSq > settleRadiusSq
                || leaderShiftSq > 0.04;

        updateCitizenLeashPoint(citizen, targetPosition);
        if (shouldMoveTarget) {
            citizensManager.updateCitizenMoveTarget(citizen.getId(), targetPosition);
            session.lastFollowTargetPosition = new Vector3d(targetPosition.x, targetPosition.y, targetPosition.z);
        } else if (followerDistanceSq <= settleRadiusSq) {
            citizensManager.stopCitizenMovement(citizen.getId());
        }

        citizen.setCurrentScheduleRuntimeState(ScheduleRuntimeState.ACTIVE);
        citizen.setCurrentScheduleStatusText("Following " + leader.getName()
                + " @ " + String.format(java.util.Locale.ROOT, "%.1f", entry.getFollowDistance()));
        return true;
    }

    @Nullable
    private Vector3d computeFollowTargetPosition(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry,
                                                 @Nonnull CitizenData leader, @Nonnull Vector3d leaderPosition,
                                                 @Nonnull ScheduleSession session) {
        List<CitizenData> activeFollowers = new ArrayList<>();
        for (CitizenData otherCitizen : citizensManager.getAllCitizens()) {
            if (otherCitizen.getId().equals(citizen.getId())) {
                activeFollowers.add(otherCitizen);
                continue;
            }

            if (otherCitizen.getCurrentScheduleRuntimeState() != ScheduleRuntimeState.ACTIVE) {
                continue;
            }

            ScheduleConfig otherConfig = otherCitizen.getScheduleConfig();
            if (otherConfig == null) {
                continue;
            }

            Optional<ScheduleEntry> otherEntry = otherConfig.findEntry(otherCitizen.getCurrentScheduleEntryId());
            if (otherEntry.isEmpty() || otherEntry.get().getActivityType() != ScheduleActivityType.FOLLOW_CITIZEN) {
                continue;
            }

            if (leader.getId().equals(otherEntry.get().getFollowCitizenId())) {
                activeFollowers.add(otherCitizen);
            }
        }

        activeFollowers.sort(Comparator.comparing(CitizenData::getId));
        int followerIndex = Math.max(0, activeFollowers.indexOf(citizen));
        int slotsPerRing = 6;
        int ringIndex = followerIndex / slotsPerRing;
        int slotIndex = followerIndex % slotsPerRing;
        int followersInRing = Math.min(slotsPerRing, activeFollowers.size() - (ringIndex * slotsPerRing));

        double baseAngle = session.followAnchorAngleRadians;
        if (!Double.isFinite(baseAngle)) {
            baseAngle = Math.PI;
        }

        if (session.lastFollowLeaderPosition != null) {
            double dx = leaderPosition.x - session.lastFollowLeaderPosition.x;
            double dz = leaderPosition.z - session.lastFollowLeaderPosition.z;
            if ((dx * dx) + (dz * dz) > 0.04) {
                baseAngle = Math.atan2(dz, dx) + Math.PI;
            }
        }

        double angle = baseAngle;
        if (followersInRing > 1) {
            angle += (Math.PI * 2.0 * slotIndex) / followersInRing;
        }
        session.followAnchorAngleRadians = baseAngle;

        double radius = Math.max(0.1f, entry.getFollowDistance()) + (ringIndex * 0.85);
        return new Vector3d(
                leaderPosition.x + (Math.cos(angle) * radius),
                leaderPosition.y,
                leaderPosition.z + (Math.sin(angle) * radius)
        );
    }

    @Nullable
    private Vector3d getSpawnedCitizenPosition(@Nonnull CitizenData citizen) {
        Ref<EntityStore> npcRef = citizen.getNpcRef();
        if (npcRef != null && npcRef.isValid()) {
            TransformComponent transformComponent = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
            if (transformComponent != null) {
                return transformComponent.getPosition();
            }
        }
        return null;
    }

    @Nullable
    private Vector3d getCitizenPosition(@Nonnull CitizenData citizen) {
        Vector3d spawnedPosition = getSpawnedCitizenPosition(citizen);
        if (spawnedPosition != null) {
            return spawnedPosition;
        }
        if (citizen.getCurrentPosition() != null) {
            return citizen.getCurrentPosition();
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

    private boolean isCitizenWithinRadius(@Nonnull CitizenData citizen, @Nonnull Vector3d targetPosition, float radius) {
        Vector3d sourcePosition = getCitizenPosition(citizen);
        double dx = sourcePosition.x - targetPosition.x;
        double dy = sourcePosition.y - targetPosition.y;
        double dz = sourcePosition.z - targetPosition.z;
        double radiusSq = Math.max(0.25f, radius) * Math.max(0.25f, radius);
        return dx * dx + dy * dy + dz * dz <= radiusSq;
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

    private void applyCitizenRotation(@Nonnull CitizenData citizen, @Nonnull Vector3f rotation) {
        Ref<EntityStore> npcRef = citizen.getNpcRef();
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        Vector3f targetRotation = new Vector3f(rotation);
        world.execute(() -> {
            TransformComponent transformComponent = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
            if (transformComponent != null) {
                transformComponent.setRotation(targetRotation);
            }
        });
    }

    private void switchCitizenRole(@Nonnull CitizenData citizen, @Nonnull String roleName, boolean ensureMoveTarget) {
        if (roleName.isEmpty()) {
            return;
        }

        Ref<EntityStore> npcRef = citizen.getNpcRef();
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }

        int roleIndex = NPCPlugin.get().getIndex(roleName);
        if (roleIndex == Integer.MIN_VALUE) {
            getLogger().atWarning().log("Schedule role not registered yet: " + roleName);
            return;
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        world.execute(() -> {
            NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, NPCEntity.getComponentType());
            if (npcEntity == null || npcEntity.getRole() == null) {
                return;
            }
            if (ensureMoveTarget && citizensManager.getPatrolManager() != null) {
                TransformComponent transformComponent =
                        npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
                Vector3d markerPosition = transformComponent != null
                        ? transformComponent.getPosition()
                        : (citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition());
                citizensManager.getPatrolManager().ensureMoveTargetNow(citizen, world, markerPosition);
            }
            RoleChangeSystem.requestRoleChange(npcRef, npcEntity.getRole(), roleIndex, true, npcRef.getStore());
            HytaleServer.SCHEDULED_EXECUTOR.schedule(
                    () -> citizensManager.refreshSpawnedCitizenAppearance(citizen),
                    50,
                    TimeUnit.MILLISECONDS
            );
        });
    }

    @Nonnull
    private String describeActiveState(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry, @Nonnull ScheduleLocation location) {
        return switch (entry.getActivityType()) {
            case IDLE -> "Idle at " + location.getName();
            case WANDER -> "Wandering around " + location.getName();
            case PATROL -> "Patrolling " + entry.getPatrolPathName() + " from " + location.getName();
            case FOLLOW_CITIZEN -> {
                CitizenData leader = citizensManager.getCitizen(entry.getFollowCitizenId());
                yield leader != null
                        ? "Following " + leader.getName() + " @ " + String.format(java.util.Locale.ROOT, "%.1f", entry.getFollowDistance())
                        : "Following from " + location.getName();
            }
        };
    }
}
