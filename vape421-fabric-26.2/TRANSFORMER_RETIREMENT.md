# Vape 4.21 Fabric 26.2 transformer retirement matrix

The Fabric runtime must not execute the recovered JVMTI/Javassist/ASM class-redefinition pipeline. This file tracks the behavior formerly installed by `PrimaryMappingTaskSet` for Minecraft 26.2 (recovered version id 110) and its Fabric replacement.

Status meanings:
- **REPLACED**: a Fabric event/Mixin exists and applies in the current 26.2 runtime-smoke build.
- **REPLACED/PARTIAL**: the principal hook exists, but feature-specific semantics still need targeted testing.
- **PORTING**: active on 26.2 and still needs a modern hook or additional behavior.
- **INACTIVE-26.2**: version-gated legacy behavior; no 26.2 replacement required.
- **RENDER-BLOCKED**: hook timing is understood, but the recovered path still depends on legacy/raw-GL rendering.

| Recovered task | 26.2 status | Fabric replacement / note |
|---|---|---|
| MinecraftMouseActionTransformer | REPLACED | `KeyboardHandlerMixin` / `MouseHandlerMixin` + `FabricInputBridge` |
| GuiScreenOpenTransformer | REPLACED | `Gui#setScreen` HEAD Mixin -> `EventGuiOpen` |
| MinecraftTickEventMappingTask | REPLACED | Fabric client tick + targeted Minecraft action/pick hooks |
| RenderTickEventMappingTask | RENDER-BLOCKED | 26.2 extraction/render lifecycle exists; recovered draw side still needs modern renderer |
| PlayerTickEventMappingTask | REPLACED/PARTIAL | `PlayerMixin` + `LocalPlayerMixin`; feature-specific semantics still under test |
| EntityLivingBaseEventMappingTask | REPLACED/PARTIAL | `LivingEntityMixin` handles tick/travel; audit remaining event branches |
| LegacyWorldEntityJoinEventMappingTask | INACTIVE-26.2 | legacy branch |
| EntityPlayerSPEventMappingTask | REPLACED/PARTIAL | local-player tick + sprint/movement adapters |
| LocalPlayerTickClassTransformer | REPLACED | `LocalPlayer#tick` HEAD/TAIL Mixin |
| RenderPlayerEventMappingTask | RENDER-BLOCKED | entity/player render-state extraction port |
| EntityRenderStateMappingTask | RENDER-BLOCKED | 26.2 entity render-state extraction port |
| PlayerControllerMPEventMappingTask | REPLACED/PARTIAL | attack/use/tick/container hooks in `MultiPlayerGameModeMixin` |
| PlayerControllerMPTransformer | REPLACED | attack pre/post handled by targeted Mixin |
| ScoreboardScoresEventMappingTask | REPLACED/PARTIAL | `ScoreboardMixin`; scoreboard HUD behavior still needs feature test |
| RenderManagerEntityMappingTask | INACTIVE-26.2 | gated to <1.21.4 |
| LegacyEntityRenderPreEventMappingTask | INACTIVE-26.2 | 1.7.10 Forge only |
| RenderLivingBaseEventMappingTask | INACTIVE-26.2 | gated to <=1.12.2 |
| RenderHandEventMappingTask | RENDER-BLOCKED | first-person hand render pipeline hook needed |
| WorldEntityJoinEventMappingTask | REPLACED | `ClientLevelMixin` entity-add hook |
| Render3DEventMappingTask | RENDER-BLOCKED | Fabric level extraction/render bridge exists; recovered renderer must be modernized |
| FogDensityEventMappingTask | INACTIVE-26.2 | gated to <1.21.4 |
| LegacyRenderStringHookMappingTask | INACTIVE-26.2 | pre-1.16.5 branch |
| NetworkPacketEventMappingTask | REPLACED/PARTIAL | `ConnectionMixin` wraps 26.2 send/receive and preserves cancel + replacement |
| EntityClientPlayerMPMotionMappingTask | INACTIVE-26.2 | 1.7.10-only branch |
| ItemRendererFirstPersonEventMappingTask | INACTIVE-26.2 | old 1.8.9-only branch |
| WorldTimeEventMappingTask | INACTIVE-26.2 | old 1.8.9-only branch |
| PlayerTabOverlayDisplayNameMappingTask | REPLACED/PARTIAL | `PlayerTabOverlayMixin#getNameForDisplay` + recovered event adapter |
| PlayerTabOverlayDisplayNameLegacyMappingTask | INACTIVE-26.2 | old branch |
| ItemStackTooltipMappingTask | REPLACED/PARTIAL | `ItemStackMixin#getTooltipLines` RETURN -> recovered `applyFabric` postprocessor |
| EntityRenderPreEventMappingTask | RENDER-BLOCKED | active >=1.21; modern render-state extraction still required |
| Render2DStaticCallbackMappingTask | INACTIVE-26.2 | recovered transformer returns immediately for >=26.1; Fabric HUD bridge replaces lifecycle |
| ScoreboardObjectiveRenderMappingTask | INACTIVE-26.2 | old branch |
| RenderWorldPassEventMappingTask | RENDER-BLOCKED | Fabric level extraction/render phases available |
| GlStateManagerBlendFuncMappingTask | PORTING | delete raw-global-state dependency; replace affected draw sites with render pipeline state |
| ThreadBoundTickEventMappingTask | REPLACED | Fabric client tick lifecycle is client-thread bound |
| ChatMessageRenderMappingTask | REPLACED/PARTIAL | `ChatComponentMixin#addMessage` + recovered component rewrite adapter |
| LivingSpecialsRenderMappingTask | INACTIVE-26.2 | exact 1.21.11/1.8.9 gate |
| KeyBindingStateEventMappingTask | REPLACED/PARTIAL | `KeyMappingMixin#setDown` + GLFW-backed input state |
| Badlion189InputQueueMappingTask | INACTIVE-26.2 | Badlion 1.8.9 only |
| Badlion189Render2DMappingTask | INACTIVE-26.2 | Badlion 1.8.9 only |
| EntityMoveEventMappingTask (lazy) | REPLACED/PARTIAL | `EntityMixin` + `RecoveredMovementAdapter`; module-specific behavior still needs testing |
| XRay/block render tasks (lazy `Y()`) | PORTING | must move to 26.2 chunk/block render pipeline rather than old GL/Javassist |
| RenderWorldPassExecutorDrainMappingTask | RENDER-BLOCKED | render task draining moves into explicit modern render stages |

## Fabric policy

`PrimaryMappingTaskSet.X()`, `d()`, and legacy class replacement are not valid initialization steps under Fabric. The recovered core keeps useful wrapper/mapping discovery temporarily, while interception behavior migrates to Fabric events, direct 26.2 access, accessors, and focused Mixins.

A missing replacement must stay visible in this matrix. It must not be hidden by returning a fake success code from `replaceClassBytes`.

## Current validation

The Fabric shell has passed compilation on Windows x64, macOS Apple Silicon, macOS Intel, and Linux, plus a headless Minecraft 26.2 `runClient` smoke test with its Mixins applied. That proves hook signatures/loadability, not complete Vape behavior.
