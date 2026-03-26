package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class ChunkPreLoadListener {
    private static final long NPC_RESOLVE_TIMEOUT_MS = 15_000L;
    private static final long NPC_RESOLVE_RETRY_MS = 500L;

    private final HyCitizensPlugin plugin;
    private final Set<String> citizensBeingProcessed = ConcurrentHashMap.newKeySet();
    private final Set<String> citizensPendingNpcResolution = ConcurrentHashMap.newKeySet();

    public ChunkPreLoadListener(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void onChunkPreload(ChunkPreLoadProcessEvent event) {
        World world = event.getChunk().getWorld();
        long eventChunkIndex = event.getChunk().getIndex();
        UUID worldUUID = world.getWorldConfig().getUuid();

        plugin.getCitizensManager().processPendingNpcRemovals(world, eventChunkIndex);
        plugin.getCitizensManager().processPendingHologramRemovals(world, eventChunkIndex);

        // Collect citizens that belong to this chunk, then return from the event
        for (CitizenData citizen : plugin.getCitizensManager().getAllCitizens()) {
            if (!worldUUID.equals(citizen.getWorldUUID()))
                continue;

            if (citizen.isAwaitingRespawn()) {
                continue;
            }

            // Skip citizens that were just created (within last 10 seconds) to prevent double spawning
            long timeSinceCreation = System.currentTimeMillis() - citizen.getCreatedAt();
            if (timeSinceCreation < 10000) {
                continue;
            }

            // Match either persisted current position or base spawn position.
            Vector3d currentPosition = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
            long currentChunkIndex = ChunkUtil.indexChunkFromBlock(currentPosition.x, currentPosition.z);
            Vector3d basePosition = citizen.getPosition();
            long baseChunkIndex = ChunkUtil.indexChunkFromBlock(basePosition.x, basePosition.z);
            if (eventChunkIndex != currentChunkIndex && eventChunkIndex != baseChunkIndex) {
                continue;
            }

            // Skip citizens that are already being spawned by another code path
            if (plugin.getCitizensManager().isCitizenSpawning(citizen.getId())) {
                continue;
            }

            // Hand off the heavy work to run outside the event
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                processCitizenAsync(world, citizen, eventChunkIndex);
            }, 0, TimeUnit.MILLISECONDS);
        }
    }

    private void processCitizenAsync(World world, CitizenData citizen, long chunkIndex) {
        if (citizen.isAwaitingRespawn()) {
            return;
        }

        // First check if the chunk is already loaded
        WorldChunk loadedChunk = world.getChunkIfLoaded(chunkIndex);
        if (loadedChunk != null) {
            world.execute(() -> {
                if (citizen.isAwaitingRespawn()) {
                    return;
                }

                Ref<EntityStore> entityRef = null;
                if (citizen.getSpawnedUUID() != null) {
                    // Not reliable
                    //entityRef = world.getEntityRef(citizen.getSpawnedUUID());

                    entityRef = checkIfNpcExists(world.getEntityStore().getStore(), citizen);
                }

                if (entityRef == null || !entityRef.isValid()) {
                    resolveOrSpawnCitizenNPC(world, citizen, true);
                } else {
                    onCitizenEntityResolved(citizen, entityRef);
                }
            });

            // Schedule delayed hologram check for chunk already loaded case
            scheduleHologramCheck(world, citizen, chunkIndex);

            return;
        }

        // Chunk is not loaded. Try to wait for it to load, if it takes too long, assume it won't load and load it
        long start = System.currentTimeMillis();
        final ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];
        boolean[] spawned = { false };
        boolean[] queued = { false };
        boolean[] hologramCheckScheduled = { false };

        futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            if (citizen.isAwaitingRespawn()) {
                futureRef[0].cancel(false);
                return;
            }

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

                    world.loadChunkIfInMemory(chunkIndex); // Todo: we should not be loading the chunk

                    world.execute(() -> {
                        if (citizen.isAwaitingRespawn()) {
                            return;
                        }

                        Ref<EntityStore> entityRef = null;
                        if (citizen.getSpawnedUUID() != null) {
                            // Not reliable
                            //entityRef = world.getEntityRef(citizen.getSpawnedUUID());

                            entityRef = checkIfNpcExists(world.getEntityStore().getStore(), citizen);
                        }

                        // If the chunk loads, try to spawn the citizen if it doesn't exist
                        if (entityRef == null || !entityRef .isValid()) {
                            resolveOrSpawnCitizenNPC(world, citizen, true);
                        } else {
                            onCitizenEntityResolved(citizen, entityRef);
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
                if (citizen.isAwaitingRespawn()) {
                    return;
                }

                WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);

                if (chunk == null) {
                    queued[0] = false;
                    return;
                }

                spawned[0] = true;
                futureRef[0].cancel(false);

                Ref<EntityStore> entityRef = null;
                if (citizen.getSpawnedUUID() != null) {
                    // Not relaible
                    //entityRef = world.getEntityRef(citizen.getSpawnedUUID());

                    entityRef = checkIfNpcExists(world.getEntityStore().getStore(), citizen);
                }

                // If the chunk is loaded, try to spawn the citizen if it doesn't exist
                if (entityRef == null || !entityRef.isValid()) {
                    resolveOrSpawnCitizenNPC(world, citizen, true);
                } else {
                    onCitizenEntityResolved(citizen, entityRef);
                }

                // Schedule delayed hologram check after periodic spawn
                if (!hologramCheckScheduled[0]) {
                    hologramCheckScheduled[0] = true;
                    scheduleHologramCheck(world, citizen, chunkIndex);
                }
            });

        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    private void resolveOrSpawnCitizenNPC(@Nonnull World world, @Nonnull CitizenData citizen, boolean save) {
        if (citizen.isAwaitingRespawn()) {
            return;
        }

        UUID storedUuid = citizen.getSpawnedUUID();
        if (storedUuid == null) {
            plugin.getCitizensManager().spawnCitizenNPC(citizen, save);
            return;
        }

        // Not reliable
        //Ref<EntityStore> currentRef = world.getEntityRef(storedUuid);

        Ref<EntityStore> currentRef = checkIfNpcExists(world.getEntityStore().getStore(), citizen);

        if (currentRef != null && currentRef.isValid()) {
            onCitizenEntityResolved(citizen, currentRef);
            return;
        }

        // The chunk pre-load event can run before UUID-backed entities are fully reattached.
        // Retry UUID resolution before deciding it is stale.
        if (!citizensPendingNpcResolution.add(citizen.getId())) {
            return;
        }

        long resolutionStart = System.currentTimeMillis();
        final ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];
        futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> world.execute(() -> {
            if (citizen.isAwaitingRespawn()) {
                if (futureRef[0] != null) {
                    futureRef[0].cancel(false);
                }
                citizensPendingNpcResolution.remove(citizen.getId());
                return;
            }

            Ref<EntityStore> currentCitizenRef = citizen.getNpcRef();
            if (currentCitizenRef != null && currentCitizenRef.isValid()) {
                if (futureRef[0] != null) {
                    futureRef[0].cancel(false);
                }
                citizensPendingNpcResolution.remove(citizen.getId());
                return;
            }

            UUID retryUuid = citizen.getSpawnedUUID();
            if (retryUuid != null) {
                // Not reliable
                //Ref<EntityStore> resolvedRef = world.getEntityRef(retryUuid);

                Ref<EntityStore> resolvedRef = checkIfNpcExists(world.getEntityStore().getStore(), citizen);
                if (resolvedRef != null && resolvedRef.isValid()) {
                    if (futureRef[0] != null) {
                        futureRef[0].cancel(false);
                    }
                    citizensPendingNpcResolution.remove(citizen.getId());
                    onCitizenEntityResolved(citizen, resolvedRef);
                    return;
                }
            }

            if (System.currentTimeMillis() - resolutionStart < NPC_RESOLVE_TIMEOUT_MS) {
                return;
            }

            if (futureRef[0] != null) {
                futureRef[0].cancel(false);
            }
            citizensPendingNpcResolution.remove(citizen.getId());

            // UUID is stale after retry timeout; allow a fresh spawn with a new UUID.
            plugin.getCitizensManager().clearCitizenEntityBinding(citizen);
            plugin.getCitizensManager().spawnCitizenNPC(citizen, save);
        }), NPC_RESOLVE_RETRY_MS, NPC_RESOLVE_RETRY_MS, TimeUnit.MILLISECONDS);
    }

    private void onCitizenEntityResolved(@Nonnull CitizenData citizen, @Nonnull Ref<EntityStore> entityRef) {
        if (citizen.isAwaitingRespawn()) {
            return;
        }

        plugin.getCitizensManager().bindCitizenEntityBinding(citizen, entityRef);

        if (!plugin.getCitizensManager().refreshSpawnedCitizenAppearance(citizen) && citizen.isPlayerModel()) {
            plugin.getCitizensManager().updateCitizenSkin(citizen, true);
        }

        plugin.getCitizensManager().setInteractionComponent(entityRef.getStore(), entityRef, citizen);
        plugin.getCitizensManager().refreshNpcNameplate(citizen);
        plugin.getCitizensManager().triggerAnimations(citizen, "DEFAULT");
        plugin.getCitizensManager().updateCitizenNPCItems(citizen);
        plugin.getCitizensManager().getScheduleManager().refreshCitizen(citizen);

        String pluginPatrolPath = citizen.getPathConfig().getPluginPatrolPath();
        if (!pluginPatrolPath.isEmpty()) {
            plugin.getCitizensManager().startCitizenPatrol(citizen.getId(), pluginPatrolPath);
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

                    if (!plugin.getCitizensManager().shouldUseSeparateNametagEntities(citizen)) {
                        plugin.getCitizensManager().updateSpawnedCitizenHologram(citizen, true);
                        return;
                    }

                    if (plugin.getCitizensManager().migrateLegacyCitizenHologramEntities(world, citizen)
                            || plugin.getCitizensManager().rebindCitizenHologramEntities(world, citizen)) {
                        plugin.getCitizensManager().updateSpawnedCitizenHologram(citizen, true);
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

                if (!plugin.getCitizensManager().shouldUseSeparateNametagEntities(citizen)) {
                    plugin.getCitizensManager().updateSpawnedCitizenHologram(citizen, true);
                    hologramChecked[0] = true;
                    hologramFutureRef[0].cancel(false);
                    citizensBeingProcessed.remove(citizen.getId());
                    return;
                }

                if (plugin.getCitizensManager().migrateLegacyCitizenHologramEntities(world, citizen)
                        || plugin.getCitizensManager().rebindCitizenHologramEntities(world, citizen)) {
                    plugin.getCitizensManager().updateSpawnedCitizenHologram(citizen, true);
                    hologramChecked[0] = true;
                    hologramFutureRef[0].cancel(false);
                    citizensBeingProcessed.remove(citizen.getId());
                    return;
                }

                if (citizen.getHologramLineUuids() == null || citizen.getHologramLineUuids().isEmpty()) {
                    // No hologram UUIDs stored, spawn new hologram
                    plugin.getCitizensManager().spawnCitizenHologram(citizen, true);
                    hologramChecked[0] = true;
                    hologramFutureRef[0].cancel(false);
                    citizensBeingProcessed.remove(citizen.getId());
                }
            });

        }, 100, 500, TimeUnit.MILLISECONDS);
    }

    Ref<EntityStore> checkIfNpcExists(Store<EntityStore> store, CitizenData citizen) {
        String rolePrefix = "HyCitizens_" + citizen.getId() + "_";
        Query<EntityStore> query = NPCEntity.getComponentType();
        CompletableFuture<Ref<EntityStore>> found = new CompletableFuture<>();

        // Todo: Switch to a custom citizen component so we don't rely on roles
        store.forEachEntityParallel(query, (index, archetypeChunk, cb) -> {
            if (found.isDone()) {
                return;
            }

            Ref<EntityStore> otherRef = archetypeChunk.getReferenceTo(index);
            if (otherRef == null || !otherRef.isValid()) {
                return;
            }

            NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
            if (npc == null || npc.getRole() == null) {
                return;
            }

            String roleName = npc.getRole().getRoleName();
            if (roleName != null && roleName.startsWith(rolePrefix)) {
                found.complete(otherRef);
            }
        });

        return found.isDone() ? found.join() : null;
    }
}
