package com.electro.hycitizens.api.scripting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.server.core.HytaleServer;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.persistence.DataStore;
import com.electro.hycitizens.persistence.PersistenceService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class VariableManager {
    private static VariableManager instance;

    private final Path scriptsDir;
    private final Path playerVarsDir;
    private final Path globalFile;
    private final Gson gson;
    private final DataStore dataStore;
    private static final int SCHEMA_VERSION = 1;
    private static final TypeToken<Map<String, Object>> VARIABLE_TYPE = new TypeToken<>() {};

    private final Map<String, Object> globalVariables = new ConcurrentHashMap<>();
    private final Object globalLock = new Object();
    private final Map<UUID, Map<String, Object>> playerVariables = new ConcurrentHashMap<>();
    private static final int PLAYER_LOCK_STRIPES = 256;
    private final Object[] playerLocks = createPlayerLocks();
    private final Map<UUID, Long> playerLastAccess = new ConcurrentHashMap<>();

    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> playerWriteFailures = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerRetryAfter = new ConcurrentHashMap<>();
    private volatile boolean globalDirty = false;
    private volatile int globalWriteFailures = 0;
    private volatile long globalRetryAfter = 0;
    private static final int MAX_LOADED_PLAYERS = 1024;

    private ScheduledExecutorService flushExecutor;

    public static VariableManager get() {
        if (instance == null) {
            instance = new VariableManager();
        }
        return instance;
    }

    private VariableManager() {
        this.scriptsDir = Paths.get("mods", "HyCitizensData", "scripts");
        this.playerVarsDir = this.scriptsDir.resolve("player_variables");
        this.globalFile = this.scriptsDir.resolve("global_variables.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataStore = PersistenceService.store();
    }

    public void init() {
        try {
            Files.createDirectories(playerVarsDir);
        } catch (IOException e) {
            getLogger().atWarning().log("Failed to create scripts directories: " + e.getMessage());
        }

        loadGlobalVariables();

        // Start write-behind flush buffer (2-second interval)
        flushExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "hycitizens-variable-flusher");
            thread.setDaemon(true);
            return thread;
        });
        flushExecutor.scheduleAtFixedRate(this::flushDirtyVariables, 2, 2, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (flushExecutor != null) {
            flushExecutor.shutdown();
            try {
                flushExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }
        // Final synchronous flush
        flushDirtyVariables();
    }

    public Object getGlobalVar(String name) {
        synchronized (globalLock) {
            return globalVariables.get(name);
        }
    }

    public void setGlobalVar(String name, Object value) {
        synchronized (globalLock) {
            if (value == null) {
                if (globalVariables.remove(name) != null) globalDirty = true;
            } else {
                Object normalized = normalizeValue(value);
                Object old = globalVariables.put(name, normalized);
                if (old == null || !old.equals(normalized)) globalDirty = true;
            }
        }
    }

    public Map<String, Object> getGlobalVariables() {
        synchronized (globalLock) {
            return new LinkedHashMap<>(globalVariables);
        }
    }

    private void loadGlobalVariables() {
        globalVariables.clear();
        try {
            Optional<com.electro.hycitizens.persistence.DocumentEnvelope<Map<String, Object>>> stored =
                    dataStore.read("script_variables", "global", VARIABLE_TYPE, SCHEMA_VERSION);
            if (stored.isPresent()) {
                stored.get().data().forEach((key, value) -> globalVariables.put(key, normalizeValue(value)));
                return;
            }
        } catch (IOException e) {
            getLogger().atWarning().log("Failed to load versioned global variables: " + e.getMessage());
        }
        if (Files.exists(globalFile)) {
            try (Reader reader = Files.newBufferedReader(globalFile, StandardCharsets.UTF_8)) {
                Map<String, Object> loaded = gson.fromJson(reader, new TypeToken<LinkedHashMap<String, Object>>(){}.getType());
                if (loaded != null) {
                    for (Map.Entry<String, Object> entry : loaded.entrySet()) {
                        globalVariables.put(entry.getKey(), normalizeValue(entry.getValue()));
                    }
                }
            } catch (Exception e) {
                getLogger().atWarning().log("Failed to load global variables: " + e.getMessage());
            }
        }
    }

    public Object getPlayerVar(UUID playerUuid, String name) {
        synchronized (lockFor(playerUuid)) {
            return getOrLoadPlayerVariables(playerUuid).get(name);
        }
    }

    public void setPlayerVar(UUID playerUuid, String name, Object value) {
        synchronized (lockFor(playerUuid)) {
            Map<String, Object> vars = getOrLoadPlayerVariables(playerUuid);
            if (value == null) {
                if (vars.remove(name) != null) dirtyPlayers.add(playerUuid);
            } else {
                Object normalized = normalizeValue(value);
                Object old = vars.put(name, normalized);
                if (old == null || !old.equals(normalized)) dirtyPlayers.add(playerUuid);
            }
        }
    }

    public Map<String, Object> getPlayerVariables(UUID playerUuid) {
        synchronized (lockFor(playerUuid)) {
            return new LinkedHashMap<>(getOrLoadPlayerVariables(playerUuid));
        }
    }

    private Map<String, Object> getOrLoadPlayerVariables(UUID playerUuid) {
        playerLastAccess.put(playerUuid, System.currentTimeMillis());
        Map<String, Object> variables = playerVariables.computeIfAbsent(playerUuid, this::loadPlayerVariables);
        evictCleanOfflineEntries(playerUuid);
        return variables;
    }

    private Map<String, Object> loadPlayerVariables(UUID playerUuid) {
        Map<String, Object> vars = new ConcurrentHashMap<>();
        try {
            Optional<com.electro.hycitizens.persistence.DocumentEnvelope<Map<String, Object>>> stored =
                    dataStore.read("script_variables", playerUuid.toString(), VARIABLE_TYPE, SCHEMA_VERSION);
            if (stored.isPresent()) {
                stored.get().data().forEach((key, value) -> vars.put(key, normalizeValue(value)));
                return vars;
            }
        } catch (IOException e) {
            getLogger().atWarning().log("Failed to load versioned player variables for " + playerUuid + ": " + e.getMessage());
        }
        Path file = playerVarsDir.resolve(playerUuid.toString() + ".json");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, Object> loaded = gson.fromJson(reader, new TypeToken<LinkedHashMap<String, Object>>(){}.getType());
                if (loaded != null) {
                    for (Map.Entry<String, Object> entry : loaded.entrySet()) {
                        vars.put(entry.getKey(), normalizeValue(entry.getValue()));
                    }
                }
            } catch (Exception e) {
                getLogger().atWarning().log("Failed to load player variables for " + playerUuid + ": " + e.getMessage());
            }
        }
        return vars;
    }

    public Object getCitizenVar(CitizenData citizen, String name) {
        if (citizen == null) return null;
        // Citizen variables map gets populated during load
        Map<String, Object> variables = getCitizenVariablesMap(citizen);
        return variables.get(name);
    }

    public void setCitizenVar(CitizenData citizen, String name, Object value) {
        if (citizen == null) return;
        Map<String, Object> variables = getCitizenVariablesMap(citizen);
        if (value == null) {
            variables.remove(name);
        } else {
            variables.put(name, normalizeValue(value));
        }
        // Persist via deferred Citizen save path to prevent disk write bombs
        HyCitizensPlugin.get().getCitizensManager().saveCitizenDeferred(citizen);
    }

    public Map<String, Object> getCitizenVariables(CitizenData citizen) {
        return new LinkedHashMap<>(getCitizenVariablesMap(citizen));
    }

    private Map<String, Object> getCitizenVariablesMap(CitizenData citizen) {
        Map<String, Object> vars = citizen.getScriptVariables();
        return vars != null ? vars : new ConcurrentHashMap<>();
    }

    private Object normalizeValue(Object val) {
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            List<Object> normalized = new ArrayList<>();
            for (Object obj : list) {
                normalized.add(normalizeValue(obj));
            }
            return normalized;
        }
        return val;
    }

    private synchronized void flushDirtyVariables() {
        long now = System.currentTimeMillis();
        if (globalDirty && now >= globalRetryAfter) {
            synchronized (globalLock) {
                try {
                    dataStore.write(
                            "script_variables", "global", VARIABLE_TYPE, SCHEMA_VERSION,
                            new LinkedHashMap<>(globalVariables)
                    );
                    globalDirty = false;
                    globalWriteFailures = 0;
                    globalRetryAfter = 0;
                } catch (IOException e) {
                    globalWriteFailures++;
                    globalRetryAfter = now + retryDelayMillis(globalWriteFailures);
                    getLogger().atWarning().log("Failed to flush global variables: " + e.getMessage());
                }
            }
        }

        if (!dirtyPlayers.isEmpty()) {
            List<UUID> toFlush = new ArrayList<>(dirtyPlayers);
            for (UUID playerUuid : toFlush) {
                if (now < playerRetryAfter.getOrDefault(playerUuid, 0L)) continue;
                synchronized (lockFor(playerUuid)) {
                    Map<String, Object> vars = playerVariables.get(playerUuid);
                    if (vars != null) {
                        try {
                            dataStore.write(
                                    "script_variables", playerUuid.toString(), VARIABLE_TYPE, SCHEMA_VERSION,
                                    new LinkedHashMap<>(vars)
                            );
                            dirtyPlayers.remove(playerUuid);
                            playerWriteFailures.remove(playerUuid);
                            playerRetryAfter.remove(playerUuid);
                        } catch (IOException e) {
                            int failures = playerWriteFailures.merge(playerUuid, 1, Integer::sum);
                            playerRetryAfter.put(playerUuid, now + retryDelayMillis(failures));
                            getLogger().atWarning().log("Failed to flush player variables for " + playerUuid + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    public void unloadPlayer(UUID playerUuid) {
        synchronized (lockFor(playerUuid)) {
            if (dirtyPlayers.contains(playerUuid)) {
                Map<String, Object> vars = playerVariables.get(playerUuid);
                if (vars != null) {
                    try {
                        dataStore.write(
                                "script_variables", playerUuid.toString(), VARIABLE_TYPE, SCHEMA_VERSION,
                                new LinkedHashMap<>(vars)
                        );
                        dirtyPlayers.remove(playerUuid);
                    } catch (IOException e) {
                        getLogger().atWarning().log("Failed to flush player variables before unload for " + playerUuid + ": " + e.getMessage());
                        return;
                    }
                }
            }
            playerVariables.remove(playerUuid);
            playerLastAccess.remove(playerUuid);
            playerWriteFailures.remove(playerUuid);
            playerRetryAfter.remove(playerUuid);
            dataStore.unload("script_variables", playerUuid.toString());
        }
    }

    private Object lockFor(UUID playerUuid) {
        return playerLocks[(playerUuid.hashCode() & Integer.MAX_VALUE) % PLAYER_LOCK_STRIPES];
    }

    private static Object[] createPlayerLocks() {
        Object[] locks = new Object[PLAYER_LOCK_STRIPES];
        Arrays.setAll(locks, ignored -> new Object());
        return locks;
    }

    private void evictCleanOfflineEntries(UUID activePlayer) {
        if (playerVariables.size() <= MAX_LOADED_PLAYERS) return;
        playerLastAccess.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(activePlayer))
                .filter(entry -> !dirtyPlayers.contains(entry.getKey()))
                .min(Map.Entry.comparingByValue())
                .ifPresent(entry -> {
                    UUID playerUuid = entry.getKey();
                    synchronized (lockFor(playerUuid)) {
                        if (!dirtyPlayers.contains(playerUuid)) {
                            playerVariables.remove(playerUuid);
                            playerLastAccess.remove(playerUuid);
                            dataStore.unload("script_variables", playerUuid.toString());
                        }
                    }
                });
    }

    private long retryDelayMillis(int failures) {
        return Math.min(60_000L, 1_000L << Math.min(6, Math.max(0, failures - 1)));
    }
}
