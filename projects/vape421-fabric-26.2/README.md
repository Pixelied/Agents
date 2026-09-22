# Vape 4.21 Fabric 26.2 migration

Working notes for the migration of the recovered Vape 4.21 client to a conventional Fabric 26.2 client mod.

## Current baseline

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.5
- Fabric API 0.161.0+26.2
- Loom 1.17-SNAPSHOT
- External injector / DLL loading is not part of the target runtime.

## Current work

- Full NativeBridge role audit started.
- Active JNI declarations in the recovered Java bridge are being replaced by Java/GLFW-side equivalents.
- Win32 mouse-message paths are being redirected to Vape's own in-process input dispatcher.
- Runtime class redefinition/JVMTI is classified as obsolete infrastructure for Fabric and is being replaced hook-by-hook with Fabric events or Mixins.
- A 107-module parity matrix is maintained locally from ModManager registration paths.
- A separate Fabric 26.2 client-only scaffold exists locally so the historical recovery build can remain intact while the modern runtime is brought up.

## Important rule

Do not mark a feature WORKING just because it compiles. Each module or subsystem must be tested for behavioral parity on 26.2, and changed behavior must be documented.
