package com.electro.hycitizens.ui;

import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.*;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.common.util.RandomUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CitizensUI {
    private final HyCitizensPlugin plugin;
    private final Map<UUID, String> pendingFollowSelections = new ConcurrentHashMap<>();

    private record NametagSettingsState(
            String name,
            float offset,
            boolean hideNametag,
            boolean modelEnabled,
            String modelId,
            float modelScale,
            boolean rotateModelTowardsPlayer
    ) {}

    private String generateAnimationDropdownOptions(String selectedValue, String modelId) {
        StringBuilder sb = new StringBuilder();

        ModelAsset model = ModelAsset.getAssetMap().getAsset(modelId);
        if (model == null) {
            return sb.toString();
        }

        Map<String, ModelAsset.AnimationSet> animations = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        animations.putAll(model.getAnimationSetMap());

        for (String anim : animations.keySet()) {
            boolean isSelected = anim.equals(selectedValue);
            sb.append("<option value=\"").append(anim).append("\"");
            if (isSelected) {
                sb.append(" selected");
            }
            sb.append(">").append(anim).append("</option>\n");
        }
        return sb.toString();
    }

    private String generateEntityDropdownOptions(String selectedValue) {
        StringBuilder sb = new StringBuilder();
        Set<String> modelIds = ModelAsset.getAssetMap().getAssetMap().keySet();

        List<String> sorted = new ArrayList<>(modelIds);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);

        for (String entity : sorted) {
            boolean isSelected = entity.equalsIgnoreCase(selectedValue);
            sb.append("<option value=\"").append(entity).append("\"");
            if (isSelected) {
                sb.append(" selected");
            }
            sb.append(">").append(entity).append("</option>\n");
        }
        return sb.toString();
    }

    private String generateModelDropdownOptions(String selectedValue, boolean allowEmpty) {
        StringBuilder sb = new StringBuilder();
        if (allowEmpty) {
            boolean noneSelected = selectedValue == null || selectedValue.isBlank();
            sb.append("<option value=\"\"");
            if (noneSelected) {
                sb.append(" selected");
            }
            sb.append(">Select a model</option>\n");
        }

        sb.append(generateEntityDropdownOptions(selectedValue));
        return sb.toString();
    }

    private String buildNametagSummary(String name, float nametagOffset, boolean hideNametag,
                                       boolean modelNametagEnabled, String nametagModelId,
                                       float nametagModelScale, boolean rotateNametagTowardsPlayer) {
        if (hideNametag) {
            return "Nametag hidden";
        }

        if (modelNametagEnabled) {
            String resolvedModelId = nametagModelId == null || nametagModelId.isBlank() ? "unselected" : nametagModelId.trim();
            return "Model nametag: " + resolvedModelId
                    + " | Scale: " + nametagModelScale
                    + " | Offset: " + nametagOffset
                    + " | Rotate to players: " + (rotateNametagTowardsPlayer ? "On" : "Off");
        }

        int lineCount = splitNametagLines(name).size();
        return "Text nametag: " + Math.max(1, lineCount) + " line(s) | Offset: " + nametagOffset;
    }

    private String generateGroupDropdownOptions(String selectedValue, List<String> allGroups) {
        StringBuilder sb = new StringBuilder();
        String normalizedSelected = normalizeGroupPath(selectedValue);

        // Add "None" option for no group
        boolean noneSelected = normalizedSelected.isEmpty();
        sb.append("<option value=\"\"");
        if (noneSelected) {
            sb.append(" selected");
        }
        sb.append(">None</option>\n");

        // Add existing groups
        for (String groupName : allGroups) {
            String normalizedGroup = normalizeGroupPath(groupName);
            boolean isSelected = normalizedGroup.equals(normalizedSelected);
            sb.append("<option value=\"").append(escapeHtml(groupName)).append("\"");
            if (isSelected) {
                sb.append(" selected");
            }
            sb.append(">").append(escapeHtml(groupName)).append("</option>\n");
        }

        return sb.toString();
    }

    private String generateGroupParentOptions(@Nullable String selectedParent, @Nullable String movingGroup) {
        String normalizedSelected = normalizeGroupPath(selectedParent);
        String normalizedMoving = normalizeGroupPath(movingGroup);
        StringBuilder sb = new StringBuilder();

        sb.append("<option value=\"\"");
        if (normalizedSelected.isEmpty()) {
            sb.append(" selected");
        }
        sb.append(">Root</option>\n");

        for (String groupName : plugin.getCitizensManager().getAllGroups()) {
            String normalizedGroup = normalizeGroupPath(groupName);
            if (normalizedGroup.isEmpty()) {
                continue;
            }
            if (!normalizedMoving.isEmpty()
                    && (normalizedGroup.equals(normalizedMoving) || normalizedGroup.startsWith(normalizedMoving + "/"))) {
                continue;
            }

            sb.append("<option value=\"").append(escapeHtml(normalizedGroup)).append("\"");
            if (normalizedGroup.equals(normalizedSelected)) {
                sb.append(" selected");
            }
            sb.append(">").append(escapeHtml(normalizedGroup)).append("</option>\n");
        }

        return sb.toString();
    }

    private String generateMapMarkerTypeOptions(@Nullable String selectedValue) {
        String selected = CitizenData.normalizeMapMarkerType(selectedValue);
        StringBuilder sb = new StringBuilder();
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_PIN, "Pin", selected.equals(CitizenData.MAP_MARKER_TYPE_PIN));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_DOT, "Dot", selected.equals(CitizenData.MAP_MARKER_TYPE_DOT));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_STAR, "Star", selected.equals(CitizenData.MAP_MARKER_TYPE_STAR));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_DIAMOND, "Diamond", selected.equals(CitizenData.MAP_MARKER_TYPE_DIAMOND));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_SQUARE, "Square", selected.equals(CitizenData.MAP_MARKER_TYPE_SQUARE));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_QUESTION, "Question Mark", selected.equals(CitizenData.MAP_MARKER_TYPE_QUESTION));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_EXCLAMATION, "Exclamation Mark", selected.equals(CitizenData.MAP_MARKER_TYPE_EXCLAMATION));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_MONEY_SYMBOL, "Money Symbol", selected.equals(CitizenData.MAP_MARKER_TYPE_MONEY_SYMBOL));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_SHOP, "Shop", selected.equals(CitizenData.MAP_MARKER_TYPE_SHOP));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_TRADER, "Trader", selected.equals(CitizenData.MAP_MARKER_TYPE_TRADER));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_CHEST, "Chest", selected.equals(CitizenData.MAP_MARKER_TYPE_CHEST));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_SWORD, "Sword", selected.equals(CitizenData.MAP_MARKER_TYPE_SWORD));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_SHIELD, "Shield", selected.equals(CitizenData.MAP_MARKER_TYPE_SHIELD));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_HEART, "Heart", selected.equals(CitizenData.MAP_MARKER_TYPE_HEART));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_HOME, "Home", selected.equals(CitizenData.MAP_MARKER_TYPE_HOME));
        appendOption(sb, CitizenData.MAP_MARKER_TYPE_NPC_TYPE, "NPC Type", selected.equals(CitizenData.MAP_MARKER_TYPE_NPC_TYPE));
        return sb.toString();
    }

    private String generatePatrolPathOptions(String selectedPath) {
        List<String> pathNames = plugin.getCitizensManager().getPatrolManager().getAllPathNames();
        StringBuilder sb = new StringBuilder();
        boolean noneSelected = selectedPath == null || selectedPath.isEmpty();
        sb.append("<option value=\"\"");
        if (noneSelected) {
            sb.append(" selected");
        }
        sb.append(">None</option>\n");
        for (String name : pathNames) {
            sb.append("<option value=\"").append(escapeHtml(name)).append("\"");
            if (name.equals(selectedPath)) {
                sb.append(" selected");
            }
            sb.append(">").append(escapeHtml(name)).append("</option>\n");
        }
        return sb.toString();
    }

    private String generateScheduleLocationOptions(@Nonnull ScheduleConfig scheduleConfig, @Nullable String selectedLocationId) {
        StringBuilder sb = new StringBuilder();
        boolean noneSelected = selectedLocationId == null || selectedLocationId.isEmpty();
        sb.append("<option value=\"\"");
        if (noneSelected) {
            sb.append(" selected");
        }
        sb.append(">None</option>\n");

        for (ScheduleLocation location : scheduleConfig.getLocations()) {
            sb.append("<option value=\"").append(escapeHtml(location.getId())).append("\"");
            if (location.getId().equals(selectedLocationId)) {
                sb.append(" selected");
            }
            sb.append(">").append(escapeHtml(location.getName())).append("</option>\n");
        }
        return sb.toString();
    }

    private String generateScheduleActivityOptions(@Nonnull ScheduleActivityType selectedType) {
        StringBuilder sb = new StringBuilder();
        appendOption(sb, ScheduleActivityType.IDLE.name(), "Idle", selectedType == ScheduleActivityType.IDLE);
        appendOption(sb, ScheduleActivityType.WANDER.name(), "Wander", selectedType == ScheduleActivityType.WANDER);
        appendOption(sb, ScheduleActivityType.PATROL.name(), "Patrol", selectedType == ScheduleActivityType.PATROL);
        appendOption(sb, ScheduleActivityType.FOLLOW_CITIZEN.name(), "Follow Citizen", selectedType == ScheduleActivityType.FOLLOW_CITIZEN);
        return sb.toString();
    }

    private String generateScheduleCitizenOptions(@Nonnull CitizenData editingCitizen, @Nullable String selectedCitizenId) {
        StringBuilder sb = new StringBuilder();
        boolean noneSelected = selectedCitizenId == null || selectedCitizenId.isEmpty();
        sb.append("<option value=\"\"");
        if (noneSelected) {
            sb.append(" selected");
        }
        sb.append(">None</option>\n");

        List<CitizenData> citizens = new ArrayList<>(plugin.getCitizensManager().getAllCitizens());
        citizens.sort(Comparator.comparing(CitizenData::getName, String.CASE_INSENSITIVE_ORDER));
        for (CitizenData citizen : citizens) {
            if (citizen.getId().equals(editingCitizen.getId())) {
                continue;
            }
            sb.append("<option value=\"").append(escapeHtml(citizen.getId())).append("\"");
            if (citizen.getId().equals(selectedCitizenId)) {
                sb.append(" selected");
            }
            sb.append(">").append(escapeHtml(citizen.getName())).append("</option>\n");
        }
        return sb.toString();
    }

    private String formatTime24(double time24) {
        double clamped = Math.max(0.0, Math.min(24.0, time24));
        int hour = (int) clamped;
        int minute = (int) Math.round((clamped - hour) * 60.0);
        if (minute == 60) {
            minute = 0;
            hour = Math.min(24, hour + 1);
        }
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private String formatScheduleTimeRange(@Nonnull ScheduleEntry entry) {
        if (Math.abs(entry.getStartTime24() - entry.getEndTime24()) < 0.0001) {
            return "All day";
        }
        return formatTime24(entry.getStartTime24()) + " - " + formatTime24(entry.getEndTime24());
    }

    @Nullable
    private Double getScheduleWorldTime24(@Nonnull CitizenData citizen) {
        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return null;
        }

        WorldTimeResource timeResource = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        if (timeResource == null) {
            return null;
        }

        LocalDateTime gameDateTime = timeResource.getGameDateTime();
        return gameDateTime.getHour() + (gameDateTime.getMinute() / 60.0);
    }

    @Nonnull
    private String formatScheduleWorldTime(@Nullable Double time24) {
        return time24 == null ? "Unknown" : formatTime24(time24);
    }

    @Nonnull
    private String describeScheduleNextEntry(@Nonnull ScheduleConfig scheduleConfig, @Nullable Double time24) {
        if (!scheduleConfig.isEnabled()) {
            return "Enable the schedule to run entries.";
        }
        if (scheduleConfig.getEntries().isEmpty()) {
            return "Add a schedule entry to start using the schedule.";
        }
        if (time24 == null) {
            return "World time is unavailable, so entries cannot be selected yet.";
        }

        Optional<ScheduleEntry> activeEntry = scheduleConfig.getEntries().stream()
                .filter(ScheduleEntry::isEnabled)
                .filter(entry -> entry.isActiveAt(time24))
                .sorted(Comparator.comparingInt(ScheduleEntry::getPriority).reversed())
                .findFirst();
        if (activeEntry.isPresent()) {
            return "Active now: " + activeEntry.get().getName() + " (" + formatScheduleTimeRange(activeEntry.get()) + ")";
        }

        return scheduleConfig.getEntries().stream()
                .filter(ScheduleEntry::isEnabled)
                .min(Comparator.comparingDouble((ScheduleEntry entry) -> hoursUntil(entry.getStartTime24(), time24))
                        .thenComparing(Comparator.comparingInt(ScheduleEntry::getPriority).reversed()))
                .map(entry -> "Next: " + entry.getName() + " at " + formatTime24(entry.getStartTime24()))
                .orElse("All entries are disabled.");
    }

    private double hoursUntil(double startTime24, double currentTime24) {
        double delta = startTime24 - currentTime24;
        if (delta < 0.0) {
            delta += 24.0;
        }
        return delta;
    }

    private String describeScheduleActivity(@Nonnull ScheduleEntry entry) {
        return switch (entry.getActivityType()) {
            case IDLE -> "Idle";
            case WANDER -> "Wander";
            case PATROL -> entry.getPatrolPathName().isEmpty()
                    ? "Patrol"
                    : "Patrol: " + entry.getPatrolPathName();
            case FOLLOW_CITIZEN -> entry.getFollowCitizenId().isEmpty()
                    ? "Follow Citizen"
                    : "Follow Citizen";
        };
    }

    private String describeScheduleFallbackMode(@Nonnull ScheduleFallbackMode fallbackMode) {
        return switch (fallbackMode) {
            case USE_BASE_BEHAVIOR -> "Use Base Behavior";
            case GO_TO_DEFAULT_LOCATION_IDLE -> "Go To Default Location";
            case HOLD_LAST_SCHEDULE_STATE -> "Hold Last Schedule State";
        };
    }

    @Nonnull
    private String generateScheduleStatusText(@Nonnull CitizenData citizen,
                                              @Nonnull ScheduleConfig scheduleConfig,
                                              @Nullable Double time24) {
        if (!scheduleConfig.isEnabled()) {
            return "Schedule is disabled. Enable it to let entries control movement.";
        }
        if (scheduleConfig.getEntries().isEmpty()) {
            return "Schedule is enabled, but there are no entries yet.";
        }
        if (time24 == null) {
            return "Schedule is enabled, but world time is unavailable.";
        }
        boolean hasActiveEntry = scheduleConfig.getEntries().stream()
                .filter(ScheduleEntry::isEnabled)
                .anyMatch(entry -> entry.isActiveAt(time24));
        if (!hasActiveEntry && citizen.getCurrentScheduleRuntimeState() == ScheduleRuntimeState.INACTIVE) {
            return "No entry matches the current Hytale time. The citizen is using fallback/base behavior.";
        }

        String statusText = citizen.getCurrentScheduleStatusText();
        if (statusText == null || statusText.isBlank()) {
            return "Inactive";
        }
        return statusText;
    }

    private String generatePlayerAttitudeOptions(String selectedValue) {
        String normalized = normalizePlayerAttitude(selectedValue);
        StringBuilder sb = new StringBuilder();
        appendOption(sb, "PASSIVE", "Passive", "PASSIVE".equals(normalized));
        appendOption(sb, "NEUTRAL", "Neutral", "NEUTRAL".equals(normalized));
        appendOption(sb, "AGGRESSIVE", "Aggressive", "AGGRESSIVE".equals(normalized));
        return sb.toString();
    }

    private String generateNpcAttitudeOptions(String selectedValue) {
        String normalized = normalizeNpcAttitude(selectedValue);
        StringBuilder sb = new StringBuilder();
        appendOption(sb, "PASSIVE", "PASSIVE", "PASSIVE".equals(normalized));
        appendOption(sb, "NEUTRAL", "NEUTRAL", "NEUTRAL".equals(normalized));
        appendOption(sb, "AGGRESSIVE", "AGGRESSIVE", "AGGRESSIVE".equals(normalized));
        return sb.toString();
    }

    private void appendOption(StringBuilder sb, String value, String label, boolean selected) {
        sb.append("<option value=\"").append(escapeHtml(value)).append("\"");
        if (selected) {
            sb.append(" selected");
        }
        sb.append(">").append(escapeHtml(label)).append("</option>\n");
    }

    private String normalizePlayerAttitude(String value) {
        if (value == null) return "PASSIVE";
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "NEUTRAL" -> "NEUTRAL";
            case "AGGRESSIVE" -> "AGGRESSIVE";
            default -> "PASSIVE";
        };
    }

    private String normalizeNpcAttitude(String value) {
        if (value == null) return "PASSIVE";
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "NEUTRAL" -> "NEUTRAL";
            case "AGGRESSIVE", "HOSTILE" -> "AGGRESSIVE";
            default -> "PASSIVE"; // Also maps legacy IGNORE.
        };
    }

    public enum Tab {
        CREATE, MANAGE
    }

    public CitizensUI(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void armFollowTargetSelection(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen) {
        pendingFollowSelections.put(playerRef.getUuid(), citizen.getId());
        playerRef.sendMessage(Message.raw(
                "Follow target selection armed. Left click another citizen to make '" + citizen.getName() + "' follow it."
        ).color(Color.YELLOW));
    }

    public boolean tryCompleteFollowTargetSelection(@Nonnull PlayerRef playerRef, @Nonnull CitizenData clickedCitizen) {
        String sourceCitizenId = pendingFollowSelections.get(playerRef.getUuid());
        if (sourceCitizenId == null || sourceCitizenId.isEmpty()) {
            return false;
        }

        CitizenData sourceCitizen = plugin.getCitizensManager().getCitizen(sourceCitizenId);
        if (sourceCitizen == null) {
            pendingFollowSelections.remove(playerRef.getUuid());
            playerRef.sendMessage(Message.raw("The citizen you were editing no longer exists.").color(Color.RED));
            return true;
        }

        if (sourceCitizen.getId().equals(clickedCitizen.getId())) {
            playerRef.sendMessage(Message.raw("Choose a different citizen to follow.").color(Color.RED));
            return true;
        }

        if (!sourceCitizen.getWorldUUID().equals(clickedCitizen.getWorldUUID())) {
            playerRef.sendMessage(Message.raw("Both citizens must be in the same world to follow each other.").color(Color.RED));
            return true;
        }

        pendingFollowSelections.remove(playerRef.getUuid());
        sourceCitizen.setFollowCitizenEnabled(true);
        sourceCitizen.getMovementBehavior().setType("FOLLOW_CITIZEN");
        sourceCitizen.setFollowCitizenId(clickedCitizen.getId());
        plugin.getCitizensManager().updateCitizenNPC(sourceCitizen, true);

        playerRef.sendMessage(Message.raw(
                "'" + sourceCitizen.getName() + "' will now follow '" + clickedCitizen.getName() + "'."
        ).color(Color.GREEN));

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef != null && playerEntityRef.isValid()) {
            openBehaviorsGUI(playerRef, playerEntityRef.getStore(), sourceCitizen);
        }

        return true;
    }

    @Nonnull
    private String groupEventId(@Nullable String groupName) {
        String normalized = normalizeGroupPath(groupName);
        UUID stableId = UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8));
        return "g" + stableId.toString().replace("-", "");
    }

    @Nonnull
    private static String normalizeGroupPath(@Nullable String groupName) {
        if (groupName == null) {
            return "";
        }

        String normalized = groupName.trim().replace('\\', '/');
        normalized = normalized.replaceAll("/+", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.trim();
    }

    @Nonnull
    private static String getParentGroup(@Nonnull String groupName) {
        String normalized = normalizeGroupPath(groupName);
        int slash = normalized.lastIndexOf('/');
        if (slash < 0) {
            return "";
        }
        return normalized.substring(0, slash);
    }

    @Nonnull
    private static String getGroupLeafName(@Nonnull String groupName) {
        String normalized = normalizeGroupPath(groupName);
        int slash = normalized.lastIndexOf('/');
        if (slash < 0) {
            return normalized;
        }
        return normalized.substring(slash + 1);
    }

    @Nonnull
    private static Set<String> collectGroupHierarchy(@Nonnull Collection<CitizenData> citizens) {
        Set<String> result = new LinkedHashSet<>();
        for (CitizenData citizen : citizens) {
            String group = normalizeGroupPath(citizen.getGroup());
            if (group.isEmpty()) {
                continue;
            }

            StringBuilder current = new StringBuilder();
            for (String part : group.split("/")) {
                if (part.isBlank()) {
                    continue;
                }
                if (!current.isEmpty()) {
                    current.append('/');
                }
                current.append(part.trim());
                result.add(current.toString());
            }
        }
        return result;
    }

    private static boolean isDirectChildGroup(@Nonnull String groupName, @Nullable String parentGroup) {
        String normalizedGroup = normalizeGroupPath(groupName);
        String normalizedParent = normalizeGroupPath(parentGroup);
        return getParentGroup(normalizedGroup).equals(normalizedParent);
    }

    private static boolean isDirectCitizenInGroup(@Nonnull CitizenData citizen, @Nullable String groupName) {
        return normalizeGroupPath(citizen.getGroup()).equals(normalizeGroupPath(groupName));
    }

    @Nonnull
    private static String getDisplayNameForList(@Nullable String value) {
        String normalized = normalizeGroupPath(value);
        if (normalized.isEmpty()) {
            return "";
        }
        return getGroupLeafName(normalized);
    }

    @Nonnull
    private static String buildChildGroupPath(@Nullable String parentGroup, @Nullable String childName) {
        String parent = normalizeGroupPath(parentGroup);
        String child = normalizeGroupPath(childName);
        if (parent.isEmpty()) {
            return child;
        }
        if (child.isEmpty()) {
            return parent;
        }
        return parent + "/" + child;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                case '\r' -> {
                }
                case '\n' -> escaped.append("&#10;");
                default -> {
                    if (c < 32 || c > 126) {
                        escaped.append("&#").append((int) c).append(';');
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    public static class SafeCitizen {
        private final CitizenData citizen;

        public SafeCitizen(CitizenData citizen) {
            this.citizen = citizen;
        }

        public String getId() { return citizen.getId(); }
        public String getName() { return escapeHtml(citizen.getName()); }
        public String getModelId() { return escapeHtml(citizen.getModelId()); }
        public float getScale() { return citizen.getScale(); }
        public String getGroup() { return escapeHtml(citizen.getGroup()); }
        public float getNametagOffset() { return citizen.getNametagOffset(); }
        public boolean isModelNametagEnabled() { return citizen.isModelNametagEnabled(); }
        public String getNametagModelId() { return escapeHtml(citizen.getNametagModelId()); }
        public float getNametagModelScale() { return citizen.getNametagModelScale(); }
        public boolean isRotateNametagTowardsPlayer() { return citizen.isRotateNametagTowardsPlayer(); }
        public boolean isPlayerModel() { return citizen.isPlayerModel(); }
        public boolean isUseLiveSkin() { return citizen.isUseLiveSkin(); }
        public String getSkinUsername() { return escapeHtml(citizen.getSkinUsername()); }
        public String getRequiredPermission() { return escapeHtml(citizen.getRequiredPermission()); }
        public String getNoPermissionMessage() { return escapeHtml(citizen.getNoPermissionMessage()); }
        public boolean getRotateTowardsPlayer() { return citizen.getRotateTowardsPlayer(); }
        public boolean getFKeyInteractionEnabled() { return citizen.getFKeyInteractionEnabled(); }
        public boolean isHideNametag() { return citizen.isHideNametag(); }
        public boolean isHideNpc() { return citizen.isHideNpc(); }
        public boolean isMapMarkerEnabled() { return citizen.isMapMarkerEnabled(); }
    }

    private String getSharedStyles() {
        return """
                <style>
                    .page-overlay {
                        layout: center;
                        flex-weight: 1;
                        padding: 20;
                    }

                    .main-container {
                    }

                    .container-title {
                        layout: top;
                        flex-weight: 0;
                    }

                    .container-contents {
                        layout: top;
                        flex-weight: 1;
                    }
                
                    .header {
                        layout: top;
                        flex-weight: 0;
                        padding: 18 18 12 18;
                    }
                
                    .header-content {
                        layout: top;
                        flex-weight: 0;
                        anchor-width: 100%;
                    }
                
                    .header-title {
                        color: #f3f7ff;
                        font-size: 26;
                        font-weight: bold;
                        text-align: center;
                    }
                
                    .header-subtitle {
                        color: #91a5bf;
                        font-size: 12;
                        padding-top: 6;
                        text-align: center;
                        anchor-width: 100%;
                    }

                    .page-description {
                        color: #91a5bf;
                        font-size: 12;
                        text-align: center;
                        anchor-width: 100%;
                    }
                
                    .body {
                        layout: top;
                        flex-weight: 1;
                        padding: 18 20 18 20;
                    }
                
                    .footer {
                        layout: center;
                        flex-weight: 0;
                        padding: 16;
                    }
                
                    .card {
                        layout: top;
                        flex-weight: 0;
                        background-color: #182231;
                        padding: 16 16 14 16;
                        border-radius: 10;
                    }
                
                    .card-header {
                        layout: center;
                        flex-weight: 0;
                        padding-bottom: 12;
                    }
                
                    .card-title {
                        color: #eaf1ff;
                        font-size: 14;
                        font-weight: bold;
                        flex-weight: 1;
                    }
                
                    .card-body {
                        layout: top;
                        flex-weight: 0;
                    }
                
                    .section {
                        layout: top;
                        flex-weight: 0;
                        background-color: #1b2737(0.88);
                        padding: 16;
                        border-radius: 10;
                    }
                
                    .section-header {
                        layout: top;
                        flex-weight: 0;
                        horizontal-align: center;
                    }
                
                    .section-title,
                    .section-description {
                        anchor-width: 100%;
                        text-align: center;
                    }
                
                    .section-title {
                        color: #f2f6ff;
                        font-size: 14;
                        font-weight: bold;
                    }
                
                    .section-description {
                        color: #92a6c0;
                        font-size: 12;
                        padding-top: 6;
                        padding-bottom: 12;
                    }
                
                    .form-group {
                        layout: top;
                        flex-weight: 0;
                        padding-bottom: 8;
                    }
                
                    .form-row {
                        layout: center;
                        flex-weight: 0;
                    }
                
                    .form-col {
                        layout: top;
                        flex-weight: 1;
                    }
                
                    .form-col-fixed {
                        layout: top;
                        flex-weight: 0;
                    }
                
                    .form-label {
                        color: #dde8f7;
                        font-size: 12;
                        font-weight: bold;
                        padding-bottom: 6;
                    }
                
                    .form-label-optional {
                        color: #758aa6;
                        font-size: 12;
                        padding-left: 6;
                    }
                
                    .form-input {
                        flex-weight: 0;
                        anchor-height: 38;
                        background-color: #111926;
                        border-radius: 8;
                        color: #ecf4ff;
                    }
                
                    .form-input-small {
                        flex-weight: 0;
                        anchor-height: 38;
                        anchor-width: 120;
                        background-color: #111926;
                        border-radius: 8;
                        color: #ecf4ff;
                    }
                
                    .form-hint {
                        color: #7f93ac;
                        font-size: 12;
                        padding-top: 4;
                    }
                
                    .form-hint-highlight {
                        color: #7ec0ff;
                        font-size: 12;
                        padding-top: 4;
                    }
                
                    .checkbox-row {
                        layout: center;
                        flex-weight: 0;
                        padding: 10 12 8 12;
                        border-radius: 8;
                        background-color: #111926(0.9);
                    }
                
                    .checkbox-label {
                        color: #eaf2ff;
                        font-size: 12;
                        padding-left: -24;
                    }
                
                    .checkbox-description {
                        color: #91a7c3;
                        font-size: 12;
                        padding-left: -24;
                    }
                
                    .btn-row {
                        layout: center;
                        flex-weight: 0;
                    }
                
                    .btn-row-left {
                        layout: left;
                        flex-weight: 0;
                    }
                
                    .btn-row-right {
                        layout: right;
                        flex-weight: 0;
                    }

                    .button-selected {
                        font-weight: bold;
                    }

                    .secondary-button {
                        flex-weight: 0;
                        anchor-height: 40;
                        anchor-width: 140;
                        border-radius: 8;
                    }

                    .small-secondary-button {
                        flex-weight: 0;
                        anchor-height: 33;
                        anchor-width: 100;
                        border-radius: 8;
                    }
                
                    .btn-wide {
                        anchor-width: 200;
                    }
                
                    .btn-full {
                        flex-weight: 1;
                        anchor-width: 0;
                    }
                
                    .tab-container {
                        layout: center;
                        flex-weight: 0;
                        padding: 5;
                        border-radius: 10;
                    }
                
                    .list-container {
                        layout-mode: TopScrolling;
                        flex-weight: 1;
                        padding: 4 16 4 16;
                        background-color: #0f1722(0.6);
                        border-radius: 10;
                    }
                
                    .list-item {
                        layout: left;
                        flex-weight: 0;
                        background-color: #162232;
                        padding: 14;
                        border-radius: 10;
                    }
                
                    .list-item-hover {
                        background-color: #21324a;
                    }
                
                    .list-item-content {
                        layout: top;
                        flex-weight: 1;
                        padding-left: 12;
                        padding-right: 12;
                    }
                
                    .list-item-title {
                        color: #edf4ff;
                        font-size: 14;
                        font-weight: bold;
                    }
                
                    .list-item-subtitle {
                        color: #8ea4c0;
                        font-size: 12;
                        padding-top: 4;
                    }
                
                    .list-item-meta {
                        color: #7088a6;
                        font-size: 12;
                        padding-top: 4;
                    }
                
                    .list-item-actions {
                        layout: left;
                        flex-weight: 0;
                    }
                
                    .stats-row {
                        layout: left;
                        flex-weight: 0;
                    }
                
                    .stat-card {
                        layout: top;
                        flex-weight: 1;
                        background-color: #142131;
                        padding: 14;
                        border-radius: 10;
                    }
                
                    .stat-value {
                        color: #eff6ff;
                        font-size: 24;
                        font-weight: bold;
                    }
                
                    .stat-label {
                        color: #9cb2cc;
                        font-size: 12;
                        padding-top: 2;
                    }
                
                    .stat-change-positive {
                        color: #4fc86e;
                        font-size: 12;
                    }
                
                    .stat-change-negative {
                        color: #ff6b75;
                        font-size: 12;
                    }
                
                    .empty-state {
                        layout: center;
                        flex-weight: 1;
                        padding: 40;
                    }
                
                    .empty-state-content {
                        layout: top;
                        flex-weight: 0;
                    }
                
                    .empty-state-title {
                        color: #a7bdd7;
                        font-size: 16;
                        text-align: center;
                        padding-top: 16;
                    }
                
                    .empty-state-description {
                        color: #8197b2;
                        font-size: 12;
                        text-align: center;
                        padding-top: 6;
                    }
                
                    .info-box {
                        layout: left;
                        flex-weight: 0;
                        background-color: #1e4871(0.36);
                        padding: 12;
                        border-radius: 8;
                    }
                
                    .info-box-text {
                        color: #c5daf6;
                        font-size: 12;
                        flex-weight: 1;
                        text-align: center;
                    }
                
                    .divider {
                        flex-weight: 0;
                        anchor-height: 1;
                        background-color: #2a3a4e;
                    }
                
                    .divider-vertical {
                        flex-weight: 0;
                        anchor-width: 1;
                        background-color: #2a3a4e;
                    }
                
                    .spacer-xs {
                        flex-weight: 0;
                        anchor-height: 4;
                    }
                
                    .spacer-sm {
                        flex-weight: 0;
                        anchor-height: 8;
                    }
                
                    .spacer-md {
                        flex-weight: 0;
                        anchor-height: 16;
                    }
                
                    .spacer-lg {
                        flex-weight: 0;
                        anchor-height: 24;
                    }
                
                    .spacer-xl {
                        flex-weight: 0;
                        anchor-height: 32;
                    }
                
                    .spacer-h-xs {
                        flex-weight: 0;
                        anchor-width: 4;
                    }
                
                    .spacer-h-sm {
                        flex-weight: 0;
                        anchor-width: 8;
                    }
                
                    .spacer-h-md {
                        flex-weight: 0;
                        anchor-width: 16;
                    }
                
                    .toggle-group {
                        layout: center;
                        flex-weight: 0;
                        anchor-width: 760;
                        padding: 5;
                        border-radius: 10;
                        gap: 8;
                    }
                
                    .toggle-btn {
                        flex-weight: 1;
                        anchor-width: 0;
                        anchor-height: 46;
                        padding-left: 12;
                        padding-right: 12;
                        border-radius: 8;
                    }
                
                
                    .toggle-active {
                        font-weight: bold;
                    }
                
                    .command-item {
                        layout: left;
                        flex-weight: 0;
                        background-color: #162232;
                        padding: 12;
                        border-radius: 8;
                    }
                
                    .command-icon {
                        layout: center;
                        flex-weight: 0;
                        anchor-width: 32;
                        anchor-height: 32;
                        border-radius: 8;
                        background-color: #0f1724;
                    }
                
                    .command-icon-server {
                        background-color: #3f315d(0.85);
                    }
                
                    .command-icon-player {
                        background-color: #1f4f70(0.85);
                    }
                
                    .command-icon-text {
                        font-size: 14;
                    }
                
                    .command-icon-text-server {
                        color: #e3d0ff;
                    }
                
                    .command-icon-text-player {
                        color: #d9ecff;
                    }
                
                    .command-content {
                        layout: top;
                        flex-weight: 1;
                        padding-left: 10;
                        padding-right: 10;
                    }
                
                    .command-text {
                        color: #76dd97;
                        font-size: 12;
                        font-weight: bold;
                    }
                
                    .command-type {
                        color: #8ba3bf;
                        font-size: 12;
                        padding-top: 2;
                    }
                
                    .command-actions {
                        layout: left;
                        flex-weight: 0;
                    }
                    
                    .drop-item-row {
                        layout: center;
                        flex-weight: 0;
                        background-color: #162232;
                        padding: 10;
                        border-radius: 8;
                    }
                
                    .drop-item-index {
                        color: #7e95b1;
                        font-size: 11;
                        font-weight: bold;
                        anchor-width: 30;
                        text-align: center;
                    }
                
                    .slot-background {
                        layout: top;
                        flex-weight: 0;
                        anchor-width: 64;
                        anchor-height: 72;
                        background-color: #0e131c;
                        border-radius: 8;
                    }
                
                    .slot-container {
                        layout: top;
                        flex-weight: 0;
                        anchor-width: 64;
                        anchor-height: 72;
                        background-color: #131b28;
                        border-radius: 8;
                        padding: 4;
                        align-items: center;
                    }
                
                    .slot-icon {
                        anchor-width: 36;
                        anchor-height: 36;
                    }
                
                    .slot-label {
                        color: #96acc7;
                        font-size: 10;
                        text-align: center;
                        padding-top: 2;
                    }
                
                    .slot-label-filled {
                        color: #e7f2ff;
                    }

                    .button-group {
                        layout: center;
                        flex-weight: 0;
                    }

                    .raw-button {
                        anchor-width: 56;
                        anchor-height: 56;
                        border-radius: 8;
                    }
                </style>
                """;
    }

    private TemplateProcessor createBaseTemplate() {
        return new TemplateProcessor()
                .registerComponent("statCard", """
                        <div class="stat-card">
                            <p class="stat-value" style="text-align: center;">{{$value}}</p>
                            <p class="stat-label" style="text-align: center;">{{$label}}</p>
                        </div>
                        """)

                .registerComponent("formField", """
                        <div class="form-group">
                            <div class="form-row">
                                <p class="form-label">{{$label}}</p>
                                {{#if optional}}
                                <p class="form-label-optional">(Optional)</p>
                                {{/if}}
                            </div>
                            <input type="text" id="{{$id}}" class="form-input" value="{{$value}}" 
                                   placeholder="{{$placeholder}}" maxlength="{{$maxlength|64}}" />
                            {{#if hint}}
                            <p class="form-hint">{{$hint}}</p>
                            {{/if}}
                        </div>
                        """)

                .registerComponent("numberField", """
                        <div class="form-group">
                            <p class="form-label">{{$label}}</p>
                            <input type="number" id="{{$id}}" class="form-input" 
                                   value="{{$value}}"
                                   placeholder="{{$placeholder}}"
                                   min="{{$min}}"
                                   max="{{$max}}"
                                   step="{{$step}}"
                                   data-hyui-max-decimal-places="{{$decimals|2}}" />
                            {{#if hint}}
                            <p class="form-hint">{{$hint}}</p>
                            {{/if}}
                        </div>
                        """)

                .registerComponent("checkbox", """
                        <div class="checkbox-row">
                            <input type="checkbox" id="{{$id}}" {{#if checked}}checked{{/if}} />
                            <div style="layout: top; flex-weight: 0; text-align: center;">
                                <p class="checkbox-label">{{$label}}</p>
                                {{#if description}}
                                <p class="checkbox-description">{{$description}}</p>
                                {{/if}}
                            </div>
                        </div>
                        """)

                .registerComponent("infoBox", """
                        <div class="info-box">
                            <p class="info-box-text">{{$text}}</p>
                        </div>
                        """)

                .registerComponent("sectionHeader", """
                        <div class="section-header">
                            <p class="section-title">{{$title}}</p>
                            {{#if description}}
                            <p class="section-description">{{$description}}</p>
                            {{else}}
                            <div class="spacer-sm"></div>
                            {{/if}}
                        </div>
                        """);
    }

    public static class ListItem {
        private final boolean isGroup;
        private final String groupName;
        private final String rawGroupName;
        private final String groupId;
        private final CitizenData rawCitizen;
        private final SafeCitizen citizen;

        public static ListItem forGroup(String groupName, String groupId) {
            return new ListItem(true, groupName, groupId, null);
        }

        public static ListItem forCitizen(CitizenData citizen) {
            return new ListItem(false, null, null, citizen);
        }

        private ListItem(boolean isGroup, String groupName, String groupId, CitizenData citizen) {
            this.isGroup = isGroup;
            this.rawGroupName = groupName;
            this.groupName = escapeHtml(getDisplayNameForList(groupName));
            this.groupId = groupId;
            this.rawCitizen = citizen;
            this.citizen = citizen != null ? new SafeCitizen(citizen) : null;
        }

        public boolean isGroup() { return isGroup; }
        public String getGroupName() { return groupName; }
        public String getRawGroupName() { return rawGroupName; }
        public String getGroupId() { return groupId; }
        public SafeCitizen getCitizen() { return citizen; }
        public CitizenData getRawCitizen() { return rawCitizen; }
    }

    public void openCitizensGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull Tab currentTab) {
        openCitizensGUI(playerRef, store, currentTab, "", null);
    }

    public void openCitizensGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull Tab currentTab, @Nonnull String searchQuery) {
        openCitizensGUI(playerRef, store, currentTab, searchQuery, null);
    }

    public void openCitizensGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull Tab currentTab, @Nonnull String searchQuery, @Nullable String viewingGroup) {
        pendingFollowSelections.remove(playerRef.getUuid());
        List<CitizenData> allCitizens = plugin.getCitizensManager().getAllCitizens();

        // Filter citizens by search query
        String lowerSearchQuery = searchQuery.toLowerCase(Locale.ROOT).trim();
        List<CitizenData> filteredCitizens = allCitizens;

        if (!lowerSearchQuery.isEmpty()) {
            filteredCitizens = allCitizens.stream()
                    .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(lowerSearchQuery)
                            || c.getId().toLowerCase(Locale.ROOT).contains(lowerSearchQuery)
                            || c.getGroup().toLowerCase(Locale.ROOT).contains(lowerSearchQuery))
                    .collect(java.util.stream.Collectors.toList());
        }

        String normalizedViewingGroup = normalizeGroupPath(viewingGroup);
        Set<String> visibleGroupHierarchy = new LinkedHashSet<>();
        if (lowerSearchQuery.isEmpty()) {
            visibleGroupHierarchy.addAll(plugin.getCitizensManager().getAllGroups());
        } else {
            for (String groupName : plugin.getCitizensManager().getAllGroups()) {
                if (groupName.toLowerCase(Locale.ROOT).contains(lowerSearchQuery)) {
                    visibleGroupHierarchy.add(groupName);
                }
            }
        }
        visibleGroupHierarchy.addAll(collectGroupHierarchy(filteredCitizens));
        List<String> childGroups = visibleGroupHierarchy.stream()
                .filter(group -> isDirectChildGroup(group, normalizedViewingGroup))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.toList());

        List<CitizenData> directCitizens = filteredCitizens.stream()
                .filter(citizen -> isDirectCitizenInGroup(citizen, normalizedViewingGroup))
                .sorted(Comparator.comparing(c -> c.getName().toLowerCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.toList());

        List<ListItem> unifiedList = new ArrayList<>();
        boolean isViewingSpecificGroup = !normalizedViewingGroup.isEmpty();

        for (String groupName : childGroups) {
            unifiedList.add(ListItem.forGroup(groupName, groupEventId(groupName)));
        }
        for (CitizenData citizen : directCitizens) {
            unifiedList.add(ListItem.forCitizen(citizen));
        }

        if (!isViewingSpecificGroup && !lowerSearchQuery.isEmpty()) {
            for (CitizenData citizen : filteredCitizens) {
                if (normalizeGroupPath(citizen.getGroup()).isEmpty()) {
                    continue;
                }
                unifiedList.add(ListItem.forCitizen(citizen));
            }
        }

        TemplateProcessor template = createBaseTemplate()
                .setVariable("citizenCount", allCitizens.size())
                .setVariable("isCreateTab", currentTab == Tab.CREATE)
                .setVariable("isManageTab", currentTab == Tab.MANAGE)
                .setVariable("unifiedList", unifiedList)
                .setVariable("hasCitizens", !unifiedList.isEmpty())
                .setVariable("searchQuery", escapeHtml(searchQuery))
                .setVariable("viewingGroup", escapeHtml(normalizedViewingGroup))
                .setVariable("isViewingGroup", isViewingSpecificGroup);

        String html = template.process(getSharedStyles() + """
            <div class="page-overlay">
                <div class="main-container decorated-container" style="anchor-width: 960; anchor-height: 900;">
            
                    <!-- Header -->
                    <div class="header container-title">
                        <div class="header-content">
                            <p class="header-title">Citizens Manager</p>
                        </div>
                    </div>
            
                    <!-- Body -->
                    <div class="body">

                        <p class="page-description">Create and manage interactive NPCs for your server</p>
                        <div class="spacer-sm"></div>
            
                        <!-- Stats Row -->
                        <div class="stats-row">
                            {{@statCard:value={{$citizenCount}},label=Total Citizens}}
                        </div>
            
                        <div class="spacer-md"></div>
            
                        <!-- Tabs -->
                        <div class="tab-container">
                            <button id="tab-create" class="secondary-button{{#if isCreateTab}} button-selected{{/if}}" style="anchor-width: 180;">Create</button>
                            <div class="spacer-h-sm"></div>
                            <button id="tab-manage" class="secondary-button{{#if isManageTab}} button-selected{{/if}}" style="anchor-width: 180;">Manage</button>
                        </div>
            
                        <div class="spacer-md"></div>
            
                        <!-- Tab Content -->
                        {{#if isCreateTab}}
                        <!-- Create Tab -->
                        <div class="card" style="flex-weight: 1;">
                            <div class="card-body" style="layout: center; flex-weight: 1;">
                                <div class="empty-state-content">
                                    <p class="empty-state-title">Create a New Citizen</p>
                                    <p class="empty-state-description">Citizens are interactive NPCs that can execute commands,</p>
                                    <p class="empty-state-description">display custom messages, and bring your server to life.</p>
                                    <div class="spacer-lg"></div>
                                    <div class="btn-row">
                                        <button id="start-create" class="secondary-button" style="anchor-width: 220;">Start Creating</button>
                                    </div>
                                </div>
                            </div>
                        </div>
                        {{else}}
                        <!-- Manage Tab -->
                        <!-- Search Bar -->
                        <div class="form-row" style="align-items: flex-end;">
                            <div class="form-group" style="flex-weight: 1;">
                                <label class="form-label">Search Citizens & Groups</label>
                                <input id="search-input" type="text" class="form-input" placeholder="Search by name, ID, or group..." value="{{$searchQuery}}" />
                            </div>
                            <div class="spacer-h-sm"></div>
                            <div style="layout: center; flex-weight: 0;">
                                <button id="search-btn" class="secondary-button" style="anchor-width: 150; anchor-height: 40;">Search</button>
                            </div>
                        </div>
                        
                        <div class="spacer-sm"></div>

                        <div class="form-row">
                            <button id="edit-closest-btn" class="secondary-button" style="anchor-width: 220; anchor-height: 40;">Edit Closest Citizen</button>
                            <div class="spacer-h-sm"></div>
                            <button id="get-citizen-stick-btn" class="secondary-button" style="anchor-width: 220; anchor-height: 40;">Get Citizen Stick</button>
                            <div class="spacer-h-sm"></div>
                            <button id="respawn-all-btn" class="secondary-button" style="anchor-width: 170; anchor-height: 40;">Respawn All</button>
                            <div class="spacer-h-sm"></div>
                            <button id="new-root-group-btn" class="secondary-button" style="anchor-width: 150; anchor-height: 40;">New Group</button>
                        </div>
                        
                        {{#if isViewingGroup}}
                        <!-- Viewing Specific Group -->
                        <div class="form-row">
                            <button id="back-to-all" class="secondary-button" style="anchor-width: 120;">Back</button>
                            <div class="spacer-h-sm"></div>
                            <button id="new-child-group-btn" class="secondary-button" style="anchor-width: 170;">New Child Group</button>
                            <div class="spacer-h-sm"></div>
                            <button id="move-current-group-btn" class="secondary-button" style="anchor-width: 150;">Move Group</button>
                            <div class="spacer-h-sm"></div>
                            <button id="respawn-current-group-btn" class="secondary-button" style="anchor-width: 160;">Respawn Group</button>
                            <div style="flex-weight: 1; layout: center;">
                                <p style="font-size: 18px; font-weight: bold; color: #FFFFFF;">Group: {{$viewingGroup}}</p>
                            </div>
                        </div>
                        <div class="spacer-sm"></div>
                        {{/if}}
                        
                        {{#if hasCitizens}}
                        <!-- Unified List -->
                        <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"'>
                        {{#each unifiedList}}
                        {{#if !isGroup}}
                        <!-- Citizen Item -->
                            <div class="list-item">
                                <div class="list-item-content">
                                    <p class="list-item-title">{{$citizen.name}}</p>
                                    <p class="list-item-subtitle">Model: {{$citizen.modelId}} | Scale: {{$citizen.scale}}</p>
                                    <p class="list-item-meta">ID: {{$citizen.id}}{{#if $citizen.group}} | Group: {{$citizen.group}}{{/if}}</p>
                                </div>
                                <div class="list-item-actions">
                                    <button id="tp-{{$citizen.id}}" class="secondary-button small-secondary-button" style="anchor-width: 110;">TP</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="edit-{{$citizen.id}}" class="secondary-button small-secondary-button" style="anchor-width: 110;">Edit</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="clone-{{$citizen.id}}" class="secondary-button small-secondary-button" style="anchor-width: 120;">Clone</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="remove-{{$citizen.id}}" class="secondary-button small-secondary-button" style="anchor-width: 140;">Remove</button>
                                </div>
                            </div>
                            {{else}}
                            <!-- Group Item -->
                            <div class="list-item" style="background: rgba(100, 100, 255, 0.1);">
                                <div class="list-item-content">
                                    <p class="list-item-title">{{$groupName}}</p>
                                    <p class="list-item-subtitle">Click to view citizens in this group</p>
                                </div>
                                <div class="list-item-actions">
                                    <button id="view-group-{{$groupId}}" class="secondary-button small-secondary-button" style="anchor-width: 110;">View</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="rename-group-{{$groupId}}" class="secondary-button small-secondary-button" style="anchor-width: 130;">Rename</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="move-group-{{$groupId}}" class="secondary-button small-secondary-button" style="anchor-width: 110;">Move</button>
                                </div>
                            </div>
                            {{/if}}
                            <div class="spacer-sm"></div>
                            {{/each}}
                        </div>
                        {{else}}
                        <div class="empty-state">
                            <div class="empty-state-content">
                                <p class="empty-state-title">No Citizens Yet</p>
                                <p class="empty-state-description">Switch to the Create tab to add your first citizen!</p>
                            </div>
                        </div>
                        {{/if}}
                        {{/if}}
                        
                    </div>
                </div>
            </div>
            """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupMainEventListeners(page, playerRef, store, currentTab, unifiedList, searchQuery, viewingGroup);

        page.open(store);
    }

    public void openCreateCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store) {
        openCreateCitizenGUI(playerRef, store, true, "", 0, false, false,
                false, CitizenData.MAP_MARKER_TYPE_PIN, "", false, "", 1.0f, true, "",
                1.0f, "", "", false, false, "", true, "", 25.0f);
    }

    public void openCreateCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                      boolean isPlayerModel, String name, float nametagOffset, boolean hideNametag, boolean hideNpc,
                                      boolean mapMarkerEnabled, String mapMarkerType,
                                      boolean modelNametagEnabled, String nametagModelId, float nametagModelScale, boolean rotateNametagTowardsPlayer,
                                      String modelId, float scale, String permission, String permMessage, boolean useLiveSkin,
                                      boolean preserveState, String skinUsername, boolean rotateTowardsPlayer,
                                      String group, float lookAtDistance) {
        openCreateCitizenGUI(playerRef, store, isPlayerModel, name, nametagOffset, hideNametag, hideNpc,
                mapMarkerEnabled, mapMarkerType, "", modelNametagEnabled, nametagModelId, nametagModelScale, rotateNametagTowardsPlayer,
                modelId, scale, permission, permMessage, useLiveSkin, preserveState, skinUsername, rotateTowardsPlayer, group, lookAtDistance);
    }

    private void openCreateCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                      boolean isPlayerModel, String name, float nametagOffset, boolean hideNametag, boolean hideNpc,
                                      boolean mapMarkerEnabled, String mapMarkerType, String mapMarkerName,
                                      boolean modelNametagEnabled, String nametagModelId, float nametagModelScale, boolean rotateNametagTowardsPlayer,
                                      String modelId, float scale, String permission, String permMessage, boolean useLiveSkin,
                                      boolean preserveState, String skinUsername, boolean rotateTowardsPlayer,
                                      String group, float lookAtDistance) {
        pendingFollowSelections.remove(playerRef.getUuid());

        List<String> allGroups = plugin.getCitizensManager().getAllGroups();
        String groupOptionsHTML = generateGroupDropdownOptions(group, allGroups);

        TemplateProcessor template = createBaseTemplate()
                .setVariable("isPlayerModel", isPlayerModel)
                .setVariable("name", escapeHtml(name))
                .setVariable("hideNpc", hideNpc)
                .setVariable("mapMarkerEnabled", mapMarkerEnabled)
                .setVariable("mapMarkerTypeOptions", generateMapMarkerTypeOptions(mapMarkerType))
                .setVariable("mapMarkerName", escapeHtml(mapMarkerName))
                .setVariable("group", escapeHtml(group))
                .setVariable("groupOptions", groupOptionsHTML)
                .setVariable("modelId", modelId.isEmpty() ? "PlayerTestModel_V" : modelId)
                .setVariable("scale", scale)
                .setVariable("permission", escapeHtml(permission))
                .setVariable("permMessage", escapeHtml(permMessage))
                .setVariable("useLiveSkin", useLiveSkin)
                .setVariable("skinUsername", escapeHtml(skinUsername))
                .setVariable("rotateTowardsPlayer", rotateTowardsPlayer)
                .setVariable("lookAtDistance", lookAtDistance)
                .setVariable("nametagSummary", buildNametagSummary(name, nametagOffset, hideNametag, modelNametagEnabled,
                        nametagModelId, nametagModelScale, rotateNametagTowardsPlayer))
                .setVariable("entityOptions", generateEntityDropdownOptions(modelId.isEmpty() ? "PlayerTestModel_V" : modelId));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 900; anchor-height: 860;">
                
                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Create New Citizen</p>
                            </div>
                        </div>
                
                        <!-- Body -->
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">

                            <p class="page-description">Configure your new NPC's appearance and behavior</p>
                            <div class="spacer-sm"></div>
                
                            <!-- Info Box -->
                            {{@infoBox:text=The citizen will spawn at your current position and rotation}}
                
                            <div class="spacer-md"></div>
                
                            <!-- Basic Information Section -->
                            <div class="section">
                                {{@sectionHeader:title=Basic Information,description=Set the citizen's name and display settings}}
                 
                                <div class="form-row" style="horizontal-align: center;">
                                     <div class="form-col-fixed" style="anchor-width: 460;">
                                         <div class="form-group">
                                             <p class="form-label">Citizen Name *</p>
                                             <input type="text" id="citizen-name" class="form-input" value="{{$name}}"\s
                                                    placeholder="Enter a display name" />
                                             <div class="spacer-xs"></div>
                                             <button id="nametag-settings-btn" class="secondary-button small-secondary-button" style="anchor-width: 210;">Nametag Settings</button>
                                             <div class="spacer-xs"></div>
                                             <p class="form-hint" style="text-align: center;">{{$nametagSummary}}</p>
                                         </div>
                                     </div>
                                </div>

                                <div class="spacer-sm"></div>

                                <div class="checkbox-row">
                                    <input type="checkbox" id="hide-npc-check" {{#if hideNpc}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Hide NPC</p>
                                        <p class="checkbox-description">Hide the NPC entity</p>
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="checkbox-row">
                                    <input type="checkbox" id="map-marker-check" {{#if mapMarkerEnabled}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Show Map Marker</p>
                                        <p class="checkbox-description">Add this citizen to the world map</p>
                                    </div>
                                </div>
                                {{#if mapMarkerEnabled}}
                                <div class="spacer-xs"></div>
                                <div class="form-group" style="anchor-width: 360;">
                                    <p class="form-label">Map Marker Type</p>
                                    <select id="map-marker-type" data-hyui-showlabel="true">
                                        {{{$mapMarkerTypeOptions}}}
                                    </select>
                                    <p class="form-hint">Used when Show Map Marker is enabled</p>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-group" style="anchor-width: 360;">
                                    <p class="form-label">Marker Name</p>
                                    <input type="text" id="map-marker-name" class="form-input" value="{{$mapMarkerName}}"
                                           placeholder="Leave empty to use citizen name" maxlength="64" />
                                    <p class="form-hint">Leave empty to use the citizen name</p>
                                </div>
                                {{/if}}
                            </div>

                            <div class="spacer-md"></div>
                
                            <!-- Group Section -->
                            <div class="section">
                                {{@sectionHeader:title=Group,description=Organize citizens into groups for easier management}}
                
                                <div class="form-group">
                                    <p class="form-label" style="text-align: center;">Select Existing Group</p>
                                    <select id="group-dropdown" data-hyui-showlabel="true">
                                        {{{$groupOptions}}}
                                    </select>
                                    <p class="form-hint" style="text-align: center;">Choose from existing groups</p>
                                </div>
                
                                <div class="spacer-sm"></div>
                                <div class="divider"></div>
                                <div class="spacer-sm"></div>
                
                                <div class="form-group">
                                    <p class="form-label" style="text-align: center;">Or Enter New Group Name</p>
                                    <input type="text" id="group-custom" class="form-input" value="{{$group}}" placeholder="Enter group name or leave empty" style="anchor-width: 200;" />
                                    <p class="form-hint" style="text-align: center;">Type a group name to create a new group or use an existing one</p>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Entity Type Section -->
                            <div class="section">
                                {{@sectionHeader:title=Entity Type,description=Choose whether this citizen uses a player model or another entity}}
                
                                <div class="toggle-group">
                                    <button id="type-player" class="secondary-button toggle-btn{{#if isPlayerModel}} toggle-active{{/if}}" style="anchor-width: 360; anchor-height: 48;">Player Model</button>
                                    <button id="type-entity" class="secondary-button toggle-btn{{#if !isPlayerModel}} toggle-active{{/if}}" style="anchor-width: 360; anchor-height: 48;">Other Entities</button>
                                </div>
                
                                <div class="spacer-md"></div>
                
                                {{#if isPlayerModel}}
                                <!-- Player Skin Configuration -->
                                <div class="card">
                                    <div class="card-header" style="layout: center;">
                                        <p class="card-title" style="text-align: center; flex-weight: 0;">Player Skin Configuration</p>
                                    </div>
                                    <div class="card-body">
                                        <div class="form-group">
                                            <p class="form-label" style="text-align: center;">Skin Username</p>
                                            <div class="form-row">
                                                <input type="text" id="skin-username" class="form-input" value="{{$skinUsername}}"
                                                       placeholder="Enter username" style="anchor-width: 250;" />
                                                <div class="spacer-h-sm"></div>
                                                <button id="get-player-skin-btn" class="secondary-button small-secondary-button" style="anchor-width: 160;">Use My Skin</button>
                                            </div>
                                            <p class="form-hint" style="text-align: center;">Leave empty to use your current skin</p>
                                        </div>

                                        <div class="spacer-sm"></div>

                                        <div class="form-group">
                                            <button id="random-skin-btn" class="secondary-button" style="anchor-width: 225;">Random Skin</button>
                                            <p class="form-hint" style="text-align: center;">Generate a random skin for this citizen</p>
                                        </div>

                                        <div class="spacer-sm"></div>
                
                                        <div class="checkbox-row">
                                            <input type="checkbox" id="live-skin-check" {{#if useLiveSkin}}checked{{/if}} />
                                            <div style="layout: top; flex-weight: 0; text-align: center;">
                                                <p class="checkbox-label">Enable Live Skin Updates</p>
                                                <p class="checkbox-description">Automatically refresh the skin every 30 minutes</p>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                {{else}}
                                <!-- Entity Selection -->
                                <div class="card">
                                    <div class="card-header">
                                        <p class="card-title" style="text-align: center; flex-weight: 0;">Entity Selection</p>
                                    </div>
                                    <div class="card-body">
                                        <div class="form-group">
                                            <p class="form-label" style="text-align: center;">Select Entity Type</p>
                                            <select id="entity-dropdown" value="{{$modelId}}" data-hyui-showlabel="true">
                                                {{$entityOptions}}
                                            </select>
                                            <p class="form-hint" style="text-align: center;">Choose from common entity types</p>
                                        </div>
                
                                        <div class="spacer-sm"></div>
                                        <div class="divider"></div>
                                        <div class="spacer-sm"></div>
                
                                        <div class="form-group">
                                            <p class="form-label" style="text-align: center;">Or Enter An Entity/Model ID</p>
                                            <input type="text" id="citizen-model-id" class="form-input" value="{{$modelId}}"
                                                   placeholder="e.g., PlayerTestModel_V, Sheep" maxlength="64" style="anchor-width: 200;" />
                                            <p class="form-hint" style="text-align: center;">You can also type an entity/model ID</p>
                                        </div>
                                    </div>
                                </div>
                                {{/if}}
                               <div class="checkbox-row">
                                    <input type="checkbox" id="rotate-towards-player" {{#if rotateTowardsPlayer}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Rotate Towards Player</p>
                                        <p class="checkbox-description">The citizen will face players when they approach</p>
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="anchor-width: 260;">
                                        {{@numberField:id=look-at-distance,label=Look-At Distance,value={{$lookAtDistance}},placeholder=25,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Scale Section -->
                            <div class="section">
                                {{@sectionHeader:title=Scale,description=Adjust the size of the citizen}}
                
                                <div class="form-row">
                                    <div class="form-col-fixed" style="anchor-width: 200;">
                                        <div class="form-group">
                                            <p class="form-label">Scale Factor *</p>
                                            <input type="number" id="citizen-scale" class="form-input"
                                                   value="{{$scale}}"
                                                   placeholder="1.0"
                                                   min="0.01" max="100" step="0.25"
                                                   data-hyui-max-decimal-places="2" />
                                            <p class="form-hint">Default: 1.0 (normal size)</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>

                            <!-- Permissions Section -->
                            <div class="section">
                                {{@sectionHeader:title=Permissions,description=Control who can interact with this citizen}}

                                <div class="form-row">
                                    <div class="form-col">
                                        <div class="form-group">
                                            <div class="form-row">
                                                <p class="form-label">Required Permission</p>
                                                <p class="form-label-optional">(Optional)</p>
                                            </div>
                                            <input type="text" id="citizen-permission" class="form-input" value="{{$permission}}"
                                                   placeholder="e.g., citizens.interact.vip" style="anchor-width: 215;" />
                                            <p class="form-hint" style="text-align: center;">Leave empty to allow everyone</p>
                                        </div>
                                    </div>
                                </div>

                                <div class="spacer-sm"></div>
                
                                <div class="form-row">
                                    <div class="form-col">
                                        <div class="form-group">
                                            <div class="form-row">
                                                <p class="form-label">No Permission Message</p>
                                                <p class="form-label-optional">(Optional)</p>
                                            </div>
                                            <input type="text" id="citizen-perm-message" class="form-input" value="{{$permMessage}}" 
                                                   placeholder="e.g., You need VIP rank to interact!" style="anchor-width: 250;" />
                                            <p class="form-hint" style="text-align: center;">Message shown when player lacks permission</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                
                            <div class="spacer-lg"></div>
                
                        </div>
                
                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="create-btn" class="secondary-button btn-wide">Create</button>
                        </div>
                
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupCreateCitizenListeners(page, playerRef, store, isPlayerModel, name, nametagOffset, hideNametag, hideNpc,
                mapMarkerEnabled, mapMarkerType, mapMarkerName,
                modelNametagEnabled, nametagModelId, nametagModelScale, rotateNametagTowardsPlayer,
                modelId, scale, permission, permMessage, useLiveSkin, skinUsername, rotateTowardsPlayer, group, lookAtDistance);

        page.open(store);
    }

    public void openEditCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull CitizenData citizen) {
        openEditCitizenGUI(playerRef, store, citizen, citizen.isMapMarkerEnabled(), citizen.getMapMarkerType(), citizen.getMapMarkerName());
    }

    private void openEditCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull CitizenData citizen,
                                    boolean draftMapMarkerEnabled, @Nullable String draftMapMarkerType, @Nullable String draftMapMarkerName) {
        pendingFollowSelections.remove(playerRef.getUuid());
        List<String> allGroups = plugin.getCitizensManager().getAllGroups();
        String groupOptionsHTML = generateGroupDropdownOptions(citizen.getGroup(), allGroups);
        String normalizedDraftMapMarkerType = CitizenData.normalizeMapMarkerType(draftMapMarkerType);

        TemplateProcessor template = createBaseTemplate()
                .setVariable("citizen", new SafeCitizen(citizen))
                .setVariable("isPlayerModel", citizen.isPlayerModel())
                .setVariable("useLiveSkin", citizen.isUseLiveSkin())
                .setVariable("rotateTowardsPlayer", citizen.getRotateTowardsPlayer())
                .setVariable("lookAtDistance", citizen.getLookAtDistance())
                .setVariable("hideNpc", citizen.isHideNpc())
                .setVariable("mapMarkerEnabled", draftMapMarkerEnabled)
                .setVariable("mapMarkerTypeOptions", generateMapMarkerTypeOptions(normalizedDraftMapMarkerType))
                .setVariable("mapMarkerName", escapeHtml(draftMapMarkerName != null ? draftMapMarkerName : ""))
                .setVariable("groupOptions", groupOptionsHTML)
                .setVariable("nametagSummary", buildNametagSummary(
                        citizen.getName(),
                        citizen.getNametagOffset(),
                        citizen.isHideNametag(),
                        citizen.isModelNametagEnabled(),
                        citizen.getNametagModelId(),
                        citizen.getNametagModelScale(),
                        citizen.isRotateNametagTowardsPlayer()
                ))
                .setVariable("entityOptions", generateEntityDropdownOptions(citizen.getModelId()));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 900; anchor-height: 850;">
                
                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Edit Citizen</p>
                            </div>
                        </div>
                
                        <!-- Body -->
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">

                            <p class="page-description">ID: {{$citizen.id}}</p>
                            <div class="spacer-sm"></div>
                
                            <!-- Basic Information Section -->
                            <div class="section">
                                {{@sectionHeader:title=Basic Information,description=Set the citizen's name and display settings}}
                 
                                <div class="form-row" style="horizontal-align: center;">
                                    <div class="form-col-fixed" style="anchor-width: 460;">
                                        <div class="form-group">
                                            <p class="form-label">Citizen Name *</p>
                                            <input type="text" id="citizen-name" class="form-input" value="{{$citizen.name}}"\s
                                                   placeholder="Enter a display name" maxlength="32" />
                                            <div class="spacer-xs"></div>
                                            <button id="nametag-settings-btn" class="secondary-button small-secondary-button" style="anchor-width: 210;">Nametag Settings</button>
                                            <div class="spacer-xs"></div>
                                            <p class="form-hint" style="text-align: center;">{{$nametagSummary}}</p>
                                        </div>
                                    </div>
                                </div>
                 
                                <div class="spacer-sm"></div>

                                <div class="checkbox-row">
                                    <input type="checkbox" id="hide-npc-check" {{#if hideNpc}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Hide NPC</p>
                                        <p class="checkbox-description">Hide the NPC entity</p>
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="checkbox-row">
                                    <input type="checkbox" id="map-marker-check" {{#if mapMarkerEnabled}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Show Map Marker</p>
                                        <p class="checkbox-description">Add this citizen to the world map</p>
                                    </div>
                                </div>
                                {{#if mapMarkerEnabled}}
                                <div class="spacer-xs"></div>
                                <div class="form-group" style="anchor-width: 360;">
                                    <p class="form-label">Map Marker Type</p>
                                    <select id="map-marker-type" data-hyui-showlabel="true">
                                        {{{$mapMarkerTypeOptions}}}
                                    </select>
                                    <p class="form-hint">NPC Type uses an official portrait when one matches the model</p>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-group" style="anchor-width: 360;">
                                    <p class="form-label">Marker Name</p>
                                    <input type="text" id="map-marker-name" class="form-input" value="{{$mapMarkerName}}"
                                           placeholder="Leave empty to use citizen name" maxlength="64" />
                                    <p class="form-hint">Leave empty to use the citizen name</p>
                                </div>
                                {{/if}}
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Group Section -->
                            <div class="section">
                                {{@sectionHeader:title=Group,description=Organize citizens into groups for easier management}}
                
                                <div class="form-group">
                                    <p class="form-label" style="text-align: center;">Select Existing Group</p>
                                    <select id="group-dropdown" data-hyui-showlabel="true">
                                        {{{$groupOptions}}}
                                    </select>
                                    <p class="form-hint" style="text-align: center;">Choose from existing groups</p>
                                </div>
                
                                <div class="spacer-sm"></div>
                                <div class="divider"></div>
                                <div class="spacer-sm"></div>
                
                                <div class="form-group">
                                    <p class="form-label" style="text-align: center;">Or Enter New Group Name</p>
                                    <input type="text" id="group-custom" class="form-input" value="{{$group}}" placeholder="Enter group name or leave empty" style="anchor-width: 200;" />
                                    <p class="form-hint" style="text-align: center;">Type a group name to create a new group or use an existing one</p>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Entity Type Section -->
                            <div class="section">
                                {{@sectionHeader:title=Entity Type,description=Choose whether this citizen uses a player model or another entity}}
                
                                <div class="toggle-group">
                                    <button id="type-player" class="secondary-button toggle-btn{{#if isPlayerModel}} toggle-active{{/if}}" style="anchor-width: 360; anchor-height: 48;">Player Model</button>
                                    <button id="type-entity" class="secondary-button toggle-btn{{#if !isPlayerModel}} toggle-active{{/if}}" style="anchor-width: 360; anchor-height: 48;">Other Entities</button>
                                </div>
                
                                <div class="spacer-md"></div>
                
                                {{#if isPlayerModel}}
                                <!-- Player Skin Configuration -->
                                <div class="card">
                                    <div class="card-header" style="layout: center;">
                                        <p class="card-title" style="text-align: center; flex-weight: 0;">Player Skin Configuration</p>
                                    </div>
                                    <div class="card-body">
                                        <div class="form-group">
                                            <p class="form-label" style="text-align: center;">Skin Username</p>
                                            <div class="form-row">
                                                <input type="text" id="skin-username" class="form-input" value="{{$citizen.skinUsername}}"
                                                       placeholder="Enter username" style="anchor-width: 250;" />
                                                <div class="spacer-h-sm"></div>
                                                <button id="get-player-skin-btn" class="secondary-button small-secondary-button" style="anchor-width: 160;">Use My Skin</button>
                                            </div>
                                            <p class="form-hint" style="text-align: center;">Leave empty to use your current skin</p>
                                        </div>

                                        <div class="spacer-sm"></div>

                                        <div class="form-group">
                                            <button id="random-skin-btn" class="secondary-button" style="anchor-width: 225;">Random Skin</button>
                                            <p class="form-hint" style="text-align: center;">Generate a random skin for this citizen</p>
                                        </div>

                                        <div class="spacer-sm"></div>

                                        <div class="form-group">
                                            <button id="customize-skin-btn" class="secondary-button" style="anchor-width: 225;">Customize Skin</button>
                                            <p class="form-hint" style="text-align: center;">Edit individual cosmetic slots</p>
                                        </div>

                                        <div class="spacer-sm"></div>

                                        <div class="checkbox-row">
                                            <input type="checkbox" id="live-skin-check" {{#if useLiveSkin}}checked{{/if}} />
                                            <div style="layout: top; flex-weight: 0; text-align: center;">
                                                <p class="checkbox-label">Enable Live Skin Updates</p>
                                                <p class="checkbox-description">Automatically refresh the skin every 30 minutes</p>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                {{else}}
                                <!-- Entity Selection -->
                                <div class="card">
                                    <div class="card-header">
                                        <p class="card-title" style="text-align: center; flex-weight: 0;">Entity Selection</p>
                                    </div>
                                    <div class="card-body">
                                        <div class="form-group">
                                            <p class="form-label" style="text-align: center;">Select Entity Type</p>
                                            <select id="entity-dropdown" value="{{$citizen.modelId}}" data-hyui-showlabel="true">
                                                {{$entityOptions}}
                                            </select>
                                            <p class="form-hint" style="text-align: center;">Choose from common entity types</p>
                                        </div>
                
                                        <div class="spacer-sm"></div>
                                        <div class="divider"></div>
                                        <div class="spacer-sm"></div>
                
                                        <div class="form-group">
                                            <p class="form-label" style="text-align: center;">Or Enter An Entity/Model ID</p>
                                            <input type="text" id="citizen-model-id" class="form-input" value="{{$citizen.modelId}}"
                                                   placeholder="Custom model ID" maxlength="64" style="anchor-width: 200;" />
                                            <p class="form-hint" style="text-align: center;">You can also type an entity/model ID</p>
                                        </div>
                                    </div>
                                </div>
                                {{/if}}
                                 <div class="checkbox-row">
                                    <input type="checkbox" id="rotate-towards-player" {{#if rotateTowardsPlayer}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Rotate Towards Player</p>
                                        <p class="checkbox-description">The citizen will face players when they approach</p>
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="anchor-width: 260;">
                                        {{@numberField:id=look-at-distance,label=Look-At Distance,value={{$lookAtDistance}},placeholder=25,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Scale Section -->
                            <div class="section">
                                {{@sectionHeader:title=Scale,description=Adjust the size of the citizen}}
                
                                <div class="form-row">
                                    <div class="form-col-fixed" style="anchor-width: 200;">
                                        <div class="form-group">
                                            <p class="form-label">Scale Factor *</p>
                                            <input type="number" id="citizen-scale" class="form-input"
                                                   value="{{$citizen.scale}}"
                                                   min="0.01" max="100" step="0.25"
                                                   data-hyui-max-decimal-places="2" />
                                            <p class="form-hint">Default: 1.0 (normal size)</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>

                            <!-- Permissions Section -->
                            <div class="section">
                                {{@sectionHeader:title=Permissions,description=Control who can interact with this citizen}}

                                <div class="form-row">
                                    <div class="form-col">
                                        <div class="form-group">
                                            <div class="form-row">
                                                <p class="form-label">Required Permission</p>
                                                <p class="form-label-optional">(Optional)</p>
                                            </div>
                                            <input type="text" id="citizen-permission" class="form-input" value="{{$citizen.requiredPermission}}"
                                                   placeholder="e.g., citizens.interact.vip" style="anchor-width: 215;" />
                                            <p class="form-hint" style="text-align: center;">Leave empty to allow everyone</p>
                                        </div>
                                    </div>
                                </div>

                                <div class="spacer-sm"></div>
                
                                <div class="form-row">
                                    <div class="form-col">
                                        <div class="form-group">
                                            <div class="form-row">
                                                <p class="form-label">No Permission Message</p>
                                                <p class="form-label-optional">(Optional)</p>
                                            </div>
                                            <input type="text" id="citizen-perm-message" class="form-input" value="{{$citizen.noPermissionMessage}}" 
                                                   placeholder="e.g., You need VIP rank!" style="anchor-width: 250;" />
                                            <p class="form-hint" style="text-align: center;">Message shown when player lacks permission</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Quick Actions Section -->
                            <div class="section">
                                {{@sectionHeader:title=Quick Actions,description=Teleport&#44; duplicate&#44; or remove this citizen immediately}}

                                <div class="form-row">
                                    <button id="edit-tp-btn" class="secondary-button" style="anchor-width: 180; anchor-height: 44;">TP</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="edit-clone-btn" class="secondary-button" style="anchor-width: 180; anchor-height: 44;">Clone</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="edit-remove-btn" class="secondary-button" style="anchor-width: 180; anchor-height: 44;">Remove</button>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Configuration Section -->
                            <div class="section">
                                {{@sectionHeader:title=Configuration,description=Open specialized editors and apply setup changes}}

                                <div class="form-row">
                                    <button id="edit-commands-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 44;">Commands</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="messages-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 44;">Messages</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="set-items-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 44;">Set Items</button>
                                </div>
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    <button id="behaviors-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 44;">Behaviors</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="schedule-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 44;">Schedule</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="change-position-btn" class="secondary-button" style="anchor-width: 210; anchor-height: 44;">Update Position</button>
                                </div>
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    <div class="spacer-h-sm"></div>
                                    <button id="first-interaction-btn" class="secondary-button" style="anchor-width: 240; anchor-height: 44;">First Interaction</button>
                                </div>
                            </div>

                            <div class="spacer-lg"></div>
                
                        </div>
                
                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 220;">Save Changes</button>
                        </div>
                
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupEditCitizenListeners(page, playerRef, store, citizen, draftMapMarkerEnabled, normalizedDraftMapMarkerType,
                draftMapMarkerName != null ? draftMapMarkerName : "");

        page.open(store);
    }

    public static class IndexedAnimationBehavior {
        private final int index;
        private final String type;
        private final String animationName;
        private final int animationSlot;
        private final String slotName;
        private final float intervalSeconds;
        private final float proximityRange;
        private final boolean isTimed;
        private final boolean isProximity;

        public IndexedAnimationBehavior(int index, AnimationBehavior ab) {
            this.index = index;
            this.type = ab.getType();
            this.animationName = escapeHtml(ab.getAnimationName());
            this.animationSlot = ab.getAnimationSlot();
            this.slotName = switch (ab.getAnimationSlot()) {
                case 1 -> "Status";
                case 2 -> "Action";
                case 3 -> "Face";
                case 4 -> "Emote";
                default -> "Movement";
            };
            this.intervalSeconds = ab.getIntervalSeconds();
            this.proximityRange = ab.getProximityRange();
            this.isTimed = "TIMED".equals(ab.getType());
            this.isProximity = ab.getType().startsWith("ON_PROXIMITY");
        }
    }

    public static class IndexedMessage {
        private final int index;
        private final String message;
        private final String truncated;
        private final String interactionTrigger;
        private final String triggerLabel;
        private final float delaySeconds;
        private final boolean hasDelay;
        private final float chancePercent;
        private final boolean hasChanceModifier;

        public IndexedMessage(int index, CitizenMessage msg) {
            this.index = index;
            this.message = msg.getMessage();
            String truncatedRaw = msg.getMessage().length() > 55
                    ? msg.getMessage().substring(0, 52) + "..."
                    : msg.getMessage();
            this.truncated = escapeHtml(truncatedRaw);
            String trig = msg.getInteractionTrigger() != null ? msg.getInteractionTrigger() : "BOTH";
            this.interactionTrigger = trig;
            this.triggerLabel = switch (trig) {
                case "LEFT_CLICK" -> "Left Click";
                case "F_KEY" -> "F Key";
                default -> "Both";
            };
            this.delaySeconds = msg.getDelaySeconds();
            this.hasDelay = msg.getDelaySeconds() > 0;
            this.chancePercent = msg.getChancePercent();
            this.hasChanceModifier = msg.getChancePercent() < 100.0f;
        }

        public int getIndex() { return index; }
        public String getMessage() { return message; }
        public String getTruncated() { return truncated; }
        public String getInteractionTrigger() { return interactionTrigger; }
        public String getTriggerLabel() { return triggerLabel; }
        public float getDelaySeconds() { return delaySeconds; }
        public boolean isHasDelay() { return hasDelay; }
        public float getChancePercent() { return chancePercent; }
        public boolean isHasChanceModifier() { return hasChanceModifier; }
    }

    public static class IndexedCommandAction {
        private final int index;
        private final String command;
        private final boolean runAsServer;
        private final float delaySeconds;
        private final boolean hasDelay;
        private final String interactionTrigger;
        private final String triggerLabel;
        private final float chancePercent;
        private final boolean hasChanceModifier;

        public IndexedCommandAction(int index, CommandAction action) {
            this.index = index;
            this.command = escapeHtml(action.getCommand());
            this.runAsServer = action.isRunAsServer();
            this.delaySeconds = action.getDelaySeconds();
            this.hasDelay = action.getDelaySeconds() > 0;
            String trig = action.getInteractionTrigger() != null ? action.getInteractionTrigger() : "BOTH";
            this.interactionTrigger = trig;
            this.triggerLabel = switch (trig) {
                case "LEFT_CLICK" -> "Left Click";
                case "F_KEY" -> "F Key";
                default -> "Both";
            };
            this.chancePercent = action.getChancePercent();
            this.hasChanceModifier = action.getChancePercent() < 100.0f;
        }

        public int getIndex() { return index; }
        public String getCommand() { return command; }
        public boolean isRunAsServer() { return runAsServer; }
        public float getDelaySeconds() { return delaySeconds; }
        public boolean isHasDelay() { return hasDelay; }
        public String getInteractionTrigger() { return interactionTrigger; }
        public String getTriggerLabel() { return triggerLabel; }
        public float getChancePercent() { return chancePercent; }
        public boolean isHasChanceModifier() { return hasChanceModifier; }
    }

    public static final class IndexedWaypoint {
        private final int index;
        private final String displayX;
        private final String displayY;
        private final String displayZ;
        private final float pauseSeconds;

        public IndexedWaypoint(int index, PatrolWaypoint wp) {
            this.index = index;
            this.displayX = String.format("%.1f", wp.getX());
            this.displayY = String.format("%.1f", wp.getY());
            this.displayZ = String.format("%.1f", wp.getZ());
            this.pauseSeconds = wp.getPauseSeconds();
        }

        public int getIndex() { return index; }
        public String getDisplayX() { return displayX; }
        public String getDisplayY() { return displayY; }
        public String getDisplayZ() { return displayZ; }
        public float getPauseSeconds() { return pauseSeconds; }
    }

    public void openCommandActionsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                      @Nonnull String citizenId, @Nonnull List<CommandAction> actions,
                                      boolean isCreating) {

        List<IndexedCommandAction> indexedActions = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            indexedActions.add(new IndexedCommandAction(i, actions.get(i)));
        }

        CitizenData citizen = plugin.getCitizensManager().getCitizen(citizenId);
        String commandMode = (citizen != null) ? citizen.getCommandSelectionMode() : "ALL";
        boolean showSelectionMode = !isCreating && citizen != null;

        TemplateProcessor template = createBaseTemplate()
                .setVariable("actions", indexedActions)
                .setVariable("hasActions", !actions.isEmpty())
                .setVariable("actionCount", actions.size())
                .setVariable("showSelectionMode", showSelectionMode)
                .setVariable("modeAll", "ALL".equalsIgnoreCase(commandMode))
                .setVariable("modeRandom", "RANDOM".equalsIgnoreCase(commandMode))
                .setVariable("modeSequential", "SEQUENTIAL".equalsIgnoreCase(commandMode));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 900; anchor-height: 900;">

                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Command Actions</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body">

                            <p class="page-description">Configure commands that execute when players interact ({{$actionCount}} commands)</p>
                            <div class="spacer-sm"></div>

                            <!-- Info + Add Section -->
                            <div class="section">
                                {{@sectionHeader:title=Commands}}

                                <div style="layout: center;">
                                    <button id="add-command-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 38;">Add Command</button>
                                </div>
                                
                                <div class="spacer-sm"></div>

                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12;"><span style="color: #58a6ff;">Variables:</span> {PlayerName}, {CitizenName}, {NpcX}, {NpcY}, {NpcZ}</p>
                                        <div class="spacer-xs"></div>
                                        <p style="color: #8b949e; font-size: 12;">Each command can be triggered by <span style="color: #58a6ff;">Left Click</span>, <span style="color: #a371f7;">F Key</span>, or <span style="color: #3fb950;">Both</span>.</p>
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            {{#if showSelectionMode}}
                            <div class="section">
                                {{@sectionHeader:title=Selection Mode,description=How matching commands are selected on interaction}}
                                <div class="form-row">
                                    <button id="cmd-mode-all" class="{{#if modeAll}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">All</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="cmd-mode-random" class="{{#if modeRandom}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Random</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="cmd-mode-sequential" class="{{#if modeSequential}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Sequential</button>
                                </div>
                                <div class="spacer-xs"></div>
                                {{#if modeAll}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Runs all matching commands in order, respecting each command delay.</p>
                                {{/if}}
                                {{#if modeRandom}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Runs one random matching command each interaction.</p>
                                {{/if}}
                                {{#if modeSequential}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Cycles matching commands in order per player.</p>
                                {{/if}}
                            </div>
                            <div class="spacer-md"></div>
                            {{/if}}

                            <!-- Commands List -->
                            {{#if hasActions}}
                            <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="anchor-height: 360;">
                                {{#each actions}}
                                <div class="command-item">
                                    <div class="command-icon {{#if runAsServer}}command-icon-server{{else}}command-icon-player{{/if}}">
                                        <p class="command-icon-text {{#if runAsServer}}command-icon-text-server{{else}}command-icon-text-player{{/if}}">{{#if runAsServer}}S{{else}}P{{/if}}</p>
                                    </div>
                                    <div class="command-content">
                                        <p class="command-text">/{{$command}}</p>
                                        <p class="command-type">{{$triggerLabel}}{{#if hasDelay}} | Delay: {{$delaySeconds}}s{{/if}}{{#if hasChanceModifier}} | Chance: {{$chancePercent}}%{{/if}}</p>
                                    </div>
                                    <div class="command-actions">
                                        <button id="edit-cmd-{{$index}}" class="secondary-button small-secondary-button">Edit</button>
                                        <div class="spacer-h-sm"></div>
                                        <button id="delete-{{$index}}" class="secondary-button small-secondary-button">Delete</button>
                                    </div>
                                </div>
                                <div class="spacer-sm"></div>
                                {{/each}}
                            </div>
                            {{else}}
                            <div class="empty-state">
                                <div class="empty-state-content">
                                    <p class="empty-state-title">No Commands Added</p>
                                    <p class="empty-state-description">Click "Add Command" above to create a new command action.</p>
                                </div>
                            </div>
                            {{/if}}

                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="done-btn" class="secondary-button">Done</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupCommandActionsListeners(page, playerRef, store, citizenId, actions, isCreating);

        page.open(store);
    }

    private void setupMainEventListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                         Tab currentTab, List<ListItem> unifiedList, String searchQuery, String viewingGroup) {
        page.addEventListener("tab-create", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.CREATE, "", null));

        page.addEventListener("tab-manage", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.MANAGE, "", null));

        if (currentTab == Tab.CREATE) {
            page.addEventListener("start-create", CustomUIEventBindingType.Activating, event ->
                    openCreateCitizenGUI(playerRef, store));
        }

        if (currentTab == Tab.MANAGE) {
            // Search button event listener
            page.addEventListener("search-btn", CustomUIEventBindingType.Activating, (event, ctx) -> {
                String newSearchQuery = ctx.getValue("search-input", String.class).orElse("");
                openCitizensGUI(playerRef, store, currentTab, newSearchQuery, viewingGroup);
            });

            page.addEventListener("edit-closest-btn", CustomUIEventBindingType.Activating, event -> {
                UUID worldUUID = playerRef.getWorldUuid();
                if (worldUUID == null) {
                    playerRef.sendMessage(Message.raw("Could not determine your world.").color(Color.RED));
                    return;
                }

                Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());
                List<CitizenData> nearby = plugin.getCitizensManager().getCitizensNear(playerPos, 75.0);
                CitizenData closest = null;
                double closestDistSq = Double.MAX_VALUE;

                for (CitizenData candidate : nearby) {
                    if (!worldUUID.equals(candidate.getWorldUUID())) {
                        continue;
                    }
                    Vector3d candidatePos = candidate.getCurrentPosition() != null ? candidate.getCurrentPosition() : candidate.getPosition();
                    double dx = candidatePos.x - playerPos.x;
                    double dy = candidatePos.y - playerPos.y;
                    double dz = candidatePos.z - playerPos.z;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closest = candidate;
                    }
                }

                if (closest == null) {
                    playerRef.sendMessage(Message.raw("No nearby citizen found within 75 blocks.").color(Color.RED));
                    return;
                }

                openEditCitizenGUI(playerRef, store, closest);
            });

            page.addEventListener("new-root-group-btn", CustomUIEventBindingType.Activating, event ->
                    openCreateGroupGUI(playerRef, store, ""));

            page.addEventListener("respawn-all-btn", CustomUIEventBindingType.Activating, event -> {
                int count = plugin.getCitizensManager().respawnAllCitizens(true);
                playerRef.sendMessage(Message.raw("Respawned " + count + " citizens.").color(Color.GREEN));
                openCitizensGUI(playerRef, store, currentTab, searchQuery, viewingGroup);
            });

            page.addEventListener("get-citizen-stick-btn", CustomUIEventBindingType.Activating, event -> {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    playerRef.sendMessage(Message.raw("An error occurred.").color(Color.RED));
                    return;
                }

                Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                if (player == null) {
                    playerRef.sendMessage(Message.raw("An error occurred.").color(Color.RED));
                    return;
                }

                if (!player.hasPermission("hycitizens.admin")) {
                    playerRef.sendMessage(Message.raw("You need hycitizens.admin to get the Citizen Stick.").color(Color.RED));
                    return;
                }

                ItemStack stack = new ItemStack("CitizenStick");
                boolean added = false;
                if (player.getInventory().getHotbar().canAddItemStack(stack)) {
                    player.getInventory().getHotbar().addItemStack(stack);
                    added = true;
                } else if (player.getInventory().getStorage().canAddItemStack(stack)) {
                    player.getInventory().getStorage().addItemStack(stack);
                    added = true;
                }

                if (!added) {
                    playerRef.sendMessage(Message.raw("Your inventory is full.").color(Color.RED));
                    return;
                }

                playerRef.sendMessage(Message.raw("You received a Citizen Stick. Hit a citizen with it to open the Edit menu.").color(Color.GREEN));
            });

            // Back button listener
            if (viewingGroup != null && !viewingGroup.isEmpty()) {
                page.addEventListener("back-to-all", CustomUIEventBindingType.Activating, event -> {
                    String parentGroup = getParentGroup(viewingGroup);
                    openCitizensGUI(playerRef, store, Tab.MANAGE, "", parentGroup.isEmpty() ? null : parentGroup);
                });
                page.addEventListener("new-child-group-btn", CustomUIEventBindingType.Activating, event ->
                        openCreateGroupGUI(playerRef, store, viewingGroup));
                page.addEventListener("move-current-group-btn", CustomUIEventBindingType.Activating, event ->
                        openMoveGroupGUI(playerRef, store, viewingGroup));
                page.addEventListener("respawn-current-group-btn", CustomUIEventBindingType.Activating, event -> {
                    int count = plugin.getCitizensManager().respawnCitizensInGroup(viewingGroup, true, true);
                    playerRef.sendMessage(Message.raw("Respawned " + count + " citizens in this group.").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, currentTab, searchQuery, viewingGroup);
                });
            }

            // Register event listeners for all items in the unified list
            for (ListItem item : unifiedList) {
                if (item.isGroup()) {
                    // Group view listener - only if we're not already viewing a specific group
                    String groupId = item.getGroupId();
                    String rawGroupName = item.getRawGroupName();
                    page.addEventListener("view-group-" + groupId, CustomUIEventBindingType.Activating, event ->
                            openCitizensGUI(playerRef, store, Tab.MANAGE, "", rawGroupName ));
                    page.addEventListener("rename-group-" + groupId, CustomUIEventBindingType.Activating, event ->
                            openRenameGroupGUI(playerRef, store, rawGroupName));
                    page.addEventListener("move-group-" + groupId, CustomUIEventBindingType.Activating, event ->
                            openMoveGroupGUI(playerRef, store, rawGroupName));
                } else {
                    // Citizen action listeners
                    CitizenData citizen = item.getRawCitizen();
                    final String cid = citizen.getId();

                    page.addEventListener("tp-" + cid, CustomUIEventBindingType.Activating, event ->
                            teleportPlayerToCitizen(playerRef, citizen));

                    page.addEventListener("edit-" + cid, CustomUIEventBindingType.Activating, event ->
                            openEditCitizenGUI(playerRef, store, citizen));

                    page.addEventListener("clone-" + cid, CustomUIEventBindingType.Activating, event -> {
                        if (cloneCitizenToPlayer(playerRef, citizen)) {
                            openCitizensGUI(playerRef, store, Tab.MANAGE);
                        }
                    });

                    page.addEventListener("remove-" + cid, CustomUIEventBindingType.Activating, event -> {
                        removeCitizen(playerRef, citizen);
                        openCitizensGUI(playerRef, store, Tab.MANAGE);
                    });
                }
            }
        }
    }

    private void teleportPlayerToCitizen(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen) {
        UUID citizenWorldUUID = citizen.getWorldUUID();

        if (citizenWorldUUID == null) {
            playerRef.sendMessage(Message.raw("Failed to teleport: Citizen has no world!").color(Color.RED));
            return;
        }

        World world = Universe.get().getWorld(citizenWorldUUID);
        if (world == null) {
            playerRef.sendMessage(Message.raw("Failed to teleport: World not found!").color(Color.RED));
            return;
        }

        Vector3d tpPos = new Vector3d(citizen.getCurrentPosition());

        if (citizen.getNpcRef() != null && citizen.getNpcRef().isValid()) {
            TransformComponent transform = citizen.getNpcRef().getStore().getComponent(citizen.getNpcRef(),
                    TransformComponent.getComponentType());

            if (transform != null) {
                tpPos = new Vector3d(transform.getPosition());
            }
        }

        playerRef.getReference().getStore().addComponent(playerRef.getReference(),
                Teleport.getComponentType(), new Teleport(world, tpPos, new Vector3f(0, 0, 0)));

        playerRef.sendMessage(Message.raw("Teleported to citizen '" + citizen.getName() + "'!").color(Color.GREEN));
    }

    private boolean cloneCitizenToPlayer(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen) {
        Vector3d position = new Vector3d(playerRef.getTransform().getPosition());
        Vector3f rotation = new Vector3f(playerRef.getTransform().getRotation());

        UUID worldUUID = playerRef.getWorldUuid();
        if (worldUUID == null) {
            playerRef.sendMessage(Message.raw("Failed to clone citizen!").color(Color.RED));
            return false;
        }

        PlayerSkin playerSkin = null;
        if (citizen.getCachedSkin() != null) {
            playerSkin = new PlayerSkin(citizen.getCachedSkin());
        }

        List<CommandAction> clonedBaseCommands = copyCommandActions(citizen.getCommandActions());
        MessagesConfig sourceMessagesConfig = citizen.getMessagesConfig();
        List<CitizenMessage> clonedBaseMessages = copyCitizenMessages(sourceMessagesConfig.getMessages());

        CitizenData clonedCitizen = new CitizenData(
                UUID.randomUUID().toString(),
                citizen.getName(),
                citizen.getModelId(),
                worldUUID,
                position,
                rotation,
                citizen.getScale(),
                null,
                new ArrayList<>(),
                citizen.getRequiredPermission(),
                citizen.getNoPermissionMessage(),
                clonedBaseCommands,
                citizen.isPlayerModel(),
                citizen.isUseLiveSkin(),
                citizen.getSkinUsername(),
                playerSkin,
                citizen.getLastSkinUpdate(),
                citizen.getRotateTowardsPlayer()
        );
        clonedCitizen.setCurrentPosition(new Vector3d(position));

        clonedCitizen.setNametagOffset(citizen.getNametagOffset());
        clonedCitizen.setHideNametag(citizen.isHideNametag());
        clonedCitizen.setModelNametagEnabled(citizen.isModelNametagEnabled());
        clonedCitizen.setNametagModelId(citizen.getNametagModelId());
        clonedCitizen.setNametagModelScale(citizen.getNametagModelScale());
        clonedCitizen.setRotateNametagTowardsPlayer(citizen.isRotateNametagTowardsPlayer());
        clonedCitizen.setHideNpc(citizen.isHideNpc());
        clonedCitizen.setMapMarkerEnabled(citizen.isMapMarkerEnabled());
        clonedCitizen.setMapMarkerType(citizen.getMapMarkerType());
        clonedCitizen.setMapMarkerName(citizen.getMapMarkerName());
        clonedCitizen.setNpcHelmet(citizen.getNpcHelmet());
        clonedCitizen.setNpcChest(citizen.getNpcChest());
        clonedCitizen.setNpcGloves(citizen.getNpcGloves());
        clonedCitizen.setNpcLeggings(citizen.getNpcLeggings());
        clonedCitizen.setNpcHand(citizen.getNpcHand());
        clonedCitizen.setNpcOffHand(citizen.getNpcOffHand());
        clonedCitizen.setLookAtDistance(citizen.getLookAtDistance());
        clonedCitizen.setCommandSelectionMode(citizen.getCommandSelectionMode());

        clonedCitizen.setAnimationBehaviors(new ArrayList<>(citizen.getAnimationBehaviors()));
        clonedCitizen.setMovementBehavior(new MovementBehavior(
                citizen.getMovementBehavior().getType(),
                citizen.getMovementBehavior().getWalkSpeed(),
                citizen.getMovementBehavior().getWanderRadius(),
                citizen.getMovementBehavior().getWanderWidth(),
                citizen.getMovementBehavior().getWanderDepth()));
        clonedCitizen.setMessagesConfig(new MessagesConfig(
                clonedBaseMessages,
                sourceMessagesConfig.getSelectionMode(),
                sourceMessagesConfig.isEnabled()));

        clonedCitizen.setGroup(citizen.getGroup());

        clonedCitizen.setAttitude(citizen.getAttitude());
        clonedCitizen.setTakesDamage(citizen.isTakesDamage());
        clonedCitizen.setOverrideHealth(citizen.isOverrideHealth());
        clonedCitizen.setHealthAmount(citizen.getHealthAmount());
        clonedCitizen.setOverrideDamage(citizen.isOverrideDamage());
        clonedCitizen.setDamageAmount(citizen.getDamageAmount());
        clonedCitizen.setHealthRegenEnabled(citizen.isHealthRegenEnabled());
        clonedCitizen.setHealthRegenAmount(citizen.getHealthRegenAmount());
        clonedCitizen.setHealthRegenIntervalSeconds(citizen.getHealthRegenIntervalSeconds());
        clonedCitizen.setHealthRegenDelayAfterDamageSeconds(citizen.getHealthRegenDelayAfterDamageSeconds());

        clonedCitizen.setRespawnOnDeath(citizen.isRespawnOnDeath());
        clonedCitizen.setRespawnDelaySeconds(citizen.getRespawnDelaySeconds());

        DeathConfig srcDc = citizen.getDeathConfig();
        DeathConfig clonedDc = new DeathConfig();
        clonedDc.setCommandSelectionMode(srcDc.getCommandSelectionMode());
        clonedDc.setMessageSelectionMode(srcDc.getMessageSelectionMode());
        clonedDc.setDropCountMin(srcDc.getDropCountMin());
        clonedDc.setDropCountMax(srcDc.getDropCountMax());
        clonedDc.setCommandCountMin(srcDc.getCommandCountMin());
        clonedDc.setCommandCountMax(srcDc.getCommandCountMax());
        clonedDc.setMessageCountMin(srcDc.getMessageCountMin());
        clonedDc.setMessageCountMax(srcDc.getMessageCountMax());
        clonedDc.setDropItems(copyDeathDropItems(srcDc.getDropItems()));
        clonedDc.setDeathCommands(copyCommandActions(srcDc.getDeathCommands()));
        clonedDc.setDeathMessages(copyCitizenMessages(srcDc.getDeathMessages()));
        clonedCitizen.setDeathConfig(clonedDc);

        clonedCitizen.setFirstInteractionEnabled(citizen.isFirstInteractionEnabled());
        clonedCitizen.setFirstInteractionCommandSelectionMode(citizen.getFirstInteractionCommandSelectionMode());
        clonedCitizen.setPostFirstInteractionBehavior(citizen.getPostFirstInteractionBehavior());
        clonedCitizen.setRunNormalOnFirstInteraction(citizen.isRunNormalOnFirstInteraction());
        clonedCitizen.setFirstInteractionCommandActions(copyCommandActions(citizen.getFirstInteractionCommandActions()));
        MessagesConfig firstMessages = citizen.getFirstInteractionMessagesConfig();
        clonedCitizen.setFirstInteractionMessagesConfig(new MessagesConfig(
                copyCitizenMessages(firstMessages.getMessages()),
                firstMessages.getSelectionMode(),
                firstMessages.isEnabled()
        ));
        clonedCitizen.setPlayersWhoCompletedFirstInteraction(Collections.emptySet());

        CombatConfig clonedCombat = new CombatConfig();
        clonedCombat.copyFrom(citizen.getCombatConfig());
        clonedCitizen.setCombatConfig(clonedCombat);

        DetectionConfig clonedDetection = new DetectionConfig();
        clonedDetection.copyFrom(citizen.getDetectionConfig());
        clonedCitizen.setDetectionConfig(clonedDetection);

        PathConfig clonedPath = new PathConfig();
        clonedPath.copyFrom(citizen.getPathConfig());
        clonedCitizen.setPathConfig(clonedPath);

        clonedCitizen.setMaxHealth(citizen.getMaxHealth());
        clonedCitizen.setLeashDistance(citizen.getLeashDistance());
        clonedCitizen.setDefaultNpcAttitude(citizen.getDefaultNpcAttitude());
        clonedCitizen.setApplySeparation(citizen.isApplySeparation());

        clonedCitizen.setDropList(citizen.getDropList());
        clonedCitizen.setRunThreshold(citizen.getRunThreshold());
        clonedCitizen.setWakingIdleBehaviorComponent(citizen.getWakingIdleBehaviorComponent());
        clonedCitizen.setDayFlavorAnimation(citizen.getDayFlavorAnimation());
        clonedCitizen.setDayFlavorAnimationLengthMin(citizen.getDayFlavorAnimationLengthMin());
        clonedCitizen.setDayFlavorAnimationLengthMax(citizen.getDayFlavorAnimationLengthMax());
        clonedCitizen.setAttitudeGroup(citizen.getAttitudeGroup());
        clonedCitizen.setNameTranslationKey(citizen.getNameTranslationKey());
        clonedCitizen.setBreathesInWater(citizen.isBreathesInWater());
        clonedCitizen.setLeashMinPlayerDistance(citizen.getLeashMinPlayerDistance());
        clonedCitizen.setLeashTimerMin(citizen.getLeashTimerMin());
        clonedCitizen.setLeashTimerMax(citizen.getLeashTimerMax());
        clonedCitizen.setHardLeashDistance(citizen.getHardLeashDistance());
        clonedCitizen.setDefaultHotbarSlot(citizen.getDefaultHotbarSlot());
        clonedCitizen.setRandomIdleHotbarSlot(citizen.getRandomIdleHotbarSlot());
        clonedCitizen.setChanceToEquipFromIdleHotbarSlot(citizen.getChanceToEquipFromIdleHotbarSlot());
        clonedCitizen.setDefaultOffHandSlot(citizen.getDefaultOffHandSlot());
        clonedCitizen.setNighttimeOffhandSlot(citizen.getNighttimeOffhandSlot());
        clonedCitizen.setKnockbackScale(citizen.getKnockbackScale());
        clonedCitizen.setWeapons(new ArrayList<>(citizen.getWeapons()));
        clonedCitizen.setOffHandItems(new ArrayList<>(citizen.getOffHandItems()));
        clonedCitizen.setCombatMessageTargetGroups(new ArrayList<>(citizen.getCombatMessageTargetGroups()));
        clonedCitizen.setFlockArray(new ArrayList<>(citizen.getFlockArray()));
        clonedCitizen.setDisableDamageGroups(new ArrayList<>(citizen.getDisableDamageGroups()));

        plugin.getCitizensManager().addCitizen(clonedCitizen, true);
        playerRef.sendMessage(Message.raw("Citizen '" + citizen.getName() + "' cloned at your position!").color(Color.GREEN));
        return true;
    }

    private void removeCitizen(@Nonnull PlayerRef playerRef, @Nonnull CitizenData citizen) {
        plugin.getCitizensManager().removeCitizen(citizen.getId());
        playerRef.sendMessage(Message.raw("Citizen '" + citizen.getName() + "' removed!").color(Color.GREEN));
    }

    private void openRenameGroupGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull String oldGroupName) {
        TemplateProcessor template = createBaseTemplate()
                .setVariable("oldName", escapeHtml(oldGroupName))
                .setVariable("newName", escapeHtml(oldGroupName));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 620; anchor-height: 360;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Rename Group</p>
                            </div>
                        </div>
                        <div class="body">
                            <p class="page-description">Change group name for all citizens in this group</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                {{@formField:id=new-group-name,label=New Group Name,value={{$newName}},placeholder=Enter a new group name...}}
                                <div class="spacer-xs"></div>
                                <p class="form-hint">Current name: {{$oldName}}</p>
                            </div>
                        </div>
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 180;">Rename Group</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] newName = {oldGroupName};
        page.addEventListener("new-group-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            newName[0] = ctx.getValue("new-group-name", String.class).orElse(oldGroupName).trim();
        });

        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, event -> {
            if (newName[0].isEmpty()) {
                playerRef.sendMessage(Message.raw("Please enter a new group name.").color(Color.RED));
                return;
            }
            if (newName[0].equals(oldGroupName)) {
                playerRef.sendMessage(Message.raw("That is already the current group name.").color(Color.YELLOW));
                openCitizensGUI(playerRef, store, Tab.MANAGE);
                return;
            }

            boolean renamed = plugin.getCitizensManager().renameGroup(oldGroupName, newName[0]);
            if (!renamed) {
                playerRef.sendMessage(Message.raw("Could not rename group. Check if the target name already exists.").color(Color.RED));
                return;
            }

            playerRef.sendMessage(Message.raw("Group renamed to '" + newName[0] + "'.").color(Color.GREEN));
            openCitizensGUI(playerRef, store, Tab.MANAGE);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.MANAGE));

        page.open(store);
    }

    private void openCreateGroupGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                    @Nullable String parentGroup) {
        String normalizedParent = normalizeGroupPath(parentGroup);
        TemplateProcessor template = createBaseTemplate()
                .setVariable("parentName", normalizedParent.isEmpty() ? "Root" : escapeHtml(normalizedParent))
                .setVariable("isChildGroup", !normalizedParent.isEmpty())
                .setVariable("newGroupName", "");

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 620; anchor-height: 380;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">{{#if isChildGroup}}Create Child Group{{else}}Create Group{{/if}}</p>
                            </div>
                        </div>
                        <div class="body">
                            <p class="page-description">Parent: {{$parentName}}</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                {{@formField:id=group-name,label=Group Name,value={{$newGroupName}},placeholder=Enter group name...}}
                                <div class="spacer-xs"></div>
                                <p class="form-hint">Child groups are shown under their parent in the Manage tab.</p>
                            </div>
                        </div>
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 170;">Create Group</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] groupName = {""};
        page.addEventListener("group-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            groupName[0] = ctx.getValue("group-name", String.class).orElse("").trim();
        });

        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, (event, ctx) -> {
            groupName[0] = ctx.getValue("group-name", String.class).orElse(groupName[0]).trim();
            String fullGroupName = buildChildGroupPath(normalizedParent, groupName[0]);
            if (fullGroupName.isEmpty()) {
                playerRef.sendMessage(Message.raw("Please enter a group name.").color(Color.RED));
                return;
            }
            if (plugin.getCitizensManager().groupExists(fullGroupName)) {
                playerRef.sendMessage(Message.raw("That group already exists.").color(Color.RED));
                return;
            }

            plugin.getCitizensManager().createGroup(fullGroupName);
            playerRef.sendMessage(Message.raw("Group created: '" + fullGroupName + "'.").color(Color.GREEN));
            openCitizensGUI(playerRef, store, Tab.MANAGE, "", normalizedParent.isEmpty() ? null : normalizedParent);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.MANAGE, "", normalizedParent.isEmpty() ? null : normalizedParent));

        page.open(store);
    }

    private void openMoveGroupGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                  @Nonnull String groupName) {
        String normalizedGroup = normalizeGroupPath(groupName);
        String currentParent = getParentGroup(normalizedGroup);
        TemplateProcessor template = createBaseTemplate()
                .setVariable("groupName", escapeHtml(normalizedGroup))
                .setVariable("parentOptions", generateGroupParentOptions(currentParent, normalizedGroup));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 660; anchor-height: 400;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Move Group</p>
                            </div>
                        </div>
                        <div class="body">
                            <p class="page-description">Move {{$groupName}} under another group or back to root</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                <div class="form-group">
                                    <p class="form-label">New Parent</p>
                                    <select id="group-parent" data-hyui-showlabel="true">
                                        {{{$parentOptions}}}
                                    </select>
                                    <p class="form-hint">Choose Root to make this a top-level group.</p>
                                </div>
                            </div>
                        </div>
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 160;">Move Group</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] selectedParent = {currentParent};
        page.addEventListener("group-parent", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            selectedParent[0] = normalizeGroupPath(ctx.getValue("group-parent", String.class).orElse(""));
        });

        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, (event, ctx) -> {
            selectedParent[0] = normalizeGroupPath(ctx.getValue("group-parent", String.class).orElse(selectedParent[0]));
            boolean moved = plugin.getCitizensManager().moveGroup(normalizedGroup, selectedParent[0]);
            if (!moved) {
                playerRef.sendMessage(Message.raw("Could not move group. Check for name conflicts or invalid parent group.").color(Color.RED));
                return;
            }

            String newPath = buildChildGroupPath(selectedParent[0], getGroupLeafName(normalizedGroup));
            playerRef.sendMessage(Message.raw("Group moved to '" + newPath + "'.").color(Color.GREEN));
            openCitizensGUI(playerRef, store, Tab.MANAGE, "", selectedParent[0].isEmpty() ? null : selectedParent[0]);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.MANAGE, "", currentParent.isEmpty() ? null : currentParent));

        page.open(store);
    }

    @Nonnull
    private List<String> splitNametagLines(@Nullable String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return new ArrayList<>(List.of(""));
        }

        String normalized = rawName.replace("\\n", "\n");
        String[] split = normalized.split("\\r?\\n", -1);
        List<String> lines = new ArrayList<>();
        Collections.addAll(lines, split);
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    @Nonnull
    private String joinNametagLines(@Nonnull List<String> lines) {
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        return String.join("\\n", cleaned);
    }

    @Nonnull
    private String joinNametagLinesRaw(@Nonnull List<String> lines) {
        List<String> raw = new ArrayList<>(lines.size());
        for (String line : lines) {
            raw.add(line == null ? "" : line);
        }
        return String.join("\\n", raw);
    }

    private void openNametagLinesGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                     @Nonnull String currentName, @Nonnull Consumer<String> onDone,
                                     @Nonnull Runnable onCancel) {
        List<String> lines = splitNametagLines(currentName);
        StringBuilder linesHtml = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            linesHtml.append("""
                    <div class="form-row">
                        <div style="flex-weight: 1;">
                            <input type="text" id="line-%d" class="form-input" value="%s" placeholder="Enter line %d..." />
                        </div>
                        <div class="spacer-h-sm"></div>
                        <button id="delete-line-%d" class="secondary-button small-secondary-button" style="anchor-width: 95;">Delete</button>
                    </div>
                    <div class="spacer-xs"></div>
                    """.formatted(i, escapeHtml(lines.get(i)), i + 1, i));
        }

        TemplateProcessor template = createBaseTemplate()
                .setVariable("lineCount", lines.size());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 760; anchor-height: 720;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Nametag Lines</p>
                            </div>
                        </div>
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">
                            <p class="page-description">Build multi-line nametags with dedicated line entries ({{$lineCount}} lines)</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                {{@sectionHeader:title=Lines,description=Each non-empty line will be shown above the citizen}}
                """ + linesHtml + """
                                <div class="spacer-xs"></div>
                                <button id="add-line-btn" class="secondary-button" style="anchor-width: 170;">Add Line</button>
                            </div>
                        </div>
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 170;">Save</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] lineValues = lines.toArray(new String[0]);

        for (int i = 0; i < lines.size(); i++) {
            final int idx = i;
            page.addEventListener("line-" + i, CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                lineValues[idx] = ctx.getValue("line-" + idx, String.class).orElse("");
            });

            page.addEventListener("delete-line-" + i, CustomUIEventBindingType.Activating, event -> {
                List<String> updated = new ArrayList<>(Arrays.asList(lineValues));
                if (idx >= 0 && idx < updated.size()) {
                    updated.remove(idx);
                }
                if (updated.isEmpty()) {
                    updated.add("");
                }
                openNametagLinesGUI(playerRef, store, joinNametagLinesRaw(updated), onDone, onCancel);
            });
        }

        page.addEventListener("add-line-btn", CustomUIEventBindingType.Activating, event -> {
            List<String> updated = new ArrayList<>(Arrays.asList(lineValues));
            updated.add("");
            openNametagLinesGUI(playerRef, store, joinNametagLinesRaw(updated), onDone, onCancel);
        });

        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, event -> {
            String merged = joinNametagLines(new ArrayList<>(Arrays.asList(lineValues)));
            onDone.accept(merged);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> onCancel.run());

        page.open(store);
    }

    private void openNametagSettingsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                        @Nonnull String currentName, float currentNametagOffset,
                                        boolean currentHideNametag, boolean currentModelNametagEnabled,
                                        @Nonnull String currentNametagModelId, float currentNametagModelScale,
                                        boolean currentRotateNametagTowardsPlayer,
                                        @Nonnull Consumer<NametagSettingsState> onDone,
                                        @Nonnull Runnable onCancel) {
        List<String> lines = splitNametagLines(currentName);

        TemplateProcessor template = createBaseTemplate()
                .setVariable("hideNametag", currentHideNametag)
                .setVariable("modelEnabled", currentModelNametagEnabled)
                .setVariable("nametagOffset", currentNametagOffset)
                .setVariable("nametagModelId", currentNametagModelId)
                .setVariable("nametagModelScale", currentNametagModelScale)
                .setVariable("rotateModelTowardsPlayer", currentRotateNametagTowardsPlayer)
                .setVariable("lineCount", lines.size())
                .setVariable("nametagSummary", buildNametagSummary(
                        currentName,
                        currentNametagOffset,
                        currentHideNametag,
                        currentModelNametagEnabled,
                        currentNametagModelId,
                        currentNametagModelScale,
                        currentRotateNametagTowardsPlayer
                ))
                .setVariable("modelOptions", generateModelDropdownOptions(currentNametagModelId, true));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 760; anchor-height: 760;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Nametag Settings</p>
                            </div>
                        </div>
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">
                            <p class="page-description">{{$nametagSummary}}</p>
                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Display Mode,description=Choose between text lines or a model nametag}}
                                <div class="toggle-group">
                                    <button id="nametag-mode-text" class="secondary-button toggle-btn{{#if !modelEnabled}} toggle-active{{/if}}" style="anchor-width: 320; anchor-height: 48;">Text Nametag</button>
                                    <button id="nametag-mode-model" class="secondary-button toggle-btn{{#if modelEnabled}} toggle-active{{/if}}" style="anchor-width: 320; anchor-height: 48;">Model Nametag</button>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=General,description=Shared nametag options}}
                                <div class="form-row" style="horizontal-align: center;">
                                    <div class="form-col-fixed" style="anchor-width: 180;">
                                        <div class="form-group">
                                            <p class="form-label">Nametag Offset</p>
                                            <input type="number" id="nametag-offset" class="form-input"
                                                   value="{{$nametagOffset}}"
                                                   placeholder="0.0"
                                                   min="-500" max="500" step="0.25"
                                                   data-hyui-max-decimal-places="2" />
                                        </div>
                                    </div>
                                </div>

                                <div class="spacer-sm"></div>

                                <div class="checkbox-row">
                                    <input type="checkbox" id="hide-nametag-check" {{#if hideNametag}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Hide Nametag</p>
                                        <p class="checkbox-description">Disable any text or model above the citizen</p>
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            {{#if modelEnabled}}
                            <div class="section">
                                {{@sectionHeader:title=Model,description=Pick the model used instead of text}}
                                <div class="card">
                                    <div class="card-body">
                                        <div class="form-group">
                                            <p class="form-label" style="text-align: center;">Select Model</p>
                                            <select id="nametag-model-dropdown" value="{{$nametagModelId}}" data-hyui-showlabel="true">
                                                {{$modelOptions}}
                                            </select>
                                            <p class="form-hint" style="text-align: center;">The dropdown includes every loaded model asset</p>
                                        </div>

                                        <div class="spacer-sm"></div>
                                        <div class="divider"></div>
                                        <div class="spacer-sm"></div>

                                        <div class="form-group">
                                            <p class="form-label" style="text-align: center;">Or Enter A Model ID</p>
                                            <input type="text" id="nametag-model-id" class="form-input" value="{{$nametagModelId}}"
                                                   placeholder="e.g., Sheep, PlayerTestModel_V" maxlength="96" style="anchor-width: 260;" />
                                        </div>

                                        <div class="spacer-sm"></div>

                                        <div class="form-row" style="horizontal-align: center;">
                                            <div style="anchor-width: 220;">
                                                {{@numberField:id=nametag-model-scale,label=Model Scale,value={{$nametagModelScale}},placeholder=1.0,min=0.01,max=100,step=0.1,decimals=2}}
                                            </div>
                                        </div>

                                        <div class="spacer-sm"></div>

                                        <div class="checkbox-row">
                                            <input type="checkbox" id="rotate-nametag-towards-player" {{#if rotateModelTowardsPlayer}}checked{{/if}} />
                                            <div style="layout: top; flex-weight: 0; text-align: center;">
                                                <p class="checkbox-label">Rotate Model Towards Players</p>
                                                <p class="checkbox-description">Rotates the model towards the player</p>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            {{else}}
                            <div class="section">
                                {{@sectionHeader:title=Text Lines,description=Manage the lines shown when using text nametags}}
                                <div class="card">
                                    <div class="card-body" style="layout: top; horizontal-align: center;">
                                        <p class="form-hint" style="text-align: center;">{{$lineCount}} configured line(s)</p>
                                        <div class="spacer-xs"></div>
                                        <button id="edit-nametag-lines-btn" class="secondary-button" style="anchor-width: 220;">Edit Text Lines</button>
                                    </div>
                                </div>
                            </div>
                            {{/if}}

                            <div class="spacer-lg"></div>
                        </div>
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="done-btn" class="secondary-button btn-wide">Done</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] editedName = {currentName};
        final float[] nametagOffset = {currentNametagOffset};
        final boolean[] hideNametag = {currentHideNametag};
        final boolean[] modelEnabled = {currentModelNametagEnabled};
        final String[] nametagModelId = {currentNametagModelId};
        final float[] nametagModelScale = {currentNametagModelScale};
        final boolean[] rotateModelTowardsPlayer = {currentRotateNametagTowardsPlayer};

        page.addEventListener("nametag-mode-text", CustomUIEventBindingType.Activating, event ->
                openNametagSettingsGUI(playerRef, store, editedName[0], nametagOffset[0], hideNametag[0],
                        false, nametagModelId[0], nametagModelScale[0], rotateModelTowardsPlayer[0], onDone, onCancel));

        page.addEventListener("nametag-mode-model", CustomUIEventBindingType.Activating, event ->
                openNametagSettingsGUI(playerRef, store, editedName[0], nametagOffset[0], hideNametag[0],
                        true, nametagModelId[0], nametagModelScale[0], rotateModelTowardsPlayer[0], onDone, onCancel));

        page.addEventListener("nametag-offset", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("nametag-offset", Double.class).ifPresent(val -> nametagOffset[0] = val.floatValue());
            if (nametagOffset[0] == 0.0f) {
                ctx.getValue("nametag-offset", String.class).ifPresent(val -> {
                    try {
                        nametagOffset[0] = Float.parseFloat(val);
                    } catch (NumberFormatException ignored) {
                    }
                });
            }
        });

        page.addEventListener("hide-nametag-check", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                hideNametag[0] = ctx.getValue("hide-nametag-check", Boolean.class).orElse(false));

        if (currentModelNametagEnabled) {
            page.addEventListener("nametag-model-id", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                    nametagModelId[0] = ctx.getValue("nametag-model-id", String.class).orElse(""));

            page.addEventListener("nametag-model-dropdown", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                    ctx.getValue("nametag-model-dropdown", String.class).ifPresent(val -> nametagModelId[0] = val));

            try {
                page.addEventListener("nametag-model-scale", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                        ctx.getValue("nametag-model-scale", Double.class)
                                .ifPresent(val -> nametagModelScale[0] = Math.max(0.01f, val.floatValue())));
            } catch (IllegalArgumentException ignored) {
            }

            page.addEventListener("rotate-nametag-towards-player", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                    rotateModelTowardsPlayer[0] = ctx.getValue("rotate-nametag-towards-player", Boolean.class).orElse(true));
        } else {
            page.addEventListener("edit-nametag-lines-btn", CustomUIEventBindingType.Activating, event ->
                    openNametagLinesGUI(
                            playerRef,
                            store,
                            editedName[0],
                            updatedName -> openNametagSettingsGUI(playerRef, store, updatedName, nametagOffset[0], hideNametag[0],
                                    modelEnabled[0], nametagModelId[0], nametagModelScale[0], rotateModelTowardsPlayer[0], onDone, onCancel),
                            () -> openNametagSettingsGUI(playerRef, store, editedName[0], nametagOffset[0], hideNametag[0],
                                    modelEnabled[0], nametagModelId[0], nametagModelScale[0], rotateModelTowardsPlayer[0], onDone, onCancel)
                    ));
        }

        page.addEventListener("done-btn", CustomUIEventBindingType.Activating, event -> {
            String trimmedModelId = nametagModelId[0].trim();
            if (modelEnabled[0]) {
                if (trimmedModelId.isEmpty()) {
                    playerRef.sendMessage(Message.raw("Please select or enter a nametag model ID.").color(Color.RED));
                    return;
                }

                if (ModelAsset.getAssetMap().getAsset(trimmedModelId) == null) {
                    playerRef.sendMessage(Message.raw("That nametag model could not be found.").color(Color.RED));
                    return;
                }
            }

            onDone.accept(new NametagSettingsState(
                    editedName[0],
                    nametagOffset[0],
                    hideNametag[0],
                    modelEnabled[0],
                    trimmedModelId,
                    Math.max(0.01f, nametagModelScale[0]),
                    rotateModelTowardsPlayer[0]
            ));
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> onCancel.run());

        page.open(store);
    }

    private void setupCreateCitizenListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                              boolean initialIsPlayerModel, String initialName, float initialNametagOffset,
                                              boolean initialHideNametag, boolean initialHideNpc,
                                              boolean initialMapMarkerEnabled, String initialMapMarkerType, String initialMapMarkerName,
                                              boolean initialModelNametagEnabled, String initialNametagModelId,
                                              float initialNametagModelScale, boolean initialRotateNametagTowardsPlayer,
                                             String initialModelId, float initialScale, String initialPermission, String initialPermMessage, boolean initialUseLiveSkin,
                                             String initialSkinUsername, boolean initialRotateTowardsPlayer,
                                             String initialGroup, float initialLookAtDistance) {
        final List<CommandAction> tempActions = new ArrayList<>();
        final String[] currentName = {initialName};
        final float[] nametagOffset = {initialNametagOffset};
        final boolean[] hideNametag = {initialHideNametag};
        final boolean[] hideNpc = {initialHideNpc};
        final boolean[] mapMarkerEnabled = {initialMapMarkerEnabled};
        final String[] mapMarkerType = {CitizenData.normalizeMapMarkerType(initialMapMarkerType)};
        final String[] mapMarkerName = {initialMapMarkerName != null ? initialMapMarkerName : ""};
        final boolean[] modelNametagEnabled = {initialModelNametagEnabled};
        final String[] currentNametagModelId = {initialNametagModelId};
        final float[] currentNametagModelScale = {initialNametagModelScale};
        final boolean[] rotateNametagTowardsPlayer = {initialRotateNametagTowardsPlayer};
        final String[] currentModelId = {initialModelId.isEmpty() ? "PlayerTestModel_V" : initialModelId};
        final float[] currentScale = {Math.max(0.01f, Math.min(100.0f, initialScale))};
        final String[] currentPermission = {initialPermission};
        final String[] currentPermMessage = {initialPermMessage};
        final boolean[] isPlayerModel = {initialIsPlayerModel};
        final boolean[] useLiveSkin = {initialUseLiveSkin};
        final String[] skinUsername = {initialSkinUsername};
        final PlayerSkin[] cachedSkin = {null};
        final boolean[] rotateTowardsPlayer = {initialRotateTowardsPlayer};
        final String[] currentGroup = {initialGroup};
        final float[] lookAtDistance = {initialLookAtDistance};

        page.addEventListener("citizen-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentName[0] = ctx.getValue("citizen-name", String.class).orElse("");
        });

        if (!initialIsPlayerModel) {
            page.addEventListener("citizen-model-id", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                currentModelId[0] = ctx.getValue("citizen-model-id", String.class).orElse("");
            });

            page.addEventListener("entity-dropdown", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("entity-dropdown", String.class).ifPresent(val -> {
                    currentModelId[0] = val;
                });
            });
        }

        if (initialIsPlayerModel) {
            page.addEventListener("skin-username", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                skinUsername[0] = ctx.getValue("skin-username", String.class).orElse("");
            });

            page.addEventListener("live-skin-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                useLiveSkin[0] = ctx.getValue("live-skin-check", Boolean.class).orElse(false);
                if (CitizenData.isGeneratedSkinUsername(skinUsername[0])) {
                    useLiveSkin[0] = false;
                }
            });

            page.addEventListener("get-player-skin-btn", CustomUIEventBindingType.Activating, event -> {
                skinUsername[0] = playerRef.getUsername();
                cachedSkin[0] = null;
                playerRef.sendMessage(Message.raw("Using your skin for this citizen!").color(Color.GREEN));
            });

            page.addEventListener("random-skin-btn", CustomUIEventBindingType.Activating, event -> {
                try {
                    PlayerSkin randomSkin = CosmeticsModule.get().generateRandomSkin(RandomUtil.getSecureRandom());
                    cachedSkin[0] = randomSkin;
                    skinUsername[0] = "random_" + UUID.randomUUID().toString().substring(0, 8);
                    useLiveSkin[0] = false;
                    playerRef.sendMessage(Message.raw("Random skin generated successfully!").color(Color.GREEN));
                } catch (Exception e) {
                    playerRef.sendMessage(Message.raw("Failed to generate random skin: " + e.getMessage()).color(Color.RED));
                }
            });
        }

        page.addEventListener("rotate-towards-player", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            rotateTowardsPlayer[0] = ctx.getValue("rotate-towards-player", Boolean.class).orElse(false);
        });

        try {
            page.addEventListener("look-at-distance", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("look-at-distance", Double.class).ifPresent(v -> lookAtDistance[0] = Math.max(0.0f, v.floatValue()));
            });
        } catch (IllegalArgumentException ignored) {
            // Defensive guard in case an older cached UI layout without this field is still open.
        }

        page.addEventListener("nametag-settings-btn", CustomUIEventBindingType.Activating, event ->
                openNametagSettingsGUI(
                        playerRef,
                        store,
                        currentName[0],
                        nametagOffset[0],
                        hideNametag[0],
                        modelNametagEnabled[0],
                        currentNametagModelId[0],
                        currentNametagModelScale[0],
                        rotateNametagTowardsPlayer[0],
                        updated -> openCreateCitizenGUI(
                                playerRef, store, isPlayerModel[0], updated.name(), updated.offset(), updated.hideNametag(), hideNpc[0],
                                mapMarkerEnabled[0], mapMarkerType[0], mapMarkerName[0],
                                updated.modelEnabled(), updated.modelId(), updated.modelScale(), updated.rotateModelTowardsPlayer(),
                                currentModelId[0], currentScale[0], currentPermission[0], currentPermMessage[0], useLiveSkin[0], true,
                                skinUsername[0], rotateTowardsPlayer[0], currentGroup[0], lookAtDistance[0]
                        ),
                        () -> openCreateCitizenGUI(
                                playerRef, store, isPlayerModel[0], currentName[0], nametagOffset[0], hideNametag[0], hideNpc[0],
                                mapMarkerEnabled[0], mapMarkerType[0], mapMarkerName[0],
                                modelNametagEnabled[0], currentNametagModelId[0], currentNametagModelScale[0], rotateNametagTowardsPlayer[0],
                                currentModelId[0], currentScale[0], currentPermission[0], currentPermMessage[0], useLiveSkin[0], true,
                                skinUsername[0], rotateTowardsPlayer[0], currentGroup[0], lookAtDistance[0]
                        )
                ));

        page.addEventListener("hide-npc-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            hideNpc[0] = ctx.getValue("hide-npc-check", Boolean.class).orElse(false);
        });

        page.addEventListener("map-marker-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            boolean enabled = ctx.getValue("map-marker-check", Boolean.class).orElse(false);
            if (mapMarkerEnabled[0]) {
                mapMarkerName[0] = ctx.getValue("map-marker-name", String.class).orElse(mapMarkerName[0]);
            }
            if (enabled == mapMarkerEnabled[0]) {
                return;
            }

            mapMarkerEnabled[0] = enabled;
            openCreateCitizenGUI(playerRef, store, isPlayerModel[0], currentName[0], nametagOffset[0], hideNametag[0], hideNpc[0],
                    mapMarkerEnabled[0], mapMarkerType[0], mapMarkerName[0],
                    modelNametagEnabled[0], currentNametagModelId[0], currentNametagModelScale[0], rotateNametagTowardsPlayer[0],
                    currentModelId[0], currentScale[0], currentPermission[0], currentPermMessage[0], useLiveSkin[0], true,
                    skinUsername[0], rotateTowardsPlayer[0], currentGroup[0], lookAtDistance[0]);
        });

        if (mapMarkerEnabled[0]) {
            page.addEventListener("map-marker-type", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                mapMarkerType[0] = CitizenData.normalizeMapMarkerType(
                        ctx.getValue("map-marker-type", String.class).orElse(CitizenData.MAP_MARKER_TYPE_PIN));
            });
            page.addEventListener("map-marker-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                mapMarkerName[0] = ctx.getValue("map-marker-name", String.class).orElse("");
            });
        }

        page.addEventListener("citizen-scale", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("citizen-scale", Double.class)
                    .ifPresent(val -> currentScale[0] = Math.max(0.01f, Math.min(100.0f, val.floatValue())));

            if (currentScale[0] == 1.0f) {
                ctx.getValue("citizen-scale", String.class)
                        .ifPresent(val -> {
                            try {
                                currentScale[0] = Math.max(0.01f, Math.min(100.0f, Float.parseFloat(val)));
                            } catch (NumberFormatException e) {
                            }
                        });
            }
        });

        page.addEventListener("citizen-permission", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentPermission[0] = ctx.getValue("citizen-permission", String.class).orElse("");
        });

        page.addEventListener("citizen-perm-message", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentPermMessage[0] = ctx.getValue("citizen-perm-message", String.class).orElse("");
        });

        page.addEventListener("group-dropdown", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            String selectedGroup = ctx.getValue("group-dropdown", String.class).orElse("");
            // Only update if not the create new option
            if (!"__CREATE_NEW__".equals(selectedGroup)) {
                currentGroup[0] = selectedGroup;
            }
        });

        page.addEventListener("group-custom", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            String customGroup = ctx.getValue("group-custom", String.class).orElse("").trim();
            currentGroup[0] = customGroup;
        });

        page.addEventListener("type-player", CustomUIEventBindingType.Activating, (event, ctx) -> {
            if (mapMarkerEnabled[0]) {
                mapMarkerType[0] = CitizenData.normalizeMapMarkerType(
                        ctx.getValue("map-marker-type", String.class).orElse(mapMarkerType[0]));
                mapMarkerName[0] = ctx.getValue("map-marker-name", String.class).orElse(mapMarkerName[0]);
            }
            openCreateCitizenGUI(playerRef, store, true, currentName[0], nametagOffset[0], hideNametag[0], hideNpc[0],
                    mapMarkerEnabled[0], mapMarkerType[0], mapMarkerName[0],
                    modelNametagEnabled[0], currentNametagModelId[0], currentNametagModelScale[0], rotateNametagTowardsPlayer[0], currentModelId[0],
                    currentScale[0], currentPermission[0], currentPermMessage[0], useLiveSkin[0], true,
                    skinUsername[0], rotateTowardsPlayer[0], currentGroup[0], lookAtDistance[0]);
        });

        page.addEventListener("type-entity", CustomUIEventBindingType.Activating, (event, ctx) -> {
            if (mapMarkerEnabled[0]) {
                mapMarkerType[0] = CitizenData.normalizeMapMarkerType(
                        ctx.getValue("map-marker-type", String.class).orElse(mapMarkerType[0]));
                mapMarkerName[0] = ctx.getValue("map-marker-name", String.class).orElse(mapMarkerName[0]);
            }
            openCreateCitizenGUI(playerRef, store, false, currentName[0], nametagOffset[0], hideNametag[0], hideNpc[0],
                    mapMarkerEnabled[0], mapMarkerType[0], mapMarkerName[0],
                    modelNametagEnabled[0], currentNametagModelId[0], currentNametagModelScale[0], rotateNametagTowardsPlayer[0], currentModelId[0],
                    currentScale[0], currentPermission[0], currentPermMessage[0], useLiveSkin[0], true,
                    skinUsername[0], rotateTowardsPlayer[0], currentGroup[0], lookAtDistance[0]);
        });

        page.addEventListener("create-btn", CustomUIEventBindingType.Activating, (event, ctx) -> {
            mapMarkerEnabled[0] = ctx.getValue("map-marker-check", Boolean.class).orElse(mapMarkerEnabled[0]);
            if (mapMarkerEnabled[0]) {
                mapMarkerType[0] = CitizenData.normalizeMapMarkerType(
                        ctx.getValue("map-marker-type", String.class).orElse(mapMarkerType[0]));
                mapMarkerName[0] = ctx.getValue("map-marker-name", String.class).orElse(mapMarkerName[0]);
            }

            String name = currentName[0].trim();

            if (name.isEmpty()) {
                playerRef.sendMessage(Message.raw("Please enter a citizen name!").color(Color.RED));
                return;
            }

            String modelId;
            if (isPlayerModel[0]) {
                modelId = "Player";
            } else {
                modelId = currentModelId[0].trim();
                if (modelId.isEmpty()) {
                    playerRef.sendMessage(Message.raw("Please select or enter a model ID!").color(Color.RED));
                    return;
                }
            }

            Vector3d position = new Vector3d(playerRef.getTransform().getPosition());
            Vector3f rotation = new Vector3f(playerRef.getTransform().getRotation());

            UUID worldUUID = playerRef.getWorldUuid();
            if (worldUUID == null) {
                playerRef.sendMessage(Message.raw("Failed to create citizen!").color(Color.RED));
                return;
            }

            if (skinUsername[0].isEmpty()) {
                skinUsername[0] = playerRef.getUsername();
            }

            CitizenData citizen = new CitizenData(
                    UUID.randomUUID().toString(),
                    name,
                    modelId,
                    worldUUID,
                    position,
                    rotation,
                    currentScale[0],
                    null,
                    new ArrayList<>(),
                    currentPermission[0].trim(),
                    currentPermMessage[0].trim(),
                    new ArrayList<>(tempActions),
                    isPlayerModel[0],
                    useLiveSkin[0],
                    skinUsername[0].trim(),
                    null,
                    0L,
                    rotateTowardsPlayer[0]
            );

            citizen.setNametagOffset(nametagOffset[0]);
            citizen.setHideNametag(hideNametag[0]);
            citizen.setModelNametagEnabled(modelNametagEnabled[0]);
            citizen.setNametagModelId(currentNametagModelId[0].trim());
            citizen.setNametagModelScale(currentNametagModelScale[0]);
            citizen.setRotateNametagTowardsPlayer(rotateNametagTowardsPlayer[0]);
            citizen.setHideNpc(hideNpc[0]);
            citizen.setMapMarkerEnabled(mapMarkerEnabled[0]);
            citizen.setMapMarkerType(mapMarkerType[0]);
            citizen.setMapMarkerName(mapMarkerName[0]);
            citizen.setGroup(currentGroup[0]);
            citizen.setLookAtDistance(lookAtDistance[0]);
            plugin.getCitizensManager().autoResolveAttackType(citizen);

            if (isPlayerModel[0]) {
                if (cachedSkin[0] != null) {
                    // Use the pre-generated random skin
                    citizen.setCachedSkin(cachedSkin[0]);
                    citizen.setLastSkinUpdate(System.currentTimeMillis());
                    plugin.getCitizensManager().addCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Citizen \"" + name + "\" created at your position!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                } else if (skinUsername[0].trim().isEmpty()) {
                    plugin.getCitizensManager().updateCitizenSkinFromPlayer(citizen, playerRef, false);
                    plugin.getCitizensManager().addCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Citizen \"" + name + "\" created at your position!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                } else {
                    playerRef.sendMessage(Message.raw("Fetching skin for \"" + skinUsername[0] + "\"...").color(Color.YELLOW));
                    com.electro.hycitizens.util.SkinUtilities.getSkin(skinUsername[0].trim()).thenAccept(skin -> {
                        if (skin != null) {
                            citizen.setCachedSkin(skin);
                            citizen.setLastSkinUpdate(System.currentTimeMillis());
                        }
                        plugin.getCitizensManager().addCitizen(citizen, true);
                        playerRef.sendMessage(Message.raw("Citizen \"" + name + "\" created at your position!").color(Color.GREEN));
                        openCitizensGUI(playerRef, store, Tab.MANAGE);
                    });
                }
            } else {
                plugin.getCitizensManager().addCitizen(citizen, true);
                playerRef.sendMessage(Message.raw("Citizen \"" + name + "\" created at your position!").color(Color.GREEN));
                openCitizensGUI(playerRef, store, Tab.MANAGE);
            }
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.CREATE));
    }

    private void setupEditCitizenListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                           CitizenData citizen, boolean initialMapMarkerEnabled, String initialMapMarkerType,
                                           String initialMapMarkerName) {
        final String[] currentName = {citizen.getName()};
        final float[] nametagOffset = {citizen.getNametagOffset()};
        final boolean[] hideNametag = {citizen.isHideNametag()};
        final boolean[] hideNpc = {citizen.isHideNpc()};
        final boolean[] mapMarkerEnabled = {initialMapMarkerEnabled};
        final String[] mapMarkerType = {CitizenData.normalizeMapMarkerType(initialMapMarkerType)};
        final String[] mapMarkerName = {initialMapMarkerName != null ? initialMapMarkerName : ""};
        final boolean[] modelNametagEnabled = {citizen.isModelNametagEnabled()};
        final String[] currentNametagModelId = {citizen.getNametagModelId()};
        final float[] currentNametagModelScale = {citizen.getNametagModelScale()};
        final boolean[] rotateNametagTowardsPlayer = {citizen.isRotateNametagTowardsPlayer()};
        final String[] currentModelId = {citizen.getModelId()};
        final float[] currentScale = {Math.max(0.01f, Math.min(100.0f, citizen.getScale()))};
        final String[] currentPermission = {citizen.getRequiredPermission()};
        final String[] currentPermMessage = {citizen.getNoPermissionMessage()};
        final boolean[] isPlayerModel = {citizen.isPlayerModel()};
        final boolean[] useLiveSkin = {citizen.isUseLiveSkin()};
        final boolean[] rotateTowardsPlayer = {citizen.getRotateTowardsPlayer()};
        final float[] lookAtDistance = {citizen.getLookAtDistance()};
        final String[] skinUsername = {citizen.getSkinUsername()};
        final PlayerSkin[] cachedSkin = {citizen.getCachedSkin()};
        final String[] currentGroup = {citizen.getGroup()};

        page.addEventListener("citizen-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentName[0] = ctx.getValue("citizen-name", String.class).orElse("");
        });

        page.addEventListener("group-dropdown", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            String selectedGroup = ctx.getValue("group-dropdown", String.class).orElse("");
            // Only update if not the create new option
            if (!"__CREATE_NEW__".equals(selectedGroup)) {
                currentGroup[0] = selectedGroup;
            }
        });

        page.addEventListener("group-custom", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            String customGroup = ctx.getValue("group-custom", String.class).orElse("").trim();
            currentGroup[0] = customGroup;
        });

        if (!citizen.isPlayerModel()) {
            page.addEventListener("citizen-model-id", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                currentModelId[0] = ctx.getValue("citizen-model-id", String.class).orElse("");
            });

            page.addEventListener("entity-dropdown", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("entity-dropdown", String.class).ifPresent(val -> {
                    currentModelId[0] = val;
                });
            });
        }

        if (citizen.isPlayerModel()) {
            page.addEventListener("skin-username", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                skinUsername[0] = ctx.getValue("skin-username", String.class).orElse("");
            });

            page.addEventListener("live-skin-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                useLiveSkin[0] = ctx.getValue("live-skin-check", Boolean.class).orElse(false);
                if (CitizenData.isGeneratedSkinUsername(skinUsername[0])) {
                    useLiveSkin[0] = false;
                }
            });

            page.addEventListener("get-player-skin-btn", CustomUIEventBindingType.Activating, event -> {
                skinUsername[0] = playerRef.getUsername();
                cachedSkin[0] = null;
                playerRef.sendMessage(Message.raw("Using your skin for this citizen!").color(Color.GREEN));
            });

            page.addEventListener("random-skin-btn", CustomUIEventBindingType.Activating, event -> {
                try {
                    PlayerSkin randomSkin = CosmeticsModule.get().generateRandomSkin(RandomUtil.getSecureRandom());
                    cachedSkin[0] = randomSkin;
                    skinUsername[0] = "random_" + UUID.randomUUID().toString().substring(0, 8);
                    useLiveSkin[0] = false;
                    playerRef.sendMessage(Message.raw("Random skin generated successfully!").color(Color.GREEN));
                } catch (Exception e) {
                    playerRef.sendMessage(Message.raw("Failed to generate random skin: " + e.getMessage()).color(Color.RED));
                }
            });

            page.addEventListener("customize-skin-btn", CustomUIEventBindingType.Activating, event -> {
                if (citizen.getCachedSkin() == null) {
                    citizen.setCachedSkin(com.electro.hycitizens.util.SkinUtilities.createDefaultSkin());
                }
                plugin.getSkinCustomizerUI().openSkinCustomizerGUI(playerRef, store, citizen);
            });
        }

        page.addEventListener("rotate-towards-player", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            rotateTowardsPlayer[0] = ctx.getValue("rotate-towards-player", Boolean.class).orElse(false);
        });

        try {
            page.addEventListener("look-at-distance", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("look-at-distance", Double.class).ifPresent(v -> lookAtDistance[0] = Math.max(0.0f, v.floatValue()));
            });
        } catch (IllegalArgumentException ignored) {
            // Defensive guard in case an older cached UI layout without this field is still open.
        }

        page.addEventListener("nametag-settings-btn", CustomUIEventBindingType.Activating, event ->
                openNametagSettingsGUI(
                        playerRef,
                        store,
                        currentName[0],
                        nametagOffset[0],
                        hideNametag[0],
                        modelNametagEnabled[0],
                        currentNametagModelId[0],
                        currentNametagModelScale[0],
                        rotateNametagTowardsPlayer[0],
                        updated -> {
                            currentName[0] = updated.name();
                            nametagOffset[0] = updated.offset();
                            hideNametag[0] = updated.hideNametag();
                            modelNametagEnabled[0] = updated.modelEnabled();
                            currentNametagModelId[0] = updated.modelId();
                            currentNametagModelScale[0] = updated.modelScale();
                            rotateNametagTowardsPlayer[0] = updated.rotateModelTowardsPlayer();
                            citizen.setName(currentName[0]);
                            citizen.setNametagOffset(nametagOffset[0]);
                            citizen.setHideNametag(hideNametag[0]);
                            citizen.setModelNametagEnabled(modelNametagEnabled[0]);
                            citizen.setNametagModelId(currentNametagModelId[0]);
                            citizen.setNametagModelScale(currentNametagModelScale[0]);
                            citizen.setRotateNametagTowardsPlayer(rotateNametagTowardsPlayer[0]);
                            openEditCitizenGUI(playerRef, store, citizen, mapMarkerEnabled[0], mapMarkerType[0], mapMarkerName[0]);
                        },
                        () -> openEditCitizenGUI(playerRef, store, citizen, mapMarkerEnabled[0], mapMarkerType[0], mapMarkerName[0])
                ));

        page.addEventListener("hide-npc-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            hideNpc[0] = ctx.getValue("hide-npc-check", Boolean.class).orElse(false);
        });

        page.addEventListener("map-marker-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            boolean enabled = ctx.getValue("map-marker-check", Boolean.class).orElse(false);
            if (mapMarkerEnabled[0]) {
                mapMarkerName[0] = ctx.getValue("map-marker-name", String.class).orElse(mapMarkerName[0]);
            }
            if (enabled == mapMarkerEnabled[0]) {
                return;
            }

            mapMarkerEnabled[0] = enabled;
            openEditCitizenGUI(playerRef, store, citizen, mapMarkerEnabled[0], mapMarkerType[0], mapMarkerName[0]);
        });

        if (mapMarkerEnabled[0]) {
            page.addEventListener("map-marker-type", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                mapMarkerType[0] = CitizenData.normalizeMapMarkerType(
                        ctx.getValue("map-marker-type", String.class).orElse(CitizenData.MAP_MARKER_TYPE_PIN));
            });
            page.addEventListener("map-marker-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                mapMarkerName[0] = ctx.getValue("map-marker-name", String.class).orElse("");
            });
        }

        page.addEventListener("citizen-scale", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("citizen-scale", Double.class)
                    .ifPresent(val -> currentScale[0] = Math.max(0.01f, Math.min(100.0f, val.floatValue())));

            if (currentScale[0] == 1.0f) {
                ctx.getValue("citizen-scale", String.class)
                        .ifPresent(val -> {
                            try {
                                currentScale[0] = Math.max(0.01f, Math.min(100.0f, Float.parseFloat(val)));
                            } catch (NumberFormatException e) {
                            }
                        });
            }
        });

        page.addEventListener("citizen-permission", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentPermission[0] = ctx.getValue("citizen-permission", String.class).orElse("");
        });

        page.addEventListener("citizen-perm-message", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentPermMessage[0] = ctx.getValue("citizen-perm-message", String.class).orElse("");
        });

        page.addEventListener("type-player", CustomUIEventBindingType.Activating, (event, ctx) -> {
            if (mapMarkerEnabled[0]) {
                mapMarkerType[0] = CitizenData.normalizeMapMarkerType(
                        ctx.getValue("map-marker-type", String.class).orElse(mapMarkerType[0]));
                mapMarkerName[0] = ctx.getValue("map-marker-name", String.class).orElse(mapMarkerName[0]);
            }
            isPlayerModel[0] = true;
            citizen.setPlayerModel(true);
            openEditCitizenGUI(playerRef, store, citizen, mapMarkerEnabled[0], mapMarkerType[0], mapMarkerName[0]);
        });

        page.addEventListener("type-entity", CustomUIEventBindingType.Activating, (event, ctx) -> {
            if (mapMarkerEnabled[0]) {
                mapMarkerType[0] = CitizenData.normalizeMapMarkerType(
                        ctx.getValue("map-marker-type", String.class).orElse(mapMarkerType[0]));
                mapMarkerName[0] = ctx.getValue("map-marker-name", String.class).orElse(mapMarkerName[0]);
            }
            isPlayerModel[0] = false;
            citizen.setPlayerModel(false);
            openEditCitizenGUI(playerRef, store, citizen, mapMarkerEnabled[0], mapMarkerType[0], mapMarkerName[0]);
        });

        page.addEventListener("edit-tp-btn", CustomUIEventBindingType.Activating, event ->
                teleportPlayerToCitizen(playerRef, citizen));

        page.addEventListener("edit-clone-btn", CustomUIEventBindingType.Activating, event ->
                cloneCitizenToPlayer(playerRef, citizen));

        page.addEventListener("edit-remove-btn", CustomUIEventBindingType.Activating, event -> {
            removeCitizen(playerRef, citizen);
            openCitizensGUI(playerRef, store, Tab.MANAGE);
        });

        page.addEventListener("edit-commands-btn", CustomUIEventBindingType.Activating, event -> {
            openCommandActionsGUI(playerRef, store, citizen.getId(),
                    new ArrayList<>(citizen.getCommandActions()), false);
        });

        page.addEventListener("change-position-btn", CustomUIEventBindingType.Activating, event -> {
            Vector3d newPosition = new Vector3d(playerRef.getTransform().getPosition());
            Vector3f newRotation = new Vector3f(playerRef.getTransform().getRotation());

            UUID worldUUID = playerRef.getWorldUuid();
            if (worldUUID == null) {
                playerRef.sendMessage(Message.raw("Failed to change citizen position!").color(Color.RED));
                return;
            }

            citizen.setPosition(newPosition);
            citizen.setRotation(newRotation);
            citizen.setWorldUUID(worldUUID);
            plugin.getCitizensManager().updateCitizen(citizen, true);

            playerRef.sendMessage(Message.raw("Position updated to your current location!").color(Color.GREEN));
        });

        page.addEventListener("set-items-btn", CustomUIEventBindingType.Activating, event -> {
            World world = Universe.get().getWorld(playerRef.getWorldUuid());
            if (world == null) {
                playerRef.sendMessage(Message.raw("Failed to set the citizen's items!").color(Color.RED));
                return;
            }

            world.execute(() -> {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null) {
                    return;
                }

                Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }

                if (player.getInventory().getItemInHand() == null) {
                    citizen.setNpcHand(null);
                } else {
                    citizen.setNpcHand(player.getInventory().getItemInHand().getItemId());
                }

                if (player.getInventory().getUtilityItem() == null) {
                    citizen.setNpcOffHand(null);
                } else {
                    citizen.setNpcOffHand(player.getInventory().getUtilityItem().getItemId());
                }

                if (player.getInventory().getArmor().getItemStack((short) 0) == null) {
                    citizen.setNpcHelmet(null);
                } else {
                    citizen.setNpcHelmet(player.getInventory().getArmor().getItemStack((short) 0).getItemId());
                }

                if (player.getInventory().getArmor().getItemStack((short) 1) == null) {
                    citizen.setNpcChest(null);
                } else {
                    citizen.setNpcChest(player.getInventory().getArmor().getItemStack((short) 1).getItemId());
                }

                if (player.getInventory().getArmor().getItemStack((short) 2) == null) {
                    citizen.setNpcGloves(null);
                } else {
                    citizen.setNpcGloves(player.getInventory().getArmor().getItemStack((short) 2).getItemId());
                }

                if (player.getInventory().getArmor().getItemStack((short) 3) == null) {
                    citizen.setNpcLeggings(null);
                } else {
                    citizen.setNpcLeggings(player.getInventory().getArmor().getItemStack((short) 3).getItemId());
                }

                plugin.getCitizensManager().saveCitizen(citizen);
                plugin.getCitizensManager().updateCitizenNPCItems(citizen);
            });

            playerRef.sendMessage(Message.raw("Citizen's equipment updated to match yours!").color(Color.GREEN));
        });

        page.addEventListener("behaviors-btn", CustomUIEventBindingType.Activating, event -> {
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("schedule-btn", CustomUIEventBindingType.Activating, event -> {
            openScheduleGUI(playerRef, store, citizen);
        });

        page.addEventListener("messages-btn", CustomUIEventBindingType.Activating, event -> {
            openMessagesGUI(playerRef, store, citizen);
        });

        page.addEventListener("first-interaction-btn", CustomUIEventBindingType.Activating, event -> {
            openFirstInteractionConfigGUI(playerRef, store, citizen);
        });

        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, (event, ctx) -> {
            mapMarkerEnabled[0] = ctx.getValue("map-marker-check", Boolean.class).orElse(mapMarkerEnabled[0]);
            if (mapMarkerEnabled[0]) {
                mapMarkerType[0] = CitizenData.normalizeMapMarkerType(
                        ctx.getValue("map-marker-type", String.class).orElse(mapMarkerType[0]));
                mapMarkerName[0] = ctx.getValue("map-marker-name", String.class).orElse(mapMarkerName[0]);
            }

            String name = currentName[0].trim();

            if (name.isEmpty()) {
                playerRef.sendMessage(Message.raw("Please enter a citizen name!").color(Color.RED));
                return;
            }

            String modelId;
            if (isPlayerModel[0]) {
                modelId = "Player";
            } else {
                modelId = currentModelId[0].trim();
                if (modelId.isEmpty()) {
                    playerRef.sendMessage(Message.raw("Please select or enter a model ID!").color(Color.RED));
                    return;
                }
            }

            boolean modelChanged = !citizen.getModelId().equals(modelId);

            if (skinUsername[0].isEmpty()) {
                skinUsername[0] = playerRef.getUsername();
            }

            String oldSkinUsername = citizen.getSkinUsername();

            citizen.setName(name);
            citizen.setModelId(modelId);
            if (modelChanged) {
                plugin.getCitizensManager().autoResolveAttackType(citizen);
            }
            citizen.setScale(currentScale[0]);
            citizen.setRequiredPermission(currentPermission[0].trim());
            citizen.setNoPermissionMessage(currentPermMessage[0].trim());
            citizen.setPlayerModel(isPlayerModel[0]);
            citizen.setUseLiveSkin(useLiveSkin[0]);
            citizen.setRotateTowardsPlayer(rotateTowardsPlayer[0]);
            citizen.setLookAtDistance(lookAtDistance[0]);
            citizen.setSkinUsername(skinUsername[0].trim());
            citizen.setNametagOffset(nametagOffset[0]);
            citizen.setHideNametag(hideNametag[0]);
            citizen.setModelNametagEnabled(modelNametagEnabled[0]);
            citizen.setNametagModelId(currentNametagModelId[0].trim());
            citizen.setNametagModelScale(currentNametagModelScale[0]);
            citizen.setRotateNametagTowardsPlayer(rotateNametagTowardsPlayer[0]);
            citizen.setHideNpc(hideNpc[0]);
            citizen.setMapMarkerEnabled(mapMarkerEnabled[0]);
            citizen.setMapMarkerType(mapMarkerType[0]);
            citizen.setMapMarkerName(mapMarkerName[0]);
            citizen.setGroup(currentGroup[0]);

            if (isPlayerModel[0]) {
                if (cachedSkin[0] != null && !cachedSkin[0].equals(citizen.getCachedSkin())) {
                    // Use the newly generated random skin (only if it changed)
                    citizen.setCachedSkin(cachedSkin[0]);
                    citizen.setLastSkinUpdate(System.currentTimeMillis());
                    plugin.getCitizensManager().updateCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Citizen '" + name + "' updated!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                } else if (skinUsername[0].trim().isEmpty()) {
                    plugin.getCitizensManager().updateCitizenSkinFromPlayer(citizen, playerRef, false);
                    plugin.getCitizensManager().updateCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Citizen '" + name + "' updated!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                } else if (CitizenData.isGeneratedSkinUsername(skinUsername[0].trim()) && citizen.getCachedSkin() != null) {
                    plugin.getCitizensManager().updateCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Citizen '" + name + "' updated!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                } else if (skinUsername[0].equals(oldSkinUsername) && citizen.getCachedSkin() != null) {
                    plugin.getCitizensManager().updateCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Citizen '" + name + "' updated!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                } else {
                    playerRef.sendMessage(Message.raw("Fetching skin for \"" + skinUsername[0] + "\"...").color(Color.YELLOW));
                    com.electro.hycitizens.util.SkinUtilities.getSkin(skinUsername[0].trim()).thenAccept(skin -> {
                        World world = Universe.get().getWorld(citizen.getWorldUUID());
                        if (world != null) {
                            world.execute(() -> {
                                if (skin != null) {
                                    citizen.setCachedSkin(skin);
                                    citizen.setLastSkinUpdate(System.currentTimeMillis());
                                }
                                plugin.getCitizensManager().updateCitizen(citizen, true);
                                playerRef.sendMessage(Message.raw("Citizen '" + name + "' updated!").color(Color.GREEN));
                                openCitizensGUI(playerRef, store, Tab.MANAGE);
                            });
                        }
                    });
                }
            } else {
                plugin.getCitizensManager().updateCitizen(citizen, true);
                playerRef.sendMessage(Message.raw("Citizen '" + name + "' updated!").color(Color.GREEN));
                openCitizensGUI(playerRef, store, Tab.MANAGE);
            }
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.MANAGE));
    }

    private void setupCommandActionsListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                              String citizenId, List<CommandAction> actions, boolean isCreating) {
        if (!isCreating) {
            page.addEventListener("cmd-mode-all", CustomUIEventBindingType.Activating, event -> {
                CitizenData citizen = plugin.getCitizensManager().getCitizen(citizenId);
                if (citizen == null) {
                    return;
                }
                citizen.setCommandSelectionMode("ALL");
                plugin.getCitizensManager().saveCitizen(citizen);
                openCommandActionsGUI(playerRef, store, citizenId, actions, false);
            });

            page.addEventListener("cmd-mode-random", CustomUIEventBindingType.Activating, event -> {
                CitizenData citizen = plugin.getCitizensManager().getCitizen(citizenId);
                if (citizen == null) {
                    return;
                }
                citizen.setCommandSelectionMode("RANDOM");
                plugin.getCitizensManager().saveCitizen(citizen);
                openCommandActionsGUI(playerRef, store, citizenId, actions, false);
            });

            page.addEventListener("cmd-mode-sequential", CustomUIEventBindingType.Activating, event -> {
                CitizenData citizen = plugin.getCitizensManager().getCitizen(citizenId);
                if (citizen == null) {
                    return;
                }
                citizen.setCommandSelectionMode("SEQUENTIAL");
                plugin.getCitizensManager().saveCitizen(citizen);
                openCommandActionsGUI(playerRef, store, citizenId, actions, false);
            });
        }

        // "Add Command" button opens the edit GUI in add-new mode (editIndex = -1)
        page.addEventListener("add-command-btn", CustomUIEventBindingType.Activating, event -> {
            openEditCommandGUI(playerRef, store, citizenId, actions, isCreating,
                    new CommandAction("", false, 0.0f, "BOTH"), -1);
        });

        for (int i = 0; i < actions.size(); i++) {
            final int index = i;

            page.addEventListener("edit-cmd-" + i, CustomUIEventBindingType.Activating, event -> {
                CommandAction action = actions.get(index);
                openEditCommandGUI(playerRef, store, citizenId, actions, isCreating, action, index);
            });

            page.addEventListener("delete-" + i, CustomUIEventBindingType.Activating, event -> {
                actions.remove(index);
                playerRef.sendMessage(Message.raw("Command removed!").color(Color.GREEN));
                openCommandActionsGUI(playerRef, store, citizenId, actions, isCreating);
            });
        }

        page.addEventListener("done-btn", CustomUIEventBindingType.Activating, event -> {
            if (!isCreating) {
                CitizenData citizen = plugin.getCitizensManager().getCitizen(citizenId);
                if (citizen != null) {
                    citizen.setCommandActions(new ArrayList<>(actions));
                    plugin.getCitizensManager().updateCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Commands updated!").color(Color.GREEN));
                    openEditCitizenGUI(playerRef, store, citizen);
                }
            } else {
                openCreateCitizenGUI(playerRef, store);
            }
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            if (!isCreating) {
                CitizenData citizen = plugin.getCitizensManager().getCitizen(citizenId);
                if (citizen != null) {
                    openEditCitizenGUI(playerRef, store, citizen);
                }
            } else {
                openCreateCitizenGUI(playerRef, store);
            }
        });
    }

    public void openEditCommandGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                   @Nonnull String citizenId, @Nonnull List<CommandAction> actions,
                                   boolean isCreating, @Nonnull CommandAction command, int editIndex) {
        boolean isNew = (editIndex == -1);
        String currentTrigger = command.getInteractionTrigger() != null ? command.getInteractionTrigger() : "BOTH";

        TemplateProcessor template = createBaseTemplate()
                .setVariable("command", escapeHtml(command.getCommand()))
                .setVariable("runAsServer", command.isRunAsServer())
                .setVariable("delaySeconds", command.getDelaySeconds())
                .setVariable("chancePercent", command.getChancePercent())
                .setVariable("isNew", isNew)
                .setVariable("isLeftClick", "LEFT_CLICK".equals(currentTrigger))
                .setVariable("isFKey", "F_KEY".equals(currentTrigger))
                .setVariable("isBoth", "BOTH".equals(currentTrigger));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 680; anchor-height: 780;">

                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">{{#if isNew}}Add Command{{else}}Edit Command{{/if}}</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body">

                            <p class="page-description">{{#if isNew}}Configure a new command to execute on interaction{{else}}Modify the command that executes on interaction{{/if}}</p>
                            <div class="spacer-sm"></div>

                            <!-- Command Input -->
                            <div class="section">
                                {{@sectionHeader:title=Command}}
                                <input type="text" id="command-input" class="form-input" value="{{$command}}"
                                       placeholder="give {PlayerName} Rock_Gem_Diamond" />
                                <p class="form-hint">The command to execute. Do not include the leading /. Variables: {PlayerName}, {CitizenName}, {NpcX}, {NpcY}, {NpcZ}.</p>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Interaction Trigger -->
                            <div class="section">
                                {{@sectionHeader:title=Interaction Trigger,description=Which player action triggers this command}}
                                <div class="form-row">
                                    <button id="trigger-left-click" class="{{#if isLeftClick}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Left Click</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="trigger-f-key" class="{{#if isFKey}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">F Key</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="trigger-both" class="{{#if isBoth}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Both</button>
                                </div>
                                <div class="spacer-xs"></div>
                                {{#if isLeftClick}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Runs when the player left-clicks the NPC.</p>
                                {{/if}}
                                {{#if isFKey}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Runs when the player presses F to interact with the NPC.</p>
                                {{/if}}
                                {{#if isBoth}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Runs on any interaction (left click or F key).</p>
                                {{/if}}
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Delay Input -->
                            <div class="section">
                                {{@sectionHeader:title=Delay}}
                                {{@numberField:id=delay-seconds,label=Delay Before Command (seconds),value={{$delaySeconds}},placeholder=0,min=0,max=300,step=0.5,decimals=1,hint=Wait time before executing this command}}
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Chance}}
                                {{@numberField:id=chance-percent,label=Chance %,value={{$chancePercent}},placeholder=100,min=0,max=100,step=1,decimals=1,hint=Percent chance this command is selected when it is otherwise eligible}}
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Run As Server Toggle -->
                            <div class="section">
                                {{@sectionHeader:title=Execution Mode}}
                                <div class="checkbox-row">
                                    <input type="checkbox" id="run-as-server" {{#if runAsServer}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0;">
                                        <p class="checkbox-label">Run as Server</p>
                                        <p class="checkbox-description">Execute as console command instead of player</p>
                                    </div>
                                </div>
                            </div>

                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-cmd-btn" class="secondary-button" style="anchor-width: 200;">{{#if isNew}}Add Command{{else}}Save Changes{{/if}}</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] commandText = {command.getCommand()};
        final boolean[] runAsServer = {command.isRunAsServer()};
        final float[] delaySeconds = {command.getDelaySeconds()};
        final float[] chancePercent = {command.getChancePercent()};
        final String[] interactionTrigger = {currentTrigger};

        page.addEventListener("command-input", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            commandText[0] = ctx.getValue("command-input", String.class).orElse("");
        });

        page.addEventListener("trigger-left-click", CustomUIEventBindingType.Activating, event -> {
            interactionTrigger[0] = "LEFT_CLICK";
            openEditCommandGUI(playerRef, store, citizenId, actions, isCreating,
                    new CommandAction(commandText[0], runAsServer[0], delaySeconds[0], "LEFT_CLICK", chancePercent[0]), editIndex);
        });

        page.addEventListener("trigger-f-key", CustomUIEventBindingType.Activating, event -> {
            interactionTrigger[0] = "F_KEY";
            openEditCommandGUI(playerRef, store, citizenId, actions, isCreating,
                    new CommandAction(commandText[0], runAsServer[0], delaySeconds[0], "F_KEY", chancePercent[0]), editIndex);
        });

        page.addEventListener("trigger-both", CustomUIEventBindingType.Activating, event -> {
            interactionTrigger[0] = "BOTH";
            openEditCommandGUI(playerRef, store, citizenId, actions, isCreating,
                    new CommandAction(commandText[0], runAsServer[0], delaySeconds[0], "BOTH", chancePercent[0]), editIndex);
        });

        page.addEventListener("delay-seconds", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("delay-seconds", Double.class)
                    .ifPresent(val -> delaySeconds[0] = val.floatValue());

            if (delaySeconds[0] == 0.0f) {
                ctx.getValue("delay-seconds", String.class)
                        .ifPresent(val -> {
                            try {
                                delaySeconds[0] = Float.parseFloat(val);
                            } catch (NumberFormatException e) {
                            }
                        });
            }
        });

        page.addEventListener("run-as-server", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            runAsServer[0] = ctx.getValue("run-as-server", Boolean.class).orElse(false);
        });

        page.addEventListener("chance-percent", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("chance-percent", Double.class).ifPresent(val -> {
                chancePercent[0] = Math.max(0.0f, Math.min(100.0f, val.floatValue()));
            });
        });

        page.addEventListener("save-cmd-btn", CustomUIEventBindingType.Activating, event -> {
            String cmd = commandText[0].trim();
            if (cmd.isEmpty()) {
                playerRef.sendMessage(Message.raw("Command cannot be empty!").color(Color.RED));
                return;
            }
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }

            CommandAction saved = new CommandAction(cmd, runAsServer[0], delaySeconds[0], interactionTrigger[0], chancePercent[0]);
            if (isNew) {
                actions.add(saved);
                playerRef.sendMessage(Message.raw("Command added!").color(Color.GREEN));
            } else {
                actions.set(editIndex, saved);
                playerRef.sendMessage(Message.raw("Command updated!").color(Color.GREEN));
            }
            openCommandActionsGUI(playerRef, store, citizenId, actions, isCreating);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openCommandActionsGUI(playerRef, store, citizenId, actions, isCreating);
        });

        page.open(store);
    }

    public void openBehaviorsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                 @Nonnull CitizenData citizen) {
        pendingFollowSelections.remove(playerRef.getUuid());
        List<AnimationBehavior> anims = citizen.getAnimationBehaviors();
        List<IndexedAnimationBehavior> indexedAnims = new ArrayList<>();
        for (int i = 0; i < anims.size(); i++) {
            indexedAnims.add(new IndexedAnimationBehavior(i, anims.get(i)));
        }

        MovementBehavior mb = citizen.getMovementBehavior();

        String attitude = citizen.getAttitude();
        String npcAttitude = normalizeNpcAttitude(citizen.getDefaultNpcAttitude());
        boolean takesDamage = citizen.isTakesDamage();
        CitizenData followTarget = plugin.getCitizensManager().getCitizen(citizen.getFollowCitizenId());
        boolean hasFollowTarget = followTarget != null && !citizen.getFollowCitizenId().trim().isEmpty();
        String followTargetName = hasFollowTarget ? followTarget.getName() : "No citizen selected";
        String followTargetMeta = hasFollowTarget
                ? "Linked to " + followTarget.getId()
                : (!citizen.getFollowCitizenId().trim().isEmpty()
                ? "Saved target ID: " + citizen.getFollowCitizenId() + " (not found)"
                : "Click Select Target, then hit another citizen in-world.");

        TemplateProcessor template = createBaseTemplate()
                .setVariable("animations", indexedAnims)
                .setVariable("hasAnimations", !anims.isEmpty())
                .setVariable("animCount", anims.size())
                .setVariable("moveType", mb.getType())
                .setVariable("isIdle", "IDLE".equals(mb.getType()))
                .setVariable("isWander", "WANDER".equals(mb.getType()))
                .setVariable("isWanderCircle", "WANDER_CIRCLE".equals(mb.getType()))
                .setVariable("isWanderRect", "WANDER_RECT".equals(mb.getType()))
                .setVariable("isFollowCitizen", "FOLLOW_CITIZEN".equals(mb.getType()))
                .setVariable("hasWalkSpeedControl",
                        "WANDER".equals(mb.getType())
                                || "WANDER_CIRCLE".equals(mb.getType())
                                || "WANDER_RECT".equals(mb.getType())
                                || "FOLLOW_CITIZEN".equals(mb.getType()))
                .setVariable("hasWanderRadiusControl",
                        "WANDER".equals(mb.getType())
                                || "WANDER_CIRCLE".equals(mb.getType())
                                || "WANDER_RECT".equals(mb.getType()))
                .setVariable("walkSpeed", mb.getWalkSpeed())
                .setVariable("wanderRadius", mb.getWanderRadius())
                .setVariable("wanderWidth", mb.getWanderWidth())
                .setVariable("wanderDepth", mb.getWanderDepth())
                .setVariable("isPassive", "PASSIVE".equals(attitude))
                .setVariable("isNeutral", "NEUTRAL".equals(attitude))
                .setVariable("isAggressive", "AGGRESSIVE".equals(attitude))
                .setVariable("playerAttitudeOptions", generatePlayerAttitudeOptions(attitude))
                .setVariable("npcAttitudeOptions", generateNpcAttitudeOptions(citizen.getDefaultNpcAttitude()))
                .setVariable("npcIsPassive", "PASSIVE".equals(npcAttitude))
                .setVariable("npcIsNeutral", "NEUTRAL".equals(npcAttitude))
                .setVariable("npcIsAggressive", "AGGRESSIVE".equals(npcAttitude))
                .setVariable("takesDamage", takesDamage)
                .setVariable("overrideHealth", citizen.isOverrideHealth())
                .setVariable("healthAmount", citizen.getHealthAmount())
                .setVariable("overrideDamage", citizen.isOverrideDamage())
                .setVariable("damageAmount", citizen.getDamageAmount())
                .setVariable("healthRegenEnabled", citizen.isHealthRegenEnabled())
                .setVariable("healthRegenAmount", citizen.getHealthRegenAmount())
                .setVariable("healthRegenInterval", citizen.getHealthRegenIntervalSeconds())
                .setVariable("healthRegenDelayAfterDamage", citizen.getHealthRegenDelayAfterDamageSeconds())
                .setVariable("isPatrol", "PATROL".equals(mb.getType()))
                .setVariable("patrolPathOptions", generatePatrolPathOptions(citizen.getPathConfig().getPluginPatrolPath()))
                .setVariable("hasPatrolPaths", !plugin.getCitizensManager().getPatrolManager().getAllPathNames().isEmpty())
                .setVariable("respawnOnDeath", citizen.isRespawnOnDeath())
                .setVariable("respawnDelay", citizen.getRespawnDelaySeconds())
                .setVariable("followDistance", citizen.getFollowDistance())
                .setVariable("followTargetName", escapeHtml(followTargetName))
                .setVariable("followTargetMeta", escapeHtml(followTargetMeta));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 950; anchor-height: 1040;">
                
                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Behaviors</p>
                            </div>
                        </div>
                
                        <!-- Body -->
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">

                            <p class="page-description">Configure movement and animations for this citizen</p>
                            <div class="spacer-sm"></div>
                
                            <!-- Attitude Section -->
                            <div class="section">
                                {{@sectionHeader:title=Attitude Towards Player,description=How the citizen reacts to players}}
                 
                                <div class="form-row">
                                    <select id="player-attitude" data-hyui-showlabel="true">
                                        {{{$playerAttitudeOptions}}}
                                    </select>
                                </div>

                                <div class="spacer-sm"></div>

                                {{#if isPassive}}
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12; text-align: center;">The citizen will ignore players completely and never engage in combat.</p>
                                    </div>
                                </div>
                                {{/if}}
                                {{#if isNeutral}}
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12; text-align: center;">The citizen will only attack players who attack them first.</p>
                                    </div>
                                </div>
                                {{/if}}
                                {{#if isAggressive}}
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12; text-align: center;">The citizen will attack players on sight.</p>
                                    </div>
                                </div>
                                {{/if}}
                                <div>
                                    <p class="form-hint" style="text-align: center; color: #f85149;">Note: You must have the citizen set to "Wander" for the attitude to have an effect</p>
                                </div>

                                <div class="spacer-sm"></div>

                                <div class="form-row">
                                    <p class="form-label" style="text-align: center;">Attitude Towards NPCs</p>
                                </div>
                                <div class="form-row">
                                    <select id="npc-attitude" data-hyui-showlabel="true">
                                        {{{$npcAttitudeOptions}}}
                                    </select>
                                </div>

                                <div class="spacer-sm"></div>

                                {{#if npcIsPassive}}
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12; text-align: center;">The citizen will ignore other NPCs by default.</p>
                                    </div>
                                </div>
                                {{/if}}
                                {{#if npcIsNeutral}}
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12; text-align: center;">The citizen treats other NPCs as neutral by default.</p>
                                    </div>
                                </div>
                                {{/if}}
                                {{#if npcIsAggressive}}
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12; text-align: center;">The citizen treats other NPCs as hostile by default.</p>
                                    </div>
                                </div>
                                {{/if}}
                            </div>
                
                            <div class="spacer-sm"></div>
                
                            <!-- Takes Damage Section -->
                            <div class="section">
                                <p style="color: #c9d1d9; font-size: 12; font-weight: bold; text-align: center;">Takes Damage</p>
                                <div class="spacer-xs"></div>
                                <div style="layout: center;">
                                    <button id="toggle-damage" class="{{#if takesDamage}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 140; anchor-height: 40;">{{#if takesDamage}}Enabled{{else}}Disabled{{/if}}</button>
                                </div>
                                <div class="spacer-xs"></div>
                                <p style="color: #8b949e; font-size: 12; text-align: center;">When enabled, the citizen can take damage and be killed by players.</p>
                            </div>

                            <div class="spacer-sm"></div>

                            <!-- Override Health Section -->
                            <div class="section">
                                <p style="color: #c9d1d9; font-size: 12; font-weight: bold; text-align: center;">Override Health</p>
                                <div class="spacer-xs"></div>
                                <div style="layout: center;">
                                    <button id="toggle-override-health" class="{{#if overrideHealth}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 140; anchor-height: 40;">{{#if overrideHealth}}Enabled{{else}}Disabled{{/if}}</button>
                                </div>
                                <div class="spacer-xs"></div>
                                {{#if overrideHealth}}
                                <div class="form-row">
                                    <div style="layout: top; flex-weight: 0; anchor-width: 260;">
                                        <p class="form-label" style="text-align: center;">Max Health</p>
                                        <input type="number" id="health-amount" class="form-input"
                                               value="{{$healthAmount}}"
                                               placeholder="100"
                                               min="1"
                                               max="1000000"
                                               step="1"
                                               data-hyui-max-decimal-places="0"
                                               style="text-align: center;" />
                                    </div>
                                </div>
                                {{/if}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Override the citizen's max health.</p>
                            </div>

                            <div class="spacer-sm"></div>

                            <!-- Override Damage Section -->
                            <div class="section">
                                <p style="color: #c9d1d9; font-size: 12; font-weight: bold; text-align: center;">Override Damage</p>
                                <div class="spacer-xs"></div>
                                <div style="layout: center;">
                                    <button id="toggle-override-damage" class="{{#if overrideDamage}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 140; anchor-height: 40;">{{#if overrideDamage}}Enabled{{else}}Disabled{{/if}}</button>
                                </div>
                                <div class="spacer-xs"></div>
                                {{#if overrideDamage}}
                                <div class="form-row">
                                    {{@numberField:id=damage-amount,label=Damage Amount,value={{$damageAmount}},placeholder=10,min=0,max=10000,step=1,decimals=1}}
                                </div>
                                {{/if}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Override how much damage the citizen deals.</p>
                            </div>

                            <div class="spacer-sm"></div>

                            <!-- Health Regeneration Section -->
                            <div class="section">
                                <p style="color: #c9d1d9; font-size: 12; font-weight: bold; text-align: center;">Health Regeneration</p>
                                <div class="spacer-xs"></div>
                                <div style="layout: center;">
                                    <button id="toggle-health-regen" class="{{#if healthRegenEnabled}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 140; anchor-height: 40;">{{#if healthRegenEnabled}}Enabled{{else}}Disabled{{/if}}</button>
                                </div>
                                <div class="spacer-xs"></div>
                                {{#if healthRegenEnabled}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=health-regen-amount,label=Regen Amount,value={{$healthRegenAmount}},placeholder=1,min=0,max=1000,step=0.5,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=health-regen-interval,label=Regen Interval (s),value={{$healthRegenInterval}},placeholder=5,min=0.5,max=600,step=0.5,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=health-regen-delay,label=Delay After Damage (s),value={{$healthRegenDelayAfterDamage}},placeholder=5,min=0,max=600,step=0.5,decimals=1}}
                                    </div>
                                </div>
                                {{/if}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">When enabled, the citizen passively heals after taking damage.</p>
                            </div>

                            <div class="spacer-sm"></div>

                            <!-- Respawn Section -->
                            <div class="section">
                                <p style="color: #c9d1d9; font-size: 12; font-weight: bold; text-align: center;">Respawn on Death</p>
                                <div class="spacer-xs"></div>
                                <div style="layout: center;">
                                    <button id="toggle-respawn" class="{{#if respawnOnDeath}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 140; anchor-height: 40;">{{#if respawnOnDeath}}Enabled{{else}}Disabled{{/if}}</button>
                                </div>
                                <div class="spacer-xs"></div>
                                {{#if respawnOnDeath}}
                                <div class="form-row">
                                    {{@numberField:id=respawn-delay,label=Respawn Delay (seconds),value={{$respawnDelay}},placeholder=5,min=1,max=300,step=1,decimals=0}}
                                </div>
                                {{/if}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">When enabled, the citizen will respawn after dying.</p>
                            </div>
                
                            <div class="spacer-sm"></div>
                
                            <!-- Movement Section -->
                            <div class="section">
                                {{@sectionHeader:title=Movement Type}}
                
                                <div class="form-row">
                                    <button id="move-idle" class="{{#if isIdle}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 130; anchor-height: 38;">Idle</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="move-wander" class="{{#if isWander}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 130; anchor-height: 38;">Wander</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="move-patrol" class="{{#if isPatrol}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 130; anchor-height: 38;">Patrol</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="move-follow" class="{{#if isFollowCitizen}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 170; anchor-height: 38;">Follow Citizen</button>
                                </div>

                                <div class="spacer-sm"></div>

                                {{#if isIdle}}
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12; text-align: center;">The citizen will stay in place and not move.</p>
                                    </div>
                                </div>
                                {{/if}}

                                {{#if isPatrol}}
                                {{#if hasPatrolPaths}}
                                <select id="plugin-patrol-path-behavior" data-hyui-showlabel="true">
                                    {{{$patrolPathOptions}}}
                                </select>
                                <div class="spacer-xs"></div>
                                <button id="manage-paths-from-behaviors-btn" class="secondary-button" style="anchor-width: 200;">Manage Patrol Paths</button>
                                {{else}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">No patrol paths exist yet.</p>
                                <div class="spacer-xs"></div>
                                <button id="manage-paths-from-behaviors-btn" class="secondary-button" style="anchor-width: 200;">Create a Patrol Path</button>
                                {{/if}}
                                {{/if}}

                                {{#if hasWalkSpeedControl}}
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    {{@numberField:id=walk-speed,label=Walk Speed,value={{$walkSpeed}},placeholder=10,min=1,max=100,step=1,decimals=0}}
                                </div>
                                {{/if}}

                                {{#if hasWanderRadiusControl}}
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    {{@numberField:id=wander-radius,label=Wander Radius,value={{$wanderRadius}},placeholder=5,min=1,max=100,step=1,decimals=0}}
                                </div>
                                {{/if}}

                                {{#if isFollowCitizen}}
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    {{@numberField:id=follow-distance,label=Follow Distance,value={{$followDistance}},placeholder=1,min=0.1,max=10,step=0.1,decimals=1}}
                                </div>

                                <div class="spacer-sm"></div>
                                <div class="card">
                                    <div class="card-body">
                                        <p class="list-item-title" style="text-align: center;">{{$followTargetName}}</p>
                                        <p class="list-item-subtitle" style="text-align: center;">{{$followTargetMeta}}</p>
                                    </div>
                                </div>

                                <div class="spacer-sm"></div>

                                <div class="form-row">
                                    <button id="select-follow-target-btn" class="secondary-button" style="anchor-width: 220; anchor-height: 40;">Select Target</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="clear-follow-target-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 40;">Clear Target</button>
                                </div>

                                <div class="spacer-xs"></div>

                                <p style="color: #8b949e; font-size: 12; text-align: center;">Select Target closes the editor, then waits for you to hit another citizen.</p>
                                {{/if}}
                            </div>
                 
                            <div class="spacer-md"></div>
                 
                            <!-- Animations Section -->
                            <div class="section">
                                {{@sectionHeader:title=Animations,description=Configure animations that play on various triggers}}

                                <div class="form-row">
                                    <button id="add-animation-btn" class="secondary-button" style="anchor-width: 250; anchor-height: 45;">Add Animation</button>
                                </div>
                
                                <div class="spacer-sm"></div>
                
                                {{#if hasAnimations}}
                                <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="anchor-height: 220;">
                                    {{#each animations}}
                                    <div class="command-item">
                                        <div class="command-icon command-icon-server">
                                            <p class="command-icon-text command-icon-text-server" style="font-size: 8;">A</p>
                                        </div>
                                        <div class="command-content">
                                            <p class="command-text">{{$animationName}} ({{$slotName}})</p>
                                            <p class="command-type">{{$type}}{{#if isTimed}} - every {{$intervalSeconds}}s{{/if}}{{#if isProximity}} - {{$proximityRange}} blocks{{/if}}</p>
                                        </div>
                                        <div class="command-actions">
                                            <button id="edit-anim-{{$index}}" class="secondary-button small-secondary-button">Edit</button>
                                            <div class="spacer-h-sm"></div>
                                            <button id="delete-anim-{{$index}}" class="secondary-button small-secondary-button">Delete</button>
                                        </div>
                                    </div>
                                    <div class="spacer-sm"></div>
                                    {{/each}}
                                </div>
                                {{else}}
                                <div class="empty-state">
                                    <div class="empty-state-content">
                                        <p class="empty-state-title">No Animations</p>
                                        <p class="empty-state-description">Add animations to play on various triggers like interact, attack, or proximity.</p>
                                    </div>
                                </div>
                                {{/if}}
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Advanced Configuration -->
                            <div class="section">
                                {{@sectionHeader:title=Advanced Configuration,description=Configure combat&#44; detection&#44; pathing&#44; and other advanced behavior parameters}}

                                <div class="form-row">
                                    <button id="combat-config-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 44;">Combat Config</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="detection-config-btn" class="secondary-button" style="anchor-width: 225; anchor-height: 44;">Detection Config</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="advanced-settings-btn" class="secondary-button" style="anchor-width: 240; anchor-height: 44;">Advanced Settings</button>
                                </div>
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    <button id="death-config-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 44;">Death Config</button>
                                </div>
                            </div>

                        </div>
                        
                        <div class="spacer-sm"></div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="done-btn" class="secondary-button" style="anchor-width: 160;">Done</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupBehaviorsListeners(page, playerRef, store, citizen);

        page.open(store);
    }

    private void setupBehaviorsListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                         CitizenData citizen) {
        final MovementBehavior mb = citizen.getMovementBehavior();
        final String moveType = mb.getType();
        final float[] walkSpeed = {mb.getWalkSpeed()};
        final float[] wanderRadius = {mb.getWanderRadius()};
        final float[] wanderWidth = {mb.getWanderWidth()};
        final float[] wanderDepth = {mb.getWanderDepth()};
        final float[] followDistance = {citizen.getFollowDistance()};
        List<AnimationBehavior> anims = new ArrayList<>(citizen.getAnimationBehaviors());

        page.addEventListener("player-attitude", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("player-attitude", String.class).ifPresent(newAttitude -> {
                citizen.setAttitude(normalizePlayerAttitude(newAttitude));
                plugin.getCitizensManager().saveCitizen(citizen, true);
                openBehaviorsGUI(playerRef, store, citizen);
            });
        });

        page.addEventListener("npc-attitude", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("npc-attitude", String.class).ifPresent(newNpcAttitude -> {
                citizen.setDefaultNpcAttitude(normalizeNpcAttitude(newNpcAttitude));
                plugin.getCitizensManager().saveCitizen(citizen, true);
                openBehaviorsGUI(playerRef, store, citizen);
            });
        });

        // Takes damage toggle
        page.addEventListener("toggle-damage", CustomUIEventBindingType.Activating, event -> {
            citizen.setTakesDamage(!citizen.isTakesDamage());
            plugin.getCitizensManager().saveCitizen(citizen);
            plugin.getCitizensManager().updateDamageInvulnerability(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        // Override health toggle
        page.addEventListener("toggle-override-health", CustomUIEventBindingType.Activating, event -> {
            boolean newValue = !citizen.isOverrideHealth();
            citizen.setOverrideHealth(newValue);
            if (!newValue) {
                citizen.setHealthAmount(100);
            }
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        // Health amount input
        if (citizen.isOverrideHealth()) {
            page.addEventListener("health-amount", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("health-amount", Double.class).ifPresent(val -> {
                    citizen.setHealthAmount(val.floatValue());
                    plugin.getCitizensManager().saveCitizen(citizen);
                });
            });
        }

        // Override damage toggle
        page.addEventListener("toggle-override-damage", CustomUIEventBindingType.Activating, event -> {
            citizen.setOverrideDamage(!citizen.isOverrideDamage());
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        // Damage amount input
        if (citizen.isOverrideDamage()) {
            page.addEventListener("damage-amount", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("damage-amount", Double.class).ifPresent(val -> {
                    citizen.setDamageAmount(val.floatValue());
                    plugin.getCitizensManager().saveCitizen(citizen);
                });
            });
        }

        page.addEventListener("toggle-health-regen", CustomUIEventBindingType.Activating, event -> {
            citizen.setHealthRegenEnabled(!citizen.isHealthRegenEnabled());
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        if (citizen.isHealthRegenEnabled()) {
            page.addEventListener("health-regen-amount", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("health-regen-amount", Double.class).ifPresent(v -> {
                    citizen.setHealthRegenAmount(Math.max(0.0f, v.floatValue()));
                    plugin.getCitizensManager().saveCitizen(citizen);
                });
            });
            page.addEventListener("health-regen-interval", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("health-regen-interval", Double.class).ifPresent(v -> {
                    citizen.setHealthRegenIntervalSeconds(Math.max(0.5f, v.floatValue()));
                    plugin.getCitizensManager().saveCitizen(citizen);
                });
            });
            page.addEventListener("health-regen-delay", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("health-regen-delay", Double.class).ifPresent(v -> {
                    citizen.setHealthRegenDelayAfterDamageSeconds(Math.max(0.0f, v.floatValue()));
                    plugin.getCitizensManager().saveCitizen(citizen);
                });
            });
        }

        // Respawn toggle
        page.addEventListener("toggle-respawn", CustomUIEventBindingType.Activating, event -> {
            citizen.setRespawnOnDeath(!citizen.isRespawnOnDeath());
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        // Respawn delay input
        if (citizen.isRespawnOnDeath()) {
            page.addEventListener("respawn-delay", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("respawn-delay", Double.class).ifPresent(val -> {
                    citizen.setRespawnDelaySeconds(val.floatValue());
                    plugin.getCitizensManager().saveCitizen(citizen);
                });
            });
        }

        // Movement type buttons
        page.addEventListener("move-idle", CustomUIEventBindingType.Activating, event -> {
            if ("PATROL".equals(moveType)) {
                plugin.getCitizensManager().stopCitizenPatrol(citizen.getId());
            }
            citizen.setFollowCitizenEnabled(false);
            citizen.setMovementBehavior(new MovementBehavior("IDLE", walkSpeed[0], wanderRadius[0], wanderWidth[0], wanderDepth[0]));
            plugin.getCitizensManager().saveCitizen(citizen, true);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("move-wander", CustomUIEventBindingType.Activating, event -> {
            if ("PATROL".equals(moveType)) {
                plugin.getCitizensManager().stopCitizenPatrol(citizen.getId());
            }
            citizen.setFollowCitizenEnabled(false);
            citizen.setMovementBehavior(new MovementBehavior("WANDER", walkSpeed[0], wanderRadius[0], wanderWidth[0], wanderDepth[0]));
            plugin.getCitizensManager().saveCitizen(citizen, true);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("move-patrol", CustomUIEventBindingType.Activating, event -> {
            citizen.setFollowCitizenEnabled(false);
            citizen.setMovementBehavior(new MovementBehavior("PATROL", walkSpeed[0], wanderRadius[0], wanderWidth[0], wanderDepth[0]));
            plugin.getCitizensManager().saveCitizen(citizen, true);
            String patrolPath = citizen.getPathConfig().getPluginPatrolPath();
            if (!patrolPath.isEmpty()) {
                plugin.getCitizensManager().startCitizenPatrol(citizen.getId(), patrolPath);
            }
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("move-follow", CustomUIEventBindingType.Activating, event -> {
            if ("PATROL".equals(moveType)) {
                plugin.getCitizensManager().stopCitizenPatrol(citizen.getId());
            }
            citizen.setFollowCitizenEnabled(true);
            citizen.setMovementBehavior(new MovementBehavior("FOLLOW_CITIZEN", walkSpeed[0], wanderRadius[0], wanderWidth[0], wanderDepth[0]));
            plugin.getCitizensManager().saveCitizen(citizen, true);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        if ("PATROL".equals(moveType)) {
            if (!plugin.getCitizensManager().getPatrolManager().getAllPathNames().isEmpty()) {
                page.addEventListener("plugin-patrol-path-behavior", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                    ctx.getValue("plugin-patrol-path-behavior", String.class).ifPresent(newPath -> {
                        citizen.getPathConfig().setPluginPatrolPath(newPath);
                        plugin.getCitizensManager().saveCitizen(citizen, true);
                        plugin.getCitizensManager().stopCitizenPatrol(citizen.getId());
                        if (!newPath.isEmpty()) {
                            plugin.getCitizensManager().startCitizenPatrol(citizen.getId(), newPath);
                        }
                    });
                });
            }

            page.addEventListener("manage-paths-from-behaviors-btn", CustomUIEventBindingType.Activating, event -> {
                openPatrolPathsGUI(playerRef, store, citizen);
            });
        }

        // Walk speed input
        boolean hasWalkSpeedControl = "WANDER".equals(moveType)
                || "WANDER_CIRCLE".equals(moveType)
                || "WANDER_RECT".equals(moveType)
                || "FOLLOW_CITIZEN".equals(moveType);
        boolean hasWanderRadiusControl = "WANDER".equals(moveType)
                || "WANDER_CIRCLE".equals(moveType)
                || "WANDER_RECT".equals(moveType);
        if (hasWalkSpeedControl) {
            page.addEventListener("walk-speed", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("walk-speed", Double.class).ifPresent(v -> {
                    walkSpeed[0] = v.floatValue();
                    citizen.setMovementBehavior(new MovementBehavior(moveType, walkSpeed[0], wanderRadius[0], wanderWidth[0], wanderDepth[0]));
                    plugin.getCitizensManager().saveCitizen(citizen, true);
                });
            });
        }

        // Wander radius input
        if (hasWanderRadiusControl) {
            page.addEventListener("wander-radius", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("wander-radius", Double.class).ifPresent(v -> {
                    wanderRadius[0] = Math.max(1, v.floatValue());
                    citizen.setMovementBehavior(new MovementBehavior(moveType, walkSpeed[0], wanderRadius[0], wanderWidth[0], wanderDepth[0]));
                    plugin.getCitizensManager().saveCitizen(citizen, true);
                });
            });
        }

        if ("FOLLOW_CITIZEN".equals(moveType)) {
            page.addEventListener("select-follow-target-btn", CustomUIEventBindingType.Activating, (event, ctx) -> {
                if (!"FOLLOW_CITIZEN".equals(citizen.getMovementBehavior().getType())) {
                    playerRef.sendMessage(Message.raw("Set the movement type to Follow Citizen first.").color(Color.RED));
                    return;
                }

                armFollowTargetSelection(playerRef, citizen);
                ctx.getPage().ifPresent(newPage -> newPage.close());
            });

            page.addEventListener("clear-follow-target-btn", CustomUIEventBindingType.Activating, event -> {
                if (citizen.getFollowCitizenId().trim().isEmpty()) {
                    playerRef.sendMessage(Message.raw("No follow target is currently linked.").color(Color.YELLOW));
                    return;
                }

                citizen.setFollowCitizenId("");
                plugin.getCitizensManager().updateCitizenNPC(citizen, true);
                playerRef.sendMessage(Message.raw("Follow target cleared.").color(Color.GREEN));
                openBehaviorsGUI(playerRef, store, citizen);
            });

            page.addEventListener("follow-distance", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("follow-distance", Double.class).ifPresent(v -> {
                    followDistance[0] = Math.max(0.1f, v.floatValue());
                    citizen.setFollowDistance(followDistance[0]);
                    plugin.getCitizensManager().saveCitizen(citizen, true);
                });
            });
        }

        // Add animation button
        page.addEventListener("add-animation-btn", CustomUIEventBindingType.Activating, event -> {
            openAnimationEditorGUI(playerRef, store, citizen, null, -1);
        });

        // Edit/Delete animation buttons
        for (int i = 0; i < anims.size(); i++) {
            final int index = i;

            page.addEventListener("edit-anim-" + i, CustomUIEventBindingType.Activating, event -> {
                openAnimationEditorGUI(playerRef, store, citizen, anims.get(index), index);
            });

            page.addEventListener("delete-anim-" + i, CustomUIEventBindingType.Activating, event -> {
                anims.remove(index);
                citizen.setAnimationBehaviors(anims);
                plugin.getCitizensManager().saveCitizen(citizen);
                playerRef.sendMessage(Message.raw("Animation removed!").color(Color.GREEN));
                openBehaviorsGUI(playerRef, store, citizen);
            });
        }

        // Advanced config navigation buttons
        page.addEventListener("combat-config-btn", CustomUIEventBindingType.Activating, event -> {
            openCombatConfigGUI(playerRef, store, citizen);
        });

        page.addEventListener("detection-config-btn", CustomUIEventBindingType.Activating, event -> {
            openDetectionConfigGUI(playerRef, store, citizen);
        });

        page.addEventListener("advanced-settings-btn", CustomUIEventBindingType.Activating, event -> {
            openAdvancedSettingsGUI(playerRef, store, citizen);
        });

        page.addEventListener("death-config-btn", CustomUIEventBindingType.Activating, event -> {
            openDeathConfigGUI(playerRef, store, citizen);
        });

        // Done - save and respawn NPC
        page.addEventListener("done-btn", CustomUIEventBindingType.Activating, event -> {
            plugin.getCitizensManager().updateCitizen(citizen, true);
            playerRef.sendMessage(Message.raw("Behaviors saved!").color(Color.GREEN));
            openEditCitizenGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openEditCitizenGUI(playerRef, store, citizen);
        });
    }

    public void openAnimationEditorGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                       @Nonnull CitizenData citizen, AnimationBehavior existing, int editIndex) {
        boolean isEditing = existing != null;
        String currentType = isEditing ? existing.getType() : "ON_INTERACT";
        String currentAnimName = isEditing ? existing.getAnimationName() : "";
        int currentSlot = isEditing ? existing.getAnimationSlot() : 2;
        float currentInterval = isEditing ? existing.getIntervalSeconds() : 5.0f;
        float currentRange = isEditing ? existing.getProximityRange() : 8.0f;
        boolean currentStopAfterTime = isEditing ? existing.isStopAfterTime() : false;
        String currentStopAnimName = isEditing ? existing.getStopAnimationName() : "";
        float currentStopTime = isEditing ? existing.getStopTimeSeconds() : 3.0f;

        // Check if citizen has a DEFAULT animation for this slot
        String defaultAnimForSlot = null;
        for (AnimationBehavior ab : citizen.getAnimationBehaviors()) {
            if ("DEFAULT".equals(ab.getType()) && ab.getAnimationSlot() == currentSlot) {
                defaultAnimForSlot = ab.getAnimationName();
                break;
            }
        }

        boolean isDefault = "DEFAULT".equals(currentType);
        boolean stopAnimNameIsEmpty = currentStopAnimName == null || currentStopAnimName.trim().isEmpty();

        TemplateProcessor template = createBaseTemplate()
                .setVariable("isEditing", isEditing)
                .setVariable("animName", escapeHtml(currentAnimName))
                .setVariable("animSlot", currentSlot)
                .setVariable("intervalSeconds", currentInterval)
                .setVariable("proximityRange", currentRange)
                .setVariable("isDefault", isDefault)
                .setVariable("isNotDefault", !isDefault)
                .setVariable("isOnInteract", "ON_INTERACT".equals(currentType))
                .setVariable("isOnAttack", "ON_ATTACK".equals(currentType))
                .setVariable("isProxEnter", "ON_PROXIMITY_ENTER".equals(currentType))
                .setVariable("isProxExit", "ON_PROXIMITY_EXIT".equals(currentType))
                .setVariable("isTimed", "TIMED".equals(currentType))
                .setVariable("isSlot0", currentSlot == 0)
                .setVariable("isSlot1", currentSlot == 1)
                .setVariable("isSlot2", currentSlot == 2)
                .setVariable("isSlot3", currentSlot == 3)
                .setVariable("isSlot4", currentSlot == 4)
                .setVariable("animationOptions", generateAnimationDropdownOptions(currentAnimName, citizen.getModelId()))
                .setVariable("stopAnimationOptions", generateAnimationDropdownOptions(currentStopAnimName, citizen.getModelId()))
                .setVariable("stopAfterTime", currentStopAfterTime)
                .setVariable("stopTimeSeconds", currentStopTime)
                .setVariable("stopAnimName", escapeHtml(currentStopAnimName))
                .setVariable("stopAnimNameIsEmpty", stopAnimNameIsEmpty)
                .setVariable("hasDefaultForSlot", defaultAnimForSlot != null)
                .setVariable("defaultAnimName", defaultAnimForSlot != null ? escapeHtml(defaultAnimForSlot) : "");

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 750; anchor-height: 900;">

                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">{{#if isEditing}}Edit Animation{{else}}Add Animation{{/if}}</p>
                            </div>
                        </div>
                
                        <!-- Body -->
                        <div class="body">

                            <p class="page-description">Configure when and how an animation plays</p>
                            <div class="spacer-sm"></div>
                
                            <!-- Trigger Type -->
                            <div class="section">
                                {{@sectionHeader:title=Trigger Type}}
                                <p style="color: #58a6ff; font-size: 12; text-align: center; margin-bottom: 8px;">
                                    Selected: {{#if isDefault}}Default{{/if}}{{#if isOnInteract}}On Interact{{/if}}{{#if isOnAttack}}On Attack{{/if}}{{#if isProxEnter}}Proximity Enter{{/if}}{{#if isProxExit}}Proximity Exit{{/if}}{{#if isTimed}}Timed{{/if}}
                                </p>
                                <div class="form-row">
                                    <button id="type-default" class="{{#if isDefault}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 150; anchor-height: 45;">Default</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="type-interact" class="{{#if isOnInteract}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 200; anchor-height: 45;">On Interact</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="type-attack" class="{{#if isOnAttack}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 150; anchor-height: 45;">On Attack</button>
                                </div>
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    <button id="type-prox-enter" class="{{#if isProxEnter}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 215; anchor-height: 45;">Proximity Enter</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="type-prox-exit" class="{{#if isProxExit}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 215; anchor-height: 45;">Proximity Exit</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="type-timed" class="{{#if isTimed}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 150; anchor-height: 45;">Timed</button>
                                </div>
                            </div>
                
                            <div class="spacer-sm"></div>
                
                            <!-- Animation Name -->
                            <div class="section">
                                {{@sectionHeader:title=Animation Name}}
                                <select id="anim-name" value="{{$animName}}" data-hyui-showlabel="true">
                                    {{$animationOptions}}
                                </select>
                                 <p class="form-hint" style="text-align: center;">Select the animation to play on the model.</p>
                                <div>
                                    <p class="form-hint" style="text-align: center;">Note: It is recommended to install third-party animation mods. For example: "Emotale" or "Emotes" from CurseForge.</p>
                                </div>
                            </div>
                
                            <div class="spacer-sm"></div>
                
                            <!-- Animation Slot -->
                            <div class="section">
                                {{@sectionHeader:title=Animation Type}}
                                <p style="color: #58a6ff; font-size: 12; text-align: center; margin-bottom: 8px;">
                                    Selected: {{#if isSlot0}}Movement{{/if}}{{#if isSlot1}}Status{{/if}}{{#if isSlot2}}Action{{/if}}{{#if isSlot3}}Face{{/if}}{{#if isSlot4}}Emote{{/if}}
                                </p>
                                <div class="form-row">
                                    <button id="slot-0" class="{{#if isSlot0}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 150; anchor-height: 45;">Movement</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="slot-1" class="{{#if isSlot1}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 120; anchor-height: 45;">Status</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="slot-2" class="{{#if isSlot2}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 120; anchor-height: 45;">Action</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="slot-3" class="{{#if isSlot3}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 110; anchor-height: 45;">Face</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="slot-4" class="{{#if isSlot4}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 110; anchor-height: 45;">Emote</button>
                                </div>
                                <div class="spacer-xs"></div>
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Trial and error may be needed to figure out which animation uses which type. Usually "Action" works for most.</p>
                            </div>
                
                            <div class="spacer-sm"></div>
                
                            <!-- Conditional fields -->
                            {{#if isTimed}}
                            <div class="section">
                                <div style="layout: center; flex-weight: 0;">
                                    <div style="anchor-width: 300; flex-weight: 0;">
                                        {{@numberField:id=interval-seconds,label=Interval (seconds),value={{$intervalSeconds}},placeholder=5.0,min=0.5,max=3600,step=0.5,decimals=1,hint=How often the animation plays}}
                                    </div>
                                </div>
                            </div>
                            {{/if}}
                
                            {{#if isProxEnter}}
                            <div class="section">
                                <div style="layout: center; flex-weight: 0;">
                                    <div style="anchor-width: 300; flex-weight: 0;">
                                        {{@numberField:id=proximity-range,label=Range (blocks),value={{$proximityRange}},placeholder=8,min=1,max=100,step=1,decimals=0,hint=Distance in blocks to trigger the animation}}
                                    </div>
                                </div>
                            </div>
                            {{/if}}

                            {{#if isProxExit}}
                            <div class="section">
                                <div style="layout: center; flex-weight: 0;">
                                    <div style="anchor-width: 300; flex-weight: 0;">
                                        {{@numberField:id=proximity-range,label=Range (blocks),value={{$proximityRange}},placeholder=8,min=1,max=100,step=1,decimals=0,hint=Distance in blocks to trigger the animation}}
                                    </div>
                                </div>
                            </div>
                            {{/if}}

                            <!-- Stop After Time (hidden for DEFAULT) -->
                            {{#if isNotDefault}}
                            <div class="spacer-sm"></div>
                            <div class="section">
                                {{@sectionHeader:title=Auto-Stop Animation,description=Automatically stop looping animations after a set time}}
                                <div>
                                    <p class="form-hint" style="text-align: center; color: #f85149;">Only enable this if the animation is set to loop!</p>
                                </div>

                                <div class="checkbox-row">
                                    <input type="checkbox" id="stop-after-time" {{#if stopAfterTime}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Stop animating after time</p>
                                    </div>
                                </div>

                                <div class="spacer-xs"></div>

                                {{#if stopAfterTime}}
                                <div class="spacer-sm"></div>
                                <div style="layout: center; flex-weight: 0;">
                                    <div style="anchor-width: 300; flex-weight: 0;">
                                        {{@numberField:id=stop-time-seconds,label=Stop After (seconds),value={{$stopTimeSeconds}},placeholder=3.0,min=0.5,max=60,step=0.5,decimals=1,hint=Time since last trigger before stopping the animation}}
                                    </div>
                                </div>

                                <div class="spacer-sm"></div>

                                {{#if hasDefaultForSlot}}
                                <p style="color: #58a6ff; font-size: 12; text-align: center; margin-bottom: 8px;">
                                    Will use DEFAULT animation "{{$defaultAnimName}}" when stopping (leave dropdown empty to use DEFAULT)
                                </p>
                                {{/if}}

                                <div class="section" style="background-color: #161b22;">
                                    <p style="color: #8b949e; font-size: 12; text-align: center; margin-bottom: 6px;">Stop Animation</p>
                                    <select id="stop-anim-name" data-hyui-showlabel="true">
                                        <option value="" {{#if stopAnimNameIsEmpty}}selected{{/if}}>{{#if hasDefaultForSlot}}DEFAULT{{else}}Idle{{/if}}</option>
                                        {{$stopAnimationOptions}}
                                    </select>
                                    <p class="form-hint" style="text-align: center;">Animation to play when stopping. Leave empty to use DEFAULT if available.</p>
                                </div>
                                {{/if}}
                            </div>
                            {{/if}}

                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-anim-btn" class="secondary-button" style="anchor-width: 200;">{{#if isEditing}}Save Changes{{else}}Add Animation{{/if}}</button>
                        </div>
                
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupAnimationEditorListeners(page, playerRef, store, citizen, existing, editIndex);

        page.open(store);
    }

    private void setupAnimationEditorListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                               CitizenData citizen, AnimationBehavior existing, int editIndex) {
        boolean isEditing = existing != null;
        final String[] currentType = {isEditing ? existing.getType() : "ON_INTERACT"};
        final String[] animName = {isEditing ? existing.getAnimationName() : ""};
        final int[] animSlot = {isEditing ? existing.getAnimationSlot() : 2};
        final float[] intervalSeconds = {isEditing ? existing.getIntervalSeconds() : 5.0f};
        final float[] proximityRange = {isEditing ? existing.getProximityRange() : 8.0f};
        final boolean[] stopAfterTime = {isEditing ? existing.isStopAfterTime() : false};
        final String[] stopAnimName = {isEditing ? existing.getStopAnimationName() : ""};
        final float[] stopTimeSeconds = {isEditing ? existing.getStopTimeSeconds() : 3.0f};

        page.addEventListener("anim-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            animName[0] = ctx.getValue("anim-name", String.class).orElse("");
        });

        // Only register listeners for elements that exist based on current type
        if ("TIMED".equals(currentType[0])) {
            page.addEventListener("interval-seconds", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("interval-seconds", Double.class).ifPresent(val -> intervalSeconds[0] = val.floatValue());
            });
        }

        if ("ON_PROXIMITY_ENTER".equals(currentType[0]) || "ON_PROXIMITY_EXIT".equals(currentType[0])) {
            page.addEventListener("proximity-range", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("proximity-range", Double.class).ifPresent(val -> proximityRange[0] = val.floatValue());
            });
        }

        // Stop-after-time listeners (only for non-DEFAULT types)
        if (!"DEFAULT".equals(currentType[0])) {
            page.addEventListener("stop-after-time", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("stop-after-time", Boolean.class).ifPresent(val -> {
                    stopAfterTime[0] = val;
                    // Rebuild GUI to show/hide conditional fields
                    AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0],
                            intervalSeconds[0], proximityRange[0], stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
                    openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
                });
            });

            if (stopAfterTime[0]) {
                page.addEventListener("stop-time-seconds", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                    ctx.getValue("stop-time-seconds", Double.class).ifPresent(val -> stopTimeSeconds[0] = val.floatValue());
                });

                page.addEventListener("stop-anim-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                    stopAnimName[0] = ctx.getValue("stop-anim-name", String.class).orElse("");
                });
            }
        }

        // Type buttons - rebuild GUI to update conditional fields
        page.addEventListener("type-default", CustomUIEventBindingType.Activating, event -> {
            currentType[0] = "DEFAULT";
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, isEditing ? ab : ab, editIndex);
        });

        page.addEventListener("type-interact", CustomUIEventBindingType.Activating, event -> {
            currentType[0] = "ON_INTERACT";
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
        });

        page.addEventListener("type-attack", CustomUIEventBindingType.Activating, event -> {
            currentType[0] = "ON_ATTACK";
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
        });

        page.addEventListener("type-prox-enter", CustomUIEventBindingType.Activating, event -> {
            currentType[0] = "ON_PROXIMITY_ENTER";
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
        });

        page.addEventListener("type-prox-exit", CustomUIEventBindingType.Activating, event -> {
            currentType[0] = "ON_PROXIMITY_EXIT";
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
        });

        page.addEventListener("type-timed", CustomUIEventBindingType.Activating, event -> {
            currentType[0] = "TIMED";
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
        });

        // Slot buttons
        page.addEventListener("slot-0", CustomUIEventBindingType.Activating, event -> {
            animSlot[0] = 0;
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
        });

        page.addEventListener("slot-1", CustomUIEventBindingType.Activating, event -> {
            animSlot[0] = 1;
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
        });

        page.addEventListener("slot-2", CustomUIEventBindingType.Activating, event -> {
            animSlot[0] = 2;
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
        });

        page.addEventListener("slot-3", CustomUIEventBindingType.Activating, event -> {
            animSlot[0] = 3;
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
        });

        page.addEventListener("slot-4", CustomUIEventBindingType.Activating, event -> {
            animSlot[0] = 4;
            AnimationBehavior ab = new AnimationBehavior(currentType[0], animName[0], animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0], stopTimeSeconds[0]);
            openAnimationEditorGUI(playerRef, store, citizen, ab, editIndex);
        });

        // Save
        page.addEventListener("save-anim-btn", CustomUIEventBindingType.Activating, event -> {
            if (animName[0].trim().isEmpty()) {
                playerRef.sendMessage(Message.raw("Please enter an animation name!").color(Color.RED));
                return;
            }

            AnimationBehavior newAb = new AnimationBehavior(
                    currentType[0], animName[0].trim(), animSlot[0], intervalSeconds[0], proximityRange[0],
                    stopAfterTime[0], stopAnimName[0].trim(), stopTimeSeconds[0]);

            List<AnimationBehavior> anims = new ArrayList<>(citizen.getAnimationBehaviors());
            if (editIndex >= 0 && editIndex < anims.size()) {
                anims.set(editIndex, newAb);
            } else {
                anims.add(newAb);
            }
            citizen.setAnimationBehaviors(anims);
            plugin.getCitizensManager().saveCitizen(citizen);

            playerRef.sendMessage(Message.raw(isEditing ? "Animation updated!" : "Animation added!").color(Color.GREEN));
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openBehaviorsGUI(playerRef, store, citizen);
        });
    }

    public void openMessagesGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                @Nonnull CitizenData citizen) {
        MessagesConfig mc = citizen.getMessagesConfig();
        List<CitizenMessage> msgs = mc.getMessages();
        List<IndexedMessage> indexedMsgs = new ArrayList<>();
        for (int i = 0; i < msgs.size(); i++) {
            indexedMsgs.add(new IndexedMessage(i, msgs.get(i)));
        }

        TemplateProcessor template = createBaseTemplate()
                .setVariable("messages", indexedMsgs)
                .setVariable("hasMessages", !msgs.isEmpty())
                .setVariable("messageCount", msgs.size())
                .setVariable("isRandom", "RANDOM".equals(mc.getSelectionMode()))
                .setVariable("isSequential", "SEQUENTIAL".equals(mc.getSelectionMode()))
                .setVariable("isAll", "ALL".equals(mc.getSelectionMode()))
                .setVariable("selectionMode", mc.getSelectionMode())
                .setVariable("enabled", mc.isEnabled());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 900; anchor-height: 720;">

                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Messages</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body">

                            <p class="page-description">Configure messages sent on interaction ({{$messageCount}} messages)</p>
                            <div class="spacer-sm"></div>

                            <!-- Info + Add Section -->
                            <div class="section">
                                {{@sectionHeader:title=Messages}}
                                
                                <div style="layout: center;">
                                    <button id="add-message-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 38;">Add Message</button>
                                </div>
                                
                                <div class="spacer-sm"></div>

                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12;"><span style="color: #58a6ff;">Colors:</span> {RED}, {GREEN}, {BLUE}, {YELLOW}, {#HEX}</p>
                                        <div class="spacer-xs"></div>
                                        <p style="color: #8b949e; font-size: 12;"><span style="color: #58a6ff;">Variables:</span> {PlayerName}, {CitizenName}, {NpcX}, {NpcY}, {NpcZ}</p>
                                        <div class="spacer-xs"></div>
                                        <p style="color: #8b949e; font-size: 12;"><span style="color: #58a6ff;">Rich Text:</span> **bold**, *italic*, [label](https://example.com)</p>
                                        <div class="spacer-xs"></div>
                                        <p style="color: #8b949e; font-size: 12;">Each message can be triggered by <span style="color: #58a6ff;">Left Click</span>, <span style="color: #a371f7;">F Key</span>, or <span style="color: #3fb950;">Both</span>.</p>
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Selection Mode -->
                            <div class="section">
                                {{@sectionHeader:title=Selection Mode,description=How messages are chosen when the citizen is interacted with}}
                                <div class="form-row">
                                    <button id="mode-random" class="{{#if isRandom}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Random</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="mode-sequential" class="{{#if isSequential}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Sequential</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="mode-all" class="{{#if isAll}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">All</button>
                                </div>
                                <div class="spacer-xs"></div>
                                {{#if isRandom}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">A random matching message is picked each interaction.</p>
                                {{/if}}
                                {{#if isSequential}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Matching messages cycle in order for each player.</p>
                                {{/if}}
                                {{#if isAll}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">All matching messages are sent in sequence, with per-message delays applied.</p>
                                {{/if}}
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Messages List -->
                            {{#if hasMessages}}
                            <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="anchor-height: 240;">
                                {{#each messages}}
                                <div class="command-item">
                                    <div class="command-icon command-icon-player">
                                        <p class="command-icon-text command-icon-text-player" style="font-size: 8;">M</p>
                                    </div>
                                    <div class="command-content">
                                        <p class="command-text">{{$truncated}}</p>
                                        <p class="command-type">{{$triggerLabel}}{{#if hasDelay}} | Delay: {{$delaySeconds}}s{{/if}}{{#if hasChanceModifier}} | Chance: {{$chancePercent}}%{{/if}}</p>
                                    </div>
                                    <div class="command-actions">
                                        <button id="edit-msg-{{$index}}" class="secondary-button small-secondary-button">Edit</button>
                                        <div class="spacer-h-sm"></div>
                                        <button id="delete-msg-{{$index}}" class="secondary-button small-secondary-button">Delete</button>
                                    </div>
                                </div>
                                <div class="spacer-sm"></div>
                                {{/each}}
                            </div>
                            {{else}}
                            <div class="empty-state">
                                <div class="empty-state-content">
                                    <p class="empty-state-title">No Messages</p>
                                    <p class="empty-state-description">Click "Add Message" above to create a new message.</p>
                                </div>
                            </div>
                            {{/if}}

                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="done-btn" class="secondary-button" style="anchor-width: 110;">Done</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupMessagesListeners(page, playerRef, store, citizen);

        page.open(store);
    }

    private void setupMessagesListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                        CitizenData citizen) {
        List<CitizenMessage> msgs = new ArrayList<>(citizen.getMessagesConfig().getMessages());

        // "Add Message" opens the edit/add form in add-new mode (editIndex = -1)
        page.addEventListener("add-message-btn", CustomUIEventBindingType.Activating, event ->
                openEditMessageGUI(playerRef, store, citizen,
                        new CitizenMessage("", "BOTH", 0.0f), -1));

        // Selection mode buttons — only affect mode, not interaction component, so use saveCitizen.
        page.addEventListener("mode-random", CustomUIEventBindingType.Activating, event -> {
            MessagesConfig mc = citizen.getMessagesConfig();
            citizen.setMessagesConfig(new MessagesConfig(mc.getMessages(), "RANDOM", mc.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            openMessagesGUI(playerRef, store, citizen);
        });

        page.addEventListener("mode-sequential", CustomUIEventBindingType.Activating, event -> {
            MessagesConfig mc = citizen.getMessagesConfig();
            citizen.setMessagesConfig(new MessagesConfig(mc.getMessages(), "SEQUENTIAL", mc.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            openMessagesGUI(playerRef, store, citizen);
        });

        page.addEventListener("mode-all", CustomUIEventBindingType.Activating, event -> {
            MessagesConfig mc = citizen.getMessagesConfig();
            citizen.setMessagesConfig(new MessagesConfig(mc.getMessages(), "ALL", mc.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            openMessagesGUI(playerRef, store, citizen);
        });

        // Edit and Delete — use updateCitizen so the F-key Interactable component is refreshed.
        for (int i = 0; i < msgs.size(); i++) {
            final int index = i;

            page.addEventListener("edit-msg-" + i, CustomUIEventBindingType.Activating, event ->
                    openEditMessageGUI(playerRef, store, citizen, msgs.get(index), index));

            page.addEventListener("delete-msg-" + i, CustomUIEventBindingType.Activating, event -> {
                msgs.remove(index);
                MessagesConfig mc = citizen.getMessagesConfig();
                citizen.setMessagesConfig(new MessagesConfig(msgs, mc.getSelectionMode(), mc.isEnabled()));
                plugin.getCitizensManager().updateCitizen(citizen, true);
                playerRef.sendMessage(Message.raw("Message removed!").color(Color.GREEN));
                openMessagesGUI(playerRef, store, citizen);
            });
        }

        page.addEventListener("done-btn", CustomUIEventBindingType.Activating, event -> {
            playerRef.sendMessage(Message.raw("Messages saved!").color(Color.GREEN));
            openEditCitizenGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openEditCitizenGUI(playerRef, store, citizen));
    }

    public void openEditMessageGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                   @Nonnull CitizenData citizen, @Nonnull CitizenMessage message, int editIndex) {
        boolean isNew = (editIndex == -1);
        String currentTrigger = message.getInteractionTrigger() != null ? message.getInteractionTrigger() : "BOTH";

        TemplateProcessor template = createBaseTemplate()
                .setVariable("message", escapeHtml(message.getMessage()))
                .setVariable("delaySeconds", message.getDelaySeconds())
                .setVariable("chancePercent", message.getChancePercent())
                .setVariable("isNew", isNew)
                .setVariable("isLeftClick", "LEFT_CLICK".equals(currentTrigger))
                .setVariable("isFKey", "F_KEY".equals(currentTrigger))
                .setVariable("isBoth", "BOTH".equals(currentTrigger));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 680; anchor-height: 760;">

                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">{{#if isNew}}Add Message{{else}}Edit Message{{/if}}</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body">

                            <p class="page-description">{{#if isNew}}Configure a new message to send on interaction{{else}}Modify the message sent on interaction{{/if}}</p>
                            <div class="spacer-sm"></div>

                            <!-- Message Input -->
                            <div class="section">
                                {{@sectionHeader:title=Message Text}}
                                <input type="text" id="message-input" class="form-input" value="{{$message}}"
                                       placeholder="Enter message text with optional color codes..." />
                                <p class="form-hint">Colors: {RED}, {GREEN}, {#HEX}.</p>
                                <div class="spacer-xs"></div>
                                <p class="form-hint">Rich text: **bold**, *italic*, [label](https://example.com).</p>
                                <div class="spacer-xs"></div>
                                <p class="form-hint">Variables: {PlayerName}, {CitizenName}, {NpcX}, {NpcY}, {NpcZ}.</p>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Interaction Trigger -->
                            <div class="section">
                                {{@sectionHeader:title=Interaction Trigger,description=Which player action sends this message}}
                                <div class="form-row">
                                    <button id="trigger-left-click" class="{{#if isLeftClick}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Left Click</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="trigger-f-key" class="{{#if isFKey}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">F Key</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="trigger-both" class="{{#if isBoth}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Both</button>
                                </div>
                                <div class="spacer-xs"></div>
                                {{#if isLeftClick}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Sent when the player left-clicks the NPC.</p>
                                {{/if}}
                                {{#if isFKey}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Sent when the player presses F to interact with the NPC.</p>
                                {{/if}}
                                {{#if isBoth}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">Sent on any interaction (left click or F key).</p>
                                {{/if}}
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Delay -->
                            <div class="section">
                                {{@sectionHeader:title=Delay,description=Wait before sending this message (especially useful in All mode)}}
                                {{@numberField:id=delay-seconds,label=Delay Before Message (seconds),value={{$delaySeconds}},placeholder=0,min=0,max=60,step=0.5,decimals=1,hint=Delay before this message is sent. In All mode messages send one after another with each message's delay.}}
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Chance}}
                                {{@numberField:id=chance-percent,label=Chance %,value={{$chancePercent}},placeholder=100,min=0,max=100,step=1,decimals=1,hint=Percent chance this message is selected when it is otherwise eligible}}
                            </div>

                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-msg-btn" class="secondary-button" style="anchor-width: 200;">{{#if isNew}}Add Message{{else}}Save Changes{{/if}}</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] messageText = {message.getMessage()};
        final float[] delaySeconds = {message.getDelaySeconds()};
        final float[] chancePercent = {message.getChancePercent()};
        final String[] interactionTrigger = {currentTrigger};

        page.addEventListener("message-input", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            messageText[0] = ctx.getValue("message-input", String.class).orElse("");
        });

        page.addEventListener("trigger-left-click", CustomUIEventBindingType.Activating, event -> {
            interactionTrigger[0] = "LEFT_CLICK";
            openEditMessageGUI(playerRef, store, citizen,
                    new CitizenMessage(messageText[0], "LEFT_CLICK", delaySeconds[0], chancePercent[0]), editIndex);
        });

        page.addEventListener("trigger-f-key", CustomUIEventBindingType.Activating, event -> {
            interactionTrigger[0] = "F_KEY";
            openEditMessageGUI(playerRef, store, citizen,
                    new CitizenMessage(messageText[0], "F_KEY", delaySeconds[0], chancePercent[0]), editIndex);
        });

        page.addEventListener("trigger-both", CustomUIEventBindingType.Activating, event -> {
            interactionTrigger[0] = "BOTH";
            openEditMessageGUI(playerRef, store, citizen,
                    new CitizenMessage(messageText[0], "BOTH", delaySeconds[0], chancePercent[0]), editIndex);
        });

        page.addEventListener("delay-seconds", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("delay-seconds", Double.class)
                    .ifPresent(val -> delaySeconds[0] = val.floatValue());

            if (delaySeconds[0] == 0.0f) {
                ctx.getValue("delay-seconds", String.class)
                        .ifPresent(val -> {
                            try {
                                delaySeconds[0] = Float.parseFloat(val);
                            } catch (NumberFormatException ignored) {
                            }
                        });
            }
        });

        page.addEventListener("chance-percent", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("chance-percent", Double.class).ifPresent(val -> {
                chancePercent[0] = Math.max(0.0f, Math.min(100.0f, val.floatValue()));
            });
        });

        page.addEventListener("save-msg-btn", CustomUIEventBindingType.Activating, event -> {
            if (messageText[0].trim().isEmpty()) {
                playerRef.sendMessage(Message.raw("Message cannot be empty!").color(Color.RED));
                return;
            }

            CitizenMessage saved = new CitizenMessage(messageText[0].trim(), interactionTrigger[0], delaySeconds[0], chancePercent[0]);
            List<CitizenMessage> msgs = new ArrayList<>(citizen.getMessagesConfig().getMessages());
            MessagesConfig mc = citizen.getMessagesConfig();

            if (isNew) {
                msgs.add(saved);
                playerRef.sendMessage(Message.raw("Message added!").color(Color.GREEN));
            } else {
                msgs.set(editIndex, saved);
                playerRef.sendMessage(Message.raw("Message updated!").color(Color.GREEN));
            }

            citizen.setMessagesConfig(new MessagesConfig(msgs, mc.getSelectionMode(), mc.isEnabled()));
            // Use updateCitizen so the F-key Interactable component is refreshed on the live NPC.
            plugin.getCitizensManager().updateCitizen(citizen, true);
            openMessagesGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openMessagesGUI(playerRef, store, citizen));

        page.open(store);
    }

    public void openFirstInteractionConfigGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                              @Nonnull CitizenData citizen) {
        MessagesConfig firstMsgConfig = citizen.getFirstInteractionMessagesConfig();
        String commandMode = "ALL".equalsIgnoreCase(citizen.getFirstInteractionCommandSelectionMode()) ? "ALL" : "RANDOM";
        if (!commandMode.equalsIgnoreCase(citizen.getFirstInteractionCommandSelectionMode())) {
            citizen.setFirstInteractionCommandSelectionMode(commandMode);
            plugin.getCitizensManager().saveCitizen(citizen);
        }
        String messageMode = "ALL".equalsIgnoreCase(firstMsgConfig.getSelectionMode()) ? "ALL" : "RANDOM";

        TemplateProcessor template = createBaseTemplate()
                .setVariable("enabled", citizen.isFirstInteractionEnabled())
                .setVariable("runNormalOnFirst", citizen.isRunNormalOnFirstInteraction())
                .setVariable("cmdModeAll", "ALL".equalsIgnoreCase(commandMode))
                .setVariable("cmdModeRandom", "RANDOM".equalsIgnoreCase(commandMode))
                .setVariable("msgModeAll", "ALL".equalsIgnoreCase(messageMode))
                .setVariable("msgModeRandom", "RANDOM".equalsIgnoreCase(messageMode))
                .setVariable("firstCommandCount", citizen.getFirstInteractionCommandActions().size())
                .setVariable("firstMessageCount", firstMsgConfig.getMessages().size())
                .setVariable("currentCommandMode", commandMode)
                .setVariable("currentMessageMode", messageMode)
                .setVariable("completedCount", citizen.getPlayersWhoCompletedFirstInteraction().size());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 900; anchor-height: 900;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">First Interaction Config</p>
                            </div>
                        </div>
                        <div class="body">
                            <p class="page-description">Configure one-time actions for each player the first time they interact</p>
                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Enable First Interaction}}
                                <div style="layout: center;">
                                    <button id="toggle-first-enabled" class="secondary-button" style="anchor-width: 220; anchor-height: 40;">{{#if enabled}}Enabled{{else}}Disabled{{/if}}</button>
                                </div>
                                <div class="spacer-xs"></div>
                                <p class="form-hint" style="text-align: center;">When enabled, first-time actions run once per player.</p>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Normal Actions On First Interaction}}
                                <div style="layout: center;">
                                    <button id="toggle-run-normal-first-btn" class="secondary-button" style="anchor-width: 320; anchor-height: 40;">{{#if runNormalOnFirst}}Enabled{{else}}Disabled{{/if}}</button>
                                </div>
                                <div class="spacer-xs"></div>
                                <p class="form-hint" style="text-align: center;">When enabled, normal commands/messages also run during the first interaction.</p>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=First Commands ({{$firstCommandCount}})}}
                                <p class="form-hint" style="text-align: center;">Current mode: {{$currentCommandMode}}</p>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <button id="first-cmd-mode-all" class="{{#if cmdModeAll}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">All</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="first-cmd-mode-random" class="{{#if cmdModeRandom}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Random</button>
                                </div>
                                <div class="spacer-sm"></div>
                                <button id="edit-first-commands-btn" class="secondary-button" style="anchor-width: 260; anchor-height: 40;">Edit First Commands</button>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=First Messages ({{$firstMessageCount}})}}
                                <p class="form-hint" style="text-align: center;">Current mode: {{$currentMessageMode}}</p>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <button id="first-msg-mode-all" class="{{#if msgModeAll}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">All</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="first-msg-mode-random" class="{{#if msgModeRandom}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Random</button>
                                </div>
                                <div class="spacer-sm"></div>
                                <button id="edit-first-messages-btn" class="secondary-button" style="anchor-width: 260; anchor-height: 40;">Edit First Messages</button>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Completion Tracking}}
                                <p class="form-hint" style="text-align: center;">Players completed first interaction: {{$completedCount}}</p>
                                <div class="spacer-xs"></div>
                                <button id="reset-first-completed-btn" class="secondary-button" style="anchor-width: 320; anchor-height: 40;">Reset Completed Player List</button>
                            </div>
                        </div>
                        <div class="footer">
                            <button id="back-btn" class="secondary-button">Back</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        page.addEventListener("toggle-first-enabled", CustomUIEventBindingType.Activating, event -> {
            citizen.setFirstInteractionEnabled(!citizen.isFirstInteractionEnabled());
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionConfigGUI(playerRef, store, citizen);
        });

        page.addEventListener("toggle-run-normal-first-btn", CustomUIEventBindingType.Activating, event -> {
            citizen.setRunNormalOnFirstInteraction(!citizen.isRunNormalOnFirstInteraction());
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionConfigGUI(playerRef, store, citizen);
        });

        page.addEventListener("first-cmd-mode-all", CustomUIEventBindingType.Activating, event -> {
            citizen.setFirstInteractionCommandSelectionMode("ALL");
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionConfigGUI(playerRef, store, citizen);
        });
        page.addEventListener("first-cmd-mode-random", CustomUIEventBindingType.Activating, event -> {
            citizen.setFirstInteractionCommandSelectionMode("RANDOM");
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionConfigGUI(playerRef, store, citizen);
        });

        page.addEventListener("first-msg-mode-all", CustomUIEventBindingType.Activating, event -> {
            MessagesConfig cfg = citizen.getFirstInteractionMessagesConfig();
            citizen.setFirstInteractionMessagesConfig(new MessagesConfig(cfg.getMessages(), "ALL", cfg.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionConfigGUI(playerRef, store, citizen);
        });
        page.addEventListener("first-msg-mode-random", CustomUIEventBindingType.Activating, event -> {
            MessagesConfig cfg = citizen.getFirstInteractionMessagesConfig();
            citizen.setFirstInteractionMessagesConfig(new MessagesConfig(cfg.getMessages(), "RANDOM", cfg.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionConfigGUI(playerRef, store, citizen);
        });

        page.addEventListener("edit-first-commands-btn", CustomUIEventBindingType.Activating, event ->
                openFirstInteractionCommandsGUI(playerRef, store, citizen));
        page.addEventListener("edit-first-messages-btn", CustomUIEventBindingType.Activating, event ->
                openFirstInteractionMessagesGUI(playerRef, store, citizen));
        page.addEventListener("reset-first-completed-btn", CustomUIEventBindingType.Activating, event -> {
            citizen.setPlayersWhoCompletedFirstInteraction(Collections.emptySet());
            plugin.getCitizensManager().saveCitizen(citizen);
            playerRef.sendMessage(Message.raw("First interaction completion list reset.").color(Color.GREEN));
            openFirstInteractionConfigGUI(playerRef, store, citizen);
        });
        page.addEventListener("back-btn", CustomUIEventBindingType.Activating, event ->
                openEditCitizenGUI(playerRef, store, citizen));

        page.open(store);
    }

    public void openFirstInteractionCommandsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                                @Nonnull CitizenData citizen) {
        List<CommandAction> actions = new ArrayList<>(citizen.getFirstInteractionCommandActions());
        List<IndexedCommandAction> indexedActions = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            indexedActions.add(new IndexedCommandAction(i, actions.get(i)));
        }

        String commandMode = "ALL".equalsIgnoreCase(citizen.getFirstInteractionCommandSelectionMode()) ? "ALL" : "RANDOM";
        if (!commandMode.equalsIgnoreCase(citizen.getFirstInteractionCommandSelectionMode())) {
            citizen.setFirstInteractionCommandSelectionMode(commandMode);
            plugin.getCitizensManager().saveCitizen(citizen);
        }
        TemplateProcessor template = createBaseTemplate()
                .setVariable("actions", indexedActions)
                .setVariable("hasActions", !actions.isEmpty())
                .setVariable("actionCount", actions.size())
                .setVariable("modeAll", "ALL".equalsIgnoreCase(commandMode))
                .setVariable("modeRandom", "RANDOM".equalsIgnoreCase(commandMode))
                .setVariable("currentMode", commandMode);

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 900; anchor-height: 900;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">First Interaction Commands</p>
                            </div>
                        </div>
                        <div class="body">
                            <p class="page-description">Commands that only run on a player's first interaction ({{$actionCount}} commands)</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                <button id="add-command-btn" class="secondary-button" style="anchor-width: 220; anchor-height: 38;">Add Command</button>
                                <div class="spacer-sm"></div>
                                <p class="form-hint">Variables: {PlayerName}, {CitizenName}, {NpcX}, {NpcY}, {NpcZ}</p>
                            </div>
                            <div class="spacer-md"></div>
                            <div class="section">
                                {{@sectionHeader:title=Selection Mode}}
                                <p class="form-hint" style="text-align: center;">Current mode: {{$currentMode}}</p>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <button id="mode-all" class="{{#if modeAll}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">All</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="mode-random" class="{{#if modeRandom}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Random</button>
                                </div>
                            </div>
                            <div class="spacer-md"></div>
                            {{#if hasActions}}
                            <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="anchor-height: 340;">
                                {{#each actions}}
                                <div class="command-item">
                                    <div class="command-icon {{#if runAsServer}}command-icon-server{{else}}command-icon-player{{/if}}">
                                        <p class="command-icon-text {{#if runAsServer}}command-icon-text-server{{else}}command-icon-text-player{{/if}}">{{#if runAsServer}}S{{else}}P{{/if}}</p>
                                    </div>
                                    <div class="command-content">
                                        <p class="command-text">/{{$command}}</p>
                                        <p class="command-type">{{$triggerLabel}}{{#if hasDelay}} | Delay: {{$delaySeconds}}s{{/if}}{{#if hasChanceModifier}} | Chance: {{$chancePercent}}%{{/if}}</p>
                                    </div>
                                    <div class="command-actions">
                                        <button id="edit-cmd-{{$index}}" class="secondary-button small-secondary-button">Edit</button>
                                        <div class="spacer-h-sm"></div>
                                        <button id="delete-cmd-{{$index}}" class="secondary-button small-secondary-button">Delete</button>
                                    </div>
                                </div>
                                <div class="spacer-sm"></div>
                                {{/each}}
                            </div>
                            {{else}}
                            <div class="empty-state">
                                <div class="empty-state-content">
                                    <p class="empty-state-title">No First Commands</p>
                                    <p class="empty-state-description">Add one or more commands for first-time interactions.</p>
                                </div>
                            </div>
                            {{/if}}
                        </div>
                        <div class="footer">
                            <button id="back-btn" class="secondary-button">Back</button>
                            <div class="spacer-h-md"></div>
                            <button id="done-btn" class="secondary-button">Done</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        page.addEventListener("mode-all", CustomUIEventBindingType.Activating, event -> {
            citizen.setFirstInteractionCommandSelectionMode("ALL");
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionCommandsGUI(playerRef, store, citizen);
        });
        page.addEventListener("mode-random", CustomUIEventBindingType.Activating, event -> {
            citizen.setFirstInteractionCommandSelectionMode("RANDOM");
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionCommandsGUI(playerRef, store, citizen);
        });
        page.addEventListener("add-command-btn", CustomUIEventBindingType.Activating, event -> {
            openEditFirstInteractionCommandGUI(playerRef, store, citizen, actions,
                    new CommandAction("", false, 0.0f, "BOTH", 100.0f), -1);
        });

        for (int i = 0; i < actions.size(); i++) {
            final int index = i;
            page.addEventListener("edit-cmd-" + i, CustomUIEventBindingType.Activating, event -> {
                openEditFirstInteractionCommandGUI(playerRef, store, citizen, actions, actions.get(index), index);
            });
            page.addEventListener("delete-cmd-" + i, CustomUIEventBindingType.Activating, event -> {
                actions.remove(index);
                citizen.setFirstInteractionCommandActions(actions);
                plugin.getCitizensManager().saveCitizen(citizen);
                openFirstInteractionCommandsGUI(playerRef, store, citizen);
            });
        }

        page.addEventListener("done-btn", CustomUIEventBindingType.Activating, event -> {
            citizen.setFirstInteractionCommandActions(actions);
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionConfigGUI(playerRef, store, citizen);
        });
        page.addEventListener("back-btn", CustomUIEventBindingType.Activating, event ->
                openFirstInteractionConfigGUI(playerRef, store, citizen));

        page.open(store);
    }

    public void openEditFirstInteractionCommandGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                                   @Nonnull CitizenData citizen, @Nonnull List<CommandAction> actions,
                                                   @Nonnull CommandAction command, int editIndex) {
        boolean isNew = (editIndex == -1);
        String currentTrigger = command.getInteractionTrigger() != null ? command.getInteractionTrigger() : "BOTH";

        TemplateProcessor template = createBaseTemplate()
                .setVariable("command", escapeHtml(command.getCommand()))
                .setVariable("runAsServer", command.isRunAsServer())
                .setVariable("delaySeconds", command.getDelaySeconds())
                .setVariable("chancePercent", command.getChancePercent())
                .setVariable("isNew", isNew)
                .setVariable("isLeftClick", "LEFT_CLICK".equals(currentTrigger))
                .setVariable("isFKey", "F_KEY".equals(currentTrigger))
                .setVariable("isBoth", "BOTH".equals(currentTrigger));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 680; anchor-height: 780;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">{{#if isNew}}Add First Command{{else}}Edit First Command{{/if}}</p>
                            </div>
                        </div>
                        <div class="body">
                            <p class="page-description">Configure a command that only runs on first interaction</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                {{@sectionHeader:title=Command}}
                                <input type="text" id="command-input" class="form-input" value="{{$command}}" placeholder="give {PlayerName} Rock_Gem_Diamond" />
                                <p class="form-hint">Variables: {PlayerName}, {CitizenName}, {NpcX}, {NpcY}, {NpcZ}</p>
                            </div>
                            <div class="spacer-md"></div>
                            <div class="section">
                                {{@sectionHeader:title=Interaction Trigger}}
                                <div class="form-row">
                                    <button id="trigger-left-click" class="{{#if isLeftClick}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Left Click</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="trigger-f-key" class="{{#if isFKey}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">F Key</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="trigger-both" class="{{#if isBoth}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Both</button>
                                </div>
                            </div>
                            <div class="spacer-md"></div>
                            <div class="section">
                                {{@sectionHeader:title=Delay}}
                                {{@numberField:id=delay-seconds,label=Delay Before Command (seconds),value={{$delaySeconds}},placeholder=0,min=0,max=300,step=0.5,decimals=1}}
                            </div>
                            <div class="spacer-md"></div>
                            <div class="section">
                                {{@sectionHeader:title=Chance}}
                                {{@numberField:id=chance-percent,label=Chance %,value={{$chancePercent}},placeholder=100,min=0,max=100,step=1,decimals=1}}
                            </div>
                            <div class="spacer-md"></div>
                            <div class="section">
                                <div class="checkbox-row">
                                    <input type="checkbox" id="run-as-server" {{#if runAsServer}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0;">
                                        <p class="checkbox-label">Run as Server</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 200;">{{#if isNew}}Add Command{{else}}Save Changes{{/if}}</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] commandText = {command.getCommand()};
        final boolean[] runAsServer = {command.isRunAsServer()};
        final float[] delaySeconds = {command.getDelaySeconds()};
        final float[] chancePercent = {command.getChancePercent()};
        final String[] interactionTrigger = {currentTrigger};

        page.addEventListener("command-input", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            commandText[0] = ctx.getValue("command-input", String.class).orElse("");
        });
        page.addEventListener("trigger-left-click", CustomUIEventBindingType.Activating, event ->
                openEditFirstInteractionCommandGUI(playerRef, store, citizen, actions,
                        new CommandAction(commandText[0], runAsServer[0], delaySeconds[0], "LEFT_CLICK", chancePercent[0]), editIndex));
        page.addEventListener("trigger-f-key", CustomUIEventBindingType.Activating, event ->
                openEditFirstInteractionCommandGUI(playerRef, store, citizen, actions,
                        new CommandAction(commandText[0], runAsServer[0], delaySeconds[0], "F_KEY", chancePercent[0]), editIndex));
        page.addEventListener("trigger-both", CustomUIEventBindingType.Activating, event ->
                openEditFirstInteractionCommandGUI(playerRef, store, citizen, actions,
                        new CommandAction(commandText[0], runAsServer[0], delaySeconds[0], "BOTH", chancePercent[0]), editIndex));
        page.addEventListener("delay-seconds", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                ctx.getValue("delay-seconds", Double.class).ifPresent(v -> delaySeconds[0] = v.floatValue()));
        page.addEventListener("chance-percent", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                ctx.getValue("chance-percent", Double.class).ifPresent(v -> chancePercent[0] = Math.max(0.0f, Math.min(100.0f, v.floatValue()))));
        page.addEventListener("run-as-server", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                runAsServer[0] = ctx.getValue("run-as-server", Boolean.class).orElse(false));

        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, event -> {
            String cmd = commandText[0].trim();
            if (cmd.isEmpty()) {
                playerRef.sendMessage(Message.raw("Command cannot be empty!").color(Color.RED));
                return;
            }
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }

            CommandAction saved = new CommandAction(cmd, runAsServer[0], delaySeconds[0], interactionTrigger[0], chancePercent[0]);
            if (isNew) {
                actions.add(saved);
            } else {
                actions.set(editIndex, saved);
            }

            citizen.setFirstInteractionCommandActions(actions);
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionCommandsGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openFirstInteractionCommandsGUI(playerRef, store, citizen));

        page.open(store);
    }

    public void openFirstInteractionMessagesGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                                @Nonnull CitizenData citizen) {
        MessagesConfig loadedConfig = citizen.getFirstInteractionMessagesConfig();
        String effectiveMode = "ALL".equalsIgnoreCase(loadedConfig.getSelectionMode()) ? "ALL" : "RANDOM";
        if (!effectiveMode.equalsIgnoreCase(loadedConfig.getSelectionMode())) {
            loadedConfig = new MessagesConfig(loadedConfig.getMessages(), effectiveMode, loadedConfig.isEnabled());
            citizen.setFirstInteractionMessagesConfig(loadedConfig);
            plugin.getCitizensManager().saveCitizen(citizen);
        }
        final MessagesConfig currentConfig = loadedConfig;
        List<CitizenMessage> messages = new ArrayList<>(currentConfig.getMessages());
        List<IndexedMessage> indexed = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            indexed.add(new IndexedMessage(i, messages.get(i)));
        }

        TemplateProcessor template = createBaseTemplate()
                .setVariable("messages", indexed)
                .setVariable("hasMessages", !messages.isEmpty())
                .setVariable("messageCount", messages.size())
                .setVariable("currentMode", effectiveMode)
                .setVariable("isAll", "ALL".equalsIgnoreCase(effectiveMode))
                .setVariable("isRandom", "RANDOM".equalsIgnoreCase(effectiveMode));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 900; anchor-height: 780;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">First Interaction Messages</p>
                            </div>
                        </div>
                        <div class="body">
                            <p class="page-description">Messages that only run on a player's first interaction ({{$messageCount}} messages)</p>
                            <div class="spacer-sm"></div>
                            <button id="add-message-btn" class="secondary-button" style="anchor-width: 220; anchor-height: 38;">Add Message</button>
                            <div class="spacer-sm"></div>
                            <p class="form-hint">Supports {RED}/{#HEX}, **bold**, *italic*, [label](https://example.com), {PlayerName}, {CitizenName}, {NpcX}, {NpcY}, {NpcZ}</p>
                            <div class="spacer-md"></div>
                            <p class="form-hint" style="text-align: center;">Current mode: {{$currentMode}}</p>
                            <div class="spacer-xs"></div>
                            <div class="form-row">
                                <button id="mode-all" class="{{#if isAll}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">All</button>
                                <div class="spacer-h-sm"></div>
                                <button id="mode-random" class="{{#if isRandom}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Random</button>
                            </div>
                            <div class="spacer-md"></div>
                            {{#if hasMessages}}
                            <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="anchor-height: 360;">
                                {{#each messages}}
                                <div class="command-item">
                                    <div class="command-icon command-icon-player">
                                        <p class="command-icon-text command-icon-text-player" style="font-size: 8;">M</p>
                                    </div>
                                    <div class="command-content">
                                        <p class="command-text">{{$truncated}}</p>
                                        <p class="command-type">{{$triggerLabel}}{{#if hasDelay}} | Delay: {{$delaySeconds}}s{{/if}}{{#if hasChanceModifier}} | Chance: {{$chancePercent}}%{{/if}}</p>
                                    </div>
                                    <div class="command-actions">
                                        <button id="edit-msg-{{$index}}" class="secondary-button small-secondary-button">Edit</button>
                                        <div class="spacer-h-sm"></div>
                                        <button id="delete-msg-{{$index}}" class="secondary-button small-secondary-button">Delete</button>
                                    </div>
                                </div>
                                <div class="spacer-sm"></div>
                                {{/each}}
                            </div>
                            {{else}}
                            <div class="empty-state">
                                <div class="empty-state-content">
                                    <p class="empty-state-title">No First Messages</p>
                                    <p class="empty-state-description">Add one or more messages for first-time interactions.</p>
                                </div>
                            </div>
                            {{/if}}
                        </div>
                        <div class="footer">
                            <button id="back-btn" class="secondary-button">Back</button>
                            <div class="spacer-h-md"></div>
                            <button id="done-btn" class="secondary-button">Done</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        page.addEventListener("mode-all", CustomUIEventBindingType.Activating, event -> {
            citizen.setFirstInteractionMessagesConfig(new MessagesConfig(messages, "ALL", currentConfig.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionMessagesGUI(playerRef, store, citizen);
        });
        page.addEventListener("mode-random", CustomUIEventBindingType.Activating, event -> {
            citizen.setFirstInteractionMessagesConfig(new MessagesConfig(messages, "RANDOM", currentConfig.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionMessagesGUI(playerRef, store, citizen);
        });
        page.addEventListener("add-message-btn", CustomUIEventBindingType.Activating, event -> {
            openEditFirstInteractionMessageGUI(playerRef, store, citizen, messages,
                    new CitizenMessage("", "BOTH", 0.0f, 100.0f), -1);
        });

        for (int i = 0; i < messages.size(); i++) {
            final int index = i;
            page.addEventListener("edit-msg-" + i, CustomUIEventBindingType.Activating, event ->
                    openEditFirstInteractionMessageGUI(playerRef, store, citizen, messages, messages.get(index), index));
            page.addEventListener("delete-msg-" + i, CustomUIEventBindingType.Activating, event -> {
                messages.remove(index);
                citizen.setFirstInteractionMessagesConfig(new MessagesConfig(messages, effectiveMode, currentConfig.isEnabled()));
                plugin.getCitizensManager().saveCitizen(citizen);
                openFirstInteractionMessagesGUI(playerRef, store, citizen);
            });
        }

        page.addEventListener("done-btn", CustomUIEventBindingType.Activating, event -> {
            citizen.setFirstInteractionMessagesConfig(new MessagesConfig(messages, effectiveMode, currentConfig.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionConfigGUI(playerRef, store, citizen);
        });
        page.addEventListener("back-btn", CustomUIEventBindingType.Activating, event ->
                openFirstInteractionConfigGUI(playerRef, store, citizen));

        page.open(store);
    }

    public void openEditFirstInteractionMessageGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                                   @Nonnull CitizenData citizen, @Nonnull List<CitizenMessage> messages,
                                                   @Nonnull CitizenMessage message, int editIndex) {
        boolean isNew = (editIndex == -1);
        String currentTrigger = message.getInteractionTrigger() != null ? message.getInteractionTrigger() : "BOTH";

        TemplateProcessor template = createBaseTemplate()
                .setVariable("message", escapeHtml(message.getMessage()))
                .setVariable("delaySeconds", message.getDelaySeconds())
                .setVariable("chancePercent", message.getChancePercent())
                .setVariable("isNew", isNew)
                .setVariable("isLeftClick", "LEFT_CLICK".equals(currentTrigger))
                .setVariable("isFKey", "F_KEY".equals(currentTrigger))
                .setVariable("isBoth", "BOTH".equals(currentTrigger));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 680; anchor-height: 760;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">{{#if isNew}}Add First Message{{else}}Edit First Message{{/if}}</p>
                            </div>
                        </div>
                        <div class="body">
                            <p class="page-description">Configure a message that only runs on first interaction</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                {{@sectionHeader:title=Message Text}}
                                <input type="text" id="message-input" class="form-input" value="{{$message}}" placeholder="Enter message..." />
                                <p class="form-hint">Colors: {COLOR}, {RED}, {BLUE}, {#HEX}, {#FFA500}, etc.</p>
                                <div class="spacer-xs"></div>
                                <p class="form-hint">Rich text: **bold**, *italic*, [label](https://example.com).</p>
                                <div class="spacer-xs"></div>
                                <p class="form-hint">Variables: {PlayerName}, {CitizenName}, {NpcX}, {NpcY}, {NpcZ}.</p>
                            </div>
                            <div class="spacer-md"></div>
                            <div class="section">
                                {{@sectionHeader:title=Interaction Trigger}}
                                <div class="form-row">
                                    <button id="trigger-left-click" class="{{#if isLeftClick}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Left Click</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="trigger-f-key" class="{{#if isFKey}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">F Key</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="trigger-both" class="{{#if isBoth}}secondary-button{{else}}secondary-button{{/if}}" style="flex-weight: 1; anchor-height: 38;">Both</button>
                                </div>
                            </div>
                            <div class="spacer-md"></div>
                            <div class="section">
                                {{@sectionHeader:title=Delay}}
                                {{@numberField:id=delay-seconds,label=Delay Before Message (seconds),value={{$delaySeconds}},placeholder=0,min=0,max=60,step=0.5,decimals=1}}
                            </div>
                            <div class="spacer-md"></div>
                            <div class="section">
                                {{@sectionHeader:title=Chance}}
                                {{@numberField:id=chance-percent,label=Chance %,value={{$chancePercent}},placeholder=100,min=0,max=100,step=1,decimals=1}}
                            </div>
                        </div>
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 200;">{{#if isNew}}Add Message{{else}}Save Changes{{/if}}</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] messageText = {message.getMessage()};
        final float[] delaySeconds = {message.getDelaySeconds()};
        final float[] chancePercent = {message.getChancePercent()};
        final String[] interactionTrigger = {currentTrigger};

        page.addEventListener("message-input", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                messageText[0] = ctx.getValue("message-input", String.class).orElse(""));
        page.addEventListener("trigger-left-click", CustomUIEventBindingType.Activating, event ->
                openEditFirstInteractionMessageGUI(playerRef, store, citizen, messages,
                        new CitizenMessage(messageText[0], "LEFT_CLICK", delaySeconds[0], chancePercent[0]), editIndex));
        page.addEventListener("trigger-f-key", CustomUIEventBindingType.Activating, event ->
                openEditFirstInteractionMessageGUI(playerRef, store, citizen, messages,
                        new CitizenMessage(messageText[0], "F_KEY", delaySeconds[0], chancePercent[0]), editIndex));
        page.addEventListener("trigger-both", CustomUIEventBindingType.Activating, event ->
                openEditFirstInteractionMessageGUI(playerRef, store, citizen, messages,
                        new CitizenMessage(messageText[0], "BOTH", delaySeconds[0], chancePercent[0]), editIndex));
        page.addEventListener("delay-seconds", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                ctx.getValue("delay-seconds", Double.class).ifPresent(v -> delaySeconds[0] = v.floatValue()));
        page.addEventListener("chance-percent", CustomUIEventBindingType.ValueChanged, (event, ctx) ->
                ctx.getValue("chance-percent", Double.class).ifPresent(v -> chancePercent[0] = Math.max(0.0f, Math.min(100.0f, v.floatValue()))));

        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, event -> {
            String text = messageText[0].trim();
            if (text.isEmpty()) {
                playerRef.sendMessage(Message.raw("Message cannot be empty!").color(Color.RED));
                return;
            }

            CitizenMessage saved = new CitizenMessage(text, interactionTrigger[0], delaySeconds[0], chancePercent[0]);
            if (isNew) {
                messages.add(saved);
            } else {
                messages.set(editIndex, saved);
            }

            MessagesConfig cfg = citizen.getFirstInteractionMessagesConfig();
            citizen.setFirstInteractionMessagesConfig(new MessagesConfig(messages, cfg.getSelectionMode(), cfg.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            openFirstInteractionMessagesGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openFirstInteractionMessagesGUI(playerRef, store, citizen));

        page.open(store);
    }

    public void openCombatConfigGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                     @Nonnull CitizenData citizen) {
        CombatConfig cc = citizen.getCombatConfig();

        TemplateProcessor template = createBaseTemplate()
                .setVariable("attackType", cc.getAttackType())
                .setVariable("attackDistance", cc.getAttackDistance())
                .setVariable("chaseSpeed", cc.getChaseSpeed())
                .setVariable("combatBehaviorDistance", cc.getCombatBehaviorDistance())
                .setVariable("combatStrafeWeight", cc.getCombatStrafeWeight())
                .setVariable("combatDirectWeight", cc.getCombatDirectWeight())
                .setVariable("backOffAfterAttack", cc.isBackOffAfterAttack())
                .setVariable("backOffDistance", cc.getBackOffDistance())
                .setVariable("desiredAttackDistanceMin", cc.getDesiredAttackDistanceMin())
                .setVariable("desiredAttackDistanceMax", cc.getDesiredAttackDistanceMax())
                .setVariable("attackPauseMin", cc.getAttackPauseMin())
                .setVariable("attackPauseMax", cc.getAttackPauseMax())
                .setVariable("combatRelativeTurnSpeed", cc.getCombatRelativeTurnSpeed())
                .setVariable("combatAlwaysMovingWeight", cc.getCombatAlwaysMovingWeight())
                .setVariable("combatStrafingDurationMin", cc.getCombatStrafingDurationMin())
                .setVariable("combatStrafingDurationMax", cc.getCombatStrafingDurationMax())
                .setVariable("combatStrafingFrequencyMin", cc.getCombatStrafingFrequencyMin())
                .setVariable("combatStrafingFrequencyMax", cc.getCombatStrafingFrequencyMax())
                .setVariable("combatAttackPreDelayMin", cc.getCombatAttackPreDelayMin())
                .setVariable("combatAttackPreDelayMax", cc.getCombatAttackPreDelayMax())
                .setVariable("combatAttackPostDelayMin", cc.getCombatAttackPostDelayMin())
                .setVariable("combatAttackPostDelayMax", cc.getCombatAttackPostDelayMax())
                .setVariable("backOffDurationMin", cc.getBackOffDurationMin())
                .setVariable("backOffDurationMax", cc.getBackOffDurationMax())
                .setVariable("blockAbility", cc.getBlockAbility())
                .setVariable("blockProbability", cc.getBlockProbability())
                .setVariable("combatFleeIfTooCloseDistance", cc.getCombatFleeIfTooCloseDistance())
                .setVariable("targetSwitchTimerMin", cc.getTargetSwitchTimerMin())
                .setVariable("targetSwitchTimerMax", cc.getTargetSwitchTimerMax())
                .setVariable("targetRange", cc.getTargetRange())
                .setVariable("combatMovingRelativeSpeed", cc.getCombatMovingRelativeSpeed())
                .setVariable("combatBackwardsRelativeSpeed", cc.getCombatBackwardsRelativeSpeed())
                .setVariable("useCombatActionEvaluator", cc.isUseCombatActionEvaluator());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 850; anchor-height: 900;">

                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Combat Configuration</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">

                            <p class="page-description">Advanced combat parameters for this citizen</p>
                            <div class="spacer-sm"></div>
                            <div>
                                <p class="form-hint" style="text-align: center; color: #f85149;">Warning: Hytale has minimum and maximum values for all of these options. If you change something and the citizen disappears,</p>
                            </div>
                            <div>
                                <p class="form-hint" style="text-align: center; color: #f85149;">check the console to see what the min and max value is.</p>
                            </div>

                            <!-- Attack Settings -->
                            <div class="section">
                                {{@sectionHeader:title=Attack Settings,description=Configure attack type, distance, and timing}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@formField:id=attack-type,label=Attack Type,value={{$attackType}},placeholder=Root_NPC_Attack_Melee,hint=Attack interaction ID}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 0; anchor-width: 160;">
                                        <button id="auto-resolve-btn" class="secondary-button" style="anchor-width: 150; anchor-height: 38;">Auto Resolve</button>
                                    </div>
                                </div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=attack-distance,label=Attack Distance,value={{$attackDistance}},placeholder=2.0,min=0,max=50,step=0.5,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=chase-speed,label=Chase Speed,value={{$chaseSpeed}},placeholder=0.67,min=0,max=5,step=0.01,decimals=2}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=target-range,label=Target Range,value={{$targetRange}},placeholder=4.0,min=0,max=100,step=0.5,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=desired-attack-dist-min,label=Desired Attack Dist Min,value={{$desiredAttackDistanceMin}},placeholder=1.5,min=0,max=50,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=desired-attack-dist-max,label=Desired Attack Dist Max,value={{$desiredAttackDistanceMax}},placeholder=1.5,min=0,max=50,step=0.1,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=attack-pause-min,label=Attack Pause Min,value={{$attackPauseMin}},placeholder=1.5,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=attack-pause-max,label=Attack Pause Max,value={{$attackPauseMax}},placeholder=2.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=attack-pre-delay-min,label=Pre-Delay Min,value={{$combatAttackPreDelayMin}},placeholder=0.2,min=0,max=10,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=attack-pre-delay-max,label=Pre-Delay Max,value={{$combatAttackPreDelayMax}},placeholder=0.2,min=0,max=10,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=attack-post-delay-min,label=Post-Delay Min,value={{$combatAttackPostDelayMin}},placeholder=0.2,min=0,max=10,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=attack-post-delay-max,label=Post-Delay Max,value={{$combatAttackPostDelayMax}},placeholder=0.2,min=0,max=10,step=0.1,decimals=1}}
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Combat Movement -->
                            <div class="section">
                                {{@sectionHeader:title=Combat Movement,description=Movement behavior during combat}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=combat-behavior-distance,label=Behavior Distance,value={{$combatBehaviorDistance}},placeholder=5.0,min=0,max=100,step=0.5,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=combat-turn-speed,label=Turn Speed,value={{$combatRelativeTurnSpeed}},placeholder=1.5,min=0,max=10,step=0.1,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=combat-direct-weight,label=Direct Weight,value={{$combatDirectWeight}},placeholder=10,min=0,max=100,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=combat-strafe-weight,label=Strafe Weight,value={{$combatStrafeWeight}},placeholder=10,min=0,max=100,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=combat-always-moving-weight,label=Always Moving,value={{$combatAlwaysMovingWeight}},placeholder=0,min=0,max=100,step=1,decimals=0}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=strafe-duration-min,label=Strafe Dur Min,value={{$combatStrafingDurationMin}},placeholder=1.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=strafe-duration-max,label=Strafe Dur Max,value={{$combatStrafingDurationMax}},placeholder=1.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=strafe-freq-min,label=Strafe Freq Min,value={{$combatStrafingFrequencyMin}},placeholder=2.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=strafe-freq-max,label=Strafe Freq Max,value={{$combatStrafingFrequencyMax}},placeholder=2.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=combat-moving-speed,label=Moving Speed,value={{$combatMovingRelativeSpeed}},placeholder=0.6,min=0,max=5,step=0.05,decimals=2}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=combat-backwards-speed,label=Backwards Speed,value={{$combatBackwardsRelativeSpeed}},placeholder=0.3,min=0,max=5,step=0.05,decimals=2}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=flee-too-close,label=Flee If Close,value={{$combatFleeIfTooCloseDistance}},placeholder=0,min=0,max=50,step=0.5,decimals=1}}
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Back Off & Blocking -->
                            <div class="section">
                                {{@sectionHeader:title=Back Off & Blocking,description=Retreat and blocking behavior}}
                                <div class="checkbox-row">
                                    <input type="checkbox" id="back-off-toggle" {{#if backOffAfterAttack}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0;">
                                        <p class="checkbox-label">Back Off After Attack</p>
                                        <p class="checkbox-description">NPC retreats after attacking</p>
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=back-off-distance,label=Back Off Distance,value={{$backOffDistance}},placeholder=4.0,min=0,max=50,step=0.5,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=back-off-dur-min,label=Back Off Dur Min,value={{$backOffDurationMin}},placeholder=2.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=back-off-dur-max,label=Back Off Dur Max,value={{$backOffDurationMax}},placeholder=3.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@formField:id=block-ability,label=Block Ability,value={{$blockAbility}},placeholder=Shield_Block,hint=Ability used for blocking}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=block-probability,label=Block Probability %,value={{$blockProbability}},placeholder=50,min=0,max=100,step=1,decimals=0}}
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Target Switching -->
                            <div class="section">
                                {{@sectionHeader:title=Target & Evaluator,description=Target switching and combat action evaluator}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=target-switch-min,label=Switch Timer Min,value={{$targetSwitchTimerMin}},placeholder=5.0,min=0,max=60,step=0.5,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=target-switch-max,label=Switch Timer Max,value={{$targetSwitchTimerMax}},placeholder=5.0,min=0,max=60,step=0.5,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="checkbox-row">
                                    <input type="checkbox" id="use-combat-evaluator" {{#if useCombatActionEvaluator}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0;">
                                        <p class="checkbox-label">Use Combat Action Evaluator</p>
                                        <p class="checkbox-description">Enable advanced combat action evaluation</p>
                                    </div>
                                </div>
                            </div>

                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Back</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 200;">Save Combat Config</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupCombatConfigListeners(page, playerRef, store, citizen);

        page.open(store);
    }

    private void setupCombatConfigListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                            CitizenData citizen) {
        CombatConfig cc = citizen.getCombatConfig();

        final String[] attackType = {cc.getAttackType()};
        final float[] attackDistance = {cc.getAttackDistance()};
        final float[] chaseSpeed = {cc.getChaseSpeed()};
        final float[] combatBehaviorDistance = {cc.getCombatBehaviorDistance()};
        final int[] combatStrafeWeight = {cc.getCombatStrafeWeight()};
        final int[] combatDirectWeight = {cc.getCombatDirectWeight()};
        final boolean[] backOffAfterAttack = {cc.isBackOffAfterAttack()};
        final float[] backOffDistance = {cc.getBackOffDistance()};
        final float[] desiredAttackDistMin = {cc.getDesiredAttackDistanceMin()};
        final float[] desiredAttackDistMax = {cc.getDesiredAttackDistanceMax()};
        final float[] attackPauseMin = {cc.getAttackPauseMin()};
        final float[] attackPauseMax = {cc.getAttackPauseMax()};
        final float[] combatTurnSpeed = {cc.getCombatRelativeTurnSpeed()};
        final int[] combatAlwaysMoving = {cc.getCombatAlwaysMovingWeight()};
        final float[] strafeDurMin = {cc.getCombatStrafingDurationMin()};
        final float[] strafeDurMax = {cc.getCombatStrafingDurationMax()};
        final float[] strafeFreqMin = {cc.getCombatStrafingFrequencyMin()};
        final float[] strafeFreqMax = {cc.getCombatStrafingFrequencyMax()};
        final float[] preDelayMin = {cc.getCombatAttackPreDelayMin()};
        final float[] preDelayMax = {cc.getCombatAttackPreDelayMax()};
        final float[] postDelayMin = {cc.getCombatAttackPostDelayMin()};
        final float[] postDelayMax = {cc.getCombatAttackPostDelayMax()};
        final float[] backOffDurMin = {cc.getBackOffDurationMin()};
        final float[] backOffDurMax = {cc.getBackOffDurationMax()};
        final String[] blockAbility = {cc.getBlockAbility()};
        final int[] blockProbability = {cc.getBlockProbability()};
        final float[] fleeTooClose = {cc.getCombatFleeIfTooCloseDistance()};
        final float[] targetSwitchMin = {cc.getTargetSwitchTimerMin()};
        final float[] targetSwitchMax = {cc.getTargetSwitchTimerMax()};
        final float[] targetRange = {cc.getTargetRange()};
        final float[] movingSpeed = {cc.getCombatMovingRelativeSpeed()};
        final float[] backwardsSpeed = {cc.getCombatBackwardsRelativeSpeed()};
        final boolean[] useCombatEvaluator = {cc.isUseCombatActionEvaluator()};

        // Text fields
        page.addEventListener("attack-type", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            attackType[0] = ctx.getValue("attack-type", String.class).orElse("Root_NPC_Attack_Melee");
        });
        page.addEventListener("block-ability", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            blockAbility[0] = ctx.getValue("block-ability", String.class).orElse("Shield_Block");
        });

        // Auto-resolve attack type
        page.addEventListener("auto-resolve-btn", CustomUIEventBindingType.Activating, event -> {
            plugin.getCitizensManager().autoResolveAttackType(citizen);
            openCombatConfigGUI(playerRef, store, citizen);
        });

        // Number fields
        page.addEventListener("attack-distance", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("attack-distance", Double.class).ifPresent(v -> attackDistance[0] = v.floatValue());
        });
        page.addEventListener("chase-speed", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("chase-speed", Double.class).ifPresent(v -> chaseSpeed[0] = v.floatValue());
        });
        page.addEventListener("target-range", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("target-range", Double.class).ifPresent(v -> targetRange[0] = v.floatValue());
        });
        page.addEventListener("combat-behavior-distance", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("combat-behavior-distance", Double.class).ifPresent(v -> combatBehaviorDistance[0] = v.floatValue());
        });
        page.addEventListener("combat-turn-speed", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("combat-turn-speed", Double.class).ifPresent(v -> combatTurnSpeed[0] = v.floatValue());
        });
        page.addEventListener("combat-direct-weight", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("combat-direct-weight", Double.class).ifPresent(v -> combatDirectWeight[0] = v.intValue());
        });
        page.addEventListener("combat-strafe-weight", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("combat-strafe-weight", Double.class).ifPresent(v -> combatStrafeWeight[0] = v.intValue());
        });
        page.addEventListener("combat-always-moving-weight", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("combat-always-moving-weight", Double.class).ifPresent(v -> combatAlwaysMoving[0] = v.intValue());
        });
        page.addEventListener("desired-attack-dist-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("desired-attack-dist-min", Double.class).ifPresent(v -> desiredAttackDistMin[0] = v.floatValue());
        });
        page.addEventListener("desired-attack-dist-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("desired-attack-dist-max", Double.class).ifPresent(v -> desiredAttackDistMax[0] = v.floatValue());
        });
        page.addEventListener("attack-pause-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("attack-pause-min", Double.class).ifPresent(v -> attackPauseMin[0] = v.floatValue());
        });
        page.addEventListener("attack-pause-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("attack-pause-max", Double.class).ifPresent(v -> attackPauseMax[0] = v.floatValue());
        });
        page.addEventListener("attack-pre-delay-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("attack-pre-delay-min", Double.class).ifPresent(v -> preDelayMin[0] = v.floatValue());
        });
        page.addEventListener("attack-pre-delay-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("attack-pre-delay-max", Double.class).ifPresent(v -> preDelayMax[0] = v.floatValue());
        });
        page.addEventListener("attack-post-delay-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("attack-post-delay-min", Double.class).ifPresent(v -> postDelayMin[0] = v.floatValue());
        });
        page.addEventListener("attack-post-delay-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("attack-post-delay-max", Double.class).ifPresent(v -> postDelayMax[0] = v.floatValue());
        });
        page.addEventListener("strafe-duration-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("strafe-duration-min", Double.class).ifPresent(v -> strafeDurMin[0] = v.floatValue());
        });
        page.addEventListener("strafe-duration-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("strafe-duration-max", Double.class).ifPresent(v -> strafeDurMax[0] = v.floatValue());
        });
        page.addEventListener("strafe-freq-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("strafe-freq-min", Double.class).ifPresent(v -> strafeFreqMin[0] = v.floatValue());
        });
        page.addEventListener("strafe-freq-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("strafe-freq-max", Double.class).ifPresent(v -> strafeFreqMax[0] = v.floatValue());
        });
        page.addEventListener("combat-moving-speed", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("combat-moving-speed", Double.class).ifPresent(v -> movingSpeed[0] = v.floatValue());
        });
        page.addEventListener("combat-backwards-speed", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("combat-backwards-speed", Double.class).ifPresent(v -> backwardsSpeed[0] = v.floatValue());
        });
        page.addEventListener("flee-too-close", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("flee-too-close", Double.class).ifPresent(v -> fleeTooClose[0] = v.floatValue());
        });
        page.addEventListener("back-off-distance", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("back-off-distance", Double.class).ifPresent(v -> backOffDistance[0] = v.floatValue());
        });
        page.addEventListener("back-off-dur-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("back-off-dur-min", Double.class).ifPresent(v -> backOffDurMin[0] = v.floatValue());
        });
        page.addEventListener("back-off-dur-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("back-off-dur-max", Double.class).ifPresent(v -> backOffDurMax[0] = v.floatValue());
        });
        page.addEventListener("block-probability", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("block-probability", Double.class).ifPresent(v -> blockProbability[0] = v.intValue());
        });
        page.addEventListener("target-switch-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("target-switch-min", Double.class).ifPresent(v -> targetSwitchMin[0] = v.floatValue());
        });
        page.addEventListener("target-switch-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("target-switch-max", Double.class).ifPresent(v -> targetSwitchMax[0] = v.floatValue());
        });

        // Checkboxes
        page.addEventListener("back-off-toggle", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("back-off-toggle", Boolean.class).ifPresent(v -> backOffAfterAttack[0] = v);
        });
        page.addEventListener("use-combat-evaluator", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("use-combat-evaluator", Boolean.class).ifPresent(v -> useCombatEvaluator[0] = v);
        });

        // Save
        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, (event, ctx) -> {
            // Ensure latest checkbox states are captured at save time.
            ctx.getValue("back-off-toggle", Boolean.class).ifPresent(v -> backOffAfterAttack[0] = v);
            ctx.getValue("use-combat-evaluator", Boolean.class).ifPresent(v -> useCombatEvaluator[0] = v);

            cc.setAttackType(attackType[0]);
            cc.setAttackDistance(attackDistance[0]);
            cc.setChaseSpeed(chaseSpeed[0]);
            cc.setCombatBehaviorDistance(combatBehaviorDistance[0]);
            cc.setCombatStrafeWeight(combatStrafeWeight[0]);
            cc.setCombatDirectWeight(combatDirectWeight[0]);
            cc.setBackOffAfterAttack(backOffAfterAttack[0]);
            cc.setBackOffDistance(backOffDistance[0]);
            cc.setDesiredAttackDistanceMin(desiredAttackDistMin[0]);
            cc.setDesiredAttackDistanceMax(desiredAttackDistMax[0]);
            cc.setAttackPauseMin(attackPauseMin[0]);
            cc.setAttackPauseMax(attackPauseMax[0]);
            cc.setCombatRelativeTurnSpeed(combatTurnSpeed[0]);
            cc.setCombatAlwaysMovingWeight(combatAlwaysMoving[0]);
            cc.setCombatStrafingDurationMin(strafeDurMin[0]);
            cc.setCombatStrafingDurationMax(strafeDurMax[0]);
            cc.setCombatStrafingFrequencyMin(strafeFreqMin[0]);
            cc.setCombatStrafingFrequencyMax(strafeFreqMax[0]);
            cc.setCombatAttackPreDelayMin(preDelayMin[0]);
            cc.setCombatAttackPreDelayMax(preDelayMax[0]);
            cc.setCombatAttackPostDelayMin(postDelayMin[0]);
            cc.setCombatAttackPostDelayMax(postDelayMax[0]);
            cc.setBackOffDurationMin(backOffDurMin[0]);
            cc.setBackOffDurationMax(backOffDurMax[0]);
            cc.setBlockAbility(blockAbility[0]);
            cc.setBlockProbability(blockProbability[0]);
            cc.setCombatFleeIfTooCloseDistance(fleeTooClose[0]);
            cc.setTargetSwitchTimerMin(targetSwitchMin[0]);
            cc.setTargetSwitchTimerMax(targetSwitchMax[0]);
            cc.setTargetRange(targetRange[0]);
            cc.setCombatMovingRelativeSpeed(movingSpeed[0]);
            cc.setCombatBackwardsRelativeSpeed(backwardsSpeed[0]);
            cc.setUseCombatActionEvaluator(useCombatEvaluator[0]);
            plugin.getCitizensManager().saveCitizen(citizen);
            playerRef.sendMessage(Message.raw("Combat config saved!").color(Color.GREEN));
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openBehaviorsGUI(playerRef, store, citizen);
        });
    }

    public void openDetectionConfigGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                        @Nonnull CitizenData citizen) {
        DetectionConfig dc = citizen.getDetectionConfig();

        TemplateProcessor template = createBaseTemplate()
                .setVariable("viewRange", dc.getViewRange())
                .setVariable("viewSector", dc.getViewSector())
                .setVariable("hearingRange", dc.getHearingRange())
                .setVariable("absoluteDetectionRange", dc.getAbsoluteDetectionRange())
                .setVariable("alertedRange", dc.getAlertedRange())
                .setVariable("alertedTimeMin", dc.getAlertedTimeMin())
                .setVariable("alertedTimeMax", dc.getAlertedTimeMax())
                .setVariable("chanceCallForHelp", dc.getChanceToBeAlertedWhenReceivingCallForHelp())
                .setVariable("confusedTimeMin", dc.getConfusedTimeMin())
                .setVariable("confusedTimeMax", dc.getConfusedTimeMax())
                .setVariable("searchTimeMin", dc.getSearchTimeMin())
                .setVariable("searchTimeMax", dc.getSearchTimeMax())
                .setVariable("investigateRange", dc.getInvestigateRange());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 800; anchor-height: 800;">

                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Detection Configuration</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">

                            <p class="page-description">How this citizen detects and responds to threats</p>
                            <div class="spacer-sm"></div>
                            <div>
                                <p class="form-hint" style="text-align: center; color: #f85149;">Warning: Hytale has minimum and maximum values for all of these options. If you change something and the citizen disappears,</p>
                            </div>
                            <div>
                                <p class="form-hint" style="text-align: center; color: #f85149;">check the console to see what the min and max value is.</p>
                            </div>

                            <!-- Primary Detection -->
                            <div class="section">
                                {{@sectionHeader:title=Primary Detection,description=Vision and hearing ranges}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=view-range,label=View Range,value={{$viewRange}},placeholder=15,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=view-sector,label=View Sector (degrees),value={{$viewSector}},placeholder=180,min=0,max=360,step=5,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=hearing-range,label=Hearing Range,value={{$hearingRange}},placeholder=8,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=absolute-detection,label=Absolute Detection Range,value={{$absoluteDetectionRange}},placeholder=2,min=0,max=100,step=0.5,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=investigate-range,label=Investigate Range,value={{$investigateRange}},placeholder=40,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Alert Settings -->
                            <div class="section">
                                {{@sectionHeader:title=Alert Settings,description=How the citizen reacts when alerted}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=alerted-range,label=Alerted Range,value={{$alertedRange}},placeholder=45,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=alerted-time-min,label=Alerted Time Min,value={{$alertedTimeMin}},placeholder=1.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=alerted-time-max,label=Alerted Time Max,value={{$alertedTimeMax}},placeholder=2.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div style="layout: center; flex-weight: 0;">
                                    <div style="anchor-width: 350; flex-weight: 0;">
                                        {{@numberField:id=chance-call-help,label=Call For Help Chance %,value={{$chanceCallForHelp}},placeholder=70,min=0,max=100,step=1,decimals=0}}
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Search & Confusion -->
                            <div class="section">
                                {{@sectionHeader:title=Search & Confusion,description=Behavior when target is lost}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=confused-time-min,label=Confused Time Min,value={{$confusedTimeMin}},placeholder=1.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=confused-time-max,label=Confused Time Max,value={{$confusedTimeMax}},placeholder=2.0,min=0,max=30,step=0.1,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=search-time-min,label=Search Time Min,value={{$searchTimeMin}},placeholder=10.0,min=0,max=120,step=0.5,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=search-time-max,label=Search Time Max,value={{$searchTimeMax}},placeholder=14.0,min=0,max=120,step=0.5,decimals=1}}
                                    </div>
                                </div>
                            </div>
                            <div class="spacer-md"></div>

                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Back</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 220;">Save Detection Config</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupDetectionConfigListeners(page, playerRef, store, citizen);

        page.open(store);
    }

    private void setupDetectionConfigListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                               CitizenData citizen) {
        DetectionConfig dc = citizen.getDetectionConfig();

        final float[] viewRange = {dc.getViewRange()};
        final float[] viewSector = {dc.getViewSector()};
        final float[] hearingRange = {dc.getHearingRange()};
        final float[] absoluteDetection = {dc.getAbsoluteDetectionRange()};
        final float[] alertedRange = {dc.getAlertedRange()};
        final float[] alertedTimeMin = {dc.getAlertedTimeMin()};
        final float[] alertedTimeMax = {dc.getAlertedTimeMax()};
        final int[] chanceCallHelp = {dc.getChanceToBeAlertedWhenReceivingCallForHelp()};
        final float[] confusedTimeMin = {dc.getConfusedTimeMin()};
        final float[] confusedTimeMax = {dc.getConfusedTimeMax()};
        final float[] searchTimeMin = {dc.getSearchTimeMin()};
        final float[] searchTimeMax = {dc.getSearchTimeMax()};
        final float[] investigateRange = {dc.getInvestigateRange()};

        page.addEventListener("view-range", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("view-range", Double.class).ifPresent(v -> viewRange[0] = v.floatValue());
        });
        page.addEventListener("view-sector", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("view-sector", Double.class).ifPresent(v -> viewSector[0] = v.floatValue());
        });
        page.addEventListener("hearing-range", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("hearing-range", Double.class).ifPresent(v -> hearingRange[0] = v.floatValue());
        });
        page.addEventListener("absolute-detection", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("absolute-detection", Double.class).ifPresent(v -> absoluteDetection[0] = v.floatValue());
        });
        page.addEventListener("investigate-range", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("investigate-range", Double.class).ifPresent(v -> investigateRange[0] = v.floatValue());
        });
        page.addEventListener("alerted-range", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("alerted-range", Double.class).ifPresent(v -> alertedRange[0] = v.floatValue());
        });
        page.addEventListener("alerted-time-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("alerted-time-min", Double.class).ifPresent(v -> alertedTimeMin[0] = v.floatValue());
        });
        page.addEventListener("alerted-time-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("alerted-time-max", Double.class).ifPresent(v -> alertedTimeMax[0] = v.floatValue());
        });
        page.addEventListener("chance-call-help", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("chance-call-help", Double.class).ifPresent(v -> chanceCallHelp[0] = v.intValue());
        });
        page.addEventListener("confused-time-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("confused-time-min", Double.class).ifPresent(v -> confusedTimeMin[0] = v.floatValue());
        });
        page.addEventListener("confused-time-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("confused-time-max", Double.class).ifPresent(v -> confusedTimeMax[0] = v.floatValue());
        });
        page.addEventListener("search-time-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("search-time-min", Double.class).ifPresent(v -> searchTimeMin[0] = v.floatValue());
        });
        page.addEventListener("search-time-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("search-time-max", Double.class).ifPresent(v -> searchTimeMax[0] = v.floatValue());
        });

        // Save
        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, event -> {
            dc.setViewRange(viewRange[0]);
            dc.setViewSector(viewSector[0]);
            dc.setHearingRange(hearingRange[0]);
            dc.setAbsoluteDetectionRange(absoluteDetection[0]);
            dc.setAlertedRange(alertedRange[0]);
            dc.setAlertedTimeMin(alertedTimeMin[0]);
            dc.setAlertedTimeMax(alertedTimeMax[0]);
            dc.setChanceToBeAlertedWhenReceivingCallForHelp(chanceCallHelp[0]);
            dc.setConfusedTimeMin(confusedTimeMin[0]);
            dc.setConfusedTimeMax(confusedTimeMax[0]);
            dc.setSearchTimeMin(searchTimeMin[0]);
            dc.setSearchTimeMax(searchTimeMax[0]);
            dc.setInvestigateRange(investigateRange[0]);
            plugin.getCitizensManager().saveCitizen(citizen);
            playerRef.sendMessage(Message.raw("Detection config saved!").color(Color.GREEN));
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openBehaviorsGUI(playerRef, store, citizen);
        });
    }

    public void openAdvancedSettingsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                        @Nonnull CitizenData citizen) {
        TemplateProcessor template = createBaseTemplate()
                .setVariable("dropList", escapeHtml(citizen.getDropList()))
                .setVariable("runThreshold", citizen.getRunThreshold())
                .setVariable("nameTranslationKey", escapeHtml(citizen.getNameTranslationKey()))
                .setVariable("attitudeGroup", escapeHtml(citizen.getAttitudeGroup()))
                .setVariable("breathesInWater", citizen.isBreathesInWater())
                .setVariable("dayFlavorAnimation", escapeHtml(citizen.getDayFlavorAnimation()))
                .setVariable("dayFlavorAnimLengthMin", citizen.getDayFlavorAnimationLengthMin())
                .setVariable("dayFlavorAnimLengthMax", citizen.getDayFlavorAnimationLengthMax())
                .setVariable("wakingIdleBehavior", escapeHtml(citizen.getWakingIdleBehaviorComponent()))
                .setVariable("defaultHotbarSlot", citizen.getDefaultHotbarSlot())
                .setVariable("randomIdleHotbarSlot", citizen.getRandomIdleHotbarSlot())
                .setVariable("chanceEquipIdle", citizen.getChanceToEquipFromIdleHotbarSlot())
                .setVariable("defaultOffHandSlot", citizen.getDefaultOffHandSlot())
                .setVariable("nighttimeOffhandSlot", citizen.getNighttimeOffhandSlot())
                .setVariable("knockbackScale", citizen.getKnockbackScale())
                .setVariable("leashDistance", citizen.getLeashDistance())
                .setVariable("leashMinPlayerDistance", citizen.getLeashMinPlayerDistance())
                .setVariable("leashTimerMin", citizen.getLeashTimerMin())
                .setVariable("leashTimerMax", citizen.getLeashTimerMax())
                .setVariable("hardLeashDistance", citizen.getHardLeashDistance())
//                .setVariable("weapons", escapeHtml(String.join(", ", citizen.getWeapons())))
//                .setVariable("offHandItems", escapeHtml(String.join(", ", citizen.getOffHandItems())))
                .setVariable("combatMessageTargetGroups", escapeHtml(String.join(", ", citizen.getCombatMessageTargetGroups())))
                .setVariable("flockArray", escapeHtml(String.join(", ", citizen.getFlockArray())))
                .setVariable("disableDamageGroups", escapeHtml(String.join(", ", citizen.getDisableDamageGroups())));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 850; anchor-height: 900;">

                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Advanced Settings</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">
                            <div>
                                <p class="form-hint" style="text-align: center; color: #f85149;">Warning: Hytale has minimum and maximum values for all of these options. If you change something and the citizen disappears,</p>
                            </div>
                            <div>
                                <p class="form-hint" style="text-align: center; color: #f85149;">check the console to see what the min and max value is.</p>
                            </div>

                            <!-- General -->
                            <div class="section">
                                {{@sectionHeader:title=General,description=Core identity and behavior settings}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@formField:id=name-translation-key,label=Name Translation Key,value={{$nameTranslationKey}},placeholder=Citizen}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@formField:id=attitude-group,label=Attitude Group,value={{$attitudeGroup}},placeholder=Empty}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@formField:id=drop-list,label=Drop List,value={{$dropList}},placeholder=Empty,hint=Loot table reference}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=run-threshold,label=Run Threshold,value={{$runThreshold}},placeholder=0.3,min=0,max=1,step=0.05,decimals=2}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=knockback-scale,label=Knockback Scale,value={{$knockbackScale}},placeholder=0.5,min=0,max=5,step=0.1,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="checkbox-row">
                                    <input type="checkbox" id="breathes-in-water" {{#if breathesInWater}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0;">
                                        <p class="checkbox-label">Breathes In Water</p>
                                        <p class="checkbox-description">Whether this NPC can breathe underwater</p>
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Animations -->
                            <div class="section">
                                {{@sectionHeader:title=Idle Behavior,description=Idle and flavor animation settings}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@formField:id=waking-idle-behavior,label=Waking Idle Component,value={{$wakingIdleBehavior}},placeholder=Component_Instruction_Waking_Idle}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@formField:id=day-flavor-anim,label=Day Flavor Animation,value={{$dayFlavorAnimation}},placeholder=Leave empty for none}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=day-flavor-len-min,label=Flavor Anim Length Min,value={{$dayFlavorAnimLengthMin}},placeholder=3.0,min=0,max=120,step=0.5,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=day-flavor-len-max,label=Flavor Anim Length Max,value={{$dayFlavorAnimLengthMax}},placeholder=5.0,min=0,max=120,step=0.5,decimals=1}}
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Hotbar & Equipment -->
                            <div class="section">
                                {{@sectionHeader:title=Hotbar & Equipment,description=Default equipment slot configuration}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=default-hotbar,label=Default Hotbar Slot,value={{$defaultHotbarSlot}},placeholder=0,min=-1,max=8,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=random-idle-hotbar,label=Idle Hotbar Slot,value={{$randomIdleHotbarSlot}},placeholder=-1,min=-1,max=8,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=chance-equip-idle,label=Equip Idle Chance %,value={{$chanceEquipIdle}},placeholder=5,min=0,max=100,step=1,decimals=0}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=default-offhand,label=Default OffHand Slot,value={{$defaultOffHandSlot}},placeholder=-1,min=-1,max=8,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=nighttime-offhand,label=Nighttime OffHand Slot,value={{$nighttimeOffhandSlot}},placeholder=0,min=-1,max=8,step=1,decimals=0}}
                                    </div>
                                </div>
                                <!--
                                <div class="spacer-xs"></div>
                                {{@formField:id=weapons,label=Weapons (Hotbar Items),value={{$weapons}},placeholder=Weapon_Sword_Iron,hint=Comma-separated list of weapon/item identifiers}}
                                <div class="spacer-xs"></div>
                                {{@formField:id=offhand-items,label=OffHand Items,value={{$offHandItems}},placeholder=Furniture_Crude_Torch,hint=Comma-separated list of offhand item identifiers}}
                                -->
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Leash Settings,description=Control distance and leash timing behavior}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=leash-distance,label=Leash Distance,value={{$leashDistance}},placeholder=45,min=1,max=1000,step=1,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=leash-min-player-distance,label=Leash Min Player Distance,value={{$leashMinPlayerDistance}},placeholder=4,min=0,max=500,step=0.5,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=hard-leash-distance,label=Hard Leash Distance,value={{$hardLeashDistance}},placeholder=200,min=1,max=5000,step=1,decimals=1}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=leash-timer-min,label=Leash Timer Min,value={{$leashTimerMin}},placeholder=3,min=0,max=600,step=0.5,decimals=1}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=leash-timer-max,label=Leash Timer Max,value={{$leashTimerMax}},placeholder=5,min=0,max=600,step=0.5,decimals=1}}
                                    </div>
                                </div>
                            </div>

                            <!-- Groups & Arrays -->
                            <div class="section">
                                {{@sectionHeader:title=Groups & Arrays,description=Comma-separated lists for group memberships}}
                                {{@formField:id=combat-msg-groups,label=Combat Message Target Groups,value={{$combatMessageTargetGroups}},placeholder=Comma-separated group names,hint=Groups notified during combat}}
                                <div class="spacer-xs"></div>
                                {{@formField:id=flock-array,label=Flock Array,value={{$flockArray}},placeholder=Comma-separated flock entries,hint=Flocking behavior groups}}
                                <div class="spacer-xs"></div>
                                {{@formField:id=disable-damage-groups,label=Disable Damage Groups,value={{$disableDamageGroups}},placeholder=Self,hint=Groups this NPC cannot damage}}
                            </div>

                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Back</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 220;">Save Advanced Settings</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupAdvancedSettingsListeners(page, playerRef, store, citizen);

        page.open(store);
    }

    private void setupAdvancedSettingsListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                                CitizenData citizen) {
        final String[] dropList = {citizen.getDropList()};
        final float[] runThreshold = {citizen.getRunThreshold()};
        final String[] nameTransKey = {citizen.getNameTranslationKey()};
        final String[] attitudeGroup = {citizen.getAttitudeGroup()};
        final boolean[] breathesInWater = {citizen.isBreathesInWater()};
        final String[] dayFlavorAnim = {citizen.getDayFlavorAnimation()};
        final float[] dayFlavorLenMin = {citizen.getDayFlavorAnimationLengthMin()};
        final float[] dayFlavorLenMax = {citizen.getDayFlavorAnimationLengthMax()};
        final String[] wakingIdleBehavior = {citizen.getWakingIdleBehaviorComponent()};
        final int[] defaultHotbar = {citizen.getDefaultHotbarSlot()};
        final int[] randomIdleHotbar = {citizen.getRandomIdleHotbarSlot()};
        final int[] chanceEquipIdle = {citizen.getChanceToEquipFromIdleHotbarSlot()};
        final int[] defaultOffHand = {citizen.getDefaultOffHandSlot()};
        final int[] nighttimeOffhand = {citizen.getNighttimeOffhandSlot()};
        final float[] knockbackScale = {citizen.getKnockbackScale()};
        final float[] leashDistance = {citizen.getLeashDistance()};
        final float[] leashMinPlayerDistance = {citizen.getLeashMinPlayerDistance()};
        final float[] leashTimerMin = {citizen.getLeashTimerMin()};
        final float[] leashTimerMax = {citizen.getLeashTimerMax()};
        final float[] hardLeashDistance = {citizen.getHardLeashDistance()};
//        final String[] weaponsStr = {String.join(", ", citizen.getWeapons())};
//        final String[] offHandItemsStr = {String.join(", ", citizen.getOffHandItems())};
        final String[] combatMsgGroups = {String.join(", ", citizen.getCombatMessageTargetGroups())};
        final String[] flockArray = {String.join(", ", citizen.getFlockArray())};
        final String[] disableDmgGroups = {String.join(", ", citizen.getDisableDamageGroups())};

        // Text fields
        page.addEventListener("drop-list", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            dropList[0] = ctx.getValue("drop-list", String.class).orElse("Empty");
        });
        page.addEventListener("name-translation-key", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            nameTransKey[0] = ctx.getValue("name-translation-key", String.class).orElse("Citizen");
        });
        page.addEventListener("attitude-group", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            attitudeGroup[0] = ctx.getValue("attitude-group", String.class).orElse("Empty");
        });
        page.addEventListener("day-flavor-anim", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            dayFlavorAnim[0] = ctx.getValue("day-flavor-anim", String.class).orElse("");
        });
        page.addEventListener("waking-idle-behavior", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            wakingIdleBehavior[0] = ctx.getValue("waking-idle-behavior", String.class).orElse("Component_Instruction_Waking_Idle");
        });
        page.addEventListener("combat-msg-groups", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            combatMsgGroups[0] = ctx.getValue("combat-msg-groups", String.class).orElse("");
        });
        page.addEventListener("flock-array", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            flockArray[0] = ctx.getValue("flock-array", String.class).orElse("");
        });
//        page.addEventListener("weapons", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
//            weaponsStr[0] = ctx.getValue("weapons", String.class).orElse("Weapon_Sword_Iron");
//        });
//        page.addEventListener("offhand-items", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
//            offHandItemsStr[0] = ctx.getValue("offhand-items", String.class).orElse("Furniture_Crude_Torch");
//        });
        page.addEventListener("disable-damage-groups", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            disableDmgGroups[0] = ctx.getValue("disable-damage-groups", String.class).orElse("Self");
        });

        // Number fields
        page.addEventListener("run-threshold", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("run-threshold", Double.class).ifPresent(v -> runThreshold[0] = v.floatValue());
        });
        page.addEventListener("day-flavor-len-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("day-flavor-len-min", Double.class).ifPresent(v -> dayFlavorLenMin[0] = v.floatValue());
        });
        page.addEventListener("day-flavor-len-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("day-flavor-len-max", Double.class).ifPresent(v -> dayFlavorLenMax[0] = v.floatValue());
        });
        page.addEventListener("default-hotbar", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("default-hotbar", Double.class).ifPresent(v -> defaultHotbar[0] = v.intValue());
        });
        page.addEventListener("random-idle-hotbar", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("random-idle-hotbar", Double.class).ifPresent(v -> randomIdleHotbar[0] = v.intValue());
        });
        page.addEventListener("chance-equip-idle", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("chance-equip-idle", Double.class).ifPresent(v -> chanceEquipIdle[0] = v.intValue());
        });
        page.addEventListener("default-offhand", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("default-offhand", Double.class).ifPresent(v -> defaultOffHand[0] = v.intValue());
        });
        page.addEventListener("nighttime-offhand", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("nighttime-offhand", Double.class).ifPresent(v -> nighttimeOffhand[0] = v.intValue());
        });

        page.addEventListener("knockback-scale", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("knockback-scale", Double.class).ifPresent(v -> knockbackScale[0] = v.floatValue());
        });
        page.addEventListener("leash-distance", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("leash-distance", Double.class).ifPresent(v -> leashDistance[0] = v.floatValue());
        });
        page.addEventListener("leash-min-player-distance", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("leash-min-player-distance", Double.class).ifPresent(v -> leashMinPlayerDistance[0] = v.floatValue());
        });
        page.addEventListener("leash-timer-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("leash-timer-min", Double.class).ifPresent(v -> leashTimerMin[0] = v.floatValue());
        });
        page.addEventListener("leash-timer-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("leash-timer-max", Double.class).ifPresent(v -> leashTimerMax[0] = v.floatValue());
        });
        page.addEventListener("hard-leash-distance", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("hard-leash-distance", Double.class).ifPresent(v -> hardLeashDistance[0] = v.floatValue());
        });

        // Checkbox
        page.addEventListener("breathes-in-water", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("breathes-in-water", Boolean.class).ifPresent(v -> breathesInWater[0] = v);
        });

        // Save
        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, (event, ctx) -> {
            // Ensure latest checkbox state is captured at save time.
            ctx.getValue("breathes-in-water", Boolean.class).ifPresent(v -> breathesInWater[0] = v);

            citizen.setDropList(dropList[0]);
            citizen.setRunThreshold(runThreshold[0]);
            citizen.setNameTranslationKey(nameTransKey[0]);
            citizen.setAttitudeGroup(attitudeGroup[0]);
            citizen.setBreathesInWater(breathesInWater[0]);
            citizen.setDayFlavorAnimation(dayFlavorAnim[0]);
            citizen.setDayFlavorAnimationLengthMin(dayFlavorLenMin[0]);
            citizen.setDayFlavorAnimationLengthMax(dayFlavorLenMax[0]);
            citizen.setWakingIdleBehaviorComponent(wakingIdleBehavior[0]);
            citizen.setDefaultHotbarSlot(defaultHotbar[0]);
            citizen.setRandomIdleHotbarSlot(randomIdleHotbar[0]);
            citizen.setChanceToEquipFromIdleHotbarSlot(chanceEquipIdle[0]);
            citizen.setDefaultOffHandSlot(defaultOffHand[0]);
            citizen.setNighttimeOffhandSlot(nighttimeOffhand[0]);

            citizen.setKnockbackScale(knockbackScale[0]);
            citizen.setLeashDistance(leashDistance[0]);
            citizen.setLeashMinPlayerDistance(leashMinPlayerDistance[0]);
            citizen.setLeashTimerMin(leashTimerMin[0]);
            citizen.setLeashTimerMax(leashTimerMax[0]);
            citizen.setHardLeashDistance(hardLeashDistance[0]);

            // Parse comma-separated lists
//            citizen.setWeapons(parseCommaSeparatedList(weaponsStr[0]));
//            citizen.setOffHandItems(parseCommaSeparatedList(offHandItemsStr[0]));
            citizen.setCombatMessageTargetGroups(parseCommaSeparatedList(combatMsgGroups[0]));
            citizen.setFlockArray(parseCommaSeparatedList(flockArray[0]));
            citizen.setDisableDamageGroups(parseCommaSeparatedList(disableDmgGroups[0]));

            plugin.getCitizensManager().saveCitizen(citizen);
            playerRef.sendMessage(Message.raw("Advanced settings saved!").color(Color.GREEN));
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openBehaviorsGUI(playerRef, store, citizen);
        });
    }

    private List<String> parseCommaSeparatedList(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String item : input.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    @Nonnull
    private List<CommandAction> copyCommandActions(@Nonnull List<CommandAction> source) {
        List<CommandAction> copy = new ArrayList<>(source.size());
        for (CommandAction action : source) {
            copy.add(new CommandAction(
                    action.getCommand(),
                    action.isRunAsServer(),
                    action.getDelaySeconds(),
                    action.getInteractionTrigger(),
                    action.getChancePercent()
            ));
        }
        return copy;
    }

    @Nonnull
    private List<CitizenMessage> copyCitizenMessages(@Nonnull List<CitizenMessage> source) {
        List<CitizenMessage> copy = new ArrayList<>(source.size());
        for (CitizenMessage message : source) {
            copy.add(new CitizenMessage(
                    message.getMessage(),
                    message.getInteractionTrigger(),
                    message.getDelaySeconds(),
                    message.getChancePercent()
            ));
        }
        return copy;
    }

    @Nonnull
    private List<DeathDropItem> copyDeathDropItems(@Nonnull List<DeathDropItem> source) {
        List<DeathDropItem> copy = new ArrayList<>(source.size());
        for (DeathDropItem drop : source) {
            copy.add(new DeathDropItem(drop.getItemId(), drop.getQuantity(), drop.getChancePercent()));
        }
        return copy;
    }

    private static final int DEATH_ITEMS_PER_PAGE = 75;

    public void openDeathConfigGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                   @Nonnull CitizenData citizen) {
        DeathConfig dc = citizen.getDeathConfig();
        List<DeathDropItem> drops = dc.getDropItems();
        List<CommandAction> cmds = dc.getDeathCommands();
        List<CitizenMessage> msgs = dc.getDeathMessages();

        // Build drop items HTML
        StringBuilder dropsHtml = new StringBuilder();
        for (int i = 0; i < drops.size(); i++) {
            DeathDropItem drop = drops.get(i);
            String itemId = drop.getItemId() != null ? drop.getItemId() : "";
            int qty = drop.getQuantity();

            dropsHtml.append("""
                <div class="list-item">
                    <div class="list-item-content">
                        <p class="list-item-title">#%d - %s</p>
                        <p class="list-item-subtitle">Quantity: %d &middot; Chance: %.1f%%</p>
                    </div>
                    <div class="list-item-actions">
                        <button id="edit-drop-%d" class="secondary-button small-secondary-button">Edit</button>
                        <div class="spacer-h-sm"></div>
                        <button id="delete-drop-%d" class="secondary-button small-secondary-button">Delete</button>
                    </div>
                </div>
                <div class="spacer-sm"></div>
                """.formatted(
                    i + 1,
                    itemId.isEmpty() ? "Empty Slot" : itemId,
                    qty,
                    drop.getChancePercent(),
                    i,
                    i));
        }

        // Build commands HTML
        StringBuilder cmdsHtml = new StringBuilder();
        for (int i = 0; i < cmds.size(); i++) {
            CommandAction cmd = cmds.get(i);
            cmdsHtml.append("""
                <div class="list-item">
                    <div class="list-item-content">
                        <p class="list-item-title">/%s</p>
                        <p class="list-item-subtitle">%s | Delay: %.1fs | Chance: %.1f%%</p>
                    </div>
                    <div class="list-item-actions">
                        <button id="edit-dcmd-%d" class="secondary-button small-secondary-button">Edit</button>
                        <div class="spacer-h-sm"></div>
                        <button id="delete-dcmd-%d" class="secondary-button small-secondary-button">Delete</button>
                    </div>
                </div>
                <div class="spacer-sm"></div>
                """.formatted(escapeHtml(cmd.getCommand()),
                    cmd.isRunAsServer() ? "Server" : "Player", cmd.getDelaySeconds(), cmd.getChancePercent(), i, i));
        }

        // Build messages HTML
        StringBuilder msgsHtml = new StringBuilder();
        for (int i = 0; i < msgs.size(); i++) {
            CitizenMessage msg = msgs.get(i);
            String preview = msg.getMessage().length() > 40
                    ? msg.getMessage().substring(0, 37) + "..."
                    : msg.getMessage();
            msgsHtml.append("""
                <div class="list-item">
                    <div class="list-item-content">
                        <p class="list-item-title">%s</p>
                        <p class="list-item-subtitle">Delay: %.1fs | Chance: %.1f%%</p>
                    </div>
                    <div class="list-item-actions">
                        <button id="edit-dmsg-%d" class="secondary-button small-secondary-button">Edit</button>
                        <div class="spacer-h-sm"></div>
                        <button id="delete-dmsg-%d" class="secondary-button small-secondary-button">Delete</button>
                    </div>
                </div>
                <div class="spacer-sm"></div>
                """.formatted(escapeHtml(preview), msg.getDelaySeconds(), msg.getChancePercent(), i, i));
        }

        TemplateProcessor template = createBaseTemplate()
                .setVariable("hasDrops", !drops.isEmpty())
                .setVariable("dropCount", drops.size())
                .setVariable("hasCmds", !cmds.isEmpty())
                .setVariable("cmdCount", cmds.size())
                .setVariable("hasMsgs", !msgs.isEmpty())
                .setVariable("msgCount", msgs.size())
                .setVariable("dropCountMin", dc.getDropCountMin())
                .setVariable("dropCountMax", dc.getDropCountMax())
                .setVariable("commandCountMin", dc.getCommandCountMin())
                .setVariable("commandCountMax", dc.getCommandCountMax())
                .setVariable("messageCountMin", dc.getMessageCountMin())
                .setVariable("messageCountMax", dc.getMessageCountMax())
                .setVariable("cmdModeAll", "ALL".equals(dc.getCommandSelectionMode()))
                .setVariable("cmdModeRandom", "RANDOM".equals(dc.getCommandSelectionMode()))
                .setVariable("cmdModeSequential", "SEQUENTIAL".equals(dc.getCommandSelectionMode()))
                .setVariable("msgModeAll", "ALL".equals(dc.getMessageSelectionMode()))
                .setVariable("msgModeRandom", "RANDOM".equals(dc.getMessageSelectionMode()))
                .setVariable("msgModeSequential", "SEQUENTIAL".equals(dc.getMessageSelectionMode()));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 950; anchor-height: 1000;">

                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Death Configuration</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">

                            <p class="page-description">Configure what happens when this citizen dies</p>
                            <div class="spacer-sm"></div>

                            <div class="spacer-sm"></div>

                            <!-- Drop Items Section -->
                            <div class="section">
                                {{@sectionHeader:title=Drop Items ({{$dropCount}}),description=Items dropped at the citizen's position on death}}

                                <button id="add-drop-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 40;">Add Drop Item</button>
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=drop-count-min,label=Drops Min,value={{$dropCountMin}},placeholder=0,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=drop-count-max,label=Drops Max,value={{$dropCountMax}},placeholder=0,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                </div>
                                <p class="form-hint">Set both to 0 for default behavior (all eligible drops).</p>
                                <div class="spacer-sm"></div>

                                {{#if hasDrops}}
                                <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="anchor-height: 200;">
                                """ + dropsHtml.toString() + """
                                </div>
                                {{else}}
                                <div class="empty-state" style="padding: 20;">
                                    <p class="empty-state-description">No drop items configured.</p>
                                </div>
                                {{/if}}
                            </div>

                            <div class="spacer-sm"></div>

                            <!-- Death Commands Section -->
                            <div class="section">
                                {{@sectionHeader:title=Death Commands ({{$cmdCount}}),description=Commands executed when the citizen dies}}

                                <div class="form-row">
                                    <button id="add-dcmd-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 40;">Add Command</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="cmd-mode-all" class="{{#if cmdModeAll}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 120; anchor-height: 40;">All</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="cmd-mode-random" class="{{#if cmdModeRandom}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 120; anchor-height: 40;">Random</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="cmd-mode-sequential" class="{{#if cmdModeSequential}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 120; anchor-height: 40;">Sequential</button>
                                </div>
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=command-count-min,label=Commands Min,value={{$commandCountMin}},placeholder=0,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=command-count-max,label=Commands Max,value={{$commandCountMax}},placeholder=0,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                </div>
                                <p class="form-hint">Set both to 0 for default behavior (All mode: all eligible, Random/Sequential: one).</p>
                                <div class="spacer-sm"></div>

                                {{#if hasCmds}}
                                <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="anchor-height: 200;">
                                """ + cmdsHtml.toString() + """
                                </div>
                                {{else}}
                                <div class="empty-state" style="padding: 20;">
                                    <p class="empty-state-description">No death commands configured.</p>
                                </div>
                                {{/if}}
                            </div>

                            <div class="spacer-sm"></div>

                            <!-- Death Messages Section -->
                            <div class="section">
                                {{@sectionHeader:title=Death Messages ({{$msgCount}}),description=Messages sent to the killer when the citizen dies}}

                                <div class="form-row">
                                    <button id="add-dmsg-btn" class="secondary-button" style="anchor-width: 200; anchor-height: 40;">Add Message</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="msg-mode-all" class="{{#if msgModeAll}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 120; anchor-height: 40;">All</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="msg-mode-random" class="{{#if msgModeRandom}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 120; anchor-height: 40;">Random</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="msg-mode-sequential" class="{{#if msgModeSequential}}secondary-button{{else}}secondary-button{{/if}}" style="anchor-width: 120; anchor-height: 40;">Sequential</button>
                                </div>
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=message-count-min,label=Messages Min,value={{$messageCountMin}},placeholder=0,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=message-count-max,label=Messages Max,value={{$messageCountMax}},placeholder=0,min=0,max=200,step=1,decimals=0}}
                                    </div>
                                </div>
                                <p class="form-hint">Set both to 0 for default behavior (All mode: all eligible, Random/Sequential: one).</p>
                                <div class="spacer-sm"></div>

                                {{#if hasMsgs}}
                                <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="anchor-height: 200;">
                                """ + msgsHtml.toString() + """
                                </div>
                                {{else}}
                                <div class="empty-state" style="padding: 20;">
                                    <p class="empty-state-description">No death messages configured.</p>
                                </div>
                                {{/if}}
                            </div>

                        </div>

                        <div class="spacer-sm"></div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="back-btn" class="secondary-button">Back</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupDeathConfigListeners(page, playerRef, store, citizen, dc, drops, cmds, msgs);

        page.open(store);
    }

    private void setupDeathConfigListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                           CitizenData citizen, DeathConfig dc,
                                           List<DeathDropItem> drops, List<CommandAction> cmds,
                                           List<CitizenMessage> msgs) {
        page.addEventListener("drop-count-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("drop-count-min", Double.class).ifPresent(v -> {
                dc.setDropCountMin(Math.max(0, v.intValue()));
                citizen.setDeathConfig(dc);
                plugin.getCitizensManager().saveCitizen(citizen);
            });
        });
        page.addEventListener("drop-count-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("drop-count-max", Double.class).ifPresent(v -> {
                dc.setDropCountMax(Math.max(0, v.intValue()));
                citizen.setDeathConfig(dc);
                plugin.getCitizensManager().saveCitizen(citizen);
            });
        });
        page.addEventListener("command-count-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("command-count-min", Double.class).ifPresent(v -> {
                dc.setCommandCountMin(Math.max(0, v.intValue()));
                citizen.setDeathConfig(dc);
                plugin.getCitizensManager().saveCitizen(citizen);
            });
        });
        page.addEventListener("command-count-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("command-count-max", Double.class).ifPresent(v -> {
                dc.setCommandCountMax(Math.max(0, v.intValue()));
                citizen.setDeathConfig(dc);
                plugin.getCitizensManager().saveCitizen(citizen);
            });
        });
        page.addEventListener("message-count-min", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("message-count-min", Double.class).ifPresent(v -> {
                dc.setMessageCountMin(Math.max(0, v.intValue()));
                citizen.setDeathConfig(dc);
                plugin.getCitizensManager().saveCitizen(citizen);
            });
        });
        page.addEventListener("message-count-max", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("message-count-max", Double.class).ifPresent(v -> {
                dc.setMessageCountMax(Math.max(0, v.intValue()));
                citizen.setDeathConfig(dc);
                plugin.getCitizensManager().saveCitizen(citizen);
            });
        });

        // Add drop item
        page.addEventListener("add-drop-btn", CustomUIEventBindingType.Activating, event -> {
            openDeathItemSelectionGUI(playerRef, store, citizen, "", 0, -1);
        });

        // Drop item click (to change) and delete
        for (int i = 0; i < drops.size(); i++) {
            final int index = i;
            page.addEventListener("edit-drop-" + i, CustomUIEventBindingType.Activating, event -> {
                openDeathItemSelectionGUI(playerRef, store, citizen, "", 0, index);
            });
            page.addEventListener("delete-drop-" + i, CustomUIEventBindingType.Activating, event -> {
                List<DeathDropItem> updated = new ArrayList<>(drops);
                updated.remove(index);
                dc.setDropItems(updated);
                citizen.setDeathConfig(dc);
                plugin.getCitizensManager().saveCitizen(citizen);
                openDeathConfigGUI(playerRef, store, citizen);
            });
        }

        // Command mode buttons
        page.addEventListener("cmd-mode-all", CustomUIEventBindingType.Activating, event -> {
            dc.setCommandSelectionMode("ALL");
            citizen.setDeathConfig(dc);
            plugin.getCitizensManager().saveCitizen(citizen);
            openDeathConfigGUI(playerRef, store, citizen);
        });
        page.addEventListener("cmd-mode-random", CustomUIEventBindingType.Activating, event -> {
            dc.setCommandSelectionMode("RANDOM");
            citizen.setDeathConfig(dc);
            plugin.getCitizensManager().saveCitizen(citizen);
            openDeathConfigGUI(playerRef, store, citizen);
        });
        page.addEventListener("cmd-mode-sequential", CustomUIEventBindingType.Activating, event -> {
            dc.setCommandSelectionMode("SEQUENTIAL");
            citizen.setDeathConfig(dc);
            plugin.getCitizensManager().saveCitizen(citizen);
            openDeathConfigGUI(playerRef, store, citizen);
        });

        // Add command
        page.addEventListener("add-dcmd-btn", CustomUIEventBindingType.Activating, event -> {
            openEditDeathCommandGUI(playerRef, store, citizen, new CommandAction("", true, 0, "BOTH", 100.0f), -1);
        });

        // Edit/delete commands
        for (int i = 0; i < cmds.size(); i++) {
            final int index = i;
            page.addEventListener("edit-dcmd-" + i, CustomUIEventBindingType.Activating, event -> {
                openEditDeathCommandGUI(playerRef, store, citizen, cmds.get(index), index);
            });
            page.addEventListener("delete-dcmd-" + i, CustomUIEventBindingType.Activating, event -> {
                List<CommandAction> updated = new ArrayList<>(cmds);
                updated.remove(index);
                dc.setDeathCommands(updated);
                citizen.setDeathConfig(dc);
                plugin.getCitizensManager().saveCitizen(citizen);
                openDeathConfigGUI(playerRef, store, citizen);
            });
        }

        // Message mode buttons
        page.addEventListener("msg-mode-all", CustomUIEventBindingType.Activating, event -> {
            dc.setMessageSelectionMode("ALL");
            citizen.setDeathConfig(dc);
            plugin.getCitizensManager().saveCitizen(citizen);
            openDeathConfigGUI(playerRef, store, citizen);
        });
        page.addEventListener("msg-mode-random", CustomUIEventBindingType.Activating, event -> {
            dc.setMessageSelectionMode("RANDOM");
            citizen.setDeathConfig(dc);
            plugin.getCitizensManager().saveCitizen(citizen);
            openDeathConfigGUI(playerRef, store, citizen);
        });
        page.addEventListener("msg-mode-sequential", CustomUIEventBindingType.Activating, event -> {
            dc.setMessageSelectionMode("SEQUENTIAL");
            citizen.setDeathConfig(dc);
            plugin.getCitizensManager().saveCitizen(citizen);
            openDeathConfigGUI(playerRef, store, citizen);
        });

        // Add message
        page.addEventListener("add-dmsg-btn", CustomUIEventBindingType.Activating, event -> {
            openEditDeathMessageGUI(playerRef, store, citizen, new CitizenMessage("", null, 0, 100.0f), -1);
        });

        // Edit/delete messages
        for (int i = 0; i < msgs.size(); i++) {
            final int index = i;
            page.addEventListener("edit-dmsg-" + i, CustomUIEventBindingType.Activating, event -> {
                openEditDeathMessageGUI(playerRef, store, citizen, msgs.get(index), index);
            });
            page.addEventListener("delete-dmsg-" + i, CustomUIEventBindingType.Activating, event -> {
                List<CitizenMessage> updated = new ArrayList<>(msgs);
                updated.remove(index);
                dc.setDeathMessages(updated);
                citizen.setDeathConfig(dc);
                plugin.getCitizensManager().saveCitizen(citizen);
                openDeathConfigGUI(playerRef, store, citizen);
            });
        }

        // Back button
        page.addEventListener("back-btn", CustomUIEventBindingType.Activating, event -> {
            openBehaviorsGUI(playerRef, store, citizen);
        });
    }

    private void openDeathItemSelectionGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                           @Nonnull CitizenData citizen, @Nonnull String searchFilter,
                                           int pageNum, int editDropIndex) {
        Map<String, Item> itemMap = Item.getAssetMap().getAssetMap();
        String lowerFilter = searchFilter.toLowerCase().trim();

        List<Map.Entry<String, Item>> filteredItems;
        if (lowerFilter.isEmpty()) {
            filteredItems = itemMap.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            filteredItems = itemMap.entrySet().stream()
                    .filter(entry -> {
                        String id = entry.getKey().toLowerCase();
                        String translation = Message.translation(entry.getValue().getTranslationKey()).getAnsiMessage().toLowerCase();
                        if (translation.startsWith("server.")) {
                            translation = translation.replaceFirst("(?i)\\.name$", "");
                            int lastDot = translation.lastIndexOf('.');
                            if (lastDot != -1) translation = translation.substring(lastDot + 1);
                            translation = translation.replace("_", " ");
                        }
                        return id.contains(lowerFilter) || translation.contains(lowerFilter);
                    })
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .collect(java.util.stream.Collectors.toList());
        }

        int totalItems = filteredItems.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / DEATH_ITEMS_PER_PAGE));
        int currentPage = Math.max(0, Math.min(pageNum, totalPages - 1));
        int startIndex = currentPage * DEATH_ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + DEATH_ITEMS_PER_PAGE, totalItems);
        List<Map.Entry<String, Item>> pageItems = filteredItems.subList(startIndex, endIndex);

        int itemsPerRow = 8;
        StringBuilder itemsHtml = new StringBuilder();

        for (int rowStart = 0; rowStart < pageItems.size(); rowStart += itemsPerRow) {
            if (rowStart > 0) {
                itemsHtml.append("<div style=\"flex-weight: 0; anchor-height: 6;\"></div>\n");
            }
            itemsHtml.append("<div style=\"layout: center; flex-weight: 0;\">\n");

            int rowEnd = Math.min(rowStart + itemsPerRow, pageItems.size());
            for (int i = rowStart; i < rowEnd; i++) {
                if (i > rowStart) {
                    itemsHtml.append("<div style=\"flex-weight: 0; anchor-width: 6;\"></div>\n");
                }

                Map.Entry<String, Item> entry = pageItems.get(i);
                String itemId = entry.getKey();
                String displayName = Message.translation(entry.getValue().getTranslationKey()).getAnsiMessage();

                if (displayName.startsWith("server.")) {
                    displayName = displayName.replaceFirst("(?i)\\.name$", "");
                    int lastDot = displayName.lastIndexOf('.');
                    if (lastDot != -1) displayName = displayName.substring(lastDot + 1);
                    displayName = displayName.replace("_", " ");
                }
                if (displayName.length() > 12) {
                    displayName = displayName.substring(0, 11) + "...";
                }

                itemsHtml.append("""
                    <div style="layout: top; flex-weight: 0; anchor-width: 96; anchor-height: 120; background-color: #535359; padding: 4;">
                        <div style="layout: top; flex-weight: 0; anchor-width: 88; anchor-height: 78; background-color: #21262d; padding: 6;">
                            <span class="item-icon" data-hyui-item-id="%s" style="anchor-width: 36; anchor-height: 36;"></span>
                            <p style="color: #e6edf3; font-size: 9; text-align: center;">%s</p>
                        </div>
                        <div style="flex-weight: 0; anchor-height: 6;"></div>
                        <button id="pick-%d" class="secondary-button small-secondary-button" style="anchor-width: 88; anchor-height: 28;">Select</button>
                    </div>
                    """.formatted(itemId, displayName, i));
            }
            itemsHtml.append("</div>\n");
        }

        if (pageItems.isEmpty()) {
            itemsHtml.append("""
                <div style="layout: center; flex-weight: 1; padding: 40;">
                    <p style="color: #6e7681; font-size: 13; text-align: center;">No items match your search.</p>
                </div>
                """);
        }

        String html = getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 850; anchor-height: 750;">

                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Select Drop Item</p>
                            </div>
                        </div>

                        <div style="layout: top; flex-weight: 0; padding: 8 16 0 16;">
                            <p class="page-description">Page %d of %d (%d items total)</p>
                        </div>

                        <div style="layout: top; flex-weight: 0; padding: 12 16 8 16;">
                            <div style="layout: center; flex-weight: 0;">
                                <input type="text" id="item-search" class="form-input" style="flex-weight: 1;"
                                       value="%s" placeholder="Search items..." maxlength="64" />
                                <div style="flex-weight: 0; anchor-width: 8;"></div>
                                <button id="search-btn" class="secondary-button" style="anchor-width: 130;">Search</button>
                            </div>
                        </div>

                        <div data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling; flex-weight: 1; padding: 4 16 4 16;">
                            %s
                        </div>

                        <div class="footer">
                            <button id="back-btn" class="secondary-button">Back</button>
                            <div style="flex-weight: 1;"></div>
                            <button id="prev-page-btn" class="secondary-button small-secondary-button" %s>Prev</button>
                            <div style="flex-weight: 0; anchor-width: 8;"></div>
                            <p style="color: #8b949e; font-size: 12;">%d / %d</p>
                            <div style="flex-weight: 0; anchor-width: 8;"></div>
                            <button id="next-page-btn" class="secondary-button small-secondary-button" %s>Next</button>
                        </div>

                    </div>
                </div>
                """.formatted(
                currentPage + 1, totalPages, totalItems,
                searchFilter,
                itemsHtml.toString(),
                currentPage <= 0 ? "disabled" : "",
                currentPage + 1, totalPages,
                currentPage >= totalPages - 1 ? "disabled" : ""
        );

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] searchText = {searchFilter};

        page.addEventListener("item-search", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            searchText[0] = ctx.getValue("item-search", String.class).orElse("");
        });

        page.addEventListener("search-btn", CustomUIEventBindingType.Activating, event -> {
            openDeathItemSelectionGUI(playerRef, store, citizen, searchText[0], 0, editDropIndex);
        });

        if (currentPage > 0) {
            page.addEventListener("prev-page-btn", CustomUIEventBindingType.Activating, event -> {
                openDeathItemSelectionGUI(playerRef, store, citizen, searchFilter, currentPage - 1, editDropIndex);
            });
        }
        if (currentPage < totalPages - 1) {
            page.addEventListener("next-page-btn", CustomUIEventBindingType.Activating, event -> {
                openDeathItemSelectionGUI(playerRef, store, citizen, searchFilter, currentPage + 1, editDropIndex);
            });
        }

        for (int i = 0; i < pageItems.size(); i++) {
            final String itemId = pageItems.get(i).getKey();
            final int idx = i;
            page.addEventListener("pick-" + idx, CustomUIEventBindingType.Activating, event -> {
                openDeathItemQuantityGUI(playerRef, store, citizen, itemId, editDropIndex);
            });
        }

        page.addEventListener("back-btn", CustomUIEventBindingType.Activating, event -> {
            openDeathConfigGUI(playerRef, store, citizen);
        });

        page.open(store);
    }

    private void openDeathItemQuantityGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                          @Nonnull CitizenData citizen, @Nonnull String itemId, int editDropIndex) {
        DeathConfig dc = citizen.getDeathConfig();
        int currentQty = 1;
        float currentChance = 100.0f;
        if (editDropIndex >= 0 && editDropIndex < dc.getDropItems().size()) {
            currentQty = dc.getDropItems().get(editDropIndex).getQuantity();
            currentChance = dc.getDropItems().get(editDropIndex).getChancePercent();
        }

        Item item = Item.getAssetMap().getAssetMap().get(itemId);
        String displayName = itemId;
        if (item != null) {
            displayName = Message.translation(item.getTranslationKey()).getAnsiMessage();
            if (displayName.startsWith("server.")) {
                displayName = displayName.replaceFirst("(?i)\\.name$", "");
                int lastDot = displayName.lastIndexOf('.');
                if (lastDot != -1) displayName = displayName.substring(lastDot + 1);
                displayName = displayName.replace("_", " ");
            }
        }

        TemplateProcessor template = createBaseTemplate()
                .setVariable("itemId", itemId)
                .setVariable("displayName", displayName)
                .setVariable("currentQty", currentQty)
                .setVariable("currentChance", currentChance);

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 500; anchor-height: 500;">

                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Set Drop Quantity</p>
                            </div>
                        </div>

                        <div class="body">

                            <p class="page-description">How many of this item to drop</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                <div style="layout: center; flex-weight: 0; padding-bottom: 16;">
                                    <span class="item-icon" data-hyui-item-id="{{$itemId}}" style="anchor-width: 48; anchor-height: 48;"></span>
                                    <p style="color: #e6edf3; font-size: 16; font-weight: bold; padding-left: 12;">{{$displayName}}</p>
                                </div>
                                <div style="layout: center; flex-weight: 0;">
                                    <div style="anchor-width: 250; flex-weight: 0;">
                                        {{@numberField:id=quantity-input,label=Quantity,value={{$currentQty}},placeholder=1,min=1,max=9999,step=1,decimals=0}}
                                    </div>
                                </div>
                                <div class="spacer-sm"></div>
                                <div style="layout: center; flex-weight: 0;">
                                    <div style="anchor-width: 250; flex-weight: 0;">
                                        {{@numberField:id=chance-input,label=Chance %,value={{$currentChance}},placeholder=100,min=0,max=100,step=1,decimals=1}}
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="footer">
                            <button id="qty-back-btn" class="secondary-button">Back</button>
                            <div style="flex-weight: 0; anchor-width: 8;"></div>
                            <button id="qty-confirm-btn" class="secondary-button" style="anchor-width: 160;">Confirm</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final int[] quantity = {currentQty};
        final float[] chancePercent = {currentChance};

        page.addEventListener("quantity-input", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("quantity-input", Double.class).ifPresent(val -> {
                quantity[0] = Math.max(1, val.intValue());
            });
        });

        page.addEventListener("chance-input", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("chance-input", Double.class).ifPresent(val -> {
                chancePercent[0] = Math.max(0.0f, Math.min(100.0f, val.floatValue()));
            });
        });

        page.addEventListener("qty-confirm-btn", CustomUIEventBindingType.Activating, event -> {
            List<DeathDropItem> drops = new ArrayList<>(dc.getDropItems());
            DeathDropItem dropItem = new DeathDropItem(itemId, quantity[0], chancePercent[0]);

            if (editDropIndex >= 0 && editDropIndex < drops.size()) {
                drops.set(editDropIndex, dropItem);
            } else {
                drops.add(dropItem);
            }

            dc.setDropItems(drops);
            citizen.setDeathConfig(dc);
            plugin.getCitizensManager().saveCitizen(citizen);
            playerRef.sendMessage(Message.raw("Drop item saved!").color(Color.GREEN));
            openDeathConfigGUI(playerRef, store, citizen);
        });

        page.addEventListener("qty-back-btn", CustomUIEventBindingType.Activating, event -> {
            openDeathItemSelectionGUI(playerRef, store, citizen, "", 0, editDropIndex);
        });

        page.open(store);
    }

    public void openEditDeathCommandGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                        @Nonnull CitizenData citizen, @Nonnull CommandAction command, int editIndex) {
        boolean isNew = (editIndex == -1);

        TemplateProcessor template = createBaseTemplate()
                .setVariable("command", escapeHtml(command.getCommand()))
                .setVariable("runAsServer", command.isRunAsServer())
                .setVariable("delaySeconds", command.getDelaySeconds())
                .setVariable("chancePercent", command.getChancePercent())
                .setVariable("isNew", isNew);

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 680; anchor-height: 620;">

                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">{{#if isNew}}Add Death Command{{else}}Edit Death Command{{/if}}</p>
                            </div>
                        </div>

                        <div class="body">

                            <p class="page-description">Command to execute when the citizen dies</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                {{@sectionHeader:title=Command}}
                                <input type="text" id="command-input" class="form-input" value="{{$command}}"
                                       placeholder="give {PlayerName} Rock_Gem_Diamond" />
                                <p class="form-hint">Do not include the leading /. Variables: {PlayerName}, {CitizenName}, {NpcX}, {NpcY}, {NpcZ}.</p>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Delay}}
                                {{@numberField:id=delay-seconds,label=Delay Before Command (seconds),value={{$delaySeconds}},placeholder=0,min=0,max=300,step=0.5,decimals=1}}
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Chance}}
                                {{@numberField:id=chance-percent,label=Chance %,value={{$chancePercent}},placeholder=100,min=0,max=100,step=1,decimals=1}}
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Execution Mode}}
                                <div class="checkbox-row">
                                    <input type="checkbox" id="run-as-server" {{#if runAsServer}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0;">
                                        <p class="checkbox-label">Run as Server</p>
                                        <p class="checkbox-description">Execute as console command instead of player</p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div style="flex-weight: 0; anchor-width: 16;"></div>
                            <button id="save-dcmd-btn" class="secondary-button" style="anchor-width: 200;">{{#if isNew}}Add Command{{else}}Save Changes{{/if}}</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] commandText = {command.getCommand()};
        final boolean[] runAsServer = {command.isRunAsServer()};
        final float[] delaySeconds = {command.getDelaySeconds()};
        final float[] chancePercent = {command.getChancePercent()};

        page.addEventListener("command-input", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            commandText[0] = ctx.getValue("command-input", String.class).orElse("");
        });

        page.addEventListener("delay-seconds", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("delay-seconds", Double.class).ifPresent(val -> delaySeconds[0] = val.floatValue());
            if (delaySeconds[0] == 0.0f) {
                ctx.getValue("delay-seconds", String.class).ifPresent(val -> {
                    try { delaySeconds[0] = Float.parseFloat(val); } catch (NumberFormatException ignored) {}
                });
            }
        });

        page.addEventListener("run-as-server", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            runAsServer[0] = ctx.getValue("run-as-server", Boolean.class).orElse(false);
        });

        page.addEventListener("chance-percent", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("chance-percent", Double.class).ifPresent(val -> {
                chancePercent[0] = Math.max(0.0f, Math.min(100.0f, val.floatValue()));
            });
        });

        page.addEventListener("save-dcmd-btn", CustomUIEventBindingType.Activating, event -> {
            String cmd = commandText[0].trim();
            if (cmd.isEmpty()) {
                playerRef.sendMessage(Message.raw("Command cannot be empty!").color(Color.RED));
                return;
            }
            if (cmd.startsWith("/")) cmd = cmd.substring(1);

            DeathConfig dc = citizen.getDeathConfig();
            List<CommandAction> cmds = new ArrayList<>(dc.getDeathCommands());
            CommandAction saved = new CommandAction(cmd, runAsServer[0], delaySeconds[0], "BOTH", chancePercent[0]);

            if (isNew) {
                cmds.add(saved);
                playerRef.sendMessage(Message.raw("Death command added!").color(Color.GREEN));
            } else {
                cmds.set(editIndex, saved);
                playerRef.sendMessage(Message.raw("Death command updated!").color(Color.GREEN));
            }

            dc.setDeathCommands(cmds);
            citizen.setDeathConfig(dc);
            plugin.getCitizensManager().saveCitizen(citizen);
            openDeathConfigGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openDeathConfigGUI(playerRef, store, citizen);
        });

        page.open(store);
    }

    private void openScheduleGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                 @Nonnull CitizenData citizen) {
        if (citizen.getScheduleConfig() == null) {
            citizen.setScheduleConfig(new ScheduleConfig());
        }
        final ScheduleConfig scheduleConfig = citizen.getScheduleConfig();
        Double scheduleTime24 = getScheduleWorldTime24(citizen);

        Map<String, String> locationNames = new HashMap<>();
        for (ScheduleLocation location : scheduleConfig.getLocations()) {
            locationNames.put(location.getId(), location.getName());
        }

        StringBuilder locationsHtml = new StringBuilder();
        List<ScheduleLocation> locations = scheduleConfig.getLocations();
        for (int i = 0; i < locations.size(); i++) {
            ScheduleLocation location = locations.get(i);
            boolean isDefault = location.getId().equals(scheduleConfig.getDefaultLocationId());
            String worldLabel = location.getWorldUUID() != null ? location.getWorldUUID().toString() : "Unknown world";
            String positionLabel = String.format(Locale.ROOT, "%.1f, %.1f, %.1f",
                    location.getPosition().x, location.getPosition().y, location.getPosition().z);

            locationsHtml.append("""
                                    <div class="list-item">
                                        <div style="layout: top; flex-weight: 1;">
                                            <div>
                                                <p style="font-size: 14; font-weight: bold;">%s%s</p>
                                            </div>
                                            <div>
                                                <p style="font-size: 12; color: #888888;">%s</p>
                                            </div>
                                            <div>
                                                <p style="font-size: 12; color: #888888;">%s</p>
                                            </div>
                                        </div>
                                        <button id="schedule-default-location-%d" class="secondary-button" style="anchor-width: 145;">%s</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="schedule-update-location-%d" class="secondary-button" style="anchor-width: 180;">Update Position</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="schedule-rename-location-%d" class="secondary-button" style="anchor-width: 120;">Rename</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="schedule-delete-location-%d" class="secondary-button" style="anchor-width: 120;">Delete</button>
                                    </div>
                                    <div class="spacer-xs"></div>
                                    """.formatted(
                    escapeHtml(location.getName()),
                    isDefault ? " [Default]" : "",
                    escapeHtml(worldLabel),
                    escapeHtml(positionLabel),
                    i,
                    isDefault ? "Default" : "Set Default",
                    i,
                    i,
                    i
            ));
        }

        StringBuilder entriesHtml = new StringBuilder();
        List<ScheduleEntry> entries = scheduleConfig.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            ScheduleEntry entry = entries.get(i);
            String locationName = locationNames.getOrDefault(entry.getLocationId(), "No location");
            String timeLabel = formatScheduleTimeRange(entry);
            String activityLabel = describeScheduleActivity(entry);
            if (entry.getActivityType() == ScheduleActivityType.FOLLOW_CITIZEN && !entry.getFollowCitizenId().isEmpty()) {
                CitizenData followTarget = plugin.getCitizensManager().getCitizen(entry.getFollowCitizenId());
                String targetName = followTarget != null ? followTarget.getName() : "Missing Citizen";
                activityLabel = "Follow: " + targetName + " @ " + String.format(Locale.ROOT, "%.1f", entry.getFollowDistance());
            }
            String detailLabel = activityLabel + " | " + locationName
                    + " | Radius " + String.format(Locale.ROOT, "%.1f", entry.getArrivalRadius());

            entriesHtml.append("""
                                    <div class="list-item">
                                        <div style="layout: top; flex-weight: 1;">
                                            <div>
                                                <p style="font-size: 14; font-weight: bold;">%s%s</p>
                                            </div>
                                            <div>
                                                <p style="font-size: 12; color: #888888;">%s</p>
                                            </div>
                                            <div>
                                                <p style="font-size: 12; color: #888888;">%s</p>
                                            </div>
                                        </div>
                                        <button id="schedule-edit-entry-%d" class="secondary-button" style="anchor-width: 90;">Edit</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="schedule-duplicate-entry-%d" class="secondary-button" style="anchor-width: 135;">Duplicate</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="schedule-toggle-entry-%d" class="secondary-button" style="anchor-width: 120;">%s</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="schedule-move-up-entry-%d" class="secondary-button" style="anchor-width: 90;">Up</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="schedule-move-down-entry-%d" class="secondary-button" style="anchor-width: 100;">Down</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="schedule-delete-entry-%d" class="secondary-button" style="anchor-width: 110;">Delete</button>
                                    </div>
                                    <div class="spacer-xs"></div>
                                    """.formatted(
                    escapeHtml(entry.getName()),
                    entry.isEnabled() ? "" : " [Disabled]",
                    escapeHtml(timeLabel),
                    escapeHtml(detailLabel),
                    i,
                    i,
                    i,
                    entry.isEnabled() ? "Disable" : "Enable",
                    i,
                    i,
                    i
            ));
        }

        String currentEntryName = scheduleConfig.findEntry(citizen.getCurrentScheduleEntryId())
                .map(ScheduleEntry::getName)
                .orElse("None");

        TemplateProcessor template = createBaseTemplate()
                .setVariable("citizenName", escapeHtml(citizen.getName()))
                .setVariable("scheduleEnabled", scheduleConfig.isEnabled())
                .setVariable("runtimeState", citizen.getCurrentScheduleRuntimeState().name())
                .setVariable("currentEntryName", escapeHtml(currentEntryName))
                .setVariable("scheduleTimeText", escapeHtml(formatScheduleWorldTime(scheduleTime24)))
                .setVariable("statusText", escapeHtml(generateScheduleStatusText(citizen, scheduleConfig, scheduleTime24)))
                .setVariable("nextEntryText", escapeHtml(describeScheduleNextEntry(scheduleConfig, scheduleTime24)))
                .setVariable("fallbackModeText", escapeHtml(describeScheduleFallbackMode(scheduleConfig.getFallbackMode())))
                .setVariable("hasLocations", !locations.isEmpty())
                .setVariable("hasEntries", !entries.isEmpty());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 1180; anchor-height: 1040;">

                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Daily Schedule</p>
                            </div>
                        </div>

                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">
                            <p class="page-description">Plan what {{$citizenName}} does throughout the day. Times use Hytale's 0-24 hour clock.</p>
                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Runtime State,description=Live schedule status for this citizen}}
                                <div class="stats-grid" style="layout: left;">
                                    {{@statCard:value={{$runtimeState}},label=State}}
                                    {{@statCard:value={{$currentEntryName}},label=Current Entry}}
                                    {{@statCard:value={{$scheduleTimeText}},label=Hytale Time}}
                                </div>
                                <div class="spacer-xs"></div>
                                {{@infoBox:text={{$statusText}}}}
                                <div class="spacer-xs"></div>
                                {{@infoBox:text={{$nextEntryText}}}}
                            </div>

                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Schedule Toggle,description=Enable or disable daily schedule control}}
                                <div style="layout: center;">
                                    <button id="schedule-toggle-enabled" class="secondary-button" style="anchor-width: 180; anchor-height: 42;">{{#if scheduleEnabled}}Enabled{{else}}Disabled{{/if}}</button>
                                </div>
                                <div class="spacer-xs"></div>
                                <p style="color: #8b949e; font-size: 12; text-align: center;">When enabled, schedule entries override the citizen's base movement as needed.</p>
                            </div>

                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Fallback Mode,description=Behavior to use when no schedule entry is currently active}}
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #c9d1d9; font-size: 13; text-align: center;">Current: {{$fallbackModeText}}</p>
                                    </div>
                                </div>
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    <button id="schedule-fallback-base" class="secondary-button" style="anchor-width: 190;">Use Base</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="schedule-fallback-default" class="secondary-button" style="anchor-width: 220;">Go To Default Location</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="schedule-fallback-hold" class="secondary-button" style="anchor-width: 190;">Hold Last State</button>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Locations,description=Named places this citizen can travel to during the day}}
                                {{#if hasLocations}}
                                <!--SCHEDULE_LOCATIONS-->
                                {{else}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">No schedule locations yet. Add one from your current position.</p>
                                <div class="spacer-xs"></div>
                                {{/if}}
                                <div style="layout: center;">
                                    <button id="schedule-add-location" class="secondary-button" style="anchor-width: 340;">Add Location At My Position</button>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Entries,description=Time blocks that define where the citizen goes and what it does there}}
                                {{#if hasEntries}}
                                <!--SCHEDULE_ENTRIES-->
                                {{else}}
                                <p style="color: #8b949e; font-size: 12; text-align: center;">No schedule entries yet. Create one to start using the system.</p>
                                <div class="spacer-xs"></div>
                                {{/if}}
                                <div class="form-row">
                                    <button id="schedule-add-entry" class="secondary-button" style="anchor-width: 210;">Add Schedule Entry</button>
                                </div>
                            </div>
                        </div>

                        <div class="footer">
                            <button id="schedule-back-btn" class="secondary-button" style="anchor-width: 180;">Back To Citizen</button>
                        </div>
                    </div>
                </div>
                """)
                .replace("<!--SCHEDULE_LOCATIONS-->", locationsHtml.toString())
                .replace("<!--SCHEDULE_ENTRIES-->", entriesHtml.toString());

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        page.addEventListener("schedule-toggle-enabled", CustomUIEventBindingType.Activating, event -> {
            scheduleConfig.setEnabled(!scheduleConfig.isEnabled());
            plugin.getCitizensManager().saveCitizen(citizen);
            plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
            openScheduleGUI(playerRef, store, citizen);
        });

        page.addEventListener("schedule-fallback-base", CustomUIEventBindingType.Activating, event -> {
            scheduleConfig.setFallbackMode(ScheduleFallbackMode.USE_BASE_BEHAVIOR);
            plugin.getCitizensManager().saveCitizen(citizen);
            plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
            openScheduleGUI(playerRef, store, citizen);
        });

        page.addEventListener("schedule-fallback-default", CustomUIEventBindingType.Activating, event -> {
            scheduleConfig.setFallbackMode(ScheduleFallbackMode.GO_TO_DEFAULT_LOCATION_IDLE);
            plugin.getCitizensManager().saveCitizen(citizen);
            plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
            openScheduleGUI(playerRef, store, citizen);
        });

        page.addEventListener("schedule-fallback-hold", CustomUIEventBindingType.Activating, event -> {
            scheduleConfig.setFallbackMode(ScheduleFallbackMode.HOLD_LAST_SCHEDULE_STATE);
            plugin.getCitizensManager().saveCitizen(citizen);
            plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
            openScheduleGUI(playerRef, store, citizen);
        });

        page.addEventListener("schedule-add-location", CustomUIEventBindingType.Activating, event -> {
            UUID worldUuid = playerRef.getWorldUuid();
            if (worldUuid == null) {
                playerRef.sendMessage(Message.raw("Could not determine your world.").color(Color.RED));
                return;
            }

            Vector3d position = new Vector3d(playerRef.getTransform().getPosition());
            Vector3f rotation = new Vector3f(playerRef.getTransform().getRotation());
            ScheduleLocation location = new ScheduleLocation(
                    UUID.randomUUID().toString(),
                    "Location " + (scheduleConfig.getLocations().size() + 1),
                    worldUuid,
                    position,
                    rotation
            );
            scheduleConfig.getLocations().add(location);
            if (scheduleConfig.getDefaultLocationId().isEmpty()) {
                scheduleConfig.setDefaultLocationId(location.getId());
            }
            plugin.getCitizensManager().saveCitizen(citizen);
            plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
            openScheduleGUI(playerRef, store, citizen);
        });

        for (int i = 0; i < locations.size(); i++) {
            final int index = i;

            page.addEventListener("schedule-default-location-" + i, CustomUIEventBindingType.Activating, event -> {
                ScheduleLocation location = scheduleConfig.getLocations().get(index);
                scheduleConfig.setDefaultLocationId(location.getId());
                plugin.getCitizensManager().saveCitizen(citizen);
                plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
                openScheduleGUI(playerRef, store, citizen);
            });

            page.addEventListener("schedule-update-location-" + i, CustomUIEventBindingType.Activating, event -> {
                UUID worldUuid = playerRef.getWorldUuid();
                if (worldUuid == null) {
                    playerRef.sendMessage(Message.raw("Could not determine your world.").color(Color.RED));
                    return;
                }

                ScheduleLocation location = scheduleConfig.getLocations().get(index);
                location.setWorldUUID(worldUuid);
                location.setPosition(new Vector3d(playerRef.getTransform().getPosition()));
                location.setRotation(new Vector3f(playerRef.getTransform().getRotation()));
                plugin.getCitizensManager().saveCitizen(citizen);
                plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
                openScheduleGUI(playerRef, store, citizen);
            });

            page.addEventListener("schedule-rename-location-" + i, CustomUIEventBindingType.Activating, event ->
                    openScheduleLocationRenameGUI(playerRef, store, citizen, index));

            page.addEventListener("schedule-delete-location-" + i, CustomUIEventBindingType.Activating, event -> {
                ScheduleLocation location = scheduleConfig.getLocations().remove(index);
                if (location.getId().equals(scheduleConfig.getDefaultLocationId())) {
                    scheduleConfig.setDefaultLocationId("");
                }
                for (ScheduleEntry entry : scheduleConfig.getEntries()) {
                    if (location.getId().equals(entry.getLocationId())) {
                        entry.setLocationId("");
                    }
                }
                plugin.getCitizensManager().saveCitizen(citizen);
                plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
                openScheduleGUI(playerRef, store, citizen);
            });
        }

        page.addEventListener("schedule-add-entry", CustomUIEventBindingType.Activating, event -> {
            ScheduleEntry newEntry = new ScheduleEntry();
            newEntry.setId(UUID.randomUUID().toString());
            newEntry.setName("New Entry");
            newEntry.setStartTime24(0.0);
            newEntry.setEndTime24(0.0);
            if (!scheduleConfig.getLocations().isEmpty()) {
                newEntry.setLocationId(scheduleConfig.getLocations().get(0).getId());
            }
            openScheduleEntryEditorGUI(playerRef, store, citizen, newEntry, true, -1);
        });

        for (int i = 0; i < entries.size(); i++) {
            final int index = i;

            page.addEventListener("schedule-edit-entry-" + i, CustomUIEventBindingType.Activating, event -> {
                ScheduleEntry source = scheduleConfig.getEntries().get(index);
                ScheduleEntry draft = new ScheduleEntry();
                draft.setId(source.getId());
                draft.setName(source.getName());
                draft.setEnabled(source.isEnabled());
                draft.setStartTime24(source.getStartTime24());
                draft.setEndTime24(source.getEndTime24());
                draft.setLocationId(source.getLocationId());
                draft.setActivityType(source.getActivityType());
                draft.setArrivalRadius(source.getArrivalRadius());
                draft.setTravelSpeed(source.getTravelSpeed());
                draft.setWanderRadius(source.getWanderRadius());
                draft.setPatrolPathName(source.getPatrolPathName());
                draft.setFollowCitizenId(source.getFollowCitizenId());
                draft.setFollowDistance(source.getFollowDistance());
                draft.setArrivalAnimationName(source.getArrivalAnimationName());
                draft.setArrivalAnimationSlot(source.getArrivalAnimationSlot());
                draft.setPriority(source.getPriority());
                openScheduleEntryEditorGUI(playerRef, store, citizen, draft, false, index);
            });

            page.addEventListener("schedule-duplicate-entry-" + i, CustomUIEventBindingType.Activating, event -> {
                ScheduleEntry source = scheduleConfig.getEntries().get(index);
                ScheduleEntry duplicate = new ScheduleEntry();
                duplicate.setId(UUID.randomUUID().toString());
                duplicate.setName(source.getName() + " Copy");
                duplicate.setEnabled(source.isEnabled());
                duplicate.setStartTime24(source.getStartTime24());
                duplicate.setEndTime24(source.getEndTime24());
                duplicate.setLocationId(source.getLocationId());
                duplicate.setActivityType(source.getActivityType());
                duplicate.setArrivalRadius(source.getArrivalRadius());
                duplicate.setTravelSpeed(source.getTravelSpeed());
                duplicate.setWanderRadius(source.getWanderRadius());
                duplicate.setPatrolPathName(source.getPatrolPathName());
                duplicate.setFollowCitizenId(source.getFollowCitizenId());
                duplicate.setFollowDistance(source.getFollowDistance());
                duplicate.setArrivalAnimationName(source.getArrivalAnimationName());
                duplicate.setArrivalAnimationSlot(source.getArrivalAnimationSlot());
                duplicate.setPriority(source.getPriority());
                scheduleConfig.getEntries().add(index + 1, duplicate);
                plugin.getCitizensManager().saveCitizen(citizen);
                plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
                openScheduleGUI(playerRef, store, citizen);
            });

            page.addEventListener("schedule-toggle-entry-" + i, CustomUIEventBindingType.Activating, event -> {
                ScheduleEntry entry = scheduleConfig.getEntries().get(index);
                entry.setEnabled(!entry.isEnabled());
                plugin.getCitizensManager().saveCitizen(citizen);
                plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
                openScheduleGUI(playerRef, store, citizen);
            });

            page.addEventListener("schedule-move-up-entry-" + i, CustomUIEventBindingType.Activating, event -> {
                if (index <= 0) {
                    openScheduleGUI(playerRef, store, citizen);
                    return;
                }
                Collections.swap(scheduleConfig.getEntries(), index, index - 1);
                plugin.getCitizensManager().saveCitizen(citizen);
                plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
                openScheduleGUI(playerRef, store, citizen);
            });

            page.addEventListener("schedule-move-down-entry-" + i, CustomUIEventBindingType.Activating, event -> {
                if (index >= scheduleConfig.getEntries().size() - 1) {
                    openScheduleGUI(playerRef, store, citizen);
                    return;
                }
                Collections.swap(scheduleConfig.getEntries(), index, index + 1);
                plugin.getCitizensManager().saveCitizen(citizen);
                plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
                openScheduleGUI(playerRef, store, citizen);
            });

            page.addEventListener("schedule-delete-entry-" + i, CustomUIEventBindingType.Activating, event -> {
                scheduleConfig.getEntries().remove(index);
                plugin.getCitizensManager().saveCitizen(citizen);
                plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
                openScheduleGUI(playerRef, store, citizen);
            });
        }

        page.addEventListener("schedule-back-btn", CustomUIEventBindingType.Activating, event ->
                openEditCitizenGUI(playerRef, store, citizen));

        page.open(store);
    }

    private void openScheduleLocationRenameGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                               @Nonnull CitizenData citizen, int locationIndex) {
        ScheduleConfig scheduleConfig = citizen.getScheduleConfig();
        if (locationIndex < 0 || locationIndex >= scheduleConfig.getLocations().size()) {
            openScheduleGUI(playerRef, store, citizen);
            return;
        }

        ScheduleLocation location = scheduleConfig.getLocations().get(locationIndex);

        TemplateProcessor template = createBaseTemplate()
                .setVariable("locationName", escapeHtml(location.getName()));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 620; anchor-height: 360;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Rename Schedule Location</p>
                            </div>
                        </div>
                        <div class="body">
                            <p class="page-description">Change the display name used by schedule entries and fallback settings.</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                {{@formField:id=schedule-location-name,label=Location Name,value={{$locationName}},placeholder=Enter a location name...}}
                            </div>
                        </div>
                        <div class="footer">
                            <button id="schedule-location-cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="schedule-location-save-btn" class="secondary-button" style="anchor-width: 200;">Save Name</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] locationName = {location.getName()};
        page.addEventListener("schedule-location-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            locationName[0] = ctx.getValue("schedule-location-name", String.class).orElse(location.getName()).trim();
        });

        page.addEventListener("schedule-location-save-btn", CustomUIEventBindingType.Activating, event -> {
            if (locationName[0].isEmpty()) {
                playerRef.sendMessage(Message.raw("Location name cannot be empty.").color(Color.RED));
                return;
            }
            location.setName(locationName[0]);
            plugin.getCitizensManager().saveCitizen(citizen);
            plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
            openScheduleGUI(playerRef, store, citizen);
        });

        page.addEventListener("schedule-location-cancel-btn", CustomUIEventBindingType.Activating, event ->
                openScheduleGUI(playerRef, store, citizen));

        page.open(store);
    }

    private void openScheduleEntryEditorGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                            @Nonnull CitizenData citizen, @Nonnull ScheduleEntry draftEntry,
                                            boolean isNew, int editIndex) {
        ScheduleConfig scheduleConfig = citizen.getScheduleConfig();

        TemplateProcessor template = createBaseTemplate()
                .setVariable("isNew", isNew)
                .setVariable("entryName", escapeHtml(draftEntry.getName()))
                .setVariable("startTime24", draftEntry.getStartTime24())
                .setVariable("endTime24", draftEntry.getEndTime24())
                .setVariable("locationOptions", generateScheduleLocationOptions(scheduleConfig, draftEntry.getLocationId()))
                .setVariable("activityOptions", generateScheduleActivityOptions(draftEntry.getActivityType()))
                .setVariable("followCitizenOptions", generateScheduleCitizenOptions(citizen, draftEntry.getFollowCitizenId()))
                .setVariable("followDistance", draftEntry.getFollowDistance())
                .setVariable("arrivalRadius", draftEntry.getArrivalRadius())
                .setVariable("travelSpeed", draftEntry.getTravelSpeed())
                .setVariable("wanderRadius", draftEntry.getWanderRadius())
                .setVariable("patrolPathOptions", generatePatrolPathOptions(draftEntry.getPatrolPathName()))
                .setVariable("arrivalAnimationName", escapeHtml(draftEntry.getArrivalAnimationName()))
                .setVariable("arrivalAnimationSlot", draftEntry.getArrivalAnimationSlot())
                .setVariable("priority", draftEntry.getPriority());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 860; anchor-height: 980;">

                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">{{#if isNew}}Create Schedule Entry{{else}}Edit Schedule Entry{{/if}}</p>
                            </div>
                        </div>

                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling;">
                            <p class="page-description">Schedule entries use Hytale's 0-24 hour clock. Set start and end to the same time for an all-day entry.</p>
                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Entry Identity}}
                                {{@formField:id=schedule-entry-name,label=Entry Name,value={{$entryName}},placeholder=Morning Patrol}}
                            </div>

                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Time Block}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=schedule-start-time,label=Start Time,value={{$startTime24}},placeholder=8.0,min=0,max=24,step=0.25,decimals=2,hint=Use 0-24 time, such as 6.5 for 06:30}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=schedule-end-time,label=End Time,value={{$endTime24}},placeholder=12.0,min=0,max=24,step=0.25,decimals=2,hint=If end is earlier than start, the entry runs overnight}}
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Destination}}
                                <div class="form-row">
                                    <select id="schedule-location-id" data-hyui-showlabel="true">
                                        {{{$locationOptions}}}
                                    </select>
                                </div>
                            </div>

                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Activity}}
                                <div class="form-row">
                                    <select id="schedule-activity-type" data-hyui-showlabel="true">
                                        {{{$activityOptions}}}
                                    </select>
                                </div>
                                <div class="spacer-xs"></div>
                                <p class="form-hint" style="text-align: center;">Patrol uses the path below after the citizen reaches the destination.</p>
                            </div>

                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Travel And Activity Settings}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=schedule-arrival-radius,label=Arrival Radius,value={{$arrivalRadius}},placeholder=1.5,min=0.25,max=50,step=0.25,decimals=2}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=schedule-travel-speed,label=Travel Speed,value={{$travelSpeed}},placeholder=10,min=1,max=100,step=1,decimals=0}}
                                    </div>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=schedule-wander-radius,label=Wander Radius,value={{$wanderRadius}},placeholder=5,min=1,max=100,step=1,decimals=0}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=schedule-priority,label=Priority,value={{$priority}},placeholder=0,min=0,max=1000,step=1,decimals=0,hint=Higher priority wins if entries overlap}}
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Patrol Path}}
                                <div class="form-row">
                                    <select id="schedule-patrol-path" data-hyui-showlabel="true">
                                        {{{$patrolPathOptions}}}
                                    </select>
                                </div>
                                <div class="spacer-xs"></div>
                                <p class="form-hint" style="text-align: center;">Used only when Activity is Patrol.</p>
                            </div>

                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Follow Target}}
                                <div class="form-row">
                                    <select id="schedule-follow-citizen-id" data-hyui-showlabel="true">
                                        {{{$followCitizenOptions}}}
                                    </select>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    {{@numberField:id=schedule-follow-distance,label=Follow Distance,value={{$followDistance}},placeholder=2.0,min=0.1,max=20,step=0.1,decimals=1}}
                                </div>
                                <div class="spacer-xs"></div>
                                <p class="form-hint" style="text-align: center;">Used only when Activity is Follow Citizen.</p>
                            </div>

                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Arrival Animation,description=Optional one-time animation to play after arriving}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@formField:id=schedule-arrival-animation-name,label=Animation Name,value={{$arrivalAnimationName}},placeholder=Leave blank for none}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 1;">
                                        {{@numberField:id=schedule-arrival-animation-slot,label=Animation Slot,value={{$arrivalAnimationSlot}},placeholder=0,min=0,max=12,step=1,decimals=0}}
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="footer">
                            <button id="schedule-entry-cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="schedule-entry-save-btn" class="secondary-button" style="anchor-width: 220;">{{#if isNew}}Create Entry{{else}}Save Changes{{/if}}</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] entryName = {draftEntry.getName()};
        final double[] startTime24 = {draftEntry.getStartTime24()};
        final double[] endTime24 = {draftEntry.getEndTime24()};
        final String[] locationId = {draftEntry.getLocationId()};
        final ScheduleActivityType[] activityType = {draftEntry.getActivityType()};
        final float[] arrivalRadius = {draftEntry.getArrivalRadius()};
        final float[] travelSpeed = {draftEntry.getTravelSpeed()};
        final float[] wanderRadius = {draftEntry.getWanderRadius()};
        final String[] patrolPathName = {draftEntry.getPatrolPathName()};
        final String[] followCitizenId = {draftEntry.getFollowCitizenId()};
        final float[] followDistance = {draftEntry.getFollowDistance()};
        final String[] arrivalAnimationName = {draftEntry.getArrivalAnimationName()};
        final int[] arrivalAnimationSlot = {draftEntry.getArrivalAnimationSlot()};
        final int[] priority = {draftEntry.getPriority()};

        page.addEventListener("schedule-entry-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            entryName[0] = ctx.getValue("schedule-entry-name", String.class).orElse(draftEntry.getName()).trim();
        });

        page.addEventListener("schedule-start-time", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("schedule-start-time", Double.class).ifPresent(v ->
                    startTime24[0] = Math.max(0.0, Math.min(24.0, v)));
        });

        page.addEventListener("schedule-end-time", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("schedule-end-time", Double.class).ifPresent(v ->
                    endTime24[0] = Math.max(0.0, Math.min(24.0, v)));
        });

        page.addEventListener("schedule-location-id", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            locationId[0] = ctx.getValue("schedule-location-id", String.class).orElse("");
        });

        page.addEventListener("schedule-activity-type", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            String value = ctx.getValue("schedule-activity-type", String.class).orElse(ScheduleActivityType.IDLE.name());
            try {
                activityType[0] = ScheduleActivityType.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                activityType[0] = ScheduleActivityType.IDLE;
            }
        });

        page.addEventListener("schedule-arrival-radius", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("schedule-arrival-radius", Double.class).ifPresent(v ->
                    arrivalRadius[0] = Math.max(0.25f, v.floatValue()));
        });

        page.addEventListener("schedule-travel-speed", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("schedule-travel-speed", Double.class).ifPresent(v ->
                    travelSpeed[0] = Math.max(1.0f, v.floatValue()));
        });

        page.addEventListener("schedule-wander-radius", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("schedule-wander-radius", Double.class).ifPresent(v ->
                    wanderRadius[0] = Math.max(1.0f, v.floatValue()));
        });

        page.addEventListener("schedule-priority", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("schedule-priority", Double.class).ifPresent(v ->
                    priority[0] = Math.max(0, v.intValue()));
        });

        page.addEventListener("schedule-patrol-path", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            patrolPathName[0] = ctx.getValue("schedule-patrol-path", String.class).orElse("");
        });

        page.addEventListener("schedule-follow-citizen-id", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            followCitizenId[0] = ctx.getValue("schedule-follow-citizen-id", String.class).orElse("");
        });

        page.addEventListener("schedule-follow-distance", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("schedule-follow-distance", Double.class).ifPresent(v ->
                    followDistance[0] = Math.max(0.1f, v.floatValue()));
        });

        page.addEventListener("schedule-arrival-animation-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            arrivalAnimationName[0] = ctx.getValue("schedule-arrival-animation-name", String.class).orElse("").trim();
        });

        page.addEventListener("schedule-arrival-animation-slot", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("schedule-arrival-animation-slot", Double.class).ifPresent(v ->
                    arrivalAnimationSlot[0] = Math.max(0, v.intValue()));
        });

        page.addEventListener("schedule-entry-save-btn", CustomUIEventBindingType.Activating, event -> {
            if (entryName[0].isEmpty()) {
                playerRef.sendMessage(Message.raw("Entry name cannot be empty.").color(Color.RED));
                return;
            }
            if (locationId[0].isEmpty()) {
                playerRef.sendMessage(Message.raw("Select a destination location first.").color(Color.RED));
                return;
            }
            if (activityType[0] == ScheduleActivityType.PATROL && patrolPathName[0].isEmpty()) {
                playerRef.sendMessage(Message.raw("Patrol entries need a patrol path.").color(Color.RED));
                return;
            }
            if (activityType[0] == ScheduleActivityType.FOLLOW_CITIZEN && followCitizenId[0].isEmpty()) {
                playerRef.sendMessage(Message.raw("Follow entries need a target citizen.").color(Color.RED));
                return;
            }

            draftEntry.setName(entryName[0]);
            draftEntry.setStartTime24(startTime24[0]);
            draftEntry.setEndTime24(endTime24[0]);
            draftEntry.setLocationId(locationId[0]);
            draftEntry.setActivityType(activityType[0]);
            draftEntry.setArrivalRadius(arrivalRadius[0]);
            draftEntry.setTravelSpeed(travelSpeed[0]);
            draftEntry.setWanderRadius(wanderRadius[0]);
            draftEntry.setPatrolPathName(patrolPathName[0]);
            draftEntry.setFollowCitizenId(followCitizenId[0]);
            draftEntry.setFollowDistance(followDistance[0]);
            draftEntry.setArrivalAnimationName(arrivalAnimationName[0]);
            draftEntry.setArrivalAnimationSlot(arrivalAnimationSlot[0]);
            draftEntry.setPriority(priority[0]);

            if (isNew) {
                scheduleConfig.getEntries().add(draftEntry);
            } else if (editIndex >= 0 && editIndex < scheduleConfig.getEntries().size()) {
                scheduleConfig.getEntries().set(editIndex, draftEntry);
            }

            plugin.getCitizensManager().saveCitizen(citizen);
            plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);
            openScheduleGUI(playerRef, store, citizen);
        });

        page.addEventListener("schedule-entry-cancel-btn", CustomUIEventBindingType.Activating, event ->
                openScheduleGUI(playerRef, store, citizen));

        page.open(store);
    }

    public void openEditDeathMessageGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                        @Nonnull CitizenData citizen, @Nonnull CitizenMessage message, int editIndex) {
        boolean isNew = (editIndex == -1);

        TemplateProcessor template = createBaseTemplate()
                .setVariable("message", escapeHtml(message.getMessage()))
                .setVariable("delaySeconds", message.getDelaySeconds())
                .setVariable("chancePercent", message.getChancePercent())
                .setVariable("isNew", isNew);

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 680; anchor-height: 580;">

                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">{{#if isNew}}Add Death Message{{else}}Edit Death Message{{/if}}</p>
                            </div>
                        </div>

                        <div class="body">

                            <p class="page-description">Message sent to the killer when the citizen dies</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                {{@sectionHeader:title=Message Text}}
                                <input type="text" id="message-input" class="form-input" value="{{$message}}"
                                       placeholder="Enter message with optional color codes..." />
                                <p class="form-hint">Colors: {COLOR}, {RED}, {BLUE}, {#HEX}, {#FFA500}, etc.</p>
                                <div class="spacer-xs"></div>
                                <p class="form-hint">Rich text: **bold**, *italic*, [label](https://example.com).</p>
                                <div class="spacer-xs"></div>
                                <p class="form-hint">Variables: {PlayerName}, {CitizenName}, {NpcX}, {NpcY}, {NpcZ}.</p>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Delay}}
                                {{@numberField:id=delay-seconds,label=Delay Before Message (seconds),value={{$delaySeconds}},placeholder=0,min=0,max=60,step=0.5,decimals=1}}
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Chance}}
                                {{@numberField:id=chance-percent,label=Chance %,value={{$chancePercent}},placeholder=100,min=0,max=100,step=1,decimals=1}}
                            </div>
                        </div>

                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div style="flex-weight: 0; anchor-width: 16;"></div>
                            <button id="save-dmsg-btn" class="secondary-button" style="anchor-width: 200;">{{#if isNew}}Add Message{{else}}Save Changes{{/if}}</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] messageText = {message.getMessage()};
        final float[] delaySeconds = {message.getDelaySeconds()};
        final float[] chancePercent = {message.getChancePercent()};

        page.addEventListener("message-input", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            messageText[0] = ctx.getValue("message-input", String.class).orElse("");
        });

        page.addEventListener("delay-seconds", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("delay-seconds", Double.class).ifPresent(val -> delaySeconds[0] = val.floatValue());
            if (delaySeconds[0] == 0.0f) {
                ctx.getValue("delay-seconds", String.class).ifPresent(val -> {
                    try { delaySeconds[0] = Float.parseFloat(val); } catch (NumberFormatException ignored) {}
                });
            }
        });

        page.addEventListener("chance-percent", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("chance-percent", Double.class).ifPresent(val -> {
                chancePercent[0] = Math.max(0.0f, Math.min(100.0f, val.floatValue()));
            });
        });

        page.addEventListener("save-dmsg-btn", CustomUIEventBindingType.Activating, event -> {
            if (messageText[0].trim().isEmpty()) {
                playerRef.sendMessage(Message.raw("Message cannot be empty!").color(Color.RED));
                return;
            }

            DeathConfig dc = citizen.getDeathConfig();
            List<CitizenMessage> msgs = new ArrayList<>(dc.getDeathMessages());
            CitizenMessage saved = new CitizenMessage(messageText[0].trim(), null, delaySeconds[0], chancePercent[0]);

            if (isNew) {
                msgs.add(saved);
                playerRef.sendMessage(Message.raw("Death message added!").color(Color.GREEN));
            } else {
                msgs.set(editIndex, saved);
                playerRef.sendMessage(Message.raw("Death message updated!").color(Color.GREEN));
            }

            dc.setDeathMessages(msgs);
            citizen.setDeathConfig(dc);
            plugin.getCitizensManager().saveCitizen(citizen);
            openDeathConfigGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openDeathConfigGUI(playerRef, store, citizen);
        });

        page.open(store);
    }

    public void openPatrolPathsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                   @Nonnull CitizenData citizen) {
        List<PatrolPath> allPaths = new ArrayList<>(plugin.getCitizensManager().getPatrolManager().getAllPaths());
        allPaths.sort(Comparator.comparing(PatrolPath::getName));

        StringBuilder pathsHtml = new StringBuilder();
        for (int i = 0; i < allPaths.size(); i++) {
            PatrolPath path = allPaths.get(i);
            pathsHtml.append("""
                                    <div class="list-item">
                                        <div style="layout: top; flex-weight: 1;">
                                            <div>
                                                <p style="font-size: 14; font-weight: bold;">%s</p>
                                            </div>
                                            <div>
                                                <p style="font-size: 12; color: #888888;">%s | %d waypoints</p>
                                            </div>
                                        </div>
                                        <button id="edit-path-%d" class="secondary-button" style="anchor-width: 100;">Edit</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="rename-path-%d" class="secondary-button" style="anchor-width: 110;">Rename</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="delete-path-%d" class="secondary-button" style="anchor-width: 120;">Delete</button>
                                    </div>
                                    <div class="spacer-xs"></div>
                                    """.formatted(
                    escapeHtml(path.getName()),
                    escapeHtml(path.getLoopMode().name()),
                    path.getWaypoints().size(),
                    i,
                    i,
                    i
            ));
        }

        TemplateProcessor template = createBaseTemplate()
                .setVariable("hasPaths", !allPaths.isEmpty());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 750; anchor-height: 700;">

                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Patrol Paths</p>
                            </div>
                        </div>

                        <div class="body">

                            <p class="page-description">Manage plugin-managed patrol paths</p>
                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Create New Path,description=Create a new named patrol path}}
                                <div class="form-row">
                                    <div style="flex-weight: 1;">
                                        {{@formField:id=new-path-name,label=Path Name,value=,placeholder=Enter path name...}}
                                    </div>
                                    <div class="spacer-h-sm"></div>
                                    <div style="flex-weight: 0; layout: center; padding-top: 18;">
                                        <button id="create-path-btn" class="secondary-button">Create</button>
                                    </div>
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Existing Paths,description=Edit or delete patrol paths}}
                                {{#if hasPaths}}
                                <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="anchor-height: 350;">
                """ + pathsHtml + """
                                </div>
                                {{else}}
                                <p style="color: #888888; font-size: 13;">No patrol paths created yet.</p>
                                {{/if}}
                            </div>

                        </div>

                        <div class="footer">
                            <button id="back-btn" class="secondary-button">Back</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupPatrolPathsListeners(page, playerRef, store, citizen, allPaths);

        page.open(store);
    }

    private void setupPatrolPathsListeners(@Nonnull PageBuilder page, @Nonnull PlayerRef playerRef,
                                           @Nonnull Store<EntityStore> store, @Nonnull CitizenData citizen,
                                           @Nonnull List<PatrolPath> paths) {
        final String[] newPathName = {""};

        page.addEventListener("new-path-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            newPathName[0] = ctx.getValue("new-path-name", String.class).orElse("").trim();
        });

        page.addEventListener("create-path-btn", CustomUIEventBindingType.Activating, event -> {
            String name = newPathName[0];
            if (name.isEmpty()) {
                playerRef.sendMessage(Message.raw("Enter a path name first.").color(Color.RED));
                return;
            }
            if (plugin.getCitizensManager().getPatrolManager().getPath(name) != null) {
                playerRef.sendMessage(Message.raw("A path with that name already exists.").color(Color.RED));
                return;
            }
            PatrolPath newPath = new PatrolPath(name, PatrolPath.LoopMode.LOOP);
            plugin.getCitizensManager().getPatrolManager().savePath(newPath);
            openPatrolPathEditorGUI(playerRef, store, citizen, name);
        });

        for (int i = 0; i < paths.size(); i++) {
            final int index = i;
            String name = paths.get(i).getName();
            page.addEventListener("edit-path-" + index, CustomUIEventBindingType.Activating, event -> {
                openPatrolPathEditorGUI(playerRef, store, citizen, name);
            });
            page.addEventListener("rename-path-" + index, CustomUIEventBindingType.Activating, event -> {
                openRenamePatrolPathGUI(playerRef, store, citizen, name);
            });
            page.addEventListener("delete-path-" + index, CustomUIEventBindingType.Activating, event -> {
                plugin.getCitizensManager().stopCitizenPatrol(citizen.getId());
                plugin.getCitizensManager().getPatrolManager().deletePath(name);
                String current = citizen.getPathConfig().getPluginPatrolPath();
                if (name.equals(current)) {
                    citizen.getPathConfig().setPluginPatrolPath("");
                    plugin.getCitizensManager().saveCitizen(citizen);
                }
                playerRef.sendMessage(Message.raw("Patrol path deleted.").color(Color.GREEN));
                openPatrolPathsGUI(playerRef, store, citizen);
            });
        }

        page.addEventListener("back-btn", CustomUIEventBindingType.Activating, event -> {
            openBehaviorsGUI(playerRef, store, citizen);
        });
    }

    private void openRenamePatrolPathGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                         @Nonnull CitizenData citizen, @Nonnull String oldPathName) {
        TemplateProcessor template = createBaseTemplate()
                .setVariable("oldName", escapeHtml(oldPathName))
                .setVariable("newName", escapeHtml(oldPathName));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 620; anchor-height: 360;">
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Rename Patrol Path</p>
                            </div>
                        </div>
                        <div class="body">
                            <p class="page-description">Rename this path and update all citizen references</p>
                            <div class="spacer-sm"></div>
                            <div class="section">
                                {{@formField:id=new-path-name,label=New Path Name,value={{$newName}},placeholder=Enter a new path name...}}
                                <div class="spacer-xs"></div>
                                <p class="form-hint">Current name: {{$oldName}}</p>
                            </div>
                        </div>
                        <div class="footer">
                            <button id="cancel-btn" class="secondary-button">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 210;">Rename Path</button>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] newName = {oldPathName};
        page.addEventListener("new-path-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            newName[0] = ctx.getValue("new-path-name", String.class).orElse(oldPathName).trim();
        });

        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, event -> {
            if (newName[0].isEmpty()) {
                playerRef.sendMessage(Message.raw("Please enter a new path name.").color(Color.RED));
                return;
            }
            if (newName[0].equals(oldPathName)) {
                playerRef.sendMessage(Message.raw("That is already the current path name.").color(Color.YELLOW));
                openPatrolPathsGUI(playerRef, store, citizen);
                return;
            }

            boolean renamed = plugin.getCitizensManager().renamePatrolPath(oldPathName, newName[0]);
            if (!renamed) {
                playerRef.sendMessage(Message.raw("Could not rename path. Check if the new name already exists.").color(Color.RED));
                return;
            }

            playerRef.sendMessage(Message.raw("Patrol path renamed to '" + newName[0] + "'.").color(Color.GREEN));
            openPatrolPathsGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openPatrolPathsGUI(playerRef, store, citizen));

        page.open(store);
    }

    public void openPatrolPathEditorGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                        @Nonnull CitizenData citizen, @Nonnull String pathName) {
        PatrolPath path = plugin.getCitizensManager().getPatrolManager().getPath(pathName);
        if (path == null) {
            openPatrolPathsGUI(playerRef, store, citizen);
            return;
        }

        List<PatrolWaypoint> waypoints = path.getWaypoints();
        List<IndexedWaypoint> indexedWaypoints = new ArrayList<>();
        for (int i = 0; i < waypoints.size(); i++) {
            indexedWaypoints.add(new IndexedWaypoint(i, waypoints.get(i)));
        }

        TemplateProcessor template = createBaseTemplate()
                .setVariable("pathName", escapeHtml(pathName))
                .setVariable("isLoop", path.getLoopMode() == PatrolPath.LoopMode.LOOP)
                .setVariable("waypoints", indexedWaypoints)
                .setVariable("hasWaypoints", !indexedWaypoints.isEmpty())
                .setVariable("waypointCount", indexedWaypoints.size());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 900; anchor-height: 750;">

                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Edit Path: {{$pathName}}</p>
                            </div>
                        </div>

                        <div class="body">

                            <p class="page-description">Manage waypoints and loop mode</p>
                            <div class="spacer-sm"></div>

                            <div class="section">
                                {{@sectionHeader:title=Loop Mode,description=How the citizen loops through waypoints}}
                                <div class="button-group">
                                    {{#if isLoop}}
                                    <button id="mode-loop" class="secondary-button" style="anchor-width: 140;">Loop</button>
                                    {{else}}
                                    <button id="mode-loop" class="secondary-button" style="anchor-width: 140;">Ping Pong</button>
                                    {{/if}}
                                </div>
                            </div>

                            <div class="spacer-md"></div>

                            <div class="section">
                                {{@sectionHeader:title=Waypoints ({{$waypointCount}}),description=Waypoints the citizen follows in order}}
                                {{#if hasWaypoints}}
                                <div class="list-container" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="anchor-height: 350;">
                                    {{#each waypoints}}
                                    <div class="list-item">
                                        <div style="flex-weight: 1;">
                                            <p style="font-size: 13; font-weight: bold;">#{{$index}}: X:{{$displayX}} Y:{{$displayY}} Z:{{$displayZ}}</p>
                                        </div>
                                        <div style="anchor-width: 110; flex-weight: 0;">
                                            {{@numberField:id=pause-{{$index}},label=Pause (s),value={{$pauseSeconds}},min=0,max=300,step=0.5,decimals=1}}
                                        </div>
                                        <div class="spacer-h-xs"></div>
                                        <button id="move-up-{{$index}}" class="secondary-button" style="anchor-width: 80; flex-weight: 0;">Up</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="move-down-{{$index}}" class="secondary-button" style="anchor-width: 100; flex-weight: 0;">Down</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="tp-wp-{{$index}}" class="secondary-button" style="anchor-width: 80; flex-weight: 0;">TP</button>
                                        <div class="spacer-h-xs"></div>
                                        <button id="delete-wp-{{$index}}" class="secondary-button" style="anchor-width: 110; flex-weight: 0;">Delete</button>
                                    </div>
                                    <div class="spacer-xs"></div>
                                    {{/each}}
                                </div>
                                {{else}}
                                <p style="color: #888888; font-size: 13;">No waypoints yet. Stand where you want a waypoint and click Add.</p>
                                {{/if}}
                                <div class="spacer-sm"></div>
                                <button id="add-waypoint-btn" class="secondary-button" style="anchor-width: 250;">Add Waypoint at my position</button>
                                <button id="get-waypoint-item-btn" class="secondary-button" style="anchor-width: 225;">Get Waypoint item</button>
                            </div>

                        </div>

                        <div class="footer">
                            <button id="back-btn" class="secondary-button">Back</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="secondary-button" style="anchor-width: 180;">Save Changes</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupPatrolPathEditorListeners(page, playerRef, store, citizen, path, indexedWaypoints);

        page.open(store);
    }

    private void setupPatrolPathEditorListeners(@Nonnull PageBuilder page, @Nonnull PlayerRef playerRef,
                                                @Nonnull Store<EntityStore> store, @Nonnull CitizenData citizen,
                                                @Nonnull PatrolPath path,
                                                @Nonnull List<IndexedWaypoint> indexedWaypoints) {
        final PatrolPath.LoopMode[] loopMode = {path.getLoopMode()};
        final float[] pauseValues = new float[indexedWaypoints.size()];
        for (int i = 0; i < indexedWaypoints.size(); i++) {
            pauseValues[i] = indexedWaypoints.get(i).getPauseSeconds();
        }

        page.addEventListener("mode-loop", CustomUIEventBindingType.Activating, event -> {
            if (path.getLoopMode() == PatrolPath.LoopMode.PING_PONG) {
                loopMode[0] = PatrolPath.LoopMode.LOOP;
                path.setLoopMode(PatrolPath.LoopMode.LOOP);
            }
            else {
                loopMode[0] = PatrolPath.LoopMode.PING_PONG;
                path.setLoopMode(PatrolPath.LoopMode.PING_PONG);
            }
            plugin.getCitizensManager().getPatrolManager().savePath(path);
            openPatrolPathEditorGUI(playerRef, store, citizen, path.getName());
        });

        for (int i = 0; i < indexedWaypoints.size(); i++) {
            final int idx = i;
            page.addEventListener("pause-" + i, CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("pause-" + idx, Double.class).ifPresent(v -> pauseValues[idx] = v.floatValue());
            });

            page.addEventListener("move-up-" + i, CustomUIEventBindingType.Activating, event -> {
                if (idx <= 0) {
                    return;
                }

                List<PatrolWaypoint> wps = path.getWaypoints();
                PatrolWaypoint current = wps.get(idx);
                wps.set(idx, wps.get(idx - 1));
                wps.set(idx - 1, current);
                plugin.getCitizensManager().getPatrolManager().savePath(path);
                openPatrolPathEditorGUI(playerRef, store, citizen, path.getName());
            });

            page.addEventListener("move-down-" + i, CustomUIEventBindingType.Activating, event -> {
                List<PatrolWaypoint> wps = path.getWaypoints();
                if (idx >= wps.size() - 1) {
                    return;
                }

                PatrolWaypoint current = wps.get(idx);
                wps.set(idx, wps.get(idx + 1));
                wps.set(idx + 1, current);
                plugin.getCitizensManager().getPatrolManager().savePath(path);
                openPatrolPathEditorGUI(playerRef, store, citizen, path.getName());
            });

            page.addEventListener("tp-wp-" + i, CustomUIEventBindingType.Activating, event -> {
                CitizenData foundCitizen = null;

                for (CitizenData c : plugin.getCitizensManager().getAllCitizens()) {

                    if (c.getPathConfig().getPluginPatrolPath().equals(path.getName())) {
                        foundCitizen = c;
                        break;
                    }
                }

                if (foundCitizen == null) {
                    playerRef.sendMessage(Message.raw("Failed to teleport. A citizen must use this path to teleport to it!").color(Color.RED));
                    return;
                }

                UUID citizenWorldUUID = foundCitizen.getWorldUUID();

                if (citizenWorldUUID == null) {
                    playerRef.sendMessage(Message.raw("Failed to teleport. Could not find the world!").color(Color.RED));
                    return;
                }

                World world = Universe.get().getWorld(citizenWorldUUID);
                if (world == null) {
                    playerRef.sendMessage(Message.raw("Failed to teleport. Could not find the world!").color(Color.RED));
                    return;
                }

                PatrolWaypoint wp = path.getWaypoints().get(indexedWaypoints.get(idx).getIndex());
                if (wp == null) {
                    playerRef.sendMessage(Message.raw("Failed to teleport. Could not find waypoint!").color(Color.RED));
                    return;
                }

                Vector3d tpPos = new Vector3d(wp.getX(), wp.getY(), wp.getZ());

                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    playerRef.sendMessage(Message.raw("Failed to teleport.").color(Color.RED));
                    return;
                }

                ref.getStore().addComponent(ref, Teleport.getComponentType(), new Teleport(world, tpPos, new Vector3f(0, 0, 0)));

                playerRef.sendMessage(Message.raw("Teleported to waypoint!").color(Color.GREEN));
            });

            page.addEventListener("delete-wp-" + i, CustomUIEventBindingType.Activating, event -> {
                plugin.getCitizensManager().getPatrolManager().removeWaypoint(path.getName(), idx);
                openPatrolPathEditorGUI(playerRef, store, citizen, path.getName());

                // Update each citizen with this path
                for (CitizenData patrolCitizen : plugin.getCitizensManager().getAllCitizens()) {
                    String patrolPath = patrolCitizen.getPathConfig().getPluginPatrolPath();
                    if (patrolPath.isEmpty() || !patrolPath.equals(path.getName())) {
                        continue;
                    }

                    plugin.getCitizensManager().startCitizenPatrol(patrolCitizen.getId(), patrolPath);
                }
            });
        }

        page.addEventListener("add-waypoint-btn", CustomUIEventBindingType.Activating, event -> {
            UUID worldUUID = playerRef.getWorldUuid();
            if (worldUUID == null) {
                playerRef.sendMessage(Message.raw("Could not determine your world.").color(Color.RED));
                return;
            }
            Vector3d pos = new Vector3d(playerRef.getTransform().getPosition());
            plugin.getCitizensManager().getPatrolManager().addWaypoint(
                    path.getName(), new PatrolWaypoint(pos.x, pos.y, pos.z, 0f));
            openPatrolPathEditorGUI(playerRef, store, citizen, path.getName());

            // Update each citizen with this path
            for (CitizenData patrolCitizen : plugin.getCitizensManager().getAllCitizens()) {
                String patrolPath = patrolCitizen.getPathConfig().getPluginPatrolPath();
                if (patrolPath.isEmpty() || !patrolPath.equals(path.getName())) {
                    continue;
                }

                plugin.getCitizensManager().startCitizenPatrol(patrolCitizen.getId(), patrolPath);
            }
        });

        page.addEventListener("get-waypoint-item-btn", CustomUIEventBindingType.Activating, (event, ctx) -> {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                playerRef.sendMessage(Message.raw("An error occurred.").color(Color.RED));
                return;
            }

            UUID worldUUID = playerRef.getWorldUuid();
            if (worldUUID == null) {
                playerRef.sendMessage(Message.raw("Could not determine your world.").color(Color.RED));
                return;
            }

            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
            if (player == null) {
                playerRef.sendMessage(Message.raw("An error occurred.").color(Color.RED));
                return;
            }

            ItemStack stack = new ItemStack("PatrolStick");

            ItemStack waypointItemStack = stack.withMetadata(
                    "HyCitizensPatrolStick",
                    Codec.STRING,
                    path.getName()
            );

            if (player.getInventory().getHotbar().canAddItemStack(waypointItemStack)) {
                player.getInventory().getHotbar().addItemStack(waypointItemStack);
                playerRef.sendMessage(Message.raw("You have received a patrol waypoint stick. Use LEFT click to open the waypoint menu, RIGHT click will add a waypoint to your position.").color(Color.GREEN));
            }
            else if (player.getInventory().getStorage().canAddItemStack(waypointItemStack)) {
                player.getInventory().getStorage().addItemStack(waypointItemStack);
                playerRef.sendMessage(Message.raw("You have received a patrol waypoint stick. Use LEFT click to open the waypoint menu, RIGHT click will add a waypoint to your position.").color(Color.GREEN));
            }
            else {
                playerRef.sendMessage(Message.raw("Your inventory is full.").color(Color.RED));
            }

            ctx.getPage().ifPresent(newPage -> newPage.close());
        });

        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, event -> {
            List<PatrolWaypoint> waypoints = path.getWaypoints();
            for (int i = 0; i < Math.min(pauseValues.length, waypoints.size()); i++) {
                waypoints.get(i).setPauseSeconds(pauseValues[i]);
            }
            path.setLoopMode(loopMode[0]);
            plugin.getCitizensManager().getPatrolManager().savePath(path);
            playerRef.sendMessage(Message.raw("Patrol path saved!").color(Color.GREEN));
            openPatrolPathsGUI(playerRef, store, citizen);
        });

        page.addEventListener("back-btn", CustomUIEventBindingType.Activating, event -> {
            openPatrolPathsGUI(playerRef, store, citizen);
        });
    }
}
