package com.electro.hycitizens.util;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class RotationUtil {
    private RotationUtil() {}

    public static Rotation3f toRotation(Vector3fc rotation) {
        return new Rotation3f(rotation.x(), rotation.y(), rotation.z());
    }

    public static Vector3f toVector3f(Rotation3fc rotation) {
        return new Vector3f(rotation.x(), rotation.y(), rotation.z());
    }
}
