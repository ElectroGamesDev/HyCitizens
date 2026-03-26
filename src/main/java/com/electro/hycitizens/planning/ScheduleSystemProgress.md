# Daily Schedule System Progress

This file tracks the design and implementation progress for the HyCitizens daily schedule system.

## Goals

- Add a full daily schedule system using Hytale's 0-24 hour world time.
- Support time-based activities like idle, wander, patrol, and follow-citizen.
- Support named schedule locations with saved world position and rotation.
- Travel to scheduled locations before activating the scheduled behavior.
- Use pre-generated roles for schedule travel and scheduled activity states.
- Avoid runtime role file rewrites that trigger noisy live role updates.
- Provide a full in-game editor for schedule locations, entries, and current runtime state.

## Implementation Plan

- [x] Define the schedule feature scope and runtime architecture.
- [x] Add schedule model classes and citizen-owned schedule config.
- [x] Persist schedule locations, entries, and settings to config.
- [x] Extend role generation with prebuilt schedule roles.
- [x] Add a schedule manager with runtime state tracking.
- [x] Evaluate schedules from Hytale world time on a scheduler.
- [x] Travel citizens to scheduled destinations using a dedicated travel role.
- [x] Activate scheduled idle, wander, and patrol states at arrival.
- [x] Add runtime role switching using prebuilt roles.
- [x] Add schedule UI entry point from the citizen editor.
- [x] Add schedule locations editor.
- [x] Add schedule entries editor.
- [x] Add current schedule state preview/debug in the UI.
- [x] Sanity check schedule interaction with spawn, despawn, and role cleanup paths.
- [x] Hook scheduled follow-citizen entries into schedule runtime follow targeting.

## Runtime Design Notes

- Base citizen behavior remains the authored fallback behavior.
- Schedule state is a runtime overlay.
- Runtime schedule transitions should switch between pre-generated role names instead of rewriting role JSON.
- Travel should always happen through a schedule travel role.
- Patrol schedule entries should start the plugin patrol manager after arrival.
- Follow schedule entries should compute and maintain their own runtime follow target positions after arrival.

## Current Status

- Started implementation.
- Confirmed Hytale world time API: `WorldTimeResource` from `com.hypixel.hytale.server.core.modules.time`.
- Confirmed runtime role swaps are available through `RoleChangeSystem.requestRoleChange(...)`.
- Added persistent schedule models, config load/save, runtime schedule evaluation, and pre-generated schedule role support.
- Added a full in-game schedule editor with runtime state display, fallback controls, location management, and entry editing.
- Added stale generated role cleanup so deleted/renamed schedule entries do not leave extra role files behind.
- Added session cleanup on citizen removal and reduced base-behavior reapplication churn.
- Scheduled follow-citizen entries are now supported directly by the schedule runtime with target-citizen selection and configurable follow distance.
- Schedule travel/follow movement now keeps `current-position` updated even when the citizen's base movement type is `IDLE`.
- Scheduled follow now requires a live spawned leader instead of following stale saved coordinates.
- Schedule arrival and default-location fallback now apply the authored schedule location rotation.
- Scheduled follow updates reuse the existing hidden move target instead of recreating it on each retarget.
