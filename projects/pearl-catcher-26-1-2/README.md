# Pearl Catcher 2.5.2 — Minecraft 26.1.2 Fabric

Client-only pearl/wind-charge catcher for Minecraft 26.1.2.

- **G** — normal catch along the current crosshair ray.
- **H** — automatic vertical catch targeting pitch `-90°` through the same solver.
- **B** — pitch-sweep debugger.


## 2.5.0: hardened execution, Legit input, and offhand support

2.5.0 keeps exactly one `GeneralCatchSolver`. Fast and Legit execution are two ways of carrying out the same physical plan; there are no new elytra/offhand/legit planners.

**Item switching** and **Rotation** are independent settings. Item switching can be `Fast` or `Legit`; rotation can be `Silent`, `Visible`, or `Current`. Fast mode uses vanilla item interaction directly after resolving main hand/offhand/hotbar state. Legit mode queues the player's actual configured hotbar, Swap Item With Offhand, and Use key mappings, waits for Minecraft to confirm the resulting hand/slot state, then re-solves before the timing-critical use.

Offhand is first-class. Pearl-in-offhand and wind-in-offhand layouts are supported. Legit mode uses the real swap-hands key when an offhand projectile must become the vanilla main-hand use item, confirms the swap, and restores it only when doing so cannot overwrite a manual/newer slot choice.

Legit timing is based on confirmed state rather than arbitrary human-delay ticks. A Use click queued at Fabric END_CLIENT_TICK is consumed by Minecraft's next normal `handleKeybinds()` pass before projectile entity advancement, so a fully prepared Legit wind can still execute a solver delay of zero. If a hotbar/swap preparation is not ready, the solver receives a minimum executable delay and searches the next viable catch instead of firing late.

The Legit Use bridge only applies solved Silent/Visible rotation around Minecraft's real `startUseItem()` call. If the selected item changes between queue and consumption, that Pearl Catcher-owned Use is cancelled rather than right-clicking an unrelated item. Synthetic clicks also drain echoes from Pearl Catcher's own G/H/B mappings so remapped vanilla keys cannot recursively start new catch attempts. Screens/overlays block new synthetic input; a queued input that becomes screen-blocked is discarded/retried instead of leaking into a later gameplay tick.

The runtime rechecks item presence, cooldown, selected hand/slot, player continuous-use state, terrain, current player motion, real pearl launch state, and the latest solver result before wind use. World/player changes and death clear execution state. Known-invalid or stale plans wait/re-solve rather than knowingly throwing a miss.

No client can guarantee a catch under literally every external condition: impossible geometry, vanilla random launch spread, severe server TPS/network delay, or server-side plugins can still make a specific instant unsolvable. Pearl Catcher treats those as conditions to wait/re-solve or refuse rather than knowingly executing a stale or invalid throw.

## 2.4.2: server-known movement / elytra fix

2.4.2 fixes the high-speed elytra failure at the state-input layer without adding another planner. Minecraft 26.1.2 spawns player-thrown projectiles from `ServerPlayer#getKnownMovement()`, which is backed by the latest accepted client movement-packet displacement. `LocalPlayer#getKnownMovement()` is not an equivalent value during elytra flight. Pearl Catcher now estimates the server value from the same per-tick position displacement the client sends and feeds that estimate into the same `GeneralCatchSolver`.

Silent rotation no longer sends a standalone `ServerboundMovePlayerPacket.Rot` immediately before item use. On the server, a rotation-only movement packet has no positional delta and can overwrite `ServerPlayer#lastKnownClientMovement` with zero before the projectile is spawned. `ServerboundUseItemPacket` already carries yaw/pitch, so the vanilla use packet alone is sufficient for silent item-use rotation while preserving the movement value that the projectile must inherit.

Debug exports now record the solver's inherited movement, the client's local known movement, the inherited movement inferred back from the real pearl launch, and the resulting estimate error.


## 2.4.1: clearance-first reliability

2.4.1 keeps the 2.3 single-solver architecture. `GeneralCatchSolver` remains the only planner: pearl launch, wind throw delay, wind launch, catch tick, and collision point are solved by one physical objective, and delayed shots are replanned by the same solver when the real pearl launch velocity becomes available.

The August 10 sweep showed why nominally exact catches could still miss. Requested distance was often correct, but many candidates only grazed a wind-charge AABB face, edge, or corner. Independent vanilla spread could shift either projectile farther than that tiny geometric margin. A finite spread sampler could even report 100% on a real shot that later missed.

2.4.1 therefore computes **deterministic collision clearance** for every finalist. Clearance is the maximum interior depth reached by the pearl movement segment inside the effective wind-charge AABB. Face/edge/corner touches have approximately zero clearance; deeper crossings have positive clearance. The solver strongly rejects low-clearance geometry before using sampled spread as a secondary sanity score. A slightly off-target deep crossing can now beat a perfect-distance graze.

Candidate retention is timing-diverse: each `(catch tick, wind delay)` pair keeps its own best geometry before the final clearance comparison. This prevents hundreds of nearly identical exact-distance grazes from crowding safer timing branches out of the finalist set.

## Overlapping catches

Manual G/H attempts no longer wait for older pearl/wind pairs to disappear. Each attempt tracks its own pearl, wind, timing, and trace state. Debug sweeps remain sequential so their export stays readable.

Because a new pearl can physically hit an older wind charge, spam is not implemented by blindly removing the old busy lock. Before throwing a new pearl, the client predicts that pearl path against every observed live older wind charge using the same one-way pearl-segment → wind-AABB collision rule. An attempt is refused only when an older charge actually crosses the new nominal path before the intended paired catch.

## Physics used

- Pearl launch power: 1.5.
- Pearl gravity: 0.03.
- Pearl inertia: 0.99.
- Pearl spawn Y offset: -0.1.
- Wind charge launch power: 1.5 with constant post-launch velocity.
- Full player X/Z launch momentum and airborne Y momentum are inherited.
- Vanilla launch spread order is normalize aim → triangle noise → multiply by 1.5 → add inherited motion.
- Collision authority is one-way: the pearl movement segment enters the wind-charge AABB.
- Projectile collision-margin growth and AABB entry semantics match the 26.1.2 source model.

## Solver geometry

Pearl candidate launch velocity is derived by inverting pearl drag/gravity for candidate points around the target ray. Wind direction is not searched on a pitch grid: for each possible wind age, the solver treats the wind AABB center as lying on a reachable sphere and analytically solves segment/AABB boundary intersections, then verifies exact entry and all earlier segments.

For each valid candidate, `Aabb3d.segmentInteriorClearance` computes the exact maximum interior depth along the collision segment by evaluating the piecewise-linear minimum distance to the six box faces. This is deterministic geometry; it does not depend on whether a random spread sample happened to include a bad direction.

If the requested range is physically impossible for the current pitch/state, the solver returns the closest physical solution from the same objective instead of switching algorithms.

## Debugging

Debug exports are written under `.minecraft/pearlcatch-debug/`. They include requested/achievable range, solved wind delay, catch tick, rotations, **collision clearance**, spread reliability, actual reconstructed pearl launch velocity when available, current player state, predicted trajectories, active-attempt count, and client interpolation hints. Client interpolation overlap is explicitly not treated as server collision authority.

## Build note

The source is configured for Minecraft 26.1.2, Fabric Loader 0.19.3, Fabric API 0.153.0+26.1.2, Mod Menu 18.0.0-beta.1, and Java 25. This sandbox cannot perform a normal online Loom/JDK-25 dependency build. Candidate jars produced here are compiled against a strict source/API signature harness, bytecode-audited, packaged with Java-25 classfile major 69, and smoke-loaded from the exact packaged classes. Runtime testing in Minecraft remains the final integration check.


## 2.5.2: reliability hardening

2.5.2 keeps the single `GeneralCatchSolver` architecture but makes safety an execution invariant. Runtime catches now require at least 80% sampled robustness and 0.03 blocks of geometric clearance, projectiles are associated by vanilla ownership, and block/world-border/entity/fluid preflight follows 26.1.2 collision semantics. Legit input leases are bounded and owner-scoped, delayed catches account conservatively for RTT/tick uncertainty, silent server rotation is restored only after the final projectile use, riding and unsupported fluid dynamics fail closed, and manual player slot choices always beat stale restoration.

The runtime boundary is intentionally small: `CatchCoordinator` owns attempt orchestration, `VanillaInputExecutor` owns the bounded vanilla-key protocol, `ProjectileTracker` owns local projectile association, and `RuntimePathSafety` owns world/entity preflight. The goal is deliberately boring reliability: if the client cannot prove a catch is safe enough with the modeled vanilla state, it does not throw.

## 2.5.1: runtime decomposition (no behavior change)

2.5.1 is an internal cleanup release. `PearlCatchMode` is now a small Fabric-facing facade, while execution, attempt/entity bookkeeping, and debug/trace/export responsibilities live in `CatchExecutor`, `CatchAttemptTracker`, and `PearlCatchDebug`. `GeneralCatchSolver` remains the only physics planner. No catch physics, item-switching semantics, offhand priority, rotation behavior, vanilla-key timing, or debug field names were intentionally changed.

## 2.4.1: Pixelied Studio identity + Fabric Event ABI fix

All project identity has been migrated to **Pixelied Studio**. The Java namespace is `studio.pixelied.pearlcatch`, the Maven group is `studio.pixelied`, and release artifacts use the `Pixelied-Studio-Pearl-Catcher-*` filename prefix.

2.4.1 also fixes the 2.4.0 startup crash caused by an incorrect manual Fabric API signature stub. Fabric API 0.153.0+26.1.2 declares `net.fabricmc.fabric.api.event.Event<T>` as an abstract class. 2.4.0 was compiled against an interface-shaped stub, which encoded `Event.register` with `invokeinterface` and crashed at startup. The corrected ABI harness models Event as an abstract class and the packaged-bytecode regression requires a class `Methodref` / `invokevirtual` call.
