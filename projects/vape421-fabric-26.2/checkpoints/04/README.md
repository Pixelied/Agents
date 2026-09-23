# Vape 4.21 -> Fabric 26.2 — Checkpoint 04 persistence bundle

Baseline full-source checkpoint: **Checkpoint 03**.

Current reconstructed source HEAD:

`d671ba04be0901e03b79cc1c2060d7897092ef51`

The full recovered third-party source is intentionally not dumped into this public repository. Instead, this directory contains every migration delta needed to reconstruct the current working source from Checkpoint 03, plus complete Fabric-owned files where useful.

## Exact replay order

Starting from the full Checkpoint 03 source, apply these patches in order:

1. `commits/d158e03.patch`
   - Initial Fabric 26.2 entity-outline bridge.
   - Forces the logical/buffered GL backend before recovered-core startup.
   - Exposes Outline mode under Fabric and disables legacy stencil/display-list execution there.

2. `commits/4bad6ae.patch`
   - Corrects the temporary extraction-target mistake.
   - Exact Fabric API 26.2 target is `net.minecraft.client.renderer.extract.LevelExtractor`.
   - Wraps the visible-entity call to `EntityRenderDispatcher#extractEntity(Entity,float)`, applies Vape's `outlineColor`, then returns the state to vanilla.

3. `commits/a09e791.patch`
   - Makes obsolete legacy lightmap enable/disable wrappers no-ops on the Fabric 26.2 extracted/submitted renderer.

4. `commits/ce6252e.patch`
   - Routes SpawnerFinder blend/depth-mask state through Vape's Fabric-safe GL abstraction instead of direct GL11 state calls.

5. `commits/97b1e16.patch`
   - Ports custom Vape world pipeline construction away from stale 26.1 builder calls.
   - Inherits final-26.2 Mojang pipeline snippets for colored, line, and textured geometry.
   - Preserves dedicated depth-tested/no-depth ESP variants.
   - Uses final-26.2 world geometry invalidation through `LevelRenderer#invalidateCompiledGeometry(...)`.

6. `commits/d671ba0.patch`
   - Routes additional PingManager, BlockIn, and CrystalAura render-state reads/writes through the Fabric-safe backend.
   - Adds logical depth-mask querying to the backend abstraction.

## Important exact-26.2 correction

Fabric API's own **26.2 branch** imports and Mixins `LevelExtractor`. The earlier temporary `LevelRenderer` extraction assumption came from an incorrectly labelled external decompile and is fully corrected by patch 2.

## Verification so far

- Every local source batch is committed.
- Local working tree was clean at `d671ba0` when this manifest was written.
- `git diff --check` passed for the source batches.
- The Fabric adapter tree has been swept for the specific stale 26.1 renderer builder calls addressed by the current patches.
- No successful Java 25/Loom build is claimed yet: the current runner has Java 21 and its Gradle dependency download path still fails DNS resolution.

## Persistence rule going forward

For every new implementation batch:

1. edit actual source;
2. run source/static verification;
3. commit locally;
4. generate a per-commit patch;
5. publish that patch here immediately;
6. update this manifest/current HEAD;
7. only then move to the next subsystem.

This prevents a local/container reset from erasing completed migration work.
