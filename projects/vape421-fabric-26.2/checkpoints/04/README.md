# Vape 4.21 -> Fabric 26.2 — Checkpoint 04 persistence bundle

Baseline full-source checkpoint: **Checkpoint 03**.

Verified implementation state: through `9af0b68`, followed by corrective reverts `dc4c88c` and `e276e4e`.

The full recovered third-party source is intentionally not dumped into this public repository. This directory is the public audit/history mirror. The preferred valid-only recovery series lives privately at:

`Pixelied/MinecraftHacks:vape421-fabric-26.2-migration/VapeV4.21-main/migration-patches/`

## Exact public replay order

Starting from the full Checkpoint 03 source, apply these **in this exact order**:

1. `commits/d158e03.patch` — initial Fabric outline bridge + buffered backend forcing.
2. `commits/4bad6ae.patch` — correct final-26.2 extraction target to `LevelExtractor`.
3. `commits/a09e791.patch` — disable obsolete legacy lightmap toggles on Fabric.
4. `commits/ce6252e.patch` — route SpawnerFinder render state through the backend.
5. `commits/97b1e16.patch` — port custom pipelines/world invalidation to final 26.2.
6. `commits/d671ba0.patch` — route additional live render state through the backend.
7. `commits/c5b113e.patch` — add narrow Mixin access to private pipeline snippets.
8. `commits/13675f6.patch` — buffered Explosions wire/filled spheres.
9. `commits/c5f476d.patch` — **rejected historical experiment** that assumed public render factories.
10. `commits/97a04d8.patch` — **rejected historical experiment** that assumed extra BakedQuad metadata fields.
11. `commits/e79cb85.patch` — route online-friend indicator transforms through the backend.
12. `commits/9af0b68.patch` — route ESP2D's live blend-state query through the backend.
13. `commits/dc4c88c.patch` — **required revert of patch 9**; restores Mixin access for private/package-private final-26.2 APIs.
14. `commits/e276e4e.patch` — **required revert of patch 10**; restores the real 10-field BakedQuad / 6-field MaterialInfo constructors.

Do not stop at patches 9 or 10. They are preserved only because this public bundle is an audit trail. Patches 13 and 14 are mandatory.

## Final-26.2 facts verified after the rejected experiments

- World extraction is owned by `net.minecraft.client.renderer.extract.LevelExtractor`.
- `LevelRenderer#invalidateCompiledGeometry(ClientLevel, Options, Camera, BlockColors)` is the current world-geometry rebuild API.
- `GameRenderer#mainCamera()` is the current camera accessor.
- Final RenderPipeline construction uses bind-group layouts, vertex bindings, and primitive topology.
- The vanilla pipeline snippets used by the adapter remain private; narrow Mixin access is required.
- `RenderType#create(String, RenderSetup)` remains package-private; the invoker is required.
- Final `BakedQuad` remains a 10-component record and `MaterialInfo` remains a 6-component record.

## Verification / limitations

- `git diff --check` passes on the committed implementation state.
- Reachable raw-GL paths are being audited instead of blindly deleting historical GL code.
- Explosions, OnlineFriend indicators, SpawnerFinder, CrystalAura state, and ESP2D live state have additional Fabric-safe paths.
- No successful Java 25/Loom build is claimed yet. The current runner still lacks a working Java-25/dependency-download path.

## Persistence rule

For each new implementation batch:

1. edit actual source;
2. verify the diff/API assumptions;
3. commit locally;
4. generate an exact patch;
5. mirror it immediately to the private valid-only series;
6. update this public audit bundle when useful;
7. only then move to the next subsystem.
