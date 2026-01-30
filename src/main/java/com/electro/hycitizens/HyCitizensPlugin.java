package com.electro.hycitizens;

import com.electro.hycitizens.commands.CitizensCommand;
import com.electro.hycitizens.listeners.*;
import com.electro.hycitizens.managers.CitizensManager;
import com.electro.hycitizens.ui.CitizensUI;
import com.electro.hycitizens.util.ConfigManager;
import com.hypixel.hytale.server.core.event.events.player.*;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.nio.file.Paths;

public class HyCitizensPlugin extends JavaPlugin {
    private static HyCitizensPlugin instance;
    private ConfigManager configManager;
    private CitizensManager citizensManager;
    private CitizensUI citizensUI;

    // Listeners
    private PlayerAddToWorldListener addToWorldListener;

    public HyCitizensPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        // Initialize config manager
        this.configManager = new ConfigManager(Paths.get("mods", "HyCitizensData"));
        this.citizensManager = new CitizensManager(this);
        this.citizensUI = new CitizensUI(this);

        // Register commands
        getCommandRegistry().registerCommand(new CitizensCommand(this));

        // Initialize listeners
        this.addToWorldListener = new PlayerAddToWorldListener(this);

        // Register event listeners
        registerEventListeners();
    }

    @Override
    protected void start() {

    }

    @Override
    protected void shutdown() {
        if (citizensManager != null) {
            citizensManager.shutdown();
        }
    }

    private void registerEventListeners() {
        this.getEntityStoreRegistry().registerSystem(new EntityDamageListener(this));
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, addToWorldListener::onAddPlayerToWorld);
    }

    public static HyCitizensPlugin get() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CitizensManager getCitizensManager() {
        return citizensManager;
    }

    public CitizensUI getCitizensUI() {
        return citizensUI;
    }
}
