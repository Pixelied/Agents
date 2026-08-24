package studio.pixelied.pearlcatch;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import studio.pixelied.pearlcatch.core.Rotation;

/**
 * Owns the synthetic vanilla-key protocol used by Legit mode.
 *
 * <p>Only one key click may be outstanding at a time. Every click has a finite confirmation deadline,
 * and every lease belongs to one catch attempt so cancellation cannot leak input into a later attempt.</p>
 */
final class VanillaInputExecutor {
    static final int LEGIT_CONFIRM_TIMEOUT_TICKS = 4;

    private LegitInputLease lease;

    boolean hasLease() { return lease != null; }
    boolean hasLease(long ownerAttemptId) {
        return lease != null && lease.ownerAttemptId() == ownerAttemptId;
    }
    long leaseOwnerAttemptId() {
        return lease == null ? -1L : lease.ownerAttemptId();
    }

    boolean queueHotbar(Minecraft mc, long clientTick, long ownerAttemptId, int slot) {
        if (slot < 0 || slot >= 9 || lease != null || screenBlocksInput(mc)) return false;
        KeyMapping mapping = mc.options.keyHotbarSlots[slot];
        if (!clickConfiguredKey(mapping)) return false;
        lease = new LegitInputLease(ownerAttemptId, LegitInputKind.HOTBAR,
                clientTick, clientTick + LEGIT_CONFIRM_TIMEOUT_TICKS, slot, null);
        return true;
    }

    boolean queueSwap(Minecraft mc, long clientTick, long ownerAttemptId, Item expectedMainItem, int selectedSlot) {
        if (lease != null || screenBlocksInput(mc)) return false;
        if (!clickConfiguredKey(mc.options.keySwapOffhand)) return false;
        lease = new LegitInputLease(ownerAttemptId, LegitInputKind.SWAP_OFFHAND,
                clientTick, clientTick + LEGIT_CONFIRM_TIMEOUT_TICKS, selectedSlot, expectedMainItem);
        return true;
    }

    QueueUseResult queueUse(
            Minecraft mc, LocalPlayer player, long clientTick, long ownerAttemptId,
            Item expectedMainItem, Rotation rotation, boolean restoreServerAfterUse,
            PearlCatchConfig.RotationMode rotationMode
    ) {
        if (lease != null || screenBlocksInput(mc)) return QueueUseResult.notQueued();
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.MISS) return QueueUseResult.notQueued();
        if (!mainHandMatchesExpected(player, expectedMainItem)) return QueueUseResult.notQueued();

        float restoreYaw = player.getYRot();
        float restorePitch = player.getXRot();
        if (rotationMode == PearlCatchConfig.RotationMode.VISIBLE_CAMERA) {
            player.setYRot((float) rotation.yaw());
            player.setXRot((float) rotation.pitch());
        }
        if (rotationMode != PearlCatchConfig.RotationMode.CURRENT_CAMERA) {
            LegitSilentUseBridge.arm(rotation, rotationMode, expectedMainItem, restoreServerAfterUse);
        }
        if (!clickConfiguredKey(mc.options.keyUse)) {
            LegitSilentUseBridge.cancel();
            if (rotationMode == PearlCatchConfig.RotationMode.VISIBLE_CAMERA) {
                player.setYRot(restoreYaw);
                player.setXRot(restorePitch);
            }
            return QueueUseResult.notQueued();
        }

        CameraRestoreRequest cameraRestore = rotationMode == PearlCatchConfig.RotationMode.VISIBLE_CAMERA
                ? new CameraRestoreRequest(clientTick + 1, restoreYaw, restorePitch) : null;
        lease = new LegitInputLease(ownerAttemptId, LegitInputKind.USE,
                clientTick, clientTick + LEGIT_CONFIRM_TIMEOUT_TICKS,
                player.getInventory().getSelectedSlot(), expectedMainItem);
        return new QueueUseResult(true, cameraRestore);
    }

    LeaseEvent settle(Minecraft mc, LocalPlayer player, long clientTick) {
        LegitInputLease current = lease;
        if (current == null || clientTick <= current.requestedClientTick()) return LeaseEvent.none();
        if (clientTick > current.deadlineClientTick()) {
            lease = null;
            if (current.kind() == LegitInputKind.USE) {
                LegitSilentUseBridge.cancel();
                return new LeaseEvent(LeaseEventKind.USE_EXPIRED, current.ownerAttemptId());
            }
            return LeaseEvent.none();
        }
        if (screenBlocksInput(mc)) return discardQueuedInputForScreen(mc, current);

        boolean complete = switch (current.kind()) {
            case HOTBAR -> player.getInventory().getSelectedSlot() == current.expectedSlot();
            case SWAP_OFFHAND -> current.expectedMainItem() != null
                    && mainHandMatchesExpected(player, current.expectedMainItem());
            case USE -> true;
        };
        if (!complete) return LeaseEvent.none();
        if (current.kind() == LegitInputKind.USE && LegitSilentUseBridge.armed()) {
            LegitSilentUseBridge.cancel();
        }
        lease = null;
        return LeaseEvent.none();
    }

    boolean cancelOwner(long ownerAttemptId) {
        if (lease == null || lease.ownerAttemptId() != ownerAttemptId) return false;
        if (lease.kind() == LegitInputKind.USE) LegitSilentUseBridge.cancel();
        lease = null;
        return true;
    }

    void cancelAll() {
        if (lease != null && lease.kind() == LegitInputKind.USE) LegitSilentUseBridge.cancel();
        lease = null;
    }

    private LeaseEvent discardQueuedInputForScreen(Minecraft mc, LegitInputLease current) {
        KeyMapping mapping = switch (current.kind()) {
            case HOTBAR -> current.expectedSlot() >= 0 && current.expectedSlot() < mc.options.keyHotbarSlots.length
                    ? mc.options.keyHotbarSlots[current.expectedSlot()] : null;
            case SWAP_OFFHAND -> mc.options.keySwapOffhand;
            case USE -> mc.options.keyUse;
        };
        boolean removedPendingSyntheticClick = mapping != null && mapping.consumeClick();
        if (current.kind() == LegitInputKind.USE) LegitSilentUseBridge.cancel();
        lease = null;
        if (removedPendingSyntheticClick && current.kind() == LegitInputKind.USE) {
            return new LeaseEvent(LeaseEventKind.USE_SCREEN_CANCELLED, current.ownerAttemptId());
        }
        return LeaseEvent.none();
    }

    private static boolean clickConfiguredKey(KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return false;
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
        KeyMapping.click(key);
        PearlCatchClient.drainSyntheticControlEchoes(key);
        return true;
    }

    private static boolean mainHandMatchesExpected(LocalPlayer player, Item item) {
        var stack = player.getMainHandItem();
        return item == Items.AIR ? stack.isEmpty() : (!stack.isEmpty() && stack.getItem() == item);
    }

    private static boolean screenBlocksInput(Minecraft mc) {
        return mc == null || mc.screen != null || mc.getOverlay() != null;
    }

    enum LeaseEventKind { NONE, USE_EXPIRED, USE_SCREEN_CANCELLED }
    record LeaseEvent(LeaseEventKind kind, long ownerAttemptId) {
        static LeaseEvent none() { return new LeaseEvent(LeaseEventKind.NONE, -1L); }
    }
    record CameraRestoreRequest(long restoreAtTick, float yaw, float pitch) {}
    record QueueUseResult(boolean queued, CameraRestoreRequest cameraRestore) {
        static QueueUseResult notQueued() { return new QueueUseResult(false, null); }
    }
    private enum LegitInputKind { HOTBAR, SWAP_OFFHAND, USE }
    private record LegitInputLease(
            long ownerAttemptId, LegitInputKind kind, long requestedClientTick,
            long deadlineClientTick, int expectedSlot, Item expectedMainItem) {}
}
