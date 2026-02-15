package com.electro.hycitizens.commands;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.ui.CitizensUI;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class CitizensCommand extends AbstractPlayerCommand{
    private final HyCitizensPlugin plugin;

    public CitizensCommand(@Nonnull HyCitizensPlugin plugin) {
        super("citizens", "Citizens Commands");
        this.requirePermission("citizens.admin");
        this.plugin = plugin;
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        plugin.getCitizensUI().openCitizensGUI(playerRef, store, CitizensUI.Tab.MANAGE);
    }

}
