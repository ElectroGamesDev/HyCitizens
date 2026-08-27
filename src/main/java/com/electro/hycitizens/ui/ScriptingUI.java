package com.electro.hycitizens.ui;

import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.managers.ScriptEditorManager;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.component.Ref;
import com.electro.hycitizens.util.HtmlUtils;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.electro.hycitizens.api.scripting.ScriptBlock;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import com.electro.hycitizens.api.scripting.ScriptContext;
import com.electro.hycitizens.api.scripting.ScriptManager;

public class ScriptingUI {
    private final HyCitizensPlugin plugin;

    public ScriptingUI(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void openScriptsGUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, @Nonnull CitizenData citizen) {
        CitizensUI.LeftPanelContext ctx = plugin.getCitizensUI().prepareLeftPanelContext(playerRef, "", citizen.getGroup(), citizen);

        // Prepare list variables or summary for templates
        List<Map<String, Object>> scriptList = new ArrayList<>();
        if (citizen.getScripts() != null) {
            for (int i = 0; i < citizen.getScripts().size(); i++) {
                ScriptBlock sb = citizen.getScripts().get(i);
                Map<String, Object> map = new HashMap<>();
                map.put("index", i);
                map.put("id", sb.getId());
                map.put("name", sb.getName());
                map.put("trigger", sb.getTrigger());
                map.put("priority", sb.getPriority());
                map.put("enabled", sb.isEnabled());
                // Short preview of conditions and actions count
                int condCount = sb.getConditions() != null ? sb.getConditions().size() : 0;
                int actCount = sb.getActions() != null ? sb.getActions().size() : 0;
                map.put("summary", condCount + " conditions, " + actCount + " actions");
                scriptList.add(map);
            }
        }

        TemplateProcessor template = plugin.getCitizensUI().createBaseTemplate()
                .setVariable("citizen", new CitizensUI.SafeCitizen(citizen))
                .setVariable("scripts", scriptList)
                .setVariable("scriptCount", scriptList.size());

        String rightPanelHtml = """
                    <!-- RIGHT PANEL: SCRIPTS CONFIGURATION -->
                    <div class="main-container decorated-container" style="anchor-width: 900; anchor-height: 900;">
                        <!-- Header -->
                        <div class="header container-title">
                            <div class="header-content">
                                <p class="header-title">Scripts Configuration</p>
                            </div>
                        </div>

                        <!-- Body -->
                        <div class="body" data-hyui-scrollbar-style='"Common.ui" "DefaultScrollbarStyle"' style="layout-mode: TopScrolling; flex-weight: 1; padding: 20; gap: 15;">
                            <p class="page-description" style="color: #8b949e; font-size: 14; text-align: center;">Manage scripts for {{$citizen.name}} ({{$citizen.id}})</p>
                            
                            <div class="section">
                                {{@sectionHeader:title=Configured Scripts ({{$scriptCount}}),description=Scripts configured for this NPC}}
                                
                                {{#if scripts}}
                                <div style="layout: top;">
                                    {{#each scripts}}
                                    <div class="command-item">
                                        <div class="command-content">
                                            <p style="font-size: 14; color: #ffffff; font-weight: bold;">{{$name}}</p>
                                            <p class="command-type">ID: {{$id}} | Trigger: {{$trigger}} | Priority: {{$priority}}</p>
                                            <p class="command-type">{{$summary}}</p>
                                        </div>
                                        <div class="command-actions">
                                            <button id="toggle-script-{{$index}}" class="secondary-button small-secondary-button">{{#if enabled}}Disable{{else}}Enable{{/if}}</button>
                                            <div class="spacer-h-sm"></div>
                                            <button id="run-script-{{$index}}" class="secondary-button small-secondary-button">Run/Test</button>
                                            <div class="spacer-h-sm"></div>
                                            <button id="web-edit-btn-{{$index}}" class="primary-button small-secondary-button">Edit in Browser</button>
                                        </div>
                                    </div>
                                    <div class="spacer-sm"></div>
                                    {{/each}}
                                </div>
                                {{else}}
                                <div class="empty-state">
                                    <p class="empty-state-description" style="color: #8b949e; font-size: 14; text-align: center;">No scripts configured for this citizen.</p>
                                    <p class="empty-state-description" style="color: #8b949e; font-size: 12; text-align: center;">Add script blocks to the citizen's JSON configuration under "scripts".</p>
                                </div>
                                {{/if}}
                            </div>

                            <div class="spacer-md"></div>
                            
                            <div class="section">
                                <div style="layout: center; flex-direction: column; align-items: center;">
                                    <p style="font-size: 13; color: #8b949e; text-align: center;">Create a new blank script and open the web editor to configure it.</p>
                                    <div class="spacer-sm"></div>
                                    <div class="form-row" style="horizontal-align: center; gap: 15;">
                                        <button id="new-script-btn" class="primary-button" style="anchor-width: 250; anchor-height: 40;">+ New Script + Edit in Browser</button>
                                        <button id="reload-scripts-btn" class="secondary-button" style="anchor-width: 180; anchor-height: 40;">Reload From Disk</button>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Footer -->
                        <div class="footer" style="layout: center; flex-weight: 0; padding: 26 16 26 16; border-top: 1 solid #1a293c; width: 100%; vertical-align: center;">
                            <button id="back-btn" class="secondary-button" style="anchor-width: 150;">Back</button>
                        </div>
                    </div>
                """;

        String html = template.process(plugin.getCitizensUI().wrapSideBySideHtml(template, ctx, rightPanelHtml));

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(HtmlUtils.sanitizeHtmlForHyUI(html));

        plugin.getCitizensUI().setupMainEventListeners(page, playerRef, store, CitizensUI.Tab.MANAGE, ctx.unifiedList(), ctx.searchQuery(), ctx.normalizedViewingGroup(), citizen);

        page.addEventListener("back-btn", CustomUIEventBindingType.Activating, event -> {
            plugin.getCitizensUI().openEditCitizenGUI(playerRef, store, citizen);
        });

        page.addEventListener("reload-scripts-btn", CustomUIEventBindingType.Activating, event -> {
            // Re-load citizen from disk config to get script changes
            plugin.getCitizensManager().reload();
            CitizenData reloaded = plugin.getCitizensManager().getCitizen(citizen.getId());
            playerRef.sendMessage(Message.raw("Scripts reloaded from disk config successfully!").color(Color.GREEN));
            openScriptsGUI(playerRef, store, reloaded != null ? reloaded : citizen);
        });

        if (citizen.getScripts() != null) {
            for (int i = 0; i < citizen.getScripts().size(); i++) {
                final int index = i;
                ScriptBlock sb = citizen.getScripts().get(i);

                page.addEventListener("toggle-script-" + index, CustomUIEventBindingType.Activating, event -> {
                    sb.setEnabled(!sb.isEnabled());
                    plugin.getCitizensManager().saveCitizen(citizen);
                    playerRef.sendMessage(Message.raw("Script '" + sb.getName() + "' " + (sb.isEnabled() ? "enabled" : "disabled") + "!").color(Color.GREEN));
                    openScriptsGUI(playerRef, store, citizen);
                });

                page.addEventListener("web-edit-btn-" + index, CustomUIEventBindingType.Activating, (event, uiCtx) -> {
                    uiCtx.getPage().ifPresent(p -> p.close());
                    ScriptEditorManager.get().startEditSession(playerRef, citizen.getId());
                });

                page.addEventListener("run-script-" + index, CustomUIEventBindingType.Activating, event -> {
                    Ref<EntityStore> npcRef = citizen.getNpcRef();
                    if (npcRef == null || !npcRef.isValid()) {
                        playerRef.sendMessage(Message.raw("NPC is not currently spawned in the world!").color(Color.RED));
                        return;
                    }
                    playerRef.sendMessage(Message.raw("Triggering script '" + sb.getName() + "' for test...").color(Color.YELLOW));
                    ScriptContext scriptContext = new ScriptContext(
                            citizen,
                            playerRef,
                            Universe.get().getWorld(citizen.getWorldUUID()),
                            store,
                            "MANUAL_TEST",
                            null
                    );
                    ScriptBlock compiled = ScriptManager.get().compileScript(sb);
                    ScriptManager.get().executeScript(compiled, scriptContext);
                    playerRef.sendMessage(Message.raw("Script triggered successfully!").color(Color.GREEN));
                });
            }
        }

        page.addEventListener("new-script-btn", CustomUIEventBindingType.Activating, (event, uiCtx) -> {
            String newId = "script_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String newName = newId;

            ScriptBlock newScript = new ScriptBlock();
            newScript.setId(newId);
            newScript.setName(newName);
            newScript.setEnabled(true);
            newScript.setPriority(0);

            List<ScriptBlock> scripts = citizen.getScripts();
            if (scripts == null) scripts = new ArrayList<>();
            scripts.add(newScript);
            citizen.setScripts(scripts);
            plugin.getCitizensManager().saveCitizen(citizen);

            uiCtx.getPage().ifPresent(p -> p.close());
            ScriptEditorManager.get().startEditSession(playerRef, citizen.getId());
        });

        page.open(store);
    }

    public void openDialogueUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, String title, String body, List<Map<String, Object>> responses, Consumer<String> callback) {
        playerRef.sendMessage(Message.raw("Dialogue UI opened: " + title).color(Color.YELLOW));
        // TODO: Implement actual Dialogue UI
        if (responses != null && !responses.isEmpty()) {
            // Auto-select first response for testing purposes until UI is built
            String id = (String) responses.get(0).get("id");
            callback.accept(id != null ? id : "0");
        } else {
            callback.accept("");
        }
    }

    public void openMerchantUI(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store, String title, List<Map<String, Object>> trades) {
        playerRef.sendMessage(Message.raw("Merchant UI opened: " + title).color(Color.YELLOW));
        // TODO: Implement actual Merchant UI
    }

    public void closePlayerUI(@Nonnull PlayerRef playerRef) {
        // TODO: Implement actual close UI
    }
}
