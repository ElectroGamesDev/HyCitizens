package com.electro.hycitizens;

import com.electro.hycitizens.actions.NpcInteractAction;
import com.electro.hycitizens.commands.CitizensCommand;
import com.electro.hycitizens.listeners.*;
import com.electro.hycitizens.managers.CitizensManager;
import com.electro.hycitizens.systems.NpcKnockbackRemoverSystem;
import com.electro.hycitizens.ui.CitizensUI;
import com.electro.hycitizens.util.ConfigManager;
import com.electro.hycitizens.util.UpdateChecker;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackSystems;
import com.hypixel.hytale.server.core.event.events.player.*;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;

import javax.annotation.Nonnull;
import java.nio.file.Paths;

public class HyCitizensPlugin extends JavaPlugin {
    private static HyCitizensPlugin instance;
    private ConfigManager configManager;
    private CitizensManager citizensManager;
    private CitizensUI citizensUI;

    // Listeners
    private ChunkPreLoadListener chunkPreLoadListener;
    private PlayerConnectionListener connectionListener;

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
        this.chunkPreLoadListener = new ChunkPreLoadListener(this);
        this.connectionListener = new PlayerConnectionListener(this);

        getEntityStoreRegistry().registerSystem(new NpcKnockbackRemoverSystem());
        this.getCodecRegistry(Interaction.CODEC).register("CitizenInteraction", NpcInteractAction.class, NpcInteractAction.CODEC);
        // Register event listeners
        registerEventListeners();
    }




    @Override
    protected void start() {
        UpdateChecker.checkAsync();
    }

    @Override
    protected void shutdown() {
        if (citizensManager != null) {
            citizensManager.shutdown();
        }
    }

    private void registerEventListeners() {
        getEventRegistry().register(PlayerDisconnectEvent.class, connectionListener::onPlayerDisconnect);
        getEventRegistry().register(PlayerConnectEvent.class, connectionListener::onPlayerConnect);

        this.getEntityStoreRegistry().registerSystem(new EntityDamageListener(this));
        //getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, addToWorldListener::onAddPlayerToWorld);
        getEventRegistry().registerGlobal(EventPriority.LAST, ChunkPreLoadProcessEvent.class, chunkPreLoadListener::onChunkPreload);
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
