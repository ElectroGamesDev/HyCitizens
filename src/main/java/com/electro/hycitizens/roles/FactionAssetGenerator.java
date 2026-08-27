package com.electro.hycitizens.roles;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.FactionConfig;
import com.electro.hycitizens.util.DataAssetPackManager;
import com.electro.hycitizens.util.GeneratedAssetReloader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeMap;
import com.hypixel.hytale.server.npc.config.AttitudeGroup;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class FactionAssetGenerator {
    private static final String DATA_PACK_KEY = "electro:HyCitizensData";

    private final File generatedAttitudeDir;
    private final File generatedNpcGroupDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, String> lastGeneratedContent = new ConcurrentHashMap<>();
    private final Map<String, String> lastGeneratedNpcGroupContent = new ConcurrentHashMap<>();

    public FactionAssetGenerator() {
        this.generatedAttitudeDir = DataAssetPackManager.GENERATED_ATTITUDE_ROLES_PATH.toFile();
        if (!generatedAttitudeDir.exists()) {
            generatedAttitudeDir.mkdirs();
        }
        this.generatedNpcGroupDir = DataAssetPackManager.GENERATED_NPC_GROUPS_PATH.toFile();
        if (!generatedNpcGroupDir.exists()) {
            generatedNpcGroupDir.mkdirs();
        }
    }

    @Nonnull
    public String ensureFactionAssets(@Nonnull CitizenData citizen) {
        String assignedFactionId = citizen.getFactionConfig().getFactionId();
        if (assignedFactionId.isEmpty()) {
            return citizen.getAttitudeGroup();
        }

        FactionConfig factionConfig = HyCitizensPlugin.get().getCitizensManager().getFactionConfig(assignedFactionId);
        if (!factionConfig.shouldGenerateAttitudeGroup()) {
            return citizen.getAttitudeGroup();
        }
        registerGeneratedNpcGroups();
        String groupId = factionConfig.getGeneratedAttitudeGroupId();
        JsonObject json = buildAttitudeGroupJson(factionConfig);
        String content = gson.toJson(json);

        if (!content.equals(lastGeneratedContent.get(groupId))) {
            if (GeneratedAssetReloader.registerAttitudeFromJson(groupId, content)) {
                lastGeneratedContent.put(groupId, content);
            } else {
                return groupId;
            }
        }

        registerAttitudeGroup(groupId, DataAssetPackManager.GENERATED_ATTITUDE_ROLES_PATH.resolve(groupId + ".json"), factionConfig);
        return groupId;
    }

    private void registerGeneratedNpcGroups() {
        for (FactionConfig factionConfig : HyCitizensPlugin.get().getCitizensManager().getAllFactionConfigs()) {
            if (!factionConfig.shouldGenerateAttitudeGroup()) {
                continue;
            }
            registerGeneratedNpcGroup(factionConfig);
        }
    }

    private void registerGeneratedNpcGroup(@Nonnull FactionConfig factionConfig) {
        String groupId = factionConfig.getGeneratedAttitudeGroupId();
        List<String> includedRoles = getFactionRoleNames(factionConfig.getFactionId());
        JsonObject json = buildNpcGroupJson(includedRoles);
        String content = gson.toJson(json);

        if (!content.equals(lastGeneratedNpcGroupContent.get(groupId))) {
            if (GeneratedAssetReloader.registerNpcGroupFromJson(groupId, content)) {
                lastGeneratedNpcGroupContent.put(groupId, content);
            } else {
                return;
            }
        }

        try {
            NPCGroup group = new NPCGroup(groupId);
            setField(group, "includedRoles", includedRoles.toArray(new String[0]));
            setField(group, "excludedRoles", new String[0]);
            setField(group, "includedGroupTags", new String[0]);
            setField(group, "excludedGroupTags", new String[0]);

            Map<String, Object> assets = new LinkedHashMap<>();
            assets.put(groupId, group);
            Map<String, Path> paths = new LinkedHashMap<>();
            paths.put(groupId, DataAssetPackManager.GENERATED_NPC_GROUPS_PATH.resolve(groupId + ".json"));

            GeneratedAssetReloader.reloadAssetMapEntry(
                    NPCGroup.getAssetMap(),
                    NPCGroup.CODEC,
                    DATA_PACK_KEY,
                    assets,
                    paths
            );
        } catch (Throwable throwable) {
            getLogger().atWarning().log("[HyCitizens] Could not register faction NPC group '" + groupId + "' at runtime: " + throwable.getMessage());
        }
    }

    @Nonnull
    private JsonObject buildNpcGroupJson(@Nonnull List<String> includedRoles) {
        JsonObject root = new JsonObject();
        addJsonStringArray(root, "IncludeRoles", includedRoles);
        addJsonStringArray(root, "ExcludeRoles", List.of());
        addJsonStringArray(root, "IncludeGroups", List.of());
        addJsonStringArray(root, "ExcludeGroups", List.of());
        return root;
    }

    private void addJsonStringArray(@Nonnull JsonObject root, @Nonnull String key, @Nonnull List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        root.add(key, array);
    }

    @Nonnull
    private List<String> getFactionRoleNames(@Nonnull String factionId) {
        List<String> roleNames = new ArrayList<>();
        for (CitizenData citizen : HyCitizensPlugin.get().getCitizensManager().getAllCitizens()) {
            if (!factionId.equalsIgnoreCase(citizen.getFactionConfig().getFactionId())) {
                continue;
            }
            for (String roleName : HyCitizensPlugin.get().getCitizensManager().getRoleGenerator().getGeneratedRoleNames(citizen)) {
                if (!containsIgnoreCase(roleNames, roleName)) {
                    roleNames.add(roleName);
                }
            }
        }
        return roleNames;
    }

    @Nonnull
    private JsonObject buildAttitudeGroupJson(@Nonnull FactionConfig factionConfig) {
        JsonObject root = new JsonObject();
        JsonObject groups = new JsonObject();
        addGroupArray(groups, "Hostile", getValidNpcGroups(factionConfig.getHostileGroups(), factionConfig.getFactionId(), "Hostile"));
        addGroupArray(groups, "Neutral", getValidNpcGroups(factionConfig.getNeutralGroups(), factionConfig.getFactionId(), "Neutral"));
        addGroupArray(groups, "Ignore", getValidNpcGroups(factionConfig.getPassiveGroups(), factionConfig.getFactionId(), "Passive"));
        root.add("Groups", groups);
        return root;
    }

    private void addGroupArray(@Nonnull JsonObject groups, @Nonnull String key, @Nonnull List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        groups.add(key, array);
    }

    private void registerAttitudeGroup(@Nonnull String groupId,
                                       @Nonnull Path path,
                                       @Nonnull FactionConfig factionConfig) {
        try {
            AttitudeGroup group = new AttitudeGroup(groupId);
            setField(group, "attitudeGroups", buildRuntimeAttitudes(factionConfig));

            Map<String, Object> assets = new LinkedHashMap<>();
            assets.put(groupId, group);
            Map<String, Path> paths = new LinkedHashMap<>();
            paths.put(groupId, path);

            GeneratedAssetReloader.reloadAssetMapEntry(
                    AttitudeGroup.getAssetMap(),
                    AttitudeGroup.CODEC,
                    DATA_PACK_KEY,
                    assets,
                    paths
            );
            updateRuntimeAttitudeMap(groupId, group, factionConfig);
        } catch (Throwable throwable) {
            getLogger().atWarning().log("[HyCitizens] Could not register faction attitude group '" + groupId + "' at runtime: " + throwable.getMessage());
        }
    }

    @Nonnull
    private Map<Attitude, String[]> buildRuntimeAttitudes(@Nonnull FactionConfig factionConfig) {
        Map<Attitude, String[]> groups = new LinkedHashMap<>();
        putRuntimeGroup(groups, Attitude.HOSTILE, getValidNpcGroups(factionConfig.getHostileGroups(), factionConfig.getFactionId(), "Hostile"));
        putRuntimeGroup(groups, Attitude.NEUTRAL, getValidNpcGroups(factionConfig.getNeutralGroups(), factionConfig.getFactionId(), "Neutral"));
        putRuntimeGroup(groups, Attitude.IGNORE, getValidNpcGroups(factionConfig.getPassiveGroups(), factionConfig.getFactionId(), "Passive"));
        return groups;
    }

    private void putRuntimeGroup(@Nonnull Map<Attitude, String[]> groups,
                                 @Nonnull Attitude attitude,
                                 @Nonnull List<String> values) {
        if (!values.isEmpty()) {
            groups.put(attitude, values.toArray(new String[0]));
        }
    }

    private void setField(@Nonnull Object target, @Nonnull String fieldName, @Nonnull Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nonnull
    private List<String> getValidNpcGroups(@Nonnull List<String> values,
                                           @Nonnull String factionId,
                                           @Nonnull String relationshipName) {
        List<String> validGroups = new ArrayList<>();
        for (String value : values) {
            String canonical = getCanonicalNpcGroupId(value);
            if (canonical == null) {
                getLogger().atWarning().log("[HyCitizens] Skipping unknown NPC group '" + value
                        + "' in " + relationshipName + " relationships for faction '" + factionId + "'.");
                continue;
            }
            if (!containsIgnoreCase(validGroups, canonical)) {
                validGroups.add(canonical);
            }
        }
        return validGroups;
    }

    @Nullable
    private String getCanonicalNpcGroupId(@Nullable String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            return null;
        }
        String trimmed = groupId.trim();
        try {
            for (String knownGroupId : NPCGroup.getAssetMap().getAssetMap().keySet()) {
                if (knownGroupId.equalsIgnoreCase(trimmed)) {
                    return knownGroupId;
                }
            }
        } catch (Exception ignored) {
        }
        String generatedFactionGroupId = getGeneratedFactionGroupIdAlias(trimmed);
        if (generatedFactionGroupId != null) {
            return generatedFactionGroupId;
        }
        return null;
    }

    @Nullable
    private String getGeneratedFactionGroupIdAlias(@Nonnull String value) {
        String prefix = "HyCitizens_Faction_";
        String candidateFactionId = value.regionMatches(true, 0, prefix, 0, prefix.length())
                ? value.substring(prefix.length())
                : value;
        String sanitizedFactionId = FactionConfig.sanitizeFactionId(candidateFactionId);
        if (sanitizedFactionId.isEmpty()) {
            return null;
        }

        for (FactionConfig factionConfig : HyCitizensPlugin.get().getCitizensManager().getAllFactionConfigs()) {
            if (factionConfig.getFactionId().equalsIgnoreCase(sanitizedFactionId)) {
                return factionConfig.getGeneratedAttitudeGroupId();
            }
        }
        return null;
    }

    private boolean containsIgnoreCase(@Nonnull List<String> values, @Nonnull String candidate) {
        for (String value : values) {
            if (value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void updateRuntimeAttitudeMap(@Nonnull String groupId,
                                          @Nonnull AttitudeGroup group,
                                          @Nonnull FactionConfig factionConfig) throws Exception {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null || npcPlugin.getAttitudeMap() == null) {
            return;
        }

        int groupIndex = AttitudeGroup.getAssetMap().getIndex(groupId);
        if (groupIndex == Integer.MIN_VALUE) {
            return;
        }

        AttitudeMap attitudeMap = npcPlugin.getAttitudeMap();
        ensureAttitudeMapCapacity(attitudeMap, groupIndex + 1);
        attitudeMap.updateAttitudeGroup(groupIndex, group);
        addGeneratedFactionTargets(attitudeMap, groupIndex, factionConfig);
    }

    private void addGeneratedFactionTargets(@Nonnull AttitudeMap attitudeMap,
                                            int groupIndex,
                                            @Nonnull FactionConfig factionConfig) throws Exception {
        Int2ObjectMap<Attitude> groupMap = getRuntimeGroupMap(attitudeMap, groupIndex);
        putGeneratedFactionTargets(groupMap, Attitude.HOSTILE, factionConfig.getHostileGroups());
        putGeneratedFactionTargets(groupMap, Attitude.NEUTRAL, factionConfig.getNeutralGroups());
        putGeneratedFactionTargets(groupMap, Attitude.IGNORE, factionConfig.getPassiveGroups());
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private Int2ObjectMap<Attitude> getRuntimeGroupMap(@Nonnull AttitudeMap attitudeMap, int groupIndex) throws Exception {
        Field mapField = AttitudeMap.class.getDeclaredField("map");
        mapField.setAccessible(true);
        Object[] currentMap = ensureAttitudeMapCapacity(mapField, attitudeMap, groupIndex + 1);
        Int2ObjectMap<Attitude> groupMap = (Int2ObjectMap<Attitude>) currentMap[groupIndex];
        if (groupMap == null) {
            groupMap = new Int2ObjectOpenHashMap<>();
            currentMap[groupIndex] = groupMap;
        }
        return groupMap;
    }

    private Object[] ensureAttitudeMapCapacity(@Nonnull AttitudeMap attitudeMap, int requiredLength) throws Exception {
        Field mapField = AttitudeMap.class.getDeclaredField("map");
        mapField.setAccessible(true);
        return ensureAttitudeMapCapacity(mapField, attitudeMap, requiredLength);
    }

    private Object[] ensureAttitudeMapCapacity(@Nonnull Field mapField,
                                               @Nonnull AttitudeMap attitudeMap,
                                               int requiredLength) throws Exception {
        Object[] currentMap = (Object[]) mapField.get(attitudeMap);
        if (currentMap == null) {
            Object[] createdMap = new Int2ObjectMap[requiredLength];
            mapField.set(attitudeMap, createdMap);
            return createdMap;
        }
        if (currentMap.length >= requiredLength) {
            return currentMap;
        }

        Object[] expandedMap = Arrays.copyOf(currentMap, requiredLength);
        mapField.set(attitudeMap, expandedMap);
        return expandedMap;
    }

    private void putGeneratedFactionTargets(@Nonnull Int2ObjectMap<Attitude> groupMap,
                                            @Nonnull Attitude attitude,
                                            @Nonnull List<String> groups) {
        for (String group : groups) {
            String generatedGroupId = getGeneratedFactionGroupIdAlias(group);
            if (generatedGroupId == null) {
                continue;
            }
            String factionId = generatedGroupId.substring("HyCitizens_Faction_".length());
            for (String roleName : getFactionRoleNames(factionId)) {
                int roleIndex = NPCPlugin.get().getIndex(roleName);
                if (roleIndex != Integer.MIN_VALUE) {
                    groupMap.put(roleIndex, attitude);
                }
            }
        }
    }
}
