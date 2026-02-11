package com.electro.hycitizens.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackSystems;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Project HyCitizens
 * Class NpcKnockbackRemoverSystem
 *
 * @author Jimmy Badaire (vSKAH) - 11/02/2026
 * @version 1.0
 * @since 1.0.0-SNAPSHOT
 */
public class NpcKnockbackRemoverSystem extends KnockbackSystems.ApplyKnockback {

    @NonNullDecl
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(KnockbackComponent.getComponentType(),NPCEntity.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk, @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        commandBuffer.removeComponent(ref, KnockbackComponent.getComponentType());
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }
}
