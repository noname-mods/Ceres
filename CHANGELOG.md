# Ceres Changelog

## [Unreleased]

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
