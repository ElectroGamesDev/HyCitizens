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
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;
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
    private ChunkUnloadListener chunkUnloadListener;
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
        this.chunkUnloadListener = new ChunkUnloadListener(this);
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

        // We need to despawn citizens with player skins to prevent issues
        getEventRegistry().register((short) -40, ShutdownEvent.class, event -> {
            List<CitizenData> citizens = getCitizensManager().getAllCitizens();
            for (CitizenData citizen : citizens) {
                if (citizen.isPlayerModel()) {
                    try {
                        getCitizensManager().despawnCitizenNPC(citizen);
                    } catch (Exception e) {
                        getLogger().atSevere().withCause(e).log("Failed to despawn citizen: " + citizen.getId());
                    }
                }
            }
        });


        // Does not work, crashes
        //getChunkStoreRegistry().registerSystem(new ChunkUnloadListener(this));
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
