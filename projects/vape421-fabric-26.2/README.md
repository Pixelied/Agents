# Vape 4.21 Fabric 26.2 migration

Working notes for the migration of the recovered Vape 4.21 client to a conventional Fabric 26.2 client mod.

## Current baseline

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.5
- Fabric API 0.161.0+26.2
- Loom 1.17-SNAPSHOT
- External injector / DLL loading is not part of the target runtime.

## Current migration state

- NativeBridge has been split conceptually into portable Java/GLFW services versus obsolete loader/JVMTI infrastructure.
- Permanent Fabric events/Mixins replace the historical runtime-transform hook model.
- Input, tick, movement, silent pre-motion, packet replacement/cancellation, screen, frame, HUD, and world-render bridges are source-implemented.
- The Fabric renderer uses Minecraft 26.2 render-state/submission APIs instead of raw OpenGL as its normal path.
- Final-26.2 render-pipeline API hardening is in progress/completed for the shared backend, including separate depth/no-depth world pipelines.
- XRay uses permanent chunk/block/fluid Mixins and final-26.2 geometry invalidation.
- ESP Outline now has a Fabric-native entity render-state path rather than the historical stencil/display-list path.
- A 107-module parity matrix is maintained in the private working migration/checkpoints.

## Current checkpoint

Checkpoint 04 (2026-09-23) preserves the latest working migration source locally. The public Agents branch intentionally stores migration support documentation rather than the recovered proprietary/source tree.

## Verification limitation

The current execution environment does not provide a working Java 25 + dependency-download path, so the migration is not marked as compiling or runtime-verified yet.

## Important rule

Do not mark a feature WORKING just because it compiles. Each module or subsystem must be tested for behavioral parity on 26.2, and changed behavior must be documented.
