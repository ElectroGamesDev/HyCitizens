package com.electro.hycitizens.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackSystems;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
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
        return Query.and(NPCEntity.getComponentType(), KnockbackComponent.getComponentType(), Velocity.getComponentType(), Query.not(Player.getComponentType()));
    }

    @Override
    public void tick(float dt, int index, @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk, @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> commandBuffer) {
        KnockbackComponent knockbackComponent = archetypeChunk.getComponent(index, KnockbackComponent.getComponentType());

        assert knockbackComponent != null;

        knockbackComponent.setDuration(0);
        Velocity velocityComponent = archetypeChunk.getComponent(index, Velocity.getComponentType());

        assert velocityComponent != null;

        velocityComponent.getInstructions().clear();

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        commandBuffer.tryRemoveComponent(ref, KnockbackComponent.getComponentType());
    }
}
