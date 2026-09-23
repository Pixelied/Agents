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

## Current verified source state

Implementation progress is preserved through `9af0b68` plus two corrective reverts:

- `dc4c88c` — reverted the stale assumption that final 26.2 exposes RenderType/pipeline factories publicly.
- `e276e4e` — reverted the stale assumption that final 26.2 BakedQuad carries extra ambient-occlusion/normal/color record fields.

Those two invalid intermediate experiments were never added to the durable private patch series.
The public `Agents` checkpoint archive retains them as historical commits, but its replay manifest follows them with explicit revert patches so the reconstructed final source is correct.

Baseline commit: `518b31e` — Checkpoint 03 baseline.

## Source changes in Checkpoint 04

1. Modern ESP Outline bridge using 26.2 entity render-state outline color; legacy stencil/display-list callbacks are gated off on Fabric.
2. Correct final-26.2 extraction boundary: `net.minecraft.client.renderer.extract.LevelExtractor`.
3. Fabric-safe render state: startup forces the buffered/logical backend; additional SpawnerFinder, PingManager, BlockIn, CrystalAura, OnlineFriend and ESP2D state operations avoid live direct-GL state on Fabric.
4. Final-26.2 custom pipeline construction using Mojang's current pipeline model, with narrow Mixins retained for private/package-private factories.
5. Buffered Explosions wire/filled spheres instead of GLU/immediate mode on Fabric.
6. XRay uses the verified final 10-field `BakedQuad` / 6-field `MaterialInfo` shape.
7. Additional reachable raw-GL hardening for online friend indicators and ESP2D.

## Persistence / reset recovery

Public audit/history branch:
`Pixelied/Agents:vape421-fabric-26.2-migration`

Private durable valid-only source-delta branch:
`Pixelied/MinecraftHacks:vape421-fabric-26.2-migration`

Preferred recovery series:
`VapeV4.21-main/migration-patches/`

To recover after a reset:

1. Start from the full Checkpoint 03 source ZIP.
2. Apply the numbered private migration-patches series in order.
3. Verify against this status.

The public Agents bundle is historical. It preserves two rejected experiments, so follow its README's explicit order including `dc4c88c` and `e276e4e`.

## Verification performed

- `git diff --check` passed for committed source changes.
- Multiple current/final 26.2 source mirrors were cross-checked for `LevelExtractor`, `RenderPipeline`, `RenderPipelines`, `RenderType`, and `BakedQuad`.
- Two stale-source assumptions were caught and reverted.
- No successful Java 25/Loom build is claimed yet.

## Current build limitation

The current runner still has Java 21 and its shell cannot resolve Gradle/JDK dependency hosts through DNS. A real Java 25 Loom compile remains mandatory before declaring compilation success.

## High-priority remaining work

- Continue exact-26.2 compile/API hardening across the Fabric adapter and Mixins.
- Audit remaining reachable raw-GL code paths; port visible geometry rather than no-oping it.
- Validate XRay face/fluid/translucency behavior and chunk rebuilds at runtime.
- Continue entity-render parity: ESP 2D/3D, Skeleton, Tracers, NameTags, Chams/outline behavior and target visuals.
- Port remaining custom procedural GUI effects and blur without raw-GL dependency.
- Run the actual Java 25/Loom build and dev client.
- Test module behavior and macOS/Windows portability.

Do not mark a module WORKING merely because it compiles.
