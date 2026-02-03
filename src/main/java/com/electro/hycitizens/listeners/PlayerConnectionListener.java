package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.util.UpdateChecker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class PlayerConnectionListener {
    private final HyCitizensPlugin plugin;

    public PlayerConnectionListener(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null)
            return;

        // We need to wait until the player is fully loaded into a world
        final long startTimeMs = System.currentTimeMillis();

        final ScheduledFuture<?>[] futureRef = new java.util.concurrent.ScheduledFuture<?>[1];

        futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            if (playerRef == null)
                return;

            // Stop the scheduler after 15 seconds
            if (System.currentTimeMillis() - startTimeMs >= 15000) {
                if (futureRef[0] != null) {
                    futureRef[0].cancel(false);
                }
                return;
            }

            if (playerRef.getWorldUuid() == null)
                return;

            World world = Universe.get().getWorld(playerRef.getWorldUuid());
            if (world == null)
                return;

            world.execute(() -> {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }

                Player player = playerRef.getReference().getStore().getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }

                // Stop the scheduler
                if (futureRef[0] != null) {
                    futureRef[0].cancel(false);
                }

                if (!player.hasPermission("hycitizens.admin")) {
                    return;
                }

                // Check for updates
                UpdateChecker.checkAsync();

                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    if (!UpdateChecker.hasUpdate()) {
                        return;
                    }

                    world.execute(() -> {
                        if (player == null) {
                            return;
                        }

                        player.sendMessage(
                                Message.join(
                                        Message.raw("[HyCitizens] ").color(Color.GREEN),

                                        Message.raw("Update available! ").color(Color.YELLOW),

                                        Message.raw("Latest: ").color(Color.GRAY),
                                        Message.raw(UpdateChecker.getLatestVersion()).color(Color.CYAN),

                                        Message.raw(" | ").color(Color.DARK_GRAY),

                                        Message.raw("Download (Click)").color(Color.ORANGE)
                                                .link("https://www.curseforge.com/hytale/mods/hycitizens"),

                                        Message.raw(" | ").color(Color.DARK_GRAY),

                                        Message.raw("Discord (Click)").color(Color.CYAN)
                                                .link("https://discord.gg/Snqz9E58Dr"),

                                        Message.raw(" - check out our Discord to get notified for future updates, request features, and submit bug reports.")
                                                .color(Color.GRAY)
                                )
                        );
                    });
                }, 5, TimeUnit.SECONDS);

            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        List<CitizenData> citizens = plugin.getCitizensManager().getAllCitizens();

        for (CitizenData citizen : citizens) {
            citizen.lastLookDirections.remove(event.getPlayerRef().getUuid());
        }
    }
}
