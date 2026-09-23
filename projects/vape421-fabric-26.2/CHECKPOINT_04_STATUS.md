# Checkpoint 04 support note

Date: 2026-09-23

Checkpoint 04 continues the existing Vape 4.21 -> Fabric 26.2 migration; it is not a restart.

## Corrected final-26.2 state

- Final world extraction uses `net.minecraft.client.renderer.extract.LevelExtractor`.
- World geometry invalidation uses `LevelRenderer#invalidateCompiledGeometry(...)` and `GameRenderer#mainCamera()`.
- Custom pipelines use the final bind-group/vertex-binding/primitive-topology model.
- Vape retains narrow Mixins for the private vanilla pipeline snippets/registration and package-private `RenderType#create(...)`.
- ESP Outline uses `EntityRenderState.outlineColor`; the legacy stencil/display-list callbacks are disabled on Fabric.
- Final `BakedQuad` is the 10-component record and `MaterialInfo` the 6-component record used by the current XRay bridge.
- Explosions spheres, online-friend indicators, SpawnerFinder, several combat/world state paths, and ESP2D live state have been moved off reachable direct-GL operations on Fabric.

Two stale-source experiments briefly assumed public render factories and extra BakedQuad fields. They were reverted. The Checkpoint-04 README includes the mandatory revert patches in its public historical replay order.

## Persistence

- Public notes/audit history: `Pixelied/Agents:vape421-fabric-26.2-migration`
- Private valid-only source deltas: `Pixelied/MinecraftHacks:vape421-fabric-26.2-migration/VapeV4.21-main/migration-patches/`

No successful Java 25/Loom build is claimed yet.
