package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class ChunkPreLoadListener {
    private final HyCitizensPlugin plugin;
    private final Set<String> citizensBeingProcessed = ConcurrentHashMap.newKeySet();

    public ChunkPreLoadListener(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void onChunkPreload(ChunkPreLoadProcessEvent event) {
        World world = event.getChunk().getWorld();
        
        for (CitizenData citizen : plugin.getCitizensManager().getAllCitizens()) {
            if (!world.getWorldConfig().getUuid().equals(citizen.getWorldUUID()))
                continue;

            // Skip citizens that were just created (within last 10 seconds) to prevent double spawning
            long timeSinceCreation = System.currentTimeMillis() - citizen.getCreatedAt();
            if (timeSinceCreation < 10000) {
                continue;
            }

            // Check if the citizens is in the chunk
            long citizenChunkIndex = ChunkUtil.indexChunkFromBlock((int)citizen.getPosition().x, (int)citizen.getPosition().getZ());
            if (event.getChunk().getIndex() != citizenChunkIndex) {
                continue;
            }

            // First check if the chunk is already loaded
            long chunkIndex = ChunkUtil.indexChunkFromBlock(citizen.getPosition().x, citizen.getPosition().z);
            WorldChunk loadedChunk = world.getChunkIfLoaded(chunkIndex);
            if (loadedChunk != null) {
                world.execute(() -> {
                    Ref<EntityStore> entityRef = null;
                    if (citizen.getSpawnedUUID() != null) {
                        entityRef = world.getEntityRef(citizen.getSpawnedUUID());
                    }

                    if (entityRef == null) {
                        plugin.getCitizensManager().spawnCitizenNPC(citizen, true);
                    } else {
                        // Entity exists, update skin
                        if (citizen.isPlayerModel()) {
                            plugin.getCitizensManager().updateCitizenSkin(citizen, true);
                        }

                        // Update NPC ref
                        citizen.setNpcRef(entityRef);
                    }
                });

                // Schedule delayed hologram check for chunk already loaded case
                scheduleHologramCheck(world, citizen, chunkIndex);

                continue;
            }

            // Chunk is not loaded. Try to wait for it to load, if it takes too long, assume it won't load and load it
            long start = System.currentTimeMillis();
            final ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];
            boolean[] spawned = { false };
            boolean[] queued = { false };
            boolean[] hologramCheckScheduled = { false };

            futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                if (spawned[0]) {
                    futureRef[0].cancel(false);
                    return;
                }

                // Timeout
                long elapsedMs = System.currentTimeMillis() - start;
                WorldChunk loadedChunk2 = world.getChunkIfLoaded(chunkIndex);

                if (elapsedMs >= 15_000 || loadedChunk2 != null) {
                    futureRef[0].cancel(false);

                    // Check if the citizen spawned, if it didn't then it's likely it's in an unloaded chunk. Load the chunk and try again
                    // Todo: This isn't very performant if there's a lot of citizens in unloaded chunks
                    if (!spawned[0]) {
                        WorldChunk chunkInMemory = world.getChunkIfInMemory(chunkIndex);
                        if (chunkInMemory == null) {
                            // Chunk is not in memory, there's nothing we can do to check if citizen is loaded or not
                            return;
                        }

                        world.loadChunkIfInMemory(chunkIndex);

                        world.execute(() -> {
                            Ref<EntityStore> entityRef = null;
                            if (citizen.getSpawnedUUID() != null) {
                                entityRef = world.getEntityRef(citizen.getSpawnedUUID());
                            }

                            // If the chunk loads, try to spawn the citizen if it doesn't exist
                            if (entityRef == null) {
                                plugin.getCitizensManager().spawnCitizenNPC(citizen, true);
                            } else {
                                // Entity exists, update skin
                                if (citizen.isPlayerModel()) {
                                    plugin.getCitizensManager().updateCitizenSkin(citizen, true);
                                }

                                // Update NPC ref
                                citizen.setNpcRef(entityRef);
                            }
                        });

                        // Schedule delayed hologram check after timeout spawn
                        if (!hologramCheckScheduled[0]) {
                            hologramCheckScheduled[0] = true;
                            scheduleHologramCheck(world, citizen, chunkIndex);
                        }
                    }

                    return;
                }

                if (queued[0]) {
                    return;
                }
                queued[0] = true;

                world.execute(() -> {
                    WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);

                    if (chunk == null) {
                        queued[0] = false;
                        return;
                    }

                    spawned[0] = true;
                    futureRef[0].cancel(false);

                    Ref<EntityStore> entityRef = null;
                    if (citizen.getSpawnedUUID() != null) {
                        entityRef = world.getEntityRef(citizen.getSpawnedUUID());
                    }

                    // If the chunk is loaded, try to spawn the citizen if it doesn't exist
                    if (entityRef == null) {
                        plugin.getCitizensManager().spawnCitizenNPC(citizen, true);
                    } else {
                        // Entity exists, update skin
                        if (citizen.isPlayerModel()) {
                            plugin.getCitizensManager().updateCitizenSkin(citizen, true);
                        }

                        // Update NPC ref
                        citizen.setNpcRef(entityRef);
                    }

                    // Schedule delayed hologram check after periodic spawn
                    if (!hologramCheckScheduled[0]) {
                        hologramCheckScheduled[0] = true;
                        scheduleHologramCheck(world, citizen, chunkIndex);
                    }
                });

            }, 0, 250, TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleHologramCheck(World world, CitizenData citizen, long chunkIndex) {
        // Check if this citizen is already being processed
        if (!citizensBeingProcessed.add(citizen.getId())) {
            // Already being processed, skip
            return;
        }

        long hologramCheckStart = System.currentTimeMillis();
        final ScheduledFuture<?>[] hologramFutureRef = new ScheduledFuture<?>[1];
        boolean[] hologramChecked = { false };

        hologramFutureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            if (hologramChecked[0]) {
                hologramFutureRef[0].cancel(false);
                citizensBeingProcessed.remove(citizen.getId());
                return;
            }

            long hologramElapsedMs = System.currentTimeMillis() - hologramCheckStart;

            WorldChunk loadedChunk = world.getChunkIfLoaded(chunkIndex);

            // Timeout after 15 seconds total or until the chunk is loaded
            if (hologramElapsedMs >= 15_000 || loadedChunk != null) {
                hologramFutureRef[0].cancel(false);
                citizensBeingProcessed.remove(citizen.getId());

                // Timeout reached, spawn hologram if still needed
                world.execute(() -> {
                    WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                    if (chunk == null) {
                        return;
                    }

                    boolean shouldSpawnHologram = citizen.getHologramLineUuids() == null || citizen.getHologramLineUuids().isEmpty();
                    if (!shouldSpawnHologram) {
                        for (UUID uuid : citizen.getHologramLineUuids()) {
                            if (uuid == null || world.getEntityRef(uuid) == null) {
                                shouldSpawnHologram = true;
                                break;
                            }
                        }
                    }

                    if (shouldSpawnHologram) {
                        plugin.getCitizensManager().spawnCitizenHologram(citizen, true);
                    }
                });

                return;
            }

            // Check if hologram entities are loaded
            world.execute(() -> {
                WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                if (chunk == null) {
                    return;
                }

                boolean allHologramsExist = false;

                if (citizen.getHologramLineUuids() != null && !citizen.getHologramLineUuids().isEmpty()) {
                    allHologramsExist = true;
                    for (UUID uuid : citizen.getHologramLineUuids()) {
                        if (uuid == null || world.getEntityRef(uuid) == null) {
                            allHologramsExist = false;
                            break;
                        }
                    }
                }

                if (allHologramsExist) {
                    // All hologram entities found, we're done
                    hologramChecked[0] = true;
                    hologramFutureRef[0].cancel(false);
                    citizensBeingProcessed.remove(citizen.getId());
                    getLogger().atInfo().log("Found hologram. Hologram UUID: "  + citizen.getHologramLineUuids() + " for " + citizen.getName());
                } else if (citizen.getHologramLineUuids() == null || citizen.getHologramLineUuids().isEmpty()) {
                    // No hologram UUIDs stored, spawn new hologram
                    plugin.getCitizensManager().spawnCitizenHologram(citizen, true);
                    hologramChecked[0] = true;
                    hologramFutureRef[0].cancel(false);
                    citizensBeingProcessed.remove(citizen.getId());
                    getLogger().atInfo().log("DID NOT find hologram. Hologram UUID: "  + citizen.getHologramLineUuids() + " for " + citizen.getName());
                }
            });

        }, 100, 500, TimeUnit.MILLISECONDS);
    }
}