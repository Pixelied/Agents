#!/usr/bin/env python3
"""Disable injected OpenGL UI only on the recovered Minecraft 26.2 Fabric path.

The existing Fabric migration overlay already skips the old Javassist/JVMTI
runtime transformers. Its GUI initialization still queued an OpenGL renderer,
which fails on Vulkan and aborts manager startup. The GUI is now a native Screen.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_legacy_startup.py <recovered src/main/java>")

root = Path(sys.argv[1])
vape = root / "gg/vape/Vape.java"
source = vape.read_text(encoding="utf-8")

replacements = {
    """        if (NativeBridge.isFabric262Runtime()) {
            // Fabric/Mixin hooks are launch-time infrastructure. Do not invoke the legacy
            // Javassist/JVMTI transformer on 26.2. Frame initialization remains client
            // functionality and is kept.
            EventRenderWorldPassExecutorDrain.EXECUTOR.execute(ClientSettings::initializeFrames);
            return;
        }
""":
    """        if (NativeBridge.isFabric262Runtime()) {
            // The recovered renderer relies on injected OpenGL shaders.
            // Minecraft 26.2 (including Vulkan) uses a native Screen instead.
            // Retain the original module implementations and Fabric/Mixin hooks.
            return;
        }
""",

    """    public void initializeRender() {
        if (this.renderInitialized) {
""":
    """    public void initializeRender() {
        if (gg.vape.runtime.FabricMigrationRuntime.isActive()) {
            // The old OpenGL shader pipeline is unsupported on Vulkan.
            // Fabric-native GUI controls the same recovered module instances.
            return;
        }
        if (this.renderInitialized) {
"""
}
for old, new in replacements.items():
    occurrences = source.count(old)
    if occurrences != 1:
        raise SystemExit(
            f"Expected exactly one recovered startup block, got {occurrences}: {old!r}"
        )
    source = source.replace(old, new, 1)

vape.write_text(source, encoding="utf-8")
print("Fabric startup patched: no injected transformer or legacy OpenGL GUI")
