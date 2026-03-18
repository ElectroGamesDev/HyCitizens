package com.electro.hycitizens.listeners;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

public class DuplicateNPCPrevention extends RefSystem<EntityStore> {

    @Nonnull
    private final ComponentType<EntityStore, NPCEntity> npcComponentType = NPCEntity.getComponentType();

    @Nonnull
    private final Query<EntityStore> query = this.npcComponentType;

    @Nonnull
    private final Map<String, Ref<EntityStore>> activeCitizenRoles = new HashMap<>();

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        NPCEntity npc = store.getComponent(ref, this.npcComponentType);
        if (npc == null) {
            return;
        }

        Role role = npc.getRole();
        if (role == null) {
            return;
        }

        String roleName = role.getRoleName();
        if (roleName == null) {
            return;
        }

        if (!isTrackedCitizenRole(roleName)) {
            return;
        }

        Ref<EntityStore> existingRef = this.activeCitizenRoles.get(roleName);
        if (existingRef != null && existingRef.isValid() && !existingRef.equals(ref)) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }

        this.activeCitizenRoles.put(roleName, ref);
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        NPCEntity npc = store.getComponent(ref, this.npcComponentType);
        if (npc == null) {
            return;
        }

        Role role = npc.getRole();
        if (role == null) {
            return;
        }

        String roleName = role.getRoleName();
        if (roleName == null) {
            return;
        }

        if (!isTrackedCitizenRole(roleName)) {
            return;
        }

        Ref<EntityStore> existingRef = this.activeCitizenRoles.get(roleName);
        if (existingRef != null && existingRef.equals(ref)) {
            this.activeCitizenRoles.remove(roleName);
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    private boolean isTrackedCitizenRole(@Nonnull String roleName) {
        return roleName.startsWith("HyCitizens_") || roleName.startsWith("Citizens_");
    }
}