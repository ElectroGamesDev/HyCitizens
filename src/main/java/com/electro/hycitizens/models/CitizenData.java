package com.electro.hycitizens.models;

import com.electro.hycitizens.roles.RoleGenerator;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class CitizenData {
    private final String id;
    private String name;
    private String modelId;
    private Vector3d position;
    private Vector3f rotation;
    private float scale;
    private String requiredPermission;
    private String noPermissionMessage;
    private UUID worldUUID;
    private List<CommandAction> commandActions;
    private UUID spawnedUUID;
    private List<UUID> hologramLineUuids = new ArrayList<>();
    private Ref<EntityStore> npcRef;
    public final Map<UUID, Direction> lastLookDirections = new ConcurrentHashMap<>();
    private boolean rotateTowardsPlayer;
    private boolean hideNametag = false;
    private boolean hideNpc = false;
    private float nametagOffset;
    private boolean fKeyInteractionEnabled;

    // Item-related fields
    private String npcHelmet;
    private String npcChest;
    private String npcLeggings;
    private String npcGloves;
    private String npcHand;
    private String npcOffHand;

    // Skin-related fields
    private boolean isPlayerModel;
    private boolean useLiveSkin;
    private String skinUsername;
    private PlayerSkin cachedSkin;
    private long lastSkinUpdate;
    private transient long createdAt;

    // Behavior fields
    private List<AnimationBehavior> animationBehaviors = new ArrayList<>();
    private MovementBehavior movementBehavior = new MovementBehavior();
    private MessagesConfig messagesConfig = new MessagesConfig();
    private transient Map<UUID, Integer> sequentialMessageIndex = new ConcurrentHashMap<>();
    private transient Map<UUID, Boolean> playersInProximity = new ConcurrentHashMap<>();
    private transient Map<String, Long> lastTimedAnimationPlay = new ConcurrentHashMap<>();
    private transient Map<String, java.util.concurrent.ScheduledFuture<?>> animationStopTasks = new ConcurrentHashMap<>();

    // Attitude and damage fields
    private String attitude = "PASSIVE";
    private boolean takesDamage = false;

    // Respawn fields
    private boolean respawnOnDeath = true;
    private float respawnDelaySeconds = 5.0f;
    private transient boolean awaitingRespawn = false;
    private transient long lastDeathTime = 0;

    // Group field
    private String group = "";

    // New config fields for runtime role generation
    private CombatConfig combatConfig = new CombatConfig();
    private DetectionConfig detectionConfig = new DetectionConfig();
    private PathConfig pathConfig = new PathConfig();
    private float maxHealth = 100;
    private float leashDistance = 45;
    private String defaultNpcAttitude = "Ignore";
    private boolean applySeparation = true;

    // Extended Template_Citizen parameters
    private String dropList = "Empty";
    private float runThreshold = 0.3f;
    private String wakingIdleBehaviorComponent = "Component_Instruction_Waking_Idle";
    private String dayFlavorAnimation = "";
    private float dayFlavorAnimationLengthMin = 3.0f;
    private float dayFlavorAnimationLengthMax = 5.0f;
    private String attitudeGroup = "Empty";
    private String nameTranslationKey = "Citizen";
    private boolean breathesInWater = false;

    // Leash extended parameters
    private float leashMinPlayerDistance = 4.0f;
    private float leashTimerMin = 3.0f;
    private float leashTimerMax = 5.0f;
    private float hardLeashDistance = 200.0f;

    // Hotbar/OffHand slot management
    private int defaultHotbarSlot = 0;
    private int randomIdleHotbarSlot = -1;
    private int chanceToEquipFromIdleHotbarSlot = 5;
    private int defaultOffHandSlot = -1;
    private int nighttimeOffhandSlot = 0;

    // KnockbackScale
    private float knockbackScale = 0.5f;

    // Role weapon/offhand arrays (for NPC combat AI, separate from visual equipment)
    private List<String> weapons = new ArrayList<>(List.of("Weapon_Sword_Iron"));
    private List<String> offHandItems = new ArrayList<>(List.of("Furniture_Crude_Torch"));

    // Group arrays for combat/flocking
    private List<String> combatMessageTargetGroups = new ArrayList<>();
    private List<String> flockArray = new ArrayList<>();
    private List<String> disableDamageGroups = new ArrayList<>(List.of("Self"));

    public CitizenData(@Nonnull String id, @Nonnull String name, @Nonnull String modelId, @Nonnull UUID worldUUID,
                       @Nonnull Vector3d position, @Nonnull Vector3f rotation, float scale, @Nullable UUID npcUUID,
                       @Nullable List<UUID> hologramLineUuids, @Nonnull String requiredPermission, @Nonnull String noPermissionMessage,
                       @Nonnull List<CommandAction> commandActions, boolean isPlayerModel, boolean useLiveSkin,
                       @Nullable String skinUsername, @Nullable PlayerSkin cachedSkin, long lastSkinUpdate,
                       boolean rotateTowardsPlayer) {
        this.id = id;
        this.name = name;
        this.modelId = modelId;
        this.worldUUID = worldUUID;
        this.position = position;
        this.rotation = rotation;
        this.scale = scale;
        this.requiredPermission = requiredPermission;
        this.noPermissionMessage = noPermissionMessage;
        this.commandActions = new ArrayList<>(commandActions);
        this.spawnedUUID = npcUUID;
        this.hologramLineUuids = hologramLineUuids;
        this.isPlayerModel = isPlayerModel;
        this.useLiveSkin = useLiveSkin;
        this.skinUsername = skinUsername != null ? skinUsername : "";
        this.cachedSkin = cachedSkin;
        this.lastSkinUpdate = lastSkinUpdate;
        this.createdAt = 0;
        this.npcRef = null;
        this.rotateTowardsPlayer = rotateTowardsPlayer;

        this.npcHelmet = null;
        this.npcChest = null;
        this.npcLeggings = null;
        this.npcGloves = null;
        this.npcHand = null;
        this.npcOffHand = null;

        this.nametagOffset = 0;
        this.hideNametag = false;

        this.fKeyInteractionEnabled = false;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    @Nonnull
    public String getModelId() {
        return modelId;
    }

    public void setModelId(@Nonnull String modelId) {
        this.modelId = modelId;
    }

    @Nonnull
    public UUID getWorldUUID() {
        return worldUUID;
    }

    public void setWorldUUID(@Nonnull UUID worldUUID) {
        this.worldUUID = worldUUID;
    }

    @Nonnull
    public Vector3d getPosition() {
        return position;
    }

    public void setPosition(@Nonnull Vector3d position) {
        this.position = position;
    }

    @Nonnull
    public Vector3f getRotation() {
        return rotation;
    }

    public void setRotation(@Nonnull Vector3f rotation) {
        this.rotation = rotation;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    @Nonnull
    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(@Nonnull String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    @Nonnull
    public String getNoPermissionMessage() {
        return noPermissionMessage;
    }

    public void setNoPermissionMessage(@Nonnull String noPermissionMessage) {
        this.noPermissionMessage = noPermissionMessage;
    }

    @Nonnull
    public List<CommandAction> getCommandActions() {
        return new ArrayList<>(commandActions);
    }

    public void setCommandActions(@Nonnull List<CommandAction> commandActions) {
        this.commandActions = new ArrayList<>(commandActions);
    }

    public void setSpawnedUUID(UUID spawnedUUID) {
        this.spawnedUUID = spawnedUUID;
    }

    public UUID getSpawnedUUID() {
        return spawnedUUID;
    }

    public List<UUID> getHologramLineUuids() {
        return hologramLineUuids;
    }

    public void setHologramLineUuids(@Nullable List<UUID> hologramLineUuids) {
        this.hologramLineUuids = hologramLineUuids;
    }

    public boolean requiresPermission() {
        return !requiredPermission.isEmpty();
    }

    public boolean hasCommands() {
        return !commandActions.isEmpty();
    }

    public boolean getRotateTowardsPlayer() {
        return rotateTowardsPlayer;
    }

    public void setRotateTowardsPlayer(boolean rotateTowardsPlayer) {
        this.rotateTowardsPlayer = rotateTowardsPlayer;
    }

    public boolean isPlayerModel() {
        return isPlayerModel;
    }

    public void setPlayerModel(boolean playerModel) {
        this.isPlayerModel = playerModel;
    }

    public boolean isUseLiveSkin() {
        return useLiveSkin;
    }

    public void setUseLiveSkin(boolean useLiveSkin) {
        this.useLiveSkin = useLiveSkin;
    }

    @Nonnull
    public String getSkinUsername() {
        return skinUsername;
    }

    public void setSkinUsername(@Nullable String skinUsername) {
        this.skinUsername = skinUsername != null ? skinUsername : "";
    }

    public Ref<EntityStore> getNpcRef() {
        return npcRef;
    }

    public void setNpcRef(Ref<EntityStore> npcRef) {
        this.npcRef = npcRef;
    }

    public String getNpcHelmet() {
        return npcHelmet;
    }

    public void setNpcHelmet(String item) {
        this.npcHelmet = item;
    }

    public String getNpcChest() {
        return npcChest;
    }

    public void setNpcChest(String item) {
        this.npcChest = item;
    }

    public String getNpcLeggings() {
        return npcLeggings;
    }

    public void setNpcLeggings(String item) {
        this.npcLeggings = item;
    }

    public String getNpcGloves() {
        return npcGloves;
    }

    public void setNpcGloves(String item) {
        this.npcGloves = item;
    }

    public String getNpcHand() {
        return npcHand;
    }

    public void setNpcHand(String item) {
        this.npcHand = item;
    }

    public String getNpcOffHand() {
        return npcOffHand;
    }

    public void setNpcOffHand(String item) {
        this.npcOffHand = item;
    }

    public void setHideNametag(boolean hideNametag) {
        this.hideNametag = hideNametag;
    }

    public boolean isHideNametag() {
        return hideNametag;
    }

    public void setHideNpc(boolean hideNpc) {
        this.hideNpc = hideNpc;
    }

    public boolean isHideNpc() {
        return hideNpc;
    }

    public void setNametagOffset(float offset) {
        this.nametagOffset = offset;
    }

    public float getNametagOffset() {
        return nametagOffset;
    }

    @Nullable
    public PlayerSkin getCachedSkin() {
        return cachedSkin;
    }

    public void setCachedSkin(@Nullable PlayerSkin cachedSkin) {
        this.cachedSkin = cachedSkin;
    }

    public long getLastSkinUpdate() {
        return lastSkinUpdate;
    }

    public void setLastSkinUpdate(long lastSkinUpdate) {
        this.lastSkinUpdate = lastSkinUpdate;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public void setFKeyInteractionEnabled(boolean enabled) {
        this.fKeyInteractionEnabled = enabled;
    }

    public boolean getFKeyInteractionEnabled() {
        return fKeyInteractionEnabled;
    }

    @Nonnull
    public List<AnimationBehavior> getAnimationBehaviors() {
        return new ArrayList<>(animationBehaviors);
    }

    public void setAnimationBehaviors(@Nonnull List<AnimationBehavior> animationBehaviors) {
        this.animationBehaviors = new ArrayList<>(animationBehaviors);
    }

    @Nonnull
    public MovementBehavior getMovementBehavior() {
        return movementBehavior;
    }

    public void setMovementBehavior(@Nonnull MovementBehavior movementBehavior) {
        this.movementBehavior = movementBehavior;
    }

    @Nonnull
    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }

    public void setMessagesConfig(@Nonnull MessagesConfig messagesConfig) {
        this.messagesConfig = messagesConfig;
    }

    @Nonnull
    public Map<UUID, Integer> getSequentialMessageIndex() {
        return sequentialMessageIndex;
    }

    @Nonnull
    public Map<UUID, Boolean> getPlayersInProximity() {
        return playersInProximity;
    }

    @Nonnull
    public Map<String, Long> getLastTimedAnimationPlay() {
        return lastTimedAnimationPlay;
    }

    @Nonnull
    public Map<String, java.util.concurrent.ScheduledFuture<?>> getAnimationStopTasks() {
        return animationStopTasks;
    }

    @Nonnull
    public String getAttitude() {
        return attitude;
    }

    public void setAttitude(@Nonnull String attitude) {
        this.attitude = attitude;
    }

    public boolean isTakesDamage() {
        return takesDamage;
    }

    public void setTakesDamage(boolean takesDamage) {
        this.takesDamage = takesDamage;
    }

    public boolean isRespawnOnDeath() {
        return respawnOnDeath;
    }

    public void setRespawnOnDeath(boolean respawnOnDeath) {
        this.respawnOnDeath = respawnOnDeath;
    }

    public float getRespawnDelaySeconds() {
        return respawnDelaySeconds;
    }

    public void setRespawnDelaySeconds(float respawnDelaySeconds) {
        this.respawnDelaySeconds = respawnDelaySeconds;
    }

    public boolean isAwaitingRespawn() {
        return awaitingRespawn;
    }

    public void setAwaitingRespawn(boolean awaitingRespawn) {
        this.awaitingRespawn = awaitingRespawn;
    }

    public long getLastDeathTime() {
        return lastDeathTime;
    }

    public void setLastDeathTime(long lastDeathTime) {
        this.lastDeathTime = lastDeathTime;
    }

    @Nonnull
    public String getGroup() {
        return group;
    }

    public void setGroup(@Nullable String group) {
        this.group = group != null ? group : "";
    }

    @Nonnull
    public CombatConfig getCombatConfig() {
        return combatConfig;
    }

    public void setCombatConfig(@Nonnull CombatConfig combatConfig) {
        this.combatConfig = combatConfig;
    }

    @Nonnull
    public DetectionConfig getDetectionConfig() {
        return detectionConfig;
    }

    public void setDetectionConfig(@Nonnull DetectionConfig detectionConfig) {
        this.detectionConfig = detectionConfig;
    }

    @Nonnull
    public PathConfig getPathConfig() {
        return pathConfig;
    }

    public void setPathConfig(@Nonnull PathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
    }

    public float getLeashDistance() {
        return leashDistance;
    }

    public void setLeashDistance(float leashDistance) {
        this.leashDistance = leashDistance;
    }

    @Nonnull
    public String getDefaultNpcAttitude() {
        return defaultNpcAttitude;
    }

    public void setDefaultNpcAttitude(@Nonnull String defaultNpcAttitude) {
        this.defaultNpcAttitude = defaultNpcAttitude;
    }

    public boolean isApplySeparation() {
        return applySeparation;
    }

    public void setApplySeparation(boolean applySeparation) {
        this.applySeparation = applySeparation;
    }

    // Extended Template_Citizen parameter getters/setters

    @Nonnull
    public String getDropList() { return dropList; }
    public void setDropList(@Nonnull String dropList) { this.dropList = dropList; }

    public float getRunThreshold() { return runThreshold; }
    public void setRunThreshold(float runThreshold) { this.runThreshold = runThreshold; }

    @Nonnull
    public String getWakingIdleBehaviorComponent() { return wakingIdleBehaviorComponent; }
    public void setWakingIdleBehaviorComponent(@Nonnull String v) { this.wakingIdleBehaviorComponent = v; }

    @Nonnull
    public String getDayFlavorAnimation() { return dayFlavorAnimation; }
    public void setDayFlavorAnimation(@Nonnull String v) { this.dayFlavorAnimation = v; }

    public float getDayFlavorAnimationLengthMin() { return dayFlavorAnimationLengthMin; }
    public void setDayFlavorAnimationLengthMin(float v) { this.dayFlavorAnimationLengthMin = v; }

    public float getDayFlavorAnimationLengthMax() { return dayFlavorAnimationLengthMax; }
    public void setDayFlavorAnimationLengthMax(float v) { this.dayFlavorAnimationLengthMax = v; }

    @Nonnull
    public String getAttitudeGroup() { return attitudeGroup; }
    public void setAttitudeGroup(@Nonnull String v) { this.attitudeGroup = v; }

    @Nonnull
    public String getNameTranslationKey() { return nameTranslationKey; }
    public void setNameTranslationKey(@Nonnull String v) { this.nameTranslationKey = v; }

    public boolean isBreathesInWater() { return breathesInWater; }
    public void setBreathesInWater(boolean v) { this.breathesInWater = v; }

    public float getLeashMinPlayerDistance() { return leashMinPlayerDistance; }
    public void setLeashMinPlayerDistance(float v) { this.leashMinPlayerDistance = v; }

    public float getLeashTimerMin() { return leashTimerMin; }
    public void setLeashTimerMin(float v) { this.leashTimerMin = v; }

    public float getLeashTimerMax() { return leashTimerMax; }
    public void setLeashTimerMax(float v) { this.leashTimerMax = v; }

    public float getHardLeashDistance() { return hardLeashDistance; }
    public void setHardLeashDistance(float v) { this.hardLeashDistance = v; }

    public int getDefaultHotbarSlot() { return defaultHotbarSlot; }
    public void setDefaultHotbarSlot(int v) { this.defaultHotbarSlot = v; }

    public int getRandomIdleHotbarSlot() { return randomIdleHotbarSlot; }
    public void setRandomIdleHotbarSlot(int v) { this.randomIdleHotbarSlot = v; }

    public int getChanceToEquipFromIdleHotbarSlot() { return chanceToEquipFromIdleHotbarSlot; }
    public void setChanceToEquipFromIdleHotbarSlot(int v) { this.chanceToEquipFromIdleHotbarSlot = v; }

    public int getDefaultOffHandSlot() { return defaultOffHandSlot; }
    public void setDefaultOffHandSlot(int v) { this.defaultOffHandSlot = v; }

    public int getNighttimeOffhandSlot() { return nighttimeOffhandSlot; }
    public void setNighttimeOffhandSlot(int v) { this.nighttimeOffhandSlot = v; }

    public float getKnockbackScale() { return knockbackScale; }
    public void setKnockbackScale(float knockbackScale) { this.knockbackScale = knockbackScale; }

    @Nonnull
    public List<String> getWeapons() { return weapons; }
    public void setWeapons(@Nonnull List<String> v) { this.weapons = new ArrayList<>(v); }

    @Nonnull
    public List<String> getOffHandItems() { return offHandItems; }
    public void setOffHandItems(@Nonnull List<String> v) { this.offHandItems = new ArrayList<>(v); }

    @Nonnull
    public List<String> getCombatMessageTargetGroups() { return combatMessageTargetGroups; }
    public void setCombatMessageTargetGroups(@Nonnull List<String> v) { this.combatMessageTargetGroups = new ArrayList<>(v); }

    @Nonnull
    public List<String> getFlockArray() { return flockArray; }
    public void setFlockArray(@Nonnull List<String> v) { this.flockArray = new ArrayList<>(v); }

    @Nonnull
    public List<String> getDisableDamageGroups() { return disableDamageGroups; }
    public void setDisableDamageGroups(@Nonnull List<String> v) { this.disableDamageGroups = new ArrayList<>(v); }
}