package studio.pixelied.pearlcatch.core;

/** Conservative estimate of how far ahead the server-side pearl can be when a follow-up use arrives. */
public record ServerTimingWindow(boolean supported, int minLeadTicks, int maxLeadTicks) {
    private static final double MILLIS_PER_TICK = 50.0;
    private static final int MAX_SUPPORTED_RTT_MS = 1000;

    public static ServerTimingWindow fromRoundTripLatencyMs(int latencyMs) {
        if (latencyMs < 0 || latencyMs > MAX_SUPPORTED_RTT_MS) {
            return new ServerTimingWindow(false, 0, 0);
        }
        int min = (int)Math.ceil(latencyMs / MILLIS_PER_TICK);
        return new ServerTimingWindow(true, min, min + 1);
    }
}
