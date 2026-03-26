package com.electro.hycitizens.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ScheduleConfig {
    private boolean enabled = false;
    private ScheduleFallbackMode fallbackMode = ScheduleFallbackMode.USE_BASE_BEHAVIOR;
    private String defaultLocationId = "";
    private List<ScheduleLocation> locations = new ArrayList<>();
    private List<ScheduleEntry> entries = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nonnull
    public ScheduleFallbackMode getFallbackMode() {
        return fallbackMode;
    }

    public void setFallbackMode(@Nonnull ScheduleFallbackMode fallbackMode) {
        this.fallbackMode = fallbackMode;
    }

    @Nonnull
    public String getDefaultLocationId() {
        return defaultLocationId;
    }

    public void setDefaultLocationId(@Nonnull String defaultLocationId) {
        this.defaultLocationId = defaultLocationId;
    }

    @Nonnull
    public List<ScheduleLocation> getLocations() {
        return locations;
    }

    public void setLocations(@Nonnull List<ScheduleLocation> locations) {
        this.locations = new ArrayList<>(locations);
    }

    @Nonnull
    public List<ScheduleEntry> getEntries() {
        return entries;
    }

    public void setEntries(@Nonnull List<ScheduleEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    @Nonnull
    public Optional<ScheduleLocation> findLocation(@Nonnull String locationId) {
        return locations.stream()
                .filter(location -> locationId.equals(location.getId()))
                .findFirst();
    }

    @Nonnull
    public Optional<ScheduleEntry> findEntry(@Nonnull String entryId) {
        return entries.stream()
                .filter(entry -> entryId.equals(entry.getId()))
                .findFirst();
    }
}
