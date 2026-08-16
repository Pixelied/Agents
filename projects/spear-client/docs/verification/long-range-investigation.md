# Long-Range Spear Reach Investigation — Minecraft 26.1.2

## Status

**NOT RUN. No long-range mode is exposed.**

The current Smart Reach implementation is intentionally bounded to the conservative source-model path documented in `vanilla-server-matrix.md`. It does not expose `Infinite`, `Map Scale`, `500 Block`, or any equivalent label.

This document defines the runtime experiment required before any multi-tick reach mode can be considered. It does not claim that the experiment will succeed.

## Why multi-tick is a separate investigation

The supplied 26.1.2 server source resets the player movement-envelope origin each server tick. Same-tick conservative Smart Reach uses bounded movement requests and the final accepted per-packet movement as the spear ray's `knownMovement` contribution. Reaching materially beyond that bounded path requires movement distributed over server ticks or a separately runtime-proven first-five-packet sequence.

A multi-tick route can cease to be a useful spear exploit and become ordinary visible packet flight/blink: the attacker may remain displaced long enough for server entity tracking to publish that position to other clients, chunks/collisions may become relevant, and the target may stop being tracked. If that is what happens, the investigation ends without a user-facing mode.

## Preconditions

Do not run this investigation until all of the following are true:

1. `./gradlew clean test build` passes on Java 25.
2. The built client mod loads on Minecraft Java Edition 26.1.2.
3. Conservative movement correction tracking has been checked against an unmodified vanilla 26.1.2 dedicated server.
4. The attacker can record structured client evidence and a second observer/target can record externally visible attacker movement.

No Fabric Loader, plugin, mod, datapack, command block, or configuration change may be added to the server to make a row pass.

## Experiment fields

Record every field for every attempted distance:

- original attacker-to-target distance;
- whether the target remains client-tracked throughout the attempt;
- number of server/client ticks the attacker is away from origin;
- requested position each tick;
- maximum requested delta from the tick origin;
- correction count and corrected positions;
- whether another client observes attacker displacement;
- collision/chunk-loading behavior along the route;
- whether the spear attack produces target-side hurt/HP change;
- whether the attacker returns to the original position;
- total elapsed ticks from first displacement through return;
- ping for attacker and target;
- exact evidence-log sequence IDs.

## Distance matrix

| Distance | Target tracked throughout | Ticks displaced | Other clients see displacement | Corrections | Attack observed on target | Return accepted | Runtime result |
|---:|---|---:|---|---:|---|---|---|
| 100 | — | — | — | — | — | — | INCONCLUSIVE — not run |
| 250 | — | — | — | — | — | — | INCONCLUSIVE — not run |
| 500+ | — | — | — | — | — | — | INCONCLUSIVE — not run |

## Smallest-first procedure

1. Start at the first distance above the largest **runtime-verified** same-tick reach. Do not jump directly to 500 blocks.
2. Use the existing `AttackSequencer`, `MovementPath`, `PacketSender`, collision validation, hard packet caps, and correction observer. Do not add a second movement system.
3. Increase distance only after the prior row has complete attacker, server/target, and observer evidence.
4. Stop immediately on a server correction and record the row; do not patch around the evidence during the same trial set.
5. Stop the investigation if the attacker remains visibly displaced across normal entity-tracking updates. Record it as packet flight/blink behavior rather than renaming it as spear reach.

## Acceptance gate for any future implementation

A user-facing multi-tick mode may be implemented only if the runtime result is meaningfully distinct from ordinary visible flight/blink and all of these are demonstrated repeatedly:

- vanilla server accepts the movement path without corrections;
- target remains tracked;
- spear hit/damage is externally observed at the intended distance;
- attacker returns reliably;
- observer evidence shows an acceptable visibility profile for the intended mode;
- the mode can be described by a precise tested behavior and bound, not by an unbounded name such as `Infinite`.

If these conditions are not met, **no production code or config option is added**.

## Current conclusion

There is no runtime evidence for 100, 250, or 500+ block spear reach in this repository. The current conservative Smart Reach remains the only implemented reach path, and its approximately 31.5-block value is a source-model prediction pending vanilla-server verification.
