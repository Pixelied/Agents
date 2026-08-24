package studio.pixelied.pearlcatch;

import studio.pixelied.pearlcatch.core.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.Item;

/**
 * Applies a solved rotation only around Minecraft's real startUseItem() call.
 * Legit mode still reaches startUseItem through the configured Use key mapping; this bridge exists only so
 * Silent rotation does not require a standalone movement/look packet or a visible one-tick camera snap.
 */
public final class LegitSilentUseBridge {
    private static Armed armed;
    private static Saved saved;

    private LegitSilentUseBridge() {}

    public static void arm(Rotation rotation, PearlCatchConfig.RotationMode mode, Item expectedMainHandItem, boolean restoreServerAfterUse) {
        if (rotation == null || mode == PearlCatchConfig.RotationMode.CURRENT_CAMERA) {
            armed = null;
            saved = null;
            return;
        }
        armed = new Armed(rotation, mode, expectedMainHandItem, restoreServerAfterUse);
        saved = null;
    }

    public static void cancel() {
        armed = null;
        saved = null;
    }

    public static boolean beforeVanillaUse(Minecraft minecraft) {
        Armed request = armed;
        if (request == null) return true;
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            cancel();
            return false;
        }
        if (request.expectedMainHandItem() != null && player.getMainHandItem().getItem() != request.expectedMainHandItem()) {
            // A user/newer input changed the slot between queueing and vanilla key consumption.
            // This Use click belongs to Pearl Catcher, so cancel it instead of right-clicking an unrelated item.
            cancel();
            return false;
        }

        saved = new Saved(player.getYRot(), player.getXRot(), request.mode());
        player.setYRot((float) request.rotation().yaw());
        player.setXRot((float) request.rotation().pitch());
        return true;
    }

    public static void afterVanillaUse(Minecraft minecraft) {
        Armed request = armed;
        Saved restore = saved;
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (request == null || player == null) return;

        if (restore != null && restore.mode() == PearlCatchConfig.RotationMode.SILENT_PACKET) {
            player.setYRot(restore.yaw());
            player.setXRot(restore.pitch());
            if (request.restoreServerAfterUse() && player.connection != null) {
                player.connection.send(new ServerboundMovePlayerPacket.Rot(
                        restore.yaw(), restore.pitch(), player.onGround(), player.horizontalCollision));
            }
        }
        armed = null;
        saved = null;
    }

    public static boolean armed() {
        return armed != null;
    }

    private record Armed(Rotation rotation, PearlCatchConfig.RotationMode mode, Item expectedMainHandItem, boolean restoreServerAfterUse) {}
    private record Saved(float yaw, float pitch, PearlCatchConfig.RotationMode mode) {}
}
