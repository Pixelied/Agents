# Vape 4.21 -> Fabric 26.2 migration

Target: Minecraft 26.2, Fabric Loader >=0.19.5, Loom 1.17.x, Fabric API 0.161.0+26.2, Java 25.

Architecture: Fabric Loader -> client entrypoint -> platform/Fabric adapters -> recovered Vape core. The EXE/DLL/JVMTI injector is reference-only and is not part of the supported runtime.

Static audit:
- 2,948 recovered Java files
- 33 files mention NativeBridge
- 75 files import raw GL11
- 25 files use Javassist
- 18 files use ASM
- 18 files reference LaunchClassLoader
- 2 files use LWJGL2 input

Current implementation work:
- Fabric 26.2 Loom build scaffold created
- client-only Fabric entrypoint created
- native bridge decomposition started
- all JNI declarations in NativeBridge replaced locally with a Java PlatformServices boundary
- class replacement is deliberately not treated as successful; transformation call sites will migrate to Mixins/events/direct 26.2 access
- cross-platform CI targets Windows x64, macOS arm64, macOS Intel, Linux

Build caveat: the current execution container cannot resolve external Gradle/Fabric dependencies, so runtime/build parity is not yet claimed. CI/networked runners are the verification path.
