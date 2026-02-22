package com.electro.hycitizens;

import com.electro.hycitizens.actions.BuilderActionInteract;
import com.electro.hycitizens.commands.CitizensCommand;
import com.electro.hycitizens.interactions.PlayerInteractionHandler;
import com.electro.hycitizens.listeners.ChunkPreLoadListener;
import com.electro.hycitizens.listeners.EntityDamageListener;
import com.electro.hycitizens.listeners.PlayerConnectionListener;
import com.electro.hycitizens.managers.CitizensManager;
import com.electro.hycitizens.ui.CitizensUI;
import com.electro.hycitizens.ui.SkinCustomizerUI;
import com.electro.hycitizens.util.ConfigManager;
import com.electro.hycitizens.util.RoleAssetPackManager;
import com.electro.hycitizens.util.UpdateChecker;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.npc.NPCPlugin;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HyCitizensPlugin extends JavaPlugin {
    private static HyCitizensPlugin instance;
    private ConfigManager configManager;
    private CitizensManager citizensManager;
    private CitizensUI citizensUI;
    private SkinCustomizerUI skinCustomizerUI;
    private Path generatedRolesPath;

    // Listeners
    private ChunkPreLoadListener chunkPreLoadListener;
    private PlayerConnectionListener connectionListener;

    private PlayerInteractionHandler interactionHandler;

    public HyCitizensPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        // Initialize config manager
        this.configManager = new ConfigManager(Paths.get("mods", "HyCitizensData"));

        this.generatedRolesPath = Paths.get("mods", "HyCitizensRoles", "Server", "NPC", "Roles");

        RoleAssetPackManager.setup();

        this.citizensManager = new CitizensManager(this);
        this.citizensUI = new CitizensUI(this);
        this.skinCustomizerUI = new SkinCustomizerUI(this);

        // Register commands
        getCommandRegistry().registerCommand(new CitizensCommand(this));

        // Initialize listeners
        this.chunkPreLoadListener = new ChunkPreLoadListener(this);
        this.connectionListener = new PlayerConnectionListener(this);

        // Register event listeners
        registerEventListeners();

        this.interactionHandler = new PlayerInteractionHandler();
        this.interactionHandler.register();
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

    public SkinCustomizerUI getSkinCustomizerUI() {
        return skinCustomizerUI;
    }

    @Nonnull
    public Path getGeneratedRolesPath() {
        return generatedRolesPath;
    }
}
