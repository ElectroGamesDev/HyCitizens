package com.electro.hycitizens.listeners;

import com.electro.hycitizens.components.CitizenNpcIdentityComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public class DuplicateNPCPrevention extends RefSystem<EntityStore> {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    );

    @Nonnull
    private final Query<EntityStore> query = NPCEntity.getComponentType();

    @Nonnull
    private final Map<String, Ref<EntityStore>> activeCitizenRoles = new ConcurrentHashMap<>();

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
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
        if (existingRef != null && !existingRef.isValid()) {
            this.activeCitizenRoles.remove(citizenKey, existingRef);
            existingRef = null;
        }

        if (existingRef != null && !isSameEntity(existingRef, ref)) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }

        this.activeCitizenRoles.put(citizenKey, ref);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
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
        if (existingRef != null && isSameEntity(existingRef, ref)) {
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

    private String extractCitizenKey(Ref<EntityStore> ref, Store<EntityStore> store, String roleName) {
        if (ref != null && store != null) {
            CitizenNpcIdentityComponent identityComponent =
                    store.getComponent(ref, CitizenNpcIdentityComponent.getComponentType());
            if (identityComponent != null && !identityComponent.getCitizenId().isBlank()) {
                return identityComponent.getCitizenId();
            }
        }

        if (roleName == null || !isTrackedCitizenRole(roleName)) {
            return null;
        }

        Matcher matcher = UUID_PATTERN.matcher(roleName);
        return matcher.find() ? matcher.group() : roleName;
    }

    private boolean isSameEntity(@Nonnull Ref<EntityStore> first, @Nonnull Ref<EntityStore> second) {
        if (first.equals(second)) {
            return true;
        }

        UUID firstUuid = getEntityUuid(first);
        UUID secondUuid = getEntityUuid(second);
        return firstUuid != null && firstUuid.equals(secondUuid);
    }

    private UUID getEntityUuid(@Nonnull Ref<EntityStore> ref) {
        if (!ref.isValid()) {
            return null;
        }

        UUIDComponent uuidComponent = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }
}
