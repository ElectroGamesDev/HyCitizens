package com.electro.hycitizens;

import com.electro.hycitizens.actions.BuilderActionInteract;
import com.electro.hycitizens.commands.CitizensCommand;
import com.electro.hycitizens.components.CitizenNametagComponent;
import com.electro.hycitizens.interactions.PlayerInteractionHandler;
import com.electro.hycitizens.listeners.*;
import com.electro.hycitizens.map.CitizenMapMarkerAsset;
import com.electro.hycitizens.map.CitizenMapMarkerProvider;
import com.electro.hycitizens.managers.CitizensManager;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.ui.CitizensUI;
import com.electro.hycitizens.ui.SkinCustomizerUI;
import com.electro.hycitizens.util.ConfigManager;
import com.electro.hycitizens.util.RoleAssetPackManager;
import com.electro.hycitizens.util.UpdateChecker;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.npc.NPCPlugin;

import javax.annotation.Nonnull;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class HyCitizensPlugin extends JavaPlugin {
    private static HyCitizensPlugin instance;
    private ConfigManager configManager;
    private CitizensManager citizensManager;
    private CitizensUI citizensUI;
    private SkinCustomizerUI skinCustomizerUI;
    private Path generatedRolesPath;
    private ComponentType<EntityStore, CitizenNametagComponent> citizenNametagComponent;
    private CitizenMapMarkerProvider citizenMapMarkerProvider;

    // Listeners
    private ChunkPreLoadListener chunkPreLoadListener;
    private PlayerConnectionListener connectionListener;

    private PlayerInteractionHandler interactionHandler;
    private PlayerItemInteractionHandler itemInteractionHandler;

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
        this.citizenNametagComponent = this.getEntityStoreRegistry().registerComponent(
                CitizenNametagComponent.class,
                "HCNAMETAG",
                CitizenNametagComponent.CODEC
        );

        this.citizensManager = new CitizensManager(this);
        this.citizenMapMarkerProvider = new CitizenMapMarkerProvider(this);
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

        this.itemInteractionHandler = new PlayerItemInteractionHandler(this);
        this.itemInteractionHandler.register();
    }

    @Override
    protected void start() {
        UpdateChecker.checkAsync();

        // Regenerate all roles
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            citizensManager.getRoleGenerator().regenerateAllRoles(citizensManager.getAllCitizens());
        }, 250, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void shutdown() {
        if (interactionHandler != null) {
            interactionHandler.unregister();
        }

        if (itemInteractionHandler != null) {
            itemInteractionHandler.unregister();
        }

        CitizenMapMarkerAsset.clearAllViewers();

        if (citizensManager != null) {
            citizensManager.shutdown();
        }
    }

    private void registerEventListeners() {
        getEventRegistry().register(PlayerDisconnectEvent.class, connectionListener::onPlayerDisconnect);
        getEventRegistry().register(PlayerConnectEvent.class, connectionListener::onPlayerConnect);
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> registerCitizenMapMarkerProvider(event.getWorld()));
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            if (event.getPlayerRef() != null) {
                CitizenMapMarkerAsset.clearViewer(event.getPlayerRef().getUuid());
            }
        });

        this.getEntityStoreRegistry().registerSystem(new EntityDamageListener(this));
        this.getEntityStoreRegistry().registerSystem(new EntityDeathListener(this));
        this.getEntityStoreRegistry().registerSystem(new PatrolStickBlockBreakListener());
        getEventRegistry().registerGlobal(EventPriority.LAST, ChunkPreLoadProcessEvent.class, chunkPreLoadListener::onChunkPreload);

        this.getEntityStoreRegistry().registerSystem(new DuplicateNPCPrevention());
        this.getEntityStoreRegistry().registerSystem(new DuplicateNametagPrevention());

        if (Universe.get() != null) {
            for (World world : Universe.get().getWorlds().values()) {
                registerCitizenMapMarkerProvider(world);
            }
        }
    }

    private void registerCitizenMapMarkerProvider(World world) {
        if (world == null || citizenMapMarkerProvider == null) {
            return;
        }

        WorldMapManager worldMapManager = world.getWorldMapManager();
        if (worldMapManager == null) {
            return;
        }

        Map<String, WorldMapManager.MarkerProvider> providers = worldMapManager.getMarkerProviders();
        if (providers != null && providers.get("hycitizens") instanceof CitizenMapMarkerProvider) {
            return;
        }

        if (providers != null) {
            try {
                providers.put("hycitizens", citizenMapMarkerProvider);
                return;
            } catch (UnsupportedOperationException ignored) {
            }
        }

        worldMapManager.addMarkerProvider("hycitizens", citizenMapMarkerProvider);
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

    @Nonnull
    public ComponentType<EntityStore, CitizenNametagComponent> getCitizenNametagComponent() {
        return citizenNametagComponent;
    }
}
