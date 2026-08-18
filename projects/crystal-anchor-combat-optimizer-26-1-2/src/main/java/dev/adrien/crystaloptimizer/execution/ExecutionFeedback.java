package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;

public record ExecutionFeedback(Status status, int waitTicks) {
    public ExecutionFeedback {
        Objects.requireNonNull(status, "status");
        if (waitTicks < 0) {
            throw new IllegalArgumentException("waitTicks must be non-negative");
        }
        if (status == Status.WAITING && waitTicks == 0) {
            throw new IllegalArgumentException("waiting feedback requires at least one tick");
        }
        if (status != Status.WAITING && waitTicks != 0) {
            throw new IllegalArgumentException("only waiting feedback may carry wait ticks");
        }
    }

    public static ExecutionFeedback sent() {
        return new ExecutionFeedback(Status.SENT, 0);
    }

    public static ExecutionFeedback deferred() {
        return new ExecutionFeedback(Status.DEFERRED, 0);
    }

    public static ExecutionFeedback waiting(int ticks) {
        return new ExecutionFeedback(Status.WAITING, ticks);
    }

    public static ExecutionFeedback failed() {
        return new ExecutionFeedback(Status.FAILED, 0);
    }

    public enum Status {
        SENT,
        DEFERRED,
        WAITING,
        FAILED
    }
}
