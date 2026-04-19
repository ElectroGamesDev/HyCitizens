package com.electro.hycitizens.listeners;

import com.electro.hycitizens.components.CitizenBindingComponent;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

public class DuplicateNPCPrevention extends RefSystem<EntityStore> {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    );

    @Nonnull
    private final ComponentType<EntityStore, NPCEntity> npcComponentType = NPCEntity.getComponentType();
    @Nonnull
    private final ComponentType<EntityStore, CitizenBindingComponent> citizenBindingComponentType = CitizenBindingComponent.getComponentType();

    @Nonnull
    private final Query<EntityStore> query = this.npcComponentType;

    @Nonnull
    private final Map<String, Ref<EntityStore>> activeCitizenRoles = new HashMap<>();

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NPCEntity npc = store.getComponent(ref, this.npcComponentType);
        if (npc == null) {
            return;
        }

        Role role = npc.getRole();
        if (role == null) {
            return;
        }

        String roleName = role.getRoleName();
        String citizenKey = extractCitizenKey(ref, store, roleName);
        if (citizenKey == null) {
            return;
        }

        Ref<EntityStore> existingRef = this.activeCitizenRoles.get(citizenKey);
        if (existingRef != null && existingRef.isValid() && !existingRef.equals(ref)) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }

        this.activeCitizenRoles.put(citizenKey, ref);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NPCEntity npc = store.getComponent(ref, this.npcComponentType);
        if (npc == null) {
            return;
        }

        Role role = npc.getRole();
        if (role == null) {
            return;
        }

        String roleName = role.getRoleName();
        String citizenKey = extractCitizenKey(ref, store, roleName);
        if (citizenKey == null) {
            return;
        }

        Ref<EntityStore> existingRef = this.activeCitizenRoles.get(citizenKey);
        if (existingRef != null && existingRef.equals(ref)) {
            this.activeCitizenRoles.remove(citizenKey);
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

    private String extractCitizenKey(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull Store<EntityStore> store,
                                     String roleName) {
        CitizenBindingComponent bindingComponent = store.getComponent(ref, this.citizenBindingComponentType);
        if (bindingComponent != null && !bindingComponent.getCitizenId().isBlank()) {
            return bindingComponent.getCitizenId();
        }

        if (roleName == null || !isTrackedCitizenRole(roleName)) {
            return null;
        }

        Matcher matcher = UUID_PATTERN.matcher(roleName);
        return matcher.find() ? matcher.group() : roleName;
    }
}
