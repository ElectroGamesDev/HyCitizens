package com.electro.hycitizens.commands;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.api.scripting.ScriptManager;
import com.electro.hycitizens.managers.ScriptEditorManager;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.ui.CitizensUI;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class CitizensCommand extends AbstractPlayerCommand {
    private final HyCitizensPlugin plugin;

    public CitizensCommand(@Nonnull HyCitizensPlugin plugin) {
        super("citizens", "Citizens Commands");
        this.requirePermission("hycitizens.admin");
        this.addAliases("citizen", "hycitizens", "hycitizen", "hc");
        this.setAllowsExtraArguments(true);
        this.plugin = plugin;
        this.addSubCommand(new RespawnAllCommand(plugin));
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String[] args = commandContext.getInputString().split(" ");

        if (args.length < 2) {
            plugin.getCitizensUI().openCitizensGUI(playerRef, store, CitizensUI.Tab.MANAGE);
            return;
        }

        String verb = args[1].toLowerCase();

        switch (verb) {
            case "script" -> {
                if (args.length < 3) {
                    playerRef.sendMessage(Message.raw("[HyCitizens] Usage: /hc script edit <citizen_id>  |  /hc script save <token>").color(Color.CYAN));
                    return;
                }
                String action = args[2].toLowerCase();
                if (args.length < 4) {
                    if (action.equals("edit")) {
                        playerRef.sendMessage(Message.raw("[HyCitizens] Usage: /hc script edit <citizen_id>").color(Color.RED));
                    } else if (action.equals("save")) {
                        playerRef.sendMessage(Message.raw("[HyCitizens] Usage: /hc script save <token>").color(Color.RED));
                    } else {
                        playerRef.sendMessage(Message.raw("[HyCitizens] Usage: /hc script edit <citizen_id>  |  /hc script save <token>").color(Color.CYAN));
                    }
                    return;
                }
                switch (action) {
                    case "edit" -> ScriptEditorManager.get().startEditSession(playerRef, args[3]);
                    case "save" -> ScriptEditorManager.get().applyEditSession(playerRef, args[3], store);
                    default -> playerRef.sendMessage(Message.raw("[HyCitizens] Usage: /hc script edit <citizen_id>  |  /hc script save <token>").color(Color.CYAN));
                }
            }
            case "scriptsave", "script-save" -> {
                // /hc scriptsave <token>
                if (args.length < 3) {
                    playerRef.sendMessage(Message.raw("[HyCitizens] Usage: /hc scriptsave <token>").color(Color.RED));
                    return;
                }
                ScriptEditorManager.get().applyEditSession(playerRef, args[2], store);
            }
            case "trigger", "runscript" -> {
                // /hc trigger <citizen_id> <command_name>
                if (args.length < 4) {
                    playerRef.sendMessage(Message.raw("[HyCitizens] Usage: /hc trigger <citizen_id> <command_name>").color(Color.RED));
                    return;
                }
                String citizenId = args[2];
                String commandName = args[3];

                CitizenData citizen = plugin.getCitizensManager().getAllCitizens().stream()
                        .filter(c -> c.getId().equalsIgnoreCase(citizenId))
                        .findFirst()
                        .orElse(null);

                if (citizen == null) {
                    playerRef.sendMessage(Message.raw("[HyCitizens] Citizen '" + citizenId + "' not found.").color(Color.RED));
                    return;
                }

                Map<String, Object> triggerArgs = new HashMap<>();
                triggerArgs.put("command", commandName);
                ScriptManager.get().fireTrigger(citizen, "ON_COMMAND", triggerArgs, playerRef, store);
                playerRef.sendMessage(Message.raw("[HyCitizens] Triggered script for citizen '" + citizenId + "'.").color(Color.GREEN));
            }
            default -> plugin.getCitizensUI().openCitizensGUI(playerRef, store, CitizensUI.Tab.MANAGE);
        }
    }

    private static class RespawnAllCommand extends AbstractPlayerCommand {
        private final HyCitizensPlugin plugin;

        private RespawnAllCommand(@Nonnull HyCitizensPlugin plugin) {
            super("respawnall", "Respawn all citizens");
            this.requirePermission("hycitizens.admin");
            this.addAliases("respawn-all", "respawn_all");
            this.plugin = plugin;
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            int count = plugin.getCitizensManager().respawnAllCitizens(true);
            playerRef.sendMessage(Message.raw("Respawned " + count + " citizens.").color(Color.GREEN));
        }
    }
}
