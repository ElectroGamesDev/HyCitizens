package com.electro.hycitizens.util;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Bridges HyCitizens' internal {@link Vector3f} rotation representation with the engine's
 * {@link Rotation3f} type. As of 0.5.0-pre, transform/head rotations on the Hytale API use
 * {@code Rotation3f} (pitch/yaw/roll) rather than a plain {@code Vector3f}.
 */
public final class RotationUtil {
    private RotationUtil() {
    }

    /**
     * Converts an internal {@link Vector3f} rotation (x=pitch, y=yaw, z=roll) into a
     * {@link Rotation3f} accepted by the engine API.
     */
    public static Rotation3f toRotation(Vector3fc rotation) {
        return new Rotation3f(rotation.x(), rotation.y(), rotation.z());
    }

    /**
     * Converts an engine {@link Rotation3fc} back into the internal {@link Vector3f} representation.
     */
    public static Vector3f toVector3f(Rotation3fc rotation) {
        return new Vector3f(rotation.x(), rotation.y(), rotation.z());
    }
}
