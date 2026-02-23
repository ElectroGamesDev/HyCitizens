package com.electro.hycitizens.models;

import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;

public class PatrolWaypoint {
    private double x;
    private double y;
    private double z;
    private float pauseSeconds;

    public PatrolWaypoint(double x, double y, double z, float pauseSeconds) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.pauseSeconds = pauseSeconds;
    }

    @Nonnull
    public Vector3d toVector3d() {
        return new Vector3d(x, y, z);
    }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public float getPauseSeconds() { return pauseSeconds; }
    public void setPauseSeconds(float pauseSeconds) { this.pauseSeconds = pauseSeconds; }
}
