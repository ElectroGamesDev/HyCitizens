package com.electro.hycitizens.ui;

import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.util.SkinUtilities;
import com.electro.hycitizens.util.SkinUtilities.CosmeticOptionEntry;
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

    private Map<String, List<CosmeticOptionEntry>> catalogue;

    private static final Map<UUID, CustomizerState> sessionStates = new ConcurrentHashMap<>();

    private static final int TILES_PER_ROW = 4;
    private static final int COLOR_TILES_PER_ROW = 6;

    private static class CustomizerState {
        PlayerSkin workingSkin;
        PlayerSkin originalSkin;
        CitizenData citizen;
        int selectedCategoryIndex;
        String selectedSlot;

        String selectedPartId;

        CustomizerState(CitizenData citizen, PlayerSkin working, PlayerSkin original) {
            this.citizen = citizen;
            this.workingSkin = working;
            this.originalSkin = original;
            this.selectedCategoryIndex = 0;
            this.selectedSlot = SkinUtilities.SLOT_CATEGORIES[0][1];
            // Pre-select the part that the skin already has equipped
            this.selectedPartId = derivePartId(working, this.selectedSlot);
        }

        static String derivePartId(PlayerSkin skin, String slot) {
            String v = SkinUtilities.getSkinField(skin, slot);
            return (v != null && !v.isEmpty()) ? SkinUtilities.partIdOf(v) : null;
        }
    }

    public SkinCustomizerUI(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void openSkinCustomizerGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store,
                                      @Nonnull CitizenData citizen) {
        if (catalogue == null || catalogue.isEmpty()) {
            catalogue = SkinUtilities.buildCosmeticCatalogue();
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

    private boolean applyValidatedSlotChange(@Nonnull PlayerRef playerRef,
                                             @Nonnull CustomizerState state,
                                             @Nonnull String slotName,
                                             String value) {
        if (!SkinUtilities.trySetSkinField(state.workingSkin, slotName, value)) {
            playerRef.sendMessage(Message.raw("That cosmetic option is not valid for this skin.").color(Color.RED));
            return false;
        }

        plugin.getCitizensManager().applySkinPreview(state.citizen, state.workingSkin);
        return true;
    }

    private String buildHTML(CustomizerState state) {
        String categorySidebar = buildCategorySidebar(state);
        String slotTabs        = buildSlotTabs(state);
        String partGrid        = buildPartGrid(state);
        String colorStrip      = buildColorStrip(state);

        String currentValue    = SkinUtilities.getSkinField(state.workingSkin, state.selectedSlot);
        String currentDisplay  = currentValue != null ? currentValue : "(none)";
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

                                    <!-- Part Grid -->
                                    <div class="grid-scroll">
                                        %s
                                    </div>

                                    <!-- Color Strip (only visible when a part is selected) -->
                                    %s

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
                partGrid,
                colorStrip
        ));
    }

    private String buildCategorySidebar(CustomizerState state) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SkinUtilities.SLOT_CATEGORIES.length; i++) {
            String catName  = SkinUtilities.SLOT_CATEGORIES[i][0];
            boolean isActive = (i == state.selectedCategoryIndex);
            String btnClass  = isActive ? "cat-btn cat-btn-active" : "cat-btn";
            sb.append("<button id=\"cat-%d\" class=\"%s\">%s</button>\n".formatted(i, btnClass, escapeHtml(catName)));
            sb.append("<div class=\"sp-sm\"></div>\n");
        }
        return sb.toString();
    }

    private String buildSlotTabs(CustomizerState state) {
        StringBuilder sb = new StringBuilder();
        String[] category = SkinUtilities.SLOT_CATEGORIES[state.selectedCategoryIndex];
        for (int i = 1; i < category.length; i++) {
            String slot        = category[i];
            String displayName = SkinUtilities.slotDisplayName(slot);
            boolean isActive   = slot.equals(state.selectedSlot);
            String btnClass    = isActive ? "slot-tab slot-tab-active" : "slot-tab";
            sb.append("<button id=\"slot-%s\" class=\"%s\">%s</button>\n"
                    .formatted(slot, btnClass, escapeHtml(displayName)));
            if (i < category.length - 1) {
                sb.append("<div class=\"sp-h-xs\"></div>\n");
            }
        }
        return sb.toString();
    }

    private String buildPartGrid(CustomizerState state) {
        List<CosmeticOptionEntry> entries = catalogue.getOrDefault(state.selectedSlot, List.of());
        String currentValue = SkinUtilities.getSkinField(state.workingSkin, state.selectedSlot);
        String currentPartId = (currentValue != null && !currentValue.isEmpty())
                ? SkinUtilities.partIdOf(currentValue) : null;

        StringBuilder sb = new StringBuilder();
        boolean isBodyType = state.selectedSlot.equalsIgnoreCase("bodyCharacteristic");

        sb.append("<div class=\"grid-row\">\n");
        int col = 0;

        // "None" tile
        if (!isBodyType) {
            boolean noneSelected = (currentValue == null || currentValue.isEmpty());
            String noneClass     = noneSelected ? "tile tile-none tile-selected" : "tile tile-none";
            sb.append("<button id=\"part-none\" class=\"%s\">None</button>\n".formatted(noneClass));
            col = 1;
        }

        for (int i = 0; i < entries.size(); i++) {
            if (col >= TILES_PER_ROW) {
                sb.append("</div>\n<div class=\"sp-tile-row\"></div>\n<div class=\"grid-row\">\n");
                col = 0;
            }

            CosmeticOptionEntry entry = entries.get(i);
            boolean isPartSelected = entry.partId.equalsIgnoreCase(
                    state.selectedPartId != null ? state.selectedPartId : "");

            boolean isEquipped = entry.partId.equalsIgnoreCase(currentPartId != null ? currentPartId : "");
            String tileClass;
            if (isEquipped) {
                tileClass = "tile tile-selected";
            } else if (isPartSelected) {
                tileClass = "tile tile-part-focused";
            } else {
                tileClass = "tile";
            }

            String displayName = formatCosmeticName(entry.partId);
            sb.append("<div class=\"sp-h-tile\"></div>\n");
            sb.append("<button id=\"part-%d\" class=\"%s\">%s</button>\n"
                    .formatted(i, tileClass, escapeHtml(displayName)));
            col++;
        }

        // Fill remainder of last row
        while (col < TILES_PER_ROW) {
            sb.append("<div class=\"sp-h-tile\"></div>\n");
            sb.append("<div class=\"tile-spacer\"></div>\n");
            col++;
        }

        sb.append("</div>\n");
        return sb.toString();
    }

    private String buildColorStrip(CustomizerState state) {
        if (state.selectedPartId == null) {
            return ""; // nothing to show
        }

        List<CosmeticOptionEntry> entries = catalogue.getOrDefault(state.selectedSlot, List.of());
        CosmeticOptionEntry focusedEntry = null;
        for (CosmeticOptionEntry e : entries) {
            if (e.partId.equalsIgnoreCase(state.selectedPartId)) {
                focusedEntry = e;
                break;
            }
        }

        if (focusedEntry == null || focusedEntry.colorOptions.size() <= 1) {
            // Part has no separate colour options
            return "";
        }

        String currentValue = SkinUtilities.getSkinField(state.workingSkin, state.selectedSlot);
        String partDisplayName = formatCosmeticName(state.selectedPartId);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"color-strip-label\"><p class=\"info-label\">Color — %s:</p></div>\n"
                .formatted(escapeHtml(partDisplayName)));
        sb.append("<div class=\"color-strip-scroll\">\n");
        sb.append("<div class=\"color-row\">\n");

        int col = 0;
        for (int i = 0; i < focusedEntry.colorOptions.size(); i++) {
            if (col >= COLOR_TILES_PER_ROW) {
                sb.append("</div>\n<div class=\"sp-tile-row\"></div>\n<div class=\"color-row\">\n");
                col = 0;
            }

            String fullId = focusedEntry.colorOptions.get(i);
            boolean isSelected = fullId.equalsIgnoreCase(currentValue != null ? currentValue : "");
            String tileClass = isSelected ? "color-tile color-tile-selected" : "color-tile";

            String colorLabel = colorLabelOf(fullId);

            sb.append("<div class=\"sp-h-tile\"></div>\n");
            sb.append("<button id=\"color-%d\" class=\"%s\">%s</button>\n"
                    .formatted(i, tileClass, escapeHtml(colorLabel)));
            col++;
        }

        // Fill remainder
        while (col < COLOR_TILES_PER_ROW) {
            sb.append("<div class=\"sp-h-tile\"></div>\n");
            sb.append("<div class=\"color-tile-spacer\"></div>\n");
            col++;
        }

        sb.append("</div>\n</div>\n");
        return sb.toString();
    }

    private void setupListeners(PageBuilder page, PlayerRef playerRef,
                                Store<EntityStore> store, CustomizerState state) {

        // Category sidebar
        for (int i = 0; i < SkinUtilities.SLOT_CATEGORIES.length; i++) {
            final int catIndex = i;
            page.addEventListener("cat-" + i, CustomUIEventBindingType.Activating, event -> {
                state.selectedCategoryIndex = catIndex;
                state.selectedSlot = SkinUtilities.SLOT_CATEGORIES[catIndex][1];
                state.selectedPartId = CustomizerState.derivePartId(state.workingSkin, state.selectedSlot);
                buildAndOpen(playerRef, store, state);
            });
        }

        // Slot tabs
        String[] category = SkinUtilities.SLOT_CATEGORIES[state.selectedCategoryIndex];
        for (int i = 1; i < category.length; i++) {
            String slot = category[i];
            page.addEventListener("slot-" + slot, CustomUIEventBindingType.Activating, event -> {
                state.selectedSlot = slot;
                state.selectedPartId = CustomizerState.derivePartId(state.workingSkin, state.selectedSlot);
                buildAndOpen(playerRef, store, state);
            });
        }

        // Part grid tiles
        List<CosmeticOptionEntry> entries = catalogue.getOrDefault(state.selectedSlot, List.of());

        // "None" tile
        boolean isBodyType = state.selectedSlot.equalsIgnoreCase("bodyCharacteristic");
        if (!isBodyType) {
            page.addEventListener("part-none", CustomUIEventBindingType.Activating, event -> {
                if (applyValidatedSlotChange(playerRef, state, state.selectedSlot, null)) {
                    state.selectedPartId = null;
                }
                buildAndOpen(playerRef, store, state);
            });
        }

        for (int i = 0; i < entries.size(); i++) {
            final CosmeticOptionEntry entry = entries.get(i);
            page.addEventListener("part-" + i, CustomUIEventBindingType.Activating, event -> {
                if (entry.colorOptions.size() == 1) {
                    if (applyValidatedSlotChange(playerRef, state, state.selectedSlot, entry.colorOptions.get(0))) {
                        state.selectedPartId = entry.partId;
                    }
                } else {
                    String currentValue = SkinUtilities.getSkinField(state.workingSkin, state.selectedSlot);
                    boolean alreadyCorrectPart = currentValue != null
                            && SkinUtilities.partIdOf(currentValue).equalsIgnoreCase(entry.partId);
                    if (!alreadyCorrectPart) {
                        if (applyValidatedSlotChange(playerRef, state, state.selectedSlot, entry.colorOptions.get(0))) {
                            state.selectedPartId = entry.partId;
                        }
                    } else {
                        state.selectedPartId = entry.partId;
                    }
                }

                buildAndOpen(playerRef, store, state);
            });
        }

        // Colour strip tiles
        if (state.selectedPartId != null) {
            CosmeticOptionEntry focusedEntry = null;
            for (CosmeticOptionEntry e : entries) {
                if (e.partId.equalsIgnoreCase(state.selectedPartId)) {
                    focusedEntry = e;
                    break;
                }
            }

            if (focusedEntry != null && focusedEntry.colorOptions.size() > 1) {
                for (int i = 0; i < focusedEntry.colorOptions.size(); i++) {
                    final String fullId = focusedEntry.colorOptions.get(i);
                    page.addEventListener("color-" + i, CustomUIEventBindingType.Activating, event -> {
                        applyValidatedSlotChange(playerRef, state, state.selectedSlot, fullId);
                        buildAndOpen(playerRef, store, state);
                    });
                }
            }
        }

        // Per-slot Random
        page.addEventListener("slot-random-btn", CustomUIEventBindingType.Activating, event -> {
            List<CosmeticOptionEntry> slotEntries = catalogue.getOrDefault(state.selectedSlot, List.of());
            if (!slotEntries.isEmpty()) {
                // Pick a random base part, then a random colour from it.
                CosmeticOptionEntry randomEntry = slotEntries.get(
                        RandomUtil.getSecureRandom().nextInt(slotEntries.size()));
                String randomValue = randomEntry.colorOptions.get(
                        RandomUtil.getSecureRandom().nextInt(randomEntry.colorOptions.size()));
                if (applyValidatedSlotChange(playerRef, state, state.selectedSlot, randomValue)) {
                    state.selectedPartId = randomEntry.partId;
                }
            }
            buildAndOpen(playerRef, store, state);
        });

        // Per-slot Clear
        page.addEventListener("slot-clear-btn", CustomUIEventBindingType.Activating, event -> {
            if (state.selectedSlot.equalsIgnoreCase("bodyCharacteristic")) return;
            if (applyValidatedSlotChange(playerRef, state, state.selectedSlot, null)) {
                state.selectedPartId = null;
            }
            buildAndOpen(playerRef, store, state);
        });

        // Randomize All
        page.addEventListener("randomize-all-btn", CustomUIEventBindingType.Activating, event -> {
            try {
                PlayerSkin randomSkin = CosmeticsModule.get().generateRandomSkin(RandomUtil.getSecureRandom());
                for (String slot : SkinUtilities.SLOT_NAMES) {
                    SkinUtilities.setSkinField(state.workingSkin, slot,
                            SkinUtilities.getSkinField(randomSkin, slot));
                }
                state.selectedPartId = CustomizerState.derivePartId(state.workingSkin, state.selectedSlot);
                plugin.getCitizensManager().applySkinPreview(state.citizen, state.workingSkin);
                buildAndOpen(playerRef, store, state);
            } catch (Exception e) {
                playerRef.sendMessage(Message.raw("Failed to randomize: " + e.getMessage()).color(Color.RED));
            }
        });

        // Done
        page.addEventListener("done-btn", CustomUIEventBindingType.Activating, event -> {
            if (!SkinUtilities.isValidSkin(state.workingSkin)) {
                playerRef.sendMessage(Message.raw("Skin customization contains an invalid cosmetic option and was not saved.").color(Color.RED));
                return;
            }

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

    private static String colorLabelOf(String fullId) {
        String[] parts = fullId.split("\\.", 3);
        if (parts.length < 2) return formatCosmeticName(fullId);
        String colorPart   = formatCosmeticName(parts[1]);
        String variantPart = parts.length > 2 ? formatCosmeticName(parts[2]) : null;
        return variantPart != null ? colorPart + " (" + variantPart + ")" : colorPart;
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

                    .tile-part-focused {
                        background-color: #2d4a7a;
                    }

                    .tile-none {
                        background-color: #30363d;
                    }

                    .tile-spacer {
                        flex-weight: 0;
                        anchor-width: 130;
                        anchor-height: 38;
                    }

                    .color-strip-label {
                        layout: left;
                        flex-weight: 0;
                        padding: 6 0 2 0;
                    }

                    .color-strip-scroll {
                        layout-mode: TopScrolling;
                        flex-weight: 0;
                        max-anchor-height: 130;
                        background-color: #161b22;
                        border-radius: 6;
                        padding: 4 0 4 0;
                    }

                    .color-row {
                        layout: left;
                        flex-weight: 0;
                    }

                    .color-tile {
                        flex-weight: 0;
                        anchor-width: 90;
                        anchor-height: 32;
                        border-radius: 5;
                        font-size: 9;
                        background-color: #21262d;
                    }

                    .color-tile-selected {
                        background-color: #1f6feb;
                    }

                    .color-tile-spacer {
                        flex-weight: 0;
                        anchor-width: 90;
                        anchor-height: 32;
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

                    .sp-xs  { flex-weight: 0; anchor-height: 4; }
                    .sp-sm  { flex-weight: 0; anchor-height: 6; }
                    .sp-h-xs  { flex-weight: 0; anchor-width: 4; }
                    .sp-h-sm  { flex-weight: 0; anchor-width: 8; }
                    .sp-h-tile  { flex-weight: 0; anchor-width: 5; }
                    .sp-tile-row { flex-weight: 0; anchor-height: 5; }
                </style>
                """;
    }
}
