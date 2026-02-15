package com.electro.hycitizens.ui;

import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.*;
import com.electro.hycitizens.util.ConfigManager;
import com.hypixel.hytale.common.util.RandomUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class CitizensUI {
    private final HyCitizensPlugin plugin;

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

    private String generateGroupDropdownOptions(String selectedValue, List<String> allGroups) {
        StringBuilder sb = new StringBuilder();

        // Add "None" option for no group
        boolean noneSelected = selectedValue == null || selectedValue.isEmpty();
        sb.append("<option value=\"\"");
        if (noneSelected) {
            sb.append(" selected");
        }
        sb.append(">None</option>\n");

        // Add existing groups
        for (String groupName : allGroups) {
            boolean isSelected = groupName.equals(selectedValue);
            sb.append("<option value=\"").append(groupName).append("\"");
            if (isSelected) {
                sb.append(" selected");
            }
            sb.append(">").append(groupName).append("</option>\n");
        }

        return sb.toString();
    }

    public enum Tab {
        CREATE, MANAGE
    }

    public CitizensUI(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    private String sanitizeGroupId(String groupName) {
        return groupName.replace(" ", "-").replace("'", "").replace("\"", "");
    }

    private String getSharedStyles() {
        return """
                <style>
                    .main-container {
                        layout: top;
                        background-color: #0d1117(0.98);
                        border-radius: 12;
                    }
                
                    .header {
                        layout: center;
                        flex-weight: 0;
                        background-color: #161b22;
                        padding: 20;
                        border-radius: 12 12 0 0;
                    }
                
                    .header-content {
                        layout: top;
                        flex-weight: 0;
                    }
                
                    .header-title {
                        color: #e6edf3;
                        font-size: 24;
                        font-weight: bold;
                        text-align: center;
                    }
                
                    .header-subtitle {
                        color: #8b949e;
                        font-size: 12;
                        padding-top: 4;
                        text-align: center;
                    }
                
                    .body {
                        layout: top;
                        flex-weight: 1;
                        padding: 20;
                    }
                
                    .footer {
                        layout: center;
                        flex-weight: 0;
                        background-color: #161b22;
                        padding: 16;
                        border-radius: 0 0 12 12;
                    }
                
                    .card {
                        layout: top;
                        flex-weight: 0;
                        background-color: #161b22;
                        padding: 16;
                        border-radius: 8;
                    }
                
                    .card-header {
                        layout: center;
                        flex-weight: 0;
                        padding-bottom: 12;
                    }
                
                    .card-title {
                        color: #e6edf3;
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
                        background-color: #21262d(0.5);
                        padding: 16;
                        border-radius: 8;
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
                        color: #e6edf3;
                        font-size: 13;
                        font-weight: bold;
                    }
                
                    .section-description {
                        color: #8b949e;
                        font-size: 12;
                        padding-top: 4;
                        padding-bottom: 12;
                    }
                
                    .form-group {
                        layout: top;
                        flex-weight: 0;
                        padding-bottom: 6;
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
                        color: #e6edf3;
                        font-size: 12;
                        font-weight: bold;
                        padding-bottom: 6;
                    }
                
                    .form-label-optional {
                        color: #6e7681;
                        font-size: 12;
                        padding-left: 6;
                    }
                
                    .form-input {
                        flex-weight: 0;
                        anchor-height: 38;
                        background-color: #21262d;
                        border-radius: 6;
                    }
                
                    .form-input-small {
                        flex-weight: 0;
                        anchor-height: 38;
                        anchor-width: 120;
                        background-color: #21262d;
                        border-radius: 6;
                    }
                
                    .form-hint {
                        color: #6e7681;
                        font-size: 12;
                        padding-top: 4;
                    }
                
                    .form-hint-highlight {
                        color: #58a6ff;
                        font-size: 12;
                        padding-top: 4;
                    }
                
                    .checkbox-row {
                        layout: center;
                        flex-weight: 0;
                        padding-top: 8;
                        padding-bottom: 4;
                    }
                
                    .checkbox-label {
                        color: #e6edf3;
                        font-size: 12;
                        padding-left: -30;
                    }
                
                    .checkbox-description {
                        color: #8b949e;
                        font-size: 12;
                        padding-left: -30;
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
                
                    .btn-primary {
                        flex-weight: 0;
                        anchor-height: 40;
                        anchor-width: 140;
                        border-radius: 6;
                    }
                
                    .btn-secondary {
                        flex-weight: 0;
                        anchor-height: 40;
                        anchor-width: 140;
                        border-radius: 6;
                    }
                
                    .btn-danger {
                        flex-weight: 0;
                        anchor-height: 40;
                        anchor-width: 140;
                        border-radius: 6;
                    }
                
                    .btn-warning {
                        flex-weight: 0;
                        anchor-height: 40;
                        anchor-width: 140;
                        border-radius: 6;
                    }
                
                    .btn-info {
                        flex-weight: 0;
                        anchor-height: 40;
                        anchor-width: 140;
                        border-radius: 6;
                    }
                
                    .btn-ghost {
                        flex-weight: 0;
                        anchor-height: 40;
                        anchor-width: 140;
                        border-radius: 6;
                    }
                
                    .btn-small {
                        anchor-height: 32;
                        anchor-width: 100;
                    }
                
                    .btn-wide {
                        anchor-width: 200;
                    }
                
                    .btn-full {
                        flex-weight: 1;
                        anchor-width: 0;
                    }
                
                    .tab-container {
                        layout: left;
                        flex-weight: 0;
                        padding: 4;
                        border-radius: 8;
                    }
                
                    .tab-btn {
                        flex-weight: 1;
                        anchor-height: 36;
                        border-radius: 6;
                    }
                
                    .tab-active {
                    }
                
                    .list-container {
                        layout-mode: TopScrolling;
                        flex-weight: 1;
                        padding: 4 16 4 16;
                    }
                
                    .list-item {
                        layout: left;
                        flex-weight: 0;
                        background-color: #21262d;
                        padding: 14;
                        border-radius: 8;
                    }
                
                    .list-item-hover {
                        background-color: #30363d;
                    }
                
                    .list-item-content {
                        layout: top;
                        flex-weight: 1;
                        padding-left: 12;
                        padding-right: 12;
                    }
                
                    .list-item-title {
                        color: #e6edf3;
                        font-size: 14;
                        font-weight: bold;
                    }
                
                    .list-item-subtitle {
                        color: #8b949e;
                        font-size: 12;
                        padding-top: 2;
                    }
                
                    .list-item-meta {
                        color: #6e7681;
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
                        background-color: #21262d;
                        padding: 14;
                        border-radius: 8;
                    }
                
                    .stat-value {
                        color: #e6edf3;
                        font-size: 24;
                        font-weight: bold;
                    }
                
                    .stat-label {
                        color: #8b949e;
                        font-size: 12;
                        padding-top: 2;
                    }
                
                    .stat-change-positive {
                        color: #3fb950;
                        font-size: 12;
                    }
                
                    .stat-change-negative {
                        color: #f85149;
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
                        color: #8b949e;
                        font-size: 16;
                        text-align: center;
                        padding-top: 16;
                    }
                
                    .empty-state-description {
                        color: #6e7681;
                        font-size: 12;
                        text-align: center;
                        padding-top: 8;
                    }
                
                    .info-box {
                        layout: left;
                        flex-weight: 0;
                        background-color: #1f6feb(0.1);
                        padding: 12;
                        border-radius: 6;
                    }
                
                    .info-box-text {
                        color: #8b949e;
                        font-size: 12;
                        flex-weight: 1;
                    }
                
                    .divider {
                        flex-weight: 0;
                        anchor-height: 1;
                        background-color: #30363d;
                    }
                
                    .divider-vertical {
                        flex-weight: 0;
                        anchor-width: 1;
                        background-color: #30363d;
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
                        padding: 4;
                        border-radius: 8;
                        gap: 8;
                    }
                
                    .toggle-btn {
                        anchor-height: 36;
                        padding-left: 12;
                        padding-right: 12;
                        border-radius: 6;
                    }
                
                
                    .toggle-active {
                    }
                
                    .command-item {
                        layout: left;
                        flex-weight: 0;
                        background-color: #21262d;
                        padding: 12;
                        border-radius: 6;
                    }
                
                    .command-icon {
                        layout: center;
                        flex-weight: 0;
                        anchor-width: 32;
                        anchor-height: 32;
                        border-radius: 6;
                    }
                
                    .command-icon-server {
                    }
                
                    .command-icon-player {
                    }
                
                    .command-icon-text {
                        font-size: 14;
                    }
                
                    .command-icon-text-server {
                        color: #a371f7;
                    }
                
                    .command-icon-text-player {
                        color: #58a6ff;
                    }
                
                    .command-content {
                        layout: top;
                        flex-weight: 1;
                        padding-left: 10;
                        padding-right: 10;
                    }
                
                    .command-text {
                        color: #3fb950;
                        font-size: 12;
                        font-weight: bold;
                    }
                
                    .command-type {
                        color: #8b949e;
                        font-size: 12;
                        padding-top: 2;
                    }
                
                    .command-actions {
                        layout: left;
                        flex-weight: 0;
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
        private final String groupId;
        private final CitizenData citizen;

        public static ListItem forGroup(String groupName, String groupId) {
            return new ListItem(true, groupName, groupId, null);
        }

        public static ListItem forCitizen(CitizenData citizen) {
            return new ListItem(false, null, null, citizen);
        }

        private ListItem(boolean isGroup, String groupName, String groupId, CitizenData citizen) {
            this.isGroup = isGroup;
            this.groupName = groupName;
            this.groupId = groupId;
            this.citizen = citizen;
        }

        public boolean isGroup() { return isGroup; }
        public String getGroupName() { return groupName; }
        public String getGroupId() { return groupId; }
        public CitizenData getCitizen() { return citizen; }
    }

    public void openCitizensGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull Tab currentTab) {
        openCitizensGUI(playerRef, store, currentTab, "", null);
    }

    public void openCitizensGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull Tab currentTab, @Nonnull String searchQuery) {
        openCitizensGUI(playerRef, store, currentTab, searchQuery, null);
    }

    public void openCitizensGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull Tab currentTab, @Nonnull String searchQuery, @Nullable String viewingGroup) {
        List<CitizenData> allCitizens = plugin.getCitizensManager().getAllCitizens();

        // Filter citizens by search query
        String lowerSearchQuery = searchQuery.toLowerCase().trim();
        List<CitizenData> filteredCitizens = allCitizens;

        if (!lowerSearchQuery.isEmpty()) {
            filteredCitizens = allCitizens.stream()
                    .filter(c -> c.getName().toLowerCase().contains(lowerSearchQuery)
                            || c.getId().toLowerCase().contains(lowerSearchQuery)
                            || c.getGroup().toLowerCase().contains(lowerSearchQuery))
                    .collect(java.util.stream.Collectors.toList());
        }

        // Organize citizens by group
        Map<String, List<CitizenData>> citizensByGroup = new LinkedHashMap<>();
        List<CitizenData> ungroupedCitizens = new ArrayList<>();

        for (CitizenData citizen : filteredCitizens) {
            String group = citizen.getGroup();
            if (group == null || group.isEmpty()) {
                ungroupedCitizens.add(citizen);
            } else {
                citizensByGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(citizen);
            }
        }

        // Sort groups alphabetically
        List<String> sortedGroups = new ArrayList<>(citizensByGroup.keySet());
        Collections.sort(sortedGroups);

        // Create unified list
        List<ListItem> unifiedList = new ArrayList<>();
        boolean isViewingSpecificGroup = viewingGroup != null && !viewingGroup.isEmpty();

        if (isViewingSpecificGroup) {
            // Viewing a specific group - show only citizens from that group
            List<CitizenData> groupCitizens = citizensByGroup.get(viewingGroup);
            if (groupCitizens != null) {
                for (CitizenData citizen : groupCitizens) {
                    unifiedList.add(ListItem.forCitizen(citizen));
                }
            }
        } else {
            // Viewing all - show groups first, then ungrouped citizens
            for (String groupName : sortedGroups) {
                unifiedList.add(ListItem.forGroup(groupName, sanitizeGroupId(groupName)));
            }
            for (CitizenData citizen : ungroupedCitizens) {
                unifiedList.add(ListItem.forCitizen(citizen));
            }
            // When searching, also show individual citizens from groups for easy access
            if (!lowerSearchQuery.isEmpty()) {
                for (String groupName : sortedGroups) {
                    List<CitizenData> groupCitizens = citizensByGroup.get(groupName);
                    if (groupCitizens != null) {
                        for (CitizenData citizen : groupCitizens) {
                            unifiedList.add(ListItem.forCitizen(citizen));
                        }
                    }
                }
            }
        }

        TemplateProcessor template = createBaseTemplate()
                .setVariable("citizenCount", allCitizens.size())
                .setVariable("isCreateTab", currentTab == Tab.CREATE)
                .setVariable("isManageTab", currentTab == Tab.MANAGE)
                .setVariable("unifiedList", unifiedList)
                .setVariable("hasCitizens", !filteredCitizens.isEmpty())
                .setVariable("searchQuery", searchQuery)
                .setVariable("viewingGroup", viewingGroup != null ? viewingGroup : "")
                .setVariable("isViewingGroup", isViewingSpecificGroup);

        String html = template.process(getSharedStyles() + """
            <div class="page-overlay">
                <div class="main-container" style="anchor-width: 960; anchor-height: 900;">
            
                    <!-- Header -->
                    <div class="header">
                        <div class="header-content">
                            <p class="header-title">Citizens Manager</p>
                            <p class="header-subtitle">Create and manage interactive NPCs for your server</p>
                        </div>
                    </div>
            
                    <!-- Body -->
                    <div class="body">
            
                        <!-- Stats Row -->
                        <div class="stats-row">
                            {{@statCard:value={{$citizenCount}},label=Total Citizens}}
                        </div>
            
                        <div class="spacer-md"></div>
            
                        <!-- Tabs -->
                        <div class="tab-container">
                            <button id="tab-create" class="tab-btn{{#if isCreateTab}} tab-active{{/if}}">Create</button>
                            <button id="tab-manage" class="tab-btn{{#if isManageTab}} tab-active{{/if}}">Manage</button>
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
                                        <button id="start-create" class="btn-primary" style="anchor-width: 220;">Start Creating</button>
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
                                <button id="search-btn" class="btn-primary" style="anchor-width: 150; anchor-height: 40;">Search</button>
                            </div>
                        </div>
                        
                        <div class="spacer-sm"></div>
                        
                        {{#if isViewingGroup}}
                        <!-- Viewing Specific Group -->
                        <div class="form-row">
                            <button id="back-to-all" class="btn-secondary" style="anchor-width: 120;">Back</button>
                            <div style="flex-weight: 1; layout: center;">
                                <p style="font-size: 18px; font-weight: bold; color: #FFFFFF;">Group: {{$viewingGroup}}</p>
                            </div>
                        </div>
                        <div class="spacer-sm"></div>
                        {{/if}}
                        
                        {{#if hasCitizens}}
                        <!-- Unified List -->
                        <div class="list-container" style="anchor-height: 640;">
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
                                    <button id="tp-{{$citizen.id}}" class="btn-info btn-small" style="anchor-width: 110;">TP</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="edit-{{$citizen.id}}" class="btn-info btn-small" style="anchor-width: 110;">Edit</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="clone-{{$citizen.id}}" class="btn-secondary btn-small" style="anchor-width: 120;">Clone</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="remove-{{$citizen.id}}" class="btn-danger btn-small" style="anchor-width: 140;">Remove</button>
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
                                    <button id="view-group-{{$groupId}}" class="btn-primary btn-small" style="anchor-width: 110;">View</button>
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
        openCreateCitizenGUI(playerRef, store, true, "", 0, false, false, "",
                1.0f, "", "", false, false, "", true, true, "");
    }

    public void openCreateCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                     boolean isPlayerModel, String name, float nametagOffset, boolean hideNametag, boolean hideNpc,
                                     String modelId, float scale, String permission, String permMessage, boolean useLiveSkin,
                                     boolean preserveState, String skinUsername, boolean rotateTowardsPlayer,
                                     boolean fKeyInteraction, String group) {

        List<String> allGroups = plugin.getCitizensManager().getAllGroups();
        String groupOptionsHTML = generateGroupDropdownOptions(group, allGroups);

        TemplateProcessor template = createBaseTemplate()
                .setVariable("isPlayerModel", isPlayerModel)
                .setVariable("name", name)
                .setVariable("nametagOffset", nametagOffset)
                .setVariable("hideNametag", hideNametag)
                .setVariable("hideNpc", hideNpc)
                .setVariable("group", group)
                .setVariable("groupOptions", groupOptionsHTML)
                .setVariable("modelId", modelId.isEmpty() ? "PlayerTestModel_V" : modelId)
                .setVariable("scale", scale)
                .setVariable("permission", permission)
                .setVariable("permMessage", permMessage)
                .setVariable("useLiveSkin", useLiveSkin)
                .setVariable("skinUsername", skinUsername)
                .setVariable("rotateTowardsPlayer", rotateTowardsPlayer)
                .setVariable("fKeyInteraction", fKeyInteraction)
                .setVariable("entityOptions", generateEntityDropdownOptions(modelId.isEmpty() ? "PlayerTestModel_V" : modelId));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container" style="anchor-width: 900; anchor-height: 820;">
                
                        <!-- Header -->
                        <div class="header">
                            <div class="header-content">
                                <p class="header-title">Create New Citizen</p>
                                <p class="header-subtitle">Configure your new NPC's appearance and behavior</p>
                            </div>
                        </div>
                
                        <!-- Body -->
                        <div class="body" style="layout-mode: TopScrolling;">
                
                            <!-- Info Box -->
                            {{@infoBox:text=The citizen will spawn at your current position and rotation}}
                
                            <div class="spacer-md"></div>
                
                            <!-- Basic Information Section -->
                            <div class="section">
                                {{@sectionHeader:title=Basic Information,description=Set the citizen's name and display settings}}
                
                                <div class="form-row" style="horizontal-align: center;">
                                     <div class="form-col-fixed" style="anchor-width: 400;">
                                         <div class="form-group">
                                             <p class="form-label">Citizen Name *</p>
                                             <input type="text" id="citizen-name" class="form-input" value="{{$name}}"\s
                                                    placeholder="Enter a display name" />
                                             <p class="form-hint" style="text-align: center;">This will be displayed above the NPC</p>
                                             <p class="form-hint" style="text-align: center;">You can type "\\n" between lines</p>
                                         </div>
                                     </div>
                
                                     <div class="spacer-h-md"></div>
                
                                     <div class="form-col-fixed" style="anchor-width: 150;">
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
                                        <p class="checkbox-description">Hide the name displayed above the citizen</p>
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
                                    <button id="type-player" class="toggle-btn{{#if isPlayerModel}} toggle-active{{/if}}">Player Model</button>
                                    <button id="type-entity" class="toggle-btn{{#if !isPlayerModel}} toggle-active{{/if}}">Other Entity</button>
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
                                                <button id="get-player-skin-btn" class="btn-secondary btn-small" style="anchor-width: 160;">Use My Skin</button>
                                            </div>
                                            <p class="form-hint" style="text-align: center;">Leave empty to use your current skin</p>
                                        </div>

                                        <div class="spacer-sm"></div>

                                        <div class="form-group">
                                            <button id="random-skin-btn" class="btn-primary" style="anchor-width: 225;">Random Skin</button>
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
                                                   min="0.01" max="500" step="0.25"
                                                   data-hyui-max-decimal-places="2" />
                                            <p class="form-hint">Default: 1.0 (normal size)</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Interaction Section -->
                            <div class="section">
                                {{@sectionHeader:title=Interaction,description=Configure how players interact with this citizen}}
                
                                <div class="checkbox-row">
                                    <input type="checkbox" id="f-key-interaction" {{#if fKeyInteraction}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Enable 'F' Key to Interact</p>
                                        <p class="checkbox-description">Allow players to use the 'F' key to interact instead of just left clicking</p>
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
                            <button id="cancel-btn" class="btn-ghost">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="create-btn" class="btn-primary btn-wide">Create</button>
                        </div>
                
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupCreateCitizenListeners(page, playerRef, store, isPlayerModel, name, nametagOffset, hideNametag, hideNpc,
                modelId, scale, permission, permMessage, useLiveSkin, skinUsername, rotateTowardsPlayer, fKeyInteraction, group);

        page.open(store);
    }

    public void openEditCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull CitizenData citizen) {
        List<String> allGroups = plugin.getCitizensManager().getAllGroups();
        String groupOptionsHTML = generateGroupDropdownOptions(citizen.getGroup(), allGroups);

        TemplateProcessor template = createBaseTemplate()
                .setVariable("citizen", citizen)
                .setVariable("isPlayerModel", citizen.isPlayerModel())
                .setVariable("useLiveSkin", citizen.isUseLiveSkin())
                .setVariable("rotateTowardsPlayer", citizen.getRotateTowardsPlayer())
                .setVariable("fKeyInteraction", citizen.getFKeyInteractionEnabled())
                .setVariable("hideNametag", citizen.isHideNametag())
                .setVariable("hideNpc", citizen.isHideNpc())
                .setVariable("groupOptions", groupOptionsHTML)
                .setVariable("entityOptions", generateEntityDropdownOptions(citizen.getModelId()));

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container" style="anchor-width: 900; anchor-height: 850;">
                
                        <!-- Header -->
                        <div class="header">
                            <div class="header-content">
                                <p class="header-title">Edit Citizen</p>
                                <p class="header-subtitle">ID: {{$citizen.id}}</p>
                            </div>
                        </div>
                
                        <!-- Body -->
                        <div class="body" style="layout-mode: TopScrolling;">
                
                            <!-- Basic Information Section -->
                            <div class="section">
                                {{@sectionHeader:title=Basic Information,description=Set the citizen's name and display settings}}
                
                                <div class="form-row" style="horizontal-align: center;">
                                    <div class="form-col-fixed" style="anchor-width: 400;">
                                        <div class="form-group">
                                            <p class="form-label">Citizen Name *</p>
                                            <input type="text" id="citizen-name" class="form-input" value="{{$citizen.name}}"\s
                                                   placeholder="Enter a display name" maxlength="32" />
                                            <p class="form-hint" style="text-align: center;">This will be displayed above the NPC</p>
                                            <p class="form-hint" style="text-align: center;">You can type "\\n" between lines</p>
                                        </div>
                                    </div>
                
                                    <div class="spacer-h-md"></div>
                
                                    <div class="form-col-fixed" style="anchor-width: 150;">
                                        <div class="form-group">
                                            <p class="form-label">Nametag Offset</p>
                                            <input type="number" id="nametag-offset" class="form-input"
                                                   value="{{$citizen.nametagOffset}}"
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
                                        <p class="checkbox-description">Hide the name displayed above the citizen</p>
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
                                    <button id="type-player" class="toggle-btn{{#if isPlayerModel}} toggle-active{{/if}}">Player Model</button>
                                    <button id="type-entity" class="toggle-btn{{#if !isPlayerModel}} toggle-active{{/if}}">Other Entity</button>
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
                                                <button id="get-player-skin-btn" class="btn-secondary btn-small" style="anchor-width: 160;">Use My Skin</button>
                                            </div>
                                            <p class="form-hint" style="text-align: center;">Leave empty to use your current skin</p>
                                        </div>

                                        <div class="spacer-sm"></div>

                                        <div class="form-group">
                                            <button id="random-skin-btn" class="btn-primary" style="anchor-width: 225;">Random Skin</button>
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
                                                   min="0.01" max="500" step="0.25"
                                                   data-hyui-max-decimal-places="2" />
                                            <p class="form-hint">Default: 1.0 (normal size)</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Interaction Section -->
                            <div class="section">
                                {{@sectionHeader:title=Interaction,description=Configure how players interact with this citizen}}
                
                                <div class="checkbox-row">
                                    <input type="checkbox" id="f-key-interaction" {{#if fKeyInteraction}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Enable 'F' Key to Interact</p>
                                        <p class="checkbox-description">Allow players to use the 'F' key to interact instead of just left clicking</p>
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
                                {{@sectionHeader:title=Quick Actions,description= }}
                
                                <div class="form-row">
                                    <button id="edit-commands-btn" class="btn-info" style="anchor-width: 200; anchor-height: 44;">Edit Commands</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="set-items-btn" class="btn-warning" style="anchor-width: 200; anchor-height: 44;">Set Items</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="change-position-btn" class="btn-secondary" style="anchor-width: 210; anchor-height: 44;">Update Position</button>
                                </div>
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    <button id="behaviors-btn" class="btn-info" style="anchor-width: 200; anchor-height: 44;">Behaviors</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="messages-btn" class="btn-info" style="anchor-width: 200; anchor-height: 44;">Messages</button>
                                </div>
                            </div>
                
                            <div class="spacer-lg"></div>
                
                        </div>
                
                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="btn-ghost">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-btn" class="btn-primary" style="anchor-width: 220;">Save Changes</button>
                        </div>
                
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupEditCitizenListeners(page, playerRef, store, citizen);

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
            this.animationName = ab.getAnimationName();
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

        public IndexedMessage(int index, CitizenMessage msg) {
            this.index = index;
            this.message = msg.getMessage();
            this.truncated = msg.getMessage().length() > 60 ? msg.getMessage().substring(0, 57) + "..." : msg.getMessage();
        }
    }

    public static class IndexedCommandAction {
        private final int index;
        private final String command;
        private final boolean runAsServer;

        public IndexedCommandAction(int index, CommandAction action) {
            this.index = index;
            this.command = action.getCommand();
            this.runAsServer = action.isRunAsServer();
        }

        public int getIndex() {
            return index;
        }

        public String getCommand() {
            return command;
        }

        public boolean isRunAsServer() {
            return runAsServer;
        }
    }

    public void openCommandActionsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                      @Nonnull String citizenId, @Nonnull List<CommandAction> actions,
                                      boolean isCreating) {

        List<IndexedCommandAction> indexedActions = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            indexedActions.add(new IndexedCommandAction(i, actions.get(i)));
        }

        TemplateProcessor template = createBaseTemplate()
                .setVariable("actions", indexedActions)
                .setVariable("hasActions", !actions.isEmpty())
                .setVariable("actionCount", actions.size());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container" style="anchor-width: 850; anchor-height: 700;">
                
                        <!-- Header -->
                        <div class="header">
                            <div class="header-content">
                                <p class="header-title">Command Actions</p>
                                <p class="header-subtitle">Configure commands that execute when players interact ({{$actionCount}} commands)</p>
                            </div>
                        </div>
                
                        <!-- Body -->
                        <div class="body">
                
                            <!-- Add Command Section -->
                            <div class="section">
                                {{@sectionHeader:title=Add New Command}}
                
                                <div class="form-row">
                                    <input type="text" id="new-command" class="form-input" value="" 
                                           placeholder="give {PlayerName} Rock_Gem_Diamond"
                                           style="flex-weight: 1;" />
                                    <div class="spacer-h-sm"></div>
                                    <button id="add-command-btn" class="btn-primary" style="anchor-width: 120;">Add</button>
                                </div>
                
                                <div class="spacer-md"></div>
                
                                <!-- Help Info -->
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12;"><span style="color: #58a6ff;">Variables:</span> Use {PlayerName} for player's name, {CitizenName} for citizen's name</p>
                                        <div class="spacer-xs"></div>
                                        <p style="color: #8b949e; font-size: 12;">Commands run as <span style="color: #58a6ff;">PLAYER</span> by default. Click toggle to run as <span style="color: #a371f7;">SERVER</span>.</p>
                                    </div>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Commands List -->
                            {{#if hasActions}}
                            <div class="list-container" style="anchor-height: 320;">
                                {{#each actions}}
                                <div class="command-item">
                                    <div class="command-icon {{#if runAsServer}}command-icon-server{{else}}command-icon-player{{/if}}">
                                        <p class="command-icon-text {{#if runAsServer}}command-icon-text-server{{else}}command-icon-text-player{{/if}}">{{#if runAsServer}}S{{else}}P{{/if}}</p>
                                    </div>
                                    <div class="command-content">
                                        <p class="command-text">/{{$command}}</p>
                                        <p class="command-type">Runs as {{#if runAsServer}}SERVER{{else}}PLAYER{{/if}}</p>
                                    </div>
                                    <div class="command-actions">
                                        <button id="edit-cmd-{{$index}}" class="btn-info btn-small">Edit</button>
                                        <div class="spacer-h-sm"></div>
                                        <button id="toggle-{{$index}}" class="btn-secondary btn-small">Toggle</button>
                                        <div class="spacer-h-sm"></div>
                                        <button id="delete-{{$index}}" class="btn-danger btn-small">Delete</button>
                                    </div>
                                </div>
                                <div class="spacer-sm"></div>
                                {{/each}}
                            </div>
                            {{else}}
                            <div class="empty-state">
                                <div class="empty-state-content">
                                    <p class="empty-state-title">No Commands Added</p>
                                    <p class="empty-state-description">Add commands above to execute when players interact with this citizen.</p>
                                </div>
                            </div>
                            {{/if}}
                
                        </div>
                
                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="btn-ghost">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="done-btn" class="btn-primary">Done</button>
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

            // Back button listener
            if (viewingGroup != null && !viewingGroup.isEmpty()) {
                page.addEventListener("back-to-all", CustomUIEventBindingType.Activating, event ->
                        openCitizensGUI(playerRef, store, Tab.MANAGE, "", null));
            }

            // Register event listeners for all items in the unified list
            for (ListItem item : unifiedList) {
                if (item.isGroup()) {
                    // Group view listener - only if we're not already viewing a specific group
                    String groupId = item.getGroupId();
                    String groupName = item.getGroupName();
                    page.addEventListener("view-group-" + groupId, CustomUIEventBindingType.Activating, event ->
                            openCitizensGUI(playerRef, store, Tab.MANAGE, "", groupName));
                } else {
                    // Citizen action listeners
                    CitizenData citizen = item.getCitizen();
                    final String cid = citizen.getId();

                    page.addEventListener("tp-" + cid, CustomUIEventBindingType.Activating, event -> {
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

                        playerRef.getReference().getStore().addComponent(playerRef.getReference(),
                                Teleport.getComponentType(), new Teleport(world, new Vector3d(citizen.getPosition()),
                                        new Vector3f(0, 0, 0)));

                        playerRef.sendMessage(Message.raw("Teleported to citizen '" + citizen.getName() + "'!").color(Color.GREEN));
                    });

                    page.addEventListener("edit-" + cid, CustomUIEventBindingType.Activating, event ->
                            openEditCitizenGUI(playerRef, store, citizen));

                    page.addEventListener("clone-" + cid, CustomUIEventBindingType.Activating, event -> {
                        Vector3d position = new Vector3d(playerRef.getTransform().getPosition());
                        Vector3f rotation = new Vector3f(playerRef.getTransform().getRotation());

                        UUID worldUUID = playerRef.getWorldUuid();
                        if (worldUUID == null) {
                            playerRef.sendMessage(Message.raw("Failed to clone citizen!").color(Color.RED));
                            return;
                        }

                        PlayerSkin playerSkin = null;
                        if (citizen.getCachedSkin() != null) {
                            playerSkin = new PlayerSkin(citizen.getCachedSkin());
                        }

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
                                new ArrayList<>(citizen.getCommandActions()),
                                citizen.isPlayerModel(),
                                citizen.isUseLiveSkin(),
                                citizen.getSkinUsername(),
                                playerSkin,
                                citizen.getLastSkinUpdate(),
                                citizen.getRotateTowardsPlayer()
                        );

                        clonedCitizen.setNametagOffset(citizen.getNametagOffset());
                        clonedCitizen.setHideNametag(citizen.isHideNametag());
                        clonedCitizen.setHideNpc(citizen.isHideNpc());
                        clonedCitizen.setFKeyInteractionEnabled(citizen.getFKeyInteractionEnabled());
                        clonedCitizen.setNpcHelmet(citizen.getNpcHelmet());
                        clonedCitizen.setNpcChest(citizen.getNpcChest());
                        clonedCitizen.setNpcGloves(citizen.getNpcGloves());
                        clonedCitizen.setNpcLeggings(citizen.getNpcLeggings());
                        clonedCitizen.setNpcHand(citizen.getNpcHand());
                        clonedCitizen.setNpcOffHand(citizen.getNpcOffHand());

                        // Copy behaviors and messages
                        clonedCitizen.setAnimationBehaviors(new ArrayList<>(citizen.getAnimationBehaviors()));
                        clonedCitizen.setMovementBehavior(new MovementBehavior(
                                citizen.getMovementBehavior().getType(),
                                citizen.getMovementBehavior().getWalkSpeed(),
                                citizen.getMovementBehavior().getWanderRadius(),
                                citizen.getMovementBehavior().getWanderWidth(),
                                citizen.getMovementBehavior().getWanderDepth()));
                        clonedCitizen.setMessagesConfig(new MessagesConfig(
                                citizen.getMessagesConfig().getMessages(),
                                citizen.getMessagesConfig().getSelectionMode(),
                                citizen.getMessagesConfig().isEnabled()));

                        // Copy group
                        clonedCitizen.setGroup(citizen.getGroup());

                        // Copy attitude and damage settings
                        clonedCitizen.setAttitude(citizen.getAttitude());
                        clonedCitizen.setTakesDamage(citizen.isTakesDamage());

                        // Copy respawn settings
                        clonedCitizen.setRespawnOnDeath(citizen.isRespawnOnDeath());
                        clonedCitizen.setRespawnDelaySeconds(citizen.getRespawnDelaySeconds());

                        plugin.getCitizensManager().addCitizen(clonedCitizen, true);
                        playerRef.sendMessage(Message.raw("Citizen '" + citizen.getName() + "' cloned at your position!").color(Color.GREEN));
                        openCitizensGUI(playerRef, store, Tab.MANAGE);
                    });

                    page.addEventListener("remove-" + cid, CustomUIEventBindingType.Activating, event -> {
                        plugin.getCitizensManager().removeCitizen(cid);
                        playerRef.sendMessage(Message.raw("Citizen '" + citizen.getName() + "' removed!").color(Color.GREEN));
                        openCitizensGUI(playerRef, store, Tab.MANAGE);
                    });
                }
            }
        }
    }

    private void setupCreateCitizenListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                             boolean initialIsPlayerModel, String initialName, float initialNametagOffset,
                                             boolean initialHideNametag, boolean initialHideNpc, String initialModelId, float initialScale,
                                             String initialPermission, String initialPermMessage, boolean initialUseLiveSkin,
                                             String initialSkinUsername, boolean initialRotateTowardsPlayer,
                                             boolean initialFKeyInteraction, String initialGroup) {
        final List<CommandAction> tempActions = new ArrayList<>();
        final String[] currentName = {initialName};
        final float[] nametagOffset = {initialNametagOffset};
        final boolean[] hideNametag = {initialHideNametag};
        final boolean[] hideNpc = {initialHideNpc};
        final String[] currentModelId = {initialModelId.isEmpty() ? "PlayerTestModel_V" : initialModelId};
        final float[] currentScale = {initialScale};
        final String[] currentPermission = {initialPermission};
        final String[] currentPermMessage = {initialPermMessage};
        final boolean[] isPlayerModel = {initialIsPlayerModel};
        final boolean[] useLiveSkin = {initialUseLiveSkin};
        final String[] skinUsername = {initialSkinUsername};
        final PlayerSkin[] cachedSkin = {null};
        final boolean[] rotateTowardsPlayer = {initialRotateTowardsPlayer};
        final boolean[] FKeyInteraction = {initialFKeyInteraction};
        final String[] currentGroup = {initialGroup};

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
                    playerRef.sendMessage(Message.raw("Random skin generated successfully!").color(Color.GREEN));
                } catch (Exception e) {
                    playerRef.sendMessage(Message.raw("Failed to generate random skin: " + e.getMessage()).color(Color.RED));
                }
            });
        }

        page.addEventListener("rotate-towards-player", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            rotateTowardsPlayer[0] = ctx.getValue("rotate-towards-player", Boolean.class).orElse(false);
        });

        page.addEventListener("nametag-offset", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("nametag-offset", Double.class)
                    .ifPresent(val -> nametagOffset[0] = val.floatValue());

            if (nametagOffset[0] == 0.0f) {
                ctx.getValue("nametag-offset", String.class)
                        .ifPresent(val -> {
                            try {
                                nametagOffset[0] = Float.parseFloat(val);
                            } catch (NumberFormatException e) {
                            }
                        });
            }
        });

        page.addEventListener("hide-nametag-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            hideNametag[0] = ctx.getValue("hide-nametag-check", Boolean.class).orElse(false);
        });

        page.addEventListener("hide-npc-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            hideNpc[0] = ctx.getValue("hide-npc-check", Boolean.class).orElse(false);
        });

        page.addEventListener("f-key-interaction", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            FKeyInteraction[0] = ctx.getValue("f-key-interaction", Boolean.class).orElse(true);
        });

        page.addEventListener("citizen-scale", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("citizen-scale", Double.class)
                    .ifPresent(val -> currentScale[0] = val.floatValue());

            if (currentScale[0] == 1.0f) {
                ctx.getValue("citizen-scale", String.class)
                        .ifPresent(val -> {
                            try {
                                currentScale[0] = Float.parseFloat(val);
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
            openCreateCitizenGUI(playerRef, store, true, currentName[0], nametagOffset[0], hideNametag[0], hideNpc[0], currentModelId[0],
                    currentScale[0], currentPermission[0], currentPermMessage[0], useLiveSkin[0], true,
                    skinUsername[0], rotateTowardsPlayer[0], FKeyInteraction[0], currentGroup[0]);
        });

        page.addEventListener("type-entity", CustomUIEventBindingType.Activating, (event, ctx) -> {
            openCreateCitizenGUI(playerRef, store, false, currentName[0], nametagOffset[0], hideNametag[0], hideNpc[0], currentModelId[0],
                    currentScale[0], currentPermission[0], currentPermMessage[0], useLiveSkin[0], true,
                    skinUsername[0], rotateTowardsPlayer[0], FKeyInteraction[0], currentGroup[0]);
        });

        page.addEventListener("create-btn", CustomUIEventBindingType.Activating, event -> {
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
            citizen.setHideNpc(hideNpc[0]);
            citizen.setFKeyInteractionEnabled(FKeyInteraction[0]);
            citizen.setGroup(currentGroup[0]);

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
                                           CitizenData citizen) {
        final String[] currentName = {citizen.getName()};
        final float[] nametagOffset = {citizen.getNametagOffset()};
        final boolean[] hideNametag = {citizen.isHideNametag()};
        final boolean[] hideNpc = {citizen.isHideNpc()};
        final String[] currentModelId = {citizen.getModelId()};
        final float[] currentScale = {citizen.getScale()};
        final String[] currentPermission = {citizen.getRequiredPermission()};
        final String[] currentPermMessage = {citizen.getNoPermissionMessage()};
        final boolean[] isPlayerModel = {citizen.isPlayerModel()};
        final boolean[] useLiveSkin = {citizen.isUseLiveSkin()};
        final boolean[] rotateTowardsPlayer = {citizen.getRotateTowardsPlayer()};
        final boolean[] FKeyInteraction = {citizen.getFKeyInteractionEnabled()};
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
                    playerRef.sendMessage(Message.raw("Random skin generated successfully!").color(Color.GREEN));
                } catch (Exception e) {
                    playerRef.sendMessage(Message.raw("Failed to generate random skin: " + e.getMessage()).color(Color.RED));
                }
            });
        }

        page.addEventListener("rotate-towards-player", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            rotateTowardsPlayer[0] = ctx.getValue("rotate-towards-player", Boolean.class).orElse(false);
        });

        page.addEventListener("nametag-offset", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("nametag-offset", Double.class)
                    .ifPresent(val -> nametagOffset[0] = val.floatValue());

            if (nametagOffset[0] == 0.0f) {
                ctx.getValue("nametag-offset", String.class)
                        .ifPresent(val -> {
                            try {
                                nametagOffset[0] = Float.parseFloat(val);
                            } catch (NumberFormatException e) {
                            }
                        });
            }
        });

        page.addEventListener("hide-nametag-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            hideNametag[0] = ctx.getValue("hide-nametag-check", Boolean.class).orElse(false);
        });

        page.addEventListener("hide-npc-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            hideNpc[0] = ctx.getValue("hide-npc-check", Boolean.class).orElse(false);
        });

        page.addEventListener("f-key-interaction", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            FKeyInteraction[0] = ctx.getValue("f-key-interaction", Boolean.class).orElse(true);
        });

        page.addEventListener("citizen-scale", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("citizen-scale", Double.class)
                    .ifPresent(val -> currentScale[0] = val.floatValue());

            if (currentScale[0] == 1.0f) {
                ctx.getValue("citizen-scale", String.class)
                        .ifPresent(val -> {
                            try {
                                currentScale[0] = Float.parseFloat(val);
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
            isPlayerModel[0] = true;
            citizen.setPlayerModel(true);
            openEditCitizenGUI(playerRef, store, citizen);
        });

        page.addEventListener("type-entity", CustomUIEventBindingType.Activating, (event, ctx) -> {
            isPlayerModel[0] = false;
            citizen.setPlayerModel(false);
            openEditCitizenGUI(playerRef, store, citizen);
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

        page.addEventListener("messages-btn", CustomUIEventBindingType.Activating, event -> {
            openMessagesGUI(playerRef, store, citizen);
        });

        page.addEventListener("save-btn", CustomUIEventBindingType.Activating, event -> {
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

            if (skinUsername[0].isEmpty()) {
                skinUsername[0] = playerRef.getUsername();
            }

            String oldSkinUsername = citizen.getSkinUsername();

            citizen.setName(name);
            citizen.setModelId(modelId);
            citizen.setScale(currentScale[0]);
            citizen.setRequiredPermission(currentPermission[0].trim());
            citizen.setNoPermissionMessage(currentPermMessage[0].trim());
            citizen.setPlayerModel(isPlayerModel[0]);
            citizen.setUseLiveSkin(useLiveSkin[0]);
            citizen.setRotateTowardsPlayer(rotateTowardsPlayer[0]);
            citizen.setFKeyInteractionEnabled(FKeyInteraction[0]);
            citizen.setSkinUsername(skinUsername[0].trim());
            citizen.setNametagOffset(nametagOffset[0]);
            citizen.setHideNametag(hideNametag[0]);
            citizen.setHideNpc(hideNpc[0]);
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
                } else if (skinUsername[0].trim().startsWith("random_") && citizen.getCachedSkin() != null) {
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
        final String[] currentCommand = {""};

        page.addEventListener("new-command", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentCommand[0] = ctx.getValue("new-command", String.class).orElse("");
        });

        page.addEventListener("add-command-btn", CustomUIEventBindingType.Activating, event -> {
            String command = currentCommand[0].trim();

            if (command.isEmpty()) {
                playerRef.sendMessage(Message.raw("Please enter a command!").color(Color.RED));
                return;
            }

            if (command.startsWith("/")) {
                command = command.substring(1);
            }

            actions.add(new CommandAction(command, false));
            playerRef.sendMessage(Message.raw("Command added!").color(Color.GREEN));

            openCommandActionsGUI(playerRef, store, citizenId, actions, isCreating);
        });

        for (int i = 0; i < actions.size(); i++) {
            final int index = i;

            page.addEventListener("edit-cmd-" + i, CustomUIEventBindingType.Activating, event -> {
                CommandAction action = actions.get(index);
                openEditCommandGUI(playerRef, store, citizenId, actions, isCreating, action, index);
            });

            page.addEventListener("toggle-" + i, CustomUIEventBindingType.Activating, event -> {
                CommandAction action = actions.get(index);
                action.setRunAsServer(!action.isRunAsServer());
                playerRef.sendMessage(Message.raw("Command will now run as " +
                        (action.isRunAsServer() ? "SERVER" : "PLAYER")).color(Color.GREEN));

                openCommandActionsGUI(playerRef, store, citizenId, actions, isCreating);
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
        TemplateProcessor template = createBaseTemplate()
                .setVariable("command", command.getCommand())
                .setVariable("runAsServer", command.isRunAsServer());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container" style="anchor-width: 650; anchor-height: 400;">

                        <!-- Header -->
                        <div class="header">
                            <div class="header-content">
                                <p class="header-title">Edit Command</p>
                                <p class="header-subtitle">Modify the command that executes on interaction</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body">

                            <!-- Command Input -->
                            <div class="section">
                                {{@sectionHeader:title=Command}}
                                <input type="text" id="command-input" class="form-input" value="{{$command}}"
                                       placeholder="Enter command (without leading /)" />
                                <p class="form-hint">The command to execute. Do not include the leading /</p>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Run As Server Toggle -->
                            <div class="section">
                                {{@sectionHeader:title=Execution Mode}}
                                <div class="checkbox-row">
                                    <input type="checkbox" id="run-as-server" {{#if runAsServer}}checked{{/if}} />
                                    <div style="layout: top; flex-weight: 0; text-align: center;">
                                        <p class="checkbox-label">Run as Server</p>
                                        <p class="checkbox-description">Execute as console command instead of player</p>
                                    </div>
                                </div>
                            </div>

                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="btn-ghost">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-cmd-btn" class="btn-primary" style="anchor-width: 200;">Save Changes</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] commandText = {command.getCommand()};
        final boolean[] runAsServer = {command.isRunAsServer()};

        page.addEventListener("command-input", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            commandText[0] = ctx.getValue("command-input", String.class).orElse("");
        });

        page.addEventListener("run-as-server", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            runAsServer[0] = ctx.getValue("run-as-server", Boolean.class).orElse(false);
        });

        page.addEventListener("save-cmd-btn", CustomUIEventBindingType.Activating, event -> {
            if (commandText[0].trim().isEmpty()) {
                playerRef.sendMessage(Message.raw("Command cannot be empty!").color(Color.RED));
                return;
            }

            actions.set(editIndex, new CommandAction(commandText[0].trim(), runAsServer[0]));
            playerRef.sendMessage(Message.raw("Command updated!").color(Color.GREEN));
            openCommandActionsGUI(playerRef, store, citizenId, actions, isCreating);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openCommandActionsGUI(playerRef, store, citizenId, actions, isCreating);
        });

        page.open(store);
    }

    public void openBehaviorsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                 @Nonnull CitizenData citizen) {
        List<AnimationBehavior> anims = citizen.getAnimationBehaviors();
        List<IndexedAnimationBehavior> indexedAnims = new ArrayList<>();
        for (int i = 0; i < anims.size(); i++) {
            indexedAnims.add(new IndexedAnimationBehavior(i, anims.get(i)));
        }

        MovementBehavior mb = citizen.getMovementBehavior();

        String attitude = citizen.getAttitude();
        boolean takesDamage = citizen.isTakesDamage();

        TemplateProcessor template = createBaseTemplate()
                .setVariable("animations", indexedAnims)
                .setVariable("hasAnimations", !anims.isEmpty())
                .setVariable("animCount", anims.size())
                .setVariable("moveType", mb.getType())
                .setVariable("isIdle", "IDLE".equals(mb.getType()))
                .setVariable("isWander", "WANDER".equals(mb.getType()))
                .setVariable("isWanderCircle", "WANDER_CIRCLE".equals(mb.getType()))
                .setVariable("isWanderRect", "WANDER_RECT".equals(mb.getType()))
                .setVariable("walkSpeed", mb.getWalkSpeed())
                .setVariable("wanderRadius", mb.getWanderRadius())
                .setVariable("wanderWidth", mb.getWanderWidth())
                .setVariable("wanderDepth", mb.getWanderDepth())
                .setVariable("isPassive", "PASSIVE".equals(attitude))
                .setVariable("isNeutral", "NEUTRAL".equals(attitude))
                .setVariable("isAggressive", "AGGRESSIVE".equals(attitude))
                .setVariable("takesDamage", takesDamage)
                .setVariable("isAnyWander", !"IDLE".equals(mb.getType()))
                .setVariable("isR0", mb.getWanderRadius() < 1)
                .setVariable("isR1", mb.getWanderRadius() >= 1 && mb.getWanderRadius() < 2)
                .setVariable("isR2", mb.getWanderRadius() >= 2 && mb.getWanderRadius() < 3)
                .setVariable("isR5", mb.getWanderRadius() >= 3 && mb.getWanderRadius() <= 7)
                .setVariable("isR10", mb.getWanderRadius() > 7 && mb.getWanderRadius() <= 12)
                .setVariable("isR15", mb.getWanderRadius() > 12)
                .setVariable("respawnOnDeath", citizen.isRespawnOnDeath())
                .setVariable("respawnDelay", citizen.getRespawnDelaySeconds());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container" style="anchor-width: 950; anchor-height: 1000;">
                
                        <!-- Header -->
                        <div class="header">
                            <div class="header-content">
                                <p class="header-title">Behaviors</p>
                                <p class="header-subtitle">Configure movement and animations for this citizen</p>
                            </div>
                        </div>
                
                        <!-- Body -->
                        <div class="body">
                
                            <!-- Attitude Section -->
                            <div class="section">
                                {{@sectionHeader:title=Attitude,description=How the citizen reacts to players}}
                
                                <div class="form-row">
                                    <button id="att-passive" class="{{#if isPassive}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 180; anchor-height: 38;">Passive</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="att-neutral" class="{{#if isNeutral}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 180; anchor-height: 38;">Neutral</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="att-aggressive" class="{{#if isAggressive}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 180; anchor-height: 38;">Aggressive</button>
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
                            </div>
                
                            <div class="spacer-sm"></div>
                
                            <!-- Takes Damage Section -->
                            <div class="section">
                                <p style="color: #c9d1d9; font-size: 12; font-weight: bold; text-align: center;">Takes Damage</p>
                                <div class="spacer-xs"></div>
                                <div style="layout: center;">
                                    <button id="toggle-damage" class="{{#if takesDamage}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 140; anchor-height: 40;">{{#if takesDamage}}Enabled{{else}}Disabled{{/if}}</button>
                                </div>
                                <div class="spacer-xs"></div>
                                <p style="color: #8b949e; font-size: 12; text-align: center;">When enabled, the citizen can take damage and be killed by players.</p>
                            </div>
                
                            <div class="spacer-sm"></div>
                
                            <!-- Respawn Section -->
                            <div class="section">
                                <p style="color: #c9d1d9; font-size: 12; font-weight: bold; text-align: center;">Respawn on Death</p>
                                <div class="spacer-xs"></div>
                                <div style="layout: center;">
                                    <button id="toggle-respawn" class="{{#if respawnOnDeath}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 140; anchor-height: 40;">{{#if respawnOnDeath}}Enabled{{else}}Disabled{{/if}}</button>
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
                                    <button id="move-idle" class="{{#if isIdle}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 38;">Idle</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="move-wander" class="{{#if isWander}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 38;">Wander</button>
                                    <!-- <div class="spacer-h-sm"></div>
                                    <button id="move-wander-circle" class="{{#if isWanderCircle}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 38;">Wander Circle</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="move-wander-rect" class="{{#if isWanderRect}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 38;">Wander Rect</button> -->
                                </div>
                
                                <div class="spacer-sm"></div>
                
                                {{#if isIdle}}
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12; text-align: center;">The citizen will stay in place and not move.</p>
                                    </div>
                                </div>
                                {{/if}}
                
                                {{#if isAnyWander}}
                                <div class="spacer-sm"></div>
                                <p style="color: #c9d1d9; font-size: 12; font-weight: bold; text-align: center;">Wander Radius</p>
                                <div class="card-body">
                                    <p style="color: #8b949e; font-size: 12; text-align: center;">Note: The wander system uses Hytale's built in wander system. While Citizens usually stay close to the selected radius, they sometimes go a bit further.</p>
                                </div>
                                <div class="spacer-xs"></div>
                                <div class="form-row">
                                    <!-- <button id="radius-0" class="{{#if isR0}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 162; anchor-height: 46;">0 Blocks</button> -->
                                    <button id="radius-1" class="{{#if isR1}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 130; anchor-height: 46;">1 Block</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="radius-2" class="{{#if isR2}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 46;">2 Blocks</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="radius-5" class="{{#if isR5}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 46;">5 Blocks</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="radius-10" class="{{#if isR10}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 160; anchor-height: 46;">10 Blocks</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="radius-15" class="{{#if isR15}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 160; anchor-height: 46;">15 Blocks</button>
                                </div>
                                {{/if}}
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Animations Section -->
                            <div class="section">
                                {{@sectionHeader:title=Animations,description=Configure animations that play on various triggers}}
                
                                <button id="add-animation-btn" class="btn-primary" style="anchor-width: 250; anchor-height: 45;">Add Animation</button>
                
                                <div class="spacer-sm"></div>
                
                                {{#if hasAnimations}}
                                <div class="list-container" style="anchor-height: 220;">
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
                                            <button id="edit-anim-{{$index}}" class="btn-secondary btn-small">Edit</button>
                                            <div class="spacer-h-sm"></div>
                                            <button id="delete-anim-{{$index}}" class="btn-danger btn-small">Delete</button>
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
                
                        </div>
                
                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="btn-ghost">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="done-btn" class="btn-primary" style="anchor-width: 160;">Done</button>
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
        List<AnimationBehavior> anims = new ArrayList<>(citizen.getAnimationBehaviors());

        // Attitude buttons
        page.addEventListener("att-passive", CustomUIEventBindingType.Activating, event -> {
            citizen.setAttitude("PASSIVE");
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("att-neutral", CustomUIEventBindingType.Activating, event -> {
            citizen.setAttitude("NEUTRAL");
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("att-aggressive", CustomUIEventBindingType.Activating, event -> {
            citizen.setAttitude("AGGRESSIVE");
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        // Takes damage toggle
        page.addEventListener("toggle-damage", CustomUIEventBindingType.Activating, event -> {
            citizen.setTakesDamage(!citizen.isTakesDamage());
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        // Respawn toggle
        page.addEventListener("toggle-respawn", CustomUIEventBindingType.Activating, event -> {
            citizen.setRespawnOnDeath(!citizen.isRespawnOnDeath());
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        // Respawn delay input (only exists when respawn is enabled)
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
            citizen.setMovementBehavior(new MovementBehavior("IDLE", walkSpeed[0], wanderRadius[0], wanderWidth[0], wanderDepth[0]));
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

        page.addEventListener("move-wander", CustomUIEventBindingType.Activating, event -> {
            citizen.setMovementBehavior(new MovementBehavior("WANDER", walkSpeed[0], wanderRadius[0], wanderWidth[0], wanderDepth[0]));
            plugin.getCitizensManager().saveCitizen(citizen);
            openBehaviorsGUI(playerRef, store, citizen);
        });

//        page.addEventListener("move-wander-circle", CustomUIEventBindingType.Activating, event -> {
//            citizen.setMovementBehavior(new MovementBehavior("WANDER_CIRCLE", walkSpeed[0], wanderRadius[0], wanderWidth[0], wanderDepth[0]));
//            plugin.getCitizensManager().saveCitizen(citizen);
//            openBehaviorsGUI(playerRef, store, citizen);
//        });
//
//        page.addEventListener("move-wander-rect", CustomUIEventBindingType.Activating, event -> {
//            citizen.setMovementBehavior(new MovementBehavior("WANDER_RECT", walkSpeed[0], wanderRadius[0], wanderWidth[0], wanderDepth[0]));
//            plugin.getCitizensManager().saveCitizen(citizen);
//            openBehaviorsGUI(playerRef, store, citizen);
//        });

        // Radius buttons
        boolean isAnyWander = !"IDLE".equals(moveType);
        if (isAnyWander) {
//            page.addEventListener("radius-0", CustomUIEventBindingType.Activating, event -> {
//                citizen.setMovementBehavior(new MovementBehavior(moveType, walkSpeed[0], 0, wanderWidth[0], wanderDepth[0]));
//                plugin.getCitizensManager().saveCitizen(citizen);
//                openBehaviorsGUI(playerRef, store, citizen);
//            });

            page.addEventListener("radius-1", CustomUIEventBindingType.Activating, event -> {
                citizen.setMovementBehavior(new MovementBehavior(moveType, walkSpeed[0], 1, wanderWidth[0], wanderDepth[0]));
                plugin.getCitizensManager().saveCitizen(citizen);
                openBehaviorsGUI(playerRef, store, citizen);
            });

            page.addEventListener("radius-2", CustomUIEventBindingType.Activating, event -> {
                citizen.setMovementBehavior(new MovementBehavior(moveType, walkSpeed[0], 2, wanderWidth[0], wanderDepth[0]));
                plugin.getCitizensManager().saveCitizen(citizen);
                openBehaviorsGUI(playerRef, store, citizen);
            });

            page.addEventListener("radius-5", CustomUIEventBindingType.Activating, event -> {
                citizen.setMovementBehavior(new MovementBehavior(moveType, walkSpeed[0], 5, wanderWidth[0], wanderDepth[0]));
                plugin.getCitizensManager().saveCitizen(citizen);
                openBehaviorsGUI(playerRef, store, citizen);
            });

            page.addEventListener("radius-10", CustomUIEventBindingType.Activating, event -> {
                citizen.setMovementBehavior(new MovementBehavior(moveType, walkSpeed[0], 10, wanderWidth[0], wanderDepth[0]));
                plugin.getCitizensManager().saveCitizen(citizen);
                openBehaviorsGUI(playerRef, store, citizen);
            });

            page.addEventListener("radius-15", CustomUIEventBindingType.Activating, event -> {
                citizen.setMovementBehavior(new MovementBehavior(moveType, walkSpeed[0], 15, wanderWidth[0], wanderDepth[0]));
                plugin.getCitizensManager().saveCitizen(citizen);
                openBehaviorsGUI(playerRef, store, citizen);
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
                .setVariable("animName", currentAnimName)
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
                .setVariable("stopAnimName", currentStopAnimName)
                .setVariable("stopAnimNameIsEmpty", stopAnimNameIsEmpty)
                .setVariable("hasDefaultForSlot", defaultAnimForSlot != null)
                .setVariable("defaultAnimName", defaultAnimForSlot != null ? defaultAnimForSlot : "");

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container" style="anchor-width: 750; anchor-height: 900;">

                        <!-- Header -->
                        <div class="header">
                            <div class="header-content">
                                <p class="header-title">{{#if isEditing}}Edit Animation{{else}}Add Animation{{/if}}</p>
                                <p class="header-subtitle">Configure when and how an animation plays</p>
                            </div>
                        </div>
                
                        <!-- Body -->
                        <div class="body">
                
                            <!-- Trigger Type -->
                            <div class="section">
                                {{@sectionHeader:title=Trigger Type}}
                                <p style="color: #58a6ff; font-size: 12; text-align: center; margin-bottom: 8px;">
                                    Selected: {{#if isDefault}}Default{{/if}}{{#if isOnInteract}}On Interact{{/if}}{{#if isOnAttack}}On Attack{{/if}}{{#if isProxEnter}}Proximity Enter{{/if}}{{#if isProxExit}}Proximity Exit{{/if}}{{#if isTimed}}Timed{{/if}}
                                </p>
                                <div class="form-row">
                                    <button id="type-default" class="{{#if isDefault}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 45;">Default</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="type-interact" class="{{#if isOnInteract}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 200; anchor-height: 45;">On Interact</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="type-attack" class="{{#if isOnAttack}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 45;">On Attack</button>
                                </div>
                                <div class="spacer-sm"></div>
                                <div class="form-row">
                                    <button id="type-prox-enter" class="{{#if isProxEnter}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 215; anchor-height: 45;">Proximity Enter</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="type-prox-exit" class="{{#if isProxExit}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 215; anchor-height: 45;">Proximity Exit</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="type-timed" class="{{#if isTimed}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 45;">Timed</button>
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
                                    <button id="slot-0" class="{{#if isSlot0}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 45;">Movement</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="slot-1" class="{{#if isSlot1}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 120; anchor-height: 45;">Status</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="slot-2" class="{{#if isSlot2}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 120; anchor-height: 45;">Action</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="slot-3" class="{{#if isSlot3}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 110; anchor-height: 45;">Face</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="slot-4" class="{{#if isSlot4}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 110; anchor-height: 45;">Emote</button>
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
                            <button id="cancel-btn" class="btn-ghost">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-anim-btn" class="btn-primary" style="anchor-width: 200;">{{#if isEditing}}Save Changes{{else}}Add Animation{{/if}}</button>
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
                    <div class="main-container" style="anchor-width: 850; anchor-height: 700;">
                
                        <!-- Header -->
                        <div class="header">
                            <div class="header-content">
                                <p class="header-title">Messages</p>
                                <p class="header-subtitle">Configure messages sent on interaction ({{$messageCount}} messages)</p>
                            </div>
                        </div>
                
                        <!-- Body -->
                        <div class="body">
                
                            <!-- Add Message Section -->
                            <div class="section">
                                {{@sectionHeader:title=Add New Message}}
                
                                <div class="form-row">
                                    <input type="text" id="new-message" class="form-input" value=""
                                           placeholder="Enter message text with optional color codes..."
                                           style="flex-weight: 1;" />
                                    <div class="spacer-h-sm"></div>
                                    <button id="add-message-btn" class="btn-primary" style="anchor-width: 120;">Add</button>
                                </div>
                
                                <div class="spacer-sm"></div>
                
                                <div class="card">
                                    <div class="card-body">
                                        <p style="color: #8b949e; font-size: 12;"><span style="color: #58a6ff;">Colors:</span> {RED}, {GREEN}, {BLUE}, {YELLOW}, {#HEX} for colored text</p>
                                        <div class="spacer-xs"></div>
                                        <p style="color: #8b949e; font-size: 12;"><span style="color: #58a6ff;">Variables:</span> {PlayerName} for player's name, {CitizenName} for citizen's name</p>
                                    </div>
                                </div>
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Selection Mode -->
                            <div class="section">
                                {{@sectionHeader:title=Selection Mode,description=How messages are chosen when the citizen is interacted with}}
                                <div class="form-row">
                                    <button id="mode-random" class="{{#if isRandom}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 38;">Random</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="mode-sequential" class="{{#if isSequential}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 175; anchor-height: 38;">Sequential</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="mode-all" class="{{#if isAll}}btn-primary{{else}}btn-secondary{{/if}}" style="anchor-width: 150; anchor-height: 38;">All</button>
                                </div>
                                <div class="spacer-xs"></div>
                                {{#if isRandom}}
                                <p style="color: #8b949e; font-size: 12; style="text-align: center;">A random message is picked each interaction.</p>
                                {{/if}}
                                {{#if isSequential}}
                                <p style="color: #8b949e; font-size: 12; style="text-align: center;">Messages cycle in order for each player.</p>
                                {{/if}}
                                {{#if isAll}}
                                <p style="color: #8b949e; font-size: 12; style="text-align: center;">All messages are sent at once on each interaction.</p>
                                {{/if}}
                            </div>
                
                            <div class="spacer-md"></div>
                
                            <!-- Messages List -->
                            {{#if hasMessages}}
                            <div class="list-container" style="anchor-height: 220;">
                                {{#each messages}}
                                <div class="command-item">
                                    <div class="command-icon command-icon-player">
                                        <p class="command-icon-text command-icon-text-player" style="font-size: 8;">M</p>
                                    </div>
                                    <div class="command-content">
                                        <p class="command-text">{{$truncated}}</p>
                                    </div>
                                    <div class="command-actions">
                                        <button id="edit-msg-{{$index}}" class="btn-info btn-small">Edit</button>
                                        <div class="spacer-h-sm"></div>
                                        <button id="delete-msg-{{$index}}" class="btn-danger btn-small">Delete</button>
                                    </div>
                                </div>
                                <div class="spacer-sm"></div>
                                {{/each}}
                            </div>
                            {{else}}
                            <div class="empty-state">
                                <div class="empty-state-content">
                                    <p class="empty-state-title">No Messages</p>
                                    <p class="empty-state-description">Add messages above to send when players interact with this citizen.</p>
                                </div>
                            </div>
                            {{/if}}
                
                        </div>
                
                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="btn-ghost">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="done-btn" class="btn-primary" style="anchor-width: 110;">Done</button>
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
        final String[] currentMessage = {""};
        List<CitizenMessage> msgs = new ArrayList<>(citizen.getMessagesConfig().getMessages());

        page.addEventListener("new-message", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentMessage[0] = ctx.getValue("new-message", String.class).orElse("");
        });

        page.addEventListener("add-message-btn", CustomUIEventBindingType.Activating, event -> {
            String msg = currentMessage[0].trim();

            if (msg.isEmpty()) {
                playerRef.sendMessage(Message.raw("Please enter a message!").color(Color.RED));
                return;
            }

            msgs.add(new CitizenMessage(msg));
            MessagesConfig mc = citizen.getMessagesConfig();
            citizen.setMessagesConfig(new MessagesConfig(msgs, mc.getSelectionMode(), mc.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            playerRef.sendMessage(Message.raw("Message added!").color(Color.GREEN));
            openMessagesGUI(playerRef, store, citizen);
        });

        // Selection mode buttons
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

        // Edit and Delete message buttons
        for (int i = 0; i < msgs.size(); i++) {
            final int index = i;

            page.addEventListener("edit-msg-" + i, CustomUIEventBindingType.Activating, event -> {
                CitizenMessage msg = msgs.get(index);
                openEditMessageGUI(playerRef, store, citizen, msg, index);
            });

            page.addEventListener("delete-msg-" + i, CustomUIEventBindingType.Activating, event -> {
                msgs.remove(index);
                MessagesConfig mc = citizen.getMessagesConfig();
                citizen.setMessagesConfig(new MessagesConfig(msgs, mc.getSelectionMode(), mc.isEnabled()));
                plugin.getCitizensManager().saveCitizen(citizen);
                playerRef.sendMessage(Message.raw("Message removed!").color(Color.GREEN));
                openMessagesGUI(playerRef, store, citizen);
            });
        }

        // Done
        page.addEventListener("done-btn", CustomUIEventBindingType.Activating, event -> {
            playerRef.sendMessage(Message.raw("Messages saved!").color(Color.GREEN));
            openEditCitizenGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openEditCitizenGUI(playerRef, store, citizen);
        });
    }

    public void openEditMessageGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                   @Nonnull CitizenData citizen, @Nonnull CitizenMessage message, int editIndex) {
        TemplateProcessor template = createBaseTemplate()
                .setVariable("message", message.getMessage());

        String html = template.process(getSharedStyles() + """
                <div class="page-overlay">
                    <div class="main-container" style="anchor-width: 650; anchor-height: 450;">

                        <!-- Header -->
                        <div class="header">
                            <div class="header-content">
                                <p class="header-title">Edit Message</p>
                                <p class="header-subtitle">Modify the message sent on interaction</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body">

                            <!-- Message Input -->
                            <div class="section">
                                {{@sectionHeader:title=Message Text}}
                                <input type="text" id="message-input" class="form-input" value="{{$message}}"
                                       placeholder="Enter message text with optional color codes..." />
                                <p class="form-hint">The message to send when interacting</p>
                            </div>

                            <div class="spacer-md"></div>

                            <!-- Color Reference -->
                            <div class="card">
                                <div class="card-body">
                                    <p style="color: #8b949e; font-size: 12;"><span style="color: #58a6ff;">Colors:</span> {RED}, {GREEN}, {BLUE}, {YELLOW}, {#HEX} for colored text</p>
                                    <div class="spacer-xs"></div>
                                    <p style="color: #8b949e; font-size: 12;"><span style="color: #58a6ff;">Variables:</span> {PlayerName} for player's name, {CitizenName} for citizen's name</p>
                                </div>
                            </div>

                        </div>

                        <!-- Footer -->
                        <div class="footer">
                            <button id="cancel-btn" class="btn-ghost">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="save-msg-btn" class="btn-primary" style="anchor-width: 200;">Save Changes</button>
                        </div>

                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        final String[] messageText = {message.getMessage()};

        page.addEventListener("message-input", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            messageText[0] = ctx.getValue("message-input", String.class).orElse("");
        });

        page.addEventListener("save-msg-btn", CustomUIEventBindingType.Activating, event -> {
            if (messageText[0].trim().isEmpty()) {
                playerRef.sendMessage(Message.raw("Message cannot be empty!").color(Color.RED));
                return;
            }

            List<CitizenMessage> msgs = new ArrayList<>(citizen.getMessagesConfig().getMessages());
            msgs.set(editIndex, new CitizenMessage(messageText[0].trim()));
            MessagesConfig mc = citizen.getMessagesConfig();
            citizen.setMessagesConfig(new MessagesConfig(msgs, mc.getSelectionMode(), mc.isEnabled()));
            plugin.getCitizensManager().saveCitizen(citizen);
            playerRef.sendMessage(Message.raw("Message updated!").color(Color.GREEN));
            openMessagesGUI(playerRef, store, citizen);
        });

        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            openMessagesGUI(playerRef, store, citizen);
        });

        page.open(store);
    }
}