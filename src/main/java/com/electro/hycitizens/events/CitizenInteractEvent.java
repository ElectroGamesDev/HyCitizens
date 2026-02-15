package com.electro.hycitizens.events;

import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class CitizenInteractEvent {

    private final CitizenData citizen;
    private final PlayerRef player;
    private boolean cancelled = false;

    public CitizenInteractEvent(CitizenData citizen, PlayerRef player) {
        this.citizen = citizen;
        this.player = player;
    }

    public CitizenData getCitizen() {
        return citizen;
    }

    public PlayerRef getPlayer() {
        return player;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}