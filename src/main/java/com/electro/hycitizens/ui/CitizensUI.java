package com.electro.hycitizens.ui;

import au.ellie.hyui.builders.PageBuilder;
import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.CommandAction;
import com.electro.hycitizens.util.ConfigManager;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CitizensUI {
    private final HyCitizensPlugin plugin;

    public enum Tab {
        CREATE, MANAGE
    }

    public CitizensUI(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void openCitizensGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull Tab currentTab) {
        ConfigManager config = plugin.getConfigManager();
        List<CitizenData> citizens = plugin.getCitizensManager().getAllCitizens();

        String html = buildMainHTML(currentTab, citizens.size());

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupMainEventListeners(page, playerRef, store, currentTab, citizens);

        page.open(store);
    }

    public void openCreateCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store) {
        openCreateCitizenGUI(playerRef, store, true, "", "", 1.0f, "", "", false, false, "");
    }

    public void openCreateCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                     boolean isPlayerModel, String name, String modelId, float scale,
                                     String permission, String permMessage, boolean useLiveSkin,
                                     boolean preserveState, String skinUsername) {
        String html = buildCreateCitizenHTML(isPlayerModel);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupCreateCitizenListeners(page, playerRef, store, isPlayerModel, name, modelId, scale,
                                   permission, permMessage, useLiveSkin, skinUsername);

        page.open(store);
    }

    public void openEditCitizenGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull CitizenData citizen) {
        String html = buildEditCitizenHTML(citizen);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupEditCitizenListeners(page, playerRef, store, citizen);

        page.open(store);
    }

    public void openCommandActionsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                      @Nonnull String citizenId, @Nonnull List<CommandAction> actions,
                                      boolean isCreating) {
        String html = buildCommandActionsHTML(actions);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupCommandActionsListeners(page, playerRef, store, citizenId, actions, isCreating);

        page.open(store);
    }

    private String buildMainHTML(Tab currentTab, int citizenCount) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
            <style>
                .citizens-container {
                    layout: top;
                    anchor-width: 920;
                    anchor-height: 500;
                    background-color: #1a1a2e(0.95);
                    border-radius: 8;
                }
                
                .title-bar {
                    layout: center;
                    flex-weight: 0;
                    background-color: #16213e(0.9);
                    padding: 18;
                    border-radius: 8 8 0 0;
                }
                
                .title-text {
                    color: #FFFFFF;
                    font-size: 22;
                    font-weight: bold;
                    text-align: center;
                }
                
                .stats-bar {
                    layout: center;
                    flex-weight: 0;
                    background-color: #0f3460(0.7);
                    padding: 14;
                }
                
                .stat-item {
                    layout: left;
                    flex-weight: 0;
                    padding: 10;
                    background-color: #1a1a2e(0.8);
                    border-radius: 4;
                }
                
                .stat-label {
                    color: #888888;
                    font-size: 11;
                }
                
                .stat-value {
                    color: #00ff88;
                    font-size: 16;
                    padding-left: 8;
                }
                
                .main-content {
                    layout: top;
                    flex-weight: 1;
                    padding: 20;
                }
                
                .tab-buttons {
                    layout: center;
                    flex-weight: 0;
                    padding-top: 8;
                    padding-bottom: 8;
                }
                
                .tab-btn {
                    flex-weight: 0;
                    anchor-width: 220;
                    anchor-height: 40;
                    background-color: #16213e(0.6);
                }
                
                .tab-spacer {
                    flex-weight: 0;
                    anchor-width: 16;
                }
                
                .tab-active {
                    background-color: #00d4ff(0.3);
                }
                
                .content-section {
                    layout: top;
                    flex-weight: 1;
                    padding: 16;
                }
                
                .spacer-small {
                    flex-weight: 0;
                    anchor-height: 10;
                }
                
                .spacer-medium {
                    flex-weight: 0;
                    anchor-height: 18;
                }
                
                .spacer-large {
                    flex-weight: 0;
                    anchor-height: 24;
                }
                
                .create-section {
                    layout: top;
                    flex-weight: 0;
                    background-color: #0f3460(0.5);
                    padding: 24;
                    border-radius: 6;
                    margin: 16;
                }
                
                .create-title {
                    color: #ffffff;
                    font-size: 18;
                    font-weight: bold;
                    text-align: center;
                }
                
                .create-desc {
                    color: #aaaaaa;
                    font-size: 12;
                    text-align: center;
                    padding-top: 8;
                }
                
                .create-btn-row {
                    layout: center;
                    flex-weight: 0;
                    padding-top: 20;
                }
                
                .create-btn {
                    flex-weight: 0;
                    anchor-width: 260;
                    anchor-height: 42;
                }
                
                .citizen-card {
                    layout: left;
                    flex-weight: 0;
                    background-color: #16213e(0.7);
                    padding: 18;
                    border-radius: 6;
                    margin: 8;
                }
                
                .citizen-info {
                    layout: top;
                    flex-weight: 1;
                    padding-right: 16;
                }
                
                .citizen-name {
                    color: #FFFFFF;
                    font-size: 16;
                    font-weight: bold;
                }
                
                .citizen-model-id {
                    color: #00d4ff;
                    font-size: 16;
                    font-weight: bold;
                }
                
                .citizen-scale {
                    color: #00d4ff;
                    font-size: 16;
                    font-weight: bold;
                }
                
                .citizen-detail {
                    color: #aaaaaa;
                    font-size: 11;
                    padding-top: 4;
                }
                
                .citizen-actions {
                    layout: left;
                    flex-weight: 0;
                }
                
                .edit-btn {
                    flex-weight: 0;
                    anchor-width: 110;
                    anchor-height: 38;
                }
                
                .remove-btn {
                    flex-weight: 0;
                    anchor-width: 130;
                    anchor-height: 38;
                }
                
                .btn-spacer {
                    flex-weight: 0;
                    anchor-width: 10;
                }
                
                .empty-state {
                    layout: center;
                    flex-weight: 1;
                    padding: 32;
                }
                
                .empty-icon {
                    color: #444444;
                    font-size: 48;
                    text-align: center;
                }
                
                .empty-text {
                    color: #666666;
                    font-size: 14;
                    text-align: center;
                    padding-top: 16;
                }
            </style>
            
            <div class="page-overlay">
                <div class="citizens-container">
                    <!-- Title Bar -->
                    <div class="title-bar">
                        <p class="title-text">Citizens Manager</p>
                    </div>
                    
                    <!-- Stats Bar -->
                    <div class="stats-bar">
                        <div class="stat-item">
                            <p class="stat-label">Total Citizens</p>
                            <p class="stat-value">%d</p>
                        </div>
                    </div>
                    
                    <!-- Main Content -->
                    <div class="main-content">
                        <!-- Tab Navigation -->
                        <div class="tab-buttons">
                            <button id="tab-create" class="tab-btn%s">Create</button>
                            <div class="tab-spacer"></div>
                            <button id="tab-manage" class="tab-btn%s">Manage</button>
                        </div>
                        
                        <div class="spacer-medium"></div>
                        
                        <!-- Content Section -->
                        <div class="content-section">
            """.formatted(
                citizenCount,
                currentTab == Tab.CREATE ? " tab-active" : "",
                currentTab == Tab.MANAGE ? " tab-active" : ""
        ));

        if (currentTab == Tab.CREATE) {
            sb.append(buildCreateTabContent());
        } else {
            sb.append(buildManageTabContent(plugin.getCitizensManager().getAllCitizens()));
        }

        sb.append("""
                        </div>
                    </div>
                </div>
            </div>
            """);

        return sb.toString();
    }

    private String buildCreateTabContent() {
        return """
                            <div class="create-section">
                                <p class="create-title">Create a New Citizen</p>
                                <p class="create-desc">Citizens are interactive NPCs that can execute commands and display custom messages.</p>
                                <p class="create-desc">The NPC will spawn at your current position and rotation.</p>
                                <div class="create-btn-row">
                                    <button id="start-create" class="create-btn">Start Creating</button>
                                </div>
                            </div>
                """;
    }

    private String buildManageTabContent(List<CitizenData> citizens) {
        StringBuilder sb = new StringBuilder();

        if (citizens.isEmpty()) {
            sb.append("""
                                <div class="empty-state">
                                    <p class="empty-text">No citizens created yet.</p>
                                    <p class="empty-text"> Switch to the Create tab to add your first citizen!</p>
                                </div>
                    """);
            return sb.toString();
        }

        sb.append("<div id=\"citizens-list\" style=\"layout-mode: TopScrolling; padding: 6; anchor-height: 385;\">");

        for (int i = 0; i < citizens.size(); i++) {
            CitizenData citizen = citizens.get(i);

            if (i > 0) {
                sb.append("                            <div class=\"spacer-small\"></div>\n");
            }

            String permInfo = citizen.getRequiredPermission().isEmpty() ?
                    "No permission required" :
                    "Requires: " + citizen.getRequiredPermission();

            String commandInfo = citizen.getCommandActions().isEmpty() ?
                    "No commands" :
                    citizen.getCommandActions().size() + " command(s)";

            sb.append("""
                                <div class="citizen-card">
                                    <div class="citizen-info">
                                        <p class="citizen-name">%s</p>
                                        <p class="citizen-detail">ID: %s</p>
                                        <p class="citizen-detail">Model: %s</p>
                                        <p class="citizen-detail">%s • %s</p>
                                    </div>
                                    <div class="citizen-actions">
                                        <button id="edit-%d" class="edit-btn">Edit</button>
                                        <div class="btn-spacer"></div>
                                        <button id="remove-%d" class="remove-btn">Remove</button>
                                    </div>
                                </div>
                    """.formatted(
                    citizen.getName(),
                    citizen.getId(),
                    citizen.getModelId(),
                    permInfo,
                    commandInfo,
                    i,
                    i
            ));
        }

        sb.append("</div>");

        return sb.toString();
    }

    private String buildCreateCitizenHTML(boolean isPlayerModel) {
        return """
            <style>
                .form-container {
                    layout: top;
                    anchor-width: 800;
                    anchor-height: 800;
                    background-color: #1a1a2e(0.95);
                    border-radius: 8;
                }
                
                .form-header {
                    layout: center;
                    flex-weight: 0;
                    background-color: #16213e(0.9);
                    padding: 18;
                    border-radius: 8 8 0 0;
                }
                
                .form-title {
                    color: #00d4ff;
                    font-size: 20;
                    font-weight: bold;
                }
                
                .form-content {
                    layout: top;
                    flex-weight: 1;
                    padding: 24;
                }
                
                .form-section {
                    layout: top;
                    flex-weight: 0;
                    padding-left: 12;
                    padding-right: 12;
                }
                
                .form-label {
                    color: #ffffff;
                    font-size: 13;
                    font-weight: bold;
                    padding-bottom: 6;
                }
                
                .form-hint {
                    color: #888888;
                    font-size: 10;
                    padding-top: 4;
                }
                
                .form-input {
                    flex-weight: 0;
                    anchor-height: 36;
                }
                
                .spacer-small {
                    flex-weight: 0;
                    anchor-height: 12;
                }
                
                .spacer-medium {
                    flex-weight: 0;
                    anchor-height: 18;
                }
                
                .button-row {
                    layout: center;
                    flex-weight: 0;
                    padding-top: 24;
                    padding-bottom: 16;
                }
                
                .action-btn {
                    flex-weight: 0;
                    anchor-width: 160;
                    anchor-height: 42;
                }
                
                .primary-btn {
                    
                }
                
                .secondary-btn {
                    
                }
                
                .danger-btn {
                    
                }
                
                .btn-spacer {
                    flex-weight: 0;
                    anchor-width: 12;
                }
                
                .info-box {
                    layout: top;
                    flex-weight: 0;
                    background-color: #00d4ff(0.15);
                    padding: 14;
                    border-radius: 4;
                    margin-left: 12;
                    margin-right: 12;
                }
                
                .info-text {
                    color: #FFFFFF;
                    font-size: 11;
                }

                .toggle-buttons {
                    layout: center;
                    flex-weight: 0;
                    padding-top: 8;
                    padding-bottom: 8;
                }

                .toggle-btn {
                    flex-weight: 0;
                    anchor-width: 180;
                    anchor-height: 38;
                    
                }

                .toggle-active {
                    
                }

                .skin-section {
                    layout: top;
                    flex-weight: 0;
                    background-color: #0f3460(0.3);
                    padding: 14;
                    border-radius: 4;
                    margin-left: 12;
                    margin-right: 12;
                }

                .checkbox-row {
                    layout: left;
                    flex-weight: 0;
                    padding-top: 8;
                }

                .checkbox-label {
                    color: #ffffff;
                    font-size: 12;
                    padding-left: 8;
                }
            </style>
            
            <div class="page-overlay">
                <div class="form-container">
                    <!-- Header -->
                    <div class="form-header">
                        <p class="form-title">Create New Citizen</p>
                    </div>
                    
                    <!-- Form Content -->
                    <div class="form-content">
                        <!-- Info Box -->
                        <div class="info-box">
                            <p class="info-text">The citizen will spawn at your current position and rotation</p>
                        </div>
                        
                        <div class="spacer-medium"></div>
                        
                        <!-- Name Input -->
                        <div class="form-section">
                            <p class="form-label">Citizen Name *</p>
                            <input type="text" id="citizen-name" class="form-input" value="" 
                                   placeholder="Enter citizen name" maxlength="32" />
                            <p class="form-hint">This will be displayed above the NPC</p>
                        </div>
                        
                        <div class="spacer-medium"></div>

                        <!-- Model Type Selection -->
                        <div class="form-section">
                            <p class="form-label">Entity Type *</p>
                            <div class="toggle-buttons">
                                <button id="type-player" class="toggle-btn%s">Player</button>
                                <div class="btn-spacer"></div>
                                <button id="type-entity" class="toggle-btn%s">Other Entity</button>
                            </div>
                        </div>

                        <div class="spacer-medium"></div>

                        <!-- Player Skin Section -->
                        <div id="player-skin-section" class="form-section"%s>
                            <div class="skin-section">
                                <p class="form-label">Player Skin Configuration</p>
                                <div class="spacer-small"></div>

                                <!-- Username Input -->
                                <input type="text" id="skin-username" class="form-input" value=""
                                       placeholder="Enter username (can be offline)" />
                                <p class="form-hint">Leave empty to use current player's skin</p>

                                <div class="spacer-small"></div>

                                <!-- Get Current Player Skin Button -->
                                <div class="button-row">
                                    <button id="get-player-skin-btn" class="action-btn secondary-btn">Use My Skin</button>
                                </div>

                                <div class="spacer-small"></div>

                                <!-- Live Skin Checkbox -->
                                <div class="checkbox-row">
                                    <input type="checkbox" id="live-skin-check" value="false" />
                                    <p class="checkbox-label">Enable Live Skin Updates (refreshes every 30 minutes)</p>
                                </div>
                            </div>
                        </div>

                        <!-- Model ID Input -->
                        <div id="model-id-section" class="form-section"%s>
                            <p class="form-label">Model ID *</p>
                            <input type="text" id="citizen-model-id" class="form-input" value="PlayerTestModel_V"
                                   placeholder="Enter model ID" maxlength="32" />
                            <p class="form-hint">PlayerTestModel_V, Sheep, etc.</p>
                        </div>

                        <div class="spacer-medium"></div>
                        
                        <!-- Scale Input -->
                        <div class="form-section">
                            <p class="form-label">Scale *</p>
                            <input type="number" id="citizen-scale" class="form-input"
                                   value="1.0"
                                   placeholder="Enter a scale"
                                   min="0.01"
                                   max="500"
                                   step="0.25"
                                   data-hyui-max-decimal-places="2" />
                            <p class="form-hint">Default is 1.0</p>
                        </div>
                        
                        <div class="spacer-medium"></div>
                        
                        <!-- Permission Input -->
                        <div class="form-section">
                            <p class="form-label">Required Permission (Optional)</p>
                            <input type="text" id="citizen-permission" class="form-input" value="" 
                                   placeholder="e.g., citizens.interact.vip" />
                            <p class="form-hint">Leave empty to allow everyone to interact</p>
                        </div>
                        
                        <div class="spacer-medium"></div>
                        
                        <!-- Permission Message Input -->
                        <div class="form-section">
                            <p class="form-label">No Permission Message (Optional)</p>
                            <input type="text" id="citizen-perm-message" class="form-input" value="" 
                                   placeholder="e.g., You need VIP rank to interact!" />
                            <p class="form-hint">Message shown when player lacks permission</p>
                        </div>
                        
                        <div class="spacer-medium"></div>
                        
                        <!-- Actions Buttons -->
                        <div class="button-row">
                            <button id="edit-commands-btn" class="action-btn secondary-btn">Commands</button>
                            <div class="btn-spacer"></div>
                            <button id="create-btn" class="action-btn primary-btn">Create</button>
                            <div class="btn-spacer"></div>
                            <button id="cancel-btn" class="action-btn danger-btn">Cancel</button>
                        </div>
                    </div>
                </div>
            </div>
            """.formatted(
                isPlayerModel ? " toggle-active" : "",
                !isPlayerModel ? " toggle-active" : "",
                isPlayerModel ? "" : " style=\"display: none;\"",
                !isPlayerModel ? "" : " style=\"display: none;\""
        );
    }

    private String buildEditCitizenHTML(CitizenData citizen) {
        String permValue = citizen.getRequiredPermission().isEmpty() ? "" : citizen.getRequiredPermission();
        String permMessage = citizen.getNoPermissionMessage().isEmpty() ? "" : citizen.getNoPermissionMessage();

        return """
            <style>
                .form-container {
                    layout: top;
                    anchor-width: 800;
                    anchor-height: 750;
                    background-color: #1a1a2e(0.95);
                    border-radius: 8;
                }
                
                .form-header {
                    layout: center;
                    flex-weight: 0;
                    background-color: #16213e(0.9);
                    padding: 18;
                    border-radius: 8 8 0 0;
                }
                
                .form-title {
                    color: #FFFFFF;
                    font-size: 20;
                    font-weight: bold;
                }
                
                .form-subtitle {
                    color: #888888;
                    font-size: 11;
                    padding-top: 4;
                }
                
                .form-content {
                    layout: top;
                    flex-weight: 1;
                    padding: 24;
                }
                
                .form-section {
                    layout: top;
                    flex-weight: 0;
                    padding-left: 12;
                    padding-right: 12;
                }
                
                .form-label {
                    color: #ffffff;
                    font-size: 13;
                    font-weight: bold;
                    padding-bottom: 6;
                }
                
                .form-hint {
                    color: #888888;
                    font-size: 10;
                    padding-top: 4;
                }
                
                .form-input {
                    flex-weight: 0;
                    anchor-height: 36;
                }
                
                .spacer-small {
                    flex-weight: 0;
                    anchor-height: 12;
                }
                
                .spacer-medium {
                    flex-weight: 0;
                    anchor-height: 18;
                }
                
                .button-row {
                    layout: center;
                    flex-weight: 0;
                    padding-top: 20;
                    padding-bottom: 16;
                }
                
                .action-btn {
                    flex-weight: 0;
                    anchor-width: 150;
                    anchor-height: 42;
                }
                
                .primary-btn {
                    
                }
                
                .secondary-btn {
                    
                }
                
                .warning-btn {
                    
                }
                
                .danger-btn {
                    
                }
                
                .btn-spacer {
                    flex-weight: 0;
                    anchor-width: 10;
                }

                .toggle-buttons {
                    layout: center;
                    flex-weight: 0;
                    padding-top: 8;
                    padding-bottom: 8;
                }

                .toggle-btn {
                    flex-weight: 0;
                    anchor-width: 180;
                    anchor-height: 38;
                    
                }

                .toggle-active {
                    
                }

                .skin-section {
                    layout: top;
                    flex-weight: 0;
                    background-color: #0f3460(0.3);
                    padding: 14;
                    border-radius: 4;
                    margin-left: 12;
                    margin-right: 12;
                }

                .checkbox-row {
                    layout: left;
                    flex-weight: 0;
                    padding-top: 8;
                }

                .checkbox-label {
                    color: #ffffff;
                    font-size: 12;
                    padding-left: 8;
                }
            </style>

            <div class="page-overlay">
                <div class="form-container">
                    <!-- Header -->
                    <div class="form-header">
                        <p class="form-title">Edit Citizen</p>
                    </div>

                    <!-- Form Content -->
                    <div class="form-content">
                        <p class="form-subtitle">ID: %s</p>

                        <div class="spacer-small"></div>

                        <!-- Name Input -->
                        <div class="form-section">
                            <p class="form-label">Citizen Name *</p>
                            <input type="text" id="citizen-name" class="form-input" value="%s"
                                   placeholder="Enter citizen name" maxlength="32" />
                        </div>

                        <div class="spacer-medium"></div>

                        <!-- Model Type Selection -->
                        <div class="form-section">
                            <p class="form-label">Entity Type *</p>
                            <div class="toggle-buttons">
                                <button id="type-player" class="toggle-btn%s">Player</button>
                                <div class="btn-spacer"></div>
                                <button id="type-entity" class="toggle-btn%s">Other Entity</button>
                            </div>
                        </div>

                        <div class="spacer-medium"></div>

                        <!-- Player Skin Section -->
                        <div id="player-skin-section" class="form-section"%s>
                            <div class="skin-section">
                                <p class="form-label">Player Skin Configuration</p>
                                <div class="spacer-small"></div>

                                <!-- Username Input -->
                                <input type="text" id="skin-username" class="form-input" value="%s"
                                       placeholder="Enter username to fetch skin from PlayerDB" />
                                <p class="form-hint">Leave empty to use current player's skin</p>

                                <div class="spacer-small"></div>

                                <!-- Get Current Player Skin Button -->
                                <div class="button-row">
                                    <button id="get-player-skin-btn" class="action-btn secondary-btn" style="anchor-width: 200;">Use My Skin</button>
                                </div>

                                <div class="spacer-small"></div>

                                <!-- Live Skin Checkbox -->
                                <div class="checkbox-row">
                                    <input type="checkbox" id="live-skin-check"%s />
                                    <p class="checkbox-label">Enable Live Skin Updates (refreshes every 30 minutes)</p>
                                </div>
                            </div>
                        </div>

                        <!-- Model ID Input -->
                        <div id="model-id-section" class="form-section"%s>
                            <p class="form-label">Model ID *</p>
                            <input type="text" id="citizen-model-id" class="form-input" value="%s"
                                   placeholder="Player, PlayerTestModel_V, Sheep, etc." maxlength="32" />
                            <p class="form-hint">PlayerTestModel_V, Sheep, etc.</p>
                        </div>

                        <div class="spacer-medium"></div>
                
                        <!-- Scale Input -->
                        <div class="form-section">
                            <p class="form-label">Scale *</p>
                            <input type="number" id="citizen-scale" class="form-input"
                                   value="%s"
                                   placeholder="Enter a scale"
                                   min="0.01"
                                   max="500"
                                   step="0.25"
                                   data-hyui-max-decimal-places="2" />
                            <p class="form-hint">Default is 1.0</p>
                        </div>
                        
                        <div class="spacer-medium"></div>
                        
                        <!-- Permission Input -->
                        <div class="form-section">
                            <p class="form-label">Required Permission (Optional)</p>
                            <input type="text" id="citizen-permission" class="form-input" value="%s" 
                                   placeholder="e.g., citizens.interact.vip" />
                            <p class="form-hint">Leave empty to allow everyone to interact</p>
                        </div>
                        
                        <div class="spacer-medium"></div>
                        
                        <!-- Permission Message Input -->
                        <div class="form-section">
                            <p class="form-label">No Permission Message (Optional)</p>
                            <input type="text" id="citizen-perm-message" class="form-input" value="%s" 
                                   placeholder="e.g., You need VIP rank to interact!" />
                        </div>
                        
                        <div class="spacer-medium"></div>
                        
                        <!-- Actions Buttons -->
                        <div class="button-row">
                            <button id="edit-commands-btn" class="action-btn secondary-btn">Commands</button>
                            <div class="btn-spacer"></div>
                            <button id="change-position-btn" class="action-btn warning-btn">Position</button>
                            <div class="btn-spacer"></div>
                            <button id="save-btn" class="action-btn primary-btn">Save</button>
                            <div class="btn-spacer"></div>
                            <button id="cancel-btn" class="action-btn danger-btn">Cancel</button>
                        </div>
                    </div>
                </div>
            </div>
            """.formatted(
                citizen.getId(),
                citizen.getName(),
                citizen.isPlayerModel() ? " toggle-active" : "",
                !citizen.isPlayerModel() ? " toggle-active" : "",
                citizen.isPlayerModel() ? "" : " style=\"display: none;\"",
                citizen.getSkinUsername(),
                citizen.isUseLiveSkin() ? " value=\"true\" checked" : " value=\"false\"",
                !citizen.isPlayerModel() ? "" : " style=\"display: none;\"",
                citizen.getModelId(),
                String.valueOf(citizen.getScale()),
                permValue,
                permMessage
        );
    }

    private String buildCommandActionsHTML(List<CommandAction> actions) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
            <style>
                .commands-container {
                    layout: top;
                    anchor-width: 800;
                    anchor-height: 640;
                    background-color: #1a1a2e(0.95);
                    border-radius: 8;
                }
                
                .commands-header {
                    layout: center;
                    flex-weight: 0;
                    background-color: #16213e(0.9);
                    padding: 18;
                    border-radius: 8 8 0 0;
                }
                
                .commands-title {
                    color: #FFFFFF;
                    font-size: 20;
                    font-weight: bold;
                }
                
                .commands-content {
                    layout: top;
                    flex-weight: 1;
                    padding: 24;
                }
                
                .add-section {
                    layout: top;
                    flex-weight: 0;
                    background-color: #0f3460(0.5);
                    padding: 18;
                    border-radius: 6;
                    margin-left: 8;
                    margin-right: 8;
                }
                
                .add-title {
                    color: #ffffff;
                    font-size: 14;
                    font-weight: bold;
                    padding-bottom: 12;
                }
                
                .add-row {
                    layout: left;
                    flex-weight: 0;
                }
                
                .add-input {
                    flex-weight: 1;
                    anchor-height: 40;
                }
                
                .add-btn {
                    flex-weight: 0;
                    anchor-width: 130;
                    anchor-height: 40;
                }
                
                .input-spacer {
                    flex-weight: 0;
                    anchor-width: 12;
                }
                
                .form-hint {
                    color: #888888;
                    font-size: 10;
                    padding-top: 6;
                }
                
                .spacer-small {
                    flex-weight: 0;
                    anchor-height: 10;
                }
                
                .spacer-medium {
                    flex-weight: 0;
                    anchor-height: 18;
                }
                
                .commands-list {
                    layout: top;
                    flex-weight: 1;
                    padding-left: 8;
                    padding-right: 8;
                }
                
                .command-card {
                    layout: left;
                    flex-weight: 0;
                    background-color: #16213e(0.7);
                    padding: 16;
                    border-radius: 6;
                }
                
                .command-info {
                    layout: top;
                    flex-weight: 1;
                    padding-right: 12;
                }
                
                .command-text {
                    color: #00ff88;
                    font-size: 13;
                    font-weight: bold;
                }
                
                .command-type {
                    color: #aaaaaa;
                    font-size: 11;
                    padding-top: 4;
                }
                
                .command-actions {
                    layout: left;
                    flex-weight: 0;
                }
                
                .toggle-btn {
                    flex-weight: 0;
                    anchor-width: 100;
                    anchor-height: 38;
                }
                
                .delete-btn {
                    flex-weight: 0;
                    anchor-width: 100;
                    anchor-height: 38;
                }
                
                .btn-spacer {
                    flex-weight: 0;
                    anchor-width: 10;
                }
                
                .button-row {
                    layout: center;
                    flex-weight: 0;
                    padding-top: 20;
                    padding-bottom: 16;
                }
                
                .action-btn {
                    flex-weight: 0;
                    anchor-width: 180;
                    anchor-height: 42;
                }
                
                .primary-btn {
                    
                }
                
                .secondary-btn {
                    
                }
                
                .empty-state {
                    layout: center;
                    flex-weight: 1;
                    padding: 32;
                }
                
                .empty-text {
                    color: #666666;
                    font-size: 13;
                    text-align: center;
                }
            </style>
            
            <div class="page-overlay">
                <div class="commands-container">
                    <!-- Header -->
                    <div class="commands-header">
                        <p class="commands-title">Command Actions</p>
                    </div>
                    
                    <!-- Content -->
                    <div class="commands-content">
                        <!-- Add Command Section -->
                        <div class="add-section">
                            <p class="add-title">Add New Command</p>
                            <div class="add-row">
                                <input type="text" id="new-command" class="add-input" value="" 
                                       placeholder="Command without '/' (e.g., give {PlayerName} Rock_Gem_Diamond)" />
                                <div class="input-spacer"></div>
                                <button id="add-command-btn" class="add-btn">Add</button>
                            </div>
                            <p class="form-hint">Command will execute as PLAYER by default. Click toggle to change.</p>
                            <p class="form-hint">You can use "{PlayerName}" which will be replaced by the player's username.</p>
                            <p class="form-hint">
                                If you want to send a player a message, start the command with "{SendMessage}".
                            </p>
                            <p class="form-hint">
                                You can use HEX values or named colors BEFORE the text to set its color.
                            </p>
                            <p class="form-hint">
                                Supported named colors include {RED}, {GREEN}, {BLUE}, {YELLOW}, {ORANGE}, {PINK}, {PURPLE}, {BLACK}, {WHITE}, {CYAN}, and more.
                            </p>
                            <p class="form-hint">
                                Colors will remain in effect until another color is specified.
                            </p>
                            <p class="form-hint">
                                Example: "{SendMessage} {BLUE} Info: {#FFA500} Warning!" – "Info:" in blue, "Warning!" in orange using HEX.
                            </p>
                        </div>
                        
                        <div class="spacer-medium"></div>
                        
                        <!-- Commands List -->
                        <div class="commands-list">
            """);

        if (actions.isEmpty()) {
            sb.append("""
                                <div class="empty-state">
                                    <p class="empty-text">No commands added yet.</p>
                                    <p class="empty-text">Add commands above to execute when players interact.</p>
                                </div>
                    """);
        } else {
            sb.append("<div id=\"commands-list\" style=\"layout-mode: TopScrolling; padding: 6; anchor-height: 335;\">");

            for (int i = 0; i < actions.size(); i++) {
                if (i > 0) {
                    sb.append("                            <div class=\"spacer-small\"></div>\n");
                }

                CommandAction action = actions.get(i);
                String typeLabel = action.isRunAsServer() ? "As SERVER" : "As PLAYER";

                sb.append("""
                                <div class="command-card">
                                    <div class="command-info">
                                        <p class="command-text">/%s</p>
                                        <p class="command-type">Runs %s</p>
                                    </div>
                                    <div class="command-actions">
                                        <button id="toggle-%d" class="toggle-btn">Toggle</button>
                                        <div class="btn-spacer"></div>
                                        <button id="delete-%d" class="delete-btn">Delete</button>
                                    </div>
                                </div>
                        """.formatted(action.getCommand(), typeLabel, i, i));
            }

            sb.append("</div>");
        }

        sb.append("""
                        </div>
                        
                        <!-- Action Buttons -->
                        <div class="button-row">
                            <button id="done-btn" class="action-btn primary-btn">Done</button>
                            <div class="btn-spacer"></div>
                            <button id="cancel-btn" class="action-btn secondary-btn">Cancel</button>
                        </div>
                    </div>
                </div>
            </div>
            """);

        return sb.toString();
    }

    private void setupMainEventListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                         Tab currentTab, List<CitizenData> citizens) {
        // Tab switching
        page.addEventListener("tab-create", CustomUIEventBindingType.Activating, event ->
                openCitizensGUI(playerRef, store, Tab.CREATE));

        page.addEventListener("tab-manage", CustomUIEventBindingType.Activating, (event, ctx) -> {
            // Rebuild manage tab content
            ctx.getById("content-section", au.ellie.hyui.builders.GroupBuilder.class).ifPresent(group -> {
            });
            openCitizensGUI(playerRef, store, Tab.MANAGE);
        });

        // Create button
        if (currentTab == Tab.CREATE) {
            page.addEventListener("start-create", CustomUIEventBindingType.Activating, event ->
                    openCreateCitizenGUI(playerRef, store));
        }

        // Manage tab - edit and remove buttons
        if (currentTab == Tab.MANAGE) {
            for (int i = 0; i < citizens.size(); i++) {
                final int index = i;
                final CitizenData citizen = citizens.get(i);

                page.addEventListener("edit-" + i, CustomUIEventBindingType.Activating, event ->
                        openEditCitizenGUI(playerRef, store, citizen));

                page.addEventListener("remove-" + i, CustomUIEventBindingType.Activating, event -> {
                    plugin.getCitizensManager().removeCitizen(citizen.getId());
                    playerRef.sendMessage(Message.raw("Citizen '" + citizen.getName() + "' removed!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                });
            }
        }
    }

    private void setupCreateCitizenListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store,
                                            boolean initialIsPlayerModel, String initialName, String initialModelId,
                                            float initialScale, String initialPermission, String initialPermMessage,
                                            boolean initialUseLiveSkin, String initialSkinUsername) {
        final List<CommandAction> tempActions = new ArrayList<>();
        final String[] currentName = {initialName};
        final String[] currentModelId = {initialModelId.isEmpty() ? "PlayerTestModel_V" : initialModelId};
        final float[] currentScale = {initialScale};
        final String[] currentPermission = {initialPermission};
        final String[] currentPermMessage = {initialPermMessage};
        final boolean[] isPlayerModel = {initialIsPlayerModel};
        final boolean[] useLiveSkin = {initialUseLiveSkin};
        final String[] skinUsername = {initialSkinUsername};

        // Track input changes
        page.addEventListener("citizen-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentName[0] = ctx.getValue("citizen-name", String.class).orElse("");
        });

        page.addEventListener("citizen-model-id", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentModelId[0] = ctx.getValue("citizen-model-id", String.class).orElse("");
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

        page.addEventListener("skin-username", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            skinUsername[0] = ctx.getValue("skin-username", String.class).orElse("");
        });

        page.addEventListener("live-skin-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            useLiveSkin[0] = ctx.getValue("live-skin-check", Boolean.class).orElse(false);
        });

        // Entity type toggle buttons
        page.addEventListener("type-player", CustomUIEventBindingType.Activating, (event, ctx) -> {
            openCreateCitizenGUI(playerRef, store, true, currentName[0], currentModelId[0], currentScale[0],
                               currentPermission[0], currentPermMessage[0], useLiveSkin[0], true, skinUsername[0]);
        });

        page.addEventListener("type-entity", CustomUIEventBindingType.Activating, (event, ctx) -> {
            openCreateCitizenGUI(playerRef, store, false, currentName[0], currentModelId[0], currentScale[0],
                               currentPermission[0], currentPermMessage[0], useLiveSkin[0], true, skinUsername[0]);
        });

        // Get current player skin button
        page.addEventListener("get-player-skin-btn", CustomUIEventBindingType.Activating, event -> {
            skinUsername[0] = playerRef.getUsername();
            playerRef.sendMessage(Message.raw("Using your skin for this citizen!").color(Color.GREEN));
        });

        // Edit commands button
        page.addEventListener("edit-commands-btn", CustomUIEventBindingType.Activating, event -> {
            String tempId = "temp-" + UUID.randomUUID().toString();
            openCommandActionsGUI(playerRef, store, tempId, tempActions, true);
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
                    playerRef.sendMessage(Message.raw("Please enter a model ID!").color(Color.RED));
                    return;
                }
            }

            // Get player position and rotation
//            var ref = playerRef.getReference();
//            if (ref == null || !ref.isValid()) {
//                playerRef.sendMessage(Message.raw("Failed to get your position!").color(Color.RED));
//                return;
//            }

//            Player player = store.getComponent(playerRef.getReference(), Player.getComponentType());
//            if (player == null) {
//                playerRef.sendMessage(Message.raw("Failed to get your position!").color(Color.RED));
//                return;
//            }

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
                    0L
            );

            // If player model, fetch and cache the skin BEFORE adding
            if (isPlayerModel[0]) {
                if (skinUsername[0].trim().isEmpty()) {
                    // Use current player's skin
                    plugin.getCitizensManager().updateCitizenSkinFromPlayer(citizen, playerRef, false);
                    plugin.getCitizensManager().addCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Citizen \"" + name + "\" created at your position!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                } else {
                    // Fetch skin from username and wait for it
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
        final String[] currentModelId = {citizen.getModelId()};
        final float[] currentScale = {citizen.getScale()};
        final String[] currentPermission = {citizen.getRequiredPermission()};
        final String[] currentPermMessage = {citizen.getNoPermissionMessage()};
        final boolean[] isPlayerModel = {citizen.isPlayerModel()};
        final boolean[] useLiveSkin = {citizen.isUseLiveSkin()};
        final String[] skinUsername = {citizen.getSkinUsername()};

        // Track input changes
        page.addEventListener("citizen-name", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentName[0] = ctx.getValue("citizen-name", String.class).orElse("");
        });

        page.addEventListener("citizen-model-id", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            currentModelId[0] = ctx.getValue("citizen-model-id", String.class).orElse("");
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

        page.addEventListener("skin-username", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            skinUsername[0] = ctx.getValue("skin-username", String.class).orElse("");
        });

        page.addEventListener("live-skin-check", CustomUIEventBindingType.ValueChanged, (event, ctx) -> {
            useLiveSkin[0] = ctx.getValue("live-skin-check", Boolean.class).orElse(false);
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

        // Get current player skin button
        page.addEventListener("get-player-skin-btn", CustomUIEventBindingType.Activating, event -> {
            skinUsername[0] = playerRef.getUsername();
            playerRef.sendMessage(Message.raw("Using your skin for this citizen!").color(Color.GREEN));
        });

        // Edit commands button
        page.addEventListener("edit-commands-btn", CustomUIEventBindingType.Activating, event -> {
            openCommandActionsGUI(playerRef, store, citizen.getId(),
                    new ArrayList<>(citizen.getCommandActions()), false);
        });

        // Change position button
        page.addEventListener("change-position-btn", CustomUIEventBindingType.Activating, event -> {
//            var ref = playerRef.getReference();
//            if (ref == null || !ref.isValid()) {
//                playerRef.sendMessage(Message.raw("Failed to get your position!").color(Color.RED));
//                return;
//            }

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
                    playerRef.sendMessage(Message.raw("Please enter a model ID!").color(Color.RED));
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
            citizen.setSkinUsername(skinUsername[0].trim());

            // If player model, fetch and cache the skin BEFORE updating
            if (isPlayerModel[0]) {
                if (skinUsername[0].trim().isEmpty()) {
                    // Use current player's skin
                    plugin.getCitizensManager().updateCitizenSkinFromPlayer(citizen, playerRef, false);
                    plugin.getCitizensManager().updateCitizen(citizen, true);
                    playerRef.sendMessage(Message.raw("Citizen '" + name + "' updated!").color(Color.GREEN));
                    openCitizensGUI(playerRef, store, Tab.MANAGE);
                } else {
                    // Fetch skin from username and wait for it
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
                // Return to create screen
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
