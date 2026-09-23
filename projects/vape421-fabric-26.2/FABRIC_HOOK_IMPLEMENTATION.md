# Fabric 26.2 hook implementation status

Concrete migration status for the recovered Vape 4.21 -> conventional Fabric 26.2 client runtime.

## Implemented — awaiting Java 25/Loom runtime test

- Fabric Loader owns startup/shutdown; injector/DLL/JNI bootstrap is not a runtime entry path.
- Recovered core startup is invoked from the Fabric lifecycle after the Minecraft client exists.
- The logical/buffered OpenGL compatibility backend is explicitly forced before recovered-core startup so early class loading cannot pin Fabric to the legacy raw-GL backend.
- Client pre/post tick and thread-bound tick events are bridged.
- Correct 26.2 screen hook is `Gui#setScreen`.
- Keyboard, character input, mouse buttons, scroll and pointer movement feed the recovered state/cancellation layer with GLFW->historical-key translation.
- `LocalPlayer#tick` pre/post events are bridged.
- `Entity#move(MoverType, Vec3)` preserves pre-move cancellation/vector replacement and post-move behavior.
- `LocalPlayer#sendPosition` preserves silent pre/post motion overrides through narrow field accessors.
- `Connection#send(...)` and `channelRead0(...)` preserve packet cancellation and replacement with a re-entry guard.
- `GameRenderer#extract` fires recovered pre-render before world/GUI extraction; `GameRenderer#render` fires post-render at real frame end.
- `LevelExtractor#extractVisibleEntities` supplies recovered pre/post world-pass events.
- `LevelExtractor#extractEntity(Entity,float)` is used for Fabric-native entity outline state.

## Modern rendering bridge

- Existing recovered `RenderBatchManager` remains the behavioral choke point.
- GUI batches become 26.2 `GuiElementRenderState` objects.
- Recovered image/font atlases upload as Minecraft `DynamicTexture` objects through synthetic compatibility handles.
- 3D lines/quads submit through `LevelRenderEvents.COLLECT_SUBMITS` / `SubmitNodeCollector`.
- Separate depth-tested and no-depth pipelines preserve normal and through-wall ESP intent without raw GL.
- Custom pipelines now inherit final-26.2 vanilla pipeline snippets instead of rebuilding removed 26.1-style uniform/sampler/vertex declarations.
- Nested GUI scissor state is translated into 26.2 GUI coordinates.
- Logical blend/depth/cull state is bookkeeping-only on Fabric instead of mutating an OpenGL context.
- Merged batch indices are normalized against each builder's base vertex before submission.
- Windows-only Arial/Bahnschrift choices use bundled portable font fallbacks on Fabric; the old Minecraft-font GL-texture path temporarily uses bundled Noto.
- Legacy framebuffer blur/offscreen allocation is gated on Fabric so unsupported effects degrade instead of crashing.

## ESP Outline

The historical stencil/display-list outline path is not used on Fabric.

- Recovered ESP enable state, targeting, enemy filters, bot/invisibility filters, and color resolution remain authoritative.
- Accepted visible world entities write Vape's color to `EntityRenderState.outlineColor`.
- `LevelRenderState.shouldShowEntityOutlines` is enabled when Vape applies at least one outline.
- The bridge only operates during visible world-entity extraction, preventing inventory/menu entity previews from inheriting ESP outline state.
- Legacy `ESPOutline` OpenGL/stencil callbacks return immediately while Fabric compatibility is active.

This is source-implemented but still needs in-game fidelity testing.

## XRay

- Renderer refresh now uses final-26.2 `LevelRenderer#invalidateCompiledGeometry(...)` rather than the removed `LevelRenderer#allChanged()`.
- `SectionCompiler#compile` filters non-whitelisted block geometry.
- `ModelBlockRenderer#shouldRenderFace` forces target faces when Cave Mode is off.
- Cave Mode preserves vanilla neighbor face checks, producing exposed-target behavior.
- Target quads moved to the translucent path preserve vanilla 26.2 geometry/UV/direction plus MaterialInfo sprite, item render type, tint, shade, and light-emission data; only the chunk layer is replaced.
- Remaining difference: the historical opacity value still needs a dedicated translucent chunk pipeline/runtime validation.

## Known remaining rendering differences

- ESP2D, Skeleton, NameTags, Chams, and related entity-linked visuals still need per-feature extraction/submission migration and runtime validation.
- Rounded/circle/custom fragment-shader UI effects currently fall back to their batched geometry bounds.
- Blur needs a 26.2 render-graph/post-process implementation.
- Some standalone item/potion/entity-preview offscreen callbacks are skipped until ported to 26.2 submission APIs.
- Historical source still contains raw GL/JVMTI code for non-Fabric/legacy paths; reachable Fabric paths are being isolated or replaced rather than deleting recovery evidence wholesale.

## Checkpoint 04

Checkpoint 04 captures the final-26.2 pipeline hardening, world invalidation update, modern Outline ESP bridge, XRay material-preserving translucent replacement, and buffered-backend startup hardening.

The recovered source/checkpoint itself is intentionally not published into this public support repository.

## Verification limitation

The current workspace has Java 21 and no working dependency-download path for a fresh Java 25/Loom build. Source/API checks are conservative, but this is not marked as a passing build until `./gradlew build` and a 26.2 dev-client run succeed under Java 25.
