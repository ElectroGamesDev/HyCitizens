package com.electro.hycitizens;

import com.electro.hycitizens.actions.BuilderActionInteract;
import com.electro.hycitizens.commands.CitizensCommand;
import com.electro.hycitizens.listeners.*;
import com.electro.hycitizens.managers.CitizensManager;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.ui.CitizensUI;
import com.electro.hycitizens.util.ConfigManager;
import com.electro.hycitizens.util.UpdateChecker;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.event.events.player.*;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import javax.annotation.Nonnull;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class HyCitizensPlugin extends JavaPlugin {
    private static HyCitizensPlugin instance;
    private ConfigManager configManager;
    private CitizensManager citizensManager;
    private CitizensUI citizensUI;

    // Listeners
    private PlayerAddToWorldListener addToWorldListener;
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
        this.addToWorldListener = new PlayerAddToWorldListener(this);
        this.chunkPreLoadListener = new ChunkPreLoadListener(this);
        this.connectionListener = new PlayerConnectionListener(this);

        NPCPlugin.get().registerCoreComponentType("CitizenInteraction", BuilderActionInteract::new);

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
