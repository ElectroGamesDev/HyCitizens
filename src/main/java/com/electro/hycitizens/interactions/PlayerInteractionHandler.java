package com.electro.hycitizens.interactions;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.*;
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

    public void unregister() {
        try {
            PacketAdapters.class.getMethod("unregisterInbound", PacketWatcher.class).invoke(null, this);
        } catch (NoSuchMethodException ignored) {
            // Older API versions may not expose explicit unregistration.
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("Failed to unregister interaction packet watcher.");
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

        String interactionSource = (type == InteractionType.Use)
                ? CitizenInteraction.SOURCE_F_KEY
                : CitizenInteraction.SOURCE_LEFT_CLICK;

        Store<EntityStore> store = playerRef.getReference().getStore();
        Ref<EntityStore> entity = store.getExternalData().getRefFromNetworkId(chain.data.entityId);
        if (entity == null) {
            return;
        }

        UUID interactedUuid = null;
        UUIDComponent uuidComponent = entity.getStore().getComponent(entity, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            interactedUuid = uuidComponent.getUuid();
        }

        List<CitizenData> citizens = HyCitizensPlugin.get().getCitizensManager().getAllCitizens();
        for (CitizenData citizen : citizens) {
            Ref<EntityStore> npcRef = citizen.getNpcRef();
            boolean isDirectRefMatch = npcRef != null && npcRef.isValid() && npcRef.equals(entity);
            boolean isUuidMatch = interactedUuid != null && citizen.getSpawnedUUID() != null
                    && citizen.getSpawnedUUID().equals(interactedUuid);

            if (!isDirectRefMatch && !isUuidMatch) {
                continue;
            }

            // If the ref became stale after chunk/entity reattachment, repair it from the interacted entity.
            if (!isDirectRefMatch) {
                //getLogger().atInfo().log("Recovered citizen NPC ref from UUID match for " + citizen.getId());
                HyCitizensPlugin.get().getCitizensManager().bindCitizenEntityRef(citizen, entity);
                World world = Universe.get().getWorld(citizen.getWorldUUID());
                if (world != null) {
                    world.execute(() -> HyCitizensPlugin.get().getCitizensManager().restoreResolvedCitizenState(citizen, entity, false));
                }
            }

            if (type != InteractionType.Use
                    && HyCitizensPlugin.get().getCitizensUI().tryCompleteFollowTargetSelection(playerRef, citizen)) {
                break;
            }

            if (!handleCitizenStick(playerRef, citizen)) {
                if (CitizenInteraction.hasConfiguredInteraction(citizen, interactionSource)) {
                    CitizenInteraction.handleInteraction(citizen, playerRef, interactionSource);
                }
            }
            break;
        }
    }

    private boolean handleCitizenStick(@Nonnull PlayerRef playerRef, CitizenData citizen) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }

        Player player = ref.getStore().getComponent(ref, Player.getComponentType());
        if (player == null) {
            return false;
        }

        ItemStack held = player.getInventory().getItemInHand();
        if (held == null || !"CitizenStick".equals(held.getItemId())) {
            return false;
        }

        if (!player.hasPermission("hycitizens.admin")) {
            player.sendMessage(Message.raw("You need hycitizens.admin to use the Citizen Stick.").color(Color.RED));
            return false;
        }

        HyCitizensPlugin.get().getCitizensUI().openEditCitizenGUI(playerRef, ref.getStore(), citizen);

        return true;
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
