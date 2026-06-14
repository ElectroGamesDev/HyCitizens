package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class CombatConfig {
    private String attackType;
    private boolean attackTypeManuallySet;
    private boolean combatStyleManuallySet;
    private float attackDistance;
    private float chaseSpeed;
    private float combatBehaviorDistance;
    private int combatStrafeWeight;
    private int combatDirectWeight;
    private boolean backOffAfterAttack;
    private float backOffDistance;

    // Extended combat parameters from Template_Citizen
    private float desiredAttackDistanceMin;
    private float desiredAttackDistanceMax;
    private float attackPauseMin;
    private float attackPauseMax;
    private float combatRelativeTurnSpeed;
    private int combatAlwaysMovingWeight;
    private float combatStrafingDurationMin;
    private float combatStrafingDurationMax;
    private float combatStrafingFrequencyMin;
    private float combatStrafingFrequencyMax;
    private float combatAttackPreDelayMin;
    private float combatAttackPreDelayMax;
    private float combatAttackPostDelayMin;
    private float combatAttackPostDelayMax;
    private float backOffDurationMin;
    private float backOffDurationMax;
    private String blockAbility;
    private int blockProbability;
    private float combatFleeIfTooCloseDistance;
    private float targetSwitchTimerMin;
    private float targetSwitchTimerMax;
    private float targetRange;
    private float combatMovingRelativeSpeed;
    private float combatBackwardsRelativeSpeed;
    private boolean useCombatActionEvaluator;

    public CombatConfig() {
        this.attackType = "Root_NPC_Attack_Melee";
        this.attackTypeManuallySet = false;
        this.combatStyleManuallySet = false;
        this.attackDistance = 2.0f;
        this.chaseSpeed = 0.67f;
        this.combatBehaviorDistance = 5.0f;
        this.combatStrafeWeight = 10;
        this.combatDirectWeight = 10;
        this.backOffAfterAttack = true;
        this.backOffDistance = 4.0f;

        this.desiredAttackDistanceMin = 1.5f;
        this.desiredAttackDistanceMax = 1.5f;
        this.attackPauseMin = 1.5f;
        this.attackPauseMax = 2.0f;
        this.combatRelativeTurnSpeed = 1.5f;
        this.combatAlwaysMovingWeight = 0;
        this.combatStrafingDurationMin = 1.0f;
        this.combatStrafingDurationMax = 1.0f;
        this.combatStrafingFrequencyMin = 2.0f;
        this.combatStrafingFrequencyMax = 2.0f;
        this.combatAttackPreDelayMin = 0.2f;
        this.combatAttackPreDelayMax = 0.2f;
        this.combatAttackPostDelayMin = 0.2f;
        this.combatAttackPostDelayMax = 0.2f;
        this.backOffDurationMin = 2.0f;
        this.backOffDurationMax = 3.0f;
        this.blockAbility = "Shield_Block";
        this.blockProbability = 50;
        this.combatFleeIfTooCloseDistance = 0f;
        this.targetSwitchTimerMin = 5.0f;
        this.targetSwitchTimerMax = 5.0f;
        this.targetRange = 4.0f;
        this.combatMovingRelativeSpeed = 0.6f;
        this.combatBackwardsRelativeSpeed = 0.3f;
        this.useCombatActionEvaluator = false;
    }

    public void copyFrom(@Nonnull CombatConfig other) {
        this.attackType = other.attackType;
        this.attackTypeManuallySet = other.attackTypeManuallySet;
        this.combatStyleManuallySet = other.combatStyleManuallySet;
        this.attackDistance = other.attackDistance;
        this.chaseSpeed = other.chaseSpeed;
        this.combatBehaviorDistance = other.combatBehaviorDistance;
        this.combatStrafeWeight = other.combatStrafeWeight;
        this.combatDirectWeight = other.combatDirectWeight;
        this.backOffAfterAttack = other.backOffAfterAttack;
        this.backOffDistance = other.backOffDistance;
        this.desiredAttackDistanceMin = other.desiredAttackDistanceMin;
        this.desiredAttackDistanceMax = other.desiredAttackDistanceMax;
        this.attackPauseMin = other.attackPauseMin;
        this.attackPauseMax = other.attackPauseMax;
        this.combatRelativeTurnSpeed = other.combatRelativeTurnSpeed;
        this.combatAlwaysMovingWeight = other.combatAlwaysMovingWeight;
        this.combatStrafingDurationMin = other.combatStrafingDurationMin;
        this.combatStrafingDurationMax = other.combatStrafingDurationMax;
        this.combatStrafingFrequencyMin = other.combatStrafingFrequencyMin;
        this.combatStrafingFrequencyMax = other.combatStrafingFrequencyMax;
        this.combatAttackPreDelayMin = other.combatAttackPreDelayMin;
        this.combatAttackPreDelayMax = other.combatAttackPreDelayMax;
        this.combatAttackPostDelayMin = other.combatAttackPostDelayMin;
        this.combatAttackPostDelayMax = other.combatAttackPostDelayMax;
        this.backOffDurationMin = other.backOffDurationMin;
        this.backOffDurationMax = other.backOffDurationMax;
        this.blockAbility = other.blockAbility;
        this.blockProbability = other.blockProbability;
        this.combatFleeIfTooCloseDistance = other.combatFleeIfTooCloseDistance;
        this.targetSwitchTimerMin = other.targetSwitchTimerMin;
        this.targetSwitchTimerMax = other.targetSwitchTimerMax;
        this.targetRange = other.targetRange;
        this.combatMovingRelativeSpeed = other.combatMovingRelativeSpeed;
        this.combatBackwardsRelativeSpeed = other.combatBackwardsRelativeSpeed;
        this.useCombatActionEvaluator = other.useCombatActionEvaluator;
    }

    // Getters and Setters

    public String getAttackType() { return attackType; }
    public void setAttackType(String attackType) { this.attackType = attackType != null ? attackType : "Root_NPC_Attack_Melee"; }

    public boolean isAttackTypeManuallySet() { return attackTypeManuallySet; }
    public void setAttackTypeManuallySet(boolean attackTypeManuallySet) { this.attackTypeManuallySet = attackTypeManuallySet; }

    public boolean isCombatStyleManuallySet() { return combatStyleManuallySet; }
    public void setCombatStyleManuallySet(boolean combatStyleManuallySet) { this.combatStyleManuallySet = combatStyleManuallySet; }

    public float getAttackDistance() { return attackDistance; }
    public void setAttackDistance(float attackDistance) { this.attackDistance = attackDistance; }

    public float getChaseSpeed() { return chaseSpeed; }
    public void setChaseSpeed(float chaseSpeed) { this.chaseSpeed = chaseSpeed; }

    public float getCombatBehaviorDistance() { return combatBehaviorDistance; }
    public void setCombatBehaviorDistance(float combatBehaviorDistance) { this.combatBehaviorDistance = combatBehaviorDistance; }

    public int getCombatStrafeWeight() { return combatStrafeWeight; }
    public void setCombatStrafeWeight(int combatStrafeWeight) { this.combatStrafeWeight = combatStrafeWeight; }

    public int getCombatDirectWeight() { return combatDirectWeight; }
    public void setCombatDirectWeight(int combatDirectWeight) { this.combatDirectWeight = combatDirectWeight; }

    public boolean isBackOffAfterAttack() { return backOffAfterAttack; }
    public void setBackOffAfterAttack(boolean backOffAfterAttack) { this.backOffAfterAttack = backOffAfterAttack; }

    public float getBackOffDistance() { return backOffDistance; }
    public void setBackOffDistance(float backOffDistance) { this.backOffDistance = backOffDistance; }

    public float getDesiredAttackDistanceMin() { return desiredAttackDistanceMin; }
    public void setDesiredAttackDistanceMin(float v) { this.desiredAttackDistanceMin = v; }

    public float getDesiredAttackDistanceMax() { return desiredAttackDistanceMax; }
    public void setDesiredAttackDistanceMax(float v) { this.desiredAttackDistanceMax = v; }

    public float getAttackPauseMin() { return attackPauseMin; }
    public void setAttackPauseMin(float v) { this.attackPauseMin = v; }

    public float getAttackPauseMax() { return attackPauseMax; }
    public void setAttackPauseMax(float v) { this.attackPauseMax = v; }

    public float getCombatRelativeTurnSpeed() { return combatRelativeTurnSpeed; }
    public void setCombatRelativeTurnSpeed(float v) { this.combatRelativeTurnSpeed = v; }

    public int getCombatAlwaysMovingWeight() { return combatAlwaysMovingWeight; }
    public void setCombatAlwaysMovingWeight(int v) { this.combatAlwaysMovingWeight = v; }

    public float getCombatStrafingDurationMin() { return combatStrafingDurationMin; }
    public void setCombatStrafingDurationMin(float v) { this.combatStrafingDurationMin = v; }

    public float getCombatStrafingDurationMax() { return combatStrafingDurationMax; }
    public void setCombatStrafingDurationMax(float v) { this.combatStrafingDurationMax = v; }

    public float getCombatStrafingFrequencyMin() { return combatStrafingFrequencyMin; }
    public void setCombatStrafingFrequencyMin(float v) { this.combatStrafingFrequencyMin = v; }

    public float getCombatStrafingFrequencyMax() { return combatStrafingFrequencyMax; }
    public void setCombatStrafingFrequencyMax(float v) { this.combatStrafingFrequencyMax = v; }

    public float getCombatAttackPreDelayMin() { return combatAttackPreDelayMin; }
    public void setCombatAttackPreDelayMin(float v) { this.combatAttackPreDelayMin = v; }

    public float getCombatAttackPreDelayMax() { return combatAttackPreDelayMax; }
    public void setCombatAttackPreDelayMax(float v) { this.combatAttackPreDelayMax = v; }

    public float getCombatAttackPostDelayMin() { return combatAttackPostDelayMin; }
    public void setCombatAttackPostDelayMin(float v) { this.combatAttackPostDelayMin = v; }

    public float getCombatAttackPostDelayMax() { return combatAttackPostDelayMax; }
    public void setCombatAttackPostDelayMax(float v) { this.combatAttackPostDelayMax = v; }

    public float getBackOffDurationMin() { return backOffDurationMin; }
    public void setBackOffDurationMin(float v) { this.backOffDurationMin = v; }

    public float getBackOffDurationMax() { return backOffDurationMax; }
    public void setBackOffDurationMax(float v) { this.backOffDurationMax = v; }

    public String getBlockAbility() { return blockAbility; }
    public void setBlockAbility(String v) { this.blockAbility = v; }

    public int getBlockProbability() { return blockProbability; }
    public void setBlockProbability(int v) { this.blockProbability = v; }

    public float getCombatFleeIfTooCloseDistance() { return combatFleeIfTooCloseDistance; }
    public void setCombatFleeIfTooCloseDistance(float v) { this.combatFleeIfTooCloseDistance = v; }

    public float getTargetSwitchTimerMin() { return targetSwitchTimerMin; }
    public void setTargetSwitchTimerMin(float v) { this.targetSwitchTimerMin = v; }

    public float getTargetSwitchTimerMax() { return targetSwitchTimerMax; }
    public void setTargetSwitchTimerMax(float v) { this.targetSwitchTimerMax = v; }

    public float getTargetRange() { return targetRange; }
    public void setTargetRange(float v) { this.targetRange = v; }

    public float getCombatMovingRelativeSpeed() { return combatMovingRelativeSpeed; }
    public void setCombatMovingRelativeSpeed(float v) { this.combatMovingRelativeSpeed = v; }

    public float getCombatBackwardsRelativeSpeed() { return combatBackwardsRelativeSpeed; }
    public void setCombatBackwardsRelativeSpeed(float v) { this.combatBackwardsRelativeSpeed = v; }

    public boolean isUseCombatActionEvaluator() { return useCombatActionEvaluator; }
    public void setUseCombatActionEvaluator(boolean v) { this.useCombatActionEvaluator = v; }
}
