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
        String currentDisplay  = formatCosmeticValue(currentValue);
        String slotDisplayName = SkinUtilities.slotDisplayName(state.selectedSlot);

        return new TemplateProcessor().process(getStyles() + """
                <div class="page-overlay">
                    <div class="main-container decorated-container" style="anchor-width: 980; anchor-height: 900;">

                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Skin Customizer</p>
                                <p class="header-subtitle">%s</p>
                            </div>
                        </div>

                        <div class="body">
                            <div class="editor-row">

                                <div class="cat-sidebar">
                                    <div class="section-label-block">
                                        <p class="section-kicker">Categories</p>
                                        <p class="section-hint">Pick a cosmetic group</p>
                                    </div>
                                    <div class="spacer-sm"></div>
                                    %s
                                </div>

                                <div class="main-area">

                                    <div class="skin-summary">
                                        <div class="summary-text">
                                            <p class="summary-label">%s</p>
                                            <p class="summary-value">%s</p>
                                        </div>
                                        <div style="flex-weight: 1;"></div>
                                        <button id="slot-random-btn" class="secondary-button small-secondary-button" style="anchor-width: 135;">Random</button>
                                        <div class="spacer-h-sm"></div>
                                        <button id="slot-clear-btn" class="secondary-button small-secondary-button" style="anchor-width: 120;">Clear</button>
                                    </div>

                                    <div class="spacer-md"></div>

                                    <div class="section slot-section">
                                        <div class="section-header-compact">
                                            <p class="section-title-left">Slots</p>
                                            <p class="section-description-left">Choose the exact skin slot to edit</p>
                                        </div>
                                        <div class="spacer-sm"></div>
                                        <div class="slot-tabs-row">
                                            %s
                                        </div>
                                    </div>

                                    <div class="spacer-md"></div>

                                    <div class="section parts-section">
                                        <div class="section-header-compact">
                                            <p class="section-title-left">Cosmetics</p>
                                            <p class="section-description-left">Selected options preview immediately on the citizen</p>
                                        </div>
                                        <div class="spacer-sm"></div>
                                        <div class="grid-scroll" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"'>
                                            %s
                                        </div>
                                    </div>

                                    %s

                                </div>

                            </div>
                        </div>

                        <div class="footer">
                            <button id="randomize-all-btn" class="secondary-button" style="anchor-width: 160; anchor-height: 40;">Randomize</button>
                            <div style="flex-weight: 1;"></div>
                            <button id="cancel-btn" class="secondary-button" style="anchor-width: 130; anchor-height: 40;">Cancel</button>
                            <div class="spacer-h-md"></div>
                            <button id="done-btn" class="secondary-button" style="anchor-width: 150; anchor-height: 40;">Done</button>
                        </div>

                    </div>
                </div>
                """.formatted(
                "Editing " + escapeHtml(state.citizen.getName()),
                categorySidebar,
                escapeHtml(slotDisplayName),
                escapeHtml(currentDisplay),
                slotTabs,
                partGrid,
                colorStrip
        ));
    }

    private String buildCategorySidebar(CustomizerState state) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SkinUtilities.SLOT_CATEGORIES.length; i++) {
            String catName  = SkinUtilities.SLOT_CATEGORIES[i][0];
            boolean isActive = (i == state.selectedCategoryIndex);
            String btnClass  = isActive
                    ? "secondary-button small-secondary-button button-selected"
                    : "secondary-button small-secondary-button";
            sb.append("<button id=\"cat-%d\" class=\"%s\" style=\"anchor-width: 150;\">%s</button>\n"
                    .formatted(i, btnClass, escapeHtml(catName)));
            sb.append("<div class=\"spacer-xs\"></div>\n");
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
            String btnClass    = isActive
                    ? "secondary-button small-secondary-button button-selected"
                    : "secondary-button small-secondary-button";
            sb.append("<button id=\"slot-%s\" class=\"%s\" style=\"anchor-width: 118;\">%s</button>\n"
                    .formatted(slot, btnClass, escapeHtml(displayName)));
            if (i < category.length - 1) {
                sb.append("<div class=\"spacer-h-sm\"></div>\n");
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

        if (entries.isEmpty() && isBodyType) {
            return """
                    <div class="empty-state">
                        <p class="empty-state-title">No cosmetic options found</p>
                        <p class="empty-state-description">Try another slot or randomize the full skin.</p>
                    </div>
                    """;
        }

        sb.append("<div class=\"grid-row\">\n");
        int col = 0;

        // "None" tile
        if (!isBodyType) {
            boolean noneSelected = (currentValue == null || currentValue.isEmpty());
            String noneClass     = noneSelected
                    ? "secondary-button small-secondary-button button-selected"
                    : "secondary-button small-secondary-button";
            sb.append("<button id=\"part-none\" class=\"%s\" style=\"anchor-width: 142;\">None</button>\n"
                    .formatted(noneClass));
            col = 1;
        }

        for (int i = 0; i < entries.size(); i++) {
            if (col >= TILES_PER_ROW) {
                sb.append("</div>\n<div class=\"spacer-sm\"></div>\n<div class=\"grid-row\">\n");
                col = 0;
            }

            CosmeticOptionEntry entry = entries.get(i);
            boolean isPartSelected = entry.partId.equalsIgnoreCase(
                    state.selectedPartId != null ? state.selectedPartId : "");

            boolean isEquipped = entry.partId.equalsIgnoreCase(currentPartId != null ? currentPartId : "");
            String tileClass;
            if (isEquipped) {
                tileClass = "secondary-button small-secondary-button button-selected";
            } else if (isPartSelected) {
                tileClass = "secondary-button small-secondary-button button-selected";
            } else {
                tileClass = "secondary-button small-secondary-button";
            }

            String displayName = formatCosmeticName(entry.partId);
            if (col > 0) {
                sb.append("<div class=\"spacer-h-sm\"></div>\n");
            }
            sb.append("<button id=\"part-%d\" class=\"%s\" style=\"anchor-width: 142;\">%s</button>\n"
                    .formatted(i, tileClass, escapeHtml(displayName)));
            col++;
        }

        // Fill remainder of last row
        while (col < TILES_PER_ROW) {
            if (col > 0) {
                sb.append("<div class=\"spacer-h-sm\"></div>\n");
            }
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
        sb.append("<div class=\"spacer-md\"></div>\n");
        sb.append("<div class=\"section color-section\">\n");
        sb.append("<div class=\"section-header-compact\"><p class=\"section-title-left\">Color</p><p class=\"section-description-left\">%s variants</p></div>\n"
                .formatted(escapeHtml(partDisplayName)));
        sb.append("<div class=\"spacer-sm\"></div>\n");
        sb.append("<div class=\"color-strip-scroll\" data-hyui-scrollbar-style='\"Common.ui\" \"DefaultScrollbarStyle\"'>\n");
        sb.append("<div class=\"color-row\">\n");

        int col = 0;
        for (int i = 0; i < focusedEntry.colorOptions.size(); i++) {
            if (col >= COLOR_TILES_PER_ROW) {
                sb.append("</div>\n<div class=\"spacer-sm\"></div>\n<div class=\"color-row\">\n");
                col = 0;
            }

            String fullId = focusedEntry.colorOptions.get(i);
            boolean isSelected = fullId.equalsIgnoreCase(currentValue != null ? currentValue : "");
            String tileClass = isSelected
                    ? "secondary-button small-secondary-button button-selected"
                    : "secondary-button small-secondary-button";

            String colorLabel = colorLabelOf(fullId);

            if (col > 0) {
                sb.append("<div class=\"spacer-h-sm\"></div>\n");
            }
            sb.append("<button id=\"color-%d\" class=\"%s\" style=\"anchor-width: 93;\">%s</button>\n"
                    .formatted(i, tileClass, escapeHtml(colorLabel)));
            col++;
        }

        // Fill remainder
        while (col < COLOR_TILES_PER_ROW) {
            if (col > 0) {
                sb.append("<div class=\"spacer-h-sm\"></div>\n");
            }
            sb.append("<div class=\"color-tile-spacer\"></div>\n");
            col++;
        }

        sb.append("</div>\n</div>\n</div>\n");
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

    private static String formatCosmeticValue(String value) {
        if (value == null || value.isEmpty()) return "None";
        String[] parts = value.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(formatCosmeticName(part));
        }
        return sb.toString();
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
                    .page-overlay {
                        layout: right;
                        flex-weight: 1;
                        anchor-width: 100%;
                        anchor-height: 100%;
                        padding: 20;
                    }

                    .main-container {
                    }

                    .container-title {
                        layout: top;
                        flex-weight: 0;
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
                        text-align: center;
                        anchor-width: 100%;
                        padding-top: 6;
                    }

                    .body {
                        layout: top;
                        flex-weight: 1;
                        padding: 18 20 10 20;
                    }

                    .footer {
                        layout: center;
                        flex-weight: 0;
                        padding: 14 16 16 16;
                    }

                    .editor-row {
                        layout: left;
                        flex-weight: 1;
                    }

                    .cat-sidebar {
                        layout: top;
                        flex-weight: 0;
                        anchor-width: 178;
                        background-color: #101a27(0.78);
                        border-radius: 10;
                        padding: 14;
                    }

                    .section-label-block {
                        layout: top;
                        flex-weight: 0;
                    }

                    .section-kicker {
                        color: #f2f6ff;
                        font-size: 14;
                        font-weight: bold;
                        text-align: center;
                    }

                    .section-hint {
                        color: #8ea4c0;
                        font-size: 11;
                        text-align: center;
                        padding-top: 4;
                    }

                    .button-selected {
                        font-weight: bold;
                    }

                    .main-area {
                        layout: top;
                        flex-weight: 1;
                        padding-left: 16;
                    }

                    .skin-summary {
                        layout: left;
                        flex-weight: 0;
                        background-color: #1e4871(0.34);
                        border-radius: 10;
                        padding: 12 14 12 14;
                    }

                    .summary-text {
                        layout: top;
                        flex-weight: 1;
                    }

                    .summary-label {
                        color: #f2f6ff;
                        font-size: 14;
                        font-weight: bold;
                    }

                    .summary-value {
                        color: #9cc9ff;
                        font-size: 12;
                        padding-top: 4;
                    }

                    .section {
                        layout: top;
                        flex-weight: 0;
                        background-color: #1b2737(0.88);
                        padding: 14;
                        border-radius: 10;
                    }

                    .parts-section {
                        flex-weight: 1;
                    }

                    .slot-section {
                        flex-weight: 0;
                    }

                    .color-section {
                        flex-weight: 0;
                    }

                    .section-header-compact {
                        layout: top;
                        flex-weight: 0;
                    }

                    .section-title-left {
                        color: #f2f6ff;
                        font-size: 14;
                        font-weight: bold;
                    }

                    .section-description-left {
                        color: #92a6c0;
                        font-size: 12;
                        padding-top: 4;
                    }

                    .slot-tabs-row {
                        layout: left;
                        flex-weight: 0;
                    }

                    .grid-scroll {
                        layout-mode: TopScrolling;
                        flex-weight: 0;
                        anchor-height: 280;
                        background-color: #101a27(0.58);
                        border-radius: 10;
                        padding: 8;
                    }

                    .grid-row {
                        layout: left;
                        flex-weight: 0;
                    }

                    .tile-spacer {
                        flex-weight: 0;
                        anchor-width: 142;
                        anchor-height: 33;
                    }

                    .color-strip-scroll {
                        layout-mode: TopScrolling;
                        flex-weight: 0;
                        anchor-height: 160;
                        padding: 2;
                    }

                    .color-row {
                        layout: left;
                        flex-weight: 0;
                    }

                    .color-tile-spacer {
                        flex-weight: 0;
                        anchor-width: 93;
                        anchor-height: 33;
                    }

                    .empty-state {
                        layout: top;
                        flex-weight: 0;
                        padding: 24;
                        background-color: #101a27(0.52);
                        border-radius: 10;
                    }

                    .empty-state-title {
                        color: #dce8f8;
                        font-size: 14;
                        font-weight: bold;
                        text-align: center;
                    }

                    .empty-state-description {
                        color: #8ea4c0;
                        font-size: 12;
                        text-align: center;
                        padding-top: 6;
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

                    .spacer-xs { flex-weight: 0; anchor-height: 4; }
                    .spacer-sm { flex-weight: 0; anchor-height: 8; }
                    .spacer-md { flex-weight: 0; anchor-height: 16; }
                    .spacer-h-sm { flex-weight: 0; anchor-width: 8; }
                    .spacer-h-md { flex-weight: 0; anchor-width: 16; }
                </style>
                """;
    }
}
