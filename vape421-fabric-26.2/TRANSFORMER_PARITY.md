# Legacy transformer retirement matrix

The Fabric 26.2 runtime must not execute `PrimaryMappingTaskSet`. This matrix tracks the behavior formerly installed by runtime ASM/Javassist/JVMTI and its Fabric replacement.

| Legacy task / transformer | Fabric 26.2 replacement | Status |
|---|---|---|
| MinecraftMouseActionTransformer | MouseHandlerMixin + FabricInputBridge | PORTING / compiles in shell |
| GuiScreenOpenTransformer | GuiMixin#setScreen hook | PORTING / compiles in shell |
| MinecraftTickEventMappingTask | ClientTickEvents START/END | PORTING / compiles in shell |
| PlayerTickEventMappingTask | Player/LocalPlayer targeted tick mixins | PORTING |
| LocalPlayerTickClassTransformer | LocalPlayerMixin | PORTING |
| EntityPlayerSPEventMappingTask | LocalPlayerMixin | PORTING |
| EntityLivingBaseEventMappingTask | LivingEntityMixin | PORTING |
| LegacyWorldEntityJoinEventMappingTask / WorldEntityJoinEventMappingTask | ClientLevelMixin | PORTING |
| PlayerControllerMPEventMappingTask / PlayerControllerMPTransformer | MultiPlayerGameModeMixin | PORTING |
| NetworkPacketEventMappingTask | ConnectionMixin around 26.2 sendPacket/channelRead0 + RecoveredPacketAdapter | PORTING |
| KeyBindingStateEventMappingTask | KeyMappingMixin + GLFW-backed input state | PORTING |
| EntityMoveEventMappingTask | EntityMixin | PORTING |
| Render2DStaticCallbackMappingTask | HudElementRegistry extraction bridge | PORTING |
| Render3DEventMappingTask / RenderWorldPassEventMappingTask | LevelExtractionEvents + LevelRenderEvents bridge | PORTING |
| EntityRendererEventMappingTask / RenderTickEventMappingTask | modern render bridge; individual event parity still being mapped | PORTING |
| RenderPlayerEventMappingTask | modern entity render-state path | TODO |
| EntityRenderStateMappingTask / EntityRenderPreEventMappingTask | modern entity render-state path | TODO |
| RenderManagerEntityMappingTask | modern entity renderer path | TODO |
| RenderHandEventMappingTask | first-person render pipeline hook | TODO |
| ItemRendererFirstPersonEventMappingTask | first-person item render pipeline hook | TODO |
| ScoreboardScoresEventMappingTask / ScoreboardObjectiveRenderMappingTask | 26.2 HUD extraction hooks | TODO |
| ItemStackTooltipMappingTask | Screen/tooltip extraction API | TODO |
| ChatMessageRenderMappingTask | 26.2 chat HUD extraction hook | TODO |
| FogDensityEventMappingTask | modern fog/render-state integration | TODO |
| GlStateManagerBlendFuncMappingTask | REMOVE as behavior-specific raw GL dependency; replace callers with render pipeline state | TODO |
| LivingSpecialsRenderMappingTask | modern living-entity render-state/layer hook | TODO |
| PlayerTabOverlayDisplayNameMappingTask | 26.2 player-list display-name hook | TODO |
| ThreadBoundTickEventMappingTask | client-thread lifecycle dispatcher | PORTING |
| X-ray/block render task family | modern block/chunk render pipeline; no raw class redefinition | TODO |
| Badlion 1.8.9 task family | not part of Fabric 26.2 target | NOT APPLICABLE |

## Runtime rule

On Fabric, a legacy class-redefinition request is an error, not a successful no-op. `PlatformServices.Provider#replaceClassBytes` therefore returns a non-zero unsupported result unless a provider genuinely performs replacement. The intended Fabric runtime never calls the legacy transformer task set.

## Verification rule

A replacement is marked WORKING only after it has both compiled against the real 26.2 development environment and been exercised in a Minecraft runtime. A green shell build alone is not feature-parity proof.
