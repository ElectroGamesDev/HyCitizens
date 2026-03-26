package com.electro.hycitizens.components;

import com.electro.hycitizens.HyCitizensPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CitizenNametagComponent implements Component<EntityStore> {
    public static final BuilderCodec<CitizenNametagComponent> CODEC =
            BuilderCodec.builder(CitizenNametagComponent.class, CitizenNametagComponent::new)
                    .append(new KeyedCodec<>("CitizenId", Codec.STRING),
                            (data, value) -> data.citizenId = value,
                            data -> data.citizenId)
                    .add()
                    .append(new KeyedCodec<>("LineIndex", Codec.INTEGER),
                            (data, value) -> data.lineIndex = value,
                            data -> data.lineIndex)
                    .add()
                    .build();

    private String citizenId = "";
    private int lineIndex = -1;

    public CitizenNametagComponent() {
    }

    public CitizenNametagComponent(@Nonnull String citizenId, int lineIndex) {
        this.citizenId = citizenId;
        this.lineIndex = lineIndex;
    }

    public static ComponentType<EntityStore, CitizenNametagComponent> getComponentType() {
        return HyCitizensPlugin.get().getCitizenNametagComponent();
    }

    @Nonnull
    public String getCitizenId() {
        return citizenId;
    }

    public void setCitizenId(@Nonnull String citizenId) {
        this.citizenId = citizenId;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public void setLineIndex(int lineIndex) {
        this.lineIndex = lineIndex;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new CitizenNametagComponent(citizenId, lineIndex);
    }
}
