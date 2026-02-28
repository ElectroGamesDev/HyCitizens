package com.electro.hycitizens.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.playerdata.PlayerStorage;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.common.util.RandomUtil;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class SkinUtilities {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Nonnull
    public static CompletableFuture<PlayerSkin> getSkin(@Nonnull String username) {
        return fetchSkinInternal(username, false);
    }

    @Nullable
    private static PlayerSkin getOnlinePlayerSkin(@Nonnull String username) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(username, NameMatching.EXACT_IGNORE_CASE);
            if (playerRef == null) {
                return null;
            }

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                PlayerSkinComponent skinComponent = ref.getStore().getComponent(ref, PlayerSkinComponent.getComponentType());
                if (skinComponent != null) {
                    return skinComponent.getPlayerSkin();
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    @Nonnull
    public static CompletableFuture<PlayerSkin> refreshSkin(@Nonnull String username) {
        return fetchSkinInternal(username, true);
    }

    @Nonnull
    private static CompletableFuture<PlayerSkin> fetchSkinInternal(@Nonnull String username, boolean forceApi) {
        if (username.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        getLogger().atInfo().log("[HyCitizens] " + (forceApi ? "Refreshing" : "Fetching") + " skin for '" + username + "'...");

        if (!forceApi) {
            PlayerSkin onlineSkin = getOnlinePlayerSkin(username);
            if (onlineSkin != null) {
                getLogger().atInfo().log("[HyCitizens] Found skin from online player.");
                return CompletableFuture.completedFuture(onlineSkin);
            }
        }

        return fetchFromHytlSkin(username, forceApi)
                .thenCompose(result -> {
                    if (result != null) {
                        return CompletableFuture.completedFuture(result);
                    }
                    getLogger().atInfo().log("[HyCitizens] Hytl.skin failed, falling back to PlayerDB...");
                    return fetchFromPlayerDB(username, forceApi);
                });
    }

    @Nonnull
    private static CompletableFuture<PlayerSkin> fetchFromHytlSkin(@Nonnull String username, boolean forceApi) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hytl.skin/character/" + username))
                .header("User-Agent", "Hytale-Plugin-HyCitizens/1.0")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        getLogger().atWarning().log("[HyCitizens] Hytl.skin API Error: " + response.statusCode());
                        return CompletableFuture.completedFuture(null);
                    }

                    try {
                        JsonObject skinData = JsonParser.parseString(response.body()).getAsJsonObject();

                        if (skinData.has("message") && skinData.get("message").getAsString().contains("profile not found")) {
                            getLogger().atWarning().log("[HyCitizens] User '" + username + "' not found in Hytl.skin.");
                            return CompletableFuture.completedFuture(null);
                        }

                        if (skinData.size() == 0) {
                            getLogger().atWarning().log("[HyCitizens] User '" + username + "' not found in Hytl.skin.");
                            return CompletableFuture.completedFuture(null);
                        }

                        PlayerSkin apiSkin = parseSkinFromHytlSkin(skinData);

                        if (forceApi) {
                            if (apiSkin != null) {
                                getLogger().atInfo().log("[HyCitizens] Refreshed skin from Hytl.skin API.");
                                return CompletableFuture.completedFuture(apiSkin);
                            }
                        } else {
                            return getUuidFromPlayerDB(username).thenCompose(uuid -> {
                                if (uuid != null) {
                                    return getSkinByUuid(uuid).thenApply(localSkin -> {
                                        if (localSkin != null) {
                                            getLogger().atInfo().log("[HyCitizens] Found local skin, ignoring API.");
                                            return localSkin;
                                        } else {
                                            return apiSkin;
                                        }
                                    });
                                } else {
                                    return CompletableFuture.completedFuture(apiSkin);
                                }
                            });
                        }

                        return CompletableFuture.completedFuture(apiSkin);

                    } catch (Exception e) {
                        getLogger().atWarning().log("[HyCitizens] Error parsing Hytl.skin response: " + e.getMessage());
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .exceptionally(e -> {
                    getLogger().atWarning().log("[HyCitizens] Hytl.skin network failed for '" + username + "': " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                    return null;
                });
    }

    @Nonnull
    private static CompletableFuture<PlayerSkin> fetchFromPlayerDB(@Nonnull String username, boolean forceApi) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://playerdb.co/api/player/hytale/" + username))
                .header("User-Agent", "Hytale-Plugin-HyCitizens/1.0")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        getLogger().atWarning().log("[HyCitizens] PlayerDB API Error: " + response.statusCode());
                        return CompletableFuture.completedFuture(null);
                    }

                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

                        if (!root.get("success").getAsBoolean()) {
                            getLogger().atWarning().log("[HyCitizens] User '" + username + "' not found in PlayerDB.");
                            return CompletableFuture.completedFuture(null);
                        }

                        JsonObject data = root.getAsJsonObject("data");
                        JsonObject player = data.getAsJsonObject("player");

                        UUID uuid = UUID.fromString(player.get("id").getAsString());
                        PlayerSkin apiSkin = parseSkinFromPlayerDB(player);

                        if (forceApi) {
                            if (apiSkin != null) {
                                getLogger().atInfo().log("[HyCitizens] Refreshed skin from PlayerDB API.");
                                return CompletableFuture.completedFuture(apiSkin);
                            }
                        } else {
                            return getSkinByUuid(uuid).thenApply(localSkin -> {
                                if (localSkin != null) {
                                    getLogger().atInfo().log("[HyCitizens] Found local skin, ignoring API.");
                                    return localSkin;
                                } else {
                                    return apiSkin;
                                }
                            });
                        }

                        return getSkinByUuid(uuid).thenApply(skin -> skin);

                    } catch (Exception e) {
                        getLogger().atWarning().log("[HyCitizens] Error parsing PlayerDB skin: " + e.getMessage());
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .exceptionally(e -> {
                    getLogger().atWarning().log("[HyCitizens] PlayerDB network failed for '" + username + "': " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                    return null;
                });
    }

    @Nonnull
    private static CompletableFuture<UUID> getUuidFromPlayerDB(@Nonnull String username) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://playerdb.co/api/player/hytale/" + username))
                .header("User-Agent", "Hytale-Plugin-HyCitizens/1.0")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        return null;
                    }

                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (!root.get("success").getAsBoolean()) {
                            return null;
                        }

                        JsonObject data = root.getAsJsonObject("data");
                        JsonObject player = data.getAsJsonObject("player");
                        return UUID.fromString(player.get("id").getAsString());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .exceptionally(e -> null);
    }

    @Nonnull
    public static CompletableFuture<PlayerSkin> getSkinByUuid(@Nonnull UUID uuid) {
        PlayerStorage playerStorage = Universe.get().getPlayerStorage();

        return playerStorage.load(uuid)
                .thenApply(entityStore -> {
                    if (entityStore == null) return null;

                    PlayerSkinComponent skinComponent = entityStore.getComponent(PlayerSkinComponent.getComponentType());
                    return skinComponent != null ? skinComponent.getPlayerSkin() : null;
                })
                .exceptionally(e -> {
                    getLogger().atWarning().log("[HyCitizens] Disk load failed: " + e.getMessage());
                    return null;
                });
    }

    @Nullable
    private static PlayerSkin parseSkinFromHytlSkin(@Nonnull JsonObject skinData) {
        return new PlayerSkin(
                getJsonString(skinData, "bodyCharacteristic"),
                getJsonString(skinData, "underwear"),
                getJsonString(skinData, "face"),
                getJsonString(skinData, "eyes"),
                getJsonString(skinData, "ears"),
                getJsonString(skinData, "mouth"),
                getJsonString(skinData, "facialHair"),
                getJsonString(skinData, "haircut"),
                getJsonString(skinData, "eyebrows"),
                getJsonString(skinData, "pants"),
                getJsonString(skinData, "overpants"),
                getJsonString(skinData, "undertop"),
                getJsonString(skinData, "overtop"),
                getJsonString(skinData, "shoes"),
                getJsonString(skinData, "headAccessory"),
                getJsonString(skinData, "faceAccessory"),
                getJsonString(skinData, "earAccessory"),
                getJsonString(skinData, "skinFeature"),
                getJsonString(skinData, "gloves"),
                getJsonString(skinData, "cape")
        );
    }

    @Nullable
    private static PlayerSkin parseSkinFromPlayerDB(@Nonnull JsonObject playerJson) {
        if (!playerJson.has("skin") || playerJson.get("skin").isJsonNull()) {
            return null;
        }

        JsonObject skin = playerJson.getAsJsonObject("skin");

        return new PlayerSkin(
                getJsonString(skin, "bodyCharacteristic"),
                getJsonString(skin, "underwear"),
                getJsonString(skin, "face"),
                getJsonString(skin, "eyes"),
                getJsonString(skin, "ears"),
                getJsonString(skin, "mouth"),
                getJsonString(skin, "facialHair"),
                getJsonString(skin, "haircut"),
                getJsonString(skin, "eyebrows"),
                getJsonString(skin, "pants"),
                getJsonString(skin, "overpants"),
                getJsonString(skin, "undertop"),
                getJsonString(skin, "overtop"),
                getJsonString(skin, "shoes"),
                getJsonString(skin, "headAccessory"),
                getJsonString(skin, "faceAccessory"),
                getJsonString(skin, "earAccessory"),
                getJsonString(skin, "skinFeature"),
                getJsonString(skin, "gloves"),
                getJsonString(skin, "cape")
        );
    }

    @Nullable
    private static String getJsonString(@Nonnull JsonObject object, @Nonnull String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    @Nonnull
    public static PlayerSkin createDefaultSkin() {
        PlayerSkin skin = new PlayerSkin();
        skin.bodyCharacteristic = "human_male";
        skin.underwear = "underwear_male";
        skin.face = "face_a";
        skin.eyes = "eyes_male";
        skin.ears = "ears_a";
        skin.mouth = "mouth_a";
        skin.haircut = "hair_short_messy";
        skin.eyebrows = "eyebrows_thick";
        skin.pants = "pants_shorts_denim";
        skin.undertop = "shirt_tshirt";
        skin.shoes = "shoes_sneakers";
        return skin;
    }

    public static final String[] SLOT_NAMES = {
            "bodyCharacteristic", "underwear", "skinFeature",
            "face", "eyes", "ears", "mouth", "eyebrows", "facialHair",
            "haircut",
            "pants", "overpants", "undertop", "overtop", "shoes", "gloves",
            "headAccessory", "faceAccessory", "earAccessory", "cape"
    };

    public static final String[][] SLOT_CATEGORIES = {
            {"Body", "bodyCharacteristic", "underwear", "skinFeature"},
            {"Face", "face", "eyes", "ears", "mouth", "eyebrows", "facialHair"},
            {"Hair", "haircut"},
            {"Clothing", "pants", "overpants", "undertop", "overtop", "shoes", "gloves"},
            {"Accessories", "headAccessory", "faceAccessory", "earAccessory", "cape"}
    };

    @Nonnull
    public static Map<String, List<String>> discoverCosmeticOptions(int sampleSize) {
        Map<String, Set<String>> optionSets = new LinkedHashMap<>();
        for (String slot : SLOT_NAMES) {
            optionSets.put(slot, new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
        }

        Random random = RandomUtil.getSecureRandom();
        for (int i = 0; i < sampleSize; i++) {
            try {
                PlayerSkin skin = CosmeticsModule.get().generateRandomSkin(random);
                for (String slot : SLOT_NAMES) {
                    String value = getSkinField(skin, slot);
                    if (value != null && !value.isEmpty()) {
                        optionSets.get(slot).add(value);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : optionSets.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    @Nonnull
    public static PlayerSkin copySkin(@Nonnull PlayerSkin source) {
        return new PlayerSkin(
                source.bodyCharacteristic,
                source.underwear,
                source.face,
                source.eyes,
                source.ears,
                source.mouth,
                source.facialHair,
                source.haircut,
                source.eyebrows,
                source.pants,
                source.overpants,
                source.undertop,
                source.overtop,
                source.shoes,
                source.headAccessory,
                source.faceAccessory,
                source.earAccessory,
                source.skinFeature,
                source.gloves,
                source.cape
        );
    }

    @Nullable
    public static String getSkinField(@Nonnull PlayerSkin skin, @Nonnull String slotName) {
        return switch (slotName) {
            case "bodyCharacteristic" -> skin.bodyCharacteristic;
            case "underwear" -> skin.underwear;
            case "skinFeature" -> skin.skinFeature;
            case "face" -> skin.face;
            case "eyes" -> skin.eyes;
            case "ears" -> skin.ears;
            case "mouth" -> skin.mouth;
            case "eyebrows" -> skin.eyebrows;
            case "facialHair" -> skin.facialHair;
            case "haircut" -> skin.haircut;
            case "pants" -> skin.pants;
            case "overpants" -> skin.overpants;
            case "undertop" -> skin.undertop;
            case "overtop" -> skin.overtop;
            case "shoes" -> skin.shoes;
            case "gloves" -> skin.gloves;
            case "headAccessory" -> skin.headAccessory;
            case "faceAccessory" -> skin.faceAccessory;
            case "earAccessory" -> skin.earAccessory;
            case "cape" -> skin.cape;
            default -> null;
        };
    }

    public static void setSkinField(@Nonnull PlayerSkin skin, @Nonnull String slotName, @Nullable String value) {
        switch (slotName) {
            case "bodyCharacteristic" -> skin.bodyCharacteristic = value;
            case "underwear" -> skin.underwear = value;
            case "skinFeature" -> skin.skinFeature = value;
            case "face" -> skin.face = value;
            case "eyes" -> skin.eyes = value;
            case "ears" -> skin.ears = value;
            case "mouth" -> skin.mouth = value;
            case "eyebrows" -> skin.eyebrows = value;
            case "facialHair" -> skin.facialHair = value;
            case "haircut" -> skin.haircut = value;
            case "pants" -> skin.pants = value;
            case "overpants" -> skin.overpants = value;
            case "undertop" -> skin.undertop = value;
            case "overtop" -> skin.overtop = value;
            case "shoes" -> skin.shoes = value;
            case "gloves" -> skin.gloves = value;
            case "headAccessory" -> skin.headAccessory = value;
            case "faceAccessory" -> skin.faceAccessory = value;
            case "earAccessory" -> skin.earAccessory = value;
            case "cape" -> skin.cape = value;
        }
    }

    @Nonnull
    public static String slotDisplayName(@Nonnull String slotName) {
        return switch (slotName) {
            case "bodyCharacteristic" -> "Body Type";
            case "underwear" -> "Underwear";
            case "skinFeature" -> "Skin Feature";
            case "face" -> "Face";
            case "eyes" -> "Eyes";
            case "ears" -> "Ears";
            case "mouth" -> "Mouth";
            case "eyebrows" -> "Eyebrows";
            case "facialHair" -> "Facial Hair";
            case "haircut" -> "Haircut";
            case "pants" -> "Pants";
            case "overpants" -> "Overpants";
            case "undertop" -> "Undertop";
            case "overtop" -> "Overtop";
            case "shoes" -> "Shoes";
            case "gloves" -> "Gloves";
            case "headAccessory" -> "Head Acc.";
            case "faceAccessory" -> "Face Acc.";
            case "earAccessory" -> "Ear Acc.";
            case "cape" -> "Cape";
            default -> slotName;
        };
    }
}