package com.electro.hycitizens.util;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UpdateChecker {
    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/ElectroGamesDev/HyCitizens/main/src/main/resources/manifest.json";

    private static volatile boolean checked = false;
    private static volatile boolean updateAvailable = false;

    private static volatile String latestVersion = null;
    private static volatile String changelog = null;

    public static void checkAsync() {
        if (checked) return;

        Thread t = new Thread(() -> {
            try {
                JsonObject json = fetchManifest();

                latestVersion = json.get("Version").getAsString();
                updateAvailable = isNewerVersion(latestVersion, getCurrentVersion());
            } catch (Exception ignored) {

            } finally {
                checked = true;
            }
        });

        t.setName("GithubUpdateChecker");
        t.setDaemon(true);
        t.start();
    }

    public static boolean hasUpdate() {
        return checked && updateAvailable;
    }

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static String getChangelog() {
        return changelog;
    }

    private static JsonObject fetchManifest() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(MANIFEST_URL))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Manifest request failed: " + response.statusCode());
        }

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static boolean isNewerVersion(String latest, String current) {
        int[] a = parseSemver(latest);
        int[] b = parseSemver(current);

        for (int i = 0; i < 3; i++) {
            if (a[i] > b[i]) return true;
            if (a[i] < b[i]) return false;
        }
        return false;
    }

    private static int[] parseSemver(String v) {
        String[] parts = v.split("\\.");
        int[] out = new int[]{0, 0, 0};

        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {}
        }

        return out;
    }

    private static String getCurrentVersion() {
        try (var in = UpdateChecker.class.getClassLoader().getResourceAsStream("manifest.json")) {
            if (in == null) return "0.0.0";

            String jsonText = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(jsonText).getAsJsonObject();

            return json.get("Version").getAsString();
        } catch (Exception e) {
            return "0.0.0";
        }
    }
}
