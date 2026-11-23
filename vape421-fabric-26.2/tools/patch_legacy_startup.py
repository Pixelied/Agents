#!/usr/bin/env python3
"""Retire old injected startup paths while retaining the original module code.

Invoked only after verifying/recovering the exact upstream source revision and
applying the existing Fabric migration overlay. Do not edit the pinned original.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_legacy_startup.py <recovered src/main/java>")

root = Path(sys.argv[1])
vape = root / "gg/vape/Vape.java"
source = vape.read_text(encoding="utf-8")

replacements = {
    "    private void initPrimaryMappingTasks() {\n":
    """    private void initPrimaryMappingTasks() {
        if (gg.vape.runtime.FabricMigrationRuntime.isActive()) {
            // Fabric's Mixins and lifecycle adapters replace the old runtime
            // bytecode injection tasks. Crucially, the old task set queues
            // an OpenGL frame initializer and blocks the render thread.
            // That is not a valid way to initialize the 26.2 Vulkan GUI.
            this.primaryMappingTaskSet = new PrimaryMappingTaskSet();
            return;
        }
""",

    """    public void initClientListeners() {
        this.primaryMappingTaskSet.X();
        this.primaryMappingTaskSet.d();
""":
    """    public void initClientListeners() {
        if (!gg.vape.runtime.FabricMigrationRuntime.isActive()) {
            this.primaryMappingTaskSet.X();
            this.primaryMappingTaskSet.d();
        }
""",

    """    public void initializeRender() {
        if (this.renderInitialized) {
""":
    """    public void initializeRender() {
        if (gg.vape.runtime.FabricMigrationRuntime.isActive()) {
            // The old standalone OpenGL shader pipeline cannot run on
            // Minecraft 26.2's Vulkan backend. Use the native Fabric screen.
            return;
        }
        if (this.renderInitialized) {
"""
}
for old, new in replacements.items():
    occurrences = source.count(old)
    if occurrences != 1:
        raise SystemExit(f"Expected exactly one legacy startup block, got {occurrences}: {old!r}")
    source = source.replace(old, new, 1)
vape.write_text(source, encoding="utf-8")
print("Disabled legacy Javassist injection and OpenGL GUI initialization for Fabric")
