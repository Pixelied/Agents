# Changelog

## 1.0.1 - 2026-09-20

- Smart Latch now ignores Sneak holds used for mining/attacking.
- Smart Latch now ignores Sneak holds used for block placement, interaction, or held item use.
- Attack/Use invalidates the current physical Sneak press for latching until Sneak is released, preventing delayed accidental locks after finishing an interaction.
- Added simulation coverage for interaction suppression, mid-hold cancellation, release-ready cancellation, and fresh-hold recovery.

## 1.0.0 - 2026-09-20

Initial Minecraft 26.2 release.

- Added configurable backslash Toggle Sneak key binding.
- Added physical Sneak override: a fresh Sneak press immediately disables an active toggle.
- Added same-press latch blocking so disabling the toggle cannot immediately relatch.
- Added adaptive Smart Latch confidence accumulation:
  - about 3.5 seconds while moving
  - about 8 seconds while stationary
  - smoothly blended rate when movement changes
- Added release-to-lock confirmation.
- Added minimal animated HUD text above the hotbar.
- Added Minecraft 26.2 GUI-aware forced-sneak suspension while preserving logical state.
- Added Smart Latch cancellation/blocking around GUI transitions.
- Added riding suspension.
- Added death, disconnect, respawn, and world-change resets.
- Added deterministic state/HUD simulation tests.
- Avoided client tick lifecycle events and Minecraft text-component construction in runtime code.
