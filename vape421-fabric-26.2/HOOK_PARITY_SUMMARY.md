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
- ItemStack tooltip post-processing through the recovered 26.2-aware `applyFabric` path
- chat-message component rewrite callback
- HUD extraction bridge
- world extraction/render lifecycle bridge
- portable font-atlas service boundary

Validation completed for the shell:
- Java 25 / Fabric 26.2 compilation on Windows x64
- Java 25 / Fabric 26.2 compilation on macOS Apple Silicon
- Java 25 / Fabric 26.2 compilation on macOS Intel
- Java 25 / Fabric 26.2 compilation on Linux
- headless `runClient` smoke launch with Mixins applied and no Mixin injection errors

Still active migration areas:
- first-person hand/item rendering
- entity/player render-state callbacks
- raw GL11/fixed-function rendering removal
- XRay/chunk/block rendering
- recovered-core source-set integration and runtime bootstrap
- authentication/online-service transport
- full module-by-module runtime validation

No hook is considered fully WORKING solely because it compiles or applies. Feature-specific behavior still needs runtime verification.
