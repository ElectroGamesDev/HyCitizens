package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public enum ScheduleFallbackMode {
    USE_BASE_BEHAVIOR,
    GO_TO_DEFAULT_LOCATION_IDLE,
    HOLD_LAST_SCHEDULE_STATE;

    @Nonnull
    public static ScheduleFallbackMode fromString(String value) {
        if (value == null || value.isEmpty()) {
            return USE_BASE_BEHAVIOR;
        }
        try {
            return ScheduleFallbackMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return USE_BASE_BEHAVIOR;
        }
    }
}
