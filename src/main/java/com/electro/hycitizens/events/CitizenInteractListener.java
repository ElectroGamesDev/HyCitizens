package com.electro.hycitizens.events;

@FunctionalInterface
public interface CitizenInteractListener {
    void onCitizenInteract(CitizenInteractEvent event);
}