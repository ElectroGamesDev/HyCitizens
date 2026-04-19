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

public class CitizenNpcIdentityComponent implements Component<EntityStore> {
    public static final BuilderCodec<CitizenNpcIdentityComponent> CODEC =
            BuilderCodec.builder(CitizenNpcIdentityComponent.class, CitizenNpcIdentityComponent::new)
                    .append(new KeyedCodec<>("CitizenId", Codec.STRING),
                            (data, value) -> data.citizenId = value,
                            data -> data.citizenId)
                    .add()
                    .build();

    private String citizenId = "";

    public CitizenNpcIdentityComponent() {
    }

    public CitizenNpcIdentityComponent(@Nonnull String citizenId) {
        this.citizenId = citizenId;
    }

    public static ComponentType<EntityStore, CitizenNpcIdentityComponent> getComponentType() {
        return HyCitizensPlugin.get().getCitizenNpcIdentityComponent();
    }

    @Nonnull
    public String getCitizenId() {
        return citizenId;
    }

    public void setCitizenId(@Nonnull String citizenId) {
        this.citizenId = citizenId;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new CitizenNpcIdentityComponent(citizenId);
    }
}
