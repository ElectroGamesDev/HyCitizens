package com.electro.hycitizens.events;

@FunctionalInterface
public interface CitizenDeathListener {
    void onCitizenDeath(CitizenDeathEvent event);
}
