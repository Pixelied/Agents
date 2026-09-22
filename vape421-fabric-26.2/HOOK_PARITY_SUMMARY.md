# Hook parity summary

The Fabric runtime does not execute the recovered JVMTI/Javassist transformer pipeline.

Implemented in the current Fabric shell:
- Minecraft/GLFW keyboard, character, mouse-button, mouse-move and scroll capture
- GUI-open callback
- global pre/post client tick callbacks
- LocalPlayer and Player tick hooks
- living-entity tick/travel hooks
- world entity-add hook
- key-binding state hook
- Minecraft attack/use/continue-attack/pick hooks
- MultiPlayerGameMode attack/use/tick/container hooks
- packet send/receive interception with cancellation and packet replacement
- entity sprint/movement hook support
- scoreboard score suppression callback
- player-tab display-name rewrite callback
- HUD extraction bridge
- world extraction/render lifecycle bridge

Still active migration areas:
- ItemStack tooltip semantic port
- chat rendering callback
- first-person hand/item rendering
- entity/player render-state callbacks
- raw GL11 rendering removal
- XRay/chunk/block rendering
- recovered-core source-set integration and runtime bootstrap
- authentication/online-service transport
- full module-by-module runtime validation

No hook is considered WORKING solely because it compiles. Runtime smoke and feature-specific testing are required.
