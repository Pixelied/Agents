# Task 7 Brief — Conservative server timing and action deadlines

Add the pure timing model used to decide whether an otherwise-valid survival action can complete on the server before a threat.

## Locked interfaces

```java
public final class ServerTimingEstimator {
    public void observeRttMillis(int rttMillis);
    public void observeClientTickNanos(long nanos);
    public TimingSnapshot snapshot(long clientTick);
}

public record TimingSnapshot(
    long clientTick,
    double rttMs,
    double jitterMs,
    TickWindow nextPacketProcessingWindow
) {
    public boolean canCompleteBefore(long requiredServerTicks, TickWindow impact);
    public Deadline deadline(long requiredServerTicks);
}

public record Deadline(TickWindow completionWindow) {
    public boolean completesBefore(TickWindow impact);
}
```

## Semantics

- Keep a short bounded rolling window of RTT and client-tick-duration samples.
- Never claim exact one-way latency from RTT. Estimate a conservative packet-processing window using half-RTT plus jitter and at least one server scheduling tick of uncertainty.
- `canCompleteBefore(requiredServerTicks, impact)` uses the **latest** possible packet-processing tick plus required server ticks and must complete strictly before or at the threat's earliest plausible impact tick.
- Negative RTT, non-positive tick duration, negative required ticks, or an impact window before the snapshot must be rejected.
- With no samples, use conservative defaults rather than an optimistic zero-latency window.
- Jitter must widen the upper packet-processing bound.

## RED tests

1. `TimingSnapshot(100, ..., arrivalWindow=102..103)` with 5 required server ticks cannot complete before impact 106, but can before impact 109..110.
2. Higher RTT jitter produces a later conservative `nextPacketProcessingWindow.latest()` than low jitter at the same mean RTT.
3. The estimator keeps only a bounded number of samples and old outliers eventually roll out.
4. A 5-tick shield warmup uses the same deadline mechanism; there is no shield-specific timing shortcut.
5. Invalid negative inputs are rejected.

Use TDD: tests first, verify timing classes are missing, then implement and run full CI.
