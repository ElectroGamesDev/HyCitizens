package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class DetectionConfig {
    private float viewRange;
    private float viewSector;
    private float hearingRange;
    private float absoluteDetectionRange;
    private float alertedRange;

    // Extended detection parameters from Template_Citizen
    private float alertedTimeMin;
    private float alertedTimeMax;
    private int chanceToBeAlertedWhenReceivingCallForHelp;
    private float confusedTimeMin;
    private float confusedTimeMax;
    private float searchTimeMin;
    private float searchTimeMax;
    private float investigateRange;

    public DetectionConfig() {
        this.viewRange = 15;
        this.viewSector = 180;
        this.hearingRange = 8;
        this.absoluteDetectionRange = 2;
        this.alertedRange = 45;
        this.alertedTimeMin = 1.0f;
        this.alertedTimeMax = 2.0f;
        this.chanceToBeAlertedWhenReceivingCallForHelp = 70;
        this.confusedTimeMin = 1.0f;
        this.confusedTimeMax = 2.0f;
        this.searchTimeMin = 10.0f;
        this.searchTimeMax = 14.0f;
        this.investigateRange = 40.0f;
    }

    public DetectionConfig(boolean hostile) {
        if (hostile) {
            this.viewRange = 15;
            this.viewSector = 180;
            this.hearingRange = 8;
            this.absoluteDetectionRange = 2;
            this.alertedRange = 45;
        } else {
            this.viewRange = 0;
            this.viewSector = 180;
            this.hearingRange = 0;
            this.absoluteDetectionRange = 0;
            this.alertedRange = 0;
        }
        this.alertedTimeMin = 1.0f;
        this.alertedTimeMax = 2.0f;
        this.chanceToBeAlertedWhenReceivingCallForHelp = 70;
        this.confusedTimeMin = 1.0f;
        this.confusedTimeMax = 2.0f;
        this.searchTimeMin = 10.0f;
        this.searchTimeMax = 14.0f;
        this.investigateRange = 40.0f;
    }

    public void copyFrom(@Nonnull DetectionConfig other) {
        this.viewRange = other.viewRange;
        this.viewSector = other.viewSector;
        this.hearingRange = other.hearingRange;
        this.absoluteDetectionRange = other.absoluteDetectionRange;
        this.alertedRange = other.alertedRange;
        this.alertedTimeMin = other.alertedTimeMin;
        this.alertedTimeMax = other.alertedTimeMax;
        this.chanceToBeAlertedWhenReceivingCallForHelp = other.chanceToBeAlertedWhenReceivingCallForHelp;
        this.confusedTimeMin = other.confusedTimeMin;
        this.confusedTimeMax = other.confusedTimeMax;
        this.searchTimeMin = other.searchTimeMin;
        this.searchTimeMax = other.searchTimeMax;
        this.investigateRange = other.investigateRange;
    }

    // Getters and Setters

    public float getViewRange() { return viewRange; }
    public void setViewRange(float viewRange) { this.viewRange = viewRange; }

    public float getViewSector() { return viewSector; }
    public void setViewSector(float viewSector) { this.viewSector = viewSector; }

    public float getHearingRange() { return hearingRange; }
    public void setHearingRange(float hearingRange) { this.hearingRange = hearingRange; }

    public float getAbsoluteDetectionRange() { return absoluteDetectionRange; }
    public void setAbsoluteDetectionRange(float absoluteDetectionRange) { this.absoluteDetectionRange = absoluteDetectionRange; }

    public float getAlertedRange() { return alertedRange; }
    public void setAlertedRange(float alertedRange) { this.alertedRange = alertedRange; }

    public float getAlertedTimeMin() { return alertedTimeMin; }
    public void setAlertedTimeMin(float v) { this.alertedTimeMin = v; }

    public float getAlertedTimeMax() { return alertedTimeMax; }
    public void setAlertedTimeMax(float v) { this.alertedTimeMax = v; }

    public int getChanceToBeAlertedWhenReceivingCallForHelp() { return chanceToBeAlertedWhenReceivingCallForHelp; }
    public void setChanceToBeAlertedWhenReceivingCallForHelp(int v) { this.chanceToBeAlertedWhenReceivingCallForHelp = v; }

    public float getConfusedTimeMin() { return confusedTimeMin; }
    public void setConfusedTimeMin(float v) { this.confusedTimeMin = v; }

    public float getConfusedTimeMax() { return confusedTimeMax; }
    public void setConfusedTimeMax(float v) { this.confusedTimeMax = v; }

    public float getSearchTimeMin() { return searchTimeMin; }
    public void setSearchTimeMin(float v) { this.searchTimeMin = v; }

    public float getSearchTimeMax() { return searchTimeMax; }
    public void setSearchTimeMax(float v) { this.searchTimeMax = v; }

    public float getInvestigateRange() { return investigateRange; }
    public void setInvestigateRange(float v) { this.investigateRange = v; }
}
