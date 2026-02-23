package com.electro.hycitizens.events;

import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CitizenDeathEvent {
    private final CitizenData citizen;
    private final PlayerRef killer;
    private boolean cancelled = false;

    public CitizenDeathEvent(@Nonnull CitizenData citizen, @Nullable PlayerRef killer) {
        this.citizen = citizen;
        this.killer = killer;
    }

    @Nonnull
    public CitizenData getCitizen() { return citizen; }

    @Nullable
    public PlayerRef getKiller() { return killer; }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
