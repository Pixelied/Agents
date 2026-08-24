# Pearl Catcher 2.0.2 — Minecraft 26.1.2 source analysis

Authority: the uploaded decompiled Minecraft 26.1.2 client archive.

## 1. Collision direction is one-way

`Projectile.canHitEntity` requires `entity.canBeHitByProjectile()`. `Entity.canBeHitByProjectile()` requires `isPickable()`, and `Projectile.isPickable()` is gated by `EntityTypeTags.REDIRECTABLE_PROJECTILE`.

The uploaded `data/minecraft/tags/entity_type/redirectable_projectile.json` contains only:

- `minecraft:fireball`
- `minecraft:wind_charge`
- `minecraft:breeze_wind_charge`

It does not contain `minecraft:ender_pearl`.

Therefore a wind charge cannot select an ender pearl as an entity-hit target. The reverse is legal: the pearl can select the wind charge. The solver must make the **pearl movement segment enter the wind charge AABB**.

`Projectile.onHit` also explicitly treats redirectable-projectile targets as projectiles and tries to deflect them before calling the pearl's hit handling. A player wind charge has `noDeflectTicks = 5`, so an early pearl collision still occurs while the wind refuses the deflection.

Relevant decompiled source:
- `Projectile.java`: `onHit` ~293, `canHitEntity` ~320, `isPickable` ~374
- `Entity.java`: `canBeHitByProjectile` ~1944, `isPickable` ~1948
- `WindCharge.java`: `noDeflectTicks` ~30, `deflect` ~52

## 2. Silent rotation was being overwritten by the item-use packet

In 26.1.2, `ServerboundUseItemPacket` carries `hand`, `sequence`, `yRot`, and `xRot`.

`MultiPlayerGameMode.useItem` constructs it from `player.getYRot()` and `player.getXRot()`. On the server, `ServerGamePacketListenerImpl.handleUseItem` reads those rotations and calls `player.absSnapRotationTo(...)` before the item use.

So this sequence is wrong:

1. send a silent move/rotation packet,
2. leave the local camera on its old rotation,
3. call vanilla `gameMode.useItem()`.

Step 3 sends a use packet containing the old camera rotation, and the server snaps back to it before creating the projectile. That directly explains attempts that appear to calculate an intercept but still launch in the camera direction.

The fixed silent mode temporarily writes the solved yaw/pitch to the local player, calls vanilla `MultiPlayerGameMode.useItem`, then restores the visible camera. The use packet itself therefore carries the solved rotation.

Relevant decompiled source:
- `ServerboundUseItemPacket.java`: constructor ~19, rotation getters ~57/61
- `MultiPlayerGameMode.java`: use packet construction ~392
- `ServerGamePacketListenerImpl.java`: `handleUseItem` ~1414, `absSnapRotationTo` ~1426

## 3. Waiting for client pearl synchronization makes downward catches unreachable

Both ender pearls and player wind charges launch with power 1.5 and inherit the player's known movement. The pearl applies gravity 0.03 and air inertia 0.99 before its movement/collision segment; the wind charge has zero acceleration power and air inertia 1.0.

If the client waits until the thrown pearl is observed and only then solves/throws the wind charge, a steep downward pearl has already accelerated away. Equal initial launch power plus that delay makes many downward catches physically impossible.

The new architecture jointly solves **pearl rotation + wind rotation + intercept tick before either item use**, then executes the two vanilla item uses back-to-back. The wind and pearl intentionally use different rotations when required.

## 4. Vanilla collision is segment-entry clipping, not overlap

`ProjectileUtil.getEntityHitResult` inflates the target AABB by an age-based margin and calls `AABB.clip(from, to)`. The margin is:

`max(0, min(0.3, (source.tickCount - 2) / 20))`

The AABB clip requires a valid segment entry. Merely starting inside the target box is not equivalent to a projectile entity hit.

`ThrowableProjectile.tick` applies gravity, applies inertia, performs collision using the updated velocity, moves, then calls `super.tick()` (which increments age). This means a debug checker running at END_CLIENT_TICK must use the **pre-increment** age for the segment that just completed. Using the observed post-tick age inflates the box by 0.05 blocks one tick early starting at observed tickCount 3.

The solver and live validator now use the exact segment age separately, and the regression test fails if the old off-by-one is reintroduced.

Relevant decompiled source:
- `ThrowableProjectile.java`: tick ~48–65
- `ProjectileUtil.java`: `computeMargin` ~151, `bb.clip(from,to)` ~170

## Implemented architecture

- G: solve and throw a real pearl + wind charge using hotbar switching and vanilla item interaction.
- H: automatic configured pitch sweep.
- Rotation modes: silent packet, visible camera, current camera/no-rotation.
- Config: `config/pearlcatcher.json`.
- Mod Menu screen.
- Joint solver searches pearl/wind rotations and accepts only a true pearl-segment -> wind-AABB entry clip near the target crosshair ray.
- Debug trace records player state, entity IDs, tick counts, position, velocity, AABB, predicted trajectories, solver plan, planned intercept, attempted wind shot/client tick, exact first observed clip point, closest approach, and finish reason.
- Debug visualization emits predicted and observed trails plus the planned intercept.

## Verification performed in this environment

- Deterministic core solver self-test: PASS.
- Regression red/green check for the post-tick margin bug: old formula fails; corrected formula passes.
- Entire mod Java source compiled successfully against a signature harness derived from the uploaded 26.1.2 decompiled Minecraft APIs plus the Fabric/Mod Menu API surfaces used by the mod.
- Final packaged candidate jar contains the expected Fabric metadata and mod classes and all mod class files report Java 25 classfile major version 69.

A normal Loom `gradle clean build` could not be executed in this sandbox because outbound dependency access is blocked and the two private GitHub Actions attempts were rejected before any job step executed. The supplied source project is configured for Loom 1.17.17 / Java 25 and is the authoritative build input.


## 2.0.2 startup crash fix

The manually assembled 2.0.0 candidate was compiled against overly loose signature stubs. That produced invalid JVM symbolic references even though Java source compilation succeeded. The observed 26.1.2 crash proved this immediately: `FabricLoader` is an interface at runtime, while 2.0.0 encoded `FabricLoader.getInstance()` as a class `Methodref`.

2.0.2 rebuilds the signature harness around the real API shapes used by Minecraft 26.1.2 / Fabric Loader 0.19.3 / Fabric API 26.1.2: `FabricLoader`, `Component`, and `InteractionResult` are interfaces; `ClientTickEvents.END_CLIENT_TICK` has type `Event`; item-use packet sends use `Packet<?>`; particles use `ParticleOptions`; Button builder callbacks use `Button.OnPress`; and `Screen#addRenderableWidget` uses the actual `GuiEventListener` erasure. These are ABI/linkage fixes only; the joint intercept physics was not changed by this crash fix.

### 2.0.2 Gson ABI correction

The 2.0.1 manual signature harness still declared a nonexistent Gson overload `Gson.toJson(Object, Writer)`. Minecraft 26.1.2 ships Gson 2.13.2, whose corresponding overload is `Gson.toJson(Object, Appendable)`. The JVM therefore raised `NoSuchMethodError` while the config was first saved during startup. 2.0.2 removes that overload from Pearl Catcher's runtime ABI entirely: config saving now serializes with the verified `Gson.toJson(Object): String` overload and writes the returned string through `Files.writeString`. The manual ABI verifier explicitly rejects the bad `(Object, Writer)` descriptor so this exact regression cannot pass packaging again.


## 2.1.0 sweep findings

The August 9 debug sweeps exposed a second solver-selection bug: a candidate could be valid at its planned tick while the same nominal pearl trajectory entered the same wind-charge AABB on an earlier segment. That made visually "late" plans catch almost immediately. 2.1.0 rejects any candidate whose first nominal AABB entry is not its intended catch tick, and the launch-randomness stress check uses the same first-entry rule.

The old `Max catch distance` field was also only a hard cap. 2.1.0 replaces the user-facing control with `Target catch distance`; candidate ranking now minimizes forward-range error from that requested target while retaining exact AABB-entry/crosshair validity.

Vanilla momentum inheritance remains source-backed: `Projectile#shootFromRotation` adds `source.getKnownMovement()`, with Y suppressed only while grounded. The 2.1.0 regression matrix includes strong positive/negative vertical velocity and high horizontal/combined motion.

The sweeps showed pearl and wind with matching observed entity tick counts after back-to-back use, so normal `Auto (vanilla)` resolves to lead 1. Lead 2/3 are retained only as explicit debug-model overrides; the client does not introduce artificial delayed wind spawns that would move the wind origin and invalidate the launch-state solver.

## 2.2.0 real-sweep findings and hybrid solver

The August 9 2.1.0 sweep showed that target-distance ranking was working in the nominal model (multiple middle pitches planned near the requested 12 blocks), but two separate diagnostics were being conflated with server truth.

First, the old robustness set sampled a handful of extreme perturbation corners rather than the actual vanilla triangular distribution, and the simulator re-normalized after adding launch noise. Minecraft instead normalizes the base aim direction, adds independent triangle noise per axis, then multiplies by launch power. 2.2.0 reproduces that order and evaluates a deterministic low-discrepancy sample of the real triangular distribution. Reliability is now the fraction whose **first** collision lands in the target-distance/crosshair acceptance zone.

Second, client entity positions can snap because of network synchronization/interpolation. The 2.1.0 trace contained apparent backwards pearl segments, so clipping those observed client positions against the observed wind AABB can manufacture a false `firstClipTick`. 2.2.0 therefore labels this only as a client interpolation hint and never uses it as collision authority.

For farther catches, 2.2.0 can use a reactive path. It throws the pearl first, captures the newly loaded pearl, reconstructs the real randomized launch velocity by inverting the pearl's 0.03-gravity / 0.99-inertia updates for its completed ticks, then re-solves only the wind shot from the player's current eye position and current inherited movement. The strict reactive solve requires 80% sampled reliability; a reactive best-effort wind has a hard 60% floor.

Reactive mode is not always physically appropriate. After waiting for an observable pearl state, some short/medium target catches are already unreachable by a 1.5-speed wind charge. Auto therefore remains hybrid: one predictive search produces both a strict >=80% candidate and the closest physical fallback; a preflight-reachable reactive strategy is preferred for farther targets when the immediate candidate is unreliable. This avoids both blindly delaying every catch and doing the expensive predictive geometry search twice.

## 2.3.0 general-solver rewrite

The 2.2.0 sweeps and the older user-supplied pearl-catcher jar showed opposite failure modes: the newer stack strongly favored back-to-back use and caught too early at some pitches, while the older implementation centered around a one-tick wind delay and tended to push catches later/farther. The root architectural problem was treating timing as planner/mode selection rather than as part of the physical solution.

2.3.0 replaces `HybridCatchPolicy`, `JointInterceptSolver`, `PearlTrajectoryPlanner`, and `ReactiveWindSolver` with one `GeneralCatchSolver`. Wind use delay is an internal optimization variable. The same solver handles the initial unknown-spread pearl and later replans from the reconstructed real pearl launch velocity/current player eye/current inherited motion if wind use has not happened yet.

The solver also removes the old wind-angle grid. For a fixed pearl segment and wind age, the wind-charge AABB center lies on a fixed-speed reachable sphere. The solver intersects that sphere analytically with the 26 boundary offsets of the inflated wind AABB and then verifies candidates using exact AABB entry clipping and earlier-collision rejection. This recovers side/edge-entry geometry important for steep downward catches without a separate downward planner.

A hidden six-tick delay cap left over from the hybrid architecture was also removed. The general solver derives an internal timing-search extent from requested catch distance and prediction horizon. This does not make physically impossible ranges possible, but it prevents the caller from truncating the closest achievable solution.


## 2.4.0 collision-clearance findings

The August 10 2.3.0 sweep ruled out fake stationary player momentum as the primary sideways-offset cause. The trace recorded player movement `(0, 0, 0)` while real pearl launch velocity still contained small X/Z components, which is expected from independent vanilla launch spread.

The more important failure was geometric. The 2.3 solver optimized exact nominal AABB entry and requested range, but an entry can be a face, edge, or corner graze with almost no perturbation tolerance. The sweep contained a particularly useful counterexample: a `-80°` plan reported sampled robustness `1.0` and a near-12-block target, yet the real shot timed out. The actual wind trajectory deviated farther than the nominal collision's spare geometric margin. Other pitch branches explicitly showed 3–6% sampled reliability when the planned contact sat near a box corner.

2.4.0 adds an exact geometric metric, `Aabb3d.segmentInteriorClearance(from, to)`. For each point on the collision segment, interior clearance is the minimum distance to the six AABB faces. Those six distances are affine in segment parameter `t`, so their pointwise minimum is concave piecewise-linear; its exact maximum occurs at an endpoint or a pairwise intersection of two face-distance lines. A graze therefore scores approximately zero while a centered crossing approaches the box half-extent.

`GeneralCatchSolver` now preserves timing-diverse candidates, computes deterministic clearance for finalists, applies a strong penalty below the minimum geometric clearance, and only then uses finite vanilla-spread sampling as a secondary sanity penalty. This specifically fixes the 18/20/24-block pattern where some requested distances happened to land on deep timing branches while neighboring distances landed on fragile exact-range grazes. The requested distance remains an objective, but a slightly off-range deep crossing is allowed to beat an exact-distance edge touch.

Manual shot tracking is also now per-attempt rather than singular. G and H may start new attempts while earlier projectiles remain alive. To preserve vanilla collision correctness, each new nominal pearl path is checked against observed older live wind charges before use; only a real predicted older-wind intersection blocks that new attempt. H now requests a vertical `-90°` catch through the same `GeneralCatchSolver`; the sequential debug sweep moved to B.

## 2.4.1 Fabric Event ABI correction

The 2.4.0 manual candidate crashed during the Fabric client entrypoint with `IncompatibleClassChangeError: Found class net.fabricmc.fabric.api.event.Event, but interface was expected`. The exact 26.1.2 Fabric API source declares `Event<T>` as an **abstract class** with `public abstract void register(T listener)`. The manual signature harness had incorrectly modeled Event as an interface, causing javac to encode `Event.register(Object)` as an `InterfaceMethodref` and emit `invokeinterface` at the two client event registrations.

2.4.1 corrects that ABI shape. The release verifier explicitly rejects `InterfaceMethodref ... Event.register` and requires a normal class `Methodref ... Event.register`, preventing this startup regression from passing packaging again.


## 2.4.2 elytra movement-inheritance correction

The 2026-08-11 elytra sweeps showed that the solver could see large local flight motion while the real projectile launch was nearly the pure 1.5-speed throw vector. The immediate cause was Pearl Catcher's silent-use path: it sent a standalone rotation-only `ServerboundMovePlayerPacket.Rot` immediately before `gameMode.useItem`. Minecraft 26.1.2's `ServerGamePacketListenerImpl#handleMovePlayer` computes client known movement from accepted positional displacement; a rotation-only packet therefore supplies zero positional displacement and can overwrite the server player's known movement immediately before `Projectile#shootFromRotation` reads it.

2.4.2 removes that pre-use rotation packet. `ServerboundUseItemPacket` already transports yaw/pitch and `handleUseItem` snaps the server player to those angles before item use, so the extra movement packet was redundant. The solver state feed also stops using client `LocalPlayer#getKnownMovement()` as a server proxy and instead tracks the position displacement spanning the client tick, including the same small movement-send threshold/reminder behavior used by `LocalPlayer#sendPosition`. The estimator is sampled before key-triggered item uses at Fabric END_CLIENT_TICK.


## 2.5.0 execution hardening

2.5.0 does not add a planner. `GeneralCatchSolver` remains the only physical solver, and a new `minimumWindDelayTicks` request constraint lets the executor tell that same solver when a wind use cannot physically be ready yet. This is used by Legit switching preparation rather than maintaining separate timing heuristics.

Fast execution now resolves the required projectile from offhand, selected main hand, or another hotbar slot and uses the corresponding vanilla interaction hand. Legit execution instead drives Minecraft's configured hotbar, swap-hands, and Use `KeyMapping`s. Item-use timing begins from confirmed vanilla state/projectile observation rather than from the time a switch was requested.

Minecraft 26.1.2 processes hotbar/swap/use mappings in `Minecraft#handleKeybinds` before the level entity tick. Therefore a Use mapping queued at client-tick end is consumed before the pearl advances another entity tick; a prepared Legit wind has minimum solver delay 0, while an outstanding hotbar/swap preparation imposes a lower bound of 1 and is re-evaluated after confirmation.

Legit Silent rotation is scoped to the real private `Minecraft#startUseItem` invocation through `MinecraftUseMixin`; it does not emit a standalone movement/rotation packet. The bridge verifies the expected main-hand item immediately at use time and cancels only the Pearl Catcher-owned invocation if a manual/newer slot change would otherwise use the wrong item.

Because `KeyMapping.click(Key)` increments every mapping sharing that physical key, Pearl Catcher immediately drains synthetic echoes from its own G/H/B mappings. This prevents recursive catches when a user remaps Use/hotbar/swap onto a Pearl Catcher control key while preserving normal vanilla duplicate-binding behavior for unrelated mappings.

Screens and overlays are treated as execution barriers. New Legit key synthesis waits while they are open; a still-pending queued key that becomes screen-blocked is removed and a Pearl/Wind Use request is returned to a re-solvable state when the queued click was definitely still pending.

## 2.5.2 reliability hardening

The 2.5.1 physics model was largely source-correct, but execution treated robustness and clearance as ranking preferences rather than hard requirements. That allowed a mathematically ranked candidate to execute even when the 64-sample spread check found it too fragile. 2.5.2 separates diagnostic ranking from executable acceptance: runtime plans must satisfy both the 80% robustness floor and 0.03-block clearance floor.

The runtime controller also now treats vanilla authority explicitly: projectile ownership comes from `Projectile#getOwner`, path preflight includes world border, blocks, hittable entities, and unsupported water/bubble dynamics, delayed wind timing is validated across a conservative RTT/tick-age window, synthetic Legit key leases expire instead of deadlocking, and silent server rotation is restored only after the final projectile use. Cancellation and disable paths are owner-scoped so stale automation cannot fire later or overwrite a user's newer slot choice.
