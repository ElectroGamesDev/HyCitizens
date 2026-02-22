package com.electro.hycitizens.roles;

import com.electro.hycitizens.models.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class RoleGenerator {
    private final File generatedRolesDir;
    private final Gson gson;
    private final Map<String, String> lastGeneratedContent = new ConcurrentHashMap<>();

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
        generateRoleIfChanged(citizen);
        return getRoleName(citizen);
    }

    // Returns true if the role file was actually written (content changed)
    public boolean generateRoleIfChanged(@Nonnull CitizenData citizen) {
        String moveType = citizen.getMovementBehavior().getType();
        boolean isIdle = "IDLE".equals(moveType);

        String roleName = getRoleName(citizen);

        JsonObject roleJson;
        if (isIdle) {
            roleJson = generateIdleRole(citizen);
        } else {
            roleJson = generateVariantRole(citizen);
        }

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
        role.addProperty("Appearance", "Player");

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
        maxHealthParam.addProperty("Value", citizen.getMaxHealth());
        maxHealthParam.addProperty("Description", "Max health for the NPC");
        parameters.add("MaxHealth", maxHealthParam);
        role.add("Parameters", parameters);

        // KnockbackScale
        role.addProperty("KnockbackScale", citizen.getKnockbackScale());

        // Empty instructions for idle
        JsonArray instructions = new JsonArray();
        role.add("Instructions", instructions);

//        if (citizen.getFKeyInteractionEnabled()) {
//            role.add("InteractionInstruction", buildInteractionInstruction());
//        }

        role.addProperty("NameTranslationKey", citizen.getNameTranslationKey());

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

        // Modifies
        JsonObject modify = new JsonObject();
        modify.addProperty("DefaultPlayerAttitude", mapPlayerAttitude(citizen.getAttitude()));
        modify.addProperty("WanderRadius", citizen.getMovementBehavior().getWanderRadius());
        role.add("Modify", modify);

        // Parameters
        JsonObject parameters = new JsonObject();

        DetectionConfig detection = citizen.getDetectionConfig();
        addParam(parameters, "ViewRange", detection.getViewRange());
        addParam(parameters, "ViewSector", detection.getViewSector());
        addParam(parameters, "HearingRange", detection.getHearingRange());
        addParam(parameters, "AbsoluteDetectionRange", detection.getAbsoluteDetectionRange());
        addParam(parameters, "AlertedRange", detection.getAlertedRange());
        addParam(parameters, "ChanceToBeAlertedWhenReceivingCallForHelp", detection.getChanceToBeAlertedWhenReceivingCallForHelp());
        addParam(parameters, "InvestigateRange", detection.getInvestigateRange());
        addParamArray(parameters, "AlertedTime", detection.getAlertedTimeMin(), detection.getAlertedTimeMax());
        addParamArray(parameters, "ConfusedTimeRange", detection.getConfusedTimeMin(), detection.getConfusedTimeMax());
        addParamArray(parameters, "SearchTimeRange", detection.getSearchTimeMin(), detection.getSearchTimeMax());

        addParam(parameters, "KnockbackScale", citizen.getKnockbackScale());

        JsonObject appearanceParam = new JsonObject();
        appearanceParam.addProperty("Value", "Player");
        appearanceParam.addProperty("Description", "Model to be used");
        parameters.add("Appearance", appearanceParam);

        JsonObject translationParam = new JsonObject();
        translationParam.addProperty("Value", citizen.getNameTranslationKey());
        translationParam.addProperty("Description", "Translation key for NPC name display");
        parameters.add("NameTranslationKey", translationParam);

        addParam(parameters, "DefaultNPCAttitude", citizen.getDefaultNpcAttitude());

        addParam(parameters, "MaxHealth", citizen.getMaxHealth());
        addParam(parameters, "MaxSpeed", citizen.getMovementBehavior().getWalkSpeed());
        addParam(parameters, "RunThreshold", citizen.getRunThreshold());

        addParam(parameters, "LeashDistance", citizen.getLeashDistance());
        addParam(parameters, "LeashMinPlayerDistance", citizen.getLeashMinPlayerDistance());
        addParam(parameters, "HardLeashDistance", citizen.getHardLeashDistance());
        addParamArray(parameters, "LeashTimer", citizen.getLeashTimerMin(), citizen.getLeashTimerMax());

        CombatConfig combat = citizen.getCombatConfig();
        addParamString(parameters, "Attack", combat.getAttackType());
        addParam(parameters, "AttackDistance", combat.getAttackDistance());
        addParam(parameters, "ChaseRelativeSpeed", combat.getChaseSpeed());
        addParam(parameters, "CombatBehaviorDistance", combat.getCombatBehaviorDistance());
        addParam(parameters, "CombatRelativeTurnSpeed", combat.getCombatRelativeTurnSpeed());
        addParam(parameters, "CombatDirectWeight", combat.getCombatDirectWeight());
        addParam(parameters, "CombatStrafeWeight", combat.getCombatStrafeWeight());
        addParam(parameters, "CombatAlwaysMovingWeight", combat.getCombatAlwaysMovingWeight());
        addParam(parameters, "CombatBackOffAfterAttack", combat.isBackOffAfterAttack());
        addParam(parameters, "CombatMovingRelativeSpeed", combat.getCombatMovingRelativeSpeed());
        addParam(parameters, "CombatBackwardsRelativeSpeed", combat.getCombatBackwardsRelativeSpeed());
        addParam(parameters, "UseCombatActionEvaluator", combat.isUseCombatActionEvaluator());
        addParamString(parameters, "BlockAbility", combat.getBlockAbility());
        addParam(parameters, "BlockProbability", combat.getBlockProbability());
        addParam(parameters, "CombatFleeIfTooCloseDistance", combat.getCombatFleeIfTooCloseDistance());
        addParam(parameters, "TargetRange", combat.getTargetRange());
        addParamArray(parameters, "DesiredAttackDistanceRange", combat.getDesiredAttackDistanceMin(), combat.getDesiredAttackDistanceMax());
        addParamArray(parameters, "AttackPauseRange", combat.getAttackPauseMin(), combat.getAttackPauseMax());
        addParamArray(parameters, "CombatStrafingDurationRange", combat.getCombatStrafingDurationMin(), combat.getCombatStrafingDurationMax());
        addParamArray(parameters, "CombatStrafingFrequencyRange", combat.getCombatStrafingFrequencyMin(), combat.getCombatStrafingFrequencyMax());
        addParamArray(parameters, "CombatAttackPreDelay", combat.getCombatAttackPreDelayMin(), combat.getCombatAttackPreDelayMax());
        addParamArray(parameters, "CombatAttackPostDelay", combat.getCombatAttackPostDelayMin(), combat.getCombatAttackPostDelayMax());
        addParamArray(parameters, "CombatBackOffDistanceRange", combat.getBackOffDistance(), combat.getBackOffDistance());
        addParamArray(parameters, "CombatBackOffDurationRange", combat.getBackOffDurationMin(), combat.getBackOffDurationMax());
        addParamArray(parameters, "TargetSwitchTimer", combat.getTargetSwitchTimerMin(), combat.getTargetSwitchTimerMax());

        PathConfig pathConfig = citizen.getPathConfig();
        addParam(parameters, "FollowPatrolPath", pathConfig.isFollowPath());
        addParamString(parameters, "PatrolPathName", pathConfig.getPathName());
        addParam(parameters, "Patrol", pathConfig.isPatrol());
        addParam(parameters, "PatrolWanderDistance", pathConfig.getPatrolWanderDistance());

        addParam(parameters, "ApplySeparation", citizen.isApplySeparation());
        addParamStringArray(parameters, "Weapons", citizen.getWeapons());
        addParamStringArray(parameters, "OffHand", citizen.getOffHandItems());

        addParamString(parameters, "DropList", citizen.getDropList());
        addParamString(parameters, "WakingIdleBehaviorComponent", citizen.getWakingIdleBehaviorComponent());
        addParamString(parameters, "AttitudeGroup", citizen.getAttitudeGroup());
        addParam(parameters, "BreathesInWater", citizen.isBreathesInWater());

        if (!citizen.getDayFlavorAnimation().isEmpty()) {
            addParamString(parameters, "DayFlavorAnimation", citizen.getDayFlavorAnimation());
            addParamArray(parameters, "DayFlavorAnimationLength", citizen.getDayFlavorAnimationLengthMin(), citizen.getDayFlavorAnimationLengthMax());
        }

        addParam(parameters, "DefaultHotbarSlot", citizen.getDefaultHotbarSlot());
        addParam(parameters, "RandomIdleHotbarSlot", citizen.getRandomIdleHotbarSlot());
        addParam(parameters, "ChanceToEquipFromIdleHotbarSlot", citizen.getChanceToEquipFromIdleHotbarSlot());
        addParam(parameters, "DefaultOffHandSlot", citizen.getDefaultOffHandSlot());
        addParam(parameters, "NighttimeOffhandSlot", citizen.getNighttimeOffhandSlot());

        if (!citizen.getCombatMessageTargetGroups().isEmpty()) {
            addParamStringArray(parameters, "CombatMessageTargetGroups", citizen.getCombatMessageTargetGroups());
        }
        if (!citizen.getFlockArray().isEmpty()) {
            addParamStringArray(parameters, "FlockArray", citizen.getFlockArray());
        }
        addParamStringArray(parameters, "DisableDamageGroups", citizen.getDisableDamageGroups());

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
        return switch (citizenAttitude) {
            case "AGGRESSIVE" -> "Hostile";
            case "NEUTRAL" -> "Neutral";
            default -> "Ignore"; // PASSIVE
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

    // Helper: add a string array to a JsonObject
    private void addStringArray(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull List<String> values) {
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(v);
        }
        obj.add(key, arr);
    }

    public void writeRoleFile(@Nonnull String roleName, @Nonnull String content) {
        File roleFile = new File(generatedRolesDir, roleName + ".json");
        try (FileWriter writer = new FileWriter(roleFile)) {
            writer.write(content);
        } catch (IOException e) {
            getLogger().atSevere().log("Failed to write role file: " + roleName + " - " + e.getMessage());
        }
    }

    public void deleteRoleFile(@Nonnull String citizenId) {
        String roleName = "HyCitizens_" + citizenId + "_Role";
        lastGeneratedContent.remove(roleName);
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
