# Vape 4.21 -> Fabric 26.2 Migration — Checkpoint 04

Date: 2026-09-23

This checkpoint continues directly from Checkpoint 03. It is not a restart or a rewrite.

## Baseline

- Minecraft: 26.2
- Java target: 25
- Fabric Loader: 0.19.5
- Fabric API: 0.161.0+26.2
- Fabric Loom: 1.17-SNAPSHOT
- Runtime target: normal client-side Fabric mod; no external injector/DLL/JVMTI bootstrap

## Current local source HEAD

`9af0b6899de21007c649e0cfce05bf0a298b5956`

Baseline commit for replaying this checkpoint:

`518b31e` — Checkpoint 03 baseline

## Source changes in Checkpoint 04

1. Modern ESP Outline bridge
   - Uses the 26.2 entity render-state outline color rather than the recovered stencil/display-list renderer on Fabric.
   - Preserves recovered targeting/friend/enemy color rules.
   - Legacy outline callbacks remain available for historical non-Fabric paths but are gated off on Fabric.
   - Fixed the recovered self-filter wrapper comparison.

2. Correct final-26.2 extraction boundary
   - Uses `net.minecraft.client.renderer.extract.LevelExtractor`.
   - Recovered pre/post world-pass events fire around visible-entity extraction.
   - Entity state decoration wraps `EntityRenderDispatcher#extractEntity(Entity,float)` so vanilla still performs its normal glowing-entity bookkeeping.

3. Fabric-safe render state
   - Fabric startup explicitly forces the buffered/logical OpenGL backend before recovered-core startup.
   - Additional SpawnerFinder, PingManager, BlockIn, CrystalAura, OnlineFriend and ESP2D state operations no longer query/mutate GL11 state directly on the Fabric path.
   - Legacy lightmap enable/disable wrappers are no-ops on Fabric 26.2 where lighting belongs to extracted/submitted render state.

4. Final-26.2 custom pipeline construction
   - Removed stale 26.1-style pipeline builder declarations from `FabricRenderBackend`.
   - Reuses Mojang's final-26.2 pipeline snippets and preserves dedicated depth-tested/no-depth Vape world geometry variants.
   - Uses final-26.2 world geometry invalidation.
   - Removed an unnecessary `RenderType` Mixin and unnecessary snippet accessors where final 26.2 exposes public APIs directly.

5. Buffered explosion spheres
   - Fabric no longer relies on GLU/immediate-mode sphere rendering for the Explosions module.
   - Added buffered wire-sphere and filled-sphere primitives routed through the existing world batch path.

6. XRay metadata preservation
   - Non-target translucent quad cloning now preserves final-26.2 ambient occlusion, baked normals and baked colors.
   - Only the chunk layer/vertex alpha behavior needed by XRay is changed.

## Persistence / reset recovery

The full recovered third-party source is intentionally not uploaded to the public `Pixelied/Agents` repository.

GitHub branch:

`Pixelied/Agents:vape421-fabric-26.2-migration`

Persistence bundle:

`projects/vape421-fabric-26.2/checkpoints/04/`

That directory contains an ordered per-commit patch series. To reconstruct this checkpoint after a local reset:

1. Start from the full Checkpoint 03 source ZIP.
2. Apply every patch listed in the checkpoint 04 GitHub README in order.
3. Verify the resulting source HEAD/content against this status file.

Every implementation batch should continue to be committed locally and mirrored to the GitHub checkpoint patch series before beginning the next batch.

## Verification performed

- `git diff --check` passed for committed source changes.
- The local working tree was clean when this checkpoint status file was authored.
- Exact Fabric API 26.2 source was used to correct the `LevelExtractor` target.
- Exact/final 26.2 renderer API documentation/source was used for the custom pipeline, RenderType and XRay quad metadata changes.
- No successful Java 25/Loom build is claimed yet.

## Current build limitation

The current runner still has Java 21 and its shell cannot resolve Gradle/JDK dependency hosts through DNS. A real Java 25 Loom compile remains mandatory before declaring compilation success.

When a suitable runner is available:

1. use Java 25;
2. run `./gradlew build` in `fabric26_2`;
3. fix every actual compile/mapping error;
4. run the development client;
5. test runtime behavior and module parity.

## High-priority remaining work

- Continue exact-26.2 compile/API hardening across the Fabric adapter and Mixins.
- Audit remaining reachable raw-GL code paths; port visible geometry rather than no-oping it.
- Validate XRay face/fluid/translucency behavior and chunk rebuilds at runtime.
- Continue entity-render parity: ESP 2D/3D, Skeleton, Tracers, NameTags, Chams/outline behavior and target visuals.
- Port remaining custom procedural GUI effects and blur without raw-GL dependency.
- Run the actual Java 25/Loom build and dev client.
- Test settings/config/keybind/input/packet/movement/combat/inventory/world/render behavior module-by-module.
- Validate online/auth/profile functionality independently from the obsolete injector infrastructure.
- Test macOS and Windows behavior.

## Important rule

Do not mark a module or subsystem WORKING merely because it compiles. Preserve the recovered behavior where practical, modernize the implementation for Fabric 26.2, and document unavoidable behavior differences.
