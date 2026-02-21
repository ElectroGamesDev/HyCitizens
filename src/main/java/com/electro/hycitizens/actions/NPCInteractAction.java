package com.electro.hycitizens.actions;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.interactions.CitizenInteraction;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.List;

public class NPCInteractAction extends SimpleInteraction {

    public static final BuilderCodec<NPCInteractAction> CODEC = BuilderCodec.builder(NPCInteractAction.class, NPCInteractAction::new).build();

    public NPCInteractAction() {
        super("NPCInteraction");
    }

    @Override
    public void handle(@NonNullDecl Ref<EntityStore> pRef, boolean firstRun, float time, @NonNullDecl InteractionType type, @NonNullDecl InteractionContext context) {
        if (type != InteractionType.Primary && type != InteractionType.Secondary && type != InteractionType.Use) {
            return;
        }

        PlayerRef playerRef = context.getCommandBuffer().getComponent(pRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUIDComponent uuidComponent = context.getCommandBuffer().getComponent(context.getTargetEntity(), UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }

        List<CitizenData> citizens = HyCitizensPlugin.get().getCitizensManager().getAllCitizens();
        for (CitizenData citizen : citizens) {
            if (citizen.getSpawnedUUID() == null || !citizen.getSpawnedUUID().equals(uuidComponent.getUuid())) {
                continue;
            }

            if (type == InteractionType.Use && !citizen.getFKeyInteractionEnabled()) {
                break;
            }

            CitizenInteraction.handleInteraction(citizen, playerRef);
            break;
        }
        super.handle(pRef, firstRun, time, type, context);
    }
}