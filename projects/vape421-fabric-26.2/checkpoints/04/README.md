# Vape 4.21 -> Fabric 26.2 Checkpoint 04 support bundle

Baseline full-source checkpoint: Checkpoint 03.

Local migration commits:
- `d158e039b9381ce315059b59e035277c5c5107a0` — first Outline/backend batch.
- `4bad6ae7a9d4c49e60a08c7fedc45da55b3712ff` — corrected extraction target against Fabric API's exact 26.2 branch.

This support bundle persists the real source delta without publishing the entire recovered client source in the public Agents repository.

## Current changes

- Final Fabric API 26.2 world extraction target is `net.minecraft.client.renderer.extract.LevelExtractor`, not the pre-26.2 `LevelRenderer` path.
- Recovered pre/post world extraction events remain attached to `LevelExtractor#extractVisibleEntities`.
- Outline ESP wraps the `EntityRenderDispatcher#extractEntity(Entity, float)` call inside `extractVisibleEntities`, applies Vape's targeting/color rules to `EntityRenderState.outlineColor`, then returns the state to vanilla so its normal glowing-entity bookkeeping still runs.
- Outline mode is exposed under the Fabric backend while legacy stencil/display-list callbacks are disabled on Fabric.
- Recovered ESP self filtering compares wrappers rather than raw-handle-to-wrapper.
- Fabric explicitly forces Vape's buffered/logical OpenGL backend before recovered-core startup so class-load order cannot select the legacy backend.

## Verification basis

The extraction correction is grounded in Fabric API branch `26.2` itself:
- `LevelExtractionEvents` imports and documents `LevelExtractor`.
- Fabric's `LevelExtractorMixin` targets `LevelExtractor#extract`, `extractBlockOutline`, and `allChanged`.
- 26.2 entity extraction passes each visible entity through `EntityRenderDispatcher#extractEntity`; the resulting render state carries mutable `outlineColor`.

`git diff --check` passed for both source batches.

A Java 25/Loom build is still not claimed until the project is actually compiled in an environment that can resolve the toolchain/dependencies.

## Files

- `recovered-source.patch` — minimal-context patch against Checkpoint 03 for recovered-source changes.
- `FabricMigrationRuntime.java` — complete Fabric-owned replacement file.
- `FabricOutlineHooks.java` — complete Fabric-owned new file.
- `LevelExtractorMixin.java` — complete Fabric-owned final-26.2 extraction Mixin.
- `vape421.mixins.json` — complete current Mixin config.
