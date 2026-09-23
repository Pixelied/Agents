# Fabric 26.2 hook implementation status

Concrete migration status for the recovered Vape 4.21 -> conventional Fabric 26.2 client runtime.

## Implemented — awaiting Java 25/Loom runtime test

- Fabric Loader owns startup/shutdown; injector/DLL/JNI bootstrap is not a runtime entry path.
- Recovered core startup is invoked from the Fabric lifecycle after the Minecraft client exists.
- Client pre/post tick and thread-bound tick events are bridged.
- Correct 26.2 screen hook is `Gui#setScreen`.
- Keyboard, character input, mouse buttons, scroll and pointer movement feed the recovered state/cancellation layer with GLFW->historical-key translation.
- `LocalPlayer#tick` pre/post events are bridged.
- `Entity#move(MoverType, Vec3)` preserves pre-move cancellation/vector replacement and post-move behavior.
- `LocalPlayer#sendPosition` preserves silent pre/post motion overrides through narrow field accessors.
- `Connection#send(...)` and `channelRead0(...)` preserve packet cancellation and replacement with a re-entry guard.
- `GameRenderer#extract` fires recovered pre-render before world/GUI extraction; `GameRenderer#render` fires post-render at real frame end.
- `LevelExtractor#extractVisibleEntities` supplies recovered pre/post world-pass events.

## Modern rendering bridge

- Existing recovered `RenderBatchManager` remains the behavioral choke point.
- GUI batches become 26.2 `GuiElementRenderState` objects.
- Recovered image/font atlases upload as Minecraft `DynamicTexture` objects through synthetic compatibility handles.
- 3D lines/quads submit through `LevelRenderEvents.COLLECT_SUBMITS` / `SubmitNodeCollector`.
- A no-depth submit pipeline preserves through-wall ESP intent without raw GL.
- Nested GUI scissor state is translated into 26.2 GUI coordinates.
- Logical blend/depth/cull state is bookkeeping-only on Fabric instead of mutating an OpenGL context.
- Merged batch indices are normalized against each builder's base vertex before submission.
- Windows-only Arial/Bahnschrift choices use bundled portable font fallbacks on Fabric; the old Minecraft-font GL-texture path temporarily uses bundled Noto.
- Legacy framebuffer blur/offscreen allocation is gated on Fabric so unsupported effects degrade instead of crashing.
- The new Fabric adapter tree contains no direct GL11/GL20/GL30 calls.

## XRay

- Renderer refresh uses `LevelRenderer#allChanged()`.
- `SectionCompiler#compile` filters non-whitelisted block geometry.
- `ModelBlockRenderer#shouldRenderFace` forces target faces when Cave Mode is off.
- Cave Mode preserves vanilla neighbor face checks, producing exposed-target behavior.
- Remaining difference: the historical opacity value still needs a dedicated translucent chunk pipeline.

## Known remaining rendering differences

- Rounded/circle/custom fragment-shader UI effects currently fall back to their batched geometry bounds.
- Blur needs a 26.2 render-graph/post-process implementation.
- Some standalone item/potion/entity-preview offscreen callbacks are skipped until ported to 26.2 submission APIs.
- Historical source still contains raw GL/JVMTI code for non-Fabric/legacy paths; each reachable Fabric path is being isolated or replaced rather than deleting recovery evidence wholesale.

## Verification limitation

The current workspace has Java 21 and no outbound download path for JDK 25/Gradle. Hook signatures are checked against current Minecraft/Fabric 26.2 source and source structure is verified, but this is not marked as a passing Loom build until it compiles/runs under Java 25.
