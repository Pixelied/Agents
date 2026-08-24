package dev.adrien.crystaloptimizer.client.execution;

import dev.adrien.crystaloptimizer.timing.ServerTimingModel;
import dev.adrien.crystaloptimizer.timing.TimingEstimate;

public final class InteractionTimingRecorder {
    private static final InteractionTimingRecorder INSTANCE = new InteractionTimingRecorder(new ServerTimingModel(64));

    private final ServerTimingModel timingModel;

    public InteractionTimingRecorder(ServerTimingModel timingModel) {
        this.timingModel = java.util.Objects.requireNonNull(timingModel, "timingModel");
    }

    public static InteractionTimingRecorder instance() {
        return INSTANCE;
    }

    public void recordSend(int sequence, long nanos) {
        timingModel.recordSend(sequence, nanos);
    }

    public void recordAck(int sequence, long nanos) {
        timingModel.recordAck(sequence, nanos);
    }

    public TimingEstimate estimateBurst(long nowNanos, int actionCount) {
        return timingModel.estimateBurst(nowNanos, actionCount);
    }

    public ServerTimingModel timingModel() {
        return timingModel;
    }
}
