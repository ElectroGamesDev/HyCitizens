package com.electro.hycitizens.api.scripting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.server.core.HytaleServer;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.HyCitizensPlugin;

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

    private final Map<String, Object> globalVariables = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> playerVariables = new ConcurrentHashMap<>();

    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private boolean globalDirty = false;

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

    // --- GLOBAL ---
    public Object getGlobalVar(String name) {
        return globalVariables.get(name);
    }

    public void setGlobalVar(String name, Object value) {
        if (value == null) {
            if (globalVariables.remove(name) != null) {
                globalDirty = true;
            }
        } else {
            Object normalized = normalizeValue(value);
            Object old = globalVariables.put(name, normalized);
            if (old == null || !old.equals(normalized)) {
                globalDirty = true;
            }
        }
    }

    public Map<String, Object> getGlobalVariables() {
        return new LinkedHashMap<>(globalVariables);
    }

    private void loadGlobalVariables() {
        globalVariables.clear();
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

    // --- PLAYER ---
    public Object getPlayerVar(UUID playerUuid, String name) {
        Map<String, Object> vars = getOrLoadPlayerVariables(playerUuid);
        return vars.get(name);
    }

    public void setPlayerVar(UUID playerUuid, String name, Object value) {
        Map<String, Object> vars = getOrLoadPlayerVariables(playerUuid);
        if (value == null) {
            if (vars.remove(name) != null) {
                dirtyPlayers.add(playerUuid);
            }
        } else {
            Object normalized = normalizeValue(value);
            Object old = vars.put(name, normalized);
            if (old == null || !old.equals(normalized)) {
                dirtyPlayers.add(playerUuid);
            }
        }
    }

    public Map<String, Object> getPlayerVariables(UUID playerUuid) {
        return new LinkedHashMap<>(getOrLoadPlayerVariables(playerUuid));
    }

    private Map<String, Object> getOrLoadPlayerVariables(UUID playerUuid) {
        return playerVariables.computeIfAbsent(playerUuid, this::loadPlayerVariables);
    }

    private Map<String, Object> loadPlayerVariables(UUID playerUuid) {
        Map<String, Object> vars = new ConcurrentHashMap<>();
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

    // --- CITIZEN ---
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

    // --- UTILS ---
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
        if (globalDirty) {
            try {
                Path temp = globalFile.getParent().resolve("global_variables.json.tmp");
                try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                    gson.toJson(globalVariables, writer);
                }
                Files.move(temp, globalFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                globalDirty = false;
            } catch (IOException e) {
                getLogger().atWarning().log("Failed to flush global variables: " + e.getMessage());
            }
        }

        if (!dirtyPlayers.isEmpty()) {
            List<UUID> toFlush = new ArrayList<>(dirtyPlayers);
            dirtyPlayers.removeAll(toFlush);

            for (UUID playerUuid : toFlush) {
                Map<String, Object> vars = playerVariables.get(playerUuid);
                if (vars != null) {
                    Path file = playerVarsDir.resolve(playerUuid.toString() + ".json");
                    try {
                        Path temp = file.getParent().resolve(playerUuid.toString() + ".json.tmp");
                        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                            gson.toJson(vars, writer);
                        }
                        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        getLogger().atWarning().log("Failed to flush player variables for " + playerUuid + ": " + e.getMessage());
                    }
                }
            }
        }
    }
}
