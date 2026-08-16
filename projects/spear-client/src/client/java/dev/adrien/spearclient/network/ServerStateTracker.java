package dev.adrien.spearclient.network;

import net.minecraft.world.phys.Vec3;

public final class ServerStateTracker {
    private static final ServerStateTracker SHARED = new ServerStateTracker();

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
    private String kind = "NONE";
    private double expectedForwardKnownMovement = Double.NaN;
    private double predictedRawDamage = Double.NaN;
    private double predictedSourceModelReach = Double.NaN;

    public static ServerStateTracker shared() {
        return SHARED;
    }

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
        this.kind = "NONE";
        this.expectedForwardKnownMovement = Double.NaN;
        this.predictedRawDamage = Double.NaN;
        this.predictedSourceModelReach = Double.NaN;
    }

    public void setPhase(String phase) {
        if (active && phase != null) {
            this.phase = phase;
        }
    }

    public void setTargetId(int targetId) {
        if (active) {
            this.targetId = targetId;
        }
    }

    public void setSourceModelTelemetry(
        String kind,
        double expectedForwardKnownMovement,
        double predictedRawDamage,
        double predictedSourceModelReach
    ) {
        if (!active) {
            return;
        }
        this.kind = kind == null ? "NONE" : kind;
        this.expectedForwardKnownMovement = expectedForwardKnownMovement;
        this.predictedRawDamage = predictedRawDamage;
        this.predictedSourceModelReach = predictedSourceModelReach;
    }

    public void endSequence(String finalPhase) {
        if (!active) {
            return;
        }
        if (finalPhase != null) {
            this.phase = finalPhase;
        }
        this.active = false;
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
            lastCorrectedPosition,
            kind,
            expectedForwardKnownMovement,
            predictedRawDamage,
            predictedSourceModelReach
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
        Vec3 lastCorrectedPosition,
        String kind,
        double expectedForwardKnownMovement,
        double predictedRawDamage,
        double predictedSourceModelReach
    ) {}
}
