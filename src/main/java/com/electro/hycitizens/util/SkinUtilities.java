package com.electro.hycitizens.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.playerdata.PlayerStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class SkinUtilities {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Standard Fetch from PlayerDB.
     */
    @Nonnull
    public static CompletableFuture<PlayerSkin> getSkin(@Nonnull String username) {
        return fetchSkinInternal(username, false);
    }

    /**
     * Force Refresh from PlayerDB.
     */
    @Nonnull
    public static CompletableFuture<PlayerSkin> refreshSkin(@Nonnull String username) {
        return fetchSkinInternal(username, true);
    }

    /**
     * Internal logic to handle the API call and decision matrix.
     */
    @Nonnull
    private static CompletableFuture<PlayerSkin> fetchSkinInternal(@Nonnull String username, boolean forceApi) {
        if (username.isBlank()) {
            return CompletableFuture.completedFuture(createDefaultSkin());
        }

        getLogger().atInfo().log("[HyCitizens] " + (forceApi ? "Refreshing" : "Fetching") + " skin for '" + username + "'...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://playerdb.co/api/player/hytale/" + username))
                .header("User-Agent", "Hytale-Plugin-HyCitizens/1.0")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        getLogger().atWarning().log("[HyCitizens] API Error: " + response.statusCode());
                        return CompletableFuture.completedFuture(createDefaultSkin());
                    }

                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

                        if (!root.get("success").getAsBoolean()) {
                            getLogger().atWarning().log("[HyCitizens] User '" + username + "' not found in PlayerDB.");
                            return CompletableFuture.completedFuture(createDefaultSkin());
                        }

                        JsonObject data = root.getAsJsonObject("data");
                        JsonObject player = data.getAsJsonObject("player");

                        UUID uuid = UUID.fromString(player.get("id").getAsString());
                        PlayerSkin apiSkin = parseSkinFromPlayerDB(player);

                        if (forceApi) {
                            if (apiSkin != null) {
                                getLogger().atInfo().log("[HyCitizens] Refreshed skin from API.");
                                return CompletableFuture.completedFuture(apiSkin);
                            }
                        } else {
                            return getSkinByUuid(uuid).thenApply(localSkin -> {
                                if (localSkin != null) {
                                    getLogger().atInfo().log("[HyCitizens] Found local skin, ignoring API.");
                                    return localSkin;
                                } else {
                                    return apiSkin != null ? apiSkin : createDefaultSkin();
                                }
                            });
                        }

                        return getSkinByUuid(uuid).thenApply(skin -> skin != null ? skin : createDefaultSkin());

                    } catch (Exception e) {
                        getLogger().atWarning().log("[HyCitizens] Error parsing skin: " + e.getMessage());
                        return CompletableFuture.completedFuture(createDefaultSkin());
                    }
                })
                .exceptionally(e -> {
                    getLogger().atWarning().log("[HyCitizens] Network failed for '" + username + "': " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                    return createDefaultSkin();
                });
    }

    /**
     * Attempts to load the skin from Hytale's internal disk storage.
     */
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

    /**
     * JSON Parser for PlayerDB API response.
     */
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

    /**
     * Creates a default skin (Steve/Alex style).
     */
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
}
