package com.electro.hycitizens.ui;

import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.api.dialogue.DialogueResponse;
import com.electro.hycitizens.api.dialogue.DialogueSession;
import com.electro.hycitizens.api.dialogue.DialogCloseReason;
import com.electro.hycitizens.api.dialogue.IDialogueNode;
import com.electro.hycitizens.api.scripting.ScriptExpressionEvaluator;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.util.HtmlUtils;
import com.electro.hycitizens.util.SkinUtilities;
import com.electro.hycitizens.managers.DialogueManager;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class DialogUI {

    private static DialogUI instance;

    private final HyCitizensPlugin plugin;
    private final Map<UUID, HyUIPage> activePages = new ConcurrentHashMap<>();
    private final Set<HyUIPage> internallyClosing = ConcurrentHashMap.newKeySet();

    public static DialogUI get() {
        if (instance == null) {
            instance = new DialogUI(HyCitizensPlugin.get());
        }
        return instance;
    }

    private DialogUI(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void openDialogueUI(@Nonnull PlayerRef player, @Nonnull DialogueSession session, @Nonnull IDialogueNode node, @Nonnull List<DialogueResponse> responses) {
        boolean showNPCHeadshot = plugin.getConfigManager().getBoolean("dialogue.showNPCHeadshot", true);
        CitizenData citizen = null;
        if (session.getNpcId() != null) {
            citizen = plugin.getCitizensManager().getCitizen(session.getNpcId());
        }

        CompletableFuture<String> headshotFuture;
        if (showNPCHeadshot && citizen != null && citizen.getCachedSkin() != null) {
            headshotFuture = SkinUtilities.cacheHeadshotAndGetUrl(session.getNpcId(), citizen.getCachedSkin());
        } else {
            headshotFuture = CompletableFuture.completedFuture("");
        }

        // Run the rendering pipeline when skin is cached (or timed out/errored)
        final CitizenData finalCitizen = citizen;
        headshotFuture.orTimeout(1000, TimeUnit.MILLISECONDS)
                .exceptionally(err -> {
                    getLogger().atWarning().log("[HyCitizens] Headshot fetching timed out, proceeding with fallback display: " + err.getMessage());
                    return "";
                })
                .thenAccept(url -> {
                    openUIInternal(player, session, node, responses, url, finalCitizen);
                });
    }

    private void openUIInternal(@Nonnull PlayerRef player, @Nonnull DialogueSession session, @Nonnull IDialogueNode node, @Nonnull List<DialogueResponse> responses, @Nonnull String headshotUrl, @Nullable CitizenData citizen) {
        // 1. Interpolate string variables inside Node Speaker name and Body Text
        String resolvedSpeaker = ScriptExpressionEvaluator.resolve(node.getSpeaker(), session.getScriptContext());
        if (resolvedSpeaker == null || resolvedSpeaker.isEmpty()) {
            resolvedSpeaker = (citizen != null) ? citizen.getName() : "NPC";
        }
        
        String resolvedText = ScriptExpressionEvaluator.resolve(node.getText(), session.getScriptContext());

        // 2. Prepare dynamic lists for template rendering
        List<Map<String, Object>> responseList = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            DialogueResponse resp = responses.get(i);
            Map<String, Object> map = new HashMap<>();
            map.put("index", i);
            map.put("id", resp.getId());
            map.put("text", ScriptExpressionEvaluator.resolve(resp.getText(), session.getScriptContext()));
            responseList.add(map);
        }

        // If there are no choices, add a generic "Continue" or "Close" button to let the conversation proceed
        boolean hasChoices = !responseList.isEmpty();
        if (!hasChoices) {
            Map<String, Object> map = new HashMap<>();
            map.put("index", 0);
            map.put("id", "continue_next");
            
            String nextNodeId = node.getNextNodeId();
            if (nextNodeId != null && !nextNodeId.isEmpty()) {
                map.put("text", "Continue");
            } else {
                map.put("text", "Finish");
            }
            responseList.add(map);
        }

        TemplateProcessor template = plugin.getCitizensUI().createBaseTemplate()
                .setVariable("speaker", resolvedSpeaker)
                .setVariable("bodyText", resolvedText)
                .setVariable("responses", responseList)
                .setVariable("hasHeadshot", headshotUrl != null && !headshotUrl.isEmpty())
                .setVariable("headshotUrl", headshotUrl);

        // State-of-the-art premium Glassmorphism template
        String html = template.process(plugin.getCitizensUI().getSharedStyles() + """
                <style>
                    .dialogue-overlay {
                        layout: center;
                        flex-weight: 1;
                        padding: 20;
                    }

                    .dialogue-card {
                        background-color: #161a26(0.94);
                        border-radius: 12;
                        anchor-width: 820;
                        anchor-height: 320;
                        layout: left;
                        padding: 24;
                    }

                    .portrait-frame {
                        anchor-width: 140;
                        anchor-height: 272;
                        background-color: #ffffff(0.02);
                        border-radius: 10;
                        layout: center;
                    }

                    .portrait-img {
                        anchor-width: 120;
                        anchor-height: 120;
                        border-radius: 8;
                    }

                    .dialogue-main-panel {
                        layout: top;
                        flex-weight: 1;
                        padding-left: 24;
                    }

                    .speaker-header-label {
                        font-size: 20;
                        font-weight: bold;
                        color: #ffd075;
                        padding-bottom: 12;
                    }

                    .dialogue-text-body {
                        font-size: 15;
                        color: #e3e6e8;
                        anchor-height: 100;
                        padding-bottom: 12;
                    }

                    .dialogue-choices-wrapper {
                        layout: TopScrolling;
                        flex-weight: 1;
                    }

                    .dialogue-choice-btn {
                        background-color: #ffffff(0.03);
                        border-radius: 8;
                        padding: 10 16;
                        color: #c9d1d9;
                        font-size: 14;
                        layout: left;
                    }

                    .dialogue-choice-btn:hover {
                        background-color: #ffffff(0.12);
                        color: #ffffff;
                    }
                </style>

                <div class="dialogue-overlay">
                    <div class="dialogue-card">
                        {{#if hasHeadshot}}
                        <div class="portrait-frame">
                            <img src="{{$headshotUrl}}" class="portrait-img dynamic-image" />
                        </div>
                        {{/if}}

                        <div class="dialogue-main-panel">
                            <div class="speaker-header-label">{{$speaker}}</div>
                            <p class="dialogue-text-body">{{$bodyText}}</p>
                            
                            <div class="dialogue-choices-wrapper" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"'>
                                {{#each responses}}
                                <button id="dialogue-choice-{{$index}}" class="dialogue-choice-btn">{{$text}}</button>
                                {{/each}}
                            </div>
                        </div>
                    </div>
                </div>
                """);

        Store<EntityStore> store = session.getScriptContext().getStore();
        final long renderedRevision = session.getRenderRevision();
        PageBuilder page = PageBuilder.pageForPlayer(player)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(HtmlUtils.sanitizeHtmlForHyUI(html));

        // Bind responses / button click events
        if (hasChoices) {
            for (int i = 0; i < responses.size(); i++) {
                final int index = i;
                final DialogueResponse resp = responses.get(i);
                page.addEventListener("dialogue-choice-" + index, CustomUIEventBindingType.Activating, (event, ctx) -> {
                    ctx.getPage().ifPresent(this::closeInternally);
                    DialogueManager.get().selectResponse(session, resp.getId(), renderedRevision);
                });
            }
        } else {
            // Bind default continue/finish button
            page.addEventListener("dialogue-choice-0", CustomUIEventBindingType.Activating, (event, ctx) -> {
                ctx.getPage().ifPresent(this::closeInternally);
                // Calling selectResponse with non-existent choice triggers default nextNodeId/finish sequence
                DialogueManager.get().selectResponse(session, "continue_next", renderedRevision);
            });
        }

        // Close any active page first to avoid overlap
        HyUIPage existingPage = activePages.get(player.getUuid());
        if (existingPage != null) {
            try {
                closeInternally(existingPage);
            } catch (Exception e) {
                getLogger().atWarning().log("[HyCitizens] Error closing existing dialogue page: " + e.getMessage());
            }
        }

        page.onDismiss((uipage, dismissed) -> {
            activePages.remove(player.getUuid(), uipage);
            boolean internal = internallyClosing.remove(uipage);
            DialogueSession active = DialogueManager.get().getDialogueSession(player);
            if (dismissed && !internal && active != null
                    && active.getSessionId().equals(session.getSessionId())
                    && active.getRenderRevision() == renderedRevision) {
                DialogueManager.get().endDialogueSession(player, DialogCloseReason.PLAYER_DISMISS.name());
            }
        });

        HyUIPage uiPage = page.open(store);
        activePages.put(player.getUuid(), uiPage);
    }

    public void closePlayerUI(@Nonnull PlayerRef player) {
        HyUIPage page = activePages.remove(player.getUuid());
        if (page != null) {
            try {
                closeInternally(page);
            } catch (Exception e) {
                getLogger().atWarning().log("[HyCitizens] Error closing player UI: " + e.getMessage());
            }
        }
    }

    public void openDialogueUI(@Nonnull PlayerRef player, @Nonnull Store<EntityStore> store, @Nullable String title, @Nullable String body, @Nonnull List<Map<String, Object>> responses, @Nonnull Consumer<String> callback) {
        List<Map<String, Object>> responseList = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            Map<String, Object> respMap = responses.get(i);
            Map<String, Object> map = new HashMap<>();
            map.put("index", i);
            map.put("id", respMap.get("id"));
            map.put("text", respMap.get("text"));
            responseList.add(map);
        }

        boolean hasChoices = !responseList.isEmpty();
        if (!hasChoices) {
            Map<String, Object> map = new HashMap<>();
            map.put("index", 0);
            map.put("id", "continue_next");
            map.put("text", "Close");
            responseList.add(map);
        }

        String resolvedSpeaker = title != null ? title : "NPC";
        String resolvedBody = body != null ? body : "";

        TemplateProcessor template = plugin.getCitizensUI().createBaseTemplate()
                .setVariable("speaker", resolvedSpeaker)
                .setVariable("bodyText", resolvedBody)
                .setVariable("responses", responseList)
                .setVariable("hasHeadshot", false)
                .setVariable("headshotUrl", "");

        String html = template.process(plugin.getCitizensUI().getSharedStyles() + """
                <style>
                    .dialogue-overlay {
                        layout: center;
                        flex-weight: 1;
                        padding: 20;
                    }

                    .dialogue-card {
                        background-color: #161a26(0.94);
                        border-radius: 12;
                        anchor-width: 820;
                        anchor-height: 320;
                        layout: top;
                        padding: 24;
                    }

                    .dialogue-main-panel {
                        layout: top;
                        flex-weight: 1;
                    }

                    .speaker-header-label {
                        font-size: 20;
                        font-weight: bold;
                        color: #ffd075;
                        padding-bottom: 12;
                    }

                    .dialogue-text-body {
                        font-size: 15;
                        color: #e3e6e8;
                        anchor-height: 100;
                        padding-bottom: 12;
                    }

                    .dialogue-choices-wrapper {
                        layout: TopScrolling;
                        flex-weight: 1;
                    }

                    .dialogue-choice-btn {
                        background-color: #ffffff(0.03);
                        border-radius: 8;
                        padding: 10 16;
                        color: #c9d1d9;
                        font-size: 14;
                        layout: left;
                    }

                    .dialogue-choice-btn:hover {
                        background-color: #ffffff(0.12);
                        color: #ffffff;
                    }
                </style>

                <div class="dialogue-overlay">
                    <div class="dialogue-card">
                        <div class="dialogue-main-panel">
                            <div class="speaker-header-label">{{$speaker}}</div>
                            <p class="dialogue-text-body">{{$bodyText}}</p>
                            
                            <div class="dialogue-choices-wrapper" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"'>
                                {{#each responses}}
                                <button id="dialogue-choice-{{$index}}" class="dialogue-choice-btn">{{$text}}</button>
                                {{/each}}
                            </div>
                        </div>
                    </div>
                </div>
                """);

        PageBuilder page = PageBuilder.pageForPlayer(player)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(HtmlUtils.sanitizeHtmlForHyUI(html));

        if (hasChoices) {
            for (int i = 0; i < responses.size(); i++) {
                final int index = i;
                final Map<String, Object> respMap = responses.get(i);
                final String respId = (String) respMap.get("id");
                page.addEventListener("dialogue-choice-" + index, CustomUIEventBindingType.Activating, (event, ctx) -> {
                    ctx.getPage().ifPresent(this::closeInternally);
                    callback.accept(respId);
                });
            }
        } else {
            page.addEventListener("dialogue-choice-0", CustomUIEventBindingType.Activating, (event, ctx) -> {
                ctx.getPage().ifPresent(this::closeInternally);
                callback.accept("");
            });
        }

        // Close any active page first to avoid overlap
        HyUIPage existingPage = activePages.get(player.getUuid());
        if (existingPage != null) {
            try {
                closeInternally(existingPage);
            } catch (Exception e) {
                getLogger().atWarning().log("[HyCitizens] Error closing existing dialogue page: " + e.getMessage());
            }
        }

        page.onDismiss((uipage, dismissed) -> {
            activePages.remove(player.getUuid(), uipage);
            boolean internal = internallyClosing.remove(uipage);
            if (dismissed && !internal) {
                DialogueManager.get().endDialogueSession(player);
            }
        });

        HyUIPage uiPage = page.open(store);
        activePages.put(player.getUuid(), uiPage);
    }

    private void closeInternally(HyUIPage page) {
        internallyClosing.add(page);
        try {
            page.close();
        } catch (RuntimeException error) {
            internallyClosing.remove(page);
            throw error;
        }
    }
}
