package com.electro.hycitizens.api.scripting;

import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScriptContext {

    private final CitizenData citizen;
    private final PlayerRef player;          // null on playerless triggers
    private final World world;
    private final Store<EntityStore> store;
    private final String triggerType;
    private final Map<String, Object> triggerArgs;
    private final Map<String, Object> sessionScope;
    private int recursionDepth = 0;

    // Control flow flags
    private boolean stopped = false;
    private boolean breakLoop = false;
    private boolean continueLoop = false;
    private boolean dryRun = false;

    public ScriptContext(CitizenData citizen, PlayerRef player, World world, Store<EntityStore> store, String triggerType, Map<String, Object> triggerArgs) {
        this.citizen = citizen;
        this.player = player;
        this.world = world;
        this.store = store;
        this.triggerType = triggerType;
        this.triggerArgs = triggerArgs != null ? new ConcurrentHashMap<>(triggerArgs) : new ConcurrentHashMap<>();
        this.sessionScope = new ConcurrentHashMap<>();
    }

    public ScriptContext(ScriptContext parent, PlayerRef overridePlayer) {
        this.citizen = parent.citizen;
        this.player = overridePlayer;
        this.world = parent.world;
        this.store = parent.store;
        this.triggerType = parent.triggerType;
        this.triggerArgs = parent.triggerArgs;
        this.recursionDepth = parent.recursionDepth;
        this.stopped = parent.stopped;
        this.breakLoop = parent.breakLoop;
        this.continueLoop = parent.continueLoop;
        this.dryRun = parent.dryRun;
        this.sessionScope = parent.sessionScope;
    }

    public CitizenData getCitizen()              { return citizen; }
    public PlayerRef getPlayer()               { return player; }   // may be null — always check
    public World getWorld()                { return world; }
    public Store<EntityStore> getStore()         { return store; }
    public String getTriggerType()          { return triggerType; }
    public Object getTriggerArg(String key) { return triggerArgs.get(key); }

    public Object getSessionVar(String name)              { return sessionScope.get(name); }
    public void setSessionVar(String name, Object value){ 
        if (value == null) {
            sessionScope.remove(name);
        } else {
            sessionScope.put(name, value);
        }
    }
    public boolean hasSessionVar(String name)              { return sessionScope.containsKey(name); }

    public int getRecursionDepth()              { return recursionDepth; }
    public void incrementRecursionDepth()        { recursionDepth++; }

    // Control flow getters/setters
    public boolean isStopped() { return stopped; }
    public void setStopped(boolean stopped) { this.stopped = stopped; }

    public boolean isBreakLoop() { return breakLoop; }
    public void setBreakLoop(boolean breakLoop) { this.breakLoop = breakLoop; }

    public boolean isContinueLoop() { return continueLoop; }
    public void setContinueLoop(boolean continueLoop) { this.continueLoop = continueLoop; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
}
