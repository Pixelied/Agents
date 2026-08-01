# SpeedBridge Assist 1.1.0 Reported Regressions Addendum

**Date:** 2026-08-01
**Status:** Mandatory amendment to the approved Breezily and Silent Aim design

## Staircase stale-placement crash

The real 1.1.0 JAR crashed at `SpeedBridgeController.tickStaircase` when it dereferenced `staircase.step().lower()` while the step was null.

Root-cause trace:

1. A staircase lower or upper placement remains in `pendingPlacement`.
2. The staircase aborts while grounded and calls `staircase.reset()`, setting `phase = IDLE` and `step = null`, without invalidating the pending placement.
3. A later timeout in `processPendingPlacement()` calls `staircase.lowerFailed()` or `staircase.upperFailed()`.
4. Those callbacks set `phase = RECOVERY` despite there being no step.
5. `staircase.active()` becomes true and the next `tickStaircase()` dereferences the null step.

A minimal reproduction produced `active=true`, `phase=RECOVERY`, and `step=null` after `start -> reset -> lowerFailed`.

The implementation must fix ownership at the source:

- every pending placement carries session, technique, and operation/cycle generation;
- reset and abort invalidate owned pending work;
- stale confirmations and failures are ignored;
- `StaircaseCycle` enforces `active() => step() != null`;
- orchestration still fails closed if an impossible null-step state appears.

## Physical backward movement regression

The current controller reads physical S as backward intent using the activation/reference yaw, but `applyMovement()` disables every synthetic movement key whenever any physical movement key is held. Visible placement rotation then changes `LocalPlayer` yaw toward the block face. Because vanilla movement is yaw-relative, the physically held S key changes world direction as the camera turns. A 180-degree placement turn makes S point exactly opposite the committed bridge direction.

The implementation must separate intent from execution:

- capture raw physical input before automated rotation;
- commit a world-space movement frame;
- transform accepted physical/synthetic intent into movement impulses relative to the current logical yaw before vanilla physics consumes them;
- preserve raw physical state for override and cleanup logic;
- abort conflicting input instead of fighting it;
- use the same movement-frame adapter in Visible and Silent Packet aim.

Changing only `applyMovement()` or pressing synthetic W against physically held S is insufficient.

## Required regression gates

- stale lower timeout after grounded staircase abort;
- stale upper timeout after grounded staircase abort;
- stale confirmation after a new cycle begins;
- disable/disconnect with pending stair work;
- property check that no public transition yields active staircase state with null step;
- physical S alignment through 180-degree placement yaw changes;
- physical S in all four cardinal bridge directions;
- cleanup of movement override and every synthetic input after failure or exit.

Implementation is not complete until these regressions pass dedicated automated tests and real gameplay verification.