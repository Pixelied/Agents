#!/usr/bin/env python3
"""Replace only legacy OpenGL frame/render initialization after source recovery.

The original modules remain byte-for-byte except for the existing Fabric
migration overlay and these narrow, verified compatibility adjustments.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_legacy_startup.py <recovered src/main/java>")

source = Path(sys.argv[1]) / "gg/vape/Vape.java"
text = source.read_text(encoding="utf-8")

replacements = {
    """            // Fabric events and targeted Mixins instead. Frame construction is
            // ordinary client state and can be initialized directly here.
            ClientSettings.initializeFrames();
            return;
""":
    """            // Fabric events and targeted Mixins instead. The legacy frame UI
            // requires fixed-function OpenGL and breaks Vulkan initialization.
            // A native Minecraft Screen displays the recovered modules instead.
            return;
""",
    """    public void initializeRender() {
        if (this.renderInitialized) {
""":
    """    public void initializeRender() {
        if (FabricMigrationRuntime.isActive()) {
            // Native Minecraft Screen and Fabric HUD replace old OpenGL shaders.
            return;
        }
        if (this.renderInitialized) {
"""
}
for old, new in replacements.items():
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one recovered source block, found {count}: {old!r}")
    text = text.replace(old, new, 1)
source.write_text(text, encoding="utf-8")
print("Vape Fabric startup: legacy OpenGL frame construction bypassed")
