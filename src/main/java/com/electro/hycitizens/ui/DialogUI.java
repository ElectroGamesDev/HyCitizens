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
import com.electro.hycitizens.managers.DialogueManager;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
        CitizenData citizen = null;
        if (session.getNpcId() != null) {
            citizen = plugin.getCitizensManager().getCitizen(session.getNpcId());
        }

        final CitizenData finalCitizen = citizen;
        final long renderedRevision = session.getRenderRevision();
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world != null) {
            world.execute(() -> openUIInternal(player, session, node, responses, finalCitizen, renderedRevision));
        } else {
            openUIInternal(player, session, node, responses, finalCitizen, renderedRevision);
        }
    }

    private void openUIInternal(@Nonnull PlayerRef player, @Nonnull DialogueSession session, @Nonnull IDialogueNode node, @Nonnull List<DialogueResponse> responses, @Nullable CitizenData citizen, long renderedRevision) {
        DialogueSession activeSession = DialogueManager.get().getDialogueSession(player);
        if (activeSession != session
                || session.getRenderRevision() != renderedRevision
                || !session.getCurrentNodeId().equals(node.getId())) {
            return;
        }

        if (citizen == null && session.getNpcId() != null && !session.getNpcId().isEmpty()) {
            citizen = plugin.getCitizensManager().getCitizen(session.getNpcId());
        }
        if (citizen == null && session.getScriptContext() != null && session.getScriptContext().getCitizen() != null) {
            citizen = session.getScriptContext().getCitizen();
        }

        // 1. Resolve speaker name
        String nodeSpeaker = node.getSpeaker();
        String resolvedSpeaker = null;
        if (nodeSpeaker != null && !nodeSpeaker.isEmpty()) {
            resolvedSpeaker = ScriptExpressionEvaluator.resolve(nodeSpeaker, session.getScriptContext());
        }
        if (resolvedSpeaker == null || resolvedSpeaker.trim().isEmpty() || resolvedSpeaker.equalsIgnoreCase("NPC")) {
            if (citizen != null && citizen.getName() != null && !citizen.getName().trim().isEmpty()) {
                resolvedSpeaker = citizen.getName();
            } else if (session.getDialogue() != null && session.getDialogue().getTitle() != null && !session.getDialogue().getTitle().trim().isEmpty()) {
                resolvedSpeaker = session.getDialogue().getTitle();
            } else {
                resolvedSpeaker = (nodeSpeaker != null && !nodeSpeaker.trim().isEmpty()) ? nodeSpeaker : "Citizen";
            }
        }

        String resolvedText = ScriptExpressionEvaluator.resolve(node.getText(), session.getScriptContext());

        // 2. Build response list
        List<Map<String, Object>> responseList = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            DialogueResponse resp = responses.get(i);
            Map<String, Object> map = new HashMap<>();
            map.put("index", i);
            map.put("id", resp.getId());
            map.put("text", ScriptExpressionEvaluator.resolve(resp.getText(), session.getScriptContext()));
            map.put("hasSpacer", i > 0);
            responseList.add(map);
        }

        // If no choices, add a default Continue/Finish button
        boolean hasChoices = !responseList.isEmpty();
        if (!hasChoices) {
            String nextNodeId = node.getNextNodeId();
            boolean isContinue = nextNodeId != null && !nextNodeId.trim().isEmpty()
                    && !nextNodeId.equalsIgnoreCase("end")
                    && !nextNodeId.equalsIgnoreCase("close")
                    && !nextNodeId.equalsIgnoreCase("finish");
            Map<String, Object> map = new HashMap<>();
            map.put("index", 0);
            map.put("id", "continue_next");
            map.put("hasSpacer", false);
            map.put("text", isContinue ? "Continue" : "Finish");
            responseList.add(map);
        }

        int numResponses = responseList.size();
        int buttonWidth;
        if (numResponses == 1) {
            buttonWidth = 240;
        } else if (numResponses == 2) {
            buttonWidth = 220;
        } else if (numResponses == 3) {
            buttonWidth = 200;
        } else if (numResponses == 4) {
            buttonWidth = 170;
        } else {
            buttonWidth = Math.max(110, (780 - (numResponses - 1) * 8) / numResponses);
        }

        int cardHeight = (resolvedText != null && resolvedText.length() > 200) ? 300 : 250;

        TemplateProcessor template = plugin.getCitizensUI().createBaseTemplate()
                .setVariable("speaker", resolvedSpeaker)
                .setVariable("bodyText", resolvedText != null ? resolvedText : "")
                .setVariable("responses", responseList)
                .setVariable("buttonWidth", buttonWidth)
                .setVariable("cardHeight", cardHeight);

        String html = template.process(plugin.getCitizensUI().getSharedStyles() + """
                <div style="layout: bottom; horizontal-align: center; vertical-align: bottom; width: 100%; height: 100%; padding-bottom: 30;">
                    <div class="main-container decorated-container" style="anchor-width: 860; anchor-height: {{$cardHeight}};">
                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title" style="color: #ffd075; font-size: 24; font-weight: bold; text-align: center;">{{$speaker}}</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling; flex-weight: 1; padding: 12 24 12 24;">
                            <p style="font-size: 16; color: #eaf2ff; text-align: center; width: 100%;">{{$bodyText}}</p>
                        </div>

                        <!-- Footer Buttons -->
                        <div class="footer" style="layout: center; flex-weight: 0; padding: 14 16 16 16; border-top: 1 solid #1a293c; width: 100%; vertical-align: center;">
                            <div class="form-row" style="layout: left; horizontal-align: center; vertical-align: center; width: 100%;">
                                {{#each responses}}
                                {{#if hasSpacer}}
                                <div class="spacer-h-sm"></div>
                                {{/if}}
                                <button id="dialogue-choice-{{$index}}" class="secondary-button" style="anchor-width: {{$buttonWidth}}; anchor-height: 40;">{{$text}}</button>
                                {{/each}}
                            </div>
                        </div>
                    </div>
                </div>
                """);

        Store<EntityStore> store = session.getScriptContext().getStore();
        if (store == null && player.getReference() != null && player.getReference().isValid()) {
            store = player.getReference().getStore();
        }
        PageBuilder page = PageBuilder.pageForPlayer(player)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(HtmlUtils.sanitizeHtmlForHyUI(html));

        // Bind button events - PageManager replaces this page when the next node opens.
        if (hasChoices) {
            for (int i = 0; i < responses.size(); i++) {
                final DialogueResponse resp = responses.get(i);
                final int index = i;
                page.addEventListener("dialogue-choice-" + index, CustomUIEventBindingType.Activating, (event, ctx) -> {
                    DialogueManager.get().selectResponse(session, resp.getId(), renderedRevision);
                });
            }
        } else {
            page.addEventListener("dialogue-choice-0", CustomUIEventBindingType.Activating, (event, ctx) -> {
                DialogueManager.get().selectResponse(session, "continue_next", renderedRevision);
            });
        }

        page.onDismiss((uipage, dismissed) -> {
            HyUIPage currentActive = activePages.get(player.getUuid());
            if (currentActive == uipage) {
                activePages.remove(player.getUuid(), uipage);
                boolean internal = internallyClosing.remove(uipage);
                if (!internal) {
                    // Player dismissed (e.g. ESC) without going through a button
                    DialogueSession active = DialogueManager.get().getDialogueSession(player);
                    if (active != null && active.getSessionId().equals(session.getSessionId())) {
                        DialogueManager.get().endDialogueSession(player, DialogCloseReason.PLAYER_DISMISS.name());
                    }
                }
            } else {
                internallyClosing.remove(uipage);
            }
        });

        // PageManager dismisses the old custom page while opening the new one.
        HyUIPage existingPage = activePages.get(player.getUuid());
        if (existingPage != null) {
            internallyClosing.add(existingPage);
        }

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

    private void closeInternally(HyUIPage page) {
        if (page == null) return;
        internallyClosing.add(page);
        try {
            page.close();
        } catch (Exception e) {
            internallyClosing.remove(page);
            getLogger().atWarning().log("[HyCitizens] Error closing dialogue page internally: " + e.getMessage());
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
            map.put("hasSpacer", i > 0);
            responseList.add(map);
        }

        boolean hasChoices = !responseList.isEmpty();
        if (!hasChoices) {
            Map<String, Object> map = new HashMap<>();
            map.put("index", 0);
            map.put("id", "continue_next");
            map.put("hasSpacer", false);
            map.put("text", "Close");
            responseList.add(map);
        }

        String resolvedSpeaker = (title != null && !title.trim().isEmpty()) ? title : "Citizen";
        String resolvedBody = (body != null) ? body : "";

        int numResponses = responseList.size();
        int buttonWidth;
        if (numResponses == 1) {
            buttonWidth = 240;
        } else if (numResponses == 2) {
            buttonWidth = 220;
        } else if (numResponses == 3) {
            buttonWidth = 200;
        } else if (numResponses == 4) {
            buttonWidth = 170;
        } else {
            buttonWidth = Math.max(110, (780 - (numResponses - 1) * 8) / numResponses);
        }

        int cardHeight = (resolvedBody.length() > 200) ? 300 : 250;

        TemplateProcessor template = plugin.getCitizensUI().createBaseTemplate()
                .setVariable("speaker", resolvedSpeaker)
                .setVariable("bodyText", resolvedBody)
                .setVariable("responses", responseList)
                .setVariable("buttonWidth", buttonWidth)
                .setVariable("cardHeight", cardHeight);

        String html = template.process(plugin.getCitizensUI().getSharedStyles() + """
                <div style="layout: bottom; horizontal-align: center; vertical-align: bottom; width: 100%; height: 100%; padding-bottom: 30;">
                    <div class="main-container decorated-container" style="anchor-width: 860; anchor-height: {{$cardHeight}};">
                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title" style="color: #ffd075; font-size: 24; font-weight: bold; text-align: center;">{{$speaker}}</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling; flex-weight: 1; padding: 12 24 12 24;">
                            <p style="font-size: 16; color: #eaf2ff; text-align: center; width: 100%;">{{$bodyText}}</p>
                        </div>

                        <!-- Footer Buttons -->
                        <div class="footer" style="layout: center; flex-weight: 0; padding: 14 16 16 16; border-top: 1 solid #1a293c; width: 100%; vertical-align: center;">
                            <div class="form-row" style="layout: left; horizontal-align: center; vertical-align: center; width: 100%;">
                                {{#each responses}}
                                {{#if hasSpacer}}
                                <div class="spacer-h-sm"></div>
                                {{/if}}
                                <button id="dialogue-choice-{{$index}}" class="secondary-button" style="anchor-width: {{$buttonWidth}}; anchor-height: 40;">{{$text}}</button>
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
                final Map<String, Object> respMap = responses.get(i);
                final String respId = (String) respMap.get("id");
                final int index = i;
                page.addEventListener("dialogue-choice-" + index, CustomUIEventBindingType.Activating, (event, ctx) -> {
            callback.accept(respId);
                });
            }
        } else {
            page.addEventListener("dialogue-choice-0", CustomUIEventBindingType.Activating, (event, ctx) -> {
                callback.accept("");
            });
        }

        page.onDismiss((uipage, dismissed) -> {
            HyUIPage currentActive = activePages.get(player.getUuid());
            if (currentActive == uipage) {
                activePages.remove(player.getUuid(), uipage);
                boolean internal = internallyClosing.remove(uipage);
                if (!internal) {
                    DialogueManager.get().endDialogueSession(player, DialogCloseReason.PLAYER_DISMISS.name());
                }
            } else {
                internallyClosing.remove(uipage);
            }
        });

        // PageManager dismisses the old custom page while opening the new one.
        HyUIPage existingPage = activePages.get(player.getUuid());
        if (existingPage != null) {
            internallyClosing.add(existingPage);
        }

        HyUIPage uiPage = page.open(store);
        activePages.put(player.getUuid(), uiPage);
    }
}
