package com.electro.hycitizens.ui;

import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.CommandAction;
import com.electro.hycitizens.util.ConfigManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CitizensUI {
    private final HyCitizensPlugin plugin;

    // Common entity types for the dropdown
    private static final List<String> COMMON_ENTITIES = Arrays.asList(
            "Player",
            "PlayerTestModel_V",
            "Sheep",
            "Chicken",
            "Cow",
            "Pig",
            "Wolf",
            "Trork",
            "Kweebec",
            "Feran",
            "Scarrak",
            "Goblin",
            "Skeleton",
            "Zombie"
    );

    // Helper method to generate dropdown options HTML (avoids template iteration issues with String lists)
    private String generateEntityDropdownOptions(String selectedValue) {
        StringBuilder sb = new StringBuilder();
        for (String entity : COMMON_ENTITIES) {
            boolean isSelected = entity.equals(selectedValue);
            sb.append("<option value=\"").append(entity).append("\"");
            if (isSelected) {
                sb.append(" selected");
            }
            sb.append(">").append(entity).append("</option>\n");
        }
        return sb.toString();
    }

    public enum Tab {
        CREATE, MANAGE
    }

    public CitizensUI(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    private String getSharedStyles() {
        return """
            <style>                
                /* Primary Colors */
                /* Background Dark:    #0d1117 */
                /* Background Medium:  #161b22 */
                /* Background Light:   #21262d */
                /* Surface:            #30363d */
                /* Border:             #484f58 */
                
                /* Accent Colors */
                /* Primary:            #58a6ff (Blue) */
                /* Success:            #3fb950 (Green) */
                /* Warning:            #d29922 (Orange) */
                /* Danger:             #f85149 (Red) */
                /* Info:               #a371f7 (Purple) */
                
                /* Text Colors */
                /* Text Primary:       #e6edf3 */
                /* Text Secondary:     #8b949e */
                /* Text Muted:         #6e7681 */
                
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
                    layout: left;
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
                    layout: left;
                    flex-weight: 0;
                }
                
                .section-title {
                    color: #e6edf3;
                    font-size: 13;
                    font-weight: bold;
                }
                
                .section-description {
                    color: #8b949e;
                    font-size: 11;
                    padding-top: 4;
                    padding-bottom: 12;
                }
                
                .form-group {
                    layout: top;
                    flex-weight: 0;
                }
                
                .form-row {
                    layout: left;
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
                    font-size: 10;
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
                    font-size: 10;
                    padding-top: 4;
                }
                
                .form-hint-highlight {
                    color: #58a6ff;
                    font-size: 10;
                    padding-top: 4;
                }
                
                .checkbox-row {
                    layout: left;
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
                    font-size: 10;
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
                    padding: 4;
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
                    font-size: 11;
                    padding-top: 2;
                }
                
                .list-item-meta {
                    color: #6e7681;
                    font-size: 10;
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
                    font-size: 11;
                    padding-top: 2;
                }
                
                .stat-change-positive {
                    color: #3fb950;
                    font-size: 10;
                }
                
                .stat-change-negative {
                    color: #f85149;
                    font-size: 10;
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
                    font-size: 11;
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
                    layout: left;
                    flex-weight: 0;
                    padding: 4;
                    border-radius: 8;
                }
                
                .toggle-btn {
                    flex-weight: 1;
                    anchor-height: 36;
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
                    font-size: 10;
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
                // Stat card component
                .registerComponent("statCard", """
                    <div class="stat-card">
                        <p class="stat-value">{{$value}}</p>
                        <p class="stat-label">{{$label}}</p>
                    </div>
                    """)

                // Form field component
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

                // Number field component
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

                // Checkbox component
                .registerComponent("checkbox", """
                    <div class="checkbox-row">
                        <input type="checkbox" id="{{$id}}" {{#if checked}}checked{{/if}} />
                        <div style="layout: top; flex-weight: 1;">
                            <p class="checkbox-label">{{$label}}</p>
                            {{#if description}}
                            <p class="checkbox-description">{{$description}}</p>
                            {{/if}}
                        </div>
                    </div>
                    """)

                // Info box component
                .registerComponent("infoBox", """
                    <div class="info-box">
                        <p class="info-box-text">{{$text}}</p>
                    </div>
                    """)

                // Section header component
                .registerComponent("sectionHeader", """
                    <div class="section-header">
                        <p class="section-title">{{$title}}</p>
                    </div>
                    {{#if description}}
                    <p class="section-description">{{$description}}</p>
                    {{else}}
                    <div class="spacer-sm"></div>
                    {{/if}}
                    """);
    }

    public void openCitizensGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull Tab currentTab) {
        List<CitizenData> citizens = plugin.getCitizensManager().getAllCitizens();

        TemplateProcessor template = createBaseTemplate()
                .setVariable("citizenCount", citizens.size())
                .setVariable("isCreateTab", currentTab == Tab.CREATE)
                .setVariable("isManageTab", currentTab == Tab.MANAGE)
                .setVariable("citizens", citizens)
                .setVariable("hasCitizens", !citizens.isEmpty());

        String html = template.process(getSharedStyles() + """
            <div class="page-overlay">
                <div class="main-container" style="anchor-width: 960; anchor-height: 600;">
                    
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
                        {{#if hasCitizens}}
                        <div class="list-container" style="anchor-height: 340;">
                            {{#each citizens}}
                            <div class="list-item">
                                <div class="list-item-content">
                                    <p class="list-item-title">{{$name}}</p>
                                    <p class="list-item-subtitle">Model: {{$modelId}} | Scale: {{$scale}}</p>
                                    <p class="list-item-meta">ID: {{$id}}</p>
                                </div>
                                <div class="list-item-actions">
                                    <button id="edit-{{$id}}" class="btn-info btn-small" style="anchor-width: 80;">Edit</button>
                                    <div class="spacer-h-sm"></div>
                                    <button id="remove-{{$id}}" class="btn-danger btn-small" style="anchor-width: 140;">Remove</button>
                                </div>
                            </div>
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

        setupMainEventListeners(page, playerRef, store, currentTab, citizens);

        page.open(store);
    }

    public void openCreateCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store) {
        openCreateCitizenGUI(playerRef, store, true, "", 0, false, "",
                1.0f, "", "", false, false, "", true);
    }

    public void openCreateCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                     boolean isPlayerModel, String name, float nametagOffset, boolean hideNametag,
                                     String modelId, float scale, String permission, String permMessage, boolean useLiveSkin,
                                     boolean preserveState, String skinUsername, boolean rotateTowardsPlayer) {

        TemplateProcessor template = createBaseTemplate()
                .setVariable("isPlayerModel", isPlayerModel)
                .setVariable("name", name)
                .setVariable("nametagOffset", nametagOffset)
                .setVariable("hideNametag", hideNametag)
                .setVariable("modelId", modelId.isEmpty() ? "PlayerTestModel_V" : modelId)
                .setVariable("scale", scale)
                .setVariable("permission", permission)
                .setVariable("permMessage", permMessage)
                .setVariable("useLiveSkin", useLiveSkin)
                .setVariable("skinUsername", skinUsername)
                .setVariable("rotateTowardsPlayer", rotateTowardsPlayer)
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
                            
                            <div class="form-row">
                                <div class="form-col">
                                    <div class="form-group">
                                        <p class="form-label">Citizen Name *</p>
                                        <input type="text" id="citizen-name" class="form-input" value="{{$name}}" 
                                               placeholder="Enter a display name" maxlength="32" />
                                        <p class="form-hint">This will be displayed above the NPC</p>
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
                            
                            {{@checkbox:id=hide-nametag-check,checked={{$hideNametag}},label=Hide Nametag,description=Hide the name displayed above the citizen}}
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
                                <div class="card-header">
                                    <p class="card-title">Player Skin Configuration</p>
                                </div>
                                <div class="card-body">
                                    <div class="form-group">
                                        <p class="form-label">Skin Username</p>
                                        <div class="form-row">
                                            <input type="text" id="skin-username" class="form-input" value="{{$skinUsername}}"
                                                   placeholder="Enter username to fetch skin" style="flex-weight: 1;" />
                                            <div class="spacer-h-sm"></div>
                                            <button id="get-player-skin-btn" class="btn-secondary btn-small" style="anchor-width: 160;">Use My Skin</button>
                                        </div>
                                        <p class="form-hint">Leave empty to use your current skin</p>
                                    </div>
                                    
                                    <div class="spacer-sm"></div>
                                    
                                    {{@checkbox:id=live-skin-check,checked={{$useLiveSkin}},label=Enable Live Skin Updates,description=Automatically refresh the skin every 30 minutes}}
                                    
                                    {{@checkbox:id=rotate-towards-player,checked={{$rotateTowardsPlayer}},label=Rotate Towards Player,description=The citizen will face players when they approach}}
                                </div>
                            </div>
                            {{else}}
                            <!-- Entity Selection -->
                            <div class="card">
                                <div class="card-header">
                                    <p class="card-title">Entity Selection</p>
                                </div>
                                <div class="card-body">
                                    <div class="form-group">
                                        <p class="form-label">Select Entity Type</p>
                                        <select id="entity-dropdown" value="{{$modelId}}" data-hyui-showlabel="true">
                                            {{$entityOptions}}
                                        </select>
                                        <p class="form-hint">Choose from common entity types</p>
                                    </div>
                                    
                                    <div class="spacer-sm"></div>
                                    <div class="divider"></div>
                                    <div class="spacer-sm"></div>
                                    
                                    <div class="form-group">
                                        <p class="form-label">Or Enter Custom Model ID</p>
                                        <input type="text" id="citizen-model-id" class="form-input" value="{{$modelId}}"
                                               placeholder="e.g., PlayerTestModel_V, Sheep" maxlength="64" />
                                        <p class="form-hint">Type a custom model ID if not in the dropdown</p>
                                    </div>
                                </div>
                            </div>
                            {{/if}}
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
                                               placeholder="e.g., citizens.interact.vip" />
                                        <p class="form-hint">Leave empty to allow everyone</p>
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
                                               placeholder="e.g., You need VIP rank to interact!" />
                                        <p class="form-hint">Message shown when player lacks permission</p>
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
                        <button id="create-btn" class="btn-primary btn-wide">Create Citizen</button>
                    </div>
                    
                </div>
            </div>
            """);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupCreateCitizenListeners(page, playerRef, store, isPlayerModel, name, nametagOffset, hideNametag,
                modelId, scale, permission, permMessage, useLiveSkin, skinUsername, rotateTowardsPlayer);

        page.open(store);
    }

    public void openEditCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull CitizenData citizen) {
        TemplateProcessor template = createBaseTemplate()
                .setVariable("citizen", citizen)
                .setVariable("isPlayerModel", citizen.isPlayerModel())
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
                            {{@sectionHeader:title=Basic Information}}
                            
                            <div class="form-row">
                                <div class="form-col">
                                    <div class="form-group">
                                        <p class="form-label">Citizen Name *</p>
                                        <input type="text" id="citizen-name" class="form-input" value="{{$citizen.name}}" 
                                               placeholder="Enter a display name" maxlength="32" />
                                    </div>
                                </div>
                                <div class="spacer-h-md"></div>
                                <div class="form-col-fixed" style="anchor-width: 150;">
                                    <div class="form-group">
                                        <p class="form-label">Nametag Offset</p>
                                        <input type="number" id="nametag-offset" class="form-input"
                                               value="{{$citizen.nametagOffset}}"
                                               min="-500" max="500" step="0.25"
                                               data-hyui-max-decimal-places="2" />
                                    </div>
                                </div>
                            </div>
                            
                            <div class="spacer-sm"></div>
                            
                            {{@checkbox:id=hide-nametag-check,checked={{$citizen.hideNametag}},label=Hide Nametag}}
                        </div>
                        
                        <div class="spacer-md"></div>
                        
                        <!-- Entity Type Section -->
                        <div class="section">
                            {{@sectionHeader:title=Entity Type}}
                            
                            <div class="toggle-group">
                                <button id="type-player" class="toggle-btn{{#if isPlayerModel}} toggle-active{{/if}}">Player Model</button>
                                <button id="type-entity" class="toggle-btn{{#if !isPlayerModel}} toggle-active{{/if}}">Other Entity</button>
                            </div>
                            
                            <div class="spacer-md"></div>
                            
                            {{#if isPlayerModel}}
                            <!-- Player Skin Configuration -->
                            <div class="card">
                                <div class="card-header">
                                    <p class="card-title">Player Skin Configuration</p>
                                </div>
                                <div class="card-body">
                                    <div class="form-group">
                                        <p class="form-label">Skin Username</p>
                                        <div class="form-row">
                                            <input type="text" id="skin-username" class="form-input" value="{{$citizen.skinUsername}}"
                                                   placeholder="Enter username" style="flex-weight: 1;" />
                                            <div class="spacer-h-sm"></div>
                                            <button id="get-player-skin-btn" class="btn-secondary btn-small" style="anchor-width: 160;">Use My Skin</button>
                                        </div>
                                    </div>
                                    
                                    <div class="spacer-sm"></div>
                                    
                                    {{@checkbox:id=live-skin-check,checked={{$citizen.useLiveSkin}},label=Enable Live Skin Updates}}
                                    {{@checkbox:id=rotate-towards-player,checked={{$citizen.rotateTowardsPlayer}},label=Rotate Towards Player}}
                                </div>
                            </div>
                            {{else}}
                            <!-- Entity Selection -->
                            <div class="card">
                                <div class="card-header">
                                    <p class="card-title">Entity Selection</p>
                                </div>
                                <div class="card-body">
                                    <div class="form-group">
                                        <p class="form-label">Select Entity Type</p>
                                        <select id="entity-dropdown" value="{{$citizen.modelId}}" data-hyui-showlabel="true">
                                            {{$entityOptions}}
                                        </select>
                                    </div>
                                    
                                    <div class="spacer-sm"></div>
                                    <div class="divider"></div>
                                    <div class="spacer-sm"></div>
                                    
                                    <div class="form-group">
                                        <p class="form-label">Or Enter Custom Model ID</p>
                                        <input type="text" id="citizen-model-id" class="form-input" value="{{$citizen.modelId}}"
                                               placeholder="Custom model ID" maxlength="64" />
                                    </div>
                                </div>
                            </div>
                            {{/if}}
                        </div>
                        
                        <div class="spacer-md"></div>
                        
                        <!-- Scale Section -->
                        <div class="section">
                            {{@sectionHeader:title=Scale}}
                            
                            <div class="form-row">
                                <div class="form-col-fixed" style="anchor-width: 200;">
                                    <div class="form-group">
                                        <p class="form-label">Scale Factor *</p>
                                        <input type="number" id="citizen-scale" class="form-input"
                                               value="{{$citizen.scale}}"
                                               min="0.01" max="500" step="0.25"
                                               data-hyui-max-decimal-places="2" />
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="spacer-md"></div>
                        
                        <!-- Permissions Section -->
                        <div class="section">
                            {{@sectionHeader:title=Permissions}}
                            
                            <div class="form-row">
                                <div class="form-col">
                                    <div class="form-group">
                                        <div class="form-row">
                                            <p class="form-label">Required Permission</p>
                                            <p class="form-label-optional">(Optional)</p>
                                        </div>
                                        <input type="text" id="citizen-permission" class="form-input" value="{{$citizen.requiredPermission}}" 
                                               placeholder="e.g., citizens.interact.vip" />
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
                                               placeholder="e.g., You need VIP rank!" />
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="spacer-md"></div>
                        
                        <!-- Quick Actions Section -->
                        <div class="section">
                            {{@sectionHeader:title=Quick Actions,description=Manage commands and items and position}}
                            
                            <div class="form-row">
                                <button id="edit-commands-btn" class="btn-info" style="anchor-width: 200; anchor-height: 44;">Edit Commands</button>
                                <div class="spacer-h-sm"></div>
                                <button id="set-items-btn" class="btn-warning" style="anchor-width: 200; anchor-height: 44;">Set Items</button>
                                <div class="spacer-h-sm"></div>
                                <button id="change-position-btn" class="btn-secondary" style="anchor-width: 200; anchor-height: 44;">Update Position</button>
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

    // Helper class to provide index information for command actions in templates
    public static class IndexedCommandAction {
        private final int index;
        private final String command;
        private final boolean runAsServer;

        public IndexedCommandAction(int index, CommandAction action) {
            this.index = index;
            this.command = action.getCommand();
            this.runAsServer = action.isRunAsServer();
        }

        public int getIndex() { return index; }
        public String getCommand() { return command; }
        public boolean isRunAsServer() { return runAsServer; }
    }

    public void openCommandActionsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                      @Nonnull String citizenId, @Nonnull List<CommandAction> actions,
                                      boolean isCreating) {

        // Create indexed actions for the template
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
                                       placeholder="Command without '/' (e.g., give {PlayerName} diamond 64)"
                                       style="flex-weight: 1;" />
                                <div class="spacer-h-sm"></div>
                                <button id="add-command-btn" class="btn-primary" style="anchor-width: 120;">Add</button>
                            </div>
                            
                            <div class="spacer-md"></div>
                            
                            <!-- Help Info -->
                            <div class="card">
                                <div class="card-body">
                                    <p style="color: #8b949e; font-size: 11;"><span style="color: #58a6ff;">Variables:</span> Use {PlayerName} for player's name, {CitizenName} for citizen's name</p>
                                    <div class="spacer-xs"></div>
                                    <p style="color: #8b949e; font-size: 11;"><span style="color: #58a6ff;">Messages:</span> Start with {SendMessage} to send a message instead of running a command</p>
                                    <div class="spacer-xs"></div>
                                    <p style="color: #8b949e; font-size: 11;"><span style="color: #58a6ff;">Colors:</span> Use {RED}, {GREEN}, {BLUE}, {YELLOW}, {#HEX} for colored messages</p>
                                    <div class="spacer-xs"></div>
                                    <p style="color: #8b949e; font-size: 11;">Commands run as <span style="color: #58a6ff;">PLAYER</span> by default. Click toggle to run as <span style="color: #a371f7;">SERVER</span>.</p>
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
                                         Tab currentTab, List<CitizenData> citizens) {
        // Tab switching
        page.addEventListener("tab-create", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.CREATE));

        page.addEventListener("tab-manage", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.MANAGE));

        // Create button
        if (currentTab == Tab.CREATE) {
            page.addEventListener("start-create", CustomUIEventBindingType.Activating, event ->
                    openCreateCitizenGUI(playerRef, store));
        }

        // Manage tab - edit and remove buttons
        if (currentTab == Tab.MANAGE) {
            for (CitizenData citizen : citizens) {
                final String cid = citizen.getId();

                page.addEventListener("edit-" + cid, CustomUIEventBindingType.Activating, event ->
                        openEditCitizenGUI(playerRef, store, citizen));

                page.addEventListener("remove-" + cid, CustomUIEventBindingType.Activating, event -> {
                    plugin.getCitizensManager().removeCitizen(cid);
                    playerRef.sendMessage(Message.raw("Citizen '" + citizen.getName() + "' removed!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                });
            }
        }
    }

    private void setupCreateCitizenListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                             boolean initialIsPlayerModel, String initialName, float initialNametagOffset,
                                             boolean initialHideNametag, String initialModelId, float initialScale,
                                             String initialPermission, String initialPermMessage, boolean initialUseLiveSkin,
                                             String initialSkinUsername, boolean initialRotateTowardsPlayer) {
        final List<CommandAction> tempActions = new ArrayList<>();
        final String[] currentName = {initialName};
        final float[] nametagOffset = {initialNametagOffset};
        final boolean[] hideNametag = {initialHideNametag};
        final String[] currentModelId = {initialModelId.isEmpty() ? "PlayerTestModel_V" : initialModelId};
        final float[] currentScale = {initialScale};
        final String[] currentPermission = {initialPermission};
        final String[] currentPermMessage = {initialPermMessage};
        final boolean[] isPlayerModel = {initialIsPlayerModel};
        final boolean[] useLiveSkin = {initialUseLiveSkin};
        final String[] skinUsername = {initialSkinUsername};
        final boolean[] rotateTowardsPlayer = {initialRotateTowardsPlayer};

        // Track input changes
        page.addEventListener("citizen-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentName[0] = ctx.getValue("citizen-name", String.class).orElse("");
        });

        // Entity-specific listeners (only when NOT in player model mode)
        if (!initialIsPlayerModel) {
            page.addEventListener("citizen-model-id", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                currentModelId[0] = ctx.getValue("citizen-model-id", String.class).orElse("");
            });

            // Entity dropdown selection
            page.addEventListener("entity-dropdown", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("entity-dropdown", String.class).ifPresent(val -> {
                    currentModelId[0] = val;
                });
            });
        }

        // Player-specific listeners (only when in player model mode)
        if (initialIsPlayerModel) {
            page.addEventListener("skin-username", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                skinUsername[0] = ctx.getValue("skin-username", String.class).orElse("");
            });

            page.addEventListener("live-skin-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                useLiveSkin[0] = ctx.getValue("live-skin-check", Boolean.class).orElse(false);
            });

            page.addEventListener("rotate-towards-player", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                rotateTowardsPlayer[0] = ctx.getValue("rotate-towards-player", Boolean.class).orElse(true);
            });

            // Get current player skin button
            page.addEventListener("get-player-skin-btn", CustomUIEventBindingType.Activating, event -> {
                skinUsername[0] = playerRef.getUsername();
                playerRef.sendMessage(Message.raw("Using your skin for this citizen!").color(Color.GREEN));
            });
        }

        page.addEventListener("nametag-offset", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("nametag-offset", Double.class)
                    .ifPresent(val -> nametagOffset[0] = val.floatValue());

            if (nametagOffset[0] == 0.0f) {
                ctx.getValue("nametag-offset", String.class)
                        .ifPresent(val -> {
                            try {
                                nametagOffset[0] = Float.parseFloat(val);
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        });
            }
        });

        page.addEventListener("hide-nametag-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            hideNametag[0] = ctx.getValue("hide-nametag-check", Boolean.class).orElse(false);
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
                                // Ignore
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

        // Entity type toggle buttons
        page.addEventListener("type-player", CustomUIEventBindingType.Activating, (event, ctx) -> {
            openCreateCitizenGUI(playerRef, store, true, currentName[0], nametagOffset[0], hideNametag[0], currentModelId[0],
                    currentScale[0], currentPermission[0], currentPermMessage[0], useLiveSkin[0], true,
                    skinUsername[0], rotateTowardsPlayer[0]);
        });

        page.addEventListener("type-entity", CustomUIEventBindingType.Activating, (event, ctx) -> {
            openCreateCitizenGUI(playerRef, store, false, currentName[0], nametagOffset[0], hideNametag[0], currentModelId[0],
                    currentScale[0], currentPermission[0], currentPermMessage[0], useLiveSkin[0], true,
                    skinUsername[0], rotateTowardsPlayer[0]);
        });

        // Create button
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

            // Create the citizen
            CitizenData citizen = new CitizenData(
                    UUID.randomUUID().toString(),
                    name,
                    modelId,
                    worldUUID,
                    position,
                    rotation,
                    currentScale[0],
                    null,
                    null,
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

            // If player model, fetch and cache the skin BEFORE adding
            if (isPlayerModel[0]) {
                if (skinUsername[0].trim().isEmpty()) {
                    plugin.getCitizensManager().updateCitizenSkinFromPlayer(citizen, playerRef, false);
                    plugin.getCitizensManager().addCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Citizen \"" + name + "\" created at your position!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                } else {
                    playerRef.sendMessage(Message.raw("Fetching skin for \"" + skinUsername[0] + "\"...").color(Color.YELLOW));
                    com.electro.hycitizens.util.SkinUtilities.getSkin(skinUsername[0].trim()).thenAccept(skin -> {
                        citizen.setCachedSkin(skin);
                        citizen.setLastSkinUpdate(System.currentTimeMillis());
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

        // Cancel button
        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.CREATE));
    }

    private void setupEditCitizenListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                           CitizenData citizen) {
        final String[] currentName = {citizen.getName()};
        final float[] nametagOffset = {citizen.getNametagOffset()};
        final boolean[] hideNametag = {citizen.isHideNametag()};
        final String[] currentModelId = {citizen.getModelId()};
        final float[] currentScale = {citizen.getScale()};
        final String[] currentPermission = {citizen.getRequiredPermission()};
        final String[] currentPermMessage = {citizen.getNoPermissionMessage()};
        final boolean[] isPlayerModel = {citizen.isPlayerModel()};
        final boolean[] useLiveSkin = {citizen.isUseLiveSkin()};
        final boolean[] rotateTowardsPlayer = {citizen.getRotateTowardsPlayer()};
        final String[] skinUsername = {citizen.getSkinUsername()};

        // Track input changes
        page.addEventListener("citizen-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentName[0] = ctx.getValue("citizen-name", String.class).orElse("");
        });

        // Entity-specific listeners (only when NOT in player model mode)
        if (!citizen.isPlayerModel()) {
            page.addEventListener("citizen-model-id", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                currentModelId[0] = ctx.getValue("citizen-model-id", String.class).orElse("");
            });

            // Entity dropdown selection
            page.addEventListener("entity-dropdown", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                ctx.getValue("entity-dropdown", String.class).ifPresent(val -> {
                    currentModelId[0] = val;
                });
            });
        }

        // Player-specific listeners (only when in player model mode)
        if (citizen.isPlayerModel()) {
            page.addEventListener("skin-username", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                skinUsername[0] = ctx.getValue("skin-username", String.class).orElse("");
            });

            page.addEventListener("live-skin-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                useLiveSkin[0] = ctx.getValue("live-skin-check", Boolean.class).orElse(false);
            });

            page.addEventListener("rotate-towards-player", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
                rotateTowardsPlayer[0] = ctx.getValue("rotate-towards-player", Boolean.class).orElse(true);
            });

            // Get current player skin button
            page.addEventListener("get-player-skin-btn", CustomUIEventBindingType.Activating, event -> {
                skinUsername[0] = playerRef.getUsername();
                playerRef.sendMessage(Message.raw("Using your skin for this citizen!").color(Color.GREEN));
            });
        }

        page.addEventListener("nametag-offset", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            ctx.getValue("nametag-offset", Double.class)
                    .ifPresent(val -> nametagOffset[0] = val.floatValue());

            if (nametagOffset[0] == 0.0f) {
                ctx.getValue("nametag-offset", String.class)
                        .ifPresent(val -> {
                            try {
                                nametagOffset[0] = Float.parseFloat(val);
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        });
            }
        });

        page.addEventListener("hide-nametag-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            hideNametag[0] = ctx.getValue("hide-nametag-check", Boolean.class).orElse(false);
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
                                // Ignore
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

        // Entity type toggle buttons
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

        // Edit commands button
        page.addEventListener("edit-commands-btn", CustomUIEventBindingType.Activating, event -> {
            openCommandActionsGUI(playerRef, store, citizen.getId(),
                    new ArrayList<>(citizen.getCommandActions()), false);
        });

        // Change position button
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

        // Set items button
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

                // Main hand
                if (player.getInventory().getItemInHand() == null) {
                    citizen.setNpcHand(null);
                } else {
                    citizen.setNpcHand(player.getInventory().getItemInHand().getItemId());
                }

                // Off hand
                if (player.getInventory().getUtilityItem() == null) {
                    citizen.setNpcOffHand(null);
                } else {
                    citizen.setNpcOffHand(player.getInventory().getUtilityItem().getItemId());
                }

                // Helmet
                if (player.getInventory().getArmor().getItemStack((short) 0) == null) {
                    citizen.setNpcHelmet(null);
                } else {
                    citizen.setNpcHelmet(player.getInventory().getArmor().getItemStack((short) 0).getItemId());
                }

                // Chest
                if (player.getInventory().getArmor().getItemStack((short) 1) == null) {
                    citizen.setNpcChest(null);
                } else {
                    citizen.setNpcChest(player.getInventory().getArmor().getItemStack((short) 1).getItemId());
                }

                // Gloves
                if (player.getInventory().getArmor().getItemStack((short) 2) == null) {
                    citizen.setNpcGloves(null);
                } else {
                    citizen.setNpcGloves(player.getInventory().getArmor().getItemStack((short) 2).getItemId());
                }

                // Leggings
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

        // Save button
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

            citizen.setName(name);
            citizen.setModelId(modelId);
            citizen.setScale(currentScale[0]);
            citizen.setRequiredPermission(currentPermission[0].trim());
            citizen.setNoPermissionMessage(currentPermMessage[0].trim());
            citizen.setPlayerModel(isPlayerModel[0]);
            citizen.setUseLiveSkin(useLiveSkin[0]);
            citizen.setRotateTowardsPlayer(rotateTowardsPlayer[0]);
            citizen.setSkinUsername(skinUsername[0].trim());
            citizen.setNametagOffset(nametagOffset[0]);
            citizen.setHideNametag(hideNametag[0]);

            // If player model, fetch and cache the skin BEFORE updating
            if (isPlayerModel[0]) {
                if (skinUsername[0].trim().isEmpty()) {
                    plugin.getCitizensManager().updateCitizenSkinFromPlayer(citizen, playerRef, false);
                    plugin.getCitizensManager().updateCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Citizen '" + name + "' updated!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                } else {
                    playerRef.sendMessage(Message.raw("Fetching skin for '" + skinUsername[0] + "'...").color(Color.YELLOW));
                    com.electro.hycitizens.util.SkinUtilities.getSkin(skinUsername[0].trim()).thenAccept(skin -> {
                        citizen.setCachedSkin(skin);
                        citizen.setLastSkinUpdate(System.currentTimeMillis());
                        plugin.getCitizensManager().updateCitizen(citizen, true);
                        playerRef.sendMessage(Message.raw("Citizen '" + name + "' updated!").color(Color.GREEN));
                        openCitizensGUI(playerRef, store, Tab.MANAGE);
                    });
                }
            } else {
                plugin.getCitizensManager().updateCitizen(citizen, true);
                playerRef.sendMessage(Message.raw("Citizen '" + name + "' updated!").color(Color.GREEN));
                openCitizensGUI(playerRef, store, Tab.MANAGE);
            }
        });

        // Cancel button
        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.MANAGE));
    }

    private void setupCommandActionsListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                              String citizenId, List<CommandAction> actions, boolean isCreating) {
        final String[] currentCommand = {""};

        // Track command input
        page.addEventListener("new-command", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentCommand[0] = ctx.getValue("new-command", String.class).orElse("");
        });

        // Add command button
        page.addEventListener("add-command-btn", CustomUIEventBindingType.Activating, event -> {
            String command = currentCommand[0].trim();

            if (command.isEmpty()) {
                playerRef.sendMessage(Message.raw("Please enter a command!").color(Color.RED));
                return;
            }

            // Remove leading slash if present
            if (command.startsWith("/")) {
                command = command.substring(1);
            }

            actions.add(new CommandAction(command, false));
            playerRef.sendMessage(Message.raw("Command added!").color(Color.GREEN));

            openCommandActionsGUI(playerRef, store, citizenId, actions, isCreating);
        });

        // Toggle and delete buttons for each command
        for (int i = 0; i < actions.size(); i++) {
            final int index = i;

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

        // Done button
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

        // Cancel button
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
}
