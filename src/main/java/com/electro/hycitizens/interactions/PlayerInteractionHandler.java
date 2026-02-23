package com.electro.hycitizens.interactions;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class PlayerInteractionHandler implements PacketWatcher {
    private final ConcurrentHashMap<UUID, Long> interactionCooldowns;
    private static final long COOLDOWN_MS = 500;

    public PlayerInteractionHandler() {
        this.interactionCooldowns = new ConcurrentHashMap<>();
    }

    public void register() {
        PacketAdapters.registerInbound(this);
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

            World world = Universe.get().getWorld(playerRef.getWorldUuid());

            if (world == null) {
                return;
            }

            // Process each interaction
            world.execute(() -> {
                for (SyncInteractionChain chain : updates) {
                    handleInteraction(playerRef, chain);
                }
            });
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("Error handling player interaction");
        }
    }

    private void handleInteraction(@Nonnull PlayerRef playerRef, @Nonnull SyncInteractionChain chain) {
        InteractionType type = chain.interactionType;
        if (type != InteractionType.Use && type != InteractionType.Secondary && type != InteractionType.Primary) {
            return;
        }

        if (chain.data == null) {
            return;
        }

        if (!checkCooldown(playerRef.getUuid())) {
            return;
        }

        // Map the raw interaction type to a semantic source constant.
        // InteractionType.Use  = F key ("Use" / interact key)
        // InteractionType.Primary = left click (primary attack/interact)
        String interactionSource = (type == InteractionType.Use)
                ? CitizenInteraction.SOURCE_F_KEY
                : CitizenInteraction.SOURCE_LEFT_CLICK;

        Store<EntityStore> store = playerRef.getReference().getStore();
        Ref<EntityStore> entity = store.getExternalData().getRefFromNetworkId(chain.data.entityId);
        if (entity == null) {
            return;
        }

        List<CitizenData> citizens = HyCitizensPlugin.get().getCitizensManager().getAllCitizens();
        for (CitizenData citizen : citizens) {
            if (citizen.getNpcRef() == null || !citizen.getNpcRef().isValid())
                continue;

            if (citizen.getNpcRef().getIndex() != entity.getIndex()) {
                continue;
            }

            // CitizenInteraction will do its own early-exit if no actions match the source.
            CitizenInteraction.handleInteraction(citizen, playerRef, interactionSource);
            break;
        }
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