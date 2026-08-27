package com.electro.hycitizens.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FactionConfig {
    private String factionId = "";
    private List<String> hostileGroups = new ArrayList<>();
    private List<String> neutralGroups = new ArrayList<>();
    private List<String> passiveGroups = new ArrayList<>();

    @Nonnull
    public String getFactionId() {
        return factionId;
    }

    public void setFactionId(@Nullable String factionId) {
        this.factionId = sanitizeFactionId(factionId);
    }

    @Nonnull
    public String getGeneratedAttitudeGroupId() {
        if (factionId.isEmpty()) {
            return "";
        }
        return "HyCitizens_Faction_" + factionId;
    }

    @Nonnull
    public List<String> getHostileGroups() {
        return new ArrayList<>(hostileGroups);
    }

    public void setHostileGroups(@Nonnull List<String> hostileGroups) {
        this.hostileGroups = sanitizeGroupList(hostileGroups);
    }

    @Nonnull
    public List<String> getNeutralGroups() {
        return new ArrayList<>(neutralGroups);
    }

    public void setNeutralGroups(@Nonnull List<String> neutralGroups) {
        this.neutralGroups = sanitizeGroupList(neutralGroups);
    }

    @Nonnull
    public List<String> getPassiveGroups() {
        return new ArrayList<>(passiveGroups);
    }

    public void setPassiveGroups(@Nonnull List<String> passiveGroups) {
        this.passiveGroups = sanitizeGroupList(passiveGroups);
    }

    @Nonnull
    public List<String> getIgnoreGroups() {
        return getPassiveGroups();
    }

    public void setIgnoreGroups(@Nonnull List<String> ignoreGroups) {
        setPassiveGroups(ignoreGroups);
    }

    public boolean shouldGenerateAttitudeGroup() {
        return !factionId.isEmpty();
    }

    public boolean hasRelationships() {
        return !hostileGroups.isEmpty() || !neutralGroups.isEmpty() || !passiveGroups.isEmpty();
    }

    public void copyFrom(@Nonnull FactionConfig other) {
        this.factionId = other.factionId;
        this.hostileGroups = other.getHostileGroups();
        this.neutralGroups = other.getNeutralGroups();
        this.passiveGroups = other.getPassiveGroups();
    }

    @Nonnull
    private static List<String> sanitizeGroupList(@Nonnull List<String> source) {
        List<String> sanitized = new ArrayList<>();
        for (String value : source) {
            String normalized = sanitizeGroupName(value);
            if (!normalized.isEmpty() && !sanitized.contains(normalized)) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

    @Nonnull
    public static String sanitizeGroupName(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[\\r\\n]+", "");
    }

    @Nonnull
    public static String sanitizeFactionId(@Nullable String value) {
        String normalized = sanitizeGroupName(value);
        if (normalized.isEmpty()) {
            return "";
        }
        normalized = normalized.replace(' ', '_').replace('-', '_');
        normalized = normalized.replaceAll("[^A-Za-z0-9_]", "");
        if (normalized.isEmpty()) {
            return "";
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "Faction_" + normalized;
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }
}
