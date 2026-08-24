package dev.adrien.spearclient.network;

public final class MovementEnvelope {
    public static final double CONSERVATIVE_RADIUS = 9.0;

    private MovementEnvelope() {}

    public static double maxDistanceFromTickOrigin(
        boolean fallFlying,
        int packetIndex,
        double expectedVelocitySquared
    ) {
        int allowance = packetIndex >= 1 && packetIndex <= 5 ? packetIndex : 1;
        double metersPerTick = fallFlying ? 300.0 : 100.0;
        return Math.sqrt(expectedVelocitySquared + metersPerTick * allowance);
    }

    public static boolean isInsideConservativeBudget(double distanceFromTickOrigin) {
        return distanceFromTickOrigin <= CONSERVATIVE_RADIUS;
    }
}
