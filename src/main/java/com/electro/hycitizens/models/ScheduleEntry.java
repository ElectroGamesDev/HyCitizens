package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class ScheduleEntry {
    private String id = "";
    private String name = "";
    private boolean enabled = true;
    private double startTime24 = 8.0;
    private double endTime24 = 17.0;
    private String locationId = "";
    private ScheduleActivityType activityType = ScheduleActivityType.IDLE;
    private float arrivalRadius = 1.5f;
    private float travelSpeed = 10.0f;
    private float wanderRadius = 5.0f;
    private String patrolPathName = "";
    private String followCitizenId = "";
    private float followDistance = 2.0f;
    private String arrivalAnimationName = "";
    private int arrivalAnimationSlot = 0;
    private int priority = 0;

    public ScheduleEntry() {
    }

    @Nonnull
    public String getId() {
        return id;
    }

    public void setId(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getStartTime24() {
        return startTime24;
    }

    public void setStartTime24(double startTime24) {
        this.startTime24 = clampTime24(startTime24);
    }

    public double getEndTime24() {
        return endTime24;
    }

    public void setEndTime24(double endTime24) {
        this.endTime24 = clampTime24(endTime24);
    }

    @Nonnull
    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(@Nonnull String locationId) {
        this.locationId = locationId;
    }

    @Nonnull
    public ScheduleActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(@Nonnull ScheduleActivityType activityType) {
        this.activityType = activityType;
    }

    public float getArrivalRadius() {
        return arrivalRadius;
    }

    public void setArrivalRadius(float arrivalRadius) {
        this.arrivalRadius = Math.max(0.25f, arrivalRadius);
    }

    public float getTravelSpeed() {
        return travelSpeed;
    }

    public void setTravelSpeed(float travelSpeed) {
        this.travelSpeed = Math.max(1.0f, travelSpeed);
    }

    public float getWanderRadius() {
        return wanderRadius;
    }

    public void setWanderRadius(float wanderRadius) {
        this.wanderRadius = Math.max(1.0f, wanderRadius);
    }

    @Nonnull
    public String getPatrolPathName() {
        return patrolPathName;
    }

    public void setPatrolPathName(@Nonnull String patrolPathName) {
        this.patrolPathName = patrolPathName;
    }

    @Nonnull
    public String getFollowCitizenId() {
        return followCitizenId;
    }

    public void setFollowCitizenId(@Nonnull String followCitizenId) {
        this.followCitizenId = followCitizenId;
    }

    public float getFollowDistance() {
        return followDistance;
    }

    public void setFollowDistance(float followDistance) {
        this.followDistance = Math.max(0.1f, followDistance);
    }

    @Nonnull
    public String getArrivalAnimationName() {
        return arrivalAnimationName;
    }

    public void setArrivalAnimationName(@Nonnull String arrivalAnimationName) {
        this.arrivalAnimationName = arrivalAnimationName;
    }

    public int getArrivalAnimationSlot() {
        return arrivalAnimationSlot;
    }

    public void setArrivalAnimationSlot(int arrivalAnimationSlot) {
        this.arrivalAnimationSlot = Math.max(0, arrivalAnimationSlot);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isActiveAt(double time24) {
        double normalized = clampTime24(time24);
        if (startTime24 == endTime24) {
            return true;
        }
        if (startTime24 < endTime24) {
            return normalized >= startTime24 && normalized < endTime24;
        }
        return normalized >= startTime24 || normalized < endTime24;
    }

    private double clampTime24(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 24.0) {
            return 24.0;
        }
        return value;
    }
}
