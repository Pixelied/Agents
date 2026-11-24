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


def rewrite(relative, old, replacement):
    source_file = root / relative
    original = source_file.read_text(encoding="utf-8")
    count = original.count(old)
    if count != 1:
        raise SystemExit(f"Expected one source block in {relative}, found {count}")
    source_file.write_text(original.replace(old, replacement, 1), encoding="utf-8")


# Profiles and favorites are data model state. Their old GUI refreshes are
# presentation-only side effects; that injected GUI is never constructed in
# the normal 26.2 Fabric path. Do not make profile initialization depend on
# non-existent click-GUI frames.
rewrite(
    "gg/vape/ui/click/frame/impl/VisibleModuleListFrame.java",
    "    public static void e() {\n",
    """    public static void e() {
        if (gg.vape.runtime.FabricMigrationRuntime.isActive()) return;
""",
)
rewrite(
    "gg/vape/ui/click/frame/impl/profile/ProfilesSettingsFrame.java",
    "    public static void refreshProfileList() {\n",
    """    public static void refreshProfileList() {
        if (gg.vape.runtime.FabricMigrationRuntime.isActive()) return;
""",
)
rewrite(
    "gg/vape/manager/client/ProfilesManager.java",
    """        ClientSettings.getFrame(ProfilesSettingsFrame.class).addProfile(profile);
        ProfilesSettingsFrame.refreshProfileList();""",
    """        if (!gg.vape.runtime.FabricMigrationRuntime.isActive()) {
            ClientSettings.getFrame(ProfilesSettingsFrame.class).addProfile(profile);
            ProfilesSettingsFrame.refreshProfileList();
        }""",
)
rewrite(
    "gg/vape/manager/client/ProfilesManager.java",
    "        ClientSettings.getFrame(ProfilesSettingsFrame.class).removeProfile(profile);",
    """        if (!gg.vape.runtime.FabricMigrationRuntime.isActive())
            ClientSettings.getFrame(ProfilesSettingsFrame.class).removeProfile(profile);""",
)

# The initial client lifecycle fires before all item registry components are
# bound on 26.2. The "missing mappings" scan is diagnostic only, and its
# ItemStack construction can fail before resource loading completes.
rewrite(
    "gg/vape/Vape.java",
    "            this.itemStackSemanticResolver.reportMissingMappings();",
    """            if (!FabricMigrationRuntime.isActive()) {
                this.itemStackSemanticResolver.reportMissingMappings();
            }""",
)

# The recovered native bridge logged initialization errors but swallowed
# them, leaving half-initialized managers and claiming the client started.
# Propagate errors to the Fabric launcher so it never enables input hooks
# against an incomplete client.
rewrite(
    "gg/vape/runtime/NativeBridge.java",
    """            catch (java.lang.reflect.InvocationTargetException wrapper) {
                NativeBridge.logThrowable(name, wrapper.getCause() != null ? wrapper.getCause() : wrapper);
            }
            catch (Throwable other) {
                NativeBridge.logThrowable(name, other);
            }
""",
    """            catch (java.lang.reflect.InvocationTargetException wrapper) {
                Throwable cause = wrapper.getCause() != null ? wrapper.getCause() : wrapper;
                NativeBridge.logThrowable(name, cause);
                if (FabricMigrationRuntime.isActive()) {
                    throw new IllegalStateException("Vape " + name + " failed", cause);
                }
            }
            catch (Throwable other) {
                NativeBridge.logThrowable(name, other);
                if (FabricMigrationRuntime.isActive()) {
                    throw new IllegalStateException("Vape " + name + " failed", other);
                }
            }
""",
)
print("Fabric patched: skip obsolete GUI view side effects; fail fast on incomplete bootstrap")

# Decompilation preserved the private helper lambda$acceptIncomingRequest$0.
# javac also generates its own synthetic lambda$acceptIncomingRequest$0 from
# the callback in acceptIncomingRequest(), producing two identical method
# signatures in the .class file. The JVM rejects that class with
# ClassFormatError. Rename only the explicit helper and its invocation.
friend_path = root / "gg/vape/friend/FriendRequestManager.java"
friend_src = friend_path.read_text(encoding="utf-8")
old_helper = "lambda$acceptIncomingRequest$0"
new_helper = "recoveredAcceptIncomingRequestCallback"
if friend_src.count(old_helper) != 2:
    raise SystemExit("Unexpected decompiled friend callback helper count")
friend_path.write_text(friend_src.replace(old_helper, new_helper), encoding="utf-8")
print("Fabric patched: removed duplicate synthetic friend callback signature")

# The recovered notifications are old fixed-function/OpenGL widgets and need
# the legacy font renderer, which is intentionally not created in the native
# 26.2/Vulkan GUI. They must not crash startup or module toggles.
rewrite(
    "gg/vape/notification/NotificationManager.java",
    """    public void show(String title, String message, NotificationType type, long durationMillis, boolean force) {
        this.enqueue(new Notification(type, title, new TextNotificationContent(message), 0.0, 0.0, durationMillis), force);
    }
""",
    """    public void show(String title, String message, NotificationType type, long durationMillis, boolean force) {
        if (gg.vape.runtime.FabricMigrationRuntime.isActive()) {
            return; // native Screen reports module state without the old overlay
        }
        this.enqueue(new Notification(type, title, new TextNotificationContent(message), 0.0, 0.0, durationMillis), force);
    }
""",
)
rewrite(
    "gg/vape/notification/NotificationManager.java",
    "    public void enqueue(INotification notification, boolean force) {\n",
    """    public void enqueue(INotification notification, boolean force) {
        if (gg.vape.runtime.FabricMigrationRuntime.isActive()) return;
""",
)
print("Fabric patched: avoid abandoned OpenGL notification/font renderer")
