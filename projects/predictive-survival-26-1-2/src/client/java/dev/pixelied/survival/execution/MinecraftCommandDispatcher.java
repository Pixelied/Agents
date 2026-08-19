package dev.pixelied.survival.execution;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;

import java.util.Objects;

public final class MinecraftCommandDispatcher {
    public boolean dispatch(Minecraft minecraft, ExecutionCommand command) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(command, "command");
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null) return false;

        if (command instanceof ExecutionCommand.SelectHotbar select) {
            player.getInventory().setSelectedSlot(select.hotbarIndex());
            player.connection.send(new ServerboundSetCarriedItemPacket(select.hotbarIndex()));
            return true;
        }

        if (command instanceof ExecutionCommand.SwapMenuSlot swap) {
            if (player.containerMenu.containerId != swap.containerId()) return false;
            minecraft.gameMode.handleContainerInput(
                swap.containerId(),
                swap.sourceMenuSlot(),
                swap.button(),
                ContainerInput.SWAP,
                player
            );
            return true;
        }

        if (command instanceof ExecutionCommand.UseItem use) {
            minecraft.gameMode.useItem(player, hand(use.hand()));
            return true;
        }

        if (command instanceof ExecutionCommand.AimAndUseItem aim) {
            aimAt(player, aim.target().x(), aim.target().y(), aim.target().z());
            minecraft.gameMode.useItem(player, hand(aim.hand()));
            return true;
        }

        // Movement and block placement need a concrete legal path/support face. The pure planner may
        // score them, but the live dispatcher must not invent one and claim execution succeeded.
        return false;
    }

    private static InteractionHand hand(dev.pixelied.survival.planner.SurvivalAction.Hand hand) {
        return hand == dev.pixelied.survival.planner.SurvivalAction.Hand.OFF_HAND
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
    }

    private static void aimAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX();
        double dy = y - player.getEyeY();
        double dz = z - player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
    }
}
