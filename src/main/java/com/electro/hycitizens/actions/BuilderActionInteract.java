package com.electro.hycitizens.actions;

import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nullable;

public class BuilderActionInteract extends BuilderActionBase {
    @Nullable
    @Override
    public String getShortDescription() {
        return "Interact";
    }

    @Nullable
    @Override
    public String getLongDescription() {
        return "Interact";
    }

    @Nullable
    @Override
    public Action build(BuilderSupport builderSupport) {
        return new CitizenInteractionActionbase(this);
    }

    @Nullable
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }
}
