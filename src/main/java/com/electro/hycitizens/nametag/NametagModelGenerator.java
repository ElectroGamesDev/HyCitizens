package com.electro.hycitizens.nametag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class NametagModelGenerator {
    private static final Gson GSON = new GsonBuilder().create();

    @Nonnull
    public static String generateBlockyModel(byte[] textureBytes) {
        int width = 25;
        int height = 25;

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(textureBytes));
            if (image != null) {
                width = image.getWidth();
                height = image.getHeight();
            }
        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] Failed to read texture dimensions: " + e.getMessage());
        }


        JsonObject root = new JsonObject();
        root.addProperty("format", "prop");
        root.addProperty("lod", "auto");

        JsonArray nodes = new JsonArray();
        JsonObject node = new JsonObject();
        node.addProperty("id", "1");
        node.addProperty("name", "quad");

        JsonObject position = new JsonObject();
        position.addProperty("x", 0);
        position.addProperty("y", 0);
        position.addProperty("z", 0);
        node.add("position", position);

        JsonObject orientation = new JsonObject();
        orientation.addProperty("x", 0);
        orientation.addProperty("y", 0);
        orientation.addProperty("z", 0);
        orientation.addProperty("w", 1);
        node.add("orientation", orientation);

        JsonObject shape = new JsonObject();
        shape.addProperty("type", "quad");

        JsonObject offset = new JsonObject();
        offset.addProperty("x", 0);
        offset.addProperty("y", height / 2.0);
        offset.addProperty("z", 0);
        shape.add("offset", offset);

        JsonObject stretch = new JsonObject();
        stretch.addProperty("x", 1);
        stretch.addProperty("y", 1);
        stretch.addProperty("z", 1);
        shape.add("stretch", stretch);

        JsonObject settings = new JsonObject();
        settings.addProperty("isPiece", false);

        JsonObject size = new JsonObject();
        size.addProperty("x", width);
        size.addProperty("y", height);
        settings.add("size", size);

        settings.addProperty("normal", "+Z");
        settings.addProperty("isStaticBox", true);
        shape.add("settings", settings);

        JsonObject textureLayout = new JsonObject();
        JsonObject front = new JsonObject();
        JsonObject texOffset = new JsonObject();
        texOffset.addProperty("x", 0);
        texOffset.addProperty("y", 0);
        front.add("offset", texOffset);
        JsonObject mirror = new JsonObject();
        mirror.addProperty("x", false);
        mirror.addProperty("y", false);
        front.add("mirror", mirror);
        front.addProperty("angle", 0);
        textureLayout.add("front", front);
        shape.add("textureLayout", textureLayout);

        shape.addProperty("unwrapMode", "custom");
        shape.addProperty("visible", true);
        shape.addProperty("doubleSided", true);
        shape.addProperty("shadingMode", "flat");

        node.add("shape", shape);
        nodes.add(node);
        root.add("nodes", nodes);

        return GSON.toJson(root);
    }
}
