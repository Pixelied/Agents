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

# The historical CSV ends in a newline, and split("\\n") produces a final
# empty record. ItemMappingEntry.parse assumes two CSV columns, so this
# previously aborted initializeManagers before profiles were created.
mapping_path = root / "gg/vape/module/utility/inventory/ItemStackSemanticResolver.java"
mapping_source = mapping_path.read_text(encoding="utf-8")
old_mapping = """            String mappingLine = line.trim();
            ItemMappingEntry itemMappingEntry = ItemMappingEntry.parse(mappingLine);
"""
new_mapping = """            String mappingLine = line.trim();
            if (mappingLine.isEmpty() || mappingLine.startsWith("#")) {
                continue;
            }
            ItemMappingEntry itemMappingEntry = ItemMappingEntry.parse(mappingLine);
"""
if mapping_source.count(old_mapping) != 1:
    raise SystemExit("Expected exactly one universal item mapping loop")
mapping_path.write_text(mapping_source.replace(old_mapping, new_mapping, 1), encoding="utf-8")
print("Fabric patched: ignore trailing empty universal_items.csv records")
