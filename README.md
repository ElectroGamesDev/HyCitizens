# You can download the mod/plguin from [CurseForge](https://www.curseforge.com/hytale/mods/hycitizens)!

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

Saved data includes:

*   Citizen ID
*   Name
*   Model ID
*   World UUID
*   Position / Rotation
*   Scale
*   Permission settings
*   Command actions
*   Spawned entity UUIDs (NPC + hologram)

***

## Discord / Support
If you would like to join the community, suggest features, report bugs, or need some help, join the Discord community! https://discord.gg/ScDW97HDXk

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
id (String) – unique Citizen identifier
name (String) – displayed name
modelId (String) – entity model ID
worldUUID (UUID) – world the Citizen belongs to
position (Vector3d) – spawn position
rotation (Vector3f) – spawn rotation
scale (float) – model scale
spawnedUUID (UUID) – spawned NPC entity UUID (if spawned)
hologramUUID (UUID) – spawned hologram UUID (if spawned)
requiredPermission (String) – permission required to use the Citizen
noPermissionMessage (String) – message shown if missing permission
commandActions (List) – commands to run on interaction
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
        null, // Hologram UUID - You should usually set this as null
        "", // Required Permission
        "", // No Permission Message - Leaving it empty sets a default message
        List.of() // Command actions
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
