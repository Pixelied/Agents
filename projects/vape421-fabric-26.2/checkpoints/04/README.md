# Vape 4.21 -> Fabric 26.2 Checkpoint 04 support bundle

Baseline full-source checkpoint: Checkpoint 03.
Local migration commit: `d158e039b9381ce315059b59e035277c5c5107a0`.

This support bundle persists the real source delta without publishing the entire recovered client source in the public Agents repository.

## Batch 1 changes

- Correct final-26.2 world entity extraction target from prerelease `LevelExtractor` to `LevelRenderer`.
- Preserve recovered pre/post world extraction events at `LevelRenderer#extractVisibleEntities`.
- Apply ESP Outline through final-26.2 `EntityRenderState.outlineColor` instead of stencil/display-list rendering.
- Expose Outline mode under the Fabric backend while leaving legacy availability rules unchanged elsewhere.
- Disable legacy Outline GL/stencil callbacks on Fabric.
- Fix recovered ESP self filtering by comparing wrappers rather than raw-handle-to-wrapper.
- Explicitly force Vape's buffered/logical OpenGL backend before recovered-core startup so class-load order cannot select the legacy backend.

## Files

- `recovered-source.patch` — minimal-context patch against Checkpoint 03 for recovered-source changes.
- `FabricMigrationRuntime.java` — complete Fabric-owned replacement file.
- `FabricOutlineHooks.java` — complete Fabric-owned new file.
- `LevelRendererEntityExtractionMixin.java` — complete Fabric-owned final-26.2 extraction Mixin.
- `vape421.mixins.json` — complete current Mixin config.

`git diff --check` passed for this batch. A Java 25/Loom build is still not claimed until the project is actually compiled in an environment that can resolve the toolchain/dependencies.
