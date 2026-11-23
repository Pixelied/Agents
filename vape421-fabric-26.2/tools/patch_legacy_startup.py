#!/usr/bin/env python3
"""Retire only the injector-era OpenGL GUI initialization on Fabric 26.2.

The original module implementations and Fabric/Mixin client hooks remain
unchanged. The UI is presented by VapeFabricMenuScreen.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_legacy_startup.py <recovered src/main/java>")

root = Path(sys.argv[1])
source_path = root / "gg/vape/module/none/ClientSettings.java"
source = source_path.read_text(encoding="utf-8")
old = "    public static void initializeFrames() {\n"
new = """    public static void initializeFrames() {
        if (gg.vape.runtime.FabricMigrationRuntime.isActive()) {
            // Native Minecraft Screen replaces the old injected OpenGL
            // renderer on Fabric, including Minecraft 26.2 with Vulkan.
            framesInitialized = true;
            return;
        }
"""
if source.count(old) != 1:
    raise SystemExit("Expected ClientSettings.initializeFrames declaration exactly once")
source_path.write_text(source.replace(old, new, 1), encoding="utf-8")
print("Fabric patched: legacy OpenGL click-GUI initialization disabled")
