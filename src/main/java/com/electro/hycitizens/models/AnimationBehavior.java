package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class AnimationBehavior {
    private String type;
    private String animationName;
    private int animationSlot;
    private float intervalSeconds;
    private float proximityRange;
    private boolean stopAfterTime;
    private String stopAnimationName;
    private float stopTimeSeconds;

    public AnimationBehavior(@Nonnull String type, @Nonnull String animationName, int animationSlot, float intervalSeconds, float proximityRange) {
        this.type = type;
        this.animationName = animationName;
        this.animationSlot = animationSlot;
        this.intervalSeconds = intervalSeconds;
        this.proximityRange = proximityRange;
        this.stopAfterTime = false;
        this.stopAnimationName = "";
        this.stopTimeSeconds = 3.0f;
    }

    public AnimationBehavior(@Nonnull String type, @Nonnull String animationName, int animationSlot, float intervalSeconds,
                             float proximityRange, boolean stopAfterTime, @Nonnull String stopAnimationName, float stopTimeSeconds) {
        this.type = type;
        this.animationName = animationName;
        this.animationSlot = animationSlot;
        this.intervalSeconds = intervalSeconds;
        this.proximityRange = proximityRange;
        this.stopAfterTime = stopAfterTime;
        this.stopAnimationName = stopAnimationName;
        this.stopTimeSeconds = stopTimeSeconds;
    }

    public AnimationBehavior() {
        this.type = "DEFAULT";
        this.animationName = "";
        this.animationSlot = 0;
        this.intervalSeconds = 5.0f;
        this.proximityRange = 8.0f;
        this.stopAfterTime = false;
        this.stopAnimationName = "";
        this.stopTimeSeconds = 3.0f;
    }

    @Nonnull
    public String getType() {
        return type;
    }

    public void setType(@Nonnull String type) {
        this.type = type;
    }

    @Nonnull
    public String getAnimationName() {
        return animationName;
    }

    public void setAnimationName(@Nonnull String animationName) {
        this.animationName = animationName;
    }

    public int getAnimationSlot() {
        return animationSlot;
    }

    public void setAnimationSlot(int animationSlot) {
        this.animationSlot = animationSlot;
    }

    public float getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(float intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public float getProximityRange() {
        return proximityRange;
    }

    public void setProximityRange(float proximityRange) {
        this.proximityRange = proximityRange;
    }

    public boolean isStopAfterTime() {
        return stopAfterTime;
    }

    public void setStopAfterTime(boolean stopAfterTime) {
        this.stopAfterTime = stopAfterTime;
    }

    @Nonnull
    public String getStopAnimationName() {
        return stopAnimationName;
    }

    public void setStopAnimationName(@Nonnull String stopAnimationName) {
        this.stopAnimationName = stopAnimationName;
    }

    public float getStopTimeSeconds() {
        return stopTimeSeconds;
    }

    public void setStopTimeSeconds(float stopTimeSeconds) {
        this.stopTimeSeconds = stopTimeSeconds;
    }
}
