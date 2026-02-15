package com.electro.hycitizens.actions;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.interactions.CitizenInteraction;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;
import java.util.List;

public class CitizenInteractionActionbase extends ActionBase {
    public CitizenInteractionActionbase(@Nonnull BuilderActionBase builderActionBase) {
        super(builderActionBase);
    }

    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        return (super.canExecute(ref, role, sensorInfo, dt, store) && role.getStateSupport().getInteractionIterationTarget() != null);
    }


    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        Ref<EntityStore> playerReference = role.getStateSupport().getInteractionIterationTarget();
        if (playerReference == null) {
            return false;
        }

        PlayerRef playerRef = store.getComponent(playerReference, PlayerRef.getComponentType());
        if (playerRef == null) {
            return false;
        }

        UUIDComponent uuidComponent = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return false;
        }

        List<CitizenData> citizens = HyCitizensPlugin.get().getCitizensManager().getAllCitizens();
        for (CitizenData citizen : citizens) {
            if (citizen.getSpawnedUUID() == null)
                continue;

            if (!citizen.getSpawnedUUID().equals(uuidComponent.getUuid()))
                continue;

            CitizenInteraction.handleInteraction(citizen, playerRef);

            break;
        }
        return true;
    }
}