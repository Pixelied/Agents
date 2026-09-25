# Audit snapshot

The recovered source is a substantial migration, not a wrapper project.

Migration hotspots:
- rendering: raw GL11/fixed-function assumptions must move to Blaze3D/RenderState/RenderPipeline APIs for Vulkan compatibility
- input: Win32 VK/message semantics and LWJGL2 input must move to GLFW/Minecraft
- mappings: partial 26.2 mapping knowledge exists, but the old runtime mapping/transformation lifecycle remains coupled to JVMTI
- online/auth: loader token handoff must be separated from actual service logic; no fake auth success
- persistence/resources/fonts: Windows paths and loader-provided resource/config behavior need Fabric config/resource equivalents
- transformers: ASM/Javassist injection hooks require targeted Mixins/events/accessors/direct calls
