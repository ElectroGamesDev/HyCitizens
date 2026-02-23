package com.electro.hycitizens.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class PatrolPath {
    public enum LoopMode {
        LOOP,
        PING_PONG
    }

    private String name;
    private LoopMode loopMode;
    private List<PatrolWaypoint> waypoints;

    public PatrolPath(@Nonnull String name, @Nonnull LoopMode loopMode) {
        this.name = name;
        this.loopMode = loopMode;
        this.waypoints = new ArrayList<>();
    }

    @Nonnull
    public String getName() { return name; }
    public void setName(@Nonnull String name) { this.name = name; }

    @Nonnull
    public LoopMode getLoopMode() { return loopMode; }
    public void setLoopMode(@Nonnull LoopMode loopMode) { this.loopMode = loopMode; }

    @Nonnull
    public List<PatrolWaypoint> getWaypoints() { return waypoints; }
    public void setWaypoints(@Nonnull List<PatrolWaypoint> waypoints) { this.waypoints = new ArrayList<>(waypoints); }

    public void addWaypoint(@Nonnull PatrolWaypoint waypoint) {
        this.waypoints.add(waypoint);
    }
}
