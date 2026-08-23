# Pearl Catcher 2.2.0 Reactive/Hybrid Solver Design

## Goal
Make normal Auto catching use the real spawned pearl state whenever the old all-before-throw prediction is not both robust and close to the requested catch distance. Keep an immediate predictive path only when it is demonstrably reliable. The reactive path must re-solve the wind charge from the player's actual current eye position and vanilla launch-time XYZ movement, so falling, ascent, horizontal speed, and projectile randomness are incorporated at the moment the wind is thrown.

## Strategy selection
- Auto first computes the existing back-to-back predictive plan.
- A predictive plan is eligible for immediate execution only when its sampled first-hit robustness is at least 0.80 and its target-distance error is at most `max(1.0, targetCatchDistance * 0.15)` blocks.
- Otherwise Auto enters reactive mode: throw only the pearl, observe the spawned pearl, reconstruct its launch velocity, then solve and throw the wind charge.
- Manual lead 1/2/3 modes remain predictive debug modes; they never masquerade as reactive timing.

## Pearl planning for reactive mode
- The pearl still needs to pass near the requested crosshair ray and distance. A pure `PearlTrajectoryPlanner` chooses the pearl rotation before use.
- It searches the same small yaw neighborhood and full pitch range but scores only pearl geometry: target-distance error, crosshair-ray error, angular deviation, and time.
- The planner uses vanilla launch inheritance from `getKnownMovement()`, including airborne Y and grounded Y suppression.
- Terrain is checked before the pearl is thrown. If no pearl path can approach the configured crosshair/range envelope, no item is used.

## Actual pearl launch reconstruction
- The pearl spawn position is authoritative from vanilla item construction: `(thrower.x, thrower.eyeY - 0.1, thrower.z)` captured at pearl use.
- At the first observed client pearl, its current velocity and `tickCount` are recorded.
- `ThrowableProjectile` updates velocity each tick as `(v + gravity) * inertia`; therefore the launch velocity can be reconstructed by inverting `v_prev = v_after / 0.99 + (0, 0.03, 0)` exactly once per observed completed tick.
- Server-path prediction is reconstructed from the stored launch position and reconstructed launch velocity rather than client entity positions, avoiding interpolation/network snap artifacts.
- If the pearl is in water or another non-air medium before wind solve, reactive solving is rejected rather than pretending the 0.99 air model is exact.

## Reactive wind solving
- `ReactiveWindSolver` receives the reconstructed actual pearl launch path, original target ray, current player eye position, current vanilla inherited movement, and the first pearl movement segment on which the newly thrown wind charge can exist.
- Because the pearl is already fixed, only wind yaw/pitch and future catch tick are searched. The same exact `AABB.clip` entry and age-dependent projectile margin are used.
- Every candidate is rejected if any earlier pearl segment after wind spawn would already enter that same wind trajectory's AABB.
- Robustness perturbs only the wind launch, because pearl randomness is already measured from the real spawned projectile. Minimum reactive robustness is 0.80.
- Target catch distance remains the dominant ranking term. Crosshair error, wind angle, and time are secondary.
- Auto may retry the reactive wind solve for up to 8 client ticks while the pearl remains alive; every retry uses the player's current eye position and current `getKnownMovement()`.

## Debug authority
- Client entity positions are not server collision authority because network/interpolation corrections can create artificial backwards segments.
- The old `observedVanillaClip` result is removed as a success criterion.
- Debug exports separately record client display positions and a reconstructed physics path from the actual launch velocity.
- Success classification is based on pearl disappearance plus consistency with the reconstructed planned intercept/teleport region, not an interpolated client segment clip.
- Exports include strategy, actual/reconstructed pearl launch velocity, pearl age when wind was used, wind origin/motion at use, reactive solve time, planned first-hit tick, target-distance error, and robustness.

## Settings
- Existing Target catch distance, Max prediction horizon, Reset to defaults, rotation mode, and debug controls stay.
- Wind timing label becomes `Auto (hybrid)` for AUTO.
- Lead 1/2/3 remain clearly labeled predictive/debug modes.
- Internal defaults: predictive/reaction minimum robustness 0.80; reactive wait limit 8 client ticks.

## Compatibility
- Minecraft 26.1.2, Fabric Loader >=0.19.3, Fabric API >=0.153.0+26.1.2, Java 25.
- Client-only. Vanilla item-use/rotation/movement packets only. No custom payloads, fake projectile motion, or server modifications.
