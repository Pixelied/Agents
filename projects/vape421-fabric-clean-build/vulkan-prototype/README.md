# Vape 4.21 Vulkan GUI renderer probe

This isolated Fabric mod targets Minecraft 26.2, Fabric Loader 0.19.5 and Fabric API 0.161.0+26.2.

It submits one custom RenderPipeline through HudElementRegistry and GuiGraphicsExtractor.fill. The element is consumed by Minecraft's normal GuiRenderState and GuiRenderer path. It deliberately does not call OpenGL directly and does not create a separate render pass.

The smoke test launches an official Minecraft 26.2 demo world with preferredGraphicsBackend set to vulkan and a Mesa Lavapipe Vulkan ICD. It verifies that the game selected a Vulkan device, reached the HUD extraction callback, and stayed alive while the custom GUI pipeline was rendered.

This is a renderer integration probe, not yet the Vape client port. After it passes, the next step is to translate Vape's legacy 32-float batch payload into native GUI render states and GPU parameter buffers, then migrate the non-GUI world render paths.
