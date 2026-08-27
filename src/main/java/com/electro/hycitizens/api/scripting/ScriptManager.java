package com.electro.hycitizens.api.scripting;

import com.electro.hycitizens.HyCitizensPlugin;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.managers.DialogueManager;
import com.electro.hycitizens.api.dialogue.DialogOverride;
import com.electro.hycitizens.api.dialogue.DialogPatch;
import com.electro.hycitizens.api.dialogue.IDialogue;
import com.electro.hycitizens.api.dialogue.PlayerDialogState;
import com.electro.hycitizens.models.FactionConfig;
import com.electro.hycitizens.models.MovementBehavior;
import com.electro.hycitizens.api.scripting.ScriptAction.Branch;
import com.electro.hycitizens.interactions.CitizenInteraction;
import com.electro.hycitizens.util.CommandExecutionUtil;
import org.joml.Vector3d;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.Selector;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;

import java.awt.Color;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;
import com.electro.hycitizens.util.RotationUtil;
import com.hypixel.hytale.component.RemoveReason;

public class ScriptManager {
    private static ScriptManager instance;

    private final Map<String, ScriptConditionHandler> conditions = new ConcurrentHashMap<>();
    private final Map<String, ScriptActionHandler> actions = new ConcurrentHashMap<>();
    private final Map<String, ConditionTypeDescriptor> conditionDescriptors = new ConcurrentHashMap<>();
    private final Map<String, ActionTypeDescriptor> actionDescriptors = new ConcurrentHashMap<>();
    private final Map<String, TriggerTypeDescriptor> triggerDescriptors = new ConcurrentHashMap<>();
    private final Map<String, VariableProviderDescriptor> variableDescriptors = new ConcurrentHashMap<>();
    private final Map<String, EventTypeDescriptor> eventDescriptors = new ConcurrentHashMap<>();

    // Timer tracking: citizenId -> (timerName -> Future)
    private final Map<String, Map<String, ScheduledFuture<?>>> activeTimers = new ConcurrentHashMap<>();
    
    // Proximity state: citizenId -> (playerUuid -> wasInside)
    private final Map<String, Map<UUID, Boolean>> proximityStates = new ConcurrentHashMap<>();

    private static class FollowPlayerState {
        final UUID playerUuid;
        final double speed;
        final double minDistance;
        final double maxDistance;
        final boolean hardStopOnMaxDistance;

        FollowPlayerState(UUID playerUuid, double speed, double minDistance, double maxDistance, boolean hardStopOnMaxDistance) {
            this.playerUuid = playerUuid;
            this.speed = speed;
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
            this.hardStopOnMaxDistance = hardStopOnMaxDistance;
        }
    }
    private final Map<String, FollowPlayerState> followingPlayers = new ConcurrentHashMap<>();

    private void stopFollowingPlayer(CitizenData citizen, String reason, PlayerRef player, Store<EntityStore> store) {
        if (followingPlayers.remove(citizen.getId()) != null) {
            Map<String, Object> args = new HashMap<>();
            args.put("reason", reason);
            
            Store<EntityStore> entityStore = store;
            if (entityStore == null) {
                World world = Universe.get().getWorld(citizen.getWorldUUID());
                if (world != null) {
                    entityStore = world.getEntityStore().getStore();
                }
            }
            
            fireTrigger(citizen, "ON_STOP_FOLLOWING", args, player, entityStore);
        }
    }

    public void handlePlayerDisconnect(PlayerRef playerRef) {
        if (playerRef == null) return;
        handlePlayerDisconnect(playerRef.getUuid(), playerRef);
    }

    public void handlePlayerDisconnect(UUID playerUuid) {
        if (playerUuid == null) return;
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        handlePlayerDisconnect(playerUuid, playerRef);
    }

    public void handlePlayerDisconnect(UUID playerUuid, PlayerRef playerRef) {
        if (playerUuid == null) return;

        // Clean up any following players associated with this player
        List<String> followingCitizenIds = new ArrayList<>();
        for (Map.Entry<String, FollowPlayerState> entry : followingPlayers.entrySet()) {
            if (playerUuid.equals(entry.getValue().playerUuid)) {
                followingCitizenIds.add(entry.getKey());
            }
        }

        for (String citizenId : followingCitizenIds) {
            CitizenData citizen = HyCitizensPlugin.get().getCitizensManager().getCitizen(citizenId);
            if (citizen != null) {
                World world = Universe.get().getWorld(citizen.getWorldUUID());
                Store<EntityStore> store = world != null ? world.getEntityStore().getStore() : null;
                stopFollowingPlayer(citizen, "PLAYER_DISCONNECT", playerRef, store);
                MovementBehavior mb = new MovementBehavior("IDLE", 2.0f, 0.0f, 0.0f, 0.0f);
                citizen.setMovementBehavior(mb);
                HyCitizensPlugin.get().getCitizensManager().stopCitizenMovement(citizenId);
                HyCitizensPlugin.get().getCitizensManager().updateCitizenRoleImmediately(citizen);
            } else {
                followingPlayers.remove(citizenId);
            }
        }

        // Clean up proximity tracking for this player
        proximityStates.values().forEach(map -> map.remove(playerUuid));

        // Fire ON_PLAYER_QUIT / ON_PLAYER_DISCONNECT trigger on citizens
        for (CitizenData citizen : HyCitizensPlugin.get().getCitizensManager().getAllCitizens()) {
            if (citizen == null) continue;
            World world = Universe.get().getWorld(citizen.getWorldUUID());
            if (world != null) {
                Store<EntityStore> store = world.getEntityStore().getStore();
                fireTrigger(citizen, "ON_PLAYER_QUIT", Map.of("player_uuid", playerUuid.toString()), playerRef, store);
                fireTrigger(citizen, "ON_PLAYER_DISCONNECT", Map.of("player_uuid", playerUuid.toString()), playerRef, store);
            }
        }
    }

    public void handlePlayerConnect(PlayerRef playerRef) {
        if (playerRef == null) return;
        for (CitizenData citizen : HyCitizensPlugin.get().getCitizensManager().getAllCitizens()) {
            if (citizen == null) continue;
            World world = Universe.get().getWorld(citizen.getWorldUUID());
            if (world != null && playerRef.getWorldUuid() != null && playerRef.getWorldUuid().equals(citizen.getWorldUUID())) {
                Store<EntityStore> store = world.getEntityStore().getStore();
                fireTrigger(citizen, "ON_PLAYER_JOIN", Map.of("player_uuid", playerRef.getUuid().toString()), playerRef, store);
                fireTrigger(citizen, "ON_PLAYER_CONNECT", Map.of("player_uuid", playerRef.getUuid().toString()), playerRef, store);
            }
        }
    }

    private ScheduledExecutorService proximityScheduler;
    private ScheduledExecutorService tickScheduler;
    private final Gson gson = new Gson();
    private final ScriptTemplateCompiler templateCompiler = new ScriptTemplateCompiler(gson);
    private volatile ScriptValidationMode validationMode = ScriptValidationMode.STRICT;
    private final Deque<ScriptExecutionTrace> recentExecutionTraces = new ConcurrentLinkedDeque<>();
    private static final int MAX_EXECUTION_TRACES = 200;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final Semaphore httpConcurrency = new Semaphore(4);
    private final AtomicLong httpRateWindow = new AtomicLong();
    private final AtomicInteger httpRateCount = new AtomicInteger();
    private final Map<String, Set<String>> runningScripts = new ConcurrentHashMap<>();

    public static ScriptManager get() {
        if (instance == null) {
            instance = new ScriptManager();
        }
        return instance;
    }

    private ScriptManager() {
        registerBuiltins();
        registerBuiltinDescriptors();
    }

    public void init() {
        // Start proximity throttling scheduler
        proximityScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "hycitizens-proximity-tracker");
            thread.setDaemon(true);
            return thread;
        });
        proximityScheduler.scheduleAtFixedRate(this::checkProximities, 500, 500, TimeUnit.MILLISECONDS);

        // Start tick scheduler
        tickScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "hycitizens-tick-tracker");
            thread.setDaemon(true);
            return thread;
        });
        tickScheduler.scheduleAtFixedRate(this::checkTicks, 500, 500, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (proximityScheduler != null) {
            proximityScheduler.shutdown();
        }
        if (tickScheduler != null) {
            tickScheduler.shutdown();
        }
        // Cancel all active timers
        for (Map<String, ScheduledFuture<?>> citizenTimers : activeTimers.values()) {
            for (ScheduledFuture<?> future : citizenTimers.values()) {
                future.cancel(true);
            }
        }
        activeTimers.clear();
    }

    public float getFollowPlayerMinDistance(String citizenId) {
        FollowPlayerState state = followingPlayers.get(citizenId);
        return state != null ? (float) state.minDistance : 2.0f;
    }

    public void registerCondition(ScriptConditionHandler handler) {
        registerCondition(handler, new ConditionTypeDescriptor(
                handler.getType().toUpperCase(), 1, "hycitizens", "General", "",
                Map.of("type", "object"), Map.of()
        ));
    }

    public void registerAction(ScriptActionHandler handler) {
        registerAction(handler, new ActionTypeDescriptor(
                handler.getType().toUpperCase(), 1, "hycitizens", "General", "",
                Map.of("type", "object"), Map.of(), false
        ));
    }

    public synchronized void registerCondition(ScriptConditionHandler handler, ConditionTypeDescriptor descriptor) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(descriptor, "descriptor");
        String id = handler.getType().toUpperCase(Locale.ROOT);
        if (conditions.containsKey(id) || conditionDescriptors.containsKey(id)) {
            throw new IllegalStateException("Duplicate script condition registration: " + id);
        }
        conditions.put(id, handler);
        conditionDescriptors.put(id, descriptor);
    }

    public synchronized void registerAction(ScriptActionHandler handler, ActionTypeDescriptor descriptor) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(descriptor, "descriptor");
        String id = handler.getType().toUpperCase(Locale.ROOT);
        if (actions.containsKey(id) || actionDescriptors.containsKey(id)) {
            throw new IllegalStateException("Duplicate script action registration: " + id);
        }
        actions.put(id, handler);
        actionDescriptors.put(id, descriptor);
    }

    public JsonObject getCapabilitySchema() {
        JsonObject root = new JsonObject();
        root.addProperty("documentType", "hycitizens:scripting-capabilities");
        root.addProperty("schemaVersion", 1);
        JsonArray actionArray = new JsonArray();
        actionDescriptors.values().stream().sorted(Comparator.comparing(ActionTypeDescriptor::id))
                .forEach(descriptor -> actionArray.add(gson.toJsonTree(descriptor)));
        JsonArray conditionArray = new JsonArray();
        conditionDescriptors.values().stream().sorted(Comparator.comparing(ConditionTypeDescriptor::id))
                .forEach(descriptor -> conditionArray.add(gson.toJsonTree(descriptor)));
        root.add("actions", actionArray);
        root.add("conditions", conditionArray);
        root.add("triggers", gson.toJsonTree(triggerDescriptors.values()));
        root.add("variables", gson.toJsonTree(variableDescriptors.values()));
        root.add("events", gson.toJsonTree(eventDescriptors.values()));
        return root;
    }

    public void registerTriggerDescriptor(TriggerTypeDescriptor descriptor) {
        if (triggerDescriptors.putIfAbsent(descriptor.id(), descriptor) != null)
            throw new IllegalStateException("Duplicate trigger descriptor: " + descriptor.id());
    }

    public void registerVariableProviderDescriptor(VariableProviderDescriptor descriptor) {
        if (variableDescriptors.putIfAbsent(descriptor.id(), descriptor) != null)
            throw new IllegalStateException("Duplicate variable descriptor: " + descriptor.id());
    }

    public void registerEventDescriptor(EventTypeDescriptor descriptor) {
        if (eventDescriptors.putIfAbsent(descriptor.id(), descriptor) != null)
            throw new IllegalStateException("Duplicate event descriptor: " + descriptor.id());
    }

    private void registerBuiltinDescriptors() {
        for (String id : List.of("STOP_SCRIPT", "BREAK_LOOP", "CONTINUE_LOOP")) {
            actionDescriptors.putIfAbsent(id, new ActionTypeDescriptor(
                    id, 1, "hycitizens", "Control Flow", id,
                    Map.of("type", "object", "additionalProperties", true), Map.of(), false
            ));
        }
        for (String id : List.of("AND", "OR", "NOT")) {
            conditionDescriptors.putIfAbsent(id, new ConditionTypeDescriptor(
                    id, 1, "hycitizens", "Core Logic", id,
                    Map.of("type", "object", "additionalProperties", true), Map.of()
            ));
        }
        for (String id : List.of("ON_INTERACT", "ON_LEFT_CLICK", "ON_FIRST_INTERACT", "ON_PROXIMITY_ENTER",
                "ON_PROXIMITY_EXIT", "ON_DAMAGE", "ON_DEATH", "ON_SPAWN", "ON_DESPAWN", "ON_TICK",
                "ON_TIMER", "ON_CUSTOM", "ON_COMMAND", "ON_SIGNAL", "ON_SCHEDULE_CHANGE", "ON_STOP_FOLLOWING",
                "ON_PLAYER_QUIT", "ON_PLAYER_DISCONNECT", "ON_PLAYER_JOIN", "ON_PLAYER_CONNECT",
                "ON_COMBAT_START", "ON_COMBAT_END", "ON_HEALTH_THRESHOLD", "ON_RESPAWN")) {
            registerTriggerDescriptor(new TriggerTypeDescriptor(id, 1, "hycitizens", Map.of("type", "object"), id));
        }
        for (String id : List.of("SESSION", "PLAYER", "CITIZEN", "GLOBAL")) {
            registerVariableProviderDescriptor(new VariableProviderDescriptor(
                    id, 1, "hycitizens", Map.of("type", "object"), id + " variable scope"
            ));
        }
        for (String id : List.of("DIALOG_STARTED", "DIALOG_COMPLETED", "DIALOG_NODE_ENTERED",
                "DIALOG_RESPONSE_SELECTED", "SCRIPT_EXECUTION_FAILED")) {
            registerEventDescriptor(new EventTypeDescriptor(id, 1, "hycitizens", Map.of("type", "object"), id));
        }
    }

    // Template Compilation
    public ScriptBlock compileScript(ScriptBlock rawBlock) {
        if (rawBlock.getTemplateId() == null || rawBlock.getTemplateId().isEmpty()) {
            return rawBlock;
        }

        ScriptBlock template = loadTemplate(rawBlock.getTemplateId());
        if (template == null) {
            getLogger().atWarning().log("Failed to resolve script template: " + rawBlock.getTemplateId());
            return rawBlock;
        }

        try {
            return templateCompiler.compile(template, rawBlock);
        } catch (IllegalArgumentException error) {
            getLogger().atWarning().log("Invalid script template parameters for "
                    + rawBlock.getTemplateId() + ": " + error.getMessage());
            throw error;
        }
    }

    private ScriptBlock loadTemplate(String templateId) {
        String resourcePath = "/Server/Citizens/ScriptTemplates/" + templateId + ".json";
        InputStream in = HyCitizensPlugin.class.getResourceAsStream(resourcePath);
        if (in != null) {
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return gson.fromJson(reader, ScriptBlock.class);
            } catch (Exception e) {
                getLogger().atSevere().log("Failed to read template resource: " + templateId + " - " + e.getMessage());
            }
        }
        return null;
    }

    // Core Execution
    public void fireTrigger(CitizenData citizen, String triggerType, Map<String, Object> triggerArgs, PlayerRef player, Store<EntityStore> store) {
        fireTrigger(citizen, triggerType, triggerArgs, player, store, ScriptConcurrencyMode.SEQUENTIAL);
    }

    public void fireTrigger(
            CitizenData citizen,
            String triggerType,
            Map<String, Object> triggerArgs,
            PlayerRef player,
            Store<EntityStore> store,
            ScriptConcurrencyMode concurrencyMode
    ) {
        if (citizen == null) return;

        if ("ON_SPAWN".equalsIgnoreCase(triggerType)) {
            registerAllTimers(citizen);
        } else if ("ON_DESPAWN".equalsIgnoreCase(triggerType)) {
            unregisterAllTimers(citizen);
        }
        
        // Reflection or direct scripts retrieval (once CitizenData compiled)
        List<ScriptBlock> rawScripts = getCitizenScripts(citizen);
        if (rawScripts == null || rawScripts.isEmpty()) return;

        Map<String, Object> safeTriggerArgs = triggerArgs != null ? triggerArgs : Collections.emptyMap();
        List<ScriptBlock> compiledScripts = new ArrayList<>();
        for (ScriptBlock raw : rawScripts) {
            if (raw.isEnabled() && matchesTriggerWithParameters(citizen, raw, triggerType, safeTriggerArgs)) {
                try {
                    compiledScripts.add(compileScript(raw));
                } catch (IllegalArgumentException error) {
                    getLogger().atWarning().log("[HyCitizens] Skipping invalid script " + raw.getId()
                            + ": " + error.getMessage());
                }
            }
        }

        // Sort by priority descending
        compiledScripts.sort(Comparator.comparingInt(ScriptBlock::getPriority).reversed());

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) return;
        Store<EntityStore> contextStore = store != null ? store : world.getEntityStore().getStore();

        ScriptContext triggerContext = new ScriptContext(citizen, player, world, contextStore, triggerType, safeTriggerArgs);
        if (concurrencyMode == ScriptConcurrencyMode.PARALLEL) {
            CompletableFuture<?>[] executions = compiledScripts.stream()
                    .map(script -> executeScriptResult(script, triggerContext))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(executions).exceptionally(error -> {
                getLogger().atWarning().log("[HyCitizens] Parallel trigger execution failed for "
                        + triggerType + ": " + error.getMessage());
                return null;
            });
            return;
        }
        CompletableFuture<Void> plan = CompletableFuture.completedFuture(null);
        for (ScriptBlock script : compiledScripts) {
            plan = plan.thenCompose(ignored -> {
                if (triggerContext.isStopped()) {
                    return CompletableFuture.completedFuture(null);
                }
                return executeScriptResult(script, triggerContext).thenApply(result -> null);
            });
        }
        plan.exceptionally(error -> {
            getLogger().atWarning().log("[HyCitizens] Trigger execution failed for " + triggerType + ": " + error.getMessage());
            return null;
        });
    }

    private boolean matchesTriggerWithParameters(CitizenData citizen, ScriptBlock script, String triggerType, Map<String, Object> triggerArgs) {
        if (script == null || triggerType == null) return false;

        boolean matched = script.matchesTrigger(triggerType);
        boolean matchedLeftClickAlias = false;
        if (!matched && "ON_INTERACT".equalsIgnoreCase(triggerType)) {
            Object source = triggerArgs != null ? triggerArgs.get("interaction_source") : null;
            if (source != null && "LEFT_CLICK".equalsIgnoreCase(source.toString())) {
                matched = script.matchesTrigger("ON_LEFT_CLICK");
                matchedLeftClickAlias = matched;
            }
        }
        if (!matched) return false;

        Map<String, Object> params = script.getTriggerParameters();
        if (params == null || params.isEmpty()) return true;

        if ("ON_INTERACT".equalsIgnoreCase(triggerType) || "ON_FIRST_INTERACT".equalsIgnoreCase(triggerType)) {
            if (matchedLeftClickAlias) return true;
            return matchesInteractionSource(params.get("source"), triggerArgs != null ? triggerArgs.get("interaction_source") : null);
        }
        if ("ON_TIMER".equalsIgnoreCase(triggerType)) {
            return matchesOptionalString(params.get("name"), triggerArgs != null ? triggerArgs.get("timer_name") : null);
        }
        if ("ON_CUSTOM".equalsIgnoreCase(triggerType)) {
            return matchesOptionalString(params.get("event_name"), triggerArgs != null ? triggerArgs.get("event_name") : null);
        }
        if ("ON_SIGNAL".equalsIgnoreCase(triggerType)) {
            return matchesOptionalString(params.get("signal_name"), triggerArgs != null ? triggerArgs.get("signal_name") : null);
        }
        if ("ON_DAMAGE".equalsIgnoreCase(triggerType)) {
            Object minAmount = params.get("min_amount");
            if (minAmount != null) {
                Object amount = triggerArgs != null ? triggerArgs.get("damage_amount") : null;
                return getAsDouble(amount, 0.0) >= getAsDouble(minAmount, 0.0);
            }
        }
        if ("ON_COMMAND".equalsIgnoreCase(triggerType)) {
            return matchesOptionalString(params.get("command"), triggerArgs != null ? triggerArgs.get("command") : null);
        }
        if ("ON_DEATH".equalsIgnoreCase(triggerType)) {
            return true;
        }
        if ("ON_HEALTH_THRESHOLD".equalsIgnoreCase(triggerType)) {
            Object thresholdPercent = params.get("threshold_percent");
            Object direction = params.get("Direction");

            if (thresholdPercent == null || direction == null)
                return false;

            Ref<EntityStore> npcRef = citizen.getNpcRef();
            if (npcRef == null || !npcRef.isValid()) {
                return false;
            }

            EntityStatMap statMap = npcRef.getStore().getComponent(npcRef, EntityStatsModule.get().getEntityStatMapComponentType());
            if (statMap == null) {
                return false;
            }

            EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
            if (healthStat == null) {
                return false;
            }

            float currentHealth = healthStat.get();
            float maxHealth = healthStat.getMax();
            float thresholdHealth = maxHealth * ((float)getAsDouble(thresholdPercent, 0.0) / 100);

            return "BELOW".equals(direction)
                    ? currentHealth < thresholdHealth
                    : currentHealth > thresholdHealth;
        }
        if ("ON_RESPAWN".equalsIgnoreCase(triggerType)) {
            return true;
        }
        if ("ON_SCHEDULE_CHANGE".equalsIgnoreCase(triggerType)) {
            String requiredEntryId = (String) params.get("entry_id");
            if (requiredEntryId != null && !requiredEntryId.isEmpty()) {
                String entryId = (String) triggerArgs.get("entry_id");
                return requiredEntryId.equals(entryId);
            }

            return true;
        }

        if ("ON_STOP_FOLLOWING".equalsIgnoreCase(triggerType)) {
            Object reasonObj = triggerArgs != null ? triggerArgs.get("reason") : null;
            if (reasonObj == null) {
                return false;
            }
            String reason = reasonObj.toString();
            
            if ("COMMAND".equals(reason)) {
                return getBooleanParam(params.get("trigger_on_command"), true);
            }
            if ("PLAYER_DISCONNECT".equals(reason)) {
                return getBooleanParam(params.get("trigger_on_disconnect"), true);
            }
            if ("MAX_DISTANCE".equals(reason)) {
                return getBooleanParam(params.get("trigger_on_max_distance"), true);
            }
            if ("WANDER".equals(reason)) {
                return getBooleanParam(params.get("trigger_on_wander"), true);
            }
            if ("PATROL".equals(reason)) {
                return getBooleanParam(params.get("trigger_on_patrol"), true);
            }
            return true;
        }

        return true;
    }

    private static boolean getBooleanParam(Object val, boolean defaultValue) {
        if (val == null) return defaultValue;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return "true".equalsIgnoreCase((String) val);
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        return defaultValue;
    }

    private boolean matchesInteractionSource(Object expectedSource, Object actualSource) {
        if (expectedSource == null) return true;
        String expected = expectedSource.toString();
        if (expected.isEmpty() || "BOTH".equalsIgnoreCase(expected)) return true;
        return actualSource != null && expected.equalsIgnoreCase(actualSource.toString());
    }

    private boolean matchesOptionalString(Object expectedValue, Object actualValue) {
        if (expectedValue == null) return true;
        String expected = expectedValue.toString();
        if (expected.isEmpty()) return true;
        return actualValue != null && expected.equalsIgnoreCase(actualValue.toString());
    }

    public void executeScript(ScriptBlock script, ScriptContext context) {
        executeScriptResult(script, context);
    }

    public CompletableFuture<ScriptExecutionResult> executeScriptResult(ScriptBlock script, ScriptContext context) {
        // Evaluate conditions AND-gate
        for (ScriptCondition cond : script.getConditions()) {
            if (!evaluateCondition(cond, context)) {
                context.trace("script:" + script.getId() + ":guard-failed");
                return CompletableFuture.completedFuture(ScriptExecutionResult.success(context));
            }
        }

        String citizenId = context.getCitizen() != null ? context.getCitizen().getId() : "_generic";
        Set<String> citizenRunning = runningScripts.computeIfAbsent(citizenId, k -> ConcurrentHashMap.newKeySet());
        citizenRunning.add(script.getId());
        context.trace("script:" + script.getId() + ":start");

        return executeActionsResult(script.getActions(), context).whenComplete((result, error) -> {
            citizenRunning.remove(script.getId());
            context.trace("script:" + script.getId() + ":end");
            ScriptExecutionResult completed = result;
            if (completed == null) {
                Throwable cause = error != null ? error : new IllegalStateException("Script execution ended without a result");
                completed = ScriptExecutionResult.failure(context, "EXECUTION", cause);
            }
            recentExecutionTraces.addFirst(new ScriptExecutionTrace(
                    script.getId(), System.currentTimeMillis(), completed.success(),
                    completed.failures(), completed.trace()
            ));
            while (recentExecutionTraces.size() > MAX_EXECUTION_TRACES) recentExecutionTraces.pollLast();
        });
    }

    public CompletableFuture<Void> executeActions(List<ScriptAction> actionsList, ScriptContext context) {
        return executeActionsResult(actionsList, context).thenCompose(result -> {
            if (!result.success() && !result.failures().isEmpty()) {
                return CompletableFuture.failedFuture(result.failures().getFirst().cause());
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<ScriptExecutionResult> executeActionsResult(List<ScriptAction> actionsList, ScriptContext context) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (ScriptAction action : actionsList) {
            future = future.thenCompose(v -> {
                if (context.isStopped() || context.isBreakLoop() || context.isContinueLoop()) {
                    return CompletableFuture.completedFuture(null);
                }
                return executeAction(action, context);
            });
        }
        return future.handle((ignored, error) -> {
            if (error == null) return ScriptExecutionResult.success(context);
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            return ScriptExecutionResult.failure(context, "ACTION_CHAIN", cause);
        });
    }

    public boolean evaluateCondition(ScriptCondition cond, ScriptContext context) {
        String type = cond.getType().toUpperCase();
        if ("AND".equals(type)) {
            for (ScriptCondition child : cond.getConditions()) {
                if (!evaluateCondition(child, context)) return false;
            }
            return true;
        } else if ("OR".equals(type)) {
            for (ScriptCondition child : cond.getConditions()) {
                if (evaluateCondition(child, context)) return true;
            }
            return false;
        } else if ("NOT".equals(type)) {
            return cond.getCondition() == null || !evaluateCondition(cond.getCondition(), context);
        }

        ScriptConditionHandler handler = conditions.get(type);
        if (handler == null) {
            getLogger().atWarning().log("Unknown script condition type: " + type);
            context.trace("condition:" + type + ":unknown");
            if (validationMode == ScriptValidationMode.STRICT) {
                throw new IllegalArgumentException("Unknown script condition type: " + type);
            }
            return false;
        }

        // Resolve parameters
        Map<String, Object> resolvedParams = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : cond.getParameters().entrySet()) {
            Object resolvedVal = ScriptExpressionEvaluator.evaluateParameter(entry.getValue(), context);
            resolvedParams.put(entry.getKey(), coerceNumericParam(entry.getKey(), resolvedVal));
        }
        ConditionTypeDescriptor conditionDescriptor = conditionDescriptors.get(type);
        if (conditionDescriptor != null) {
            List<String> errors = conditionDescriptor.validate(resolvedParams);
            if (!errors.isEmpty()) {
                getLogger().atWarning().log("Invalid script condition " + type + ": " + String.join(", ", errors));
                return false;
            }
        }

        return handler.evaluate(context, resolvedParams);
    }

    public CompletableFuture<Void> executeAction(ScriptAction action, ScriptContext context) {
        String type = action.getType().toUpperCase();
        // Support loop control actions directly in engine
        if ("BREAK_LOOP".equals(type)) {
            context.setBreakLoop(true);
            return CompletableFuture.completedFuture(null);
        } else if ("CONTINUE_LOOP".equals(type)) {
            context.setContinueLoop(true);
            return CompletableFuture.completedFuture(null);
        } else if ("STOP_SCRIPT".equals(type)) {
            context.setStopped(true);
            return CompletableFuture.completedFuture(null);
        }

        ScriptActionHandler handler = actions.get(type);
        if (handler == null) {
            getLogger().atWarning().log("Unknown script action type: " + type);
            context.trace("action:" + type + ":unknown");
            if (validationMode == ScriptValidationMode.PERMISSIVE) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown script action type: " + type));
        }

        // Resolve target & parameters
        String resolvedTarget = ScriptExpressionEvaluator.resolve(action.getTarget(), context);
        Double resolvedRadius = action.getTargetRadius();

        // Resolve parameters
        Map<String, Object> resolvedParams = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : action.getParameters().entrySet()) {
            Object resolvedVal = ScriptExpressionEvaluator.evaluateParameter(entry.getValue(), context);
            resolvedParams.put(entry.getKey(), coerceNumericParam(entry.getKey(), resolvedVal));
        }
        ActionTypeDescriptor actionDescriptor = actionDescriptors.get(type);
        if (actionDescriptor != null) {
            List<String> errors = actionDescriptor.validate(resolvedParams);
            if (!errors.isEmpty()) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Invalid action " + type + ": " + String.join(", ", errors))
                );
            }
        }

        // Handle target overriding
        ScriptContext targetContext = context;
        if (resolvedTarget != null && !resolvedTarget.isEmpty()) {
            targetContext = createTargetedContext(context, resolvedTarget, resolvedRadius);
        }

        if (action.getCondition() != null) {
            resolvedParams.put("condition", action.getCondition());
        }
        if (action.getActions() != null && !action.getActions().isEmpty()) {
            resolvedParams.put("_sub_actions", action.getActions());
        }
        if (action.getTrueActions() != null && !action.getTrueActions().isEmpty()) {
            resolvedParams.put("_true_actions", action.getTrueActions());
        }
        if (action.getFalseActions() != null && !action.getFalseActions().isEmpty()) {
            resolvedParams.put("_false_actions", action.getFalseActions());
        }
        if (action.getBranches() != null && !action.getBranches().isEmpty()) {
            resolvedParams.put("_branches", action.getBranches());
        }

        try {
            context.trace("action:" + type);
            return handler.execute(targetContext, resolvedParams).exceptionallyCompose(error ->
                    CompletableFuture.failedFuture(error instanceof CompletionException && error.getCause() != null
                            ? error.getCause() : error)
            );
        } catch (Exception e) {
            getLogger().atSevere().log("Error executing script action: " + type + " - " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private ScriptContext createTargetedContext(ScriptContext context, String target, Double radius) {
        PlayerRef targetPlayer = context.getPlayer();
        if ("ALL_IN_RADIUS".equalsIgnoreCase(target) || "NEAREST_PLAYER".equalsIgnoreCase(target)) {
            double r = radius != null ? radius : 10.0;
            PlayerRef nearest = getNearestPlayer(context, r);
            if (nearest != null) targetPlayer = nearest;
        } else if ("ALL".equalsIgnoreCase(target)) {
            // ALL matches first active player, action handles broadcast
        } else {
            // Stored player UUID
            try {
                UUID uuid = UUID.fromString(target);
                PlayerRef p = Universe.get().getPlayer(uuid);
                if (p != null) targetPlayer = p;
            } catch (Exception ignored) {}
        }
        return new ScriptContext(context, targetPlayer);
    }

    private PlayerRef getNearestPlayer(ScriptContext context, double radius) {
        CitizenData citizen = context.getCitizen();
        World world = context.getWorld();
        if (citizen == null || world == null) return null;

        Vector3d cPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        PlayerRef nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (PlayerRef player : world.getPlayerRefs()) {
            double dist = player.getTransform().getPosition().distance(cPos);
            if (dist <= radius && dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }
        return nearest;
    }

    //  Proximity and Ticks Throttled Processing
    private void checkProximities() {
        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> checkProximitiesOnWorld(world));
        }
    }

    private void checkProximitiesOnWorld(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<CitizenData> worldCitizens = HyCitizensPlugin.get().getCitizensManager().getAllCitizens().stream()
                .filter(citizen -> Universe.get().getWorld(citizen.getWorldUUID()) == world)
                .toList();

        for (Map.Entry<String, FollowPlayerState> entry : followingPlayers.entrySet()) {
            String citizenId = entry.getKey();
            FollowPlayerState followState = entry.getValue();
            CitizenData citizen = HyCitizensPlugin.get().getCitizensManager().getCitizen(citizenId);
            if (citizen == null) {
                followingPlayers.remove(citizenId);
                continue;
            }
            if (Universe.get().getWorld(citizen.getWorldUUID()) != world) continue;
            if (!"FOLLOW_PLAYER".equals(citizen.getMovementBehavior().getType())) {
                followingPlayers.remove(citizenId);
                continue;
            }
                if (HyCitizensPlugin.get().getCitizensManager().isCitizenInAiBusyState(citizen)) {
                    continue;
                }
                PlayerRef player = Universe.get().getPlayer(followState.playerUuid);
                if (player == null || !player.isValid() || player.getWorldUuid() == null || !player.getWorldUuid().equals(citizen.getWorldUUID())) {
                    stopFollowingPlayer(citizen, "PLAYER_DISCONNECT", null, store);
                    MovementBehavior mb = new MovementBehavior("IDLE", 2.0f, 0.0f, 0.0f, 0.0f);
                    citizen.setMovementBehavior(mb);
                    HyCitizensPlugin.get().getCitizensManager().stopCitizenMovement(citizenId);
                    HyCitizensPlugin.get().getCitizensManager().updateCitizenRoleImmediately(citizen);
                    continue;
                }
                Vector3d pPos = player.getTransform().getPosition();
                Vector3d cPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
                double dist = cPos.distance(pPos);

                if (dist > followState.maxDistance) {
                    if (followState.hardStopOnMaxDistance) {
                        stopFollowingPlayer(citizen, "MAX_DISTANCE", player, store);
                        MovementBehavior mb = new MovementBehavior("IDLE", 2.0f, 0.0f, 0.0f, 0.0f);
                        citizen.setMovementBehavior(mb);
                        HyCitizensPlugin.get().getCitizensManager().stopCitizenMovement(citizenId);
                        HyCitizensPlugin.get().getCitizensManager().updateCitizenRoleImmediately(citizen);
                    } else {
                        HyCitizensPlugin.get().getCitizensManager().updateCitizenMoveTarget(citizenId, cPos);
                    }
                } else if (dist > followState.minDistance) {
                    // Update movement target
                    HyCitizensPlugin.get().getCitizensManager().updateCitizenMoveTarget(citizenId, pPos);
                } else {
                    // Destination reached - maintain target so Seek halts within stop distance
                    HyCitizensPlugin.get().getCitizensManager().updateCitizenMoveTarget(citizenId, pPos);
                }
        }

        final double cellSize = 16.0;
        Map<Long, List<PlayerRef>> playerCells = new HashMap<>();
        Set<UUID> currentPlayers = new HashSet<>();
        for (PlayerRef player : world.getPlayerRefs()) {
            if (!player.isValid()) continue;
            Vector3d position = player.getTransform().getPosition();
            playerCells.computeIfAbsent(spatialCell(position, cellSize), ignored -> new ArrayList<>()).add(player);
            currentPlayers.add(player.getUuid());
        }

        for (CitizenData citizen : worldCitizens) {
            List<ScriptBlock> rawScripts = getCitizenScripts(citizen);
            if (rawScripts == null || rawScripts.isEmpty()) continue;

            boolean hasProxEnter = false;
            boolean hasProxExit = false;
            double radius = 5.0;

            for (ScriptBlock script : rawScripts) {
                if (script.isEnabled()) {
                    if (script.matchesTrigger("ON_PROXIMITY_ENTER")) {
                        hasProxEnter = true;
                        radius = getAsDouble(script.getTriggerParameters().getOrDefault("radius", 5.0), radius);
                    } else if (script.matchesTrigger("ON_PROXIMITY_EXIT")) {
                        hasProxExit = true;
                        radius = getAsDouble(script.getTriggerParameters().getOrDefault("radius", 5.0), radius);
                    }
                }
            }

            if (!hasProxEnter && !hasProxExit) continue;

            Map<UUID, Boolean> citizenProxState = proximityStates.computeIfAbsent(citizen.getId(), k -> new ConcurrentHashMap<>());
            Vector3d cPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
            int cellRadius = Math.max(1, (int) Math.ceil(radius / cellSize));
            int centerX = (int) Math.floor(cPos.x / cellSize);
            int centerZ = (int) Math.floor(cPos.z / cellSize);
            Set<UUID> checkedPlayers = new HashSet<>();

            for (int dx = -cellRadius; dx <= cellRadius; dx++) {
                for (int dz = -cellRadius; dz <= cellRadius; dz++) {
                    for (PlayerRef player : playerCells.getOrDefault(spatialCell(centerX + dx, centerZ + dz), List.of())) {
                        UUID playerId = player.getUuid();
                        checkedPlayers.add(playerId);
                        boolean inside = player.getTransform().getPosition().distance(cPos) <= radius;
                        boolean wasInside = citizenProxState.getOrDefault(playerId, false);
                        citizenProxState.put(playerId, inside);
                        if (inside && !wasInside && hasProxEnter) {
                            fireTrigger(citizen, "ON_PROXIMITY_ENTER", null, player, store);
                        } else if (!inside && wasInside && hasProxExit) {
                            fireTrigger(citizen, "ON_PROXIMITY_EXIT", null, player, store);
                        }
                    }
                }
            }

            for (UUID previousPlayer : new HashSet<>(citizenProxState.keySet())) {
                if (!currentPlayers.contains(previousPlayer)) {
                    citizenProxState.remove(previousPlayer);
                } else if (!checkedPlayers.contains(previousPlayer) && Boolean.TRUE.equals(citizenProxState.put(previousPlayer, false))
                        && hasProxExit) {
                    PlayerRef player = Universe.get().getPlayer(previousPlayer);
                    if (player != null && player.isValid()) {
                        fireTrigger(citizen, "ON_PROXIMITY_EXIT", null, player, store);
                    }
                }
            }
        }
    }

    private void checkTicks() {
        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> checkTicksOnWorld(world));
        }
    }

    private void checkTicksOnWorld(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (CitizenData citizen : HyCitizensPlugin.get().getCitizensManager().getAllCitizens()) {
            if (Universe.get().getWorld(citizen.getWorldUUID()) != world) continue;
            List<ScriptBlock> rawScripts = getCitizenScripts(citizen);
            if (rawScripts == null || rawScripts.isEmpty()) continue;
            for (ScriptBlock script : rawScripts) {
                if (script.isEnabled() && script.matchesTrigger("ON_TICK")) {
                    double seconds = getAsDouble(script.getTriggerParameters().getOrDefault("interval_seconds", 1.0), 1.0);
                    if (seconds < 0.5) seconds = 0.5;
                    String runKey = citizen.getId() + ":" + script.getId();
                    long lastTick = contextLastRunTime(runKey);
                    if (System.currentTimeMillis() - lastTick >= (seconds * 1000)) {
                        setContextLastRunTime(runKey, System.currentTimeMillis());
                        fireTrigger(citizen, "ON_TICK", null, null, store);
                    }
                }
            }
        }
    }

    private long spatialCell(Vector3d position, double cellSize) {
        return spatialCell((int) Math.floor(position.x / cellSize), (int) Math.floor(position.z / cellSize));
    }

    private long spatialCell(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private final Map<String, Long> lastRunTimes = new ConcurrentHashMap<>();
    private long contextLastRunTime(String scriptId) { return lastRunTimes.getOrDefault(scriptId, 0L); }
    private void setContextLastRunTime(String scriptId, long time) { lastRunTimes.put(scriptId, time); }

    // Timers API
    public void registerTimer(CitizenData citizen, String name, double intervalSeconds) {
        Map<String, ScheduledFuture<?>> timers = activeTimers.computeIfAbsent(citizen.getId(), k -> new ConcurrentHashMap<>());
        ScheduledFuture<?> old = timers.remove(name);
        if (old != null) {
            old.cancel(true);
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) return;

        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            world.execute(() -> {
                fireTrigger(citizen, "ON_TIMER", Map.of("timer_name", name), null, world.getEntityStore().getStore());
            });
        }, (long) (intervalSeconds * 1000), (long) (intervalSeconds * 1000), TimeUnit.MILLISECONDS);

        timers.put(name, future);
    }

    public void unregisterTimer(CitizenData citizen, String name) {
        Map<String, ScheduledFuture<?>> timers = activeTimers.get(citizen.getId());
        if (timers != null) {
            ScheduledFuture<?> future = timers.remove(name);
            if (future != null) {
                future.cancel(true);
            }
        }
    }

    public void registerAllTimers(CitizenData citizen) {
        List<ScriptBlock> rawScripts = getCitizenScripts(citizen);
        if (rawScripts == null) return;
        for (ScriptBlock script : rawScripts) {
            if (script.isEnabled() && script.matchesTrigger("ON_TIMER")) {
                Map<String, Object> params = script.getTriggerParameters();
                if (params != null) {
                    String name = (String) params.get("name");
                    Object intervalNum = params.get("interval_seconds");
                    if (name != null && intervalNum != null) {
                        registerTimer(citizen, name, getAsDouble(intervalNum, 5.0));
                    }
                }
            }
        }
    }

    public void unregisterAllTimers(CitizenData citizen) {
        Map<String, ScheduledFuture<?>> timers = activeTimers.remove(citizen.getId());
        if (timers != null) {
            for (ScheduledFuture<?> future : timers.values()) {
                if (future != null) {
                    future.cancel(true);
                }
            }
        }
    }

    public void refreshTimers(CitizenData citizen) {
        unregisterAllTimers(citizen);
        registerAllTimers(citizen);
    }

    public void cleanupCitizen(String citizenId) {
        proximityStates.remove(citizenId);
        followingPlayers.remove(citizenId);
        Map<String, ScheduledFuture<?>> timers = activeTimers.remove(citizenId);
        if (timers != null) {
            for (ScheduledFuture<?> future : timers.values()) {
                if (future != null) {
                    future.cancel(true);
                }
            }
        }
    }
    private List<ScriptBlock> getCitizenScripts(CitizenData citizen) {
        List<ScriptBlock> scripts = citizen.getScripts();
        return scripts != null ? scripts : Collections.emptyList();
    }

    private void registerBuiltins() {
        // Register Conditions
        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "COMPARE_VARIABLE"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String scope = (String) params.get("scope");
                String name = (String) params.get("name");
                String operator = (String) params.get("operator");
                Object targetValue = params.get("value");

                Object currentValue = null;
                if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                    currentValue = VariableManager.get().getPlayerVar(context.getPlayer().getUuid(), name);
                } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                    currentValue = VariableManager.get().getCitizenVar(context.getCitizen(), name);
                } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                    currentValue = VariableManager.get().getGlobalVar(name);
                } else if ("SESSION".equalsIgnoreCase(scope)) {
                    currentValue = context.getSessionVar(name);
                }

                if (currentValue == null) currentValue = "0";
                return evaluateComparison(currentValue.toString(), operator, targetValue != null ? targetValue.toString() : "");
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "VARIABLE_EXISTS"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String scope = (String) params.get("scope");
                String name = (String) params.get("name");

                if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                    return VariableManager.get().getPlayerVariables(context.getPlayer().getUuid()).containsKey(name);
                } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                    return VariableManager.get().getCitizenVariables(context.getCitizen()).containsKey(name);
                } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                    return VariableManager.get().getGlobalVariables().containsKey(name);
                } else if ("SESSION".equalsIgnoreCase(scope)) {
                    return context.hasSessionVar(name);
                }
                return false;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "CHANCE"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                Number percentNum = (Number) params.get("percent");
                double percent = percentNum != null ? percentNum.doubleValue() : 50.0;
                return ThreadLocalRandom.current().nextDouble() * 100.0 <= percent;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "COOLDOWN"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String name = (String) params.get("name");
                Number durationNum = (Number) params.get("duration_seconds");
                double duration = durationNum != null ? durationNum.doubleValue() : 0.0;

                String cdKey = "cooldown_" + name;
                Object cdEnd = context.getSessionVar(cdKey);
                if (cdEnd instanceof Long && System.currentTimeMillis() < (Long) cdEnd) {
                    return false;
                }

                // Start/Reset cooldown
                context.setSessionVar(cdKey, System.currentTimeMillis() + (long)(duration * 1000));
                return true;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "LIST_CONTAINS"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String scope = (String) params.get("scope");
                String name = (String) params.get("name");
                Object val = params.get("value");

                List<?> list = null;
                if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                    Object obj = VariableManager.get().getPlayerVar(context.getPlayer().getUuid(), name);
                    if (obj instanceof List) list = (List<?>) obj;
                } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                    Object obj = VariableManager.get().getCitizenVar(context.getCitizen(), name);
                    if (obj instanceof List) list = (List<?>) obj;
                } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                    Object obj = VariableManager.get().getGlobalVar(name);
                    if (obj instanceof List) list = (List<?>) obj;
                } else if ("SESSION".equalsIgnoreCase(scope)) {
                    Object obj = context.getSessionVar(name);
                    if (obj instanceof List) list = (List<?>) obj;
                }

                return list != null && list.contains(normalizeValue(val));
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "HEALTH_PERCENTAGE"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String target = (String) params.get("target");
                String operator = (String) params.get("operator");
                Number thresholdNum = (Number) params.get("value");
                double threshold = thresholdNum != null ? thresholdNum.doubleValue() : 100.0;

                Ref<EntityStore> ref = "PLAYER".equalsIgnoreCase(target) 
                    ? (context.getPlayer() != null ? context.getPlayer().getReference() : null) 
                    : (context.getCitizen() != null ? context.getCitizen().getNpcRef() : null);

                if (ref == null) return false;

                double health = ScriptExpressionEvaluator.getEntityHealth(ref);
                double max = ScriptExpressionEvaluator.getEntityMaxHealth(ref);
                double percent = (health / max) * 100.0;

                return evaluateComparison(String.valueOf(percent), operator, String.valueOf(threshold));
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "HAS_ITEM"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String itemId = (String) params.get("item_id");
                Number amountNum = (Number) params.get("amount");
                int amount = amountNum != null ? amountNum.intValue() : 1;

                Ref<EntityStore> pRef = context.getPlayer().getReference();
                if (pRef == null || !pRef.isValid()) return false;
                Player p = pRef.getStore().getComponent(pRef, Player.getComponentType());
                if (p == null) return false;

                CombinedItemContainer container = InventoryComponent.getCombined(pRef.getStore(), pRef, InventoryComponent.HOTBAR_FIRST);
                return container != null && InventoryHelper.countItems(container, List.of(itemId)) >= amount;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "IS_IN_REGION"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String target = (String) params.get("target");
                Number x1 = (Number) params.get("x1");
                Number y1 = (Number) params.get("y1");
                Number z1 = (Number) params.get("z1");
                Number x2 = (Number) params.get("x2");
                Number y2 = (Number) params.get("y2");
                Number z2 = (Number) params.get("z2");
                if (x1 == null || y1 == null || z1 == null || x2 == null || y2 == null || z2 == null) return false;

                Vector3d pos;
                if ("PLAYER".equalsIgnoreCase(target)) {
                    if (context.getPlayer() == null) return false;
                    pos = context.getPlayer().getTransform().getPosition();
                } else {
                    CitizenData citizen = context.getCitizen();
                    if (citizen == null) return false;
                    pos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
                }

                double minX = Math.min(x1.doubleValue(), x2.doubleValue());
                double maxX = Math.max(x1.doubleValue(), x2.doubleValue());
                double minY = Math.min(y1.doubleValue(), y2.doubleValue());
                double maxY = Math.max(y1.doubleValue(), y2.doubleValue());
                double minZ = Math.min(z1.doubleValue(), z2.doubleValue());
                double maxZ = Math.max(z1.doubleValue(), z2.doubleValue());

                return pos.x >= minX && pos.x <= maxX && pos.y >= minY && pos.y <= maxY && pos.z >= minZ && pos.z <= maxZ;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "TIME_OF_DAY"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                WorldTimeResource timeResource = context.getStore().getResource(WorldTimeResource.getResourceType());
                if (timeResource == null) return false;
                double time24h = timeResource.getDayProgress() * 24.0;
                String operator = (String) params.get("operator");
                Number thresholdNum = (Number) params.get("time_24h");
                double threshold = thresholdNum != null ? thresholdNum.doubleValue() : 12.0;
                return evaluateComparison(String.valueOf(time24h), operator, String.valueOf(threshold));
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "FACTION_STANDING"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String factionId = (String) params.get("faction_id");
                String standing = (String) params.get("standing");
                if (factionId == null || standing == null) return false;

                FactionConfig faction = HyCitizensPlugin.get().getCitizensManager().getFactionConfig(factionId);
                if (faction == null) return false;

                PlayerRef player = context.getPlayer();
                String permissionPrefix = "hycitizens.faction." + faction.getFactionId().toLowerCase() + ".";
                if (player.hasPermission(permissionPrefix + standing.toLowerCase())) {
                    return true;
                }

                List<String> groups;
                if ("HOSTILE".equalsIgnoreCase(standing)) {
                    groups = faction.getHostileGroups();
                } else if ("NEUTRAL".equalsIgnoreCase(standing)) {
                    groups = faction.getNeutralGroups();
                } else {
                    groups = faction.getPassiveGroups();
                }

                for (String group : groups) {
                    if (player.hasPermission("group." + group.toLowerCase())) {
                        return true;
                    }
                }
                return false;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "HAS_TAG"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String target = (String) params.get("target");
                String tag = (String) params.get("tag");
                if (tag == null) return false;

                if ("PLAYER".equalsIgnoreCase(target) && context.getPlayer() != null) {
                    Object obj = VariableManager.get().getPlayerVar(context.getPlayer().getUuid(), "tags");
                    return obj instanceof List && ((List<?>) obj).contains(tag);
                } else if (context.getCitizen() != null) {
                    Object obj = VariableManager.get().getCitizenVar(context.getCitizen(), "tags");
                    return obj instanceof List && ((List<?>) obj).contains(tag);
                }
                return false;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "IS_SCRIPT_RUNNING"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String scriptId = (String) params.get("script_id");
                if (scriptId == null) return false;
                Set<String> running = runningScripts.get(context.getCitizen().getId());
                return running != null && running.contains(scriptId);
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "IS_IN_COMBAT"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String target = (String) params.getOrDefault("target", "PLAYER");
                if ("PLAYER".equalsIgnoreCase(target)) {
                    return false;
                } else if (context.getCitizen() != null) {
                    return HyCitizensPlugin.get().getCitizensManager().isCitizenInCombat(context.getCitizen());
                }
                return false;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "IS_FLYING"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;

                Ref<EntityStore> playerRef = context.getPlayer().getReference();
                if (playerRef == null)
                    return false;

                Store<EntityStore> playerStore = playerRef.getStore();

                MovementStatesComponent movementStatesComponent = playerStore.getComponent(playerRef, MovementStatesComponent.getComponentType());
                if (movementStatesComponent == null) {
                    return false;
                }

                MovementStates movementStates = movementStatesComponent.getMovementStates();
                return movementStates.flying;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "IS_FOLLOWING_PLAYER"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getCitizen() == null) return false;
                return followingPlayers.containsKey(context.getCitizen().getId());
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "CITIZEN_IN_STATE"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getCitizen() == null) return false;
                String state = (String) params.get("state");
                if (state == null) return false;
                
                if ("FOLLOWING_PLAYER".equalsIgnoreCase(state)) {
                    return followingPlayers.containsKey(context.getCitizen().getId());
                }
                
                String stateName = getCitizenStateName(context.getCitizen());
                return stateName != null && stateName.equalsIgnoreCase(state);
            }
        });



        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "DISTANCE_TO_LOCATION"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                Number xVal = (Number) params.get("x");
                Number yVal = (Number) params.get("y");
                Number zVal = (Number) params.get("z");
                String operator = (String) params.get("operator");
                Number thresholdNum = (Number) params.get("value");
                if (xVal == null || yVal == null || zVal == null || thresholdNum == null) return false;
                
                CitizenData citizen = context.getCitizen();
                if (citizen == null) return false;
                Vector3d pos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
                double dist = pos.distance(new Vector3d(xVal.doubleValue(), yVal.doubleValue(), zVal.doubleValue()));
                
                return evaluateComparison(String.valueOf(dist), operator, String.valueOf(thresholdNum.doubleValue()));
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "ENTITY_TYPE_NEARBY"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String entityType = (String) params.get("entity_type");
                if (entityType == null || entityType.isBlank()) return false;
                
                CitizenData citizen = context.getCitizen();
                if (citizen == null) return false;

                double radius = getAsDouble(params.get("radius"), 10.0);
                int minCount = Math.max(1, getAsInt(params.getOrDefault("min_count", 1), 1));
                return findEntitiesInRadius(context, radius, entityType).size() >= minCount;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "HAS_LINE_OF_SIGHT"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                CitizenData citizen = context.getCitizen();
                if (citizen == null) return false;
                Vector3d start = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
                Vector3d targetPos = null;
                
                Object target = params.get("target");
                if ("PLAYER".equalsIgnoreCase(String.valueOf(target)) && context.getPlayer() != null) {
                    targetPos = context.getPlayer().getTransform().getPosition();
                } else if (target instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) target;
                    Number x = (Number) map.get("x");
                    Number y = (Number) map.get("y");
                    Number z = (Number) map.get("z");
                    if (x != null && y != null && z != null) {
                        targetPos = new Vector3d(x.doubleValue(), y.doubleValue(), z.doubleValue());
                    }
                }
                if (targetPos == null) return false;

                return true; // Todo: Add this
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "RAYCAST_HIT"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                // Todo: Add this
                return true;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "INVENTORY_SPACE_FREE"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                Number slotsVal = (Number) params.get("slots");
                int required = slotsVal != null ? slotsVal.intValue() : 1;
                
                PlayerRef playerRef = context.getPlayer();
                Ref<EntityStore> pRef = playerRef.getReference();
                Player p = pRef.getStore().getComponent(pRef, Player.getComponentType());
                if (p == null) return false;
                
                CombinedItemContainer container = InventoryComponent.getCombined(pRef.getStore(), pRef, InventoryComponent.HOTBAR_FIRST);
                if (container == null) return false;
                int free = 0;
                for (short i = 0; i < container.getCapacity(); i++) {
                    ItemStack item = container.getItemStack(i);
                    if (item == null || item.isEmpty()) {
                        free++;
                        if (free >= required) return true;
                    }
                }
                return free >= required;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "HAS_EQUIPPED"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String itemId = (String) params.get("item_id");
                String slot = ((String) params.getOrDefault("slot", "HAND")).toUpperCase();
                if (itemId == null) return false;
                
                PlayerRef playerRef = context.getPlayer();
                Ref<EntityStore> pRef = playerRef.getReference();
                Store<EntityStore> pStore = pRef.getStore();
                Player p = pStore.getComponent(pRef, Player.getComponentType());
                if (p == null) return false;
                
                ItemStack item = null;
                if ("HELMET".equals(slot)) {
                    InventoryComponent.Armor armor = pStore.getComponent(pRef, InventoryComponent.Armor.getComponentType());
                    item = armor != null ? armor.getInventory().getItemStack((short) 0) : null;
                } else if ("CHEST".equals(slot)) {
                    InventoryComponent.Armor armor = pStore.getComponent(pRef, InventoryComponent.Armor.getComponentType());
                    item = armor != null ? armor.getInventory().getItemStack((short) 1) : null;
                } else if ("GLOVES".equals(slot)) {
                    InventoryComponent.Armor armor = pStore.getComponent(pRef, InventoryComponent.Armor.getComponentType());
                    item = armor != null ? armor.getInventory().getItemStack((short) 2) : null;
                } else if ("LEGGINGS".equals(slot)) {
                    InventoryComponent.Armor armor = pStore.getComponent(pRef, InventoryComponent.Armor.getComponentType());
                    item = armor != null ? armor.getInventory().getItemStack((short) 3) : null;
                } else if ("HAND".equals(slot) || "MAIN_HAND".equals(slot)) {
                    item = InventoryComponent.getItemInHand(pStore, pRef);
                } else if ("OFF_HAND".equals(slot) || "OFFHAND".equals(slot)) {
                    InventoryComponent.Utility utility = pStore.getComponent(pRef, InventoryComponent.Utility.getComponentType());
                    item = utility != null ? utility.getActiveItem() : null;
                }
                
                return item != null && !item.isEmpty() && itemId.equalsIgnoreCase(item.getItem().getId());
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "HAS_PERMISSION"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String permission = (String) params.get("permission");
                return permission != null && context.getPlayer().hasPermission(permission);
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "CITIZEN_EXISTS"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String citizenId = (String) params.get("citizen_id");
                return citizenId != null && HyCitizensPlugin.get().getCitizensManager().getCitizen(citizenId) != null;
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "CITIZEN_IS_ALIVE"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                String citizenId = (String) params.get("citizen_id");
                if (citizenId == null) return false;
                CitizenData citizen = HyCitizensPlugin.get().getCitizensManager().getCitizen(citizenId);
                return citizen != null && citizen.getNpcRef() != null && citizen.getNpcRef().isValid();
            }
        });

        // Register Actions
        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "WAIT"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                double seconds = getAsDouble(params.get("seconds"), 0.0);

                CompletableFuture<Void> future = new CompletableFuture<>();
                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    context.getWorld().execute(() -> future.complete(null));
                }, (long) (seconds * 1000), TimeUnit.MILLISECONDS);
                return future;
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "IF_ELSE"; }
            @SuppressWarnings("unchecked")
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                ScriptCondition cond = parseCondition(params.get("condition"));
                List<ScriptAction> trueActions = (List<ScriptAction>) params.get("_true_actions");
                List<ScriptAction> falseActions = (List<ScriptAction>) params.get("_false_actions");

                boolean result = cond != null && evaluateCondition(cond, context);
                List<ScriptAction> branch = result ? trueActions : falseActions;

                if (branch == null || branch.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                return executeActions(branch, context);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "WHILE_LOOP"; }
            @SuppressWarnings("unchecked")
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                int maxIterations = getAsInt(params.get("max_iterations"), 10);
                if (maxIterations > 1000) maxIterations = 1000;

                ScriptCondition cond = parseCondition(params.get("condition"));
                List<ScriptAction> subActions = (List<ScriptAction>) params.getOrDefault("_sub_actions", Collections.emptyList());

                CompletableFuture<Void> loopFuture = new CompletableFuture<>();
                runWhileIteration(cond, subActions, maxIterations, 0, context, loopFuture);
                return loopFuture;
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "REPEAT"; }
            @SuppressWarnings("unchecked")
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                int count = Math.max(0, getAsInt(params.get("count"), 1));
                List<ScriptAction> subActions = (List<ScriptAction>) params.getOrDefault("_sub_actions", Collections.emptyList());

                CompletableFuture<Void> repeatFuture = new CompletableFuture<>();
                runRepeatIteration(subActions, count, 0, context, repeatFuture);
                return repeatFuture;
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "FOREACH_PLAYER_IN_RADIUS"; }
            @SuppressWarnings("unchecked")
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                CitizenData citizen = context.getCitizen();
                if (citizen == null) return CompletableFuture.completedFuture(null);

                double radius = Math.max(0.0, getAsDouble(params.get("radius"), 10.0));
                Vector3d cPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
                List<PlayerRef> players = new ArrayList<>();
                for (PlayerRef player : context.getWorld().getPlayerRefs()) {
                    if (player != null && player.isValid() && player.getTransform().getPosition().distance(cPos) <= radius) {
                        players.add(player);
                    }
                }

                List<ScriptAction> subActions = (List<ScriptAction>) params.getOrDefault("_sub_actions", Collections.emptyList());
                CompletableFuture<Void> loopFuture = new CompletableFuture<>();
                runForeachPlayerIteration(players, subActions, 0, context, loopFuture);
                return loopFuture;
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "FOREACH_ENTITY_IN_RADIUS"; }
            @SuppressWarnings("unchecked")
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                double radius = Math.max(0.0, getAsDouble(params.get("radius"), 10.0));
                String entityType = (String) params.getOrDefault("entity_type", "");
                List<Ref<EntityStore>> refs = findEntitiesInRadius(context, radius, entityType);
                List<ScriptAction> subActions = (List<ScriptAction>) params.getOrDefault("_sub_actions", Collections.emptyList());

                CompletableFuture<Void> loopFuture = new CompletableFuture<>();
                runForeachEntityIteration(refs, subActions, 0, context, loopFuture);
                return loopFuture;
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "CHOOSE_RANDOM"; }
            @SuppressWarnings("unchecked")
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                List<Branch> branches = (List<Branch>) params.get("_branches");
                if (branches == null || branches.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }

                int totalWeight = 0;
                for (Branch b : branches) {
                    totalWeight += Math.max(1, b.getWeight());
                }
                if (totalWeight <= 0) {
                    return CompletableFuture.completedFuture(null);
                }

                int roll = ThreadLocalRandom.current().nextInt(totalWeight);
                int currentWeight = 0;
                Branch selected = null;
                for (Branch b : branches) {
                    currentWeight += Math.max(1, b.getWeight());
                    if (roll < currentWeight) {
                        selected = b;
                        break;
                    }
                }

                if (selected != null && selected.getActions() != null) {
                    return executeActions(selected.getActions(), context);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SEND_MESSAGE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return CompletableFuture.completedFuture(null);
                String rawMsg = (String) params.get("message");
                String message = ScriptExpressionEvaluator.resolve(rawMsg, context);

                Message parsed = CitizenInteraction.parseColoredMessage(message);
                if (parsed != null) {
                    context.getPlayer().sendMessage(parsed);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "RUN_COMMAND"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return CompletableFuture.completedFuture(null);
                String rawCmd = (String) params.get("command");
                boolean runAsServer = getBooleanParam(params.get("run_as_server"), true);

                String command = ScriptExpressionEvaluator.resolve(rawCmd, context);
                CommandExecutionUtil.execute(context.getPlayer(), command, runAsServer);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "RUN_COMMAND_AND_CAPTURE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String rawCmd = (String) params.get("command");
                String captureVar = (String) params.get("capture_variable");
                String captureScope = (String) params.getOrDefault("capture_scope", "SESSION");
                String regexPattern = (String) params.get("regex_pattern");

                if (captureVar == null || regexPattern == null || rawCmd == null) {
                    return CompletableFuture.completedFuture(null);
                }

                String command = ScriptExpressionEvaluator.resolve(rawCmd, context);
                
                // Execute as console and capture the output asynchronously
                return CommandExecutionUtil.executeWithCapture(command).thenAccept(output -> {
                    if (output != null && !output.isEmpty()) {
                        try {
                            Pattern pattern = Pattern.compile(regexPattern);
                            Matcher matcher = pattern.matcher(output);
                            if (matcher.find() && matcher.groupCount() >= 1) {
                                String capturedValue = matcher.group(1);
                                
                                // Store in the specified variable scope
                                if ("PLAYER".equalsIgnoreCase(captureScope) && context.getPlayer() != null) {
                                    VariableManager.get().setPlayerVar(context.getPlayer().getUuid(), captureVar, capturedValue);
                                } else if ("CITIZEN".equalsIgnoreCase(captureScope)) {
                                    VariableManager.get().setCitizenVar(context.getCitizen(), captureVar, capturedValue);
                                } else if ("GLOBAL".equalsIgnoreCase(captureScope)) {
                                    VariableManager.get().setGlobalVar(captureVar, capturedValue);
                                } else if ("SESSION".equalsIgnoreCase(captureScope)) {
                                    context.setSessionVar(captureVar, capturedValue);
                                }
                            }
                        } catch (Exception e) {
                            getLogger().atWarning().log("[HyCitizens] Regex capture failed: " + e.getMessage());
                        }
                    }
                });
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "FOREACH_DAMAGE_DEALER"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun()) return CompletableFuture.completedFuture(null);
                
                CitizenData citizen = context.getCitizen();
                if (citizen == null) return CompletableFuture.completedFuture(null);

                Number maxDealersNum = (Number) params.get("max_dealers");
                int maxDealers = maxDealersNum != null ? maxDealersNum.intValue() : 5;
                
                List<ScriptAction> subActions = (List<ScriptAction>) params.get("actions");
                if (subActions == null || subActions.isEmpty()) return CompletableFuture.completedFuture(null);

                Map<UUID, Double> dealers = citizen.getRecentDamageDealers();
                if (dealers == null || dealers.isEmpty()) return CompletableFuture.completedFuture(null);

                List<Map.Entry<UUID, Double>> sortedDealers = new ArrayList<>(dealers.entrySet());
                sortedDealers.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

                int count = 0;
                for (Map.Entry<UUID, Double> entry : sortedDealers) {
                    if (count >= maxDealers) break;
                    
                    UUID playerUuid = entry.getKey();
                    double damageAmount = entry.getValue();
                    
                    // Set loop variables in session
                    context.setSessionVar("loop:player_uuid", playerUuid.toString());
                    context.setSessionVar("loop:damage_amount", damageAmount);

                    // Try to get player name
                    String playerName = "Unknown";
                    if (context.getWorld() != null) {
                        for (PlayerRef playerRef : context.getWorld().getPlayerRefs()) {
                            if (playerRef.getUuid().equals(playerUuid)) {
                                playerName = playerRef.getUsername();
                                break;
                            }
                        }
                    }
                    context.setSessionVar("loop:player_name", playerName);

                    // Execute sub-actions
                    executeActions(subActions, context);
                    count++;
                }
                
                // Clean up loop variables
                context.setSessionVar("loop:player_uuid", null);
                context.setSessionVar("loop:player_name", null);
                context.setSessionVar("loop:damage_amount", null);
                
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_RESPAWN_SETTINGS"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun()) return CompletableFuture.completedFuture(null);
                
                CitizenData citizen = context.getCitizen();
                if (citizen == null) return CompletableFuture.completedFuture(null);

                citizen.setCustomRespawnSettingsEnabled(true);
                
                Number delayNum = (Number) params.get("delay_seconds");
                if (delayNum != null) {
                    citizen.setCustomRespawnDelaySeconds(delayNum.floatValue());
                }
                
                Object requireNoPlayers = params.get("require_no_players_in_radius");
                if (requireNoPlayers != null) {
                    citizen.setRequireNoPlayersInRadiusForRespawn(Boolean.parseBoolean(requireNoPlayers.toString()));
                }
                
                Number radiusNum = (Number) params.get("radius");
                if (radiusNum != null) {
                    citizen.setRespawnPlayerCheckRadius(radiusNum.floatValue());
                }
                
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "PLAY_ANIMATION"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                CitizenData citizen = context.getCitizen();
                if (citizen == null || citizen.getNpcRef() == null) return CompletableFuture.completedFuture(null);

                String anim = (String) params.get("animation");
                Number slotNum = (Number) params.get("slot");
                int slot = slotNum != null ? slotNum.intValue() : 2;
                Number stopAfterNum = (Number) params.get("stop_after_seconds");
                double stopAfter = stopAfterNum != null ? stopAfterNum.doubleValue() : 3.0;
                String stopAnim = (String) params.get("stop_animation");

                AnimationUtils.playAnimation(citizen.getNpcRef(), AnimationSlot.values()[slot], anim, false, context.getStore());

                if (stopAfter > 0.0) {
                    String finalStopAnim = stopAnim != null ? stopAnim : "Idle";
                    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        context.getWorld().execute(() -> {
                            AnimationUtils.playAnimation(citizen.getNpcRef(), AnimationSlot.values()[slot], finalStopAnim, false, context.getStore());
                        });
                    }, (long) (stopAfter * 1000), TimeUnit.MILLISECONDS);
                }

                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_VARIABLE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun()) return CompletableFuture.completedFuture(null);

                String scope = (String) params.get("scope");
                String name = (String) params.get("name");
                Object val = params.get("value");
                String varType = (String) params.get("type");
                if (varType != null && val != null) {
                    val = coerceValueType(val, varType);
                }

                if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                    VariableManager.get().setPlayerVar(context.getPlayer().getUuid(), name, val);
                } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                    VariableManager.get().setCitizenVar(context.getCitizen(), name, val);
                } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                    VariableManager.get().setGlobalVar(name, val);
                } else if ("SESSION".equalsIgnoreCase(scope)) {
                    context.setSessionVar(name, val);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_FLYING"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun())
                    return CompletableFuture.completedFuture(null);

                Ref<EntityStore> playerRef = context.getPlayer().getReference();
                if (playerRef == null || !playerRef.isValid())
                    return CompletableFuture.completedFuture(null);

                Object enabledVal = params.get("enabled");
                boolean enabled = enabledVal == null || Boolean.TRUE.equals(enabledVal) || "true".equalsIgnoreCase(enabledVal.toString());

                context.getWorld().execute(() -> {
                    Store<EntityStore> playerStore = playerRef.getStore();

                    MovementManager movementManager = playerStore.getComponent(playerRef, MovementManager.getComponentType());
                    if (movementManager == null) {
                        return;
                    }

                    movementManager.getSettings().fly = enabled ? FlyMode.Allowed : FlyMode.Disabled;
                    movementManager.update(context.getPlayer().getPacketHandler());
                    MovementStatesComponent movementStatesComponent = playerStore.getComponent(playerRef, MovementStatesComponent.getComponentType());
                    if (movementStatesComponent == null) {
                        return;
                    }

                    MovementStates movementStates = movementStatesComponent.getMovementStates();
                    if (movementStates.flying) {
                        movementStates.flying = enabled;
                        context.getPlayer().getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(enabled)));
                    }
                });

                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_RANDOM_NUMBER"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun()) return CompletableFuture.completedFuture(null);

                String scope = (String) params.getOrDefault("scope", "SESSION");
                String name = (String) params.get("name");
                boolean integer = getBooleanParam(params.get("integer"), true);
                if (name == null || name.isEmpty()) return CompletableFuture.completedFuture(null);

                double min = getAsDouble(params.get("min"), 1.0);
                double max = getAsDouble(params.get("max"), 100.0);
                if (max < min) {
                    double tmp = min;
                    min = max;
                    max = tmp;
                }

                Object value;
                if (Boolean.TRUE.equals(integer)) {
                    int low = (int) Math.ceil(min);
                    int high = (int) Math.floor(max);
                    if (high < low) high = low;
                    value = ThreadLocalRandom.current().nextInt(low, high + 1);
                } else {
                    value = ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
                }

                setScopedVariable(scope, name, value, context);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "INCREMENT_VARIABLE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun()) return CompletableFuture.completedFuture(null);
                String scope = (String) params.getOrDefault("scope", "SESSION");
                String name = (String) params.get("name");
                if (name == null || name.isBlank()) return CompletableFuture.completedFuture(null);

                double current = getAsDouble(getScopedVariable(scope, name, context), 0.0);
                double amount = getAsDouble(params.get("amount"), 1.0);
                setScopedVariable(scope, name, current + amount, context);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "DECREMENT_VARIABLE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun()) return CompletableFuture.completedFuture(null);
                String scope = (String) params.getOrDefault("scope", "SESSION");
                String name = (String) params.get("name");
                if (name == null || name.isBlank()) return CompletableFuture.completedFuture(null);

                double current = getAsDouble(getScopedVariable(scope, name, context), 0.0);
                double amount = getAsDouble(params.get("amount"), 1.0);
                setScopedVariable(scope, name, current - amount, context);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "TOGGLE_VARIABLE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun()) return CompletableFuture.completedFuture(null);
                String scope = (String) params.getOrDefault("scope", "SESSION");
                String name = (String) params.get("name");
                if (name == null || name.isBlank()) return CompletableFuture.completedFuture(null);

                Object current = getScopedVariable(scope, name, context);
                boolean next = !getBooleanParam(current, false);
                setScopedVariable(scope, name, next, context);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "CLEAR_VARIABLE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun()) return CompletableFuture.completedFuture(null);
                String scope = (String) params.getOrDefault("scope", "SESSION");
                String name = (String) params.get("name");
                if (name == null || name.isBlank()) return CompletableFuture.completedFuture(null);

                setScopedVariable(scope, name, null, context);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "COPY_VARIABLE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun()) return CompletableFuture.completedFuture(null);
                String fromScope = (String) params.getOrDefault("from_scope", "SESSION");
                String fromName = (String) params.get("from_name");
                String toScope = (String) params.getOrDefault("to_scope", "SESSION");
                String toName = (String) params.get("to_name");
                if (fromName == null || fromName.isBlank() || toName == null || toName.isBlank()) {
                    return CompletableFuture.completedFuture(null);
                }

                setScopedVariable(toScope, toName, getScopedVariable(fromScope, fromName, context), context);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "APPEND_TEXT"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.isDryRun()) return CompletableFuture.completedFuture(null);
                String scope = (String) params.getOrDefault("scope", "SESSION");
                String name = (String) params.get("name");
                if (name == null || name.isBlank()) return CompletableFuture.completedFuture(null);

                Object current = getScopedVariable(scope, name, context);
                String suffix = String.valueOf(params.getOrDefault("text", ""));
                setScopedVariable(scope, name, (current != null ? current.toString() : "") + suffix, context);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "REGISTER_TIMER"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String name = (String) params.get("name");
                double interval = getAsDouble(params.get("interval_seconds"), 1.0);

                registerTimer(context.getCitizen(), name, interval);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "UNREGISTER_TIMER"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String name = (String) params.get("name");
                unregisterTimer(context.getCitizen(), name);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SEND_WEBHOOK"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String url = (String) params.get("url");
                Object payload = params.get("payload");

                if (url == null || url.isEmpty()) return CompletableFuture.completedFuture(null);

                String jsonPayload = gson.toJson(payload);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        getLogger().atWarning().log("Failed to send webhook request: " + ex.getMessage());
                        return null;
                    });

                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "TELEPORT"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String entityType = (String) params.get("entity");
                Number xNum = (Number) params.get("x");
                Number yNum = (Number) params.get("y");
                Number zNum = (Number) params.get("z");
                if (xNum == null || yNum == null || zNum == null) return CompletableFuture.completedFuture(null);
                double x = xNum.doubleValue();
                double y = yNum.doubleValue();
                double z = zNum.doubleValue();

                Number yawNum = (Number) params.get("yaw");
                Number pitchNum = (Number) params.get("pitch");
                float yaw = yawNum != null ? yawNum.floatValue() : 0f;
                float pitch = pitchNum != null ? pitchNum.floatValue() : 0f;

                World destWorld = context.getWorld();
                String worldParam = (String) params.get("world");
                if (worldParam != null && !worldParam.isEmpty()) {
                    World found = findWorld(worldParam);
                    if (found != null) {
                        destWorld = found;
                    }
                }

                if ("PLAYER".equalsIgnoreCase(entityType)) {
                    if (context.getPlayer() != null) {
                        Teleport tp = new Teleport(destWorld, new Vector3d(x, y, z), RotationUtil.toRotation(new Vector3f(yaw, pitch, 0f)));
                        context.getStore().putComponent(context.getPlayer().getReference(), Teleport.getComponentType(), tp);
                    }
                } else {
                    CitizenData citizen = context.getCitizen();
                    if (citizen != null) {
                        UUID currentWorldUuid = citizen.getWorldUUID();
                        UUID destWorldUuid = destWorld.getWorldConfig().getUuid();
                        if (!currentWorldUuid.equals(destWorldUuid)) {
                            // Cross-world teleport
                            HyCitizensPlugin.get().getCitizensManager().despawnCitizen(citizen);
                            citizen.setWorldUUID(destWorldUuid);
                            citizen.setPosition(new Vector3d(x, y, z));
                            citizen.setCurrentPosition(new Vector3d(x, y, z));
                            citizen.setRotation(new Vector3f(yaw, pitch, 0f));
                            HyCitizensPlugin.get().getCitizensManager().saveCitizen(citizen);
                            HyCitizensPlugin.get().getCitizensManager().spawnCitizen(citizen, false);
                        } else {
                            // Same world teleport
                            citizen.setPosition(new Vector3d(x, y, z));
                            HyCitizensPlugin.get().getCitizensManager().saveCitizen(citizen);
                            HyCitizensPlugin.get().getCitizensManager().teleportCitizen(citizen, new Vector3d(x, y, z), new Vector3f(yaw, pitch, 0f));
                        }
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "FOLLOW_PLAYER"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null || context.getCitizen() == null) return CompletableFuture.completedFuture(null);
                Number speedNum = (Number) params.get("speed");
                Number minDistanceNum = (Number) params.get("min_distance");
                Number maxDistanceNum = (Number) params.get("max_distance");
                boolean hardStop = getBooleanParam(params.get("hard_stop_on_max_distance"), false);
                if (params.get("hard_stop_on_max_distance") == null && params.get("cancel_on_max_distance") != null) {
                    hardStop = getBooleanParam(params.get("cancel_on_max_distance"), false);
                }

                double speed = speedNum != null ? speedNum.doubleValue() : 1.2;
                double minDistance = minDistanceNum != null ? minDistanceNum.doubleValue() : 2.0;
                double maxDistance = maxDistanceNum != null ? maxDistanceNum.doubleValue() : 15.0;

                CitizenData citizen = context.getCitizen();
                MovementBehavior mb = new MovementBehavior("FOLLOW_PLAYER", (float) speed, 1f, 1f, 1f);
                citizen.setMovementBehavior(mb);

                HyCitizensPlugin.get().getCitizensManager().stopCitizenPatrol(citizen.getId());
                followingPlayers.put(citizen.getId(), new FollowPlayerState(context.getPlayer().getUuid(), speed, minDistance, maxDistance, hardStop));
                HyCitizensPlugin.get().getCitizensManager().updateCitizenRoleImmediately(citizen);

                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "STOP_FOLLOWING"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getCitizen() == null) return CompletableFuture.completedFuture(null);
                CitizenData citizen = context.getCitizen();
                stopFollowingPlayer(citizen, "COMMAND", context.getPlayer(), context.getStore());
                MovementBehavior mb = new MovementBehavior("IDLE", 2.0f, 0f, 0f, 0f);
                citizen.setMovementBehavior(mb);
                HyCitizensPlugin.get().getCitizensManager().stopCitizenMovement(citizen.getId());
                HyCitizensPlugin.get().getCitizensManager().updateCitizenRoleImmediately(citizen);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "WANDER"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getCitizen() == null) return CompletableFuture.completedFuture(null);
                Number radiusNum = (Number) params.get("radius");
                Number speedNum = (Number) params.get("speed");
                float radius = radiusNum != null ? radiusNum.floatValue() : 10f;
                float speed = speedNum != null ? speedNum.floatValue() : 2.0f;

                CitizenData citizen = context.getCitizen();
                stopFollowingPlayer(citizen, "WANDER", context.getPlayer(), context.getStore());
                HyCitizensPlugin.get().getCitizensManager().stopCitizenPatrol(citizen.getId());

                MovementBehavior mb = new MovementBehavior("WANDER", speed, radius, radius, radius);
                citizen.setMovementBehavior(mb);
                HyCitizensPlugin.get().getCitizensManager().updateCitizenRoleImmediately(citizen);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "START_PATROL"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getCitizen() == null) return CompletableFuture.completedFuture(null);
                String pathName = (String) params.get("path_name");
                Number speedNum = (Number) params.get("speed");
                float speed = speedNum != null ? speedNum.floatValue() : 2.0f;

                CitizenData citizen = context.getCitizen();
                stopFollowingPlayer(citizen, "PATROL", context.getPlayer(), context.getStore());

                MovementBehavior mb = new MovementBehavior("PATROL", speed, 0f, 0f, 0f);
                citizen.setMovementBehavior(mb);
                HyCitizensPlugin.get().getCitizensManager().startCitizenPatrol(citizen.getId(), pathName);
                HyCitizensPlugin.get().getCitizensManager().updateCitizenRoleImmediately(citizen);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "EQUIP_ITEM"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getCitizen() == null) return CompletableFuture.completedFuture(null);
                String slot = ((String) params.getOrDefault("slot", "HAND")).toUpperCase();
                String itemId = (String) params.get("item_id");

                CitizenData citizen = context.getCitizen();
                if ("HELMET".equals(slot)) {
                    citizen.setNpcHelmet(itemId);
                } else if ("CHEST".equals(slot)) {
                    citizen.setNpcChest(itemId);
                } else if ("GLOVES".equals(slot)) {
                    citizen.setNpcGloves(itemId);
                } else if ("LEGGINGS".equals(slot)) {
                    citizen.setNpcLeggings(itemId);
                } else if ("HAND".equals(slot) || "MAIN_HAND".equals(slot)) {
                    citizen.setNpcHand(itemId);
                } else if ("OFF_HAND".equals(slot) || "OFFHAND".equals(slot)) {
                    citizen.setNpcOffHand(itemId);
                }

                HyCitizensPlugin.get().getCitizensManager().updateCitizenNPCItems(citizen);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "HEAL"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String target = (String) params.get("target");
                Number amountNum = (Number) params.get("amount");
                float amount = amountNum != null ? amountNum.floatValue() : 20.0f;

                Ref<EntityStore> ref = "PLAYER".equalsIgnoreCase(target)
                    ? (context.getPlayer() != null ? context.getPlayer().getReference() : null)
                    : (context.getCitizen() != null ? context.getCitizen().getNpcRef() : null);

                if (ref != null && ref.isValid()) {
                    EntityStatMap statMap = ref.getStore().getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
                    if (statMap != null) {
                        EntityStatValue val = statMap.get(DefaultEntityStatTypes.getHealth());
                        if (val != null) {
                            float newHealth = Math.min(val.get() + amount, val.getMax());
                            statMap.setStatValue(DefaultEntityStatTypes.getHealth(), newHealth);
                        }
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "DAMAGE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String target = (String) params.get("target");
                Number amountNum = (Number) params.get("amount");
                float amount = amountNum != null ? amountNum.floatValue() : 5.0f;
                String causeId = (String) params.get("cause");

                Ref<EntityStore> ref = "PLAYER".equalsIgnoreCase(target)
                    ? (context.getPlayer() != null ? context.getPlayer().getReference() : null)
                    : (context.getCitizen() != null ? context.getCitizen().getNpcRef() : null);

                if (ref != null && ref.isValid()) {
                    DamageCause damageCause = null;
                    if (causeId != null) {
                        damageCause = DamageCause.getAssetMap().getAsset(causeId);
                    }
                    if (damageCause == null) {
                        damageCause = DamageCause.COMMAND != null ? DamageCause.COMMAND : new DamageCause("hytale:command");
                    }
                    Damage dmg = new Damage(Damage.NULL_SOURCE, damageCause, amount);
                    DamageSystems.executeDamage(ref, context.getStore(), dmg);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SHOW_DIALOGUE_UI"; }
            @SuppressWarnings("unchecked")
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return CompletableFuture.completedFuture(null);
                String title = (String) params.get("title");
                String body = (String) params.get("body");
                String resultVar = (String) params.get("result_variable");
                String resultScope = (String) params.get("result_scope");
                List<Map<String, Object>> responses = (List<Map<String, Object>>) params.get("responses");

                if (resultVar == null || responses == null) return CompletableFuture.completedFuture(null);

                CompletableFuture<Void> future = new CompletableFuture<>();
                HyCitizensPlugin.get().getScriptingUI().openDialogueUI(context.getPlayer(), context.getStore(), title, body, responses, responseId -> {
                    if ("PLAYER".equalsIgnoreCase(resultScope)) {
                        VariableManager.get().setPlayerVar(context.getPlayer().getUuid(), resultVar, responseId);
                    } else if ("CITIZEN".equalsIgnoreCase(resultScope)) {
                        VariableManager.get().setCitizenVar(context.getCitizen(), resultVar, responseId);
                    } else if ("GLOBAL".equalsIgnoreCase(resultScope)) {
                        VariableManager.get().setGlobalVar(resultVar, responseId);
                    } else {
                        context.setSessionVar(resultVar, responseId);
                    }
                    future.complete(null);
                });

                return future;
            }
        });



        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_COOLDOWN"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String name = (String) params.get("name");
                Number durationNum = (Number) params.get("duration_seconds");
                double duration = durationNum != null ? durationNum.doubleValue() : 0.0;
                String scope = (String) params.get("scope");

                String cdKey = "cooldown_" + name;
                long endTime = System.currentTimeMillis() + (long)(duration * 1000);

                if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                    VariableManager.get().setPlayerVar(context.getPlayer().getUuid(), cdKey, endTime);
                } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                    VariableManager.get().setCitizenVar(context.getCitizen(), cdKey, endTime);
                } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                    VariableManager.get().setGlobalVar(cdKey, endTime);
                } else {
                    context.setSessionVar(cdKey, endTime);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "GIVE_ITEM"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return CompletableFuture.completedFuture(null);
                String itemId = (String) params.get("item_id");
                Number amountNum = (Number) params.get("amount");
                int amount = amountNum != null ? amountNum.intValue() : 1;

                PlayerRef playerRef = context.getPlayer();
                Ref<EntityStore> pRef = playerRef.getReference();
                Store<EntityStore> pStore = pRef.getStore();
                Player p = pStore.getComponent(pRef, Player.getComponentType());
                if (p != null) {
                    ItemStack stack = new ItemStack(itemId, amount);
                    InventoryComponent.Hotbar hotbar = pStore.getComponent(pRef, InventoryComponent.Hotbar.getComponentType());
                    InventoryComponent.Storage storage = pStore.getComponent(pRef, InventoryComponent.Storage.getComponentType());
                    if (hotbar != null && hotbar.getInventory().canAddItemStack(stack)) {
                        hotbar.getInventory().addItemStack(stack);
                    } else if (storage != null && storage.getInventory().canAddItemStack(stack)) {
                        storage.getInventory().addItemStack(stack);
                    } else {
                        playerRef.sendMessage(Message.raw("Your inventory is full!").color(Color.RED));
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "TAKE_ITEM"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return CompletableFuture.completedFuture(null);
                String itemId = (String) params.get("item_id");
                Number amountNum = (Number) params.get("amount");
                int amount = amountNum != null ? amountNum.intValue() : 1;

                PlayerRef playerRef = context.getPlayer();
                Ref<EntityStore> pRef = playerRef.getReference();
                Player p = pRef.getStore().getComponent(pRef, Player.getComponentType());
                if (p != null) {
                    CombinedItemContainer container = InventoryComponent.getCombined(pRef.getStore(), pRef, InventoryComponent.HOTBAR_FIRST);
                    if (container != null) {
                        removeItems(container, itemId, amount);
                    }
                }
                return CompletableFuture.completedFuture(null);
            }

            private void removeItems(ItemContainer container, String itemId, int amountToRemove) {
                int remaining = amountToRemove;
                for (short i = 0; i < container.getCapacity(); i++) {
                    ItemStack item = container.getItemStack(i);
                    if (item != null && !item.isEmpty() && itemId.equals(item.getItem().getId())) {
                        int qty = item.getQuantity();
                        if (qty <= remaining) {
                            container.removeItemStackFromSlot(i);
                            remaining -= qty;
                        } else {
                            container.removeItemStackFromSlot(i, remaining);
                            remaining = 0;
                        }
                        if (remaining <= 0) return;
                    }
                }
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "DROP_ITEM"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String itemId = (String) params.get("item_id");
                Number amountNum = (Number) params.get("amount");
                int amount = amountNum != null ? amountNum.intValue() : 1;
                Number xNum = (Number) params.get("x");
                Number yNum = (Number) params.get("y");
                Number zNum = (Number) params.get("z");

                double x = xNum != null ? xNum.doubleValue() : (context.getCitizen() != null ? context.getCitizen().getPosition().x : 0.0);
                double y = yNum != null ? yNum.doubleValue() : (context.getCitizen() != null ? context.getCitizen().getPosition().y : 0.0);
                double z = zNum != null ? zNum.doubleValue() : (context.getCitizen() != null ? context.getCitizen().getPosition().z : 0.0);

                ItemStack itemStack = new ItemStack(itemId, amount);
                Holder<EntityStore>[] entities = ItemComponent.generateItemDrops(
                    context.getStore(), new ArrayList<>(List.of(itemStack)), new Vector3d(x, y, z), new Rotation3f());
                context.getStore().addEntities(entities, AddReason.SPAWN);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "ADD_TAG"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String target = (String) params.get("target");
                String tag = (String) params.get("tag");
                if (tag == null) return CompletableFuture.completedFuture(null);

                if ("PLAYER".equalsIgnoreCase(target) && context.getPlayer() != null) {
                    UUID uuid = context.getPlayer().getUuid();
                    Object obj = VariableManager.get().getPlayerVar(uuid, "tags");
                    List<Object> tagsList = obj instanceof List ? new ArrayList<>((List<?>) obj) : new ArrayList<>();
                    if (!tagsList.contains(tag)) {
                        tagsList.add(tag);
                        VariableManager.get().setPlayerVar(uuid, "tags", tagsList);
                    }
                } else if (context.getCitizen() != null) {
                    Object obj = VariableManager.get().getCitizenVar(context.getCitizen(), "tags");
                    List<Object> tagsList = obj instanceof List ? new ArrayList<>((List<?>) obj) : new ArrayList<>();
                    if (!tagsList.contains(tag)) {
                        tagsList.add(tag);
                        VariableManager.get().setCitizenVar(context.getCitizen(), "tags", tagsList);
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "REMOVE_TAG"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String target = (String) params.get("target");
                String tag = (String) params.get("tag");
                if (tag == null) return CompletableFuture.completedFuture(null);

                if ("PLAYER".equalsIgnoreCase(target) && context.getPlayer() != null) {
                    UUID uuid = context.getPlayer().getUuid();
                    Object obj = VariableManager.get().getPlayerVar(uuid, "tags");
                    if (obj instanceof List) {
                        List<Object> tagsList = new ArrayList<>((List<?>) obj);
                        if (tagsList.remove(tag)) {
                            VariableManager.get().setPlayerVar(uuid, "tags", tagsList);
                        }
                    }
                } else if (context.getCitizen() != null) {
                    Object obj = VariableManager.get().getCitizenVar(context.getCitizen(), "tags");
                    if (obj instanceof List) {
                        List<Object> tagsList = new ArrayList<>((List<?>) obj);
                        if (tagsList.remove(tag)) {
                            VariableManager.get().setCitizenVar(context.getCitizen(), "tags", tagsList);
                        }
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SAVE_LOCATION"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String targetType = (String) params.getOrDefault("target", "CITIZEN");
                String varName = (String) params.get("variable_name");
                String scope = (String) params.getOrDefault("scope", "CITIZEN");

                Vector3d pos = null;
                UUID worldUuid = null;

                if ("PLAYER".equalsIgnoreCase(targetType) && context.getPlayer() != null) {
                    pos = context.getPlayer().getTransform().getPosition();
                    worldUuid = context.getPlayer().getWorldUuid();
                } else if (context.getCitizen() != null) {
                    CitizenData citizen = context.getCitizen();
                    pos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
                    worldUuid = citizen.getWorldUUID();
                }

                if (pos != null && worldUuid != null && varName != null) {
                    Map<String, Object> locMap = Map.of(
                        "x", pos.x,
                        "y", pos.y,
                        "z", pos.z,
                        "world", worldUuid.toString()
                    );
                    String val = gson.toJson(locMap);

                    if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                        VariableManager.get().setPlayerVar(context.getPlayer().getUuid(), varName, val);
                    } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                        VariableManager.get().setCitizenVar(context.getCitizen(), varName, val);
                    } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                        VariableManager.get().setGlobalVar(varName, val);
                    } else if ("SESSION".equalsIgnoreCase(scope)) {
                        context.setSessionVar(varName, val);
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "RETURN_TO_LOCATION"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getCitizen() == null) return CompletableFuture.completedFuture(null);

                String varName = (String) params.get("variable_name");
                String scope = (String) params.getOrDefault("scope", "CITIZEN");
                Number speedNum = (Number) params.getOrDefault("speed", 1.0);

                Object val = null;
                if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                    val = VariableManager.get().getPlayerVar(context.getPlayer().getUuid(), varName);
                } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                    val = VariableManager.get().getCitizenVar(context.getCitizen(), varName);
                } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                    val = VariableManager.get().getGlobalVar(varName);
                } else if ("SESSION".equalsIgnoreCase(scope)) {
                    val = context.getSessionVar(varName);
                }

                if (val instanceof String) {
                    try {
                        Map<String, Object> locMap = gson.fromJson((String) val, Map.class);
                        double x = ((Number) locMap.get("x")).doubleValue();
                        double y = ((Number) locMap.get("y")).doubleValue();
                        double z = ((Number) locMap.get("z")).doubleValue();
                        
                        UUID destWorldUuid = null;
                        if (locMap.containsKey("world")) {
                            try {
                                destWorldUuid = UUID.fromString((String) locMap.get("world"));
                            } catch (Exception ignored) {}
                        }

                        CitizenData citizen = context.getCitizen();
                        if (destWorldUuid != null && !destWorldUuid.equals(citizen.getWorldUUID())) {
                            // Cross-world teleport
                            HyCitizensPlugin.get().getCitizensManager().despawnCitizen(citizen);
                            citizen.setWorldUUID(destWorldUuid);
                            citizen.setPosition(new Vector3d(x, y, z));
                            citizen.setCurrentPosition(new Vector3d(x, y, z));
                            HyCitizensPlugin.get().getCitizensManager().saveCitizen(citizen);
                            HyCitizensPlugin.get().getCitizensManager().spawnCitizen(citizen, false);
                        } else {
                            // Same world movement
                            citizen.setPosition(new Vector3d(x, y, z));
                            HyCitizensPlugin.get().getCitizensManager().saveCitizen(citizen);
                            MovementBehavior mb = new MovementBehavior("WANDER", speedNum.floatValue(), 0f, 0f, 0f);
                            citizen.setMovementBehavior(mb);
                            HyCitizensPlugin.get().getCitizensManager().updateCitizenMoveTarget(citizen.getId(), new Vector3d(x, y, z));
                        }
                    } catch (Exception e) {
                        getLogger().atWarning().log("Failed to parse saved location in RETURN_TO_LOCATION: " + e.getMessage());
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "GET_POSITION"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String targetType = (String) params.getOrDefault("target", "CITIZEN");
                String xVar = (String) params.get("x_var");
                String yVar = (String) params.get("y_var");
                String zVar = (String) params.get("z_var");
                String worldVar = (String) params.get("world_var");
                String scope = (String) params.getOrDefault("scope", "CITIZEN");

                Vector3d pos = null;
                UUID worldUuid = null;

                if ("PLAYER".equalsIgnoreCase(targetType) && context.getPlayer() != null) {
                    pos = context.getPlayer().getTransform().getPosition();
                    worldUuid = context.getPlayer().getWorldUuid();
                } else if (context.getCitizen() != null) {
                    CitizenData citizen = context.getCitizen();
                    pos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
                    worldUuid = citizen.getWorldUUID();
                }

                if (pos != null) {
                    if (xVar != null && !xVar.isEmpty()) {
                        saveVar(scope, xVar, pos.x, context);
                    }
                    if (yVar != null && !yVar.isEmpty()) {
                        saveVar(scope, yVar, pos.y, context);
                    }
                    if (zVar != null && !zVar.isEmpty()) {
                        saveVar(scope, zVar, pos.z, context);
                    }
                    if (worldUuid != null && worldVar != null && !worldVar.isEmpty()) {
                        saveVar(scope, worldVar, worldUuid.toString(), context);
                    }
                }
                return CompletableFuture.completedFuture(null);
            }

            private void saveVar(String scope, String name, Object val, ScriptContext context) {
                String valStr = val.toString();
                if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                    VariableManager.get().setPlayerVar(context.getPlayer().getUuid(), name, valStr);
                } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                    VariableManager.get().setCitizenVar(context.getCitizen(), name, valStr);
                } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                    VariableManager.get().setGlobalVar(name, valStr);
                } else if ("SESSION".equalsIgnoreCase(scope)) {
                    context.setSessionVar(name, valStr);
                }
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "BREAK_BLOCK"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                Number xVal = (Number) params.get("x");
                Number yVal = (Number) params.get("y");
                Number zVal = (Number) params.get("z");
                if (xVal == null || yVal == null || zVal == null) return CompletableFuture.completedFuture(null);

                try {
                    context.getWorld().breakBlock(xVal.intValue(), yVal.intValue(), zVal.intValue(), SetBlockSettings.FORCE_CHANGED);
                } catch (Exception _) {}
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "PLACE_BLOCK"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                Number xVal = (Number) params.get("x");
                Number yVal = (Number) params.get("y");
                Number zVal = (Number) params.get("z");
                String blockId = (String) params.get("block_id");
                if (xVal == null || yVal == null || zVal == null || blockId == null) return CompletableFuture.completedFuture(null);
                
                try {
                    context.getWorld().setBlock(xVal.intValue(), yVal.intValue(), zVal.intValue(), blockId);
                } catch (Exception _) {}
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "LOOK_AT"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                CitizenData citizen = context.getCitizen();
                if (citizen == null) return CompletableFuture.completedFuture(null);
                
                Vector3d targetPos = null;
                Object target = params.get("target");
                if ("PLAYER".equalsIgnoreCase(String.valueOf(target)) && context.getPlayer() != null) {
                    targetPos = context.getPlayer().getTransform().getPosition();
                } else if (target instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) target;
                    Number x = (Number) map.get("x");
                    Number y = (Number) map.get("y");
                    Number z = (Number) map.get("z");
                    if (x != null && y != null && z != null) {
                        targetPos = new Vector3d(x.doubleValue(), y.doubleValue(), z.doubleValue());
                    }
                }
                
                if (targetPos != null) {
                    Vector3d citizenPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
                    double dx = targetPos.x - citizenPos.x;
                    double dy = targetPos.y - citizenPos.y;
                    double dz = targetPos.z - citizenPos.z;
                    float yaw = (float) (Math.atan2(dx, dz) + Math.PI);
                    float pitch = (float) (Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                    
                    HyCitizensPlugin.get().getCitizensManager().teleportCitizen(citizen, citizenPos, new Vector3f(yaw, pitch, 0f));
                }
                return CompletableFuture.completedFuture(null);
            }
        });



        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "PLAY_SOUND"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String soundId = (String) params.get("sound_id");
                if (soundId == null) return CompletableFuture.completedFuture(null);
                String categoryStr = (String) params.getOrDefault("category", "SFX");
                String mode = (String) params.getOrDefault("mode", "3D");
                Number volumeNum = (Number) params.getOrDefault("volume", 1.0);
                Number pitchNum = (Number) params.getOrDefault("pitch", 1.0);
                
                SoundCategory category = SoundCategory.SFX;
                try {
                    category = SoundCategory.valueOf(categoryStr.toUpperCase());
                } catch (Exception ignored) {}
                
                CitizenData citizen = context.getCitizen();
                Vector3d pos = citizen != null ? (citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition()) : new Vector3d();
                
                try {
                    int index = Integer.parseInt(soundId);
                    if ("2D".equalsIgnoreCase(mode) && context.getPlayer() != null) {
                        SoundUtil.playSoundEvent2dToPlayer(context.getPlayer(), index, category, volumeNum.floatValue(), pitchNum.floatValue());
                    } else {
                        SoundUtil.playSoundEvent3d(index, category, pos, context.getStore());
                    }
                } catch (NumberFormatException e) {
                    getLogger().atWarning().log("Invalid sound ID (must be an integer index): " + soundId);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "PLAY_PARTICLE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return CompletableFuture.completedFuture(null);
                String systemId = (String) params.get("particle_system");
                if (systemId == null) return CompletableFuture.completedFuture(null);
                
                Number ox = (Number) params.getOrDefault("offset_x", 0.0);
                Number oy = (Number) params.getOrDefault("offset_y", 0.0);
                Number oz = (Number) params.getOrDefault("offset_z", 0.0);
                String targetNode = (String) params.getOrDefault("target_node", "Head");
                boolean detached = getBooleanParam(params.get("detached"), false);
                
                // Todo: Add this
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SPAWN_ENTITY"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String role = (String) params.get("role");
                Number xVal = (Number) params.get("x");
                Number yVal = (Number) params.get("y");
                Number zVal = (Number) params.get("z");
                String saveRefVar = (String) params.get("save_ref_variable");
                if (role == null || role.trim().isEmpty() || xVal == null || yVal == null || zVal == null) return CompletableFuture.completedFuture(null);

                double x = xVal.doubleValue();
                double y = yVal.doubleValue();
                double z = zVal.doubleValue();

                try {
                    NPCPlugin npcPlugin = NPCPlugin.get();
                    if (npcPlugin != null) {
                        Pair<Ref<EntityStore>, INonPlayerCharacter> pair =
                            npcPlugin.spawnNPC(context.getStore(), role, null, new Vector3d(x, y, z), new Rotation3f());
                        if (pair != null && saveRefVar != null && !saveRefVar.isEmpty()) {
                            Ref<EntityStore> spawnedRef = pair.first();
                            if (spawnedRef != null && spawnedRef.isValid()) {
                                UUIDComponent uuidComp = context.getStore().getComponent(spawnedRef, UUIDComponent.getComponentType());
                                String refId = uuidComp != null ? uuidComp.getUuid().toString() : spawnedRef.toString();
                                context.setSessionVar(saveRefVar, refId);
                            }
                        }
                    }
                } catch (Exception e) {
                    getLogger().atWarning().log("[HyCitizens] Failed to spawn NPC of role '" + role + "': " + e.getMessage());
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "DESPAWN"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String target = (String) params.getOrDefault("target", "CITIZEN");
                if ("CITIZEN".equalsIgnoreCase(target) && context.getCitizen() != null) {
                    Ref<EntityStore> ref = context.getCitizen().getNpcRef();
                    if (ref != null && ref.isValid()) {
                        ref.getStore().removeEntity(ref, RemoveReason.REMOVE);
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "MODIFY_MAX_HEALTH"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String targetType = (String) params.getOrDefault("target", "CITIZEN");
                Number amountNum = (Number) params.get("amount");
                String modifierId = (String) params.getOrDefault("modifier_id", "script_bonus_health");
                if (amountNum == null) return CompletableFuture.completedFuture(null);
                
                Ref<EntityStore> ref = "PLAYER".equalsIgnoreCase(targetType)
                    ? (context.getPlayer() != null ? context.getPlayer().getReference() : null)
                    : (context.getCitizen() != null ? context.getCitizen().getNpcRef() : null);
                    
                if (ref != null && ref.isValid()) {
                    EntityStatMap statMap = ref.getStore().getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
                    if (statMap != null) {
                        try {
                            StaticModifier modifier = new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, amountNum.floatValue());
                            statMap.putModifier(DefaultEntityStatTypes.getHealth(), "hycitizens_" + modifierId, modifier);
                        } catch (Exception ignored) {}
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "RUN_ASYNC"; }
            @SuppressWarnings("unchecked")
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                List<ScriptAction> subActions = (List<ScriptAction>) params.get("_sub_actions");
                if (subActions == null || subActions.isEmpty()) return CompletableFuture.completedFuture(null);
                
                executeActions(subActions, context);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "CALL_TRIGGER"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String targetCitizen = (String) params.getOrDefault("target_citizen", "THIS");
                String eventName = (String) params.get("event_name");
                Map<String, Object> args = (Map<String, Object>) params.get("args");
                if (eventName == null) return CompletableFuture.completedFuture(null);
                
                if ("THIS".equalsIgnoreCase(targetCitizen)) {
                    fireTrigger(context.getCitizen(), "ON_CUSTOM", Map.of("event_name", eventName, "args", args != null ? args : Map.of()), context.getPlayer(), context.getStore());
                } else if ("GLOBAL".equalsIgnoreCase(targetCitizen)) {
                    for (CitizenData citizen : HyCitizensPlugin.get().getCitizensManager().getAllCitizens()) {
                        fireTrigger(citizen, "ON_CUSTOM", Map.of("event_name", eventName, "args", args != null ? args : Map.of()), context.getPlayer(), context.getStore());
                    }
                } else {
                    CitizenData citizen = HyCitizensPlugin.get().getCitizensManager().getCitizen(targetCitizen);
                    if (citizen != null) {
                        fireTrigger(citizen, "ON_CUSTOM", Map.of("event_name", eventName, "args", args != null ? args : Map.of()), context.getPlayer(), context.getStore());
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SEND_SIGNAL_TO_CITIZEN"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String citizenId = (String) params.get("citizen_id");
                String signalName = (String) params.get("signal_name");
                Map<String, Object> args = (Map<String, Object>) params.get("args");
                if (citizenId == null || signalName == null) return CompletableFuture.completedFuture(null);

                CitizenData target = HyCitizensPlugin.get().getCitizensManager().getCitizen(citizenId);
                if (target == null) {
                    getLogger().atWarning().log("SEND_SIGNAL_TO_CITIZEN target not found: " + citizenId);
                    return CompletableFuture.completedFuture(null);
                }

                fireTrigger(target, "ON_SIGNAL", Map.of(
                    "signal_name", signalName,
                    "args", args != null ? args : Map.of()
                ), context.getPlayer(), context.getStore());
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "CANCEL_TASK"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "CANCEL_ALL_SCRIPTS"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "ENABLE_SCRIPT"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String citizenId = (String) params.getOrDefault("citizen_id", "THIS");
                String scriptId = (String) params.get("script_id");
                if (scriptId == null) return CompletableFuture.completedFuture(null);
                
                CitizenData citizen = "THIS".equalsIgnoreCase(citizenId) ? context.getCitizen() : HyCitizensPlugin.get().getCitizensManager().getCitizen(citizenId);
                if (citizen != null && citizen.getScripts() != null) {
                    for (ScriptBlock script : citizen.getScripts()) {
                        if (scriptId.equals(script.getId())) {
                            script.setEnabled(true);
                            HyCitizensPlugin.get().getCitizensManager().saveCitizen(citizen);
                            break;
                        }
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "DISABLE_SCRIPT"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String citizenId = (String) params.getOrDefault("citizen_id", "THIS");
                String scriptId = (String) params.get("script_id");
                if (scriptId == null) return CompletableFuture.completedFuture(null);
                
                CitizenData citizen = "THIS".equalsIgnoreCase(citizenId) ? context.getCitizen() : HyCitizensPlugin.get().getCitizensManager().getCitizen(citizenId);
                if (citizen != null && citizen.getScripts() != null) {
                    for (ScriptBlock script : citizen.getScripts()) {
                        if (scriptId.equals(script.getId())) {
                            script.setEnabled(false);
                            HyCitizensPlugin.get().getCitizensManager().saveCitizen(citizen);
                            break;
                        }
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "STOP_ANIMATION"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                CitizenData citizen = context.getCitizen();
                if (citizen == null || citizen.getNpcRef() == null) return CompletableFuture.completedFuture(null);
                Number slotNum = (Number) params.get("slot");
                int slot = slotNum != null ? slotNum.intValue() : 2;
                String returnAnim = (String) params.getOrDefault("return_animation", "Idle");
                AnimationUtils.playAnimation(citizen.getNpcRef(), AnimationSlot.values()[slot], returnAnim, false, context.getStore());
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "CHANGE_MODEL"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String modelId = (String) params.get("model_id");
                if (modelId != null && context.getCitizen() != null) {
                    context.getCitizen().setModelId(modelId);
                    HyCitizensPlugin.get().getCitizensManager().saveCitizen(context.getCitizen());
                    HyCitizensPlugin.get().getCitizensManager().updateSpawnedCitizen(context.getCitizen(), true);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "CHANGE_SKIN"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String skin = (String) params.get("skin_username");
                if (skin != null && context.getCitizen() != null) {
                    context.getCitizen().setSkinUsername(skin);
                    HyCitizensPlugin.get().getCitizensManager().saveCitizen(context.getCitizen());
                    HyCitizensPlugin.get().getCitizensManager().updateSpawnedCitizen(context.getCitizen(), true);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_ATTITUDE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String attitude = (String) params.get("attitude");
                if (attitude != null && context.getCitizen() != null) {
                    context.getCitizen().setAttitude(attitude.toUpperCase());
                    HyCitizensPlugin.get().getCitizensManager().saveCitizen(context.getCitizen());
                    HyCitizensPlugin.get().getCitizensManager().updateCitizenNPC(context.getCitizen(), true);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_INVULNERABLE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (params.containsKey("invulnerable") && context.getCitizen() != null) {
                    boolean invulnerable = getBooleanParam(params.get("invulnerable"), true);
                    context.getCitizen().setTakesDamage(!invulnerable);
                    HyCitizensPlugin.get().getCitizensManager().saveCitizen(context.getCitizen());
                    HyCitizensPlugin.get().getCitizensManager().updateSpawnedCitizen(context.getCitizen(), true);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_SLEEPING"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (params.containsKey("sleeping") && context.getCitizen() != null) {
                    boolean sleeping = getBooleanParam(params.get("sleeping"), true);
                    String anim = sleeping ? "Sleep" : "Idle";
                    AnimationUtils.playAnimation(context.getCitizen().getNpcRef(), AnimationSlot.values()[1], anim, false, context.getStore());
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_CITIZEN_NAME"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String name = (String) params.get("name");
                if (name != null && context.getCitizen() != null) {
                    String resolved = ScriptExpressionEvaluator.resolve(name, context);
                    context.getCitizen().setName(resolved);
                    HyCitizensPlugin.get().getCitizensManager().saveCitizen(context.getCitizen());
                    HyCitizensPlugin.get().getCitizensManager().refreshNpcNameplate(context.getCitizen());
                    HyCitizensPlugin.get().getCitizensManager().updateSpawnedCitizenHologram(context.getCitizen(), true);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SHOW_TITLE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return CompletableFuture.completedFuture(null);
                String title = (String) params.get("title");
                String subtitle = (String) params.getOrDefault("subtitle", "");

                EventTitleUtil.showEventTitleToPlayer(context.getPlayer(), Message.raw(title), Message.raw(subtitle), true);
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_WEATHER"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String weather = (String) params.get("weather");
                if (weather == null) return CompletableFuture.completedFuture(null);

                if (weather.equals("CLEAR")) {
                    WeatherResource weatherResource = context.getStore().getResource(WeatherResource.getResourceType());
                    weatherResource.setForcedWeather(null);
                    WorldConfig config = context.getWorld().getWorldConfig();
                    config.setForcedWeather(null);
                    config.markChanged();
                }

                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_TIME"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                Number time24h = (Number) params.get("time_24h");
                if (time24h == null) return CompletableFuture.completedFuture(null);
                WorldTimeResource timeResource = context.getStore().getResource(WorldTimeResource.getResourceType());
                timeResource.setDayTime(time24h.floatValue() / 24.0f, context.getWorld(), context.getStore());
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "LIST_ADD"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String scope = (String) params.get("scope");
                String name = (String) params.get("name");
                Object val = params.get("value");
                if (name == null || val == null) return CompletableFuture.completedFuture(null);
                
                Object current = null;
                if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                    current = VariableManager.get().getPlayerVar(context.getPlayer().getUuid(), name);
                } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                    current = VariableManager.get().getCitizenVar(context.getCitizen(), name);
                } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                    current = VariableManager.get().getGlobalVar(name);
                } else if ("SESSION".equalsIgnoreCase(scope)) {
                    current = context.getSessionVar(name);
                }
                
                List<Object> list = current instanceof List ? new ArrayList<>((List<?>) current) : new ArrayList<>();
                list.add(normalizeValue(val));
                
                if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                    VariableManager.get().setPlayerVar(context.getPlayer().getUuid(), name, list);
                } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                    VariableManager.get().setCitizenVar(context.getCitizen(), name, list);
                } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                    VariableManager.get().setGlobalVar(name, list);
                } else if ("SESSION".equalsIgnoreCase(scope)) {
                    context.setSessionVar(name, list);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "LIST_REMOVE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String scope = (String) params.get("scope");
                String name = (String) params.get("name");
                Object val = params.get("value");
                if (name == null || val == null) return CompletableFuture.completedFuture(null);
                
                Object current = null;
                if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                    current = VariableManager.get().getPlayerVar(context.getPlayer().getUuid(), name);
                } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                    current = VariableManager.get().getCitizenVar(context.getCitizen(), name);
                } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                    current = VariableManager.get().getGlobalVar(name);
                } else if ("SESSION".equalsIgnoreCase(scope)) {
                    current = context.getSessionVar(name);
                }
                
                if (current instanceof List) {
                    List<Object> list = new ArrayList<>((List<?>) current);
                    list.remove(normalizeValue(val));
                    
                    if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                        VariableManager.get().setPlayerVar(context.getPlayer().getUuid(), name, list);
                    } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                        VariableManager.get().setCitizenVar(context.getCitizen(), name, list);
                    } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                        VariableManager.get().setGlobalVar(name, list);
                    } else if ("SESSION".equalsIgnoreCase(scope)) {
                        context.setSessionVar(name, list);
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "MAKE_HTTP_REQUEST"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String url = (String) params.get("url");
                String method = (String) params.getOrDefault("method", "GET");
                String resultVar = (String) params.get("result_variable");
                String scope = (String) params.getOrDefault("result_scope", "SESSION");
                if (url == null || resultVar == null) return CompletableFuture.completedFuture(null);

                if (!HyCitizensPlugin.get().getConfigManager().getBoolean("scripting.http.enabled", false)) {
                    return CompletableFuture.failedFuture(new SecurityException("MAKE_HTTP_REQUEST is disabled"));
                }

                URI uri;
                try {
                    uri = validateHttpUri(url);
                } catch (Exception error) {
                    return CompletableFuture.failedFuture(error);
                }
                if (!allowHttpRequest()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("HTTP scripting rate limit exceeded"));
                }
                if (!httpConcurrency.tryAcquire()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("HTTP scripting concurrency limit exceeded"));
                }

                HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(5));
                if ("POST".equalsIgnoreCase(method)) {
                    Object payload = params.get("payload");
                    builder.header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8));
                } else {
                    builder.GET();
                }

                CompletableFuture<Void> future = new CompletableFuture<>();
                getLogger().atInfo().log("[HyCitizens] Privileged HTTP script request to " + uri.getScheme() + "://" + uri.getHost());
                httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(res -> {
                        try {
                            int maxBytes = Math.max(1_024, HyCitizensPlugin.get().getConfigManager()
                                    .getInt("scripting.http.max_response_bytes", 262_144));
                            byte[] bytes = res.body().readNBytes(maxBytes + 1);
                            if (bytes.length > maxBytes) {
                                throw new IOException("HTTP response exceeded " + maxBytes + " bytes");
                            }
                            String body = new String(bytes, StandardCharsets.UTF_8);
                            Object val = body;
                            try {
                                val = gson.fromJson(body, Map.class);
                            } catch (Exception e) {
                                try {
                                    val = gson.fromJson(body, List.class);
                                } catch (Exception ignored) {}
                            }
                            final Object finalVal = val;
                            Runnable storeResult = () -> {
                            if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
                                VariableManager.get().setPlayerVar(context.getPlayer().getUuid(), resultVar, finalVal);
                            } else if ("CITIZEN".equalsIgnoreCase(scope)) {
                                VariableManager.get().setCitizenVar(context.getCitizen(), resultVar, finalVal);
                            } else if ("GLOBAL".equalsIgnoreCase(scope)) {
                                VariableManager.get().setGlobalVar(resultVar, finalVal);
                            } else {
                                context.setSessionVar(resultVar, finalVal);
                            }
                            future.complete(null);
                            };
                            if (context.getWorld() != null) context.getWorld().execute(storeResult); else storeResult.run();
                        } catch (Exception error) {
                            future.completeExceptionally(error);
                        } finally {
                            httpConcurrency.release();
                            try { res.body().close(); } catch (IOException ignored) {}
                        }
                    }).exceptionally(ex -> {
                        httpConcurrency.release();
                        future.completeExceptionally(ex);
                        return null;
                    });
                return future;
            }
        }, new ActionTypeDescriptor(
                "MAKE_HTTP_REQUEST", 1, "hycitizens", "Privileged", "Restricted outbound HTTP request",
                Map.of("type", "object", "required", List.of("url", "result_variable")),
                Map.of("warning", "Disabled by default and restricted by server allowlist"), true
        ));

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "START_PRESET_DIALOGUE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return CompletableFuture.completedFuture(null);
                String dialogueId = (String) params.get("dialogue_id");
                if (dialogueId == null) dialogueId = (String) params.get("id");
                if (dialogueId == null || dialogueId.isEmpty()) {
                    getLogger().atWarning().log("[HyCitizens] START_PRESET_DIALOGUE executed with null or empty dialogue_id!");
                    return CompletableFuture.completedFuture(null);
                }

                IDialogue dialogue = DialogueManager.get().getDialogue(dialogueId);
                if (dialogue == null) {
                    getLogger().atWarning().log("[HyCitizens] No dialogue found with ID: " + dialogueId);
                    context.getPlayer().sendMessage(Message.raw("[Dialogue Error] No preset dialogue found with ID: " + dialogueId).color(Color.RED));
                    return CompletableFuture.completedFuture(null);
                }

                String npcId = context.getCitizen() != null ? context.getCitizen().getId() : null;
                DialogueManager.get().startDialogueSession(context.getPlayer(), dialogue, context, npcId);
                return CompletableFuture.completedFuture(null);
            }
        });

        // SET_CITIZEN_DIALOGUE Action
        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "SET_CITIZEN_DIALOGUE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String citizenId = (String) params.get("citizen_id");
                String dialogueId = (String) params.get("dialogue_id");
                if (dialogueId == null) dialogueId = (String) params.get("id");

                CitizenData citizen = null;
                if (citizenId != null && !citizenId.isEmpty()) {
                    citizen = HyCitizensPlugin.get().getCitizensManager().getCitizen(citizenId);
                } else if (context.getCitizen() != null) {
                    citizen = context.getCitizen();
                }

                if (citizen == null) {
                    getLogger().atWarning().log("[HyCitizens] SET_CITIZEN_DIALOGUE executed with no valid citizen!");
                    return CompletableFuture.completedFuture(null);
                }

                String owner = "visual-script:set-dialog:" + citizen.getId();
                if ("none".equalsIgnoreCase(dialogueId) || "null".equalsIgnoreCase(dialogueId) || dialogueId == null || dialogueId.isEmpty()) {
                    DialogueManager.get().removeOverridesByOwner(owner);
                } else {
                    DialogueManager.get().removeOverridesByOwner(owner);
                    DialogueManager.get().addOverride(new DialogOverride(
                            UUID.randomUUID(),
                            DialogOverride.Scope.NPC,
                            citizen.getId(),
                            dialogueId,
                            getAsInt(params.get("priority"), 0),
                            owner,
                            getAsLong(params.get("expires_at"), 0L)
                    ));
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        // START_QUEST Action
        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "START_QUEST"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return CompletableFuture.completedFuture(null);
                String questId = (String) params.get("quest_id");
                if (questId == null) questId = (String) params.get("id");
                if (questId == null || questId.isEmpty()) return CompletableFuture.completedFuture(null);

                try {
                    QuestIntegration.startQuest(context.getPlayer(), questId);
                } catch (QuestIntegration.QuestIntegrationException e) {
                    getLogger().atWarning().log("[HyCitizens] START_QUEST failed for '" + questId + "': " + e.getMessage());
                    return CompletableFuture.failedFuture(e);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        // PROGRESS_QUEST_OBJECTIVE Action
        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "PROGRESS_QUEST_OBJECTIVE"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return CompletableFuture.completedFuture(null);
                String rawType = (String) params.get("type");
                String target = (String) params.get("target");
                int amount = getAsInt(params.get("amount"), 1);
                if (rawType == null || rawType.isEmpty()) return CompletableFuture.completedFuture(null);

                try {
                    QuestIntegration.progressObjective(
                            context.getPlayer(),
                            rawType,
                            target != null ? target : "",
                            amount
                    );
                } catch (QuestIntegration.QuestIntegrationException | IllegalArgumentException e) {
                    getLogger().atWarning().log("[HyCitizens] PROGRESS_QUEST_OBJECTIVE failed: " + e.getMessage());
                    return CompletableFuture.failedFuture(e);
                }
                return CompletableFuture.completedFuture(null);
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "APPLY_DIALOG_PATCH"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                try {
                    String dialogId = Objects.toString(params.get("dialog_id"), "");
                    String nodeId = Objects.toString(params.get("node_id"), "");
                    String operation = Objects.toString(params.get("operation"), "");
                    String scopeName = Objects.toString(params.getOrDefault("scope", "PLAYER"), "PLAYER");
                    if (dialogId.isEmpty() || nodeId.isEmpty() || operation.isEmpty()) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("Dialog patch requires dialog_id, node_id, and operation"));
                    }
                    DialogPatch.Scope scope =
                            DialogPatch.Scope.valueOf(scopeName.toUpperCase(Locale.ROOT));
                    String scopeId = switch (scope) {
                        case GLOBAL -> "";
                        case NPC -> context.getCitizen() != null ? context.getCitizen().getId() : "";
                        case PLAYER -> context.getPlayer() != null ? context.getPlayer().getUuid().toString() : "";
                        case SESSION -> Objects.toString(params.get("session_id"), "");
                    };
                    UUID patchId = params.get("patch_id") != null
                            ? UUID.fromString(params.get("patch_id").toString()) : UUID.randomUUID();
                    DialogPatch patch =
                            new DialogPatch(
                                    patchId, dialogId, scope, scopeId,
                                    Objects.toString(params.getOrDefault("owner", "visual-script"), "visual-script"),
                                    getAsInt(params.get("priority"), 0),
                                    getAsLong(params.get("expires_at"), 0L),
                                    DialogPatch.Operation.valueOf(operation.toUpperCase(Locale.ROOT)),
                                    nodeId, Objects.toString(params.get("response_id"), null),
                                    Objects.toString(params.get("value"), null)
                            );
                    DialogueManager.get().applyPatch(patch);
                    context.setSessionVar("last_dialog_patch_id", patchId.toString());
                    return CompletableFuture.completedFuture(null);
                } catch (RuntimeException error) {
                    return CompletableFuture.failedFuture(error);
                }
            }
        });

        registerAction(new ScriptActionHandler() {
            @Override public String getType() { return "REMOVE_DIALOG_PATCH"; }
            @Override public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> params) {
                String id = Objects.toString(params.getOrDefault("patch_id", context.getSessionVar("last_dialog_patch_id")), "");
                if (id.isEmpty()) return CompletableFuture.completedFuture(null);
                DialogueManager.get().removePatch(UUID.fromString(id));
                return CompletableFuture.completedFuture(null);
            }
        });

        // PLAYER_HAS_QUEST Condition
        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "PLAYER_HAS_QUEST"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String questId = (String) params.get("quest_id");
                if (questId == null) questId = (String) params.get("id");
                if (questId == null || questId.isEmpty()) return false;

                try {
                    return QuestIntegration.hasActiveQuest(context.getPlayer().getUuid(), questId);
                } catch (QuestIntegration.QuestIntegrationException e) {
                    getLogger().atWarning().log("[HyCitizens] PLAYER_HAS_QUEST failed for '" + questId + "': " + e.getMessage());
                    return false;
                }
            }
        });

        // PLAYER_COMPLETED_QUEST Condition
        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "PLAYER_COMPLETED_QUEST"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String questId = (String) params.get("quest_id");
                if (questId == null) questId = (String) params.get("id");
                if (questId == null || questId.isEmpty()) return false;

                try {
                    return QuestIntegration.hasCompletedQuest(context.getPlayer().getUuid(), questId);
                } catch (QuestIntegration.QuestIntegrationException e) {
                    getLogger().atWarning().log("[HyCitizens] PLAYER_COMPLETED_QUEST failed for '" + questId + "': " + e.getMessage());
                    return false;
                }
            }
        });

        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "PLAYER_REWARD_CLAIMED"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String questId = (String) params.getOrDefault("quest_id", params.get("id"));
                if (questId == null || questId.isEmpty()) return false;
                try {
                    return QuestIntegration.isRewardClaimed(context.getPlayer().getUuid(), questId);
                } catch (QuestIntegration.QuestIntegrationException error) {
                    getLogger().atWarning().log("[HyCitizens] PLAYER_REWARD_CLAIMED failed for '"
                            + questId + "': " + error.getMessage());
                    return false;
                }
            }
        });

        // HAS_SEEN_DIALOGUE Condition
        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "HAS_SEEN_DIALOGUE"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String dialogueId = (String) params.get("dialogue_id");
                if (dialogueId == null) dialogueId = (String) params.get("id");
                if (dialogueId == null || dialogueId.isEmpty()) return false;
                PlayerDialogState state = DialogueManager.get().getPlayerStateSnapshot(context.getPlayer().getUuid());
                return state != null && state.getSeenDialogs().contains(dialogueId);
            }
        });

        // HAS_COMPLETED_DIALOGUE Condition
        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "HAS_COMPLETED_DIALOGUE"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String dialogueId = (String) params.get("dialogue_id");
                if (dialogueId == null) dialogueId = (String) params.get("id");
                if (dialogueId == null || dialogueId.isEmpty()) return false;
                PlayerDialogState state = DialogueManager.get().getPlayerStateSnapshot(context.getPlayer().getUuid());
                return state != null && state.getCompletedDialogs().contains(dialogueId);
            }
        });

        // HAS_SEEN_DIALOGUE_NODE Condition
        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "HAS_SEEN_DIALOGUE_NODE"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String dialogueId = (String) params.get("dialogue_id");
                if (dialogueId == null) dialogueId = (String) params.get("id");
                String nodeId = (String) params.get("node_id");
                if (dialogueId == null || dialogueId.isEmpty() || nodeId == null || nodeId.isEmpty()) return false;
                PlayerDialogState state = DialogueManager.get().getPlayerStateSnapshot(context.getPlayer().getUuid());
                if (state == null) return false;
                Set<String> nodes = state.getSeenNodes().get(dialogueId);
                return nodes != null && nodes.contains(nodeId);
            }
        });

        // DIALOGUE_VISIT_COUNT Condition
        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "DIALOGUE_VISIT_COUNT"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String dialogueId = (String) params.get("dialogue_id");
                if (dialogueId == null) dialogueId = (String) params.get("id");
                if (dialogueId == null || dialogueId.isEmpty()) return false;
                int requiredCount = getAsInt(params.get("count"), getAsInt(params.get("amount"), 1));
                String operator = (String) params.getOrDefault("operator", "GREATER_EQUAL");
                PlayerDialogState state = DialogueManager.get().getPlayerStateSnapshot(context.getPlayer().getUuid());
                int actualCount = (state != null) ? state.getDialogVisits().getOrDefault(dialogueId, 0) : 0;
                return evaluateComparison(String.valueOf(actualCount), operator, String.valueOf(requiredCount));
            }
        });

        // DIALOGUE_RESPONSE_CHOSEN Condition
        registerCondition(new ScriptConditionHandler() {
            @Override public String getType() { return "DIALOGUE_RESPONSE_CHOSEN"; }
            @Override public boolean evaluate(ScriptContext context, Map<String, Object> params) {
                if (context.getPlayer() == null) return false;
                String dialogueId = (String) params.get("dialogue_id");
                if (dialogueId == null) dialogueId = (String) params.get("id");
                String responseId = (String) params.get("response_id");
                if (dialogueId == null || dialogueId.isEmpty() || responseId == null || responseId.isEmpty()) return false;
                PlayerDialogState state = DialogueManager.get().getPlayerStateSnapshot(context.getPlayer().getUuid());
                if (state == null) return false;
                Map<String, Integer> choices = state.getResponseChoices().get(dialogueId);
                return choices != null && choices.getOrDefault(responseId, 0) > 0;
            }
        });
    }


    private void runWhileIteration(ScriptCondition cond, List<ScriptAction> actions, int maxIterations, int iteration, ScriptContext context, CompletableFuture<Void> loopFuture) {
        if (iteration >= maxIterations) {
            getLogger().atWarning().log("WHILE_LOOP hit max iterations (" + maxIterations + ")");
            loopFuture.complete(null);
            return;
        }
        if (context.isStopped() || !evaluateCondition(cond, context)) {
            loopFuture.complete(null);
            return;
        }

        executeActions(actions, context).thenRun(() -> {
            if (context.isStopped()) {
                loopFuture.complete(null);
                return;
            }
            if (context.isBreakLoop()) {
                context.setBreakLoop(false);
                loopFuture.complete(null);
                return;
            }
            if (context.isContinueLoop()) {
                context.setContinueLoop(false);
            }

            // Yield execution to next tick using world.execute()
            context.getWorld().execute(() -> {
                runWhileIteration(cond, actions, maxIterations, iteration + 1, context, loopFuture);
            });
        });
    }

    private void runRepeatIteration(List<ScriptAction> actions, int count, int index, ScriptContext context, CompletableFuture<Void> repeatFuture) {
        if (index >= count || context.isStopped()) {
            repeatFuture.complete(null);
            return;
        }

        context.setSessionVar("loop:index", index);

        executeActions(actions, context).thenRun(() -> {
            if (context.isStopped()) {
                repeatFuture.complete(null);
                return;
            }
            if (context.isBreakLoop()) {
                context.setBreakLoop(false);
                repeatFuture.complete(null);
                return;
            }
            if (context.isContinueLoop()) {
                context.setContinueLoop(false);
            }

            // Yield execution to next tick using world.execute()
            context.getWorld().execute(() -> {
                runRepeatIteration(actions, count, index + 1, context, repeatFuture);
            });
        });
    }

    private void runForeachPlayerIteration(List<PlayerRef> players, List<ScriptAction> actions, int index, ScriptContext context, CompletableFuture<Void> loopFuture) {
        if (index >= players.size() || context.isStopped()) {
            loopFuture.complete(null);
            return;
        }

        PlayerRef player = players.get(index);
        ScriptContext subContext = new ScriptContext(context, player);
        subContext.setSessionVar("loop:index", index);
        subContext.setSessionVar("loop:item", player.getUsername());
        subContext.setSessionVar("loop:player_uuid", player.getUuid().toString());
        subContext.setSessionVar("loop:player_name", player.getUsername());
        if (player.getTransform() != null && player.getTransform().getPosition() != null) {
            subContext.setSessionVar("loop:player_x", player.getTransform().getPosition().x);
            subContext.setSessionVar("loop:player_y", player.getTransform().getPosition().y);
            subContext.setSessionVar("loop:player_z", player.getTransform().getPosition().z);
        }

        executeActions(actions, subContext).thenRun(() -> {
            if (subContext.isStopped()) {
                context.setStopped(true);
                loopFuture.complete(null);
                return;
            }
            if (subContext.isBreakLoop()) {
                loopFuture.complete(null);
                return;
            }

            context.getWorld().execute(() -> {
                runForeachPlayerIteration(players, actions, index + 1, context, loopFuture);
            });
        });
    }

    private void runForeachEntityIteration(List<Ref<EntityStore>> entities, List<ScriptAction> actions, int index, ScriptContext context, CompletableFuture<Void> loopFuture) {
        if (index >= entities.size() || context.isStopped()) {
            loopFuture.complete(null);
            return;
        }

        Ref<EntityStore> ref = entities.get(index);
        if (ref == null || !ref.isValid()) {
            context.getWorld().execute(() -> runForeachEntityIteration(entities, actions, index + 1, context, loopFuture));
            return;
        }

        TransformComponent transform = context.getStore().getComponent(ref, TransformComponent.getComponentType());
        context.setSessionVar("loop:index", index);
        context.setSessionVar("loop:item", getEntityLoopId(ref, context.getStore()));
        context.setSessionVar("loop:entity_id", getEntityLoopId(ref, context.getStore()));
        context.setSessionVar("loop:entity_type", getEntityTypeId(ref, context.getStore()));
        if (transform != null) {
            context.setSessionVar("loop:entity_x", transform.getPosition().x);
            context.setSessionVar("loop:entity_y", transform.getPosition().y);
            context.setSessionVar("loop:entity_z", transform.getPosition().z);
        }

        executeActions(actions, context).thenRun(() -> {
            if (context.isStopped()) {
                loopFuture.complete(null);
                return;
            }
            if (context.isBreakLoop()) {
                context.setBreakLoop(false);
                loopFuture.complete(null);
                return;
            }
            if (context.isContinueLoop()) {
                context.setContinueLoop(false);
            }

            context.getWorld().execute(() -> {
                runForeachEntityIteration(entities, actions, index + 1, context, loopFuture);
            });
        });
    }

    private List<Ref<EntityStore>> findEntitiesInRadius(ScriptContext context, double radius, String entityType) {
        CitizenData citizen = context.getCitizen();
        if (citizen == null) return Collections.emptyList();

        Vector3d pos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        List<Ref<EntityStore>> refs = new ArrayList<>();
        try {
            Selector.selectNearbyEntities(context.getStore(), pos, radius, ref -> {
                if (ref == null || !ref.isValid()) return;
                if (entityType == null || entityType.isBlank()) {
                    refs.add(ref);
                    return;
                }
                String actualType = getEntityTypeId(ref, context.getStore());
                if (actualType != null && actualType.equalsIgnoreCase(entityType)) {
                    refs.add(ref);
                }
            }, null);
        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] FOREACH_ENTITY_IN_RADIUS failed to collect entities: " + e.getMessage());
        }
        return refs;
    }

    private static String getEntityLoopId(Ref<EntityStore> ref, Store<EntityStore> store) {
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuid != null ? uuid.getUuid().toString() : ref.toString();
    }

    private static String getEntityTypeId(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            Archetype<EntityStore> archetype = store.getArchetype(ref);
            for (int i = archetype.getMinIndex(); i < archetype.length(); i++) {
                ComponentType<EntityStore, ?> componentType = archetype.get(i);
                if (componentType == null) continue;
                Class<?> typeClass = componentType.getTypeClass();
                if (Entity.class.isAssignableFrom(typeClass)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Entity> entityClass = (Class<? extends Entity>) typeClass;
                    String id = EntityModule.get().getIdentifier(entityClass);
                    return id != null ? id : entityClass.getSimpleName();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static final Set<String> NUMERIC_KEYS = Set.of(
        "x", "y", "z", "x1", "y1", "z1", "x2", "y2", "z2", 
        "yaw", "pitch", "amount", "duration_seconds", "percent", 
        "value", "slots", "max_dealers", "delay_seconds", "radius", 
        "slot", "stop_after_seconds", "speed", "min_distance", "max_distance", 
        "time_24h", "min", "max", "seconds", "interval_seconds",
        "scale", "volume", "offset_x", "offset_y", "offset_z",
        "fade_in_seconds", "stay_seconds", "fade_out_seconds", "timeout_seconds",
        "max_iterations", "count", "min_count", "arrival_radius"
    );

    private static Object coerceNumericParam(String key, Object value) {
        if (NUMERIC_KEYS.contains(key)) {
            if (value == null) return null;
            if (value instanceof String) {
                String str = ((String) value).trim();
                if (str.isEmpty()) {
                    return null;
                }
                if (str.matches("^(?=.*[0-9])[0-9.+\\-*/%()\\s]+$")) {
                    try {
                        return ScriptExpressionEvaluator.evaluateMathExpression(str);
                    } catch (Exception ignored) {}
                }
            }
        }
        return value;
    }

    private static double getAsDouble(Object val, double defaultValue) {
        if (val == null) return defaultValue;
        if (val instanceof Number) return ((Number) val).doubleValue();
        String raw = val.toString().trim();
        if (raw.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            getLogger().atWarning().log("[HyCitizens] Expected numeric script value but got '" + raw + "'. Using " + defaultValue + ".");
            return defaultValue;
        }
    }

    private static int getAsInt(Object val, int defaultValue) {
        return (int) Math.round(getAsDouble(val, defaultValue));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void putReflectiveComponent(Store<EntityStore> store, Ref<EntityStore> ref, ComponentType<EntityStore, ?> type, Object component) {
        store.putComponent(ref, (ComponentType) type, (Component) component);
    }

    private static Object normalizeValue(Object val) {
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return val;
    }

    private static void setScopedVariable(String scope, String name, Object value, ScriptContext context) {
        if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
            VariableManager.get().setPlayerVar(context.getPlayer().getUuid(), name, value);
        } else if ("CITIZEN".equalsIgnoreCase(scope)) {
            VariableManager.get().setCitizenVar(context.getCitizen(), name, value);
        } else if ("GLOBAL".equalsIgnoreCase(scope)) {
            VariableManager.get().setGlobalVar(name, value);
        } else if ("SESSION".equalsIgnoreCase(scope)) {
            context.setSessionVar(name, value);
        }
    }

    private static Object getScopedVariable(String scope, String name, ScriptContext context) {
        if ("PLAYER".equalsIgnoreCase(scope) && context.getPlayer() != null) {
            return VariableManager.get().getPlayerVar(context.getPlayer().getUuid(), name);
        } else if ("CITIZEN".equalsIgnoreCase(scope)) {
            return VariableManager.get().getCitizenVar(context.getCitizen(), name);
        } else if ("GLOBAL".equalsIgnoreCase(scope)) {
            return VariableManager.get().getGlobalVar(name);
        } else if ("SESSION".equalsIgnoreCase(scope)) {
            return context.getSessionVar(name);
        }
        return null;
    }

    private static boolean evaluateComparison(String left, String op, String right) {
        Double dLeft = null;
        Double dRight = null;
        try {
            dLeft = Double.parseDouble(left);
            dRight = Double.parseDouble(right);
        } catch (NumberFormatException ignored) {}

        if (dLeft != null && dRight != null) {
            return switch (op) {
                case "==", "EQUALS" -> dLeft.equals(dRight);
                case "!=", "NOT_EQUALS" -> !dLeft.equals(dRight);
                case ">", "GREATER_THAN" -> dLeft > dRight;
                case ">=", "GREATER_THAN_OR_EQUAL" -> dLeft >= dRight;
                case "<", "LESS_THAN" -> dLeft < dRight;
                case "<=", "LESS_THAN_OR_EQUAL" -> dLeft <= dRight;
                default -> false;
            };
        }

        return switch (op) {
            case "==", "EQUALS" -> left.equalsIgnoreCase(right);
            case "!=", "NOT_EQUALS" -> !left.equalsIgnoreCase(right);
            case "CONTAINS" -> left.toLowerCase().contains(right.toLowerCase());
            case "STARTS_WITH" -> left.toLowerCase().startsWith(right.toLowerCase());
            case "ENDS_WITH" -> left.toLowerCase().endsWith(right.toLowerCase());
            default -> false;
        };
    }

    private ScriptCondition parseCondition(Object obj) {
        if (obj == null) return null;
        if (obj instanceof ScriptCondition) {
            return (ScriptCondition) obj;
        }
        if (obj instanceof Map) {
            try {
                String json = gson.toJson(obj);
                return gson.fromJson(json, ScriptCondition.class);
            } catch (Exception e) {
                getLogger().atWarning().log("Failed to parse nested condition from Map: " + e.getMessage());
            }
        }
        return null;
    }

    private Object coerceValueType(Object val, String varType) {
        if (val == null) return null;
        if (varType == null) return val;
        
        switch (varType.toUpperCase()) {
            case "NUMBER":
                if (val instanceof Number) {
                    return ((Number) val).doubleValue();
                }
                try {
                    return Double.parseDouble(val.toString());
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            case "BOOLEAN":
                if (val instanceof Boolean) {
                    return val;
                }
                return Boolean.parseBoolean(val.toString());
            case "LIST":
                if (val instanceof List) {
                    return val;
                }
                if (val instanceof String) {
                    String str = ((String) val).trim();
                    if (str.startsWith("[") && str.endsWith("]")) {
                        try {
                            return gson.fromJson(str, List.class);
                        } catch (Exception ignored) {}
                    }
                    return new ArrayList<>(List.of(val));
                }
                return new ArrayList<>(List.of(val));
            case "STRING":
            default:
                if (val instanceof List || val instanceof Map) {
                    return gson.toJson(val);
                }
                return val.toString();
        }
    }

    private String getCitizenStateName(CitizenData citizen) {
        if (citizen == null) return "INACTIVE";
        return citizen.getCurrentScheduleRuntimeState() != null ? citizen.getCurrentScheduleRuntimeState().name() : "INACTIVE";
    }

    private World findWorld(String worldNameOrUuid) {
        if (worldNameOrUuid == null || worldNameOrUuid.isEmpty()) return null;
        try {
            UUID uuid = UUID.fromString(worldNameOrUuid);
            World world = Universe.get().getWorld(uuid);
            if (world != null) return world;
        } catch (IllegalArgumentException ignored) {}
        for (World world : Universe.get().getWorlds().values()) {
            if (world.getName() != null && world.getName().equalsIgnoreCase(worldNameOrUuid)) {
                return world;
            }
        }
        return null;
    }

    public boolean hasCondition(String type) {
        return conditions.containsKey(type.toUpperCase());
    }

    public boolean evaluateConditionDirect(String type, ScriptContext context, Map<String, Object> params) {
        ScriptConditionHandler handler = conditions.get(type.toUpperCase());
        if (handler == null) {
            return false;
        }
        return handler.evaluate(context, params);
    }

    public void setValidationMode(ScriptValidationMode validationMode) {
        this.validationMode = Objects.requireNonNull(validationMode);
    }

    public ScriptValidationMode getValidationMode() {
        return validationMode;
    }

    public List<ScriptExecutionTrace> getRecentExecutionTraces() {
        return List.copyOf(recentExecutionTraces);
    }

    public List<String> validateScript(ScriptBlock script) {
        List<String> diagnostics = new ArrayList<>();
        for (ScriptCondition condition : script.getConditions()) validateConditionTree(condition, diagnostics);
        for (ScriptAction action : script.getActions()) validateActionTree(action, diagnostics);
        return List.copyOf(diagnostics);
    }

    private void validateConditionTree(ScriptCondition condition, List<String> diagnostics) {
        String type = condition.getType() == null ? "" : condition.getType().toUpperCase(Locale.ROOT);
        if (!Set.of("AND", "OR", "NOT").contains(type) && !conditions.containsKey(type)) {
            diagnostics.add("Unknown condition type: " + type);
        }
        condition.getConditions().forEach(child -> validateConditionTree(child, diagnostics));
        if (condition.getCondition() != null) validateConditionTree(condition.getCondition(), diagnostics);
    }

    private void validateActionTree(ScriptAction action, List<String> diagnostics) {
        String type = action.getType() == null ? "" : action.getType().toUpperCase(Locale.ROOT);
        if (!Set.of("BREAK_LOOP", "CONTINUE_LOOP", "STOP_SCRIPT").contains(type) && !actions.containsKey(type)) {
            diagnostics.add("Unknown action type: " + type);
        }
        action.getActions().forEach(child -> validateActionTree(child, diagnostics));
        action.getTrueActions().forEach(child -> validateActionTree(child, diagnostics));
        action.getFalseActions().forEach(child -> validateActionTree(child, diagnostics));
        action.getBranches().forEach(branch ->
                branch.getActions().forEach(child -> validateActionTree(child, diagnostics)));
    }

    private static long getAsLong(Object value, long defaultValue) {
        if (value instanceof Number number) return number.longValue();
        if (value != null) {
            try { return Long.parseLong(value.toString()); }
            catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private URI validateHttpUri(String rawUrl) throws Exception {
        URI uri = URI.create(rawUrl);
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        String allowedProtocols = HyCitizensPlugin.get().getConfigManager()
                .getString("scripting.http.allowed_protocols", "https");
        Set<String> protocols = new HashSet<>();
        for (String value : allowedProtocols.split(",")) protocols.add(value.trim().toLowerCase(Locale.ROOT));
        if (!protocols.contains(scheme) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new SecurityException("HTTP URL protocol or authority is not allowed");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String allowedHosts = HyCitizensPlugin.get().getConfigManager().getString("scripting.http.allowed_hosts", "");
        boolean hostAllowed = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> host.equals(value) || (value.startsWith("*.") && host.endsWith(value.substring(1))));
        if (!hostAllowed) {
            throw new SecurityException("HTTP host is not in scripting.http.allowed_hosts");
        }

        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new SecurityException("HTTP host resolves to a private or local address");
            }
        }
        return uri;
    }

    private boolean allowHttpRequest() {
        long minute = System.currentTimeMillis() / 60_000L;
        long previous = httpRateWindow.get();
        if (previous != minute && httpRateWindow.compareAndSet(previous, minute)) {
            httpRateCount.set(0);
        }
        int limit = Math.max(1, HyCitizensPlugin.get().getConfigManager()
                .getInt("scripting.http.max_requests_per_minute", 30));
        return httpRateCount.incrementAndGet() <= limit;
    }
}
