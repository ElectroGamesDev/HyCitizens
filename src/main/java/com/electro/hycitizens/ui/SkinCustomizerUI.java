package com.electro.hycitizens.ui;

import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.util.SkinUtilities;
import com.hypixel.hytale.common.util.RandomUtil;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SkinCustomizerUI {

    private final HyCitizensPlugin plugin;
    private Map<String, List<String>> cosmeticOptions;

    private static final Map<UUID, CustomizerState> sessionStates = new ConcurrentHashMap<>();

    private static final int TILES_PER_ROW = 4;

    private static class CustomizerState {
        PlayerSkin workingSkin;
        PlayerSkin originalSkin;
        CitizenData citizen;
        int selectedCategoryIndex;
        String selectedSlot;
        String customInputValue = "";

        CustomizerState(CitizenData citizen, PlayerSkin working, PlayerSkin original) {
            this.citizen = citizen;
            this.workingSkin = working;
            this.originalSkin = original;
            this.selectedCategoryIndex = 0;
            this.selectedSlot = SkinUtilities.SLOT_CATEGORIES[0][1];
        }
    }

    public SkinCustomizerUI(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void openSkinCustomizerGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                      @Nonnull CitizenData citizen) {
        if (cosmeticOptions == null || cosmeticOptions.isEmpty()) {
            playerRef.sendMessage(Message.raw("Discovering cosmetic options... please wait.").color(Color.YELLOW));
            cosmeticOptions = SkinUtilities.discoverCosmeticOptions(50);
            playerRef.sendMessage(Message.raw("Found cosmetic options! Opening customizer...").color(Color.GREEN));
        }

        UUID playerId = playerRef.getUuid();
        CustomizerState state = sessionStates.get(playerId);
        if (state == null || state.citizen != citizen) {
            PlayerSkin current = citizen.getCachedSkin();
            if (current == null) current = SkinUtilities.createDefaultSkin();
            state = new CustomizerState(citizen, SkinUtilities.copySkin(current), SkinUtilities.copySkin(current));
            sessionStates.put(playerId, state);
        }

        buildAndOpen(playerRef, store, state);
    }

    public void clearState(@Nonnull PlayerRef playerRef) {
        sessionStates.remove(playerRef.getUuid());
    }

    private void buildAndOpen(PlayerRef playerRef, Store<EntityStore> store, CustomizerState state) {
        String html = buildHTML(state);

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

        setupListeners(page, playerRef, store, state);
        page.open(store);
    }

    private String buildHTML(CustomizerState state) {
        String categorySidebar = buildCategorySidebar(state);
        String slotTabs = buildSlotTabs(state);
        String optionsGrid = buildOptionsGrid(state);

        String currentValue = SkinUtilities.getSkinField(state.workingSkin, state.selectedSlot);
        String currentDisplay = currentValue != null ? currentValue : "(none)";
        String slotDisplayName = SkinUtilities.slotDisplayName(state.selectedSlot);

        return new TemplateProcessor().process(getStyles() + """
                <div class="overlay">
                    <div class="panel">
                    
                        <div class="panel-body">

                            <!-- Header -->
                            <div class="panel-header">
                                <p class="title">Skin Customizer</p>
                                <p class="subtitle">%s</p>
                            </div>
                            
                            <!-- Content: Sidebar + Main -->
                            <div class="content-row">
    
                                <!-- Category Sidebar -->
                                <div class="cat-sidebar">
                                    %s
                                </div>
    
                                <!-- Main Area -->
                                <div class="main-area">
    
                                    <!-- Slot Tabs -->
                                    <div class="slot-tabs-row">
                                        %s
                                    </div>
    
                                    <!-- Current Value Info -->
                                    <div class="info-bar">
                                        <p class="info-label">%s:</p>
                                        <div class="sp-h-xs"></div>
                                        <p class="info-value">%s</p>
                                        <div style="flex-weight: 1;"></div>
                                        <button id="slot-random-btn" class="btn-small-accent" style="anchor-width: 140;">Random</button>
                                        <div class="sp-h-xs"></div>
                                        <button id="slot-clear-btn" class="btn-small-ghost" style="anchor-width: 120;">Clear</button>
                                    </div>
    
                                    <!-- Options Grid -->
                                    <div class="grid-scroll">
                                        %s
                                    </div>
    
                                </div>
    
                            </div>
                            
                            <!-- Action Bar -->
                            <div class="action-bar">
                                <button id="randomize-all-btn" class="btn-accent" style="anchor-width: 140;">Randomize</button>
                                <div style="flex-weight: 1;"></div>
                                <button id="done-btn" class="btn-success" style="anchor-width: 120;">Done</button>
                                <div class="sp-h-sm"></div>
                                <button id="cancel-btn" class="btn-ghost" style="anchor-width: 125;">Cancel</button>
                            </div>
                        </div>
                    </div>
                </div>
                """.formatted(
                escapeHtml(state.citizen.getName()),
                categorySidebar,
                slotTabs,
                escapeHtml(slotDisplayName),
                escapeHtml(currentDisplay),
                optionsGrid,
                escapeHtml(state.customInputValue)
        ));
    }

    private String buildCategorySidebar(CustomizerState state) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SkinUtilities.SLOT_CATEGORIES.length; i++) {
            String catName = SkinUtilities.SLOT_CATEGORIES[i][0];
            boolean isActive = (i == state.selectedCategoryIndex);
            String btnClass = isActive ? "cat-btn cat-btn-active" : "cat-btn";
            sb.append("<button id=\"cat-%d\" class=\"%s\">%s</button>\n".formatted(i, btnClass, escapeHtml(catName)));
            sb.append("<div class=\"sp-sm\"></div>\n");
        }
        return sb.toString();
    }

    private String buildSlotTabs(CustomizerState state) {
        StringBuilder sb = new StringBuilder();
        String[] category = SkinUtilities.SLOT_CATEGORIES[state.selectedCategoryIndex];
        for (int i = 1; i < category.length; i++) {
            String slot = category[i];
            String displayName = SkinUtilities.slotDisplayName(slot);
            boolean isActive = slot.equals(state.selectedSlot);
            String btnClass = isActive ? "slot-tab slot-tab-active" : "slot-tab";
            sb.append("<button id=\"slot-%s\" class=\"%s\">%s</button>\n".formatted(slot, btnClass, escapeHtml(displayName)));
            if (i < category.length - 1) {
                sb.append("<div class=\"sp-h-xs\"></div>\n");
            }
        }
        return sb.toString();
    }

    private String buildOptionsGrid(CustomizerState state) {
        String currentSlot = state.selectedSlot;
        String currentValue = SkinUtilities.getSkinField(state.workingSkin, currentSlot);
        List<String> options = new ArrayList<>(cosmeticOptions.getOrDefault(currentSlot, List.of()));

        // Add current value to list if not already present
        if (currentValue != null && !currentValue.isEmpty()) {
            boolean found = false;
            for (String opt : options) {
                if (opt.equalsIgnoreCase(currentValue)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                options.add(0, currentValue);
            }
        }

        StringBuilder sb = new StringBuilder();

        boolean isBodyType = state.selectedSlot.equalsIgnoreCase("bodyCharacteristic");

        sb.append("<div class=\"grid-row\">\n");

        int col = 0;

        if (!isBodyType) {
            boolean noneSelected = (currentValue == null || currentValue.isEmpty());
            String noneClass = noneSelected ? "tile tile-none tile-selected" : "tile tile-none";
            sb.append("<button id=\"opt-none\" class=\"%s\">None</button>\n".formatted(noneClass));
            col = 1;
        }

        for (int i = 0; i < options.size(); i++) {
            if (col >= TILES_PER_ROW) {
                sb.append("</div>\n<div class=\"sp-tile-row\"></div>\n<div class=\"grid-row\">\n");
                col = 0;
            }

            String value = options.get(i);
            boolean isSelected = value.equalsIgnoreCase(currentValue != null ? currentValue : "");
            String tileClass = isSelected ? "tile tile-selected" : "tile";
            String displayName = formatCosmeticName(value);

            sb.append("<div class=\"sp-h-tile\"></div>\n");
            sb.append("<button id=\"opt-%d\" class=\"%s\">%s</button>\n".formatted(i, tileClass, escapeHtml(displayName)));
            col++;
        }

        // Fill remaining cells in last row with empty spacers for alignment
        while (col < TILES_PER_ROW) {
            sb.append("<div class=\"sp-h-tile\"></div>\n");
            sb.append("<div class=\"tile-spacer\"></div>\n");
            col++;
        }

        sb.append("</div>\n");
        return sb.toString();
    }

    private void setupListeners(PageBuilder page, PlayerRef playerRef, Store<EntityStore> store, CustomizerState state) {
        // Category sidebar buttons
        for (int i = 0; i < SkinUtilities.SLOT_CATEGORIES.length; i++) {
            final int catIndex = i;
            page.addEventListener("cat-" + i, CustomUIEventBindingType.Activating, event -> {
                state.selectedCategoryIndex = catIndex;
                state.selectedSlot = SkinUtilities.SLOT_CATEGORIES[catIndex][1]; // first slot in category
                state.customInputValue = "";
                buildAndOpen(playerRef, store, state);
            });
        }

        // Slot tab buttons
        String[] category = SkinUtilities.SLOT_CATEGORIES[state.selectedCategoryIndex];
        for (int i = 1; i < category.length; i++) {
            String slot = category[i];
            page.addEventListener("slot-" + slot, CustomUIEventBindingType.Activating, event -> {
                state.selectedSlot = slot;
                state.customInputValue = "";
                buildAndOpen(playerRef, store, state);
            });
        }

        // Grid option tiles
        List<String> options = new ArrayList<>(cosmeticOptions.getOrDefault(state.selectedSlot, List.of()));
        String currentValue = SkinUtilities.getSkinField(state.workingSkin, state.selectedSlot);
        if (currentValue != null && !currentValue.isEmpty()) {
            boolean found = false;
            for (String opt : options) {
                if (opt.equalsIgnoreCase(currentValue)) { found = true; break; }
            }
            if (!found) options.add(0, currentValue);
        }

        // "None" tile
        boolean isBodyType = state.selectedSlot.equalsIgnoreCase("bodyCharacteristic");
        if (!isBodyType) {
            page.addEventListener("opt-none", CustomUIEventBindingType.Activating, event -> {
                if (state.selectedSlot.equalsIgnoreCase("bodyCharacteristic")) {
                    return;
                }

                SkinUtilities.setSkinField(state.workingSkin, state.selectedSlot, null);
                plugin.getCitizensManager().applySkinPreview(state.citizen, state.workingSkin);
                buildAndOpen(playerRef, store, state);
            });
        }

        for (int i = 0; i < options.size(); i++) {
            final String value = options.get(i);
            page.addEventListener("opt-" + i, CustomUIEventBindingType.Activating, event -> {
                SkinUtilities.setSkinField(state.workingSkin, state.selectedSlot, value);
                plugin.getCitizensManager().applySkinPreview(state.citizen, state.workingSkin);
                buildAndOpen(playerRef, store, state);
            });
        }

        // Per-slot Random
        page.addEventListener("slot-random-btn", CustomUIEventBindingType.Activating, event -> {

            List<String> slotOptions = cosmeticOptions.getOrDefault(state.selectedSlot, List.of());

            List<String> valid = new ArrayList<>();
            for (String opt : slotOptions) {
                if (opt != null && !opt.isBlank()) {
                    valid.add(opt);
                }
            }

            if (!valid.isEmpty()) {
                String randomValue = valid.get(RandomUtil.getSecureRandom().nextInt(valid.size()));
                SkinUtilities.setSkinField(state.workingSkin, state.selectedSlot, randomValue);
                plugin.getCitizensManager().applySkinPreview(state.citizen, state.workingSkin);
            }

            buildAndOpen(playerRef, store, state);
        });

        // Per-slot Clear
        page.addEventListener("slot-clear-btn", CustomUIEventBindingType.Activating, event -> {
            if (state.selectedSlot.equalsIgnoreCase("bodyCharacteristic")) {
                return;
            }

            SkinUtilities.setSkinField(state.workingSkin, state.selectedSlot, null);
            plugin.getCitizensManager().applySkinPreview(state.citizen, state.workingSkin);
            buildAndOpen(playerRef, store, state);
        });


        // Randomize All
        page.addEventListener("randomize-all-btn", CustomUIEventBindingType.Activating, event -> {
            try {
                PlayerSkin randomSkin = CosmeticsModule.get().generateRandomSkin(RandomUtil.getSecureRandom());
                for (String slot : SkinUtilities.SLOT_NAMES) {
                    SkinUtilities.setSkinField(state.workingSkin, slot, SkinUtilities.getSkinField(randomSkin, slot));
                }
                plugin.getCitizensManager().applySkinPreview(state.citizen, state.workingSkin);
                buildAndOpen(playerRef, store, state);
            } catch (Exception e) {
                playerRef.sendMessage(Message.raw("Failed to randomize: " + e.getMessage()).color(Color.RED));
            }
        });

        // Done
        page.addEventListener("done-btn", CustomUIEventBindingType.Activating, event -> {
            state.citizen.setCachedSkin(SkinUtilities.copySkin(state.workingSkin));
            state.citizen.setUseLiveSkin(false);
            state.citizen.setSkinUsername("custom_" + UUID.randomUUID().toString().substring(0, 8));
            state.citizen.setLastSkinUpdate(System.currentTimeMillis());
            plugin.getCitizensManager().saveCitizen(state.citizen);
            plugin.getCitizensManager().applySkinPreview(state.citizen, state.workingSkin);
            playerRef.sendMessage(Message.raw("Skin customization saved!").color(Color.GREEN));
            clearState(playerRef);
            plugin.getCitizensUI().openEditCitizenGUI(playerRef, store, state.citizen);
        });

        // Cancel
        page.addEventListener("cancel-btn", CustomUIEventBindingType.Activating, event -> {
            plugin.getCitizensManager().applySkinPreview(state.citizen, state.originalSkin);
            clearState(playerRef);
            plugin.getCitizensUI().openEditCitizenGUI(playerRef, store, state.citizen);
        });
    }

    private static String formatCosmeticName(String value) {
        if (value == null || value.isEmpty()) return "None";
        String[] words = value.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(" ");
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
            }
        }
        String result = sb.toString();
        if (result.length() > 20) {
            result = result.substring(0, 18) + "..";
        }
        return result;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String getStyles() {
        return """
                <style>
                    .overlay {
                        layout: right;
                        anchor-width: 100%;
                        anchor-height: 100%;
                        padding: 20;
                    }

                    .panel {
                        layout: top;
                        anchor-width: 680;
                        anchor-height: 850;
                        background-color: #0d1117(0.97);
                        border-radius: 12;
                    }

                    .panel-header {
                        layout: top;
                        flex-weight: 0;
                        background-color: #161b22;
                        padding: 14 20 14 20;
                        border-radius: 12 12 0 0;
                    }

                    .title {
                        color: #e6edf3;
                        font-size: 20;
                        font-weight: bold;
                        text-align: center;
                    }

                    .subtitle {
                        color: #8b949e;
                        font-size: 12;
                        text-align: center;
                        padding-top: 2;
                    }

                    .action-bar {
                        layout: left;
                        flex-weight: 0;
                        padding: 8 12 8 12;
                        background-color: #161b22;
                    }

                    .panel-body {
                        layout: top;
                        flex-weight: 1;
                    }
                    
                    .content-row {
                        layout: left;
                        flex-weight: 1;
                    }

                    .cat-sidebar {
                        layout: top;
                        flex-weight: 0;
                        anchor-width: 82;
                        padding: 8 4 8 8;
                        background-color: #0d1117;
                    }

                    .cat-btn {
                        flex-weight: 0;
                        anchor-width: 125;
                        anchor-height: 42;
                        border-radius: 6;
                        font-size: 11;
                        background-color: #21262d;
                    }

                    .cat-btn-active {
                        background-color: #1f6feb;
                    }

                    .main-area {
                        layout: top;
                        flex-weight: 1;
                        padding: 8;
                    }

                    .slot-tabs-row {
                        layout: left;
                        flex-weight: 0;
                        padding-bottom: 6;
                    }

                    .slot-tab {
                        flex-weight: 0;
                        anchor-height: 30;
                        border-radius: 6;
                        font-size: 10;
                        padding-left: 8;
                        padding-right: 8;
                        background-color: #21262d;
                    }

                    .slot-tab-active {
                        background-color: #30363d;
                    }

                    .info-bar {
                        layout: left;
                        flex-weight: 0;
                        padding: 6 8 6 8;
                        background-color: #161b22;
                        border-radius: 6;
                    }

                    .info-label {
                        color: #8b949e;
                        font-size: 11;
                        font-weight: bold;
                        flex-weight: 0;
                    }

                    .info-value {
                        color: #58a6ff;
                        font-size: 11;
                        flex-weight: 0;
                    }

                    .grid-scroll {
                        layout-mode: TopScrolling;
                        flex-weight: 1;
                        padding: 6 0 6 0;
                    }

                    .grid-row {
                        layout: left;
                        flex-weight: 0;
                    }

                    .tile {
                        flex-weight: 0;
                        anchor-width: 130;
                        anchor-height: 38;
                        border-radius: 6;
                        font-size: 10;
                        background-color: #21262d;
                    }

                    .tile-selected {
                        background-color: #1f6feb;
                    }

                    .tile-none {
                        background-color: #30363d;
                    }

                    .tile-spacer {
                        flex-weight: 0;
                        anchor-width: 130;
                        anchor-height: 38;
                    }

                    .custom-bar {
                        layout: left;
                        flex-weight: 0;
                        padding: 6 0 2 0;
                    }

                    .custom-input {
                        flex-weight: 1;
                        anchor-height: 32;
                        background-color: #21262d;
                        border-radius: 6;
                        font-size: 10;
                    }

                    .btn-accent {
                        flex-weight: 0;
                        anchor-height: 36;
                        border-radius: 6;
                    }

                    .btn-success {
                        flex-weight: 0;
                        anchor-height: 36;
                        border-radius: 6;
                    }

                    .btn-ghost {
                        flex-weight: 0;
                        anchor-height: 36;
                        border-radius: 6;
                    }

                    .btn-small-accent {
                        flex-weight: 0;
                        anchor-height: 26;
                        border-radius: 4;
                        font-size: 10;
                    }

                    .btn-small-ghost {
                        flex-weight: 0;
                        anchor-height: 26;
                        border-radius: 4;
                        font-size: 10;
                    }

                    .sp-xs {
                        flex-weight: 0;
                        anchor-height: 4;
                    }

                    .sp-sm {
                        flex-weight: 0;
                        anchor-height: 6;
                    }

                    .sp-h-xs {
                        flex-weight: 0;
                        anchor-width: 4;
                    }

                    .sp-h-sm {
                        flex-weight: 0;
                        anchor-width: 8;
                    }

                    .sp-h-tile {
                        flex-weight: 0;
                        anchor-width: 5;
                    }

                    .sp-tile-row {
                        flex-weight: 0;
                        anchor-height: 5;
                    }
                </style>
                """;
    }
}
