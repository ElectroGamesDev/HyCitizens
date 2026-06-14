package com.electro.hycitizens.nametag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;

public class NametagItemGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final float DEFAULT_SCALE = 0.5f;

    @Nonnull
    public static String generateItemJson(@Nonnull String modelPath, @Nonnull String texturePath) {
        return generateItemJson(modelPath, texturePath, DEFAULT_SCALE);
    }

    @Nonnull
    public static String generateItemJson(@Nonnull String modelPath, @Nonnull String texturePath, float scale) {
        JsonObject root = new JsonObject();

        JsonObject translationProps = new JsonObject();
        translationProps.addProperty("Name", "Custom Nametag");
        translationProps.addProperty("Description", "Custom formatted nametag");
        root.add("TranslationProperties", translationProps);

        root.addProperty("ItemLevel", 1);

        JsonObject playerAnimations = new JsonObject();
        playerAnimations.addProperty("Parent", "Item");
        playerAnimations.add("Animations", new JsonObject());
        root.add("PlayerAnimationsId", playerAnimations);

        JsonArray categories = new JsonArray();
        categories.add("Items.Utilities");
        root.add("Categories", categories);

        root.add("InteractionVars", new JsonObject());

        JsonObject utility = new JsonObject();
        utility.addProperty("Compatible", false);
        root.add("Utility", utility);

        JsonObject blockType = new JsonObject();
        blockType.addProperty("BlockParticleSetId", "Wood");
        blockType.addProperty("BlockSoundSetId", "Wood");
        blockType.addProperty("CustomModel", modelPath);

        JsonArray customModelTexture = new JsonArray();
        JsonObject textureEntry = new JsonObject();
        textureEntry.addProperty("Texture", texturePath);
        textureEntry.addProperty("Weight", 1);
        customModelTexture.add(textureEntry);
        blockType.add("CustomModelTexture", customModelTexture);

        blockType.addProperty("CustomModelScale", scale);
        blockType.addProperty("DrawType", "Model");

        JsonObject flags = new JsonObject();
        flags.addProperty("IsUsable", true);
        blockType.add("Flags", flags);

        JsonObject gathering = new JsonObject();
        gathering.add("Harvest", new JsonObject());
        gathering.add("Soft", new JsonObject());
        blockType.add("Gathering", gathering);

        blockType.addProperty("HitboxType", "Potion");
        blockType.addProperty("Material", "Empty");
        blockType.addProperty("RandomRotation", "YawStep1");
        blockType.addProperty("Opacity", "Transparent");

        JsonObject support = new JsonObject();
        JsonArray down = new JsonArray();
        JsonObject downEntry = new JsonObject();
        downEntry.addProperty("FaceType", "Full");
        down.add(downEntry);
        support.add("Down", down);
        blockType.add("Support", support);

        blockType.addProperty("ParticleColor", "#ffffff");

        JsonObject light = new JsonObject();
        light.addProperty("Color", "#000");
        blockType.add("Light", light);

        root.add("BlockType", blockType);

        root.addProperty("Consumable", false);

        JsonObject tags = new JsonObject();
        JsonArray typeArray = new JsonArray();
        typeArray.add("Utility");
        tags.add("Type", typeArray);
        root.add("Tags", tags);

        root.addProperty("Scale", 0.6);
        root.addProperty("MaxStack", 1);
        root.addProperty("DropOnDeath", false);
        root.addProperty("Quality", "Common");

        return GSON.toJson(root);
    }

    @Nonnull
    public static String generateModelAssetJson(@Nonnull String blockymodelPath, @Nonnull String texturePath) {
        JsonObject root = new JsonObject();
        root.addProperty("Model", blockymodelPath);
        root.addProperty("Texture", texturePath);

        JsonObject hitBox = new JsonObject();
        JsonObject min = new JsonObject();
        min.addProperty("X", -0.1);
        min.addProperty("Y", 0);
        min.addProperty("Z", -0.1);
        hitBox.add("Min", min);

        JsonObject max = new JsonObject();
        max.addProperty("X", 0.1);
        max.addProperty("Y", 0.1);
        max.addProperty("Z", 0.1);
        hitBox.add("Max", max);

        root.add("HitBox", hitBox);
        root.addProperty("EyeHeight", 0.05);

        return GSON.toJson(root);
    }

}
