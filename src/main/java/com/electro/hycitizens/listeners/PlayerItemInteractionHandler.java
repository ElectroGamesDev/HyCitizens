package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.interactions.NullInteraction;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.PathConfig;
import com.electro.hycitizens.models.PatrolPath;
import com.electro.hycitizens.models.PatrolWaypoint;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.InteractionType;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class PlayerItemInteractionHandler implements PacketWatcher {
    private final HyCitizensPlugin plugin;

    private final ConcurrentHashMap<UUID, Long> interactionCooldowns;
    private static final long COOLDOWN_MS = 1000; // 1 second cooldown between interactions

    public PlayerItemInteractionHandler(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
        this.interactionCooldowns = new ConcurrentHashMap<>();
    }

    public void register() {
        PacketAdapters.registerInbound(this);

        Interaction.CODEC.register("PatrolStick", NullInteraction.class, NullInteraction.CODEC);
    }

    public void unregister() {
        try {
            PacketAdapters.class.getMethod("unregisterInbound", PacketWatcher.class).invoke(null, this);
        } catch (NoSuchMethodException ignored) {
            // Older API versions may not expose explicit unregistration.
        } catch (Exception e) {
            getLogger().atWarning().log("Failed to unregister item interaction packet watcher", e);
        }
    }

    @Override
    public void accept(PacketHandler packetHandler, Packet packet) {
        if (!(packet instanceof SyncInteractionChains interactionChains)) {
            return;
        }

        try {
            SyncInteractionChain[] updates = interactionChains.updates;

            if (packetHandler.getAuth() == null)
                return;

            // Get player from packet handler
            UUID playerUuid = packetHandler.getAuth().getUuid();
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);

            if (playerRef == null || !playerRef.isValid()) {
                return;
            }

            // Process each interaction
            for (SyncInteractionChain chain : updates) {
                handleInteraction(playerRef, chain);
            }
        } catch (Exception e) {
            getLogger().atWarning().log("Error handling player interaction", e);
        }
    }

    private void handleInteraction(@Nonnull PlayerRef playerRef, @Nonnull SyncInteractionChain chain) {

        InteractionType type = chain.interactionType;

        if (type != InteractionType.Primary && type != InteractionType.Secondary) {
            return;
        }

        // Cooldown check can stay synchronous
        if (!checkCooldown(playerRef.getUuid())) {
            return;
        }

        getHeldItem(playerRef, heldItem -> {
            if (heldItem == null || !heldItem.isValid()) {
                return;
            }

            String itemId = heldItem.getItemId();

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }

            Store<EntityStore> store = ref.getStore();

            switch (itemId) {
                case "PatrolStick" -> {
                    String pathName = heldItem.getFromMetadataOrNull(
                            "HyCitizensPatrolStick",
                            Codec.STRING
                    );

                    if (pathName == null) {
                        return;
                    }

                    PatrolPath path = plugin.getCitizensManager().getPatrolManager().getPath(pathName);

                    if (path == null) {
                        playerRef.sendMessage(Message.raw("Could not find a path linked to this item").color(Color.RED));
                        return;
                    }

                    if (type == InteractionType.Primary) {
                        // We need to pass in a citizen with the patrol path, so we'll get the first one using it
                        CitizenData foundCitizen = null;

                        for (CitizenData citizen : plugin.getCitizensManager().getAllCitizens()) {
                            if (citizen.getPathConfig().getPluginPatrolPath().equals(pathName)) {
                                foundCitizen = citizen;
                                break;
                            }
                        }

                        if (foundCitizen == null) {
                            playerRef.sendMessage(Message.raw("At least one citizen must be using this path.").color(Color.RED));
                            return;
                        }

                        plugin.getCitizensUI().openPatrolPathEditorGUI(playerRef, store, foundCitizen, pathName);
                    }
                    else
                    {
                        Vector3d pos = new Vector3d(playerRef.getTransform().getPosition());
                        plugin.getCitizensManager().getPatrolManager().addWaypoint(
                                path.getName(), new PatrolWaypoint(pos.x, pos.y, pos.z, 0f));

                        playerRef.sendMessage(Message.raw("Waypoint added at your position").color(Color.GREEN));
                    }
                }
            }
        });
    }

    private void getHeldItem(@Nonnull PlayerRef playerRef, @Nonnull Consumer<ItemStack> callback) {

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            callback.accept(null);
            return;
        }

        Store<EntityStore> store = ref.getStore();

        store.getExternalData().getWorld().execute(() -> {
            try {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    callback.accept(null);
                    return;
                }

                callback.accept(player.getInventory().getItemInHand());
            } catch (Exception e) {
                getLogger().atWarning().log("Failed to get held item", e.getMessage());
                callback.accept(null);
            }
        });
    }

    private boolean checkCooldown(@Nonnull UUID playerUuid) {
        long currentTime = System.currentTimeMillis();
        Long lastInteraction = interactionCooldowns.get(playerUuid);

        if (lastInteraction != null && (currentTime - lastInteraction) < COOLDOWN_MS) {
            return false; // Still on cooldown
        }

        // Update cooldown timestamp
        interactionCooldowns.put(playerUuid, currentTime);
        return true;
    }

    public void clearCooldown(@Nonnull UUID playerUuid) {
        interactionCooldowns.remove(playerUuid);
    }
}
