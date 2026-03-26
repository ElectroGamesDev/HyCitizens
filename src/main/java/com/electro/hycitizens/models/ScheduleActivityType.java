package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public enum ScheduleActivityType {
    IDLE,
    WANDER,
    PATROL,
    FOLLOW_CITIZEN;

    @Nonnull
    public static ScheduleActivityType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return IDLE;
        }
        try {
            return ScheduleActivityType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return IDLE;
        }
    }
}
