# Crystal + Anchor Combat Optimizer 26.1.2

**Version 0.2.0**

Experimental fully client-side Fabric combat optimizer for Minecraft Java 26.1.2. V2 uses an event-driven reactive combat lane for low decision latency while keeping the source-grounded vanilla simulation and legality core.

## Requirements

- Minecraft Java 26.1.2
- Java 25
- Fabric Loader 0.19.3+
- Fabric API 0.155.2+26.1.2
- Mod Menu 18.0.0-beta.1 (optional)

Mod Menu is optional; the mod still loads, toggles, and renders its HUD without it.

## Install

1. Install Fabric Loader for Minecraft 26.1.2.
2. Put Fabric API and `crystaloptimizer-0.2.0.jar` in your Minecraft `mods` folder.
3. Optionally add Mod Menu if you want the in-game configuration screens.
4. Launch Minecraft 26.1.2.
5. Press `O` to toggle the optimizer.

The module starts disabled by default. Default strategy is Lethal Speed. Settings are persisted in `crystaloptimizer.json`.

## V2 runtime

- a bounded strategic scanner refreshes target-local damage opportunities outside the packet hot path
- immutable approvals are published to a shared combat blackboard
- crystal spawn/removal, totem, equipment, block, inventory, target-movement, and config events feed a constant-time reactive lane
- the reactive lane materializes only already-approved actions, runs a linear current-state arbiter, and dispatches normal vanilla interactions
- same-base crystal recycling is `break -> place -> wait for the real server-observed spawn ID -> break -> place`; future entity IDs are never invented, and attacks use only real server entity IDs
- typed timing tracks block acknowledgement, crystal place-to-spawn, crystal attack-to-removal, visible totem refill, and server cadence with bounded p50/p90 distributions
- exact-observable explosion damage uses the verified vanilla 26.1.2 mechanics; incomplete remote state is represented as lower/expected/upper damage instead of a guessed scalar
- hurt-window selection values useful marginal damage and lethal timing instead of raw damage or CPS alone
- target selection is bounded and sticky, with immediate lethal timing and recent threat considered before distance
- real visible rotations gate attacks and block interactions; there are no silent/server-only rotations
- hotbar restocking uses the vanilla container swap path and pauses while reactive item reservations are outstanding
- the HUD is read-only and renders cached strategy, target, approval, damage bounds, timing, CPU latency, mismatch, and rejection diagnostics
- optional Mod Menu screens edit persisted strategy/range/damage/crystal/anchor/restock/rotation/HUD settings and expose a separate diagnostics view
- no automatic movement
- no fabricated packets, entity IDs, movement, reach, hidden inventory, server RNG, or impossible state

## Performance gate

The test harness warms both paths, then times only decision/arbitration work. V2 must keep reactive CPU p50 at or below 1 ms, p95 at or below 2 ms, and achieve at least a 5x median speedup over the equivalent V1 planner replay on the same CI runner.

## Build and test

The authoritative verification command is:

```bash
gradle --no-daemon clean test build runGameTest --stacktrace
```

The GitHub Actions workflow runs that command on Java 25 and publishes a distribution artifact containing the runnable JAR, sources JAR, complete project source ZIP, and SHA-256 checksums.

## Important runtime note

The deterministic unit tests, architecture gates, timing replays, latency benchmark, and Fabric GameTests verify the implemented mechanics and structure, but live multiplayer combat is still the final validation layer. Server latency, player movement, plugins, anti-cheat behavior, and unmodeled runtime edge cases can expose bugs that local tests do not reproduce. Keep the implementation branches available for real-game fixes after the first combat test pass.
