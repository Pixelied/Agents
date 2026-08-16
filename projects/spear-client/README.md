# Spear Client — Minecraft Java 26.1.2

Spear Client is an attacker-side Fabric client mod that investigates Minecraft Java Edition 26.1.2 spear behavior using only vanilla packets and server-authoritative mechanics.

The implementation is intentionally conservative: it contains three Smart modules built around behavior found in the supplied 26.1.2 client/server source, but **none of the exploit outcomes are labeled runtime-verified yet**. The vanilla dedicated-server verification matrix is still unrun.

## Compatibility

- Minecraft Java Edition: **26.1.2 exactly**
- Java: **25+**
- Fabric Loader: **0.19.3+**
- Fabric API: **0.155.2+26.1.2**
- Loom: **1.17-SNAPSHOT**
- Gradle distribution: **9.4.1**
- Mod environment: **client only**
- Server requirement: **unmodified vanilla Minecraft 26.1.2**

The server must not need Fabric Loader, a plugin, a mod, a datapack, operator commands, custom payload support, or configuration changes for these features.

## Controls

Press **O** to open the Spear Client settings screen.

All combat modules default to **Off**. The screen exposes only implemented conservative controls:

- One-Tap: On / Off
- Lunge Boost: On / Off
- Reach: On / Off
- Reach Team Check: On / Off
- Debug: On / Off

There are no packet-strength, range, timing, or `Maximum` sliders.

Settings are stored in `config/spearclient.json`. Saves use a temporary file and atomic replace when supported; malformed config falls back to safe defaults.

## Smart One-Tap

Smart One-Tap uses the spear's legitimate **item-use / kinetic damage** path rather than pretending an idle left-click STAB has kinetic damage.

Source-backed sequence:

1. acquire a bounded player target;
2. begin legitimate main-hand spear use if the player is not already using it;
3. wait the spear's actual `KineticWeapon.delayTicks()`;
4. calculate the required source-model known movement from the spear's actual kinetic damage multiplier;
5. stage a collision-checked conservative back-to-origin movement path;
6. let the server's normal item-use tick perform kinetic damage;
7. stop on corrections, target loss, spear loss, death, lifecycle reset, or timeout.

The current planner uses a 72 raw-damage source-model target and clamps staging to 6.0–8.5 blocks. For the ordinary spear tiers in the supplied 26.1.2 source, the planner currently chooses 6.0 blocks.

Those damage numbers are **predictions from the source formula**, not proof that a given target dies. Armor, Protection, resistance, absorption, blocking, hurt-invulnerability state, target motion, timing, and server acceptance still matter at runtime.

## Smart Lunge Boost

Smart Lunge Boost does not modify local velocity and call that success.

It requires a real Lunge-enchanted spear and the vanilla Lunge eligibility conditions found in the 26.1.2 source. The current implementation checks that the player is not riding, fall-flying, or in water, and that a survival player has enough food for vanilla Lunge. It also checks the same public STAB cooldown predicate used by the server path.

Source-backed sequence:

1. verify real Lunge eligibility and collision-safe forward route;
2. send ordinary vanilla STAB;
3. wait one full client/server tick boundary;
4. request one forward position capped at 8.5 blocks;
5. stop on any correction or lifecycle failure.

The source vanilla horizontal impulse is approximately `0.458 * Lunge level`. The mod does **not** claim its packet movement amplifies that impulse until vanilla-server testing demonstrates the final accepted displacement.

## Smart Reach

Smart Reach is a bounded spear-ray experiment, despite the internal historical class name `InfiniteReachModule`.

The server source extends the spear ray by the positive projection of the attacker's accepted `knownMovement` onto the server look direction. Current Smart Reach therefore uses a conservative path:

```text
-9 blocks -> +9 blocks -> STAB -> origin
```

For off-crosshair targets, the mod first sends a packet-only target rotation and waits one tick so server head yaw has an opportunity to follow. Movement staging uses the **same target direction** as that rotation; this was fixed after a regression test proved that staging along the old camera look could reduce the forward movement projection to zero.

If the two staged positions are accepted, the source model predicts:

- final forward known-movement component: about 18 blocks;
- temporary attack offset from origin: 9 blocks;
- normal spear maximum range: 4.5 blocks;
- source-model original-position reach: about **31.5 blocks**.

The controller acquisition range is intentionally bounded to that same 31.5-block source-model envelope. This value is **not yet a runtime-verified reach limit**.

## Rotation and restoration

Off-crosshair Reach uses packet-only server rotation. It does not rotate the local camera.

The sequencer sends target `Rot`, waits one tick, uses target `PosRot` for staged movement, sends STAB, returns, then restores the original packet rotation.

If vanilla sends a position correction, the sequencer sends **no further cleanup packets**. Vanilla correction wins. Ordinary local aborts may restore the staged packet rotation once.

## Safety and sequencing rules

The implementation uses one shared `AttackSequencer` so modules cannot overlap packet sequences. Current hard rules include:

- maximum 8 movement positions per sequence;
- finite coordinates only;
- conservative movement positions remain within a 9-block radius of the sequence origin;
- collision validation samples movement segments at 0.25-block spacing;
- no sleeps or background timing hacks;
- server corrections abort immediately;
- disconnect and level-change resets emit no cleanup packets;
- death, respawn, target loss, spear loss, module disable, and timeouts fail closed;
- disabling the module that owns an active sequence aborts that sequence even if another module remains enabled.

## Debug evidence

Debug mode exposes read-only sequence information such as:

- target name / entity id / client distance;
- active module and phase;
- movement packet count;
- correction count;
- base spear range;
- expected forward known movement;
- predicted raw damage when applicable;
- **predicted source-model reach** when applicable.

Unknown or inapplicable predictions are displayed as `n/a` instead of zero.

Each finished sequence also emits one structured `spearclient-evidence` log line. Its `predicted*` fields are source-model values; its result only describes what the attacker client observed about its own sequence (`done`, `corrected`, or `aborted`). A client evidence log does **not** prove target HP, damage, death, or server acceptance by itself.

## Runtime verification status

### Source/code checks completed

The implementation has deterministic tests or focused source-signature checks for:

- server movement-envelope calculations;
- kinetic damage calculations;
- target priority scoring;
- bounded movement paths;
- correction tracking;
- single-owner sequence state;
- packet-only pre-rotation and restoration;
- no restore packet after a vanilla correction;
- Lunge STAB-to-movement tick gap;
- conservative module precedence;
- config defaults and persistence;
- source-model debug formatting;
- lifecycle reset behavior;
- off-axis Reach staging direction.

### Still unverified

The following gates have **not** passed:

- real Java 25 Loom compilation of the full project;
- Mixin target resolution against a launched 26.1.2 client;
- `runClient` UI/keybind smoke test;
- an unmodified vanilla 26.1.2 dedicated-server test;
- target-side/server-observable One-Tap damage outcomes;
- accepted Lunge Boost displacement;
- accepted 31.5-block Smart Reach hit behavior.

Accordingly, no exploit mode is described as runtime-verified or guaranteed.

## Aggressive and long-range experiments

**Aggressive mode status: NOT EXPOSED — first-five packet timing not runtime-verified.**

No first-five-packet `MAXIMUM` mode is present in config or UI.

There is also no 100-, 250-, 500-block, map-scale, or unbounded reach mode. Multi-tick experiments are documented separately because they may reduce to visible packet flight/blink and can be constrained by corrections, collision, chunks, and entity tracking.

See:

- `docs/verification/vanilla-server-matrix.md`
- `docs/verification/long-range-investigation.md`

## Building

The intended verification command is:

```bash
./gradlew --no-daemon clean test build
```

Use **Java 25**.

### Current wrapper limitation

`gradle/wrapper/gradle-wrapper.properties` pins Gradle 9.4.1, but the binary `gradle-wrapper.jar` is not currently committed. The launchers therefore behave as follows:

1. if `gradle-wrapper.jar` exists, use the normal Gradle wrapper main class;
2. otherwise, use a system `gradle` **only if it is exactly 9.4.1**;
3. otherwise, fail with an explicit error.

GitHub CI installs Gradle 9.4.1 and runs the same `./gradlew` entrypoint.

At the time of this README, the repository's GitHub Actions job fails before executing any workflow step due to external Actions runner/account infrastructure, so there is no valid Java 25/Loom build result yet. A four-second infrastructure failure is not treated as a compiler failure or a passing build.

## Vanilla server test protocol

Do not call a feature verified from source inspection or attacker-client logs alone.

For runtime claims, use the matrix in `docs/verification/vanilla-server-matrix.md` against the official vanilla 26.1.2 dedicated server. Record corrections, accepted movement, ping, target tracking, target-side HP/death evidence, and observer-visible displacement where relevant.

Every currently unrun row remains `INCONCLUSIVE`.

## Development boundary

The supplied decompiled Minecraft 26.1.2 source is treated as a read-only primary implementation reference. It is reconstructed source, not the original Mojang source, so Fabric/Loom symbols and build-time behavior still require independent compilation/runtime verification.
