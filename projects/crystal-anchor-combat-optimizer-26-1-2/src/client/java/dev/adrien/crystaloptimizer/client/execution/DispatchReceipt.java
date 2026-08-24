package dev.adrien.crystaloptimizer.client.execution;

public record DispatchReceipt(Status status, String detail, int waitTicks) {
    public DispatchReceipt {
        if (status == null) {
            throw new NullPointerException("status");
        }
        detail = detail == null ? "" : detail;
        if (waitTicks < 0) {
            throw new IllegalArgumentException("waitTicks must be non-negative");
        }
    }

    public static DispatchReceipt sent(String detail) {
        return new DispatchReceipt(Status.SENT, detail, 0);
    }

    public static DispatchReceipt deferred(String detail) {
        return new DispatchReceipt(Status.DEFERRED, detail, 0);
    }

    public static DispatchReceipt waiting(int ticks) {
        return new DispatchReceipt(Status.WAITING, "wait", ticks);
    }

    public static DispatchReceipt failed(String detail) {
        return new DispatchReceipt(Status.FAILED, detail, 0);
    }

    public boolean accepted() {
        return status != Status.FAILED;
    }

    public enum Status {
        SENT,
        DEFERRED,
        WAITING,
        FAILED
    }
}
