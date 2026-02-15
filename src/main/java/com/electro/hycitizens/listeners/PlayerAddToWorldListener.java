package com.electro.hycitizens.listeners;
import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class PlayerAddToWorldListener {
    private final HyCitizensPlugin plugin;

    public PlayerAddToWorldListener(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    // Moved to ChunkPreLoadListener
    public void onAddPlayerToWorld(@Nonnull AddPlayerToWorldEvent event) {
//        World world = event.getWorld();
//        if (world == null) {
//            return;
//        }
//        for (CitizenData citizen : plugin.getCitizensManager().getAllCitizens()) {
//            if (!world.getWorldConfig().getUuid().equals(citizen.getWorldUUID()))
//                continue;
//
//            // Skip citizens that were just created (within last 10 seconds) to prevent double spawning
//            long timeSinceCreation = System.currentTimeMillis() - citizen.getCreatedAt();
//            if (timeSinceCreation < 10000) {
//                continue;
//            }
//
//            // It's possible the entity is already in the world but just isn't loaded in yet
//
//            // First check if the chunk is already loaded
//            long chunkIndex = ChunkUtil.indexChunkFromBlock(citizen.getPosition().x, citizen.getPosition().z);
//            WorldChunk loadedChunk = world.getChunkIfLoaded(chunkIndex);
//            if (loadedChunk != null) {
//                if (world.getEntityRef(citizen.getSpawnedUUID()) == null) {
//                    plugin.getCitizensManager().spawnCitizenNPC(citizen, true);
//                } else {
//                    // Entity exists, update skin if live skin is enabled
//                    if (citizen.isPlayerModel() && citizen.isUseLiveSkin()) {
//                        plugin.getCitizensManager().updateCitizenSkin(citizen, true);
//                    }
//                }
//
//                continue;
//            }
//
//            // Chunk is not loaded. Try to wait for it to load, if it takes too long, assume it won't load and load it
//            long start = System.currentTimeMillis();
//            final ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];
//            boolean[] spawned = { false };
//            boolean[] queued = { false };
//
//            futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
//                if (spawned[0]) {
//                    futureRef[0].cancel(false);
//                    return;
//                }
//
//                // Timeout
//                long elapsedMs = System.currentTimeMillis() - start;
//                if (elapsedMs >= 15_000) {
//                    futureRef[0].cancel(false);
//
//                    // Check if the citizen spawned, if it didn't then it's likely it's in an unloaded chunk. Load the chunk and try again
//                    // Todo: This isn't very performant if there's a lot of citizens in unloaded chunks
//                    if (!spawned[0]) {
//                        WorldChunk chunkInMemory = world.getChunkIfInMemory(chunkIndex);
//                        if (chunkInMemory == null) {
//                            // Chunk is not in memory, there's nothing we can do to check if citizen is loaded or not
//                            return;
//                        }
//
//                        world.loadChunkIfInMemory(chunkIndex);
//
//                        // If the chunk loads, try to spawn the citizen if it doesn't exist
//                        if (world.getEntityRef(citizen.getSpawnedUUID()) == null) {
//                            plugin.getCitizensManager().spawnCitizenNPC(citizen, true);
//                        } else {
//                            // Entity exists, update skin if live skin is enabled
//                            if (citizen.isPlayerModel() && citizen.isUseLiveSkin()) {
//                                plugin.getCitizensManager().updateCitizenSkin(citizen, true);
//                            }
//                        }
//
//                        if (world.getEntityRef(citizen.getHologramUUID()) == null) {
//                            plugin.getCitizensManager().spawnCitizenHologram(citizen, true);
//                        }
//                    }
//
//                    return;
//                }
//
//                if (queued[0]) {
//                    return;
//                }
//                queued[0] = true;
//
//                world.execute(() -> {
//                    WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
//
//                    if (chunk == null) {
//                        queued[0] = false;
//                        return;
//                    }
//
//                    spawned[0] = true;
//                    futureRef[0].cancel(false);
//
//                    // If the chunk is loaded, try to spawn the citizen if it doesn't exist
//                    if (world.getEntityRef(citizen.getSpawnedUUID()) == null) {
//                        plugin.getCitizensManager().spawnCitizenNPC(citizen, true);
//                    } else {
//                        // Entity exists, update skin if live skin is enabled
//                        if (citizen.isPlayerModel() && citizen.isUseLiveSkin()) {
//                            plugin.getCitizensManager().updateCitizenSkin(citizen, true);
//                        }
//                    }
//
//                    if (world.getEntityRef(citizen.getHologramUUID()) == null) {
//                        plugin.getCitizensManager().spawnCitizenHologram(citizen, true);
//                    }
//                });
//
//            }, 0, 250, TimeUnit.MILLISECONDS);
//        }
    }
}
