# Checkpoint 04 support note

Date: 2026-09-23

Checkpoint 04 was created from the current recovered-source migration workspace.

## Main changes

- Final-26.2 Blaze3D custom-pipeline construction now inherits vanilla 26.2 pipeline snippets instead of using removed 26.1-style builder declarations.
- World geometry invalidation uses `LevelRenderer#invalidateCompiledGeometry(...)` and `GameRenderer#mainCamera()`.
- Fabric explicitly forces Vape's logical/buffered OpenGL abstraction before recovered-core startup.
- ESP Outline uses 26.2 entity render state (`EntityRenderState.outlineColor` + `LevelRenderState.shouldShowEntityOutlines`) during visible world-entity extraction.
- Legacy Outline stencil/display-list callbacks are disabled on Fabric.
- XRay's translucent replacement preserves vanilla quad geometry/UV/direction and MaterialInfo fields while changing the chunk layer.
- Redundant `RenderType` Mixin factory access was removed because final 26.2 exposes the required factory publicly.

## Verification

- Static stale-API audit completed for the touched renderer paths.
- Direct LWJGL2-era import audit completed for the active Fabric compile path.
- Active Java native/JVMTI declaration audit completed.
- `git diff --check` passed before the local checkpoint was archived.
- No successful Java 25/Loom build is claimed; the current runner still lacks the required Java/dependency-download environment.

The full recovered-source checkpoint is not published in this public repository.
