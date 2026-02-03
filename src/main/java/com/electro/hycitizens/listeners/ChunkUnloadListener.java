package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;

public class ChunkUnloadListener extends EntityEventSystem<ChunkStore, ChunkUnloadEvent> {

    private final HyCitizensPlugin plugin;

    public ChunkUnloadListener(@Nonnull HyCitizensPlugin plugin) {
        super(ChunkUnloadEvent.class);
        this.plugin = plugin;
    }

    // DOES NOT WORK, CRASHES
    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                       @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer,
                       @Nonnull ChunkUnloadEvent event) {
        WorldChunk chunk = event.getChunk();

        if (chunk.getEntityChunk() == null) {
            return;
        }

        for (Ref<EntityStore> ref : chunk.getEntityChunk().getEntityReferences()) {
            for (CitizenData citizen : plugin.getCitizensManager().getAllCitizens()) {
                // Despawn the citizen if it's in the chunk and uses a player model
                if (citizen.getNpcRef() != ref || !citizen.isPlayerModel()) {
                    continue;
                }

                plugin.getCitizensManager().despawnCitizenNPC(citizen);
            }
        }
    }

    @NullableDecl
    @Override
    public Query<ChunkStore> getQuery() {
        return Query.any();
    }
}
