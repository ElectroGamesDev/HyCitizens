package com.electro.hycitizens.roles;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.*;
import com.google.gson.*;

import javax.annotation.Nonnull;
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
    private Set<String> getGeneratedRoleNames(@Nonnull CitizenData citizen) {
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
                generateSeekRole(citizen, citizen.getMovementBehavior().getWalkSpeed(), 0.05f, 1.0f));
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
                generateSeekRole(citizen, citizen.getMovementBehavior().getWalkSpeed(), 0.05f, 1.0f));
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
        if (isIdle) {
            return generateIdleRole(citizen);
        } else if (isPatrol) {
            return generatePatrolRole(citizen);
        } else if (isFollowCitizen) {
            return generateSeekRole(citizen, citizen.getMovementBehavior().getWalkSpeed(), 0.05f,
                    Math.max(0.6f, Math.min(1.4f, citizen.getFollowDistance() + 0.35f)));
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
        role.addProperty("KnockbackScale", citizen.getKnockbackScale());

        role.addProperty("NameTranslationKey", citizen.getNameTranslationKey());

        return role;
    }

    @Nonnull
    private JsonObject generatePatrolRole(@Nonnull CitizenData citizen) {
        JsonObject role = new JsonObject();
        role.addProperty("Type", "Generic");
        role.addProperty("Appearance", citizen.getModelId());

        JsonArray motionControllers = new JsonArray();
        JsonObject walkController = new JsonObject();
        walkController.addProperty("Type", "Walk");
        motionControllers.add(walkController);
        role.add("MotionControllerList", motionControllers);

        JsonObject maxHealthCompute = new JsonObject();
        maxHealthCompute.addProperty("Compute", "MaxHealth");
        role.add("MaxHealth", maxHealthCompute);

        JsonObject parameters = new JsonObject();
        JsonObject maxHealthParam = new JsonObject();
        maxHealthParam.addProperty("Value", 100);
        maxHealthParam.addProperty("Description", "Max health for the NPC");
        parameters.add("MaxHealth", maxHealthParam);
        role.add("Parameters", parameters);

        role.addProperty("KnockbackScale", citizen.getKnockbackScale());

        JsonArray instructions = new JsonArray();
        instructions.add(buildSeekInstruction(citizen.getMovementBehavior().getWalkSpeed(), 0.05f, 1.0f));
        role.add("Instructions", instructions);

        role.addProperty("NameTranslationKey", citizen.getNameTranslationKey());

        return role;
    }

    @Nonnull
    private JsonObject generateScheduleTravelRole(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry) {
        return generateSeekRole(citizen, entry.getTravelSpeed(), 0.05f, Math.max(0.75f, entry.getArrivalRadius() + 0.5f));
    }

    @Nonnull
    private JsonObject generateScheduleEntryRole(@Nonnull CitizenData citizen, @Nonnull ScheduleEntry entry) {
        return switch (entry.getActivityType()) {
            case IDLE -> generateIdleRole(citizen);
            case WANDER -> generateScheduleWanderRole(citizen, entry);
            case PATROL -> generateSeekRole(citizen, entry.getTravelSpeed(), 0.05f,
                    Math.max(0.75f, entry.getArrivalRadius() + 0.5f));
            case FOLLOW_CITIZEN -> generateSeekRole(citizen, entry.getTravelSpeed(), 0.05f,
                    Math.max(0.6f, Math.min(1.4f, entry.getFollowDistance() + 0.35f)));
        };
    }

    @Nonnull
    private JsonObject generateSeekRole(@Nonnull CitizenData citizen, float walkSpeed, float stopDistance, float slowDownDistance) {
        JsonObject role = new JsonObject();
        role.addProperty("Type", "Generic");
        role.addProperty("Appearance", citizen.getModelId());

        JsonArray motionControllers = new JsonArray();
        JsonObject walkController = new JsonObject();
        walkController.addProperty("Type", "Walk");
        motionControllers.add(walkController);
        role.add("MotionControllerList", motionControllers);

        JsonObject maxHealthCompute = new JsonObject();
        maxHealthCompute.addProperty("Compute", "MaxHealth");
        role.add("MaxHealth", maxHealthCompute);

        JsonObject parameters = new JsonObject();
        JsonObject maxHealthParam = new JsonObject();
        maxHealthParam.addProperty("Value", 100);
        maxHealthParam.addProperty("Description", "Max health for the NPC");
        parameters.add("MaxHealth", maxHealthParam);
        role.add("Parameters", parameters);

        role.addProperty("KnockbackScale", citizen.getKnockbackScale());

        JsonArray instructions = new JsonArray();
        instructions.add(buildSeekInstruction(walkSpeed, stopDistance, slowDownDistance));
        role.add("Instructions", instructions);

        role.addProperty("NameTranslationKey", citizen.getNameTranslationKey());
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
        modify.addProperty("AttitudeGroup", citizen.getAttitudeGroup());
        modify.addProperty("BreathesInWater", citizen.isBreathesInWater());

        if (!citizen.getDayFlavorAnimation().isEmpty()) {
            modify.addProperty("DayFlavorAnimation", citizen.getDayFlavorAnimation());
            modify.add("DayFlavorAnimationLength", rangeArray(citizen.getDayFlavorAnimationLengthMin(), citizen.getDayFlavorAnimationLengthMax()));
        }

        modify.addProperty("DefaultHotbarSlot", citizen.getDefaultHotbarSlot());
        modify.addProperty("RandomIdleHotbarSlot", citizen.getRandomIdleHotbarSlot());
        modify.addProperty("ChanceToEquipFromIdleHotbarSlot", citizen.getChanceToEquipFromIdleHotbarSlot());
        modify.addProperty("DefaultOffHandSlot", citizen.getDefaultOffHandSlot());
        modify.addProperty("NighttimeOffhandSlot", citizen.getNighttimeOffhandSlot());

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
    private JsonObject buildSeekInstruction(float walkSpeed, float stopDistance, float slowDownDistance) {
        JsonObject instruction = new JsonObject();

        JsonObject sensor = new JsonObject();
        sensor.addProperty("Type", "Target");
        instruction.add("Sensor", sensor);

        JsonObject headMotion = new JsonObject();
        //headMotion.addProperty("Type", "Watch");
        headMotion.addProperty("Type", "Observe");
        headMotion.add("AngleRange", JsonParser.parseString("[-5.0, 5.0]"));
        headMotion.addProperty("PickRandomAngle", true);
        instruction.add("HeadMotion", headMotion);

        JsonObject bodyMotion = new JsonObject();
        bodyMotion.addProperty("Type", "Seek");
        bodyMotion.addProperty("StopDistance", Math.max(0.05f, stopDistance));
        bodyMotion.addProperty("SlowDownDistance", Math.max(stopDistance + 0.25f, slowDownDistance));
        bodyMotion.addProperty("AbortDistance", 500.0);
        bodyMotion.addProperty("RelativeSpeed", Math.max(0.2f, Math.min(3.0f, walkSpeed / 18.0f)));
        bodyMotion.addProperty("UsePathfinder", true);
        instruction.add("BodyMotion", bodyMotion);

        return instruction;
    }

    @Nonnull
    private String mapPlayerAttitude(@Nonnull String citizenAttitude) {
        return switch (citizenAttitude) {
            case "AGGRESSIVE" -> "Hostile";
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
        String prefix = "HyCitizens_" + citizenId + "_";
        File[] files = generatedRolesDir.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".json"));
        if (files == null) {
            return;
        }

        for (File roleFile : files) {
            String fileName = roleFile.getName();
            String roleName = fileName.substring(0, fileName.length() - ".json".length());
            lastGeneratedContent.remove(roleName);
            if (roleFile.exists()) {
                roleFile.delete();
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
}
