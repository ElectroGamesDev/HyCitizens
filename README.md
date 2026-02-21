# You can download the mod/plugin from [CurseForge](https://www.curseforge.com/hytale/mods/hycitizens)!
Note: It's not recommended to build the plugin from source for production use. Changes between updates may not be completely tested or finished.

[![Join us on Discord](https://img.shields.io/badge/Discord-Join%20Community-7289DA?logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/Snqz9E58Dr)
[![Star on GitHub](https://img.shields.io/badge/GitHub-Source-181717?logo=github&logoColor=white&style=for-the-badge)](https://github.com/ElectroGamesDev/HyCitizens)
[![Support me on Ko-fi](https://img.shields.io/badge/Ko--fi-Support-FF0000?logo=kofi&logoColor=white&style=for-the-badge)](https://ko-fi.com/electrogames)

**HyCitizens** is a full in-game NPC (Citizen) management plugin that allows you to create interactive NPCs for your server.  
Everything can be configured directly in-game through a clean UI, with an optional developer API for full control and integration.

***

**Command: /citizens**

## Features

### Citizen / NPC System

Create and manage **Citizens** (NPCs) that can be placed anywhere in your world.

Each Citizen supports:

*   Custom **name**
*   Custom **position**
*   Custom **rotation**
*   Custom **entity type / model id**
*   Visible/hidden nametags
*   Player models and skins (with cached skins and live skins support)
*   Armor and an item in hand
*   Custom **scale**
*   Custom **hit actions**
*   Persistent saving/loading (Citizens stay after restarts)

***

## In-Game Management UI

Use:

/citizens

This opens the **Citizens UI**, where you can:

*   Create new Citizens
*   Edit existing Citizens
*   Delete Citizens
*   Update Citizen properties instantly

No configuration files required to use the plugin.

***

## Interactions (Hit Actions)

Citizens can be configured to run actions when a player **hits/interacts** with them.

### Command Actions

You can add one or more commands to a Citizen that will run when the NPC is hit.

Each command supports:

*   Running as the **Player**
*   Running as the **Server**
*   Optional permission requirement before running

### Permissions

Citizens can require a permission before they can be used.

If a permission is set:

*   Players without the permission will be blocked
*   A custom “no permission” message can be shown

### Messages

Citizens can also send messages to the player when hit/interacted with.

***

## Saving / Persistence

Citizens are stored automatically and restored when the server restarts.

***

## Discord / Support

If you would like to join the community, suggest features, report bugs, or need some help, join the Discord community! [https://discord.gg/Snqz9E58Dr](https://discord.gg/Snqz9E58Dr)

***

## Support HyCitizens

Want to support HyCitizens? You can donate at [Ko-fi](https://ko-fi.com/electrogames) or share HyCitizens with your friends!

***

# Developer API

Citizens includes a full developer API for creating, editing, querying, spawning, and listening to interactions.

***

## Getting the plugin instance:

```
HyCitizensPlugin plugin = HyCitizensPlugin.get();
```

CitizensManager - The main entry point for all Citizen operations:

```
CitizensManager manager = CitizensPlugin.get().getCitizensManager();
```

CitizenData represents a single Citizen and all of its settings.

## Citizen fields:

```
id (String) – unique Citizen identifier.
name (String) – displayed name.
modelId (String) – entity model ID.
position (Vector3d) – spawn position.
rotation (Vector3f) – spawn rotation.
scale (float) – model scale.
requiredPermission (String) – permission required to use the Citizen.
noPermissionMessage (String) – message shown if missing permission.
worldUUID (UUID) – UUID of the world the Citizen belongs to.
commandActions (List<CommandAction>) – commands to run on interaction.
spawnedUUID (UUID) – spawned NPC entity UUID (if spawned).
hologramLineUuids (List<UUID>) – spawned hologram UUIDs (if spawned).
npcRef (Ref<EntityStore>) – reference to the NPC entity store.
lastLookDirections (Map<UUID, Direction>) – tracks the last look direction of players interacting with the Citizen.
rotateTowardsPlayer (boolean) – whether the Citizen always looks at players.
hideNametag (boolean) – whether the Citizen’s nametag is hidden. Default: false.
nametagOffset (float) – vertical offset for the nametag.
npcHelmet (String) – item equipped on the head.
npcChest (String) – item equipped on the chest.
npcLeggings (String) – item equipped on the legs.
npcGloves (String) – item equipped on the hands.
npcHand (String) – item held in main hand.
npcOffHand (String) – item held in offhand.
isPlayerModel (boolean) – whether this Citizen uses a player model instead of a normal entity model.
useLiveSkin (boolean) – enables automatic skin updates every 30 minutes.
skinUsername (String) – username used to fetch skins from PlayerDB.
cachedSkin (PlayerSkin) – stored skin data for this Citizen.
lastSkinUpdate (long) – timestamp of the last skin update.
createdAt (transient long) – timestamp of when the Citizen was created (not serialized).
fKeyInteractionEnabled (boolean) – whether players can interact with the Citizen using the "F" key.
```

## Creating a Citizen

```
CitizenData citizen = new CitizenData(
        "npc_1_id", // ID
        "Example Citizen", // Name
        "Player", // Model ID
        worldUuid, // World UUID
        new Vector3d(0, 70, 0), // Position
        new Vector3f(0, 0, 0), // Rotation
        1.0f, // Scale
        null, // NPC UUID - You should usually set this as null
        new ArrayList<>(), // Hologram UUIDs - You should usually leave this empty
        "", // Required Permission
        "", // No Permission Message - Leaving it empty sets a default message
        List.of(), // Command actions
        true, // Is Player Model
        false, // Use Live Skin
        "Simon", // Skin Username
        null, // Cached Skin - Usually set to null
        0L, // Last Skin Update - Usually set to 0L
        true // Rotate towards players
);

manager.addCitizen(citizen, true); // true means it will save to storage and will respawn every time the world loads.
```

This will add it to the registry, save it to storage, and spawn it in the world.

Please note that when setting a citizen to not save, Hytale automatically respawns entities. Even with this disabled, the entities still may spawn, they will just no be considered citizens. This is perfect if you're creating temporary worlds, else you may need to manually despawn the entities.

## Updating a Citizen

If you edit values inside a CitizenData, call an update method after.

Full update (NPC + hologram) example:

```
citizen.setName("New Name");
citizen.setScale(1.25f);

manager.updateCitizen(citizen, true); // Note: true means it will save to storage
```

Update only the NPC example:

```
citizen.setModelId("New_Model_Id");
manager.updateCitizenNPC(citizen, true); // Note: true means it will save to storage
```

Update only the hologram example:

```
citizen.setName("New Hologram Name");
manager.updateCitizenHologram(citizen, true); // Note: true means it will save to storage
```

Removing a Citizen example:

```
manager.removeCitizen("npc_1_id");
```

This will remove the Citizen from memory, delete the saved data, and despawn the Citizen.

## Spawning / Despawning

Spawn a Citizen:

```
manager.spawnCitizen(citizen, true); // Note: true means it will save to storage
```

Spawn only the NPC:

```
manager.spawnCitizenNPC(citizen, true); // Note: true means it will save to storage
```

Spawn only the hologram:

```
manager.spawnCitizenHologram(citizen, true); // Note: true means it will save to storage
```

Despawn both:

```
manager.despawnCitizen(citizen);
```

Despawn only the NPC:

```
manager.despawnCitizenNPC(citizen);
```

Despawn only the hologram:

```
manager.despawnCitizenHologram(citizen);
```

## Updating player model and skin settings

Make a Citizen use a player model:

```
citizen.setPlayerModel(true);
manager.updateCitizenNPC(citizen, true);
```

Set a cached skin (no live updates):

```
citizen.setPlayerModel(true);
citizen.setUseLiveSkin(false);
citizen.setSkinUsername("Simon");

manager.updateCitizenNPC(citizen, true);
```

Enable live skin updates (updates every 30 minutes):

```
citizen.setPlayerModel(true);
citizen.setUseLiveSkin(true);
citizen.setSkinUsername("Simon");

manager.updateCitizenNPC(citizen, true);
```

## Other Skin API

Update a Citizen skin:

This fetches the skin using the Citizen’s `skinUsername`, caches it, and applies it to the spawned NPC (if spawned).

```
manager.updateCitizenSkin(citizen, true); // true = save to storage
```

Copy skin from a real player (use their current skin):

This will copy the player's current skin and store it as the Citizen’s cached skin.

It will also:

*   Clear the skin username
*   Disable live skin updates
*   Apply the skin instantly

```
manager.updateCitizenSkinFromPlayer(citizen, playerRef, true); // true = save to storage
```

Get a citizen's player skin:

```
manager.determineSkin(citizen)
```

Force an instant skin update:

```
manager.updateCitizenSkin(citizen, true);
```

## Access NPC Entity Reference

Get or set the NPC entity reference:

```
Ref<EntityStore> ref = citizen.getNpcRef();
citizen.setNpcRef(ref);
```

### Rotate Towards Player

Get or set whether the Citizen rotates towards nearby players:

```
boolean rotates = citizen.getRotateTowardsPlayer();
citizen.setRotateTowardsPlayer(true);
```

### NPC Equipment

These methods allow you to get or set the items equipped by the Citizen.

```
citizen.getNpcHelmet();
citizen.setNpcHelmet("Armor_Adamantite_Head");

citizen.getNpcChest();
citizen.setNpcChest("Armor_Adamantite_Chest");

citizen.getNpcLeggings();
citizen.setNpcLeggings("Armor_Adamantite_Legs");

citizen.getNpcGloves();
citizen.setNpcGloves("Armor_Adamantite_Hands");

citizen.getNpcHand();
citizen.setNpcHand("Weapon_Sword_Adamantite");

// Note: citizens currently can not hold items in their offhand
citizen.getNpcOffHand();
citizen.setNpcOffHand("Weapon_Shield_Adamantite");
```

**Important:** These changes **do not automatically save and apply**.  
You must call the following to apply the items to an existing citizen:

```
manager.updateCitizenNPCItems(citizen); // or manager.updateCitizen(citizen)
```

You must call the following to save the items to storage:

```
manager.saveCitizen(citizen);
```

### "F" Key Interaction

To get or set "F" key interactions:

```
boolean fKeyInteractions = getFKeyInteractionEnabled();
citizen.setFKeyInteractionEnabled(true);
```

## Other Nametag Settings

Hide or adjust the Citizen’s nametag:

```
citizen.setHideNametag(true);
boolean hidden = citizen.isHideNametag();

citizen.setNametagOffset(1.5f);
float offset = citizen.getNametagOffset();
```

## Notes about player model Citizens

*   If `isPlayerModel = true`, the Citizen will spawn using a player model instead of a normal model ID.
*   If `useLiveSkin = true`, skins will automatically refresh every 30 minutes.
*   If live skin fails or is missing, the Citizen will fall back to a default skin.

## Getting Citizens

Get by ID:

```
CitizenData citizen = manager.getCitizen("npc_1_id");
```

Get all Citizens:

```
List<CitizenData> citizens = manager.getAllCitizens();
```

Count Citizens:

```
int count = manager.getCitizenCount();
```

Check if a Citizen exists:

```
boolean exists = manager.citizenExists("npc_1_id");
```

Find Citizens near a position:

```
List<CitizenData> nearby = manager.getCitizensNear(playerPos, 10.0);
```

## CommandAction API

CommandAction represents a single command OR message that can be executed when a Citizen is hit.

Create a CommandAction:

```
CommandAction action = new CommandAction("spawn", true); // True mean it will run as server. False would means it runs as the player
```

Methods:

```
getCommand() - Returns the raw command (without "/")

getFormattedCommand() - Returns command with "/" prefix

isRunAsServer() - Whether it runs as server or player
```

Variables/Placeholders:

```
{PlayerName} - Will be replaced at runtime with the player's username
{CitizenName} - Will be replaced at runtime with the citizen's name
```

Sending Messages:  
Sending messages uses command actions. To send a message to the player, start the command with "{SendMessage}" and then enter your message after that. You are able to customize the message with colors using either {ColorName} or {HEX} BEFORE the text. Note: For the HEX, you need to include the "#". for example, red would be {#FF0000}.

Example:

```
List<CommandAction> actions = new ArrayList<>();

actions.add(new CommandAction("tp {PlayerName} 500 100 500", true));

actions.add(new CommandAction("{SendMessage} {LIME} Welcome to the {#FF0000} PVP ARENA {GREEN}!", false));

citizen.setCommandActions(actions);
manager.updateCitizen(citizen, true);
```

## Citizen Interaction Event API

Citizens includes an interaction event system developers can hook into.

`CitizenInteractEvent` - Fired when a player interacts with / hits a Citizen.

Event Fields:

```
getCitizen() - The CitizenData being interacted with

getPlayer() - The PlayerRef who interacted

isCancelled() - Whether the event is cancelled

setCancelled(true) - Cancels further handling
```

`CitizenInteractListener` - To listen for Citizen interactions:

```
manager.addCitizenInteractListener(event -> {
    CitizenData citizen = event.getCitizen();
    PlayerRef player = event.getPlayer();

    // Example: Block interaction with a specific citizen
    if (citizen.getId().equalsIgnoreCase("npc_1_id")) {
        event.setCancelled(true);
        player.sendMessage(Message.raw("You can't use this citizen right now!"));
    }
});
```

Removing a Listener:

```
manager.removeCitizenInteractListener(listenerInstance);
```

Manually Firing the Event - If you need to trigger a CitizenInteractEvent manually:

```
CitizenInteractEvent event = new CitizenInteractEvent(citizen, playerRef);
manager.fireCitizenInteractEvent(event);
```

If any listener cancels the event, further listeners will not be called, including the command actions.

***

## Notes

Citizens are spawned using the built-in NPC system and tracked using UUIDs.

Citizens automatically spawn their hologram nameplate above them based on their scale.

Citizens are designed to be fully usable in-game, while still providing a complete API for developers.

## Credits

This plugin has been made possible by [HyUI](https://www.curseforge.com/hytale/mods/hyui).
