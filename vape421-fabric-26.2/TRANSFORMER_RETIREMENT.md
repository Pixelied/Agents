# Vape 4.21 Fabric 26.2 transformer retirement matrix

The Fabric runtime must not execute the recovered JVMTI/Javassist/ASM class-redefinition pipeline. This file tracks the behavior formerly installed by `PrimaryMappingTaskSet` for Minecraft 26.2 (recovered version id 110) and its Fabric replacement.

Status meanings:
- **REPLACED**: a Fabric event/Mixin exists in the migration shell.
- **CORE-DERIVED**: the recovered core derives the behavior from another replaced event.
- **PORTING**: active on 26.2 and still needs a modern hook.
- **INACTIVE-26.2**: version-gated legacy behavior; no 26.2 replacement required.
- **RENDER-BLOCKED**: hook timing is understood, but the recovered event still enters raw-GL rendering and must wait for the Blaze3D render port.

| Recovered task | 26.2 status | Fabric replacement / note |
|---|---|---|
| MinecraftMouseActionTransformer | REPLACED | Minecraft/GLFW input + targeted Minecraft action Mixins |
| GuiScreenOpenTransformer | REPLACED | `Gui#setScreen` HEAD Mixin -> `EventGuiOpen` |
| MinecraftTickEventMappingTask | REPLACED | Fabric client tick + Minecraft action/pick hooks |
| RenderTickEventMappingTask | RENDER-BLOCKED | Use 26.2 extraction/render lifecycle after raw-GL event side effects are ported |
| PlayerTickEventMappingTask | PORTING | Need player tick semantics distinct from LocalPlayer tick |
| EntityLivingBaseEventMappingTask | PORTING | travel/update/potion/ray-trace hooks need targeted modern hooks |
| LegacyWorldEntityJoinEventMappingTask | INACTIVE-26.2 | legacy branch |
| EntityPlayerSPEventMappingTask | PORTING | motion/sprint semantics require LocalPlayer packet/input hooks |
| LocalPlayerTickClassTransformer | REPLACED | `LocalPlayer#tick` HEAD/TAIL Mixin |
| RenderPlayerEventMappingTask | RENDER-BLOCKED | entity rendering extraction/state port |
| EntityRenderStateMappingTask | RENDER-BLOCKED | 26.2 entity render-state extraction |
| PlayerControllerMPEventMappingTask | REPLACED/PARTIAL | attack/use/tick/window hooks in `MultiPlayerGameModeMixin`; signatures under CI verification |
| PlayerControllerMPTransformer | REPLACED | attack pre/post handled by targeted Mixin |
| ScoreboardScoresEventMappingTask | PORTING | modern scoreboard extraction/render hook needed |
| RenderManagerEntityMappingTask | INACTIVE-26.2 | gated to <1.21.4 |
| LegacyEntityRenderPreEventMappingTask | INACTIVE-26.2 | 1.7.10 Forge only |
| RenderLivingBaseEventMappingTask | INACTIVE-26.2 | gated to <=1.12.2 |
| RenderHandEventMappingTask | RENDER-BLOCKED | first-person hand render pipeline hook needed |
| WorldEntityJoinEventMappingTask | PORTING | entity add hook still required |
| Render3DEventMappingTask | RENDER-BLOCKED | Fabric level extraction/render bridge exists; recovered draw path must be modernized |
| FogDensityEventMappingTask | INACTIVE-26.2 | gated to <1.21.4 |
| LegacyRenderStringHookMappingTask | INACTIVE-26.2 | pre-1.16.5 branch |
| NetworkPacketEventMappingTask | REPLACED | `Connection` send/receive WrapMethod preserving cancel + replacement |
| EntityClientPlayerMPMotionMappingTask | INACTIVE-26.2 | 1.7.10-only branch; modern motion behavior covered separately |
| ItemRendererFirstPersonEventMappingTask | INACTIVE-26.2 | 1.8.9-only branch |
| WorldTimeEventMappingTask | INACTIVE-26.2 | 1.8.9-only branch |
| PlayerTabOverlayDisplayNameMappingTask | PORTING | active >=1.16.5 |
| PlayerTabOverlayDisplayNameLegacyMappingTask | INACTIVE-26.2 | old branch |
| ItemStackTooltipMappingTask | PORTING | tooltip behavior still needs modern screen/item hook |
| EntityRenderPreEventMappingTask | RENDER-BLOCKED | active >=1.21; use render-state extraction |
| Render2DStaticCallbackMappingTask | INACTIVE-26.2 | recovered transformer itself returns immediately for >=26.1 |
| ScoreboardObjectiveRenderMappingTask | INACTIVE-26.2 | old branch |
| RenderWorldPassEventMappingTask | RENDER-BLOCKED | Fabric level extraction/render phases available |
| GlStateManagerBlendFuncMappingTask | PORTING | do not emulate global GL state; replace behavior at affected draw sites |
| ThreadBoundTickEventMappingTask | REPLACED | Fabric client tick is already client-thread bound |
| ChatMessageRenderMappingTask | PORTING | modern chat extraction/render hook needed |
| LivingSpecialsRenderMappingTask | INACTIVE-26.2 | exact 1.21.11/1.8.9 gate |
| KeyBindingStateEventMappingTask | PORTING | Minecraft/GLFW input bridge exists; recovered key-state event semantics still need wiring |
| Badlion189InputQueueMappingTask | INACTIVE-26.2 | Badlion 1.8.9 runtime only |
| Badlion189Render2DMappingTask | INACTIVE-26.2 | Badlion 1.8.9 runtime only |
| EntityMoveEventMappingTask (lazy) | PORTING | movement pre/post event semantics still required by modules that request the lazy hook |
| XRay/block render tasks (lazy `Y()`) | PORTING | only if a 26.2 module requests them; must use modern block/chunk rendering, not old GL/Javassist |
| RenderWorldPassExecutorDrainMappingTask | RENDER-BLOCKED | Fabric mode does not install the legacy executor drain; render task draining moves into modern render phases |

## Fabric policy

`PrimaryMappingTaskSet.X()`, `d()`, and legacy class replacement are not valid initialization steps under Fabric. The recovered core keeps useful wrapper/mapping discovery temporarily, while interception behavior migrates to Fabric events, direct 26.2 access, accessors, and focused Mixins.

A missing replacement must stay visible in this matrix. It must not be hidden by returning a fake success code from `replaceClassBytes`.
