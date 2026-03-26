package com.electro.hycitizens.listeners;

import com.electro.hycitizens.components.CitizenNametagComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DuplicateNametagPrevention extends RefSystem<EntityStore> {

    @Nonnull
    private final ComponentType<EntityStore, CitizenNametagComponent> nametagComponentType =
            CitizenNametagComponent.getComponentType();

    @Nonnull
    private final Query<EntityStore> query = this.nametagComponentType;

    @Nonnull
    private final Map<String, Ref<EntityStore>> activeNametags = new ConcurrentHashMap<>();

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
                              @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        CitizenNametagComponent nametagComponent = store.getComponent(ref, this.nametagComponentType);
        if (nametagComponent == null || nametagComponent.getCitizenId().isEmpty() || nametagComponent.getLineIndex() < 0) {
            return;
        }

        String identityKey = nametagComponent.getCitizenId() + ":" + nametagComponent.getLineIndex();
        Ref<EntityStore> existingRef = this.activeNametags.get(identityKey);
        if (existingRef != null && existingRef.isValid() && !existingRef.equals(ref)) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }

        this.activeNametags.put(identityKey, ref);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
                               @Nonnull RemoveReason reason,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        CitizenNametagComponent nametagComponent = store.getComponent(ref, this.nametagComponentType);
        if (nametagComponent == null || nametagComponent.getCitizenId().isEmpty() || nametagComponent.getLineIndex() < 0) {
            return;
        }

        String identityKey = nametagComponent.getCitizenId() + ":" + nametagComponent.getLineIndex();
        Ref<EntityStore> existingRef = this.activeNametags.get(identityKey);
        if (existingRef != null && existingRef.equals(ref)) {
            this.activeNametags.remove(identityKey);
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }
}
