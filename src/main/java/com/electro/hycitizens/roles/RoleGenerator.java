package com.electro.hycitizens.roles;

import com.electro.hycitizens.models.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class RoleGenerator {
    private final File generatedRolesDir;
    private final Gson gson;

    public static final String[] ATTACK_INTERACTIONS = {
            "Root_NPC_Attack_Melee",
            "Root_NPC_Scarak_Fighter_Attack",
            "Root_NPC_Bear_Grizzly_Attack",
            "Root_NPC_Bear_Polar_Attack",
            "Root_NPC_Fox_Attack",
            "Root_NPC_Hyena_Attack",
            "Root_NPC_Wolf_Attack",
            "Root_NPC_Yeti_Attack",
            "Root_NPC_Rat_Attack",
            "Root_NPC_Scorpion_Attack",
            "Root_NPC_Snake_Attack",
            "Root_NPC_Spider_Attack",
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
            "Root_NPC_Wraith_Attack",
            "Root_NPC_Skeleton_Burnt_Praetorian_Attack",
            "Root_NPC_Crawler_Void_Attack",
            "Root_NPC_Spawn_Void_Attack"
    };

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
            return "Root_NPC_Attack_Melee";
        }

        for (String attack : ATTACK_INTERACTIONS) {
            // Strip "Root_NPC_" prefix and "_Attack"/"_BasicAttacks" suffix to get the entity key
            String key = attack.replace("Root_NPC_", "")
                    .replace("_BasicAttacks", "")
                    .replace("_Attack", "");
            if (modelId.equalsIgnoreCase(key) || modelId.replace("_", "").equalsIgnoreCase(key.replace("_", ""))) {
                return attack;
            }
        }

        // Fallback
        return "Root_NPC_Attack_Melee";
    }

    @Nonnull
    public String getRoleName(@Nonnull CitizenData citizen) {
        return "HyCitizens_" + citizen.getId() + "_Role";
    }

    @Nonnull
    public String generateRole(@Nonnull CitizenData citizen) {
        String moveType = citizen.getMovementBehavior().getType();
        boolean isIdle = "IDLE".equals(moveType);

        String roleName = getRoleName(citizen);

        JsonObject roleJson;
        if (isIdle) {
            roleJson = generateIdleRole(citizen);
        } else {
            roleJson = generateVariantRole(citizen);
        }

        writeRoleFile(roleName, roleJson);
        return roleName;
    }

    @Nonnull
    public String regenerateRole(@Nonnull CitizenData citizen) {
        return generateRole(citizen);
    }

    @Nonnull
    public String getFallbackRoleName(@Nonnull CitizenData citizen) {
        String moveType = citizen.getMovementBehavior().getType();
        boolean interactable = citizen.getFKeyInteractionEnabled();
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

        // MotionControllerList
        JsonArray motionControllers = new JsonArray();
        JsonObject walkController = new JsonObject();
        walkController.addProperty("Type", "Walk");
        motionControllers.add(walkController);
        role.add("MotionControllerList", motionControllers);

        role.addProperty("Appearance", "Player");

        // MaxHealth via Compute
        JsonObject maxHealthCompute = new JsonObject();
        maxHealthCompute.addProperty("Compute", "MaxHealth");
        role.add("MaxHealth", maxHealthCompute);

        // Parameters
        JsonObject parameters = new JsonObject();
        JsonObject maxHealthParam = new JsonObject();
        maxHealthParam.addProperty("Value", citizen.getMaxHealth());
        maxHealthParam.addProperty("Description", "Max health for the NPC");
        parameters.add("MaxHealth", maxHealthParam);
        role.add("Parameters", parameters);

        // Empty instructions for idle
        JsonArray instructions = new JsonArray();
        instructions.add(new JsonObject());
        role.add("Instructions", instructions);

        // InteractionInstruction if F-key interaction is enabled
        if (citizen.getFKeyInteractionEnabled()) {
            role.add("InteractionInstruction", buildInteractionInstruction());
        }

        role.addProperty("NameTranslationKey", citizen.getNameTranslationKey());

        return role;
    }

    @Nonnull
    private JsonObject generateVariantRole(@Nonnull CitizenData citizen) {
        JsonObject role = new JsonObject();
        role.addProperty("Type", "Variant");
        role.addProperty("Reference", "Template_Citizen");

        // Build Modify block with all per-citizen overrides
        JsonObject modify = new JsonObject();

        // Appearance goes inside Modify
        modify.addProperty("Appearance", "Player");

        // Player attitude from citizen attitude setting
        String playerAttitude = mapPlayerAttitude(citizen.getAttitude());
        modify.addProperty("DefaultPlayerAttitude", playerAttitude);

        // Movement
        modify.addProperty("WanderRadius", citizen.getMovementBehavior().getWanderRadius());
        modify.addProperty("MaxSpeed", citizen.getMovementBehavior().getWalkSpeed());
        modify.addProperty("RunThreshold", citizen.getRunThreshold());

        // Detection config
        DetectionConfig detection = citizen.getDetectionConfig();
        modify.addProperty("ViewRange", detection.getViewRange());
        modify.addProperty("ViewSector", detection.getViewSector());
        modify.addProperty("HearingRange", detection.getHearingRange());
        modify.addProperty("AbsoluteDetectionRange", detection.getAbsoluteDetectionRange());
        modify.addProperty("AlertedRange", detection.getAlertedRange());
        modify.addProperty("ChanceToBeAlertedWhenReceivingCallForHelp", detection.getChanceToBeAlertedWhenReceivingCallForHelp());
        modify.addProperty("InvestigateRange", detection.getInvestigateRange());

        // Detection range arrays
        modify.add("AlertedTime", rangeArray(detection.getAlertedTimeMin(), detection.getAlertedTimeMax()));
        modify.add("ConfusedTimeRange", rangeArray(detection.getConfusedTimeMin(), detection.getConfusedTimeMax()));
        modify.add("SearchTimeRange", rangeArray(detection.getSearchTimeMin(), detection.getSearchTimeMax()));

        // Health and leash
        modify.addProperty("MaxHealth", citizen.getMaxHealth());
        modify.addProperty("LeashDistance", citizen.getLeashDistance());
        modify.addProperty("LeashMinPlayerDistance", citizen.getLeashMinPlayerDistance());
        modify.addProperty("HardLeashDistance", citizen.getHardLeashDistance());
        modify.add("LeashTimer", rangeArray(citizen.getLeashTimerMin(), citizen.getLeashTimerMax()));

        // Combat config
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

        // Combat range arrays
        modify.add("DesiredAttackDistanceRange", rangeArray(combat.getDesiredAttackDistanceMin(), combat.getDesiredAttackDistanceMax()));
        modify.add("AttackPauseRange", rangeArray(combat.getAttackPauseMin(), combat.getAttackPauseMax()));
        modify.add("CombatStrafingDurationRange", rangeArray(combat.getCombatStrafingDurationMin(), combat.getCombatStrafingDurationMax()));
        modify.add("CombatStrafingFrequencyRange", rangeArray(combat.getCombatStrafingFrequencyMin(), combat.getCombatStrafingFrequencyMax()));
        modify.add("CombatAttackPreDelay", rangeArray(combat.getCombatAttackPreDelayMin(), combat.getCombatAttackPreDelayMax()));
        modify.add("CombatAttackPostDelay", rangeArray(combat.getCombatAttackPostDelayMin(), combat.getCombatAttackPostDelayMax()));
        modify.add("CombatBackOffDistanceRange", rangeArray(combat.getBackOffDistance(), combat.getBackOffDistance()));
        modify.add("CombatBackOffDurationRange", rangeArray(combat.getBackOffDurationMin(), combat.getBackOffDurationMax()));
        modify.add("TargetSwitchTimer", rangeArray(combat.getTargetSwitchTimerMin(), combat.getTargetSwitchTimerMax()));

        // Path config
        PathConfig pathConfig = citizen.getPathConfig();
        modify.addProperty("FollowPatrolPath", pathConfig.isFollowPath());
        modify.addProperty("PatrolPathName", pathConfig.getPathName());
        modify.addProperty("Patrol", pathConfig.isPatrol());
        modify.addProperty("PatrolWanderDistance", pathConfig.getPatrolWanderDistance());

        // Separation
        modify.addProperty("ApplySeparation", citizen.isApplySeparation());

        // Extended parameters
        modify.addProperty("DropList", citizen.getDropList());
        modify.addProperty("WakingIdleBehaviorComponent", citizen.getWakingIdleBehaviorComponent());
        modify.addProperty("AttitudeGroup", citizen.getAttitudeGroup());
        modify.addProperty("NameTranslationKey", citizen.getNameTranslationKey());
        modify.addProperty("BreathesInWater", citizen.isBreathesInWater());

        // Day flavor animation (only set if non-empty)
        if (!citizen.getDayFlavorAnimation().isEmpty()) {
            modify.addProperty("DayFlavorAnimation", citizen.getDayFlavorAnimation());
            modify.add("DayFlavorAnimationLength", rangeArray(citizen.getDayFlavorAnimationLengthMin(), citizen.getDayFlavorAnimationLengthMax()));
        }

        // Hotbar/weapon slots
        modify.addProperty("DefaultHotbarSlot", citizen.getDefaultHotbarSlot());
        modify.addProperty("RandomIdleHotbarSlot", citizen.getRandomIdleHotbarSlot());
        modify.addProperty("ChanceToEquipFromIdleHotbarSlot", citizen.getChanceToEquipFromIdleHotbarSlot());
        modify.addProperty("DefaultOffHandSlot", citizen.getDefaultOffHandSlot());
        modify.addProperty("NighttimeOffhandSlot", citizen.getNighttimeOffhandSlot());

        // String array parameters
        addStringArrayIfNotEmpty(modify, "CombatMessageTargetGroups", citizen.getCombatMessageTargetGroups());
        addStringArrayIfNotEmpty(modify, "FlockArray", citizen.getFlockArray());
        addStringArray(modify, "DisableDamageGroups", citizen.getDisableDamageGroups());

        // DefaultNPCAttitude inside Modify
        modify.addProperty("DefaultNPCAttitude", citizen.getDefaultNpcAttitude());

        // InteractionInstruction inside Modify via Compute reference
        if (citizen.getFKeyInteractionEnabled()) {
            JsonObject interactionCompute = new JsonObject();
            interactionCompute.addProperty("Compute", "InteractionInstruction");
            modify.add("InteractionInstruction", interactionCompute);
        }

        role.add("Modify", modify);

        // Parameters block
        JsonObject parameters = new JsonObject();

        JsonObject maxHealthParam = new JsonObject();
        maxHealthParam.addProperty("Value", citizen.getMaxHealth());
        parameters.add("MaxHealth", maxHealthParam);

        // InteractionInstruction parameter value
        if (citizen.getFKeyInteractionEnabled()) {
            JsonObject interactionParam = new JsonObject();
            interactionParam.add("Value", buildInteractionInstruction());
            parameters.add("InteractionInstruction", interactionParam);
        }

        role.add("Parameters", parameters);

        return role;
    }

    @Nonnull
    private JsonObject buildInteractionInstruction() {
        JsonObject interactionInstruction = new JsonObject();
        JsonArray instructions = new JsonArray();

        // First instruction: SetInteractable
        JsonObject setInteractable = new JsonObject();
        setInteractable.addProperty("Continue", true);

        JsonObject anySensor = new JsonObject();
        anySensor.addProperty("Type", "Any");
        setInteractable.add("Sensor", anySensor);

        JsonArray setActions = new JsonArray();
        JsonObject setAction = new JsonObject();
        setAction.addProperty("Type", "SetInteractable");
        setAction.addProperty("Interactable", true);
        setActions.add(setAction);
        setInteractable.add("Actions", setActions);

        instructions.add(setInteractable);

        // Second instruction: HasInteracted -> CitizenInteraction
        JsonObject hasInteracted = new JsonObject();

        JsonObject hasInteractedSensor = new JsonObject();
        hasInteractedSensor.addProperty("Type", "HasInteracted");
        hasInteracted.add("Sensor", hasInteractedSensor);

        JsonArray interactActions = new JsonArray();
        JsonObject interactAction = new JsonObject();
        interactAction.addProperty("Type", "CitizenInteraction");
        interactActions.add(interactAction);
        hasInteracted.add("Actions", interactActions);

        instructions.add(hasInteracted);

        interactionInstruction.add("Instructions", instructions);
        return interactionInstruction;
    }

    @Nonnull
    private String mapPlayerAttitude(@Nonnull String citizenAttitude) {
        return switch (citizenAttitude) {
            case "AGGRESSIVE" -> "Hostile";
            case "NEUTRAL" -> "Neutral";
            default -> "Ignore"; // PASSIVE
        };
    }

    // Helper: create a [min, max] JsonArray
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

    // Helper: add a string array to a JsonObject
    private void addStringArray(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull List<String> values) {
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(v);
        }
        obj.add(key, arr);
    }

    public void writeRoleFile(@Nonnull String roleName, @Nonnull JsonObject roleJson) {
        File roleFile = new File(generatedRolesDir, roleName + ".json");
        String newContent = gson.toJson(roleJson);

        // Skip writing if the file content hasn't changed to avoid triggering
        // Hytale's hot-reload which resets NPC appearance (including skins)
        if (roleFile.exists()) {
            try {
                String existingContent = Files.readString(roleFile.toPath());
                if (existingContent.equals(newContent)) {
                    return;
                }
            } catch (IOException ignored) {
            }
        }

        try (FileWriter writer = new FileWriter(roleFile)) {
            writer.write(newContent);
        } catch (IOException e) {
            getLogger().atSevere().log("Failed to write role file: " + roleName + " - " + e.getMessage());
        }
    }

    public void deleteRoleFile(@Nonnull String citizenId) {
        String roleName = "HyCitizens_" + citizenId + "_Role";
        File roleFile = new File(generatedRolesDir, roleName + ".json");
        if (roleFile.exists()) {
            roleFile.delete();
        }
    }

    public void regenerateAllRoles(@Nonnull Collection<CitizenData> citizens) {
        for (CitizenData citizen : citizens) {
            generateRole(citizen);
        }
        getLogger().atInfo().log("Regenerated " + citizens.size() + " citizen role files.");
    }
}
