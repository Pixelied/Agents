package dev.adrien.spearclient.network;

import net.minecraft.world.phys.Vec3;

public final class ServerStateTracker {
    private long sequenceId = -1L;
    private Vec3 origin;
    private boolean active;
    private boolean corrected;
    private int correctionCount;
    private int movementPacketsSent;
    private Vec3 lastRequestedPosition;
    private Vec3 lastCorrectedPosition;
    private String phase = "IDLE";
    private int targetId = -1;

    public void beginSequence(long sequenceId, Vec3 origin) {
        this.sequenceId = sequenceId;
        this.origin = origin;
        this.active = true;
        this.corrected = false;
        this.correctionCount = 0;
        this.movementPacketsSent = 0;
        this.lastRequestedPosition = null;
        this.lastCorrectedPosition = null;
        this.phase = "PREPARE";
        this.targetId = -1;
    }

    public void onMovementPacket(Vec3 requestedPosition) {
        if (!active) {
            return;
        }
        movementPacketsSent++;
        lastRequestedPosition = requestedPosition;
    }

    public void onCorrection(Vec3 correctedPosition) {
        if (!active) {
            return;
        }
        corrected = true;
        correctionCount++;
        lastCorrectedPosition = correctedPosition;
    }

    public Snapshot snapshot() {
        return new Snapshot(
            sequenceId,
            origin,
            active,
            corrected,
            correctionCount,
            movementPacketsSent,
            phase,
            targetId,
            lastRequestedPosition,
            lastCorrectedPosition
        );
    }

    public record Snapshot(
        long sequenceId,
        Vec3 origin,
        boolean active,
        boolean corrected,
        int correctionCount,
        int movementPacketsSent,
        String phase,
        int targetId,
        Vec3 lastRequestedPosition,
        Vec3 lastCorrectedPosition
    ) {}
}
