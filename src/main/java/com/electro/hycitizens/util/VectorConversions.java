package com.electro.hycitizens.util;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

public final class VectorConversions {
    private VectorConversions() {}

    public static Rotation3f zeroRotation3f() {
        return new Rotation3f(0.0f, 0.0f, 0.0f);
    }

    public static Rotation3f toRotation3f(Vector3f v) {
        return new Rotation3f(v.x, v.y, v.z);
    }

    public static Vector3f toVector3f(Rotation3fc r) {
        return new Vector3f(r.x(), r.y(), r.z());
    }

    public static Vector3dc toVector3dc(Vector3f v) {
        return new Vector3d(v.x, v.y, v.z);
    }

    public static Vector3dc toVector3dc(Vector3d v) {
        return new Vector3d(v.x, v.y, v.z);
    }
}
