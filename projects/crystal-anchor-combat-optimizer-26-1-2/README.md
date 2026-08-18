# Crystal + Anchor Combat Optimizer 26.1.2

Experimental fully client-side Fabric combat optimizer for Minecraft Java 26.1.2.

## Requirements

- Minecraft Java 26.1.2
- Java 25
- Fabric Loader 0.19.3
- Fabric API 0.155.2+26.1.2

## Install

1. Install Fabric Loader for Minecraft 26.1.2.
2. Put Fabric API and the built `crystal-anchor-combat-optimizer-0.1.0.jar` in your Minecraft `mods` folder.
3. Launch Minecraft 26.1.2.
4. Press `O` to toggle the optimizer.

The module starts disabled.

## What is implemented

- end-crystal and respawn-anchor combat planning
- exact sequential hurt-window / progressive-damage simulation with explicit uncertainty handling
- bounded beam search rather than a greedy highest-damage loop
- support obsidian and bounded anchor setup / Prepare behavior
- target movement hypotheses and planner-informed target selection
- real visible adaptive rotations; no silent rotations
- real hotbar selection and stack-accurate inventory simulation
- conservative automatic hotbar restocking outside critical commits
- packet-observed opponent equipment, pickup, totem-pop, and timing intelligence
- zero-feedback vs server-feedback dependency modeling
- commit, dispatch, reconciliation, and AutoTotem reservation boundaries
- compact diagnostics HUD
- no automatic movement
- no fabricated entity IDs, movement, reach, rotations, hidden inventory, or fake packet sequencing

## Build and test

The authoritative verification command is:

```bash
gradle --no-daemon clean test build runGameTest --stacktrace
```

The GitHub Actions workflow runs that command on Java 25 and publishes a distribution artifact containing the runnable JAR, sources JAR, complete project source ZIP, and SHA-256 checksums.

## Important runtime note

The deterministic unit tests and Fabric GameTests verify the implemented mechanics and architecture, but live multiplayer combat is still the final validation layer. Server latency, player movement, plugins, anti-cheat behavior, and unmodeled runtime edge cases can expose bugs that a local test environment cannot reproduce. Keep the implementation branch available for real-game fixes after the first combat test pass.
