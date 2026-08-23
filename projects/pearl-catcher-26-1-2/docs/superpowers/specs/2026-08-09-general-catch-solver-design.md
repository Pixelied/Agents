# General Catch Solver Design

## Goal
Replace Pearl Catcher 2.2.0's predictive/reactive/best-effort planner stack with one continuously replanning solver that works across pitch, target distance, wind timing, and arbitrary finite player momentum using Minecraft 26.1.2 projectile physics.

## Core model
`GeneralCatchSolver` is the only catch planner. Wind throw delay is a search variable, not a mode. A request may represent either the pre-pearl state or a later state where the real pearl launch velocity is known. The same solver returns the best physically valid plan from the information available.

For each future pearl collision tick and allowed wind delay, the solver derives launch directions from kinematics instead of brute-forcing four angular dimensions. Pearl gravity/drag is inverted to derive candidate launch velocity for a target-ray range. Wind reachable position at collision is represented by its fixed-speed reachable sphere plus the real wind-charge AABB. Every candidate is verified using exact per-tick pearl segment -> wind AABB entry semantics and rejected if an earlier collision exists.

## Continuous replanning
Before the pearl exists, the solver returns pearl rotation plus intended wind delay/rotation/catch. The client throws the pearl. On every subsequent client tick before wind use, the client invokes the same solver with the observed/reconstructed pearl launch velocity, current eye position, current player inherited motion, and elapsed pearl age. If the best plan's wind delay from now is zero, throw the wind; otherwise wait and re-solve next tick. There are no predictive/reactive strategy enums.

## Objective
One score ranks every physical candidate:
1. exact AABB entry is mandatory;
2. no earlier pearl->wind collision is mandatory;
3. minimize absolute error from target catch distance;
4. minimize distance from the current/predicted crosshair ray;
5. maximize sampled vanilla-spread success probability;
6. prefer less waiting only as a weak tie-breaker.

No separate best-effort algorithm exists. If requested distance is impossible, the same candidate set returns the closest physically achievable catch and reports requested vs achievable range.

## Player motion
Projectile launch uses Minecraft's full inherited X/Z movement and airborne Y movement. The pre-throw solve predicts future wind origin with constant current motion only as an estimate; once the pearl exists, the same solver is re-run from the player's actual current eye and actual current inherited motion. The target ray is re-anchored to the actual current eye during replanning so player movement does not silently turn into crosshair error.

## User settings
Normal settings retain Target catch distance, Crosshair radius, Rotation mode, Restore slot, and Reset. Max prediction horizon and manual wind timing become debug-only/internal. Normal Auto/policy choices are removed because there is only one solver.

## Debugging
Trace each solve with requested range, achievable range, wind delay, catch tick, predicted crosshair error, spread reliability, whether pearl launch velocity was observed/reconstructed, and why a candidate/action was refused. Client interpolation overlap remains explicitly non-authoritative.
