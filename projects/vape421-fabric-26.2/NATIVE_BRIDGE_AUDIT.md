# Vape 4.21 -> Fabric 26.2 NativeBridge audit

Status legend: WORKING, PORTING, NEEDS TESTING, BEHAVIOR DIFFERENCE, BLOCKED, OBSOLETE INFRASTRUCTURE.

| NativeBridge member | Historical role | Current call sites / subsystem | Fabric 26.2 disposition | Status |
|---|---|---|---|---|
| `gkn` | Win32 key-name lookup | `KeyboardCodeUtil` | GLFW key/scancode naming through portable bridge | NEEDS TESTING |
| `gks` | Win32 async keyboard/mouse state | input state helpers | GLFW `glfwGetKey` / `glfwGetMouseButton` through portable bridge | NEEDS TESTING |
| `gat` | loader-provided access token | API/auth | no injector dependency; token now comes from explicit runtime property/env until client auth flow is wired | BEHAVIOR DIFFERENCE |
| `gcb` | JVMTI class-byte retrieval | mapping/ASM pipeline | class-resource read is available for inspection; runtime instrumentation must move to Mixins/events | PORTING |
| `trs` | loader trace/progress state | startup diagnostics | in-process trace state | WORKING |
| `gfb` | loader/native resource read | client resources | classpath resource loading | WORKING |
| `mvk` | Win32 `MapVirtualKey` | key translation/bind text | portable VK<->GLFW/scancode translation | NEEDS TESTING |
| `cpy` | Win32 clipboard | UI | GLFW clipboard, AWT fallback | NEEDS TESTING |
| `smd` / `smdp` | Win32 mouse message injection | click/input helpers | direct dispatch into Vape input layer; Minecraft action integration still needs Fabric-side hook verification | PORTING |
| `dsv2`, `mfv2` | native font path | no current Java call sites | migrate rendering/font use to Minecraft 26.2 rendering; explicit unsupported legacy path | OBSOLETE INFRASTRUCTURE |
| `ss_2`, `ss` | loader settings transport | no current Java call sites | do not preserve loader transport; settings belong to client config/sync layer | OBSOLETE INFRASTRUCTURE |
| `scb` | JVMTI class redefinition | ASM/Javassist mapping tasks | replaced by Fabric Mixins/events per hook; returns distinct legacy-unavailable code until old transform callers are removed | PORTING |
| `sce` | native/client logging | startup/errors | normal JVM logging fallback | WORKING |
| `inv` | native reflective invocation | mapped member invoker | Java reflection | NEEDS TESTING |
| `dc` | disconnect injected DLL from loader | bootstrap | no purpose under normal Fabric loading | OBSOLETE INFRASTRUCTURE |
| `wh` | native window-handle callback | input/UI | stores GLFW window and updates Vape dispatcher | NEEDS TESTING |
| `gc`, `gcj`, `gvc` | runtime class lookup/mapping | wrappers/mapping | keep adapter role; 26.2 Fabric mappings already exist in recovered source | PORTING |
| `gcf`, `gcm`, `gcs` | native reflection helpers | mapping | Java reflection/descriptors | WORKING |
| `trn`, `rs`, pointer helpers | legacy GL rendering state | GUI/render utilities | preserve behavior only temporarily; must move off GL11 for 26.2/Vulkan compatibility | PORTING |
| `gp` | settings/profile bootstrap | config | recovered default local config currently used; real profile persistence must be validated | NEEDS TESTING |
| `updc` | Discord/status integration | presence | isolate from loader; preserve only if actual service path still exists | PORTING |

## Key conclusion

`NativeBridge` was not one subsystem. It mixed loader/bootstrap, Win32 platform helpers, runtime transformation, rendering helpers, resources, logging, auth transport, and input. The Fabric migration therefore removes the JNI boundary while preserving each useful service through the appropriate Java/GLFW/Minecraft implementation. Runtime class redefinition is the major exception: it is architecture-specific and must be replaced hook-by-hook with Fabric events or Mixins rather than emulated.
