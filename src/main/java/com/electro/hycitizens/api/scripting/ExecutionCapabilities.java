package com.electro.hycitizens.api.scripting;

import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;

public final class ExecutionCapabilities {
    private ExecutionCapabilities() {}

    public record Actor(PlayerRef player) {}
    public record Subject(CitizenData citizen) {}
    public record WorldAccess(World world, Store<EntityStore> store) {}
    public record EventPayload(String type, Map<String, Object> values) {}
    public record VariableScopes(Map<String, Object> session) {}
}
