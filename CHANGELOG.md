# Ceres Changelog

## [Unreleased]

---

## [1.1.6] - 2026-08-23

### Fixed
- **Log Level config.** It was a bare 0–3 number slider whose range never matched the logger's actual
  levels (so "0" logged nothing and "Debug" never reached real debug). It's now a labelled **Error / Warn /
  Info / Debug** dropdown mapped to the correct levels. Existing configs migrate automatically (schema v10).

### Removed
- Dead `BotConfigScreen` (an orphaned pre-config-library screen, no longer referenced).

---

## [1.1.5] - 2026-08-23

### Changed
- **Minecraft 26.2** support.
- **New config screen** — Ceres now uses PlayerAPI's built-in config library; the **YACL** dependency is gone.
- Requires **PlayerAPI 2.0.0**.

---

## [1.1.4]

Everything since the 1.1.3 hotpatch.

### Added
- **Crop check.** For ~1s after start, samples the crosshair block against the crop the held tool
  harvests; a majority mismatch plays an audio alert (never stops the bot) so a wrong-tool-for-this-farm
  start is caught early. New **Crop Check** toggle.
- **Movement checker.** Flags and soft-stops when the player's position stalls (~2s). New **Movement
  Checker** toggle.
- **HUD editor + split HUD elements.** The main panel, the keybind hints, and the log are now independent,
  movable/scalable elements via PlayerAPI's shared HUD editor (an **Edit HUD Position** button, plus
  show/hide toggles for the hints and log). Replaces the old fixed HUD layout.

### Changed
- **Tool & crop detection now uses the SkyBlock item id.** Tool/crop identity reads the item's stable
  SkyBlock internal id instead of its display name (which Hypixel renames) — a more durable replacement
  for the 1.1.3 name-matcher. Requires PlayerAPI 1.18.0+.
- **Smoother Pest Repellent re-apply.** On a mid-run re-apply the bot stops moving, uses the repellent,
  swaps **back** to the farming tool, and resumes at human speed — instead of a long pause.
- **Inventory checker reworked.** Shorter (~3s) check window; the Personal Compactor edge case is handled
  (samples every 2s, only flags when identical three times in a row); the first ~10s after a stop→start is
  excluded so a warp/reposition can't false-flag.
- **Soft-stop behaviour.** Yaw/pitch change, held-item change, and a no-movement stall now "continue ~1s,
  then stop" (keys released, mouse freed); leaving the farm area is an instant stop. A startup grace period
  keeps the `/warp garden` reposition from tripping the look/position checks.

### Fixed
- **Camera snap on stop.** Releasing the mouse lock no longer snaps the camera — accumulated mouse deltas
  are zeroed while running, plus a brief tab-back guard drops single-frame delta spikes.
- **Keybind category label.** The Controls-menu category showed a raw translation key; corrected.

---

## [1.1.3] - 2026-07-10
### Fixed
- **Resource-pack tool names.** The mandatory resource pack renamed the farming tools (Hoe →
  Sickle / Shovel / Cutter), which broke held-tool → crop-profile auto-loading. Updated the tool
  matcher to the new names: Wheat **Sickle**, Carrot/Potato **Shovel**, Sugar Cane / Nether Wart /
  Wild Rose **Cutter**, and Eclipse **Sickle**. Cactus Knife, Cocoa Chopper, Fungi Cutter, and
  Melon/Pumpkin Dicer were unchanged.

## [1.1.2] - 2026-06-17
### Changed
- Update checker moved to PlayerAPI's shared `UpdateChecker` (requires PlayerAPI 1.12.0+). Adds a
  click-to-hide link on the notification and a distinct message when the latest release targets a
  different Minecraft version (release tags use `<modVersion>+<mcVersion>`, e.g. `1.2.0+26.1.2`).

---

## [1.1.1] - 2026-05-11
### Added
- `RebootAlertManager` — detects the Hypixel scheduled reboot chat message and plays a persistent alarm sound until the player leaves the Garden (warp anywhere to dismiss)
- Reboot Alert section in the Sounds config tab: enable toggle, sound ID, volume, pitch, interval; no fixed duration since the alarm loops until area changes
- `BotConfig` schema v5 — added `rebootAlertEnabled` (default `true`) and `rebootAlertSound` (`block.bell.use`, 40-tick interval)

---

## [1.1.0]
### Added
- Blocks Per Second (BPS) tracking — 30-second sliding window counting block breaks via `PlayerAPIEvents.BLOCK_BROKEN`; early-run divisor adjusts so the reading is accurate from the first second rather than artificially low
- BPS HUD row (`bps`) — shows rolling blocks/second while bot is running; green when active, grey when zero
- Update checker — contacts GitHub Releases API on world join; notifies in chat if a newer version is available; runs on a daemon thread
- `updateCheckEnabled` config option (default `true`) and Updates config tab
- `BotConfig` schema v4 — added `updateCheckEnabled` (default `true`)

---

## [1.0.0]
### Added
- Initial release
- Waypoint path following on Primary and Secondary path slots with per-path sprint toggles
- Smart profile auto-load based on held tool display name (substring matching, handles reforged tools)
- Safety checkers: Inventory (stuck detector), Tool (held item change), Yaw/Pitch (look drift), Pest (count threshold); area check always active
- Pest Repellent Manager — auto-detects timer expiry from tab list and applies repellent from hotbar
- Live HUD overlay with 14 configurable rows pulled from the Hypixel tab list
- Path Editor screen for recording and managing waypoints
- Named profile system with per-crop auto-load toggles
- Anti-detection random path pauses
- Alarm sounds (Cycle Complete, Stop Alert, Warn Alert) with configurable sound ID, volume, pitch, duration, interval
- Full YACL config screen with Bot Settings, Auto-Load, HUD Lines, Checkers, Sounds, Keybinds, Developer tabs
- `BotConfig` schema with versioned migrations (v0–v3)
- `BotLogger` with in-memory ring buffer (last 100 lines) and append-only file output
- `/ceres` command to open config screen; ALLOW_COMMAND fallback for servers that override the client command tree
