package dev.adrien.spearclient.network;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.phys.Vec3;

public class PacketSender {
    private final ServerStateTracker tracker;

    public PacketSender() {
        this(ServerStateTracker.shared());
    }

    public PacketSender(ServerStateTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    public void move(Vec3 pos, boolean onGround, boolean horizontalCollision) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        connection.send(new ServerboundMovePlayerPacket.Pos(pos, onGround, horizontalCollision));
        tracker.onMovementPacket(pos);
    }

    public void moveAndRotate(
        Vec3 pos,
        float yaw,
        float pitch,
        boolean onGround,
        boolean horizontalCollision
    ) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        connection.send(new ServerboundMovePlayerPacket.PosRot(
            pos, yaw, pitch, onGround, horizontalCollision
        ));
        tracker.onMovementPacket(pos);
    }

    public void rotate(float yaw, float pitch, boolean onGround, boolean horizontalCollision) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        connection.send(new ServerboundMovePlayerPacket.Rot(
            yaw, pitch, onGround, horizontalCollision
        ));
    }

    public void stab() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.STAB,
            BlockPos.ZERO,
            Direction.DOWN
        ));
    }
}
