# Ceres

A client-side Fabric mod that automates crop farming — follow a recorded waypoint path, hold the attack key to break crops, manage Pest Repellent automatically, and monitor a live dashboard of farming metrics.  
Named after the Roman goddess of agriculture.

> **Requires [PlayerAPI](https://github.com/noname-mods/PlayerAPI) and [YetAnotherConfigLib](https://modrinth.com/mod/yacl) to run.**  
> [ModMenu](https://modrinth.com/mod/modmenu) is optional — it adds a settings button to the mod list.

---

## Features

### Waypoint Path Following
Ceres follows a sequence of recorded waypoints while holding the attack key to break crops. Two independent path slots (Primary and Secondary) let you define separate routes — for example an outward pass and a return pass. Each path has its own sprint toggle. Paths are recorded in-game by walking your route and pressing "Add Here" in the Path Editor.

### Smart Profile Auto-Load
When you start the bot, Ceres reads your held tool's display name and loads the matching crop profile automatically. A Wheat Hoe loads your Wheat profile; a Carrot Hoe loads Carrot — even reforged tools work because matching is substring-based. Profiles save the full path configuration (both slots + sprint settings) as named files you can switch between at any time.

### Passive Safety Checkers
Four independent monitors run continuously while farming:

| Checker | Behaviour |
|---|---|
| **Inventory** | Watches your hotbar for item changes. If nothing moves for 6 seconds the bot assumes it is stuck and stops. |
| **Tool** | Detects when your held item changes from what you started with and logs a warning. Does not stop the bot — other systems legitimately switch items. |
| **Yaw / Pitch** | Detects if an external force rotates the camera unexpectedly. Plays an alert sound and shows a title overlay but keeps farming — the bot is still running. |
| **Pest** | Reads the live pest count from the tab list. Plays an alert sound when it reaches your configured threshold. Does not stop the bot. |

The area check is always active regardless of checker toggles: if the tab list shows you have left the Garden, the bot stops immediately and returns full control to you.

### Pest Repellent Manager
Reads the "Repellent:" timer from the Hypixel tab list. When it reads "None", Ceres pauses the bot, switches to the repellent in your hotbar, uses it, waits for the tab list to update, and resumes farming — all without you touching anything. Works with all repellent tiers.

### Live HUD Overlay
A configurable overlay panel shows up to 14 live rows of farming data pulled directly from the Hypixel tab list:

- Bot state, active profile, and current area
- Player coordinates and camera look direction
- Live pest count vs. your alarm threshold
- Plots, Spray, Repellent timer, Bonus, Cooldown, and Bonus Pest Chance
- Path type and waypoint progress (while running)
- Blocks per second — a 30-second rolling average showing your farming rate
- Next waypoint coordinates

Every row can be individually shown or hidden in the config screen. A small log panel below the main HUD shows the last 5 bot messages.

### Blocks Per Second (BPS) Tracking
Ceres counts every crop block broken and keeps a rolling 30-second window. The live BPS figure in the HUD reflects your actual throughput, giving you a consistent number to compare across paths and crops.

### Fully Configurable
All settings are exposed through a YACL config screen with seven tabs. Open it from ModMenu, the `/ceres` command, or a keybind. No file editing required.

---

## Controls

All keybinds are rebindable in **Options → Controls → Ceres**.

| Action | Default Key |
|---|---|
| Start Primary Path | O |
| Start Secondary Path | U |
| Pause | P |
| Resume | J |
| Stop | K |
| Toggle HUD | ; |
| Open Path Editor | I |
| Open Config | *(unbound)* |

Type `/ceres` in chat to open the config screen directly.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 1.21.11
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Install [PlayerAPI](https://github.com/noname-mods/PlayerAPI)
4. Install [YetAnotherConfigLib](https://modrinth.com/mod/yacl)
5. Install [ModMenu](https://modrinth.com/mod/modmenu) *(optional)*
6. Drop `ceres-*.jar` into your `mods` folder

---

## Compatibility

| Minecraft | Fabric Loader | Java |
|---|---|---|
| 1.21.11 | ≥ 0.18.4 | 21 |

---

## For Developers

# Ceres — Design & Reference Documentation

**Version:** 1.1.1  
**Minecraft:** 1.21.11 (Fabric)  
**Depends on:** PlayerAPI 1.8.0+  

---

## AI Session Quick-Start

> **Read this first if you are a new Claude Code session working in this folder.**

### What this mod is

Ceres is a **client-side farming automation bot** for Hypixel Skyblock. It moves the player along a pre-recorded waypoint path, holds the attack key to break crops, auto-uses Pest Repellent, monitors safety conditions, and displays a live HUD. It is built entirely on top of PlayerAPI and never imports Minecraft internals directly.

### Key dependency

| Dependency | Version field | Location |
|------------|--------------|----------|
| PlayerAPI | `playerapi_version` in `gradle.properties` | `C:\Users\willi\Documents\FarmBot\PlayerAPI` |

**Build order:** If you changed PlayerAPI, run `./gradlew publishToMavenLocal` there first, then build Ceres.

### Source layout (what lives where)

```
src/main/java/com/ceres/
├── CeresMod.java                  — entry point, event wiring, keybind handling
├── core/
│   ├── BotState.java              — enum: STOPPED / PAUSED / RUNNING
│   ├── BotStateManager.java       — THE central singleton: all live state, tab list data, BPS tracking
│   ├── BotConfig.java             — all persistent settings, JSON config, versioned migrations
│   └── BotLogger.java             — ring-buffer logger + file output
├── path/
│   ├── Waypoint.java              — record(x, y, z, pathType)
│   ├── PathType.java              — enum: PRIMARY / SECONDARY / EVACUATION
│   ├── PathConfig.java            — the in-memory path model (waypoints + loop flag)
│   ├── PathManager.java           — walks the path each tick (movement + attack)
│   ├── ProfileManager.java        — loads/saves named .json profile files
│   └── CropToolMapper.java        — maps tool item IDs → profile names (auto-load)
├── tablist/
│   └── CeresTabListReader.java    — parses Hypixel tab list each tick → BotStateManager fields
├── checkers/
│   ├── CheckerController.java     — orchestrates all checkers, random reset interval
│   ├── InventoryChecker.java      — detects stalled inventory (crops not changing)
│   ├── ToolChecker.java           — verifies correct tool is held
│   ├── YawPitchChecker.java       — detects unexpected look direction changes
│   └── PestChecker.java           — stops bot when pest count reaches threshold
├── repellent/
│   └── PestRepellentManager.java  — detects repellent expiry, auto-uses from inventory
└── gui/
    ├── BotHudRenderer.java        — all HUD rendering (main panel + log panel)
    ├── CeresConfigScreen.java     — YACL config screen (5 tabs)
    └── PathEditorScreen.java      — path recording/editing GUI
```

### Config files (in `<game>/config/ceres/`)

| File | Contents |
|------|----------|
| `config.json` | All `BotConfig` settings (schema versioned, auto-migrated) |
| `paths.json` | The active waypoint data (Primary + Secondary + Evacuation paths) |
| `profiles/<name>.json` | One file per saved profile (same format as paths.json) |
| `ceres.log` | Appended log output |

### BotConfig schema version — current: 4

When adding a new config field that defaults to `true` (boolean), you **must** add a migration step — GSON defaults missing booleans to `false`. Pattern:
1. Bump `CURRENT_VERSION`
2. Add `migrateVn()` method
3. Call it in `migrate()`
4. Add the field to the manual-copy block in `load()`

If `INSTANCE` needs to iterate a new static list in the constructor, declare the list **before** `INSTANCE` in the class — static fields initialise in declaration order and a `NullPointerException` will result otherwise (this was a real bug).

### HUD line keys (all 14, used in BotConfig.hudLines map)

`"profile"`, `"area"`, `"xyz"`, `"look"`, `"pests"`, `"plots"`, `"spray"`, `"repellent"`, `"bonus"`, `"cooldown"`, `"pest_chance"`, `"path"`, `"bps"`, `"target"`

These keys are the source of truth. `BotConfig.ALL_HUD_LINES` is the authoritative list. The config screen, HUD renderer, and migration all derive from it.

### PlayerAPI version in use

Ceres currently uses PlayerAPI **1.8.0**. Key additions in recent versions used here:
- `TabListInfo.isLineStrikethrough(String substring)` — used to detect the crossed-out "Bonus Pest Chance" line
- `PlayerAPIEvents.BLOCK_BROKEN` — used for BPS (blocks per second) tracking
- `SoundActions.playByIdRepeated()` — used for all alarm sounds
- `DisplayActions` — not used in Ceres (used in Poseidon)

### Common things you'll need to touch

**Adding a new HUD row:**
1. Add the key string to `BotConfig.ALL_HUD_LINES`
2. Bump `BotConfig.CURRENT_VERSION` and add a migration
3. Add the toggle to `CeresConfigScreen` (use the `hudLineOption()` helper)
4. Add the render call in `BotHudRenderer` (gate it with `cfg.isHudLineVisible("key")`)
5. If the value comes from the tab list, add parsing in `CeresTabListReader` and a field + getter in `BotStateManager`

**Adding a new config setting:**
1. Add field + getter/setter to `BotConfig`
2. Add to the manual-copy block in `BotConfig.load()`
3. Add the YACL option to the appropriate tab in `CeresConfigScreen`
4. If it's a new boolean defaulting to true: add a migration

**Changing tab list parsing:**
- All tab list logic is in `CeresTabListReader.java`
- Values are written to `BotStateManager` via setters
- `TabListInfo` is the PlayerAPI class used (static methods, no instantiation)

### Key design rule

Ceres never imports `net.minecraft.*` except in `BotHudRenderer` (rendering only) and `CeresMod` (keybind registration + Fabric hooks). All player interaction, world queries, and scheduling go through PlayerAPI classes.

---

## Table of Contents

1. [Overview & Purpose](#1-overview--purpose)
2. [Architecture](#2-architecture)
3. [Installation](#3-installation)
4. [Config Files](#4-config-files)
5. [Bot Lifecycle — States](#5-bot-lifecycle--states)
6. [Path System](#6-path-system)
   - [Waypoints](#61-waypoints)
   - [PathType](#62-pathtype)
   - [PathConfig](#63-pathconfig)
   - [PathManager](#64-pathmanager)
   - [ProfileManager](#65-profilemanager)
   - [CropToolMapper](#66-croptoolmapper)
7. [Safety Checkers](#7-safety-checkers)
   - [CheckerController](#71-checkercontroller)
   - [InventoryChecker](#72-inventorychecker)
   - [ToolChecker](#73-toolchecker)
   - [YawPitchChecker](#74-yawpitchchecker)
   - [PestChecker](#75-pestchecker)
8. [Pest Repellent Manager](#8-pest-repellent-manager)
9. [Tab List Reader](#9-tab-list-reader)
10. [BotStateManager](#10-botstatemanager)
11. [BotConfig — All Settings](#11-botconfig--all-settings)
12. [HUD Overlay](#12-hud-overlay)
13. [GUI Screens](#13-gui-screens)
    - [Config Screen](#131-config-screen-ceres)
    - [Path Editor Screen](#132-path-editor-screen)
14. [Keybinds](#14-keybinds)
15. [In-Game Commands](#15-in-game-commands)
16. [BotLogger](#16-botlogger)
17. [Known Behaviours & Design Decisions](#17-known-behaviours--design-decisions)

---

## 1. Overview & Purpose

Ceres is a **client-side Fabric farming automation mod** for Minecraft 1.21.11. It moves the player along a pre-recorded path, holds the attack key to break crops, and manages a suite of supporting systems: repellent auto-use, safety checkers, a live HUD overlay, and a GUI for editing paths and configuration.

Ceres is built entirely on top of **PlayerAPI** — it never imports Minecraft internals directly. All player movement, inventory access, interaction, and world queries go through PlayerAPI's abstraction layer.

**What Ceres does:**
- Follows a user-defined waypoint path (two paths: Primary and Secondary) while holding the attack key, breaking crops in the player's reach.
- Automatically reapplies Pest Repellent when it expires.
- Monitors for unexpected conditions (leaving the farming area, inventory not changing, look direction changing, pest count threshold) and stops or alerts accordingly.
- Reads the server's tab list every tick to display live farming metrics in a HUD overlay.
- Saves and loads named profiles so the player can switch between crop configurations instantly.
- Auto-detects which profile to load based on the held tool at bot start.

**What Ceres does not do:**
- Navigate to the farming area (it assumes you are already there).
- Handle combat, fishing, mining, or any non-farming activity.
- Modify server-side data.

---

## 2. Architecture

```
CeresMod (entry point)
│
├── onTick() — called every tick via PlayerAPIEvents.TICK
│   ├── CeresTabListReader.update()          — pull tab list data into state
│   ├── PestRepellentManager.tick()          — check and apply repellent
│   ├── PathManager.tick()      [RUNNING]    — advance waypoint following
│   ├── CheckerController.tick() [RUNNING]   — run all safety checkers
│   ├── MovementActions.pressKey("attack")   — heartbeat, every 20 ticks
│   └── handleKeybinds()                     — process key presses
│
├── BotStateManager (singleton)              — single source of truth for all runtime state
│
├── BotConfig (singleton)                    — persistent settings (config.json)
│
├── PathConfig (singleton)                   — persistent waypoints (paths.json)
│
├── ProfileManager                           — named profiles (profiles/<name>.json)
│
├── BotHudRenderer                           — HUD overlay drawn via HudRenderCallback
│
├── CeresConfigScreen                        — YACL config GUI (/ceres)
│
├── PathEditorScreen                         — waypoint editor GUI (I key)
│
└── BotLogger                                — in-memory ring buffer + file log
```

### Tick Flow

```
PlayerAPIEvents.TICK (main game thread, every tick)
    │
    ├─ CeresTabListReader.update()
    │      Reads all tab-list lines → pushes values into BotStateManager
    │
    ├─ PestRepellentManager.tick()
    │      If repellent timer = "None" and cooldown elapsed → pause bot, apply, resume
    │
    ├─ [if RUNNING] PathManager.tick()
    │      Advance toward next waypoint; at each cycle end: loop or stop+alert
    │
    ├─ [if RUNNING] CheckerController.tick()
    │      Every 60–100 ticks: reset checker baselines
    │      Between resets: run all four checkers
    │
    ├─ [if RUNNING] pressKey("attack") every 20 ticks
    │      Heartbeat to prevent the attack key from being dropped
    │
    ├─ [if PAUSED] releaseAll()
    │      Safety: ensure no movement keys are held while paused
    │
    └─ handleKeybinds()
           Process any keybind presses from this tick
```

---

## 3. Installation

1. Install Fabric Loader 0.18.4 for Minecraft 1.21.11.
2. Install Fabric API 0.141.3+1.21.11.
3. Place **PlayerAPI** `playerapi-1.8.0.jar` in your mods folder.
4. Place **Ceres** `ceres-1.1.0.jar` in your mods folder.
5. (Optional) Install ModMenu to get the "Config" button in the mods list.

Ceres generates its config files in `.minecraft/config/ceres/` on first launch.

---

## 4. Config Files

All Ceres data lives in `.minecraft/config/ceres/`:

| File / Directory | Contents |
|-----------------|----------|
| `config.json` | All bot settings (schema versioned, auto-migrated). |
| `paths.json` | The currently loaded waypoints for Primary and Secondary paths. |
| `alias.json` | Command aliases (name → command string map). |
| `ceres.log` | Append-only log file. All INFO/WARN/ERROR messages land here. |
| `profiles/` | Directory of named profile files, one `.json` per profile. |
| `profiles/<name>.json` | A profile: waypoints + sprint settings for both path types. |

**Profiles are completely separate from `paths.json`.** Loading a profile in the Path Editor copies its data into the live edit buffer. Clicking Done saves that buffer to `paths.json`. If you close the editor without clicking Done, changes are discarded and `paths.json` is unchanged.

---

## 5. Bot Lifecycle — States

The bot is always in one of three states, tracked by `BotStateManager.getCurrentState()`:

```
STOPPED ──startBot()──► RUNNING ──pauseBot()──► PAUSED
   ▲                        │                      │
   └──────stopBot()──────────┘◄──resumeBot()────────┘
```

### STOPPED

- Default state on mod load.
- No movement keys are held.
- No checkers run.
- Repellent manager still runs (it also monitors PAUSED state).
- HUD shows state label in **red**.

### RUNNING

- PathManager advances waypoints every tick.
- CheckerController runs all four safety checks.
- Attack key is held (with a 20-tick heartbeat re-press in case it drops).
- HUD shows state label in **green**.

### PAUSED

- All movement keys are released every tick (enforced in the tick loop).
- PathManager does not run.
- CheckerController does not run.
- Repellent manager continues — it can trigger a pause→apply→resume sequence even when already paused.
- HUD shows state label in **orange/yellow**.

### Starting the bot

`startBot(PathType)` performs these actions in order:
1. Validates the player is in the "Garden" area (unless bypass is enabled or `checksBypassed` is set).
2. Sets state to RUNNING.
3. Records the held item as the "initial tool" for the ToolChecker.
4. Clears the BPS sliding window and records the run start tick.
5. Calls `MovementActions.setActive(true)`.

### Stopping the bot

`stopBot()` performs these actions:
1. Sets state to STOPPED.
2. Releases all movement keys.
3. Calls `MovementActions.setActive(false)`.
4. Calls `Scheduler.cancelAll()` — clears all pending scheduled tasks.
5. Clears the BPS sliding window.

### Auto-load on start

When the bot is started via the **O** (Primary) or **U** (Secondary) keybind, `autoLoadAndStart()` runs first:
1. Calls `CropToolMapper.resolveProfile()` to identify the held tool.
2. If a matching profile is found and auto-load is enabled for that crop in `BotConfig`, loads the profile into `PathConfig` before calling `startBot()`.
3. Sets the active profile name shown in the HUD.
4. If no profile is found or auto-load is disabled, starts with whatever paths are currently in `PathConfig`.

---

## 6. Path System

### 6.1 Waypoints

A `Waypoint` is a single point the bot must reach on its path.

```java
class Waypoint {
    double x;
    double y;              // Y is recorded but not used for movement (2D navigation only)
    double z;
    List<String> forcedKeys;  // which movement keys to hold between this and the next waypoint
}
```

**`forcedKeys`** determines exactly how the bot moves toward this waypoint:
- If the list is empty, the bot holds no directional keys and waits for the waypoint to come within 0.5 blocks (useful for stationary waypoints or teleport-based movement).
- If keys are present, those keys are held every tick until the waypoint is reached.
- Sprint is added on top of whatever keys are set, controlled by the per-path sprint setting — it is not a forced key.

**Valid key names for `forcedKeys`:** `"forward"`, `"back"`, `"left"`, `"right"`, `"sneak"`, `"jump"`. (`"sprint"` is handled separately by the sprint toggle.)

**Waypoint reach threshold:** 0.5 blocks (2D distance on X/Z plane). Y coordinate is ignored for reach detection.

---

### 6.2 PathType

There are exactly two path types:

| Enum value | Default keybind | Typical use |
|------------|----------------|-------------|
| `PRIMARY`  | O | Main farming route |
| `SECONDARY`| U | Secondary or return route |

Each path type has its own independent waypoint list and sprint setting in `PathConfig`.

---

### 6.3 PathConfig

Stores the **currently active** waypoint lists. Persisted to `config/ceres/paths.json`.

```json
{
  "version": 2,
  "sprint": {
    "PRIMARY": true,
    "SECONDARY": false
  },
  "paths": {
    "PRIMARY": [
      { "x": 100.5, "y": 64.0, "z": -200.5, "forcedKeys": ["forward"] },
      { "x": 110.5, "y": 64.0, "z": -200.5, "forcedKeys": ["forward"] }
    ],
    "SECONDARY": []
  }
}
```

PathConfig is **the live file the bot actually runs from**. Profiles are separate — loading a profile copies data into PathConfig only when you press Done in the Path Editor.

---

### 6.4 PathManager

Drives movement every tick while the bot is RUNNING.

**Waypoint following loop:**

```
tick()
  → if not yet following: startPathFollowing()
  → if messUpTimer > 0: release all keys, count down (anti-detection pause)
  → otherwise: followPathStep()
      → compute 2D distance to current waypoint
      → if within 0.5 blocks: advance index, log "reached waypoint N"
      → else: apply forcedKeys (+ sprint if enabled)
      → attemptMessUp(): ~1/180,000 chance each tick of a 10–30 tick pause
```

**Cycle completion (index reaches end of list):**
- **One-Cycle Mode:** stops the bot and plays the cycle-complete alarm sound.
- **Normal mode:** resets index to 0, sends the cycle-start command (if configured), starts a new loop.

**Cycle-start command:** A command (default: `"warp garden"`) sent at the start of every cycle (including the first) and after every loop reset. Sent with a 5-tick delay to allow state to settle. The leading `/` is stripped if accidentally included.

**Sprint:** Applied on top of `forcedKeys` when `PathConfig.isSprintEnabled(currentPathType)` is true. Sprint is per-path and toggled independently for Primary and Secondary.

**Sneak on path start:** If `BotConfig.isSneakOnPathStart()` is true, the bot briefly presses sneak for 5 ticks (250 ms) at the start of each path to snap orientation before moving.

---

### 6.5 ProfileManager

Manages named profiles saved in `config/ceres/profiles/`.

A **profile** contains:
- Waypoint lists for both PRIMARY and SECONDARY.
- Sprint settings for both path types.

Profiles are identified by filename (without the `.json` extension). File names are sanitised — any characters in `\ / : * ? " < > |` are replaced with underscores.

**Profile operations:**

| Operation | Result |
|-----------|--------|
| Save profile | Writes current editor state to `profiles/<name>.json`. Does not touch `paths.json`. |
| Load profile | Reads `profiles/<name>.json`, returns a `ProfileData` record. Caller applies it to PathConfig. |
| Delete profile | Deletes `profiles/<name>.json`. |
| List profiles | Returns alphabetically sorted list of all `.json` filenames in the profiles directory. |
| Open profiles folder | Opens the OS file browser to the profiles directory. |

**Profile file format:**

```json
{
  "version": 2,
  "sprint": { "PRIMARY": true, "SECONDARY": false },
  "paths": {
    "PRIMARY": [ { "x": 100.0, "y": 64.0, "z": -200.0, "forcedKeys": ["forward"] } ],
    "SECONDARY": []
  }
}
```

---

### 6.6 CropToolMapper

Maps the player's held tool display name to a profile name. Used only at bot start by `autoLoadAndStart()`.

**Matching is substring-based and case-insensitive.** The key fragment (e.g. `"wheat hoe"`) only needs to appear somewhere in the full display name, so reforged or tiered items like `"Blessed Euclid's Wheat Hoe Mk. II"` are matched correctly.

**Full tool → profile mapping:**

| Tool name substring | Resolves to profile |
|--------------------|---------------------|
| `"cactus knife"` | `Cactus` |
| `"carrot hoe"` | `Carrot` |
| `"cocoa chopper"` | `Cocoa Beans` |
| `"fungi cutter"` | `Mushroom` |
| `"melon dicer"` | `Melon` |
| `"nether wart hoe"` | `Nether Wart` |
| `"potato hoe"` | `Potato` |
| `"pumpkin dicer"` | `Pumpkin` |
| `"sugar cane hoe"` | `Sugarcane` |
| `"wheat hoe"` | `Wheat` |
| `"wild rose hoe"` | `Wild Rose` |
| `"eclipse hoe"` | *see below* |

**Eclipse Hoe special case:** The Eclipse Hoe is used for both Sunflower and Moonflower (they are the same plant but flower at different times of day). Resolution priority:
1. Both `Sunflower` and `Moonflower` profiles exist on disk → resolves to `"Sunflower"`.
2. Only one exists → resolves to that one.
3. Neither exists → returns `null` (no auto-load; bot starts with current paths).

`ALL_CROPS` — the complete list of profile names CropToolMapper can resolve to, in alphabetical order. Used by BotConfig to build the per-crop toggle storage and by the config screen to build the UI:

```
Cactus, Carrot, Cocoa Beans, Melon, Moonflower, Mushroom,
Nether Wart, Potato, Pumpkin, Sugarcane, Sunflower, Wheat, Wild Rose
```

---

## 7. Safety Checkers

All checkers are coordinated by `CheckerController`. They only run while the bot is `RUNNING`. Each checker can individually be enabled/disabled in `BotConfig`.

### 7.1 CheckerController

Runs every tick while `RUNNING`. Operates in two phases that alternate on a **60–100 tick (3–5 second) random interval:**

**Reset phase:** Snapshots the current player state as the new baseline for all checkers. Randomised interval prevents detectable patterns.

**Check phase:** Runs all four checkers against the current state. Each checker returns `true` (OK) or `false` (problem).

**Area check (always on, regardless of checker toggles):** If the tab-list area is known (non-empty) and is not `"Garden"`, the bot stops and plays the stop alert sound immediately. This is the only hard-stop that cannot be bypassed through checker toggles.

**Bypass mode:** If `BotStateManager.isChecksBypassed()` is true, the entire checker tick is skipped. This is useful during development and path-recording. Not exposed in the standard config screen — toggle programmatically or via debug commands.

---

### 7.2 InventoryChecker

**Purpose:** Detect when the bot has become stuck — if no items are entering or leaving the hotbar for 6 seconds (120 ticks), farming has likely stopped.

**How it works:**
- On reset: snapshots the current hotbar (`ItemSnapshot` list).
- On check: compares the current hotbar to the last snapshot.
  - If different: updates the snapshot and records the tick.
  - If unchanged: returns false if more than 120 ticks have passed since the last change.

**On failure:** Stops the bot and plays the stop alert sound.

**Important:** The 120-tick window is checked against the *last change*, not the *last reset*. If inventory changes frequently but then freezes, the checker correctly triggers after 6 seconds of no change — even if a reset happened recently.

---

### 7.3 ToolChecker

**Purpose:** Detect if the player's held item changed from what was held when the bot started.

**How it works:**
- On reset: records `BotStateManager.getInitialTool()` (the display name of the held item when `startBot()` was called).
- On check: compares the current held item's display name to the initial tool (case-insensitive).

**On failure:** Logs a warning and **continues**. Does not stop the bot. This is intentional — other systems (including the Repellent Manager) legitimately switch items, and the bot should continue farming regardless.

---

### 7.4 YawPitchChecker

**Purpose:** Detect if an external force (another mod, a player, or server-side teleportation) has changed the player's look direction unexpectedly.

**How it works:**
- On reset: records the current yaw and pitch as the baseline.
- On check: computes the angular difference between current and baseline.
  - Yaw difference is wrap-corrected (handles the 0°/360° boundary).
  - Alerts if yaw changed by more than **1.0°** or pitch by more than **1.0°**.
  - Always updates the baseline to the current value so it tracks gradual drift without repeating alerts.

**On failure:** Shows a screen title (`§c⚠ LOOK CHANGED` / `§eBot is still running`) and plays the warn alert sound. **Does not stop the bot** — farming continues uninterrupted. The purpose is to alert the player so they can manually intervene if needed.

---

### 7.5 PestChecker

**Purpose:** Alert when the live pest count from the tab list reaches the configured minimum threshold.

**How it works:**
- Reads `BotStateManager.getPestCount()` and compares to `BotConfig.getMinPestCount()`.
- If `pestCount >= minPestCount`, returns false.
- Rate-limited: will not alert more than once per 600 ticks (30 seconds) to avoid alert spam.

**On failure:** Plays the warn alert sound. **Does not stop the bot.** The alert is informational — it is up to the player to decide whether to stop and deal with the pests.

---

## 8. Pest Repellent Manager

Automatically applies Pest Repellent when it expires. Runs every tick regardless of bot state (RUNNING, PAUSED, or STOPPED — but not when STOPPED).

**Detection trigger:** Watches `BotStateManager.getPestRepellentTimerText()`. When this value equals `"None"` (case-insensitive, trimmed), repellent has expired.

**Cooldown:** 1200 ticks (60 seconds) minimum between applications. This prevents rapid re-application if the tab list is slow to update after the repellent is used.

**Application sequence:**
1. If the bot is RUNNING → pause it.
2. Search the hotbar for an item whose display name **starts with** `"Pest Repellent"` (matches all tiers: I, II, MAX, etc.). If not found, log a warning, skip, and resume.
3. Switch to the repellent slot.
4. Wait 5 ticks (250 ms) for the slot switch to register.
5. Use the item (right-click).
6. Wait 1200 ticks (60 seconds) for the internal cooldown.
7. If the bot was RUNNING before the pause → resume it.

**While applying:** `isApplying` flag blocks any re-entrant application during the 1200-tick wait window.

**Enable/disable:** Controlled by `BotConfig.isRepellentReapplyEnabled()`. When disabled, the entire manager tick is a no-op.

---

## 9. Tab List Reader

`CeresTabListReader.update()` is called every tick. It reads specific lines from the tab list using PlayerAPI's `TabListInfo` and pushes values into `BotStateManager`. All values are stored as strings (or integers for pest count) with the prefix stripped.

**Parsed lines:**

| Tab list prefix | BotStateManager field | Notes |
|-----------------|----------------------|-------|
| `"Area:"` | `currentArea` | Not cleared if line disappears — retains last known area. |
| `"Alive:"` | `pestCount` (int) | Parses the first integer from the value. |
| `"Plots:"` | `plotsText` | Cleared to `""` if line is absent. |
| `"Spray:"` | `sprayText` | Cleared to `""` if line is absent. |
| `"Repellent:"` | `pestRepellentTimerText` | Cleared to `""` if line is absent. |
| `"Bonus:"` | `bonusText` | Cleared to `""` if line is absent. |
| `"Cooldown:"` | `sprayCooldownText` | Cleared to `""` if line is absent. |
| `"Bonus Pest Chance:"` | `bonusPestChanceText` | If line has strikethrough styling → stores `"DISABLED"`. Otherwise stores the value (e.g. `"217"`). Cleared to `""` if absent. |

**Strikethrough detection for Bonus Pest Chance:** Uses `TabListInfo.isLineStrikethrough()` which walks the Minecraft `Text` object's style tree via `text.visit()`. This is the only reliable method — `getString()` strips all formatting including strikethrough.

**Area retention:** Unlike the other fields, `currentArea` is not cleared when the `"Area:"` line disappears. This prevents false-positive area-check failures during brief tab-list gaps.

---

## 10. BotStateManager

`BotStateManager` is the single source of truth for all runtime state. It is a singleton accessed via `BotStateManager.getInstance()`.

### Bot control methods

| Method | Description |
|--------|-------------|
| `startBot(PathType)` | Start the bot on the given path type. Validates area unless bypassed. Clears BPS data and records run start tick. |
| `pauseBot()` | Pause if currently RUNNING. Releases all keys. Clears BPS data. |
| `resumeBot()` | Resume if currently PAUSED. Re-presses attack key. Resets BPS window. |
| `stopBot()` | Stop unconditionally. Releases all keys, cancels all scheduled tasks, clears BPS data. |

### State fields

| Field | Type | Description |
|-------|------|-------------|
| `currentState` | `BotState` | RUNNING / PAUSED / STOPPED. |
| `currentPathType` | `PathType` | Which path type is active (PRIMARY or SECONDARY). |
| `isFollowingPath` | `boolean` | True once PathManager has started following (set false on stop). |
| `currentPathIndex` | `int` | Index of the next waypoint to reach. |
| `currentPath` | `List<Waypoint>` | Copy of the active waypoint list (or null when stopped). |
| `currentArea` | `String` | Last known area from the tab list. |
| `pestCount` | `int` | Live pest count from `"Alive:"` tab line. |
| `plotsText` | `String` | Value of `"Plots:"` tab line, or `""`. |
| `sprayText` | `String` | Value of `"Spray:"` tab line, or `""`. |
| `pestRepellentTimerText` | `String` | Value of `"Repellent:"` tab line, or `""`. |
| `bonusText` | `String` | Value of `"Bonus:"` tab line, or `""`. |
| `sprayCooldownText` | `String` | Value of `"Cooldown:"` tab line, or `""`. |
| `bonusPestChanceText` | `String` | Value of `"Bonus Pest Chance:"` line — a number, `"DISABLED"`, or `""`. |
| `guiVisible` | `boolean` | Whether the HUD overlay is shown. |
| `checksBypassed` | `boolean` | Whether all checkers are skipped. |
| `checkFailed` | `boolean` | Set when a hard-stop checker triggers. |
| `initialTool` | `String` | Display name of the held item when `startBot()` was called. |
| `activeProfileName` | `String` | Name of the last named profile loaded (`"Custom"` if paths were edited manually or no profile was loaded). |

### Blocks-per-second (BPS) tracking

The BPS system uses a **30-second sliding window** of block-break timestamps:

- `recordBlockBroken(long tick)` — called from `CeresMod` whenever `PlayerAPIEvents.BLOCK_BROKEN` fires while in RUNNING state. Adds the tick to a deque and prunes entries older than 600 ticks.
- `getBlocksPerSecond()` — returns the rolling BPS. The divisor is `min(elapsed seconds since run start, 30)` rather than always 30, so early readings are accurate instead of artificially low.
- The window is **cleared** on `startBot()`, `pauseBot()`, and `stopBot()`, and reset (fresh window) on `resumeBot()`. This ensures each continuous run session is measured independently.

---

## 11. BotConfig — All Settings

`BotConfig` persists to `config/ceres/config.json`. Schema version 4. All migrations run automatically — old config files are upgraded transparently.

### Schema history

| Version | Change |
|---------|--------|
| 0 | Initial release (no version field). |
| 1 | `repeatCount` (int, plays) → `durationSeconds` (int, seconds) in alarm sounds. |
| 2 | Added `autoLoadEnabled` and `autoLoadCrops` (all default `true`). |
| 3 | Added `hudLines` per-row visibility map (all default `true`). |
| 4 | Added `updateCheckEnabled` (defaults `true`). |

### Bot Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `minPestCount` | `int` | `4` | Pest count threshold for the PestChecker alert. Alert fires when `pestCount >= minPestCount`. |
| `logLevel` | `int` | `2` (WARN) | Minimum severity to log. `1`=ERROR, `2`=WARN, `3`=INFO, `4`=DEBUG. |
| `sneakOnPathStart` | `boolean` | `true` | If true, sneak is pressed for 250 ms at the start of each path to snap orientation. |
| `repellentReapplyEnabled` | `boolean` | `true` | If false, the repellent manager is entirely disabled. |
| `oneCycleMode` | `boolean` | `false` | If true, the bot stops and plays the cycle-complete alarm after one full pass instead of looping. |
| `cycleRestartCommand` | `String` | `"warp garden"` | Command sent (without `/`) at the start of every cycle. Leave blank to disable. |

### Checker Toggles

| Setting | Default | Description |
|---------|---------|-------------|
| `inventoryCheckerEnabled` | `true` | Enable the inventory change detector (stuck detector). |
| `toolCheckerEnabled` | `true` | Enable the held-item change monitor. |
| `yawPitchCheckerEnabled` | `true` | Enable the look-direction drift monitor. |
| `pestCheckerEnabled` | `true` | Enable the pest count threshold alert. |

### Alarm Sounds

Three independent alarm sound configurations, each with five sub-fields:

| Alarm | Trigger |
|-------|---------|
| `cycleCompleteSound` | One-Cycle Mode finishes. |
| `stopAlertSound` | InventoryChecker hard-stops the bot, or area check fails. |
| `warnAlertSound` | YawPitchChecker detects look change, or PestChecker threshold reached. |

Each alarm has:

| Sub-field | Type | Description |
|-----------|------|-------------|
| `soundId` | `String` | Minecraft sound ID, e.g. `"entity.player.levelup"`. Namespace `minecraft:` is optional. Browse at https://misode.github.io/sounds/ |
| `volume` | `double` | 0.1–2.0 (1.0 = normal). |
| `pitch` | `double` | 0.5–2.0 (1.0 = normal pitch/speed). |
| `durationSeconds` | `int` | Total alarm duration in seconds (sound repeats for this long). Set 0 to play once. |
| `intervalTicks` | `int` | Ticks between each repeat play (20 ticks = 1 second). |

**Defaults:**

| Alarm | soundId | volume | pitch | durationSeconds | intervalTicks |
|-------|---------|--------|-------|-----------------|---------------|
| Cycle Complete | `entity.player.levelup` | 1.0 | 1.0 | 10 | 20 |
| Stop Alert | `entity.player.levelup` | 1.0 | 1.0 | 10 | 20 |
| Warn Alert | `entity.experience_orb.pickup` | 1.0 | 1.5 | 5 | 15 |

### Auto-Load

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `autoLoadEnabled` | `boolean` | `true` | Master toggle. When false, no profile is auto-loaded regardless of per-crop settings. |
| `autoLoadCrops` | `Map<String, Boolean>` | all `true` | Per-crop enable. Keys are the 13 profile names from `CropToolMapper.ALL_CROPS`. |

### HUD Lines

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `hudLines` | `Map<String, Boolean>` | all `true` | Per-row visibility. See [HUD Overlay](#12-hud-overlay) for key names. |

### Updates

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `updateCheckEnabled` | `boolean` | `true` | On every world join, Ceres contacts GitHub to check for a newer release. If one is found, a single chat message is shown with the version and a link. Nothing is downloaded automatically. Disable if you are offline, on a restricted network, or do not want the notification. |

### Developer Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `bypassAreaCheck` | `boolean` | `false` | If true, `startBot()` skips the area validation. Useful for testing paths outside the farming area. |
| `microLookEnabled` | `boolean` | `false` | Enables `HumanProfile.enableMicroLook` for subtle idle camera drift. |
| `debugMode` | `boolean` | `false` | Prints extra diagnostic messages (useful for diagnosing `/ceres` command interception issues). |

---

## 12. HUD Overlay

The HUD is a small overlay panel drawn in the top-left corner when the HUD is visible (toggled with the `;` key by default). Each row can be individually hidden in the **HUD Lines** config tab.

### Main Panel

The panel has a coloured left accent bar showing the current bot state:
- **Green** — RUNNING
- **Orange/Yellow** — PAUSED
- **Red** — STOPPED

The header shows "Ceres" on the left and the state name on the right, in the state colour.

**Rows, in order:**

| Row label | Config key | Always shown? | Description |
|-----------|-----------|--------------|-------------|
| **Profile** | `"profile"` | Yes | Name of the currently loaded profile. Shows `"Custom"` if paths were edited manually or no named profile was loaded this session. |
| **Area** | `"area"` | Yes | Current area from the tab list. Shows `"Unknown"` if no area has been received yet. |
| **XYZ** | `"xyz"` | Yes | Player coordinates formatted as `X  /  Y  /  Z` (1 decimal place). |
| **Look** | `"look"` | Yes | Yaw and pitch. Yaw is wrapped to [−180, 180]. Format: `Y 45.0  P -10.0`. |
| **Pests** | `"pests"` | Yes | Live pest count vs. the alarm threshold. Colour: **red** if `count >= threshold`, **green** if below. |
| **Plots** | `"plots"` | Only when tab line present | Value from the `"Plots:"` tab line. Neutral colour. |
| **Spray** | `"spray"` | Only when tab line present | Value from the `"Spray:"` tab line. **Red** if `"None"`, **green** otherwise. Indicates whether the current plot has an active spray. |
| **Repellent** | `"repellent"` | Only when tab line present | Value from the `"Repellent:"` tab line. **Red** if `"None"` (expired), **green** if a timer is showing. |
| **Bonus** | `"bonus"` | Only when tab line present | Value from the `"Bonus:"` tab line. **Red** if `"INACTIVE"`, **green** otherwise. |
| **Cooldown** | `"cooldown"` | Only when tab line present | Pest spawn cooldown timer. Colour: **green** if `"READY"`, **yellow** if ≤ 10 seconds remaining, **red** if more than 10 seconds. Useful for timing gear swaps around when a new pest can spawn. |
| **Pest Ch.** | `"pest_chance"` | Only when tab line present | Bonus pest chance value. **Dark green** when active (e.g. `"217"`), **red** when `"DISABLED"` (line is crossed out in tab list). |
| **Path** | `"path"` | Only when RUNNING | Path type name and waypoint progress: `PRIMARY  12 / 48`. |
| **Bps** | `"bps"` | Only when RUNNING | Blocks broken per second (rolling 30-second window, dynamic early divisor). **Green** if > 0, **grey** if 0. Format: `3.4/s`. |
| **Target** | `"target"` | Only when RUNNING and has next waypoint | Coordinates of the next waypoint. Format: `100 / 64 / -200`. |

The panel height is calculated dynamically based on how many rows are visible. The panel is always wide enough to show all content (210px wide, label column at offset 7, value column at offset 60 from the panel left edge).

### Log Panel

Below the main panel, a secondary log panel shows the **last 5 log lines** from `BotLogger`. Timestamps and log level prefixes are stripped for brevity. Lines longer than 52 characters are truncated with `…`.

The log panel only appears when there are log entries. It uses a different background colour (`#90000000`) and a grey accent bar to visually distinguish it from the main panel.

### Keybind Hint Panel

In the **bottom-right corner**, a small panel shows the default keybind hints:
```
O=Primary  U=Secondary
P=Pause  J=Resume  K=Stop
I=Paths  ;=Toggle HUD
```

This panel is always visible when the HUD is visible, regardless of which rows are hidden.

---

## 13. GUI Screens

### 13.1 Config Screen (`/ceres`)

Opened by:
- The `/ceres` command in chat.
- The "Open Config" keybind (unbound by default).
- The ModMenu "Config" button (if ModMenu is installed).

The config screen is built with **YACL (Yet Another Config Library)** and has seven tabs:

#### Bot Settings tab
- Sneak on Path Start
- Pest Repellent Reapply
- One Cycle Mode
- Cycle Start Command

#### Auto-Load tab
- Description label (explains substring matching for reforged tools)
- Enable Auto-Load (master toggle)
- Divider label
- 13 per-crop toggles (one per entry in `CropToolMapper.ALL_CROPS`)

#### HUD Lines tab
- Description label
- 14 per-row toggles (see [HUD Overlay](#12-hud-overlay) for the full list and descriptions)

#### Checkers tab
- Min Pest Count for Alarm (slider 1–20)
- Inventory Checker toggle
- Tool Checker toggle
- Yaw / Pitch Checker toggle
- Pest Checker toggle

#### Sounds tab
Three alarm sound groups (Cycle Complete, Stop Alert, Warn Alert), each with:
- Sound ID (text field)
- Volume (slider 0.1–2.0, step 0.05)
- Pitch (slider 0.5–2.0, step 0.05)
- Duration (slider 0–30 seconds, step 1)
- Interval (slider 5–60 ticks, step 5)

#### Updates tab
- Update Check toggle (enable/disable the GitHub release notification on world join)

#### Keybinds tab
- Read-only label listing all default bindings. Actual rebinding is done in Options → Controls → Key Binds → "Ceres" category.

#### Developer tab (shown in red)
- Debug Mode
- Bypass Area Check
- Micro-Look Jitter
- Log Level (cycling: DEBUG / INFO / WARN / ERROR)

**Screen opening note:** The `/ceres` command sets a flag (`CeresMod.openConfigNextTick = true`) and the screen is opened on the next tick. This avoids a race condition where the chat screen closes after the command fires but before the new screen opens. If the server overrides the client command tree (preventing `ClientCommandManager` from intercepting `/ceres`), a `ALLOW_COMMAND` fallback catches it and sets the same flag.

---

### 13.2 Path Editor Screen

Opened by the **I** keybind (default).

The Path Editor is a custom screen (not YACL) that lets you record, edit, and manage waypoints for both path types.

#### Layout
- Top: Profile controls (save/load/delete, open profiles folder).
- Left: Profile list (all saved profiles, clickable to load).
- Middle: Waypoint list for the selected path type (scrollable).
- Right: Per-waypoint key controls.
- Bottom: Path type selector, Sprint toggle, Add Here button, Done button.

#### Profile management
- **Save As** — saves the current editor state to a new named profile.
- **Load** — loads the selected profile from disk into the editor. The HUD profile name will be set to this profile's name when Done is pressed (only if no further edits are made after loading; otherwise shows "Custom").
- **Delete** — deletes the selected profile from disk.
- **Open Folder** — opens the OS file browser to `config/ceres/profiles/`.

#### Waypoint recording
- **Add Here** — adds a waypoint at the player's current X/Y/Z position, appended to the end of the list.
- Each waypoint row shows its coordinates and allows toggling forced keys (checkboxes for `forward`, `back`, `left`, `right`, `sneak`, `jump`).
- **↑ / ↓** arrows reorder waypoints.
- **✕** removes a waypoint.
- **Clear** removes all waypoints from the current path type.

#### Sprint toggle
Per-path sprint can be toggled in the editor. The toggle state is saved to `paths.json` on Done and included in profile saves.

#### Profile name tracking
The editor tracks whether the current state is a clean load of a named profile or has been modified:
- Load a profile, press Done without changing anything → HUD shows the profile name.
- Load a profile, then add or remove a waypoint → HUD shows `"Custom"`.
- Edit without loading → HUD shows `"Custom"`.

#### Saving to paths.json
Clicking **Done** saves the current editor state to `paths.json`. Sprint settings are applied first (so they are included in the save). **Closing without Done discards all unsaved changes.**

---

## 14. Keybinds

All bindings are in the Minecraft Controls menu under the **"Ceres"** category. All can be rebound or unbound.

| Action | Default Key | Description |
|--------|-------------|-------------|
| Toggle HUD | `;` (semicolon) | Show/hide the HUD overlay. |
| Open Path Editor | `I` | Open the waypoint editor screen. |
| Open Config | (none) | Open the config screen. |
| Start Primary | `O` | Auto-load profile from held tool, then start on PRIMARY path. Requires HUD to be visible first. |
| Start Secondary | `U` | Auto-load profile from held tool, then start on SECONDARY path. Requires HUD to be visible first. |
| Pause Bot | `P` | Pause the bot (releases all movement keys). |
| Resume Bot | `J` | Resume from pause (re-presses attack key). |
| Stop Bot | `K` | Stop the bot entirely. |

**Start requires HUD visible:** If you press Start Primary or Start Secondary while the HUD is hidden, the bot will not start and a warning is logged. This prevents accidental starts.

---

## 15. In-Game Commands

### `/ceres`

Opens the config screen. The command sets a flag and the screen is opened on the next tick to avoid a chat-close race condition.

If the server overrides the client command tree (preventing `/ceres` from being intercepted by the client), a fallback `ALLOW_COMMAND` listener catches the command instead, sets the same flag, and returns `false` to suppress the message being sent to the server. Enable Debug Mode in the config to see which path fired.

---

## 16. BotLogger

`BotLogger` is a singleton that writes log entries to two places simultaneously:
- **In-memory ring buffer** (last 100 lines) — displayed in the HUD log panel.
- **File** at `config/ceres/ceres.log` — append-only, survives game restarts.

### Log levels

| Constant | Value | When to use |
|----------|-------|-------------|
| `LEVEL_ERROR` | 1 | Unrecoverable errors (file load failures, unexpected null states). |
| `LEVEL_WARN` | 2 | Conditions requiring attention (checker triggers, area mismatch, no repellent found). |
| `LEVEL_INFO` | 3 | Normal lifecycle events (bot started/stopped, profile loaded, path started). |
| `LEVEL_DEBUG` | 4 | Verbose internals (individual waypoint reached, anti-detection pause, cycle-start command sent). |

**Default log level:** WARN. Only ERROR and WARN messages are written to the system console. All levels (that pass the filter) go to the file and HUD buffer.

**Log format:**
```
[27/04/2026 - 14:32:05] [INFO] Started on PRIMARY
```

**HUD display:** The log panel strips the timestamp and log level prefix, showing only the message. Lines over 52 characters are truncated with `…`.

---

## 17. Known Behaviours & Design Decisions

### Waypoint Y coordinate is ignored

PathManager uses only X and Z for both movement key selection and reach detection. The Y coordinate is recorded in waypoints (and displayed in the HUD Target row) but plays no role in navigation. This is intentional — Ceres is designed for flat or near-flat farming plots where vertical navigation is not required.

### Forced keys must be pre-set; no auto-direction

Unlike bots that compute which keys to press from the angle to the target, Ceres uses explicitly set `forcedKeys`. This means:
- **If a waypoint has no forced keys and the player is not within 0.5 blocks, the bot holds position indefinitely.** This is by design — it allows stationary waypoints (e.g. for waiting at a specific point before a teleport).
- The path author is responsible for setting the correct keys on each waypoint. The Path Editor provides checkboxes for all six direction keys.

### Sprint is per-path, not per-waypoint

Sprint is enabled or disabled for the entire PRIMARY or SECONDARY path as a whole. It cannot be toggled mid-path through waypoint settings. If you need a path that sprints in some sections and walks in others, use two separate profiles and switch between them.

### Attack is held continuously

The attack key (`left click`) is pressed when the path starts and re-pressed every 20 ticks as a heartbeat. Ceres does not aim at specific blocks — it relies on the path placing the player in a position where the crosshair naturally sweeps across the crops during movement.

### Checker baselines reset on a random interval

The CheckerController resets all checker baselines every 60–100 ticks (random within that range). This randomisation prevents the checker timing from becoming a detectable pattern. The consequence is that a problem can be masked for up to 5 seconds after a reset — this is an accepted trade-off.

### The ToolChecker warns but does not stop

Other systems legitimately change the held item (e.g. the Repellent Manager switches to the repellent, uses it, and should switch back). Stopping the bot on any held-item change would make the Repellent Manager incompatible with the ToolChecker. The ToolChecker is informational only.

### Repellent manager pauses during application

The Repellent Manager pauses the bot before applying and resumes after a 60-second wait. This is because the repellent use requires the player to be holding the item, which is incompatible with farming. The 60-second wait is not related to the repellent's actual duration — it is a cooldown to prevent the manager from trying to apply again before the tab list updates to show the new timer value.

### `Scheduler.cancelAll()` on stop

`stopBot()` calls `Scheduler.cancelAll()` from PlayerAPI, which cancels **every** pending task across all mods using PlayerAPI. This is intentional — it is the safest way to ensure no lingering actions fire after a stop. If multiple mods using PlayerAPI are running simultaneously, be aware that stopping Ceres will also cancel those mods' scheduled tasks.

### Profile name "Custom"

`"Custom"` is the display name shown in the HUD when no named profile has been cleanly loaded during the current session. It is not a real profile — there is no `Custom.json` in the profiles folder. It simply means "whatever is currently in paths.json, which may have been hand-edited or is a leftover from a previous session."

### Area check uses tab list, not coordinates

Ceres does not validate the player's position using coordinates. It checks the `"Area:"` line in the tab list. If the tab list hasn't loaded yet (the line is absent/empty), no area check fires. This prevents false positives on world join before the tab list populates.

### `checksBypassed` flag

Setting `BotStateManager.isChecksBypassed()` to true skips the entire `CheckerController.tick()` call. This is separate from the per-checker toggles in BotConfig — those prevent individual checkers from firing, but the area check always runs regardless of those toggles. `checksBypassed` bypasses even the area check. It is not exposed in the standard config GUI and is intended for development/testing only.
