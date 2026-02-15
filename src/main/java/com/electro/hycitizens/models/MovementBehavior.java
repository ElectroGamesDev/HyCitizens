package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class MovementBehavior {
    private String type;
    private float walkSpeed;
    private float wanderRadius;
    private float wanderWidth;
    private float wanderDepth;

    public MovementBehavior(@Nonnull String type, float walkSpeed, float wanderRadius, float wanderWidth, float wanderDepth) {
        this.type = type;
        this.walkSpeed = walkSpeed;
        this.wanderRadius = wanderRadius;
        this.wanderWidth = wanderWidth;
        this.wanderDepth = wanderDepth;
    }

    public MovementBehavior() {
        this.type = "IDLE";
        this.walkSpeed = 1.0f;
        this.wanderRadius = 10.0f;
        this.wanderWidth = 10.0f;
        this.wanderDepth = 10.0f;
    }

    @Nonnull
    public String getType() {
        return type;
    }

    public void setType(@Nonnull String type) {
        this.type = type;
    }

    public float getWalkSpeed() {
        return walkSpeed;
    }

    public void setWalkSpeed(float walkSpeed) {
        this.walkSpeed = walkSpeed;
    }

    public float getWanderRadius() {
        return wanderRadius;
    }

    public void setWanderRadius(float wanderRadius) {
        this.wanderRadius = wanderRadius;
    }

    public float getWanderWidth() {
        return wanderWidth;
    }

    public void setWanderWidth(float wanderWidth) {
        this.wanderWidth = wanderWidth;
    }

    public float getWanderDepth() {
        return wanderDepth;
    }

    public void setWanderDepth(float wanderDepth) {
        this.wanderDepth = wanderDepth;
    }
}
