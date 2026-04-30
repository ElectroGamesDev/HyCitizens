package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class PathConfig {
    private boolean followPath;
    private String pathName;
    private boolean patrol;
    private float patrolWanderDistance;
    private String loopMode;
    private String pluginPatrolPath;
    private float pluginPatrolSpeed;

    public PathConfig() {
        this.followPath = false;
        this.pathName = "";
        this.patrol = false;
        this.patrolWanderDistance = 25;
        this.loopMode = "LOOP";
        this.pluginPatrolPath = "";
        this.pluginPatrolSpeed = 2.0f;
    }

    public void copyFrom(@Nonnull PathConfig other) {
        this.followPath = other.followPath;
        this.pathName = other.pathName;
        this.patrol = other.patrol;
        this.patrolWanderDistance = other.patrolWanderDistance;
        this.loopMode = other.loopMode;
        this.pluginPatrolPath = other.pluginPatrolPath;
        this.pluginPatrolSpeed = other.pluginPatrolSpeed;
    }

    public boolean isFollowPath() { return followPath; }
    public void setFollowPath(boolean followPath) { this.followPath = followPath; }

    @Nonnull
    public String getPathName() { return pathName; }
    public void setPathName(@Nonnull String pathName) { this.pathName = pathName; }

    public boolean isPatrol() { return patrol; }
    public void setPatrol(boolean patrol) { this.patrol = patrol; }

    public float getPatrolWanderDistance() { return patrolWanderDistance; }
    public void setPatrolWanderDistance(float patrolWanderDistance) { this.patrolWanderDistance = patrolWanderDistance; }

    @Nonnull
    public String getLoopMode() { return loopMode; }
    public void setLoopMode(@Nonnull String loopMode) { this.loopMode = loopMode; }

    @Nonnull
    public String getPluginPatrolPath() { return pluginPatrolPath; }
    public void setPluginPatrolPath(@Nonnull String pluginPatrolPath) { this.pluginPatrolPath = pluginPatrolPath; }

    public float getPluginPatrolSpeed() { return pluginPatrolSpeed; }
    public void setPluginPatrolSpeed(float pluginPatrolSpeed) { this.pluginPatrolSpeed = Math.max(0.1f, pluginPatrolSpeed); }
}
