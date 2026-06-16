package com.electro.hycitizens.roles;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.*;
import com.electro.hycitizens.api.scripting.ScriptManager;
import com.electro.hycitizens.util.GeneratedAssetReloader;
import com.google.gson.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class RoleGenerator {
    private static final float MARKER_ROLE_MAX_SPEED = 18.0f;
    private static final float COMBAT_MOVE_SPEED_RATIO = 0.67f;
    private static final float COMBAT_BACKWARDS_SPEED_RATIO = 0.33f;
    private final File generatedRolesDir;
    private final Gson gson;
    private final Map<String, String> lastGeneratedContent = new ConcurrentHashMap<>();
    private final Map<String, Integer> roleIndices = new ConcurrentHashMap<>();
    private final FactionAssetGenerator factionAssetGenerator = new FactionAssetGenerator();

    public static final String DEFAULT_ATTACK_INTERACTION = "Root_NPC_Attack_Melee";

    public static final String[] ATTACK_INTERACTIONS = {
            DEFAULT_ATTACK_INTERACTION,
            "Deer_Stag_Ram",
            "Larva_Silk_Bite",
            "Larva_Void_Bite",
            "NPC_Rubble_Throw",
            "Root_NPC_Scarak_Fighter_Attack",
            "Root_NPC_Bear_Grizzly_Attack",
            "Root_NPC_Bear_Polar_Attack",
            "Root_NPC_Crawler_Void_Attack",
            "Root_NPC_Fox_Attack",
            "Root_NPC_Goblin_Ogre_Attack",
            "Root_NPC_Hyena_Attack",
            "Root_NPC_Rat_Attack",
            "Root_NPC_Scorpion_Attack",
            "Root_NPC_Snake_Attack",
            "Root_NPC_Spider_Attack",
            "Root_NPC_Golem_Crystal_Attack",
            "Root_NPC_Golem_Crystal_Earth_Attack",
            "Root_NPC_Golem_Crystal_Flame_Attack",
            "Root_NPC_Golem_Crystal_Frost_Attack",
            "Root_NPC_Golem_Crystal_Sand_Attack",
            "Root_NPC_Golem_Crystal_Thunder_Attack",
            "Root_NPC_Golem_Firesteel_Attack",
            "Root_NPC_Hedera_BasicAttacks",
            "Root_NPC_Skeleton_Burnt_Lancer_Attack",
            "Root_NPC_Skeleton_Burnt_Soldier_Attack",
            "Root_NPC_Skeleton_Fighter_Attack",
            "Root_NPC_Skeleton_Frost_Fighter_Attack",
            "Root_NPC_Skeleton_Frost_Knight_Attack",
            "Root_NPC_Skeleton_Frost_Soldier_Attack",
            "Root_NPC_Skeleton_Incandescent_Fighter_Attack",
            "Root_NPC_Skeleton_Incandescent_Footman_Attack",
            "Root_NPC_Skeleton_Knight_Attack",
            "Root_NPC_Skeleton_Pirate_Captain_Attack",
            "Root_NPC_Skeleton_Pirate_Striker_Attack",
            "Root_NPC_Skeleton_Praetorian_Attack",
            "Root_NPC_Skeleton_Sand_Assassin_Attack",
            "Root_NPC_Skeleton_Sand_Guard_Attack",
            "Root_NPC_Skeleton_Sand_Soldier_Attack",
            "Root_NPC_Skeleton_Soldier_Attack",
            "Root_NPC_Skeleton_Burnt_Praetorian_Attack",
            "Root_NPC_Wraith_Attack",
            "Root_NPC_Spawn_Void_Attack",
            "Root_NPC_Toad_Rhino_Attack",
            "Root_NPC_Toad_Rhino_Magma_Attack",
            "Root_NPC_Wolf_Attack",
            "Root_NPC_Yeti_Attack",
            "Scarak_Defender_Bite",
            "Scarak_Louse_Bite",
            "Scarak_Seeker_Sting",
            "Shark_Hammerhead_Bite",
            "Skeleton_Archer_Bow_Shoot",
            "Skeleton_Archmage_Staff_Corruption_Orb",
            "Skeleton_Burnt_Alchemist_Bomb_Throw",
            "Skeleton_Burnt_Archer_Bow_Shoot",
            "Skeleton_Burnt_Gunner_Gun_Shoot",
            "Skeleton_Frost_Archer_Bow_Shoot",
            "Skeleton_Frost_Ranger_Bow_Shoot",
            "Skeleton_Frost_Scout_Bow_Shoot",
            "Skeleton_Mage_Wand_Corruption_Orb",
            "Skeleton_Pirate_Gunner_Gun_Shoot",
            "Skeleton_Ranger_Crossbow_Shoot",
            "Skeleton_Sand_Archer_Bow_Shoot",
            "Skeleton_Sand_Mage_Spellbook_Corruption_Orb",
            "Skeleton_Sand_Ranger_Crossbow_Shoot",
            "Skeleton_Sand_Scout_Bow_Shoot",
            "Skeleton_Scout_Bow_Shoot"
    };

    private static final Map<String, String> ATTACK_BY_MODEL = Map.ofEntries(
            Map.entry("Antelope", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Bear_Grizzly", "Root_NPC_Bear_Grizzly_Attack"),
            Map.entry("Bear_Polar", "Root_NPC_Bear_Polar_Attack"),
            Map.entry("Bison", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Boar", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Camel", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Camel_Calf", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Chicken_Undead", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Cow", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Cow_Undead", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Crawler_Void", "Root_NPC_Crawler_Void_Attack"),
            Map.entry("Crocodile", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Deer_Stag", "Deer_Stag_Ram"),
            Map.entry("Dungeon_Scarak_Defender", "Scarak_Defender_Bite"),
            Map.entry("Dungeon_Scarak_Defender_Patrol", "Scarak_Defender_Bite"),
            Map.entry("Dungeon_Scarak_Fighter", "Root_NPC_Scarak_Fighter_Attack"),
            Map.entry("Dungeon_Scarak_Louse", "Scarak_Louse_Bite"),
            Map.entry("Dungeon_Skeleton_Sand_Archer", "Skeleton_Sand_Archer_Bow_Shoot"),
            Map.entry("Dungeon_Skeleton_Sand_Mage", "Skeleton_Sand_Mage_Spellbook_Corruption_Orb"),
            Map.entry("Emberwulf", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Fen_Stalker", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Fox", "Root_NPC_Fox_Attack"),
            Map.entry("Ghoul", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Goat", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Golem_Crystal_Earth", "Root_NPC_Golem_Crystal_Attack"),
            Map.entry("Golem_Crystal_Flame", "Root_NPC_Golem_Crystal_Attack"),
            Map.entry("Golem_Crystal_Frost", "Root_NPC_Golem_Crystal_Attack"),
            Map.entry("Golem_Crystal_Sand", "Root_NPC_Golem_Crystal_Attack"),
            Map.entry("Golem_Crystal_Thunder", "Root_NPC_Golem_Crystal_Attack"),
            Map.entry("Golem_Firesteel", "Root_NPC_Golem_Firesteel_Attack"),
            Map.entry("Horse", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Horse_Skeleton", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Horse_Skeleton_Armored", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Hound_Bleached", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Hyena", "Root_NPC_Hyena_Attack"),
            Map.entry("Larva_Silk", "Larva_Silk_Bite"),
            Map.entry("Larva_Void", "Larva_Void_Bite"),
            Map.entry("Leopard_Snow", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Model_Deer_Stag", "Deer_Stag_Ram"),
            Map.entry("Molerat", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Moose_Bull", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Moose_Cow", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Mosshorn", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Mosshorn_Plain", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Pig_Undead", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Ram", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Raptor_Cave", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Rat", "Root_NPC_Rat_Attack"),
            Map.entry("Rex_Cave", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Risen_Gunner", "Skeleton_Burnt_Gunner_Gun_Shoot"),
            Map.entry("Risen_Knight", "Root_NPC_Skeleton_Knight_Attack"),
            Map.entry("Scarak_Broodmother", "NPC_Rubble_Throw"),
            Map.entry("Scarak_Defender", "Scarak_Defender_Bite"),
            Map.entry("Scarak_Defender_Patrol", "Scarak_Defender_Bite"),
            Map.entry("Scarak_Fighter", "Root_NPC_Scarak_Fighter_Attack"),
            Map.entry("Scarak_Fighter_Royal_Guard", "Root_NPC_Scarak_Fighter_Attack"),
            Map.entry("Scarak_Louse", "Scarak_Louse_Bite"),
            Map.entry("Scarak_Seeker", "Scarak_Seeker_Sting"),
            Map.entry("Scorpion", "Root_NPC_Scorpion_Attack"),
            Map.entry("Shadow_Knight", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Shark_Hammerhead", "Shark_Hammerhead_Bite"),
            Map.entry("Skeleton_Archer", "Skeleton_Archer_Bow_Shoot"),
            Map.entry("Skeleton_Archmage", "Skeleton_Archmage_Staff_Corruption_Orb"),
            Map.entry("Skeleton_Burnt_Alchemist", "Skeleton_Burnt_Alchemist_Bomb_Throw"),
            Map.entry("Skeleton_Burnt_Archer", "Skeleton_Burnt_Archer_Bow_Shoot"),
            Map.entry("Skeleton_Burnt_Gunner", "Skeleton_Burnt_Gunner_Gun_Shoot"),
            Map.entry("Skeleton_Burnt_Knight", "Root_NPC_Skeleton_Knight_Attack"),
            Map.entry("Skeleton_Burnt_Lancer", "Root_NPC_Skeleton_Burnt_Lancer_Attack"),
            Map.entry("Skeleton_Burnt_Soldier", "Root_NPC_Skeleton_Burnt_Soldier_Attack"),
            Map.entry("Skeleton_Burnt_Wizard", "Skeleton_Archmage_Staff_Corruption_Orb"),
            Map.entry("Skeleton_Fighter", "Root_NPC_Skeleton_Fighter_Attack"),
            Map.entry("Skeleton_Frost_Archer", "Skeleton_Frost_Archer_Bow_Shoot"),
            Map.entry("Skeleton_Frost_Archmage", "Skeleton_Archmage_Staff_Corruption_Orb"),
            Map.entry("Skeleton_Frost_Fighter", "Root_NPC_Skeleton_Frost_Fighter_Attack"),
            Map.entry("Skeleton_Frost_Knight", "Root_NPC_Skeleton_Frost_Knight_Attack"),
            Map.entry("Skeleton_Frost_Mage", "Skeleton_Sand_Mage_Spellbook_Corruption_Orb"),
            Map.entry("Skeleton_Frost_Ranger", "Skeleton_Frost_Ranger_Bow_Shoot"),
            Map.entry("Skeleton_Frost_Scout", "Skeleton_Frost_Scout_Bow_Shoot"),
            Map.entry("Skeleton_Frost_Soldier", "Root_NPC_Skeleton_Frost_Soldier_Attack"),
            Map.entry("Skeleton_Incandescent_Fighter", "Root_NPC_Skeleton_Incandescent_Fighter_Attack"),
            Map.entry("Skeleton_Incandescent_Footman", "Root_NPC_Skeleton_Incandescent_Footman_Attack"),
            Map.entry("Skeleton_Incandescent_Mage", "Skeleton_Sand_Mage_Spellbook_Corruption_Orb"),
            Map.entry("Skeleton_Knight", "Root_NPC_Skeleton_Knight_Attack"),
            Map.entry("Skeleton_Mage", "Skeleton_Mage_Wand_Corruption_Orb"),
            Map.entry("Skeleton_Pirate_Captain", "Root_NPC_Skeleton_Pirate_Captain_Attack"),
            Map.entry("Skeleton_Pirate_Gunner", "Skeleton_Pirate_Gunner_Gun_Shoot"),
            Map.entry("Skeleton_Pirate_Striker", "Root_NPC_Skeleton_Pirate_Captain_Attack"),
            Map.entry("Skeleton_Ranger", "Skeleton_Ranger_Crossbow_Shoot"),
            Map.entry("Skeleton_Sand_Archer", "Skeleton_Sand_Archer_Bow_Shoot"),
            Map.entry("Skeleton_Sand_Archmage", "Skeleton_Archmage_Staff_Corruption_Orb"),
            Map.entry("Skeleton_Sand_Assassin", "Root_NPC_Skeleton_Sand_Assassin_Attack"),
            Map.entry("Skeleton_Sand_Guard", "Root_NPC_Skeleton_Sand_Guard_Attack"),
            Map.entry("Skeleton_Sand_Mage", "Skeleton_Sand_Mage_Spellbook_Corruption_Orb"),
            Map.entry("Skeleton_Sand_Ranger", "Skeleton_Sand_Ranger_Crossbow_Shoot"),
            Map.entry("Skeleton_Sand_Scout", "Skeleton_Sand_Scout_Bow_Shoot"),
            Map.entry("Skeleton_Sand_Soldier", "Root_NPC_Skeleton_Sand_Soldier_Attack"),
            Map.entry("Skeleton_Scout", "Skeleton_Scout_Bow_Shoot"),
            Map.entry("Skeleton_Soldier", "Root_NPC_Skeleton_Soldier_Attack"),
            Map.entry("Slug_Magma", "Larva_Silk_Bite"),
            Map.entry("Snake_Marsh", "Root_NPC_Snake_Attack"),
            Map.entry("Snapdragon", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Spawn_Void", "Root_NPC_Spawn_Void_Attack"),
            Map.entry("Spider", "Root_NPC_Spider_Attack"),
            Map.entry("Tamed_Bison", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Tamed_Boar", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Tamed_Camel", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Tamed_Camel_Calf", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Tamed_Cow", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Tamed_Goat", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Tamed_Horse", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Tamed_Mosshorn", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Tamed_Mosshorn_Plain", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Tamed_Ram", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Tamed_Warthog", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Template_Goblin_Ogre_Tutorial", "Root_NPC_Goblin_Ogre_Attack"),
            Map.entry("Template_Intelligent", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Template_Predator", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Template_Scarak_Broodmother", "NPC_Rubble_Throw"),
            Map.entry("Template_Scarak_Defender", "Scarak_Defender_Bite"),
            Map.entry("Template_Scarak_Louse", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Template_Scarak_Seeker", "Scarak_Seeker_Sting"),
            Map.entry("Template_Summoned_Ally", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Template_Swimming_Aggressive", "Shark_Hammerhead_Bite"),
            Map.entry("Template_Trork_Companion", "Root_NPC_Wolf_Attack"),
            Map.entry("Tiger_Sabertooth", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Toad_Rhino", "Root_NPC_Toad_Rhino_Attack"),
            Map.entry("Toad_Rhino_Magma", "Root_NPC_Toad_Rhino_Magma_Attack"),
            Map.entry("Trork_Hunter", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Warthog", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Werewolf", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Wolf_Black", "Root_NPC_Wolf_Attack"),
            Map.entry("Wolf_Outlander_Priest", "Root_NPC_Wolf_Attack"),
            Map.entry("Wolf_Outlander_Sorcerer", "Root_NPC_Wolf_Attack"),
            Map.entry("Wolf_Trork_Hunter", "Root_NPC_Wolf_Attack"),
            Map.entry("Wolf_Trork_Shaman", "Root_NPC_Wolf_Attack"),
            Map.entry("Wraith", "Root_NPC_Wraith_Attack"),
            Map.entry("Wraith_Lantern", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Yeti", "Root_NPC_Yeti_Attack"),
            Map.entry("Zombie_Aberrant", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Zombie_Aberrant_Big", DEFAULT_ATTACK_INTERACTION),
            Map.entry("Zombie_Aberrant_Small", DEFAULT_ATTACK_INTERACTION)
    );

    private record CombatStylePreset(
            float attackDistance,
            float chaseSpeed,
            float combatBehaviorDistance,
            float desiredAttackDistanceMin,
            float desiredAttackDistanceMax,
            float attackPauseMin,
            float attackPauseMax,
            float combatRelativeTurnSpeed,
            int combatDirectWeight,
            int combatStrafeWeight,
            int combatAlwaysMovingWeight,
            boolean backOffAfterAttack,
            float backOffDistance,
            float backOffDurationMin,
            float backOffDurationMax,
            int blockProbability,
            float combatFleeIfTooCloseDistance,
            float targetRange,
            float combatMovingRelativeSpeed,
            float combatBackwardsRelativeSpeed,
            float combatAttackPreDelayMin,
            float combatAttackPreDelayMax,
            float combatAttackPostDelayMin,
            float combatAttackPostDelayMax
    ) {
        private void applyTo(@Nonnull CombatConfig combat) {
            combat.setAttackDistance(attackDistance);
            combat.setChaseSpeed(chaseSpeed);
            combat.setCombatBehaviorDistance(combatBehaviorDistance);
            combat.setDesiredAttackDistanceMin(desiredAttackDistanceMin);
            combat.setDesiredAttackDistanceMax(desiredAttackDistanceMax);
            combat.setAttackPauseMin(attackPauseMin);
            combat.setAttackPauseMax(attackPauseMax);
            combat.setCombatRelativeTurnSpeed(combatRelativeTurnSpeed);
            combat.setCombatDirectWeight(combatDirectWeight);
            combat.setCombatStrafeWeight(combatStrafeWeight);
            combat.setCombatAlwaysMovingWeight(combatAlwaysMovingWeight);
            combat.setBackOffAfterAttack(backOffAfterAttack);
            combat.setBackOffDistance(backOffDistance);
            combat.setBackOffDurationMin(backOffDurationMin);
            combat.setBackOffDurationMax(backOffDurationMax);
            combat.setBlockAbility("Shield_Block");
            combat.setBlockProbability(blockProbability);
            combat.setCombatFleeIfTooCloseDistance(combatFleeIfTooCloseDistance);
            combat.setTargetRange(targetRange);
            combat.setCombatMovingRelativeSpeed(combatMovingRelativeSpeed);
            combat.setCombatBackwardsRelativeSpeed(combatBackwardsRelativeSpeed);
            combat.setCombatAttackPreDelayMin(combatAttackPreDelayMin);
            combat.setCombatAttackPreDelayMax(combatAttackPreDelayMax);
            combat.setCombatAttackPostDelayMin(combatAttackPostDelayMin);
            combat.setCombatAttackPostDelayMax(combatAttackPostDelayMax);
            combat.setUseCombatActionEvaluator(false);
        }
    }

    private static final CombatStylePreset HUMAN_MELEE_STYLE = new CombatStylePreset(
            2.0f, 0.67f, 5.0f, 1.5f, 1.5f, 1.5f, 2.0f, 1.5f,
            10, 10, 0, true, 4.0f, 2.0f, 3.0f, 50, 0.0f, 4.0f,
            0.6f, 0.3f, 0.2f, 0.2f, 0.2f, 0.2f);
    private static final CombatStylePreset BEAR_STYLE = new CombatStylePreset(
            3.25f, 1.0f, 6.5f, 2.5f, 3.0f, 3.0f, 4.0f, 0.5f,
            10, 0, 0, true, 4.0f, 3.0f, 4.0f, 0, 2.0f, 3.25f,
            0.2f, 0.15f, 0.2f, 0.2f, 0.2f, 0.2f);
    private static final CombatStylePreset AGILE_PREDATOR_STYLE = new CombatStylePreset(
            2.5f, 1.0f, 6.0f, 1.8f, 2.4f, 1.5f, 2.5f, 1.0f,
            0, 10, 0, true, 5.0f, 2.0f, 3.0f, 0, 0.0f, 2.5f,
            0.6f, 0.3f, 0.2f, 0.2f, 0.2f, 0.2f);
    private static final CombatStylePreset DIRECT_BEAST_STYLE = new CombatStylePreset(
            3.0f, 0.9f, 6.0f, 2.0f, 2.8f, 1.6f, 2.4f, 1.0f,
            10, 0, 0, false, 4.0f, 2.0f, 3.0f, 0, 0.0f, 3.0f,
            0.5f, 0.25f, 0.2f, 0.2f, 0.2f, 0.2f);
    private static final CombatStylePreset SMALL_BEAST_STYLE = new CombatStylePreset(
            2.0f, 0.9f, 3.5f, 1.3f, 1.8f, 1.0f, 1.8f, 1.0f,
            0, 10, 0, false, 3.0f, 1.5f, 2.5f, 0, 0.0f, 2.0f,
            0.8f, 0.3f, 0.15f, 0.15f, 0.15f, 0.15f);
    private static final CombatStylePreset RANGED_STYLE = new CombatStylePreset(
            15.0f, 0.67f, 15.0f, 5.0f, 14.5f, 1.5f, 2.0f, 1.5f,
            0, 10, 0, false, 4.0f, 2.0f, 3.0f, 0, 4.0f, 15.0f,
            0.6f, 0.3f, 0.2f, 0.2f, 0.2f, 0.2f);
    private static final CombatStylePreset GOLEM_STYLE = new CombatStylePreset(
            2.75f, 0.8f, 5.0f, 2.0f, 2.5f, 1.8f, 2.4f, 0.25f,
            10, 0, 0, false, 4.0f, 2.0f, 3.0f, 0, 0.0f, 2.75f,
            0.4f, 0.2f, 0.2f, 0.2f, 1.0f, 1.2f);

    public RoleGenerator(@Nonnull Path generatedRolesPath) {
        this.generatedRolesDir = generatedRolesPath.toFile();
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        if (!generatedRolesDir.exists()) {
            generatedRolesDir.mkdirs();
        }
    }

    @Nonnull
    public static String resolveAttackInteraction(@Nonnull String modelId) {
        if ("Player".equalsIgnoreCase(modelId)) {
            return DEFAULT_ATTACK_INTERACTION;
        }

        String normalizedModel = normalizeAttackKey(modelId);
        String exactMatch = findMappedAttack(normalizedModel, true);
        if (exactMatch != null) {
            return exactMatch;
        }

        String fuzzyMatch = findMappedAttack(normalizedModel, false);
        if (fuzzyMatch != null) {
            return fuzzyMatch;
        }

        for (String attack : ATTACK_INTERACTIONS) {
            String key = attack.replace("Root_NPC_", "")
                    .replace("_Attack", "");
            String normalizedAttackKey = normalizeAttackKey(key);
            if (normalizedModel.equals(normalizedAttackKey)
                    || normalizedModel.startsWith(normalizedAttackKey)
                    || normalizedModel.endsWith(normalizedAttackKey)) {
                return attack;
            }
        }

        return DEFAULT_ATTACK_INTERACTION;
    }

    @Nonnull
    public static String[] getAttackInteractions() {
        return ATTACK_INTERACTIONS.clone();
    }

    public static void applyAutoCombatStyle(@Nonnull CombatConfig combat, @Nonnull String modelId) {
        resolveCombatStylePreset(modelId).applyTo(combat);
    }

    public static boolean isAutoResolvedAttackInteraction(@Nonnull String modelId, @Nonnull String attackInteractionId) {
        return resolveAttackInteraction(modelId).equals(attackInteractionId);
    }

    @Nullable
    private static String findMappedAttack(@Nonnull String normalizedModel, boolean exactOnly) {
        String bestAttack = null;
        int bestLength = -1;
        for (Map.Entry<String, String> entry : ATTACK_BY_MODEL.entrySet()) {
            String normalizedKey = normalizeAttackKey(entry.getKey());
            boolean match = exactOnly
                    ? normalizedModel.equals(normalizedKey)
                    : normalizedModel.endsWith(normalizedKey) || normalizedKey.endsWith(normalizedModel);
            if (match && normalizedKey.length() > bestLength) {
                bestAttack = entry.getValue();
                bestLength = normalizedKey.length();
            }
        }
        return bestAttack;
    }

    @Nonnull
    private static String normalizeAttackKey(@Nonnull String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    @Nonnull
    private static CombatStylePreset resolveCombatStylePreset(@Nonnull String modelId) {
        if ("Player".equalsIgnoreCase(modelId)) {
            return HUMAN_MELEE_STYLE;
        }

        String normalized = normalizeAttackKey(modelId);
        String attack = resolveAttackInteraction(modelId);
        String normalizedAttack = normalizeAttackKey(attack);

        if (normalized.contains("bear")) {
            return BEAR_STYLE;
        }
        if (isRangedCombatModel(normalized, normalizedAttack)) {
            return RANGED_STYLE;
        }
        if (normalized.contains("golem")) {
            return GOLEM_STYLE;
        }
        if (normalized.contains("fox")
                || normalized.contains("wolf")
                || normalized.contains("hyena")
                || normalized.contains("leopard")
                || normalized.contains("tiger")) {
            return AGILE_PREDATOR_STYLE;
        }
        if (normalized.contains("rat")
                || normalized.contains("larva")
                || normalized.contains("louse")
                || normalized.contains("scorpion")
                || normalized.contains("snake")
                || normalized.contains("spider")) {
            return SMALL_BEAST_STYLE;
        }
        if (normalized.contains("yeti")
                || normalized.contains("crawler")
                || normalized.contains("crocodile")
                || normalized.contains("rex")
                || normalized.contains("toadrhino")) {
            return DIRECT_BEAST_STYLE;
        }

        return DEFAULT_ATTACK_INTERACTION.equals(attack) ? HUMAN_MELEE_STYLE : DIRECT_BEAST_STYLE;
    }

    private static boolean isRangedCombatModel(@Nonnull String normalizedModel, @Nonnull String normalizedAttack) {
        return normalizedModel.contains("archer")
                || normalizedModel.contains("ranger")
                || normalizedModel.contains("scout")
                || normalizedModel.contains("gunner")
                || normalizedModel.contains("mage")
                || normalizedModel.contains("wizard")
                || normalizedModel.contains("alchemist")
                || normalizedAttack.contains("bow")
                || normalizedAttack.contains("crossbow")
                || normalizedAttack.contains("gun")
                || normalizedAttack.contains("staff")
                || normalizedAttack.contains("wand")
                || normalizedAttack.contains("spellbook")
                || normalizedAttack.contains("orb")
                || normalizedAttack.contains("bomb")
                || normalizedAttack.contains("throw");
    }

    @Nonnull
    public String getRoleName(@Nonnull CitizenData citizen) {
        return "HyCitizens_" + citizen.getId() + "_Role";
    }

    @Nonnull
    public String getScheduleTravelRoleName(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry) {
        return "HyCitizens_" + citizen.getId() + "_ScheduleTravel_" + sanitizeScheduleId(entry.getId()) + "_Role";
    }

    @Nonnull
    public String getScheduleFallbackTravelRoleName(@Nonnull CitizenData citizen) {
        return "HyCitizens_" + citizen.getId() + "_ScheduleFallbackTravel_Role";
    }

    @Nonnull
    public String getScheduleFallbackIdleRoleName(@Nonnull CitizenData citizen) {
        return "HyCitizens_" + citizen.getId() + "_ScheduleFallbackIdle_Role";
    }

    @Nonnull
    public String getScheduleEntryRoleName(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry) {
        return "HyCitizens_" + citizen.getId() + "_ScheduleEntry_" + sanitizeScheduleId(entry.getId()) + "_Role";
    }

    @Nonnull
    public Set<String> getGeneratedRoleNames(@Nonnull CitizenData citizen) {
        Set<String> roleNames = new LinkedHashSet<>();
        roleNames.add(getRoleName(citizen));
        roleNames.add(getScheduleFallbackTravelRoleName(citizen));
        roleNames.add(getScheduleFallbackIdleRoleName(citizen));
        for (ScheduleEntry entry : citizen.getScheduleConfig().getEntries()) {
            roleNames.add(getScheduleTravelRoleName(citizen, entry));
            roleNames.add(getScheduleEntryRoleName(citizen, entry));
        }
        return roleNames;
    }

    @Nonnull
    public String generateRole(@Nonnull CitizenData citizen) {
        generateRoleIfChanged(citizen);
        return getRoleName(citizen);
    }

    public boolean forceRoleGeneration(@Nonnull CitizenData citizen) {
        Set<String> activeRoleNames = getGeneratedRoleNames(citizen);
        boolean changed = forceSingleRoleGeneration(getRoleName(citizen), generateCurrentBaseRole(citizen));
        changed |= forceSingleRoleGeneration(getScheduleFallbackTravelRoleName(citizen),
                generateMoveTargetRole(citizen, citizen.getMovementBehavior().getWalkSpeed(), 0.05f, 1.0f));
        changed |= forceSingleRoleGeneration(getScheduleFallbackIdleRoleName(citizen), generateIdleRole(citizen));
        for (ScheduleEntry entry : citizen.getScheduleConfig().getEntries()) {
            changed |= forceSingleRoleGeneration(getScheduleTravelRoleName(citizen, entry), generateScheduleTravelRole(citizen, entry));
            changed |= forceSingleRoleGeneration(getScheduleEntryRoleName(citizen, entry), generateScheduleEntryRole(citizen, entry));
        }
        changed |= cleanupStaleGeneratedRoles(citizen.getId(), activeRoleNames);
        return changed;
    }

    // Returns true if the role file was actually written
    public boolean generateRoleIfChanged(@Nonnull CitizenData citizen) {
        Set<String> activeRoleNames = getGeneratedRoleNames(citizen);
        boolean changed = writeRoleIfChanged(getRoleName(citizen), generateCurrentBaseRole(citizen));
        changed |= writeRoleIfChanged(getScheduleFallbackTravelRoleName(citizen),
                generateMoveTargetRole(citizen, citizen.getMovementBehavior().getWalkSpeed(), 0.05f, 1.0f));
        changed |= writeRoleIfChanged(getScheduleFallbackIdleRoleName(citizen), generateIdleRole(citizen));
        for (ScheduleEntry entry : citizen.getScheduleConfig().getEntries()) {
            changed |= writeRoleIfChanged(getScheduleTravelRoleName(citizen, entry), generateScheduleTravelRole(citizen, entry));
            changed |= writeRoleIfChanged(getScheduleEntryRoleName(citizen, entry), generateScheduleEntryRole(citizen, entry));
        }
        changed |= cleanupStaleGeneratedRoles(citizen.getId(), activeRoleNames);
        return changed;
    }

    @Nonnull
    private JsonObject generateCurrentBaseRole(@Nonnull CitizenData citizen) {
        String moveType = citizen.getMovementBehavior().getType();
        boolean isIdle = "IDLE".equals(moveType);
        boolean isPatrol = "PATROL".equals(moveType);
        boolean isFollowCitizen = "FOLLOW_CITIZEN".equals(moveType);
        boolean isFollowPlayer = "FOLLOW_PLAYER".equals(moveType);
        if (isIdle) {
            return generateIdleRole(citizen);
        } else if (isPatrol) {
            return generatePatrolRole(citizen);
        } else if (isFollowCitizen || isFollowPlayer) {
            float followDistance = isFollowPlayer
                    ? ScriptManager.get().getFollowPlayerMinDistance(citizen.getId())
                    : citizen.getFollowDistance();
            float followStopDistance = getFollowStopDistance(followDistance);
            return generateMoveTargetRole(citizen, citizen.getMovementBehavior().getWalkSpeed(), followStopDistance,
                    Math.max(followStopDistance + 0.25f, Math.min(1.4f, followDistance + 0.35f)));
        } else {
            return generateVariantRole(citizen);
        }
    }

    @Nonnull
    public String getFallbackRoleName(@Nonnull CitizenData citizen) {
        String moveType = citizen.getMovementBehavior().getType();
        boolean interactable = HyCitizensPlugin.get().getCitizensManager().hasFKeyActions(citizen);
        String attitude = citizen.getAttitude();
        boolean isWander = "WANDER".equals(moveType) || "WANDER_CIRCLE".equals(moveType) || "WANDER_RECT".equals(moveType);

        if (isWander) {
            int radius = getEffectiveRadius(citizen);

            String base = switch (attitude) {
                case "NEUTRAL" -> "Citizen_Wander_Neutral_R" + radius;
                case "AGGRESSIVE" -> "Citizen_Wander_Aggressive_R" + radius;
                default -> "Citizen_Wander_Passive_R" + radius;
            };
            return interactable ? base + "_Interactable_Role" : base + "_Role";
        } else {
            return interactable ? "Citizen_Interactable_Role" : "Citizen_Role";
        }
    }

    private int getEffectiveRadius(@Nonnull CitizenData citizen) {
        float radius = citizen.getMovementBehavior().getWanderRadius();
        if (radius < 1) return 0;
        if (radius < 2) return 1;
        if (radius < 3) return 2;
        if (radius <= 7) return 5;
        if (radius <= 12) return 10;
        return 15;
    }

    @Nonnull
    private JsonObject generateIdleRole(@Nonnull CitizenData citizen) {
        JsonObject role = new JsonObject();
        role.addProperty("Type", "Generic");
        role.addProperty("Appearance", citizen.getModelId());

        // MotionControllerList
        JsonArray motionControllers = new JsonArray();
        JsonObject walkController = new JsonObject();
        walkController.addProperty("Type", "Walk");
        motionControllers.add(walkController);
        role.add("MotionControllerList", motionControllers);

        // MaxHealth via Compute
        JsonObject maxHealthCompute = new JsonObject();
        maxHealthCompute.addProperty("Compute", "MaxHealth");
        role.add("MaxHealth", maxHealthCompute);

        // Parameters
        JsonObject parameters = new JsonObject();
        JsonObject maxHealthParam = new JsonObject();
        maxHealthParam.addProperty("Value", 100);
        maxHealthParam.addProperty("Description", "Max health for the NPC");
        parameters.add("MaxHealth", maxHealthParam);
        role.add("Parameters", parameters);

        // KnockbackScale
        //role.addProperty("KnockbackScale", citizen.getKnockbackScale());
        role.addProperty("KnockbackScale", 0);

        role.addProperty("NameTranslationKey", citizen.getNameTranslationKey());

        return role;
    }

    @Nonnull
    private JsonObject generatePatrolRole(@Nonnull CitizenData citizen) {
        return generateMoveTargetRole(citizen, citizen.getPathConfig().getPluginPatrolSpeed(), 0.05f, 1.0f);
    }

    @Nonnull
    private JsonObject generateScheduleTravelRole(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry) {
        return generateMoveTargetRole(citizen, entry.getTravelSpeed(), 0.05f, Math.max(0.75f, entry.getArrivalRadius() + 0.5f));
    }

    @Nonnull
    private JsonObject generateScheduleEntryRole(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry) {
        return switch (entry.getActivityType()) {
            case IDLE -> generateIdleRole(citizen);
            case WANDER -> generateScheduleWanderRole(citizen, entry);
            case PATROL -> generateMoveTargetRole(citizen, entry.getTravelSpeed(), 0.05f,
                    Math.max(0.75f, entry.getArrivalRadius() + 0.5f));
            case FOLLOW_CITIZEN -> {
                float followStopDistance = getFollowStopDistance(entry.getFollowDistance());
                yield generateMoveTargetRole(citizen, entry.getTravelSpeed(), followStopDistance,
                        Math.max(followStopDistance + 0.25f, Math.min(1.4f, entry.getFollowDistance() + 0.35f)));
            }
        };
    }

    private float getFollowStopDistance(float followDistance) {
        return Math.max(0.35f, Math.min(0.9f, followDistance * 0.35f));
    }

    @Nonnull
    private JsonObject generateMoveTargetRole(@Nonnull CitizenData citizen, float walkSpeed, float stopDistance, float slowDownDistance) {
        JsonObject role = generateVariantRole(citizen);
        JsonObject modify = role.getAsJsonObject("Modify");
        modify.addProperty("MaxSpeed", MARKER_ROLE_MAX_SPEED);
        modify.addProperty("FollowPatrolPath", false);
        modify.addProperty("PatrolPathName", "");
        modify.addProperty("Patrol", false);
        modify.addProperty("PatrolWanderDistance", 0.0f);
        modify.addProperty("FollowMoveTarget", true);
        modify.addProperty("MoveTargetStopDistance", Math.max(0.05f, stopDistance));
        modify.addProperty("MoveTargetSlowDownDistance", Math.max(stopDistance + 0.25f, slowDownDistance));
        modify.addProperty("MoveTargetRelativeSpeed", Math.max(0.05f, Math.min(3.0f, walkSpeed / MARKER_ROLE_MAX_SPEED)));
        modify.addProperty("ApplySeparation", false); // Used to prevent collision issues
        float runSpeed = Math.max(0.1f, citizen.getMovementBehavior().getRunSpeed());
        modify.addProperty("ChaseRelativeSpeed", Math.min(3.0f, runSpeed / MARKER_ROLE_MAX_SPEED));
        modify.addProperty("CombatMovingRelativeSpeed", Math.min(3.0f, (runSpeed * COMBAT_MOVE_SPEED_RATIO) / MARKER_ROLE_MAX_SPEED));
        modify.addProperty("CombatBackwardsRelativeSpeed", Math.min(3.0f, (runSpeed * COMBAT_BACKWARDS_SPEED_RATIO) / MARKER_ROLE_MAX_SPEED));
        return role;
    }

    @Nonnull
    private JsonObject generateScheduleWanderRole(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry) {
        JsonObject role = generateVariantRole(citizen);
        JsonObject modify = role.getAsJsonObject("Modify");
        modify.addProperty("WanderRadius", entry.getWanderRadius());
        modify.addProperty("MaxSpeed", entry.getTravelSpeed());
        modify.addProperty("FollowPatrolPath", false);
        modify.addProperty("PatrolPathName", "");
        modify.addProperty("Patrol", false);
        modify.addProperty("PatrolWanderDistance", 0.0f);
        return role;
    }

    @Nonnull
    private JsonObject generateVariantRole(@Nonnull CitizenData citizen) {
        JsonObject role = new JsonObject();
        role.addProperty("Type", "Variant");
        role.addProperty("Reference", "Template_Citizen");

//        if (citizen.getFKeyInteractionEnabled()) {
//            role.add("InteractionInstruction", buildInteractionInstruction());
//        }

        JsonObject modify = new JsonObject();
        modify.addProperty("DefaultPlayerAttitude", mapPlayerAttitude(citizen.getAttitude()));
        modify.addProperty("WanderRadius", citizen.getMovementBehavior().getWanderRadius());

        DetectionConfig detection = citizen.getDetectionConfig();
        modify.addProperty("ViewRange", detection.getViewRange());
        modify.addProperty("ViewSector", detection.getViewSector());
        modify.addProperty("HearingRange", detection.getHearingRange());
        modify.addProperty("AbsoluteDetectionRange", detection.getAbsoluteDetectionRange());
        modify.addProperty("AlertedRange", detection.getAlertedRange());
        modify.addProperty("ChanceToBeAlertedWhenReceivingCallForHelp", detection.getChanceToBeAlertedWhenReceivingCallForHelp());
        modify.addProperty("InvestigateRange", detection.getInvestigateRange());
        modify.add("AlertedTime", rangeArray(detection.getAlertedTimeMin(), detection.getAlertedTimeMax()));
        modify.add("ConfusedTimeRange", rangeArray(detection.getConfusedTimeMin(), detection.getConfusedTimeMax()));
        modify.add("SearchTimeRange", rangeArray(detection.getSearchTimeMin(), detection.getSearchTimeMax()));

        modify.addProperty("KnockbackScale", citizen.getKnockbackScale());
        modify.addProperty("Appearance", citizen.getModelId());
        modify.addProperty("DefaultNPCAttitude", mapNpcAttitude(citizen.getDefaultNpcAttitude()));

        modify.addProperty("MaxHealth", 100);
        modify.addProperty("MaxSpeed", citizen.getMovementBehavior().getWalkSpeed());
        modify.addProperty("RunThreshold", citizen.getRunThreshold());

        modify.addProperty("LeashDistance", citizen.getLeashDistance());
        modify.addProperty("LeashMinPlayerDistance", citizen.getLeashMinPlayerDistance());
        modify.addProperty("HardLeashDistance", citizen.getHardLeashDistance());
        modify.add("LeashTimer", rangeArray(citizen.getLeashTimerMin(), citizen.getLeashTimerMax()));

        CombatConfig combat = citizen.getCombatConfig();
        modify.addProperty("Attack", combat.getAttackType());
        modify.addProperty("AttackDistance", combat.getAttackDistance());
        modify.addProperty("ChaseRelativeSpeed", combat.getChaseSpeed());
        modify.addProperty("CombatBehaviorDistance", combat.getCombatBehaviorDistance());
        modify.addProperty("CombatRelativeTurnSpeed", combat.getCombatRelativeTurnSpeed());
        modify.addProperty("CombatDirectWeight", combat.getCombatDirectWeight());
        modify.addProperty("CombatStrafeWeight", combat.getCombatStrafeWeight());
        modify.addProperty("CombatAlwaysMovingWeight", combat.getCombatAlwaysMovingWeight());
        modify.addProperty("CombatBackOffAfterAttack", combat.isBackOffAfterAttack());
        modify.addProperty("CombatMovingRelativeSpeed", combat.getCombatMovingRelativeSpeed());
        modify.addProperty("CombatBackwardsRelativeSpeed", combat.getCombatBackwardsRelativeSpeed());
        modify.addProperty("UseCombatActionEvaluator", combat.isUseCombatActionEvaluator());
        modify.addProperty("BlockAbility", combat.getBlockAbility());
        modify.addProperty("BlockProbability", combat.getBlockProbability());
        modify.addProperty("CombatFleeIfTooCloseDistance", combat.getCombatFleeIfTooCloseDistance());
        modify.addProperty("TargetRange", combat.getTargetRange());
        modify.add("DesiredAttackDistanceRange", rangeArray(combat.getDesiredAttackDistanceMin(), combat.getDesiredAttackDistanceMax()));
        modify.add("AttackPauseRange", rangeArray(combat.getAttackPauseMin(), combat.getAttackPauseMax()));
        modify.add("CombatStrafingDurationRange", rangeArray(combat.getCombatStrafingDurationMin(), combat.getCombatStrafingDurationMax()));
        modify.add("CombatStrafingFrequencyRange", rangeArray(combat.getCombatStrafingFrequencyMin(), combat.getCombatStrafingFrequencyMax()));
        modify.add("CombatAttackPreDelay", rangeArray(combat.getCombatAttackPreDelayMin(), combat.getCombatAttackPreDelayMax()));
        modify.add("CombatAttackPostDelay", rangeArray(combat.getCombatAttackPostDelayMin(), combat.getCombatAttackPostDelayMax()));
        modify.add("CombatBackOffDistanceRange", rangeArray(combat.getBackOffDistance(), combat.getBackOffDistance()));
        modify.add("CombatBackOffDurationRange", rangeArray(combat.getBackOffDurationMin(), combat.getBackOffDurationMax()));
        modify.add("TargetSwitchTimer", rangeArray(combat.getTargetSwitchTimerMin(), combat.getTargetSwitchTimerMax()));

        PathConfig pathConfig = citizen.getPathConfig();
        modify.addProperty("FollowPatrolPath", pathConfig.isFollowPath());
        modify.addProperty("PatrolPathName", pathConfig.getPathName());
        modify.addProperty("Patrol", pathConfig.isPatrol());
        modify.addProperty("PatrolWanderDistance", pathConfig.getPatrolWanderDistance());

        modify.addProperty("ApplySeparation", citizen.isApplySeparation());
        addStringArray(modify, "Weapons", citizen.getWeapons());
        addStringArray(modify, "OffHand", citizen.getOffHandItems());

        modify.addProperty("DropList", citizen.getDropList());
        modify.addProperty("WakingIdleBehaviorComponent", citizen.getWakingIdleBehaviorComponent());
        modify.addProperty("AttitudeGroup", factionAssetGenerator.ensureFactionAssets(citizen));
        modify.addProperty("BreathesInWater", citizen.isBreathesInWater());

        if (!citizen.getDayFlavorAnimation().isEmpty()) {
            modify.addProperty("DayFlavorAnimation", citizen.getDayFlavorAnimation());
            modify.add("DayFlavorAnimationLength", rangeArray(citizen.getDayFlavorAnimationLengthMin(), citizen.getDayFlavorAnimationLengthMax()));
        }

        modify.addProperty("DefaultHotbarSlot", citizen.getDefaultHotbarSlot());
        modify.addProperty("RandomIdleHotbarSlot", citizen.getRandomIdleHotbarSlot());
        modify.addProperty("ChanceToEquipFromIdleHotbarSlot", citizen.getChanceToEquipFromIdleHotbarSlot());
        modify.addProperty("DefaultOffHandSlot", citizen.getDefaultOffHandSlot());
        modify.addProperty("NighttimeOffhandSlot", getGeneratedNighttimeOffhandSlot(citizen));

        addStringArrayIfNotEmpty(modify, "CombatMessageTargetGroups", citizen.getCombatMessageTargetGroups());
        addStringArrayIfNotEmpty(modify, "FlockArray", citizen.getFlockArray());
        addStringArray(modify, "DisableDamageGroups", citizen.getDisableDamageGroups());

        JsonObject nameTranslationCompute = new JsonObject();
        nameTranslationCompute.addProperty("Compute", "NameTranslationKey");
        modify.add("NameTranslationKey", nameTranslationCompute);
        role.add("Modify", modify);

        // Keep translation as a parameter to drive the NameTranslationKey compute.
        JsonObject parameters = new JsonObject();
        JsonObject translationParam = new JsonObject();
        translationParam.addProperty("Value", citizen.getNameTranslationKey());
        translationParam.addProperty("Description", "Translation key for NPC name display");
        parameters.add("NameTranslationKey", translationParam);
        role.add("Parameters", parameters);

        return role;
    }

//    @Nonnull
//    private JsonObject buildInteractionInstruction() {
//        JsonObject interactionInstruction = new JsonObject();
//        JsonArray instructions = new JsonArray();
//
//        JsonObject setInteractable = new JsonObject();
//        setInteractable.addProperty("Continue", true);
//        JsonObject anySensor = new JsonObject();
//        anySensor.addProperty("Type", "Any");
//        setInteractable.add("Sensor", anySensor);
//        JsonArray setActions = new JsonArray();
//        JsonObject setAction = new JsonObject();
//        setAction.addProperty("Type", "SetInteractable");
//        setAction.addProperty("Interactable", true);
//        setActions.add(setAction);
//        setInteractable.add("Actions", setActions);
//        instructions.add(setInteractable);
//
//        JsonObject hasInteracted = new JsonObject();
//        JsonObject hasInteractedSensor = new JsonObject();
//        hasInteractedSensor.addProperty("Type", "HasInteracted");
//        hasInteracted.add("Sensor", hasInteractedSensor);
//        JsonArray interactActions = new JsonArray();
//        JsonObject interactAction = new JsonObject();
//        interactAction.addProperty("Type", "CitizenInteraction");
//        interactActions.add(interactAction);
//        hasInteracted.add("Actions", interactActions);
//        instructions.add(hasInteracted);
//
//        interactionInstruction.add("Instructions", instructions);
//        return interactionInstruction;
//    }

    @Nonnull
    private String mapPlayerAttitude(@Nonnull String citizenAttitude) {
        String normalized = citizenAttitude.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AGGRESSIVE", "HOSTILE" -> "Hostile";
            case "NEUTRAL" -> "Neutral";
            default -> "Ignore"; // PASSIVE
        };
    }

    @Nonnull
    private String mapNpcAttitude(@Nonnull String npcAttitude) {
        String normalized = npcAttitude.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AGGRESSIVE", "HOSTILE" -> "Hostile";
            case "NEUTRAL" -> "Neutral";
            default -> "Ignore"; // PASSIVE / Ignore / unknown
        };
    }

    private void addParam(@Nonnull JsonObject params, @Nonnull String key, float value) {
        JsonObject param = new JsonObject();
        param.addProperty("Value", value);
        params.add(key, param);
    }

    private void addParam(@Nonnull JsonObject params, @Nonnull String key, String value) {
        JsonObject param = new JsonObject();
        param.addProperty("Value", value);
        params.add(key, param);
    }

    private void addParam(@Nonnull JsonObject params, @Nonnull String key, int value) {
        JsonObject param = new JsonObject();
        param.addProperty("Value", value);
        params.add(key, param);
    }

    private void addParam(@Nonnull JsonObject params, @Nonnull String key, boolean value) {
        JsonObject param = new JsonObject();
        param.addProperty("Value", value);
        params.add(key, param);
    }

    private void addParamString(@Nonnull JsonObject params, @Nonnull String key, @Nonnull String value) {
        JsonObject param = new JsonObject();
        param.addProperty("Value", value);
        params.add(key, param);
    }

    private void addParamArray(@Nonnull JsonObject params, @Nonnull String key, float min, float max) {
        JsonObject param = new JsonObject();
        param.add("Value", rangeArray(min, max));
        params.add(key, param);
    }

    private void addParamStringArray(@Nonnull JsonObject params, @Nonnull String key, @Nonnull List<String> values) {
        JsonObject param = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(v);
        }
        param.add("Value", arr);
        params.add(key, param);
    }

    @Nonnull
    private JsonArray rangeArray(float min, float max) {
        JsonArray arr = new JsonArray();
        arr.add(min);
        arr.add(max);
        return arr;
    }

    // Helper: add a string array to a JsonObject if not empty
    private void addStringArrayIfNotEmpty(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull List<String> values) {
        if (!values.isEmpty()) {
            addStringArray(obj, key, values);
        }
    }

    private int getGeneratedNighttimeOffhandSlot(@Nonnull CitizenData citizen) {
        int slot = citizen.getNighttimeOffhandSlot();
        if (slot != 0 || citizen.getDefaultOffHandSlot() >= 0) {
            return slot;
        }

        List<String> offHandItems = citizen.getOffHandItems();
        if (offHandItems.size() == 1 && "Furniture_Crude_Torch".equals(offHandItems.get(0))) {
            return -1;
        }

        return slot;
    }

    // Helper: add a string array to a JsonObject
    private void addStringArray(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull List<String> values) {
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(v);
        }
        obj.add(key, arr);
    }

    public void writeRoleFile(@Nonnull String roleName, @Nonnull String content) {
        int oldIndex = roleIndices.getOrDefault(roleName, Integer.MIN_VALUE);
        boolean allowUpdate = oldIndex != Integer.MIN_VALUE;

        int newIndex = GeneratedAssetReloader.registerNpcBuilderFromJson(roleName, content, allowUpdate);

        if (newIndex != Integer.MIN_VALUE) {
            roleIndices.put(roleName, newIndex);
            return;
        }

        getLogger().atWarning().log("[HyCitizens] Programmatic registration failed for " + roleName + ", falling back to file-based");

        File roleFile = new File(generatedRolesDir, roleName + ".json");
        try (FileWriter writer = new FileWriter(roleFile)) {
            writer.write(content);
        } catch (IOException e) {
            getLogger().atSevere().log("[HyCitizens] Failed to write role file for " + roleName + ": " + e.getMessage());
            return;
        }

        GeneratedAssetReloader.reloadNpcBuilderFile(roleFile.toPath());
    }

    public void deleteRoleFile(@Nonnull String citizenId) {
        String prefix = "HyCitizens_" + citizenId + "_";

        roleIndices.keySet().stream()
                .filter(name -> name.startsWith(prefix))
                .forEach(name -> {
                    GeneratedAssetReloader.removeNpcBuilder(name);
                    roleIndices.remove(name);
                    lastGeneratedContent.remove(name);
                });

        File[] files = generatedRolesDir.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".json"));
        if (files == null) {
            return;
        }

        for (File roleFile : files) {
            String fileName = roleFile.getName();
            String roleName = fileName.substring(0, fileName.length() - ".json".length());
            lastGeneratedContent.remove(roleName);
            if (roleFile.exists()) {
                if (roleFile.delete()) {
                    GeneratedAssetReloader.removeNpcBuilder(roleName);
                }
            }
        }
    }

    private boolean forceSingleRoleGeneration(@Nonnull String roleName, @Nonnull JsonObject roleJson) {
        String content = gson.toJson(roleJson);
        writeRoleFile(roleName, content);
        lastGeneratedContent.put(roleName, content);
        return true;
    }

    private boolean writeRoleIfChanged(@Nonnull String roleName, @Nonnull JsonObject roleJson) {
        String newContent = gson.toJson(roleJson);
        String previousContent = lastGeneratedContent.get(roleName);

        if (newContent.equals(previousContent)) {
            return false;
        }

        writeRoleFile(roleName, newContent);
        lastGeneratedContent.put(roleName, newContent);
        return true;
    }

    @Nonnull
    private String sanitizeScheduleId(@Nonnull String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private boolean cleanupStaleGeneratedRoles(@Nonnull String citizenId, @Nonnull Set<String> activeRoleNames) {
        boolean changed = false;
        String prefix = "HyCitizens_" + citizenId + "_";
        File[] files = generatedRolesDir.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".json"));
        if (files == null) {
            return false;
        }

        for (File roleFile : files) {
            String fileName = roleFile.getName();
            String roleName = fileName.substring(0, fileName.length() - ".json".length());
            if (activeRoleNames.contains(roleName)) {
                continue;
            }
            lastGeneratedContent.remove(roleName);
            if (roleFile.delete()) {
                GeneratedAssetReloader.removeNpcBuilder(roleName);
                changed = true;
            }
        }

        lastGeneratedContent.keySet().removeIf(roleName ->
                roleName.startsWith(prefix) && !activeRoleNames.contains(roleName));
        return changed;
    }

    public void regenerateAllRoles(@Nonnull Collection<CitizenData> citizens) {
        for (CitizenData citizen : citizens) {
            forceRoleGeneration(citizen);
        }
    }

    public void cleanup() {
        roleIndices.clear();
        lastGeneratedContent.clear();
    }
}
