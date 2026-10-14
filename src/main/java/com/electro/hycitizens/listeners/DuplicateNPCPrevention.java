package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.components.CitizenNpcIdentityComponent;
import com.electro.hycitizens.managers.CitizensManager;
import com.electro.hycitizens.managers.PatrolManager;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import org.joml.Vector3d;

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

        String roleName = npc.getRole() != null ? npc.getRole().getRoleName() : npc.getRoleName();
        String citizenKey = extractCitizenKey(ref, store, roleName, reason == AddReason.LOAD);
        if (citizenKey == null) {
            return;
        }

        String worldKey = getWorldKey(store);
        String scopedKey = worldKey + ":" + citizenKey;

        Ref<EntityStore> existingRef = this.activeCitizenRoles.get(scopedKey);
        if (existingRef != null && (!existingRef.isValid() || existingRef.getStore().getComponent(existingRef, DeathComponent.getComponentType()) != null)) {
            this.activeCitizenRoles.remove(scopedKey, existingRef);
            existingRef = null;
        }

        CitizensManager citizensManager = HyCitizensPlugin.get().getCitizensManager();
        CitizenData citizen = citizensManager != null ? citizensManager.getCitizen(citizenKey) : null;
        if (existingRef == null && citizen != null) {
            Ref<EntityStore> citizenNpcRef = citizen.getNpcRef();
            if (citizenNpcRef != null && citizenNpcRef.isValid()
                    && citizenNpcRef.getStore().getComponent(citizenNpcRef, DeathComponent.getComponentType()) == null) {
                existingRef = citizenNpcRef;
            }
        }

        if (existingRef != null && !isSameEntity(existingRef, ref)) {
            if (ref.isValid()) {
                commandBuffer.tryRemoveEntity(ref, RemoveReason.REMOVE);
            }
            return;
        }

        this.activeCitizenRoles.put(scopedKey, ref);

        if (citizensManager != null && citizen != null) {
            World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
            UUID worldUuid = (world != null && world.getWorldConfig() != null) ? world.getWorldConfig().getUuid() : null;
            boolean isPrimaryWorld = worldUuid != null && worldUuid.equals(citizen.getWorldUUID());

            if (isPrimaryWorld) {
                citizen.setNpcRef(ref);
                UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uuidComponent != null) {
                    citizen.setSpawnedUUID(uuidComponent.getUuid());
                }
                TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
                if (transformComponent != null && transformComponent.getPosition() != null) {
                    citizen.setCurrentPosition(new Vector3d(transformComponent.getPosition()));
                    if (transformComponent.getRotation() != null) {
                        citizen.setCurrentRotation(transformComponent.getRotation());
                    }
                }
            }

            commandBuffer.run(s -> {
                if (!ref.isValid()) {
                    return;
                }
                citizensManager.applyCitizenComponentsToEntity(s, ref, citizen);
                if (isPrimaryWorld && !citizensManager.isCitizenSpawning(citizen.getId())) {
                    citizensManager.restoreResolvedCitizenStateDeferredSave(citizen, ref);
                }
            });
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }

        String roleName = npc.getRole() != null ? npc.getRole().getRoleName() : npc.getRoleName();
        String citizenKey = extractCitizenKey(ref, store, roleName, false);
        if (citizenKey == null) {
            return;
        }

        String worldKey = getWorldKey(store);
        String scopedKey = worldKey + ":" + citizenKey;

        Ref<EntityStore> existingRef = this.activeCitizenRoles.get(scopedKey);
        if (existingRef != null && isSameEntity(existingRef, ref)) {
            this.activeCitizenRoles.remove(scopedKey);
        }
    }

    private String getWorldKey(@Nonnull Store<EntityStore> store) {
        if (store.getExternalData() != null && store.getExternalData().getWorld() != null) {
            World world = store.getExternalData().getWorld();
            if (world.getWorldConfig() != null && world.getWorldConfig().getUuid() != null) {
                return world.getWorldConfig().getUuid().toString();
            }
            if (world.getName() != null) {
                return world.getName();
            }
        }
        return "global";
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    private boolean isTrackedCitizenRole(@Nonnull String roleName) {
        return roleName.startsWith("HyCitizens_") || roleName.startsWith("Citizens_") || roleName.startsWith("Citizen_");
    }

    private String extractCitizenKey(Ref<EntityStore> ref, Store<EntityStore> store, String roleName, boolean allowLegacyPositionMatch) {
        if (ref != null && store != null) {
            CitizenNpcIdentityComponent identityComponent =
                    store.getComponent(ref, CitizenNpcIdentityComponent.getComponentType());
            if (identityComponent != null && !identityComponent.getCitizenId().isBlank()) {
                return identityComponent.getCitizenId();
            }

            UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComponent != null && uuidComponent.getUuid() != null) {
                CitizensManager citizensManager = HyCitizensPlugin.get().getCitizensManager();
                if (citizensManager != null) {
                    CitizenData matched = citizensManager.getCitizenBySpawnedUUID(uuidComponent.getUuid());
                    if (matched != null) {
                        return matched.getId();
                    }
                }
            }
        }

        if (roleName == null || !isTrackedCitizenRole(roleName)) {
            return null;
        }

        if (roleName.startsWith("HyCitizens_") || roleName.startsWith("Citizens_")) {
            Matcher matcher = UUID_PATTERN.matcher(roleName);
            return matcher.find() ? matcher.group() : roleName;
        }

        if (allowLegacyPositionMatch && roleName.startsWith("Citizen_") && ref != null && store != null) {
            CitizensManager citizensManager = HyCitizensPlugin.get().getCitizensManager();
            if (citizensManager != null) {
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null && transform.getPosition() != null) {
                    Vector3d pos = transform.getPosition();
                    World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
                    UUID worldUuid = (world != null && world.getWorldConfig() != null) ? world.getWorldConfig().getUuid() : null;
                    CitizenData closestCitizen = null;
                    double closestDistSq = 2.25;
                    for (CitizenData c : citizensManager.getAllCitizens()) {
                        if (worldUuid != null && !worldUuid.equals(c.getWorldUUID())) {
                            continue;
                        }
                        if (c.getSpawnedUUID() != null) {
                            continue;
                        }
                        Vector3d cPos = c.getPosition();
                        double dx = pos.x - cPos.x;
                        double dy = pos.y - cPos.y;
                        double dz = pos.z - cPos.z;
                        double distSq = dx * dx + dy * dy + dz * dz;
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closestCitizen = c;
                        }
                    }
                    if (closestCitizen != null) {
                        return closestCitizen.getId();
                    }
                }
            }
        }

        return null;
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
