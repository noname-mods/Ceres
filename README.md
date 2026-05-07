# Ceres

A client-side Fabric automation helper for repetitive gathering and farming tasks.  
Named after the Roman goddess of agriculture.

> **Requires [PlayerAPI](https://github.com/noname-mods/PlayerAPI) and [YetAnotherConfigLib](https://modrinth.com/mod/yacl) to run.**  
> [ModMenu](https://modrinth.com/mod/modmenu) supported but not required

---

## Features

### Path System
Define sequences of waypoints for the bot to follow. Multiple named path configurations can be saved, loaded, and switched between at runtime.

- **Primary and Secondary** path slots
- Per-path sprint toggle
- Pause, resume, and stop at any time
- Profile system — save and load full path configurations

### Checkers
Passive monitors that alert or stop the bot when something goes wrong.

| Checker | What it watches |
|---|---|
| Inventory | Warns when inventory reaches a threshold |
| Tool | Detects when the active tool breaks |
| Yaw/Pitch | Detects unexpected camera movement |
| Pest | Monitors a configurable count before allowing the bot to start |

### Pest Repellent Manager
Automatically reapplies repellent items at a configurable interval.

### HUD Overlay
A minimal in-game overlay showing current bot state, active path, and checker status.

### Config Screen
Full in-game configuration via ModMenu or the `/ceres` command. Settings are organised into logical categories:

- **Bot Settings** — core behaviour toggles
- **Checkers** — enable/disable each checker, set thresholds
- **Keybinds** — view your assigned keys (rebind in Options → Controls → Ceres)
- **Developer** — debug options for troubleshooting

---

## Controls

All bot actions are controlled via keybinds, rebindable in **Options → Controls → Ceres**.

| Action | Default Key |
|---|---|
| Start Primary Path | O |
| Start Secondary Path | U |
| Pause | P |
| Resume | J |
| Stop | K |
| Toggle HUD | ; |
| Open Path Editor | I |

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
