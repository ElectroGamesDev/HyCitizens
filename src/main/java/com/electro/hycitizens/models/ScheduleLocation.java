package com.electro.hycitizens.models;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

import javax.annotation.Nonnull;
import java.util.UUID;

public class ScheduleLocation {
    private String id = "";
    private String name = "";
    private UUID worldUUID;
    private Vector3d position = new Vector3d(0, 0, 0);
    private Vector3f rotation = new Vector3f(0, 0, 0);

    public ScheduleLocation() {
    }

    public ScheduleLocation(@Nonnull String id, @Nonnull String name, @Nonnull UUID worldUUID,
                            @Nonnull Vector3d position, @Nonnull Vector3f rotation) {
        this.id = id;
        this.name = name;
        this.worldUUID = worldUUID;
        this.position = position;
        this.rotation = rotation;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    public void setId(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    @Nonnull
    public UUID getWorldUUID() {
        return worldUUID;
    }

    public void setWorldUUID(@Nonnull UUID worldUUID) {
        this.worldUUID = worldUUID;
    }

    @Nonnull
    public Vector3d getPosition() {
        return position;
    }

    public void setPosition(@Nonnull Vector3d position) {
        this.position = position;
    }

    @Nonnull
    public Vector3f getRotation() {
        return rotation;
    }

    public void setRotation(@Nonnull Vector3f rotation) {
        this.rotation = rotation;
    }
}
