# Ceres

A client-side Fabric mod that automates crop farming — follow a recorded waypoint path, hold the attack key to break crops, manage Pest Repellent automatically, and monitor a live dashboard of farming metrics.  
Named after the Roman goddess of agriculture.

**GitHub:** <https://github.com/noname-mods/Ceres>

> **Requires [PlayerAPI](https://github.com/noname-mods/PlayerAPI) 2.1.0+ and [Fabric API](https://modrinth.com/mod/fabric-api) to run.**  
> [ModMenu](https://modrinth.com/mod/modmenu) is optional — it adds a settings button to the mod list.

---

## Features

### Waypoint Path Following
Ceres follows a sequence of recorded waypoints while holding the attack key to break crops. Two independent path slots (Primary and Secondary) let you define separate routes — for example an outward pass and a return pass. Each path has its own sprint toggle. Paths are recorded in-game by walking your route and pressing "Add Here" in the Path Editor.

### Smart Profile Auto-Load
When you start the bot, Ceres reads your held tool's display name and loads the matching crop profile automatically. A Wheat Sickle loads your Wheat profile; a Carrot Shovel loads Carrot — even reforged tools work because matching is substring-based. Profiles save the full path configuration (both slots + sprint settings) as named files you can switch between at any time.

### Passive Safety Checkers
Six independent monitors run continuously while farming (each toggleable in the config):

| Checker | Behaviour |
|---|---|
| **Inventory** | Watches your hotbar for changes; if nothing changes across a short (~3s) check it assumes the bot is stuck and stops. The Personal Compactor edge case is handled (samples every 2s, only flags on three identical reads), and the first ~10s after a start is excluded so a warp can't false-flag. |
| **Tool** | Detects when your held item changes from what you started with (via the item's stable SkyBlock id, not its display name). |
| **Yaw / Pitch** | Detects if an external force rotates the camera unexpectedly → continues ~1s, then soft-stops (keys released, mouse freed). |
| **Movement** | Flags when your position stalls (~2s) and soft-stops. |
| **Crop** | At start, samples the crosshair block against the crop your held tool harvests — a mismatch plays an audio alert (never stops the bot), catching a wrong-tool-for-this-farm start. |
| **Pest** | Reads the live pest count from the tab list. Plays an alert sound when it reaches your configured threshold. Does not stop the bot. |

The area check is always active regardless of checker toggles: if the tab list shows you have left the Garden, the bot stops immediately and returns full control to you.

### Pest Repellent Manager
Reads the "Repellent:" timer from the Hypixel tab list. When it reads "None", Ceres stops moving, switches to the repellent in your hotbar, uses it, waits for the tab list to update, then swaps **back** to your farming tool and resumes at human speed — all without you touching anything. Works with all repellent tiers.

### Live HUD Overlay
A configurable overlay panel shows up to 14 live rows of farming data pulled directly from the Hypixel tab list:

- Bot state, active profile, and current area
- Player coordinates and camera look direction
- Live pest count vs. your alarm threshold
- Plots, Spray, Repellent timer, Bonus, Cooldown, and Bonus Pest Chance
- Path type and waypoint progress (while running)
- Blocks per second — a 30-second rolling average showing your farming rate
- Next waypoint coordinates

Every row can be individually shown or hidden in the config screen. The main panel, the keybind hints, and the log are **independent movable/scalable elements** — reposition and resize them with the HUD editor (**Edit HUD Position** in the config), or hide the hints/log entirely.

### Blocks Per Second (BPS) Tracking
Ceres counts every crop block broken and keeps a rolling 30-second window. The live BPS figure in the HUD reflects your actual throughput, giving you a consistent number to compare across paths and crops.

### Fully Configurable
All settings are exposed through a config screen with seven tabs. Open it from ModMenu, the `/ceres` command, or a keybind. No file editing required.

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

1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 26.2
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Install [PlayerAPI](https://github.com/noname-mods/PlayerAPI)
4. Install [ModMenu](https://modrinth.com/mod/modmenu) *(optional)*
5. Drop `ceres-*.jar` into your `mods` folder

---

## Compatibility

| Minecraft | Fabric Loader | Java |
|---|---|---|
| 26.2 | ≥ 0.19.3 | 25 |

---

## Minecraft Version Support

This mod targets **one Minecraft version at a time.** When it updates to a new Minecraft version, **previous versions receive zero further support** — no backports, no bug fixes, and a release is never published with support for multiple Minecraft versions at once.

- Want the newest features? You must be on the mod's currently supported Minecraft version.
- Want to stay on an older Minecraft version? Stay on that version's last release — it won't be updated.

The in-game update checker is Minecraft-version aware: if the latest release targets a different Minecraft version than you're running, it tells you so instead of prompting you to install an incompatible build.

---

## For Developers

The full design & documentation is maintained in [CERES_DOCS.md](CERES_DOCS.md). A summary of the internals:

- **`BotStateManager`** — the central singleton: all live state, parsed tab-list data, and BPS tracking. States are `STOPPED / PAUSED / RUNNING`.
- **`PathManager`** — walks the pre-recorded waypoint path each tick (movement + attack key); profiles are named `.json` files auto-loaded per tool via `CropToolMapper` (matched on the item's stable **SkyBlock id**, not display name).
- **Checkers** (`CheckerController` + per-checker classes) — safety monitors that soft-stop or alert: inventory-stall, tool mismatch, yaw/pitch look changes, pest threshold, no-movement, and a start-of-run crop check. A startup grace period suppresses flagging until `/warp garden` finishes repositioning.
- **`PestRepellentManager`** — detects repellent expiry and auto-uses from inventory.
- **`CeresTabListReader`** — parses the Hypixel tab list each tick into `BotStateManager` fields.
- **HUD editor** — main panel, keybind-hints, and log are independently movable/scalable elements via PlayerAPI's shared `HudManager`.
- **`BotConfig`** — all persistent settings, JSON, versioned migrations; `paths.json` + `profiles/<name>.json` hold waypoint data.

See [CERES_DOCS.md](CERES_DOCS.md) for the full section-by-section reference (state machine, path model, checkers, repellent, tab-list parsing, config schema & migrations, HUD, and design notes).
