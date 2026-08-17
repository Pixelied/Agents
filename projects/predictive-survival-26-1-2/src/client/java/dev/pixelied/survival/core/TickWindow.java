package dev.pixelied.survival.core;

public record TickWindow(long earliest, long latest) {
    public TickWindow {
        if (earliest > latest) {
            throw new IllegalArgumentException("earliest must be <= latest");
        }
    }

    public boolean contains(long tick) {
        return tick >= earliest && tick <= latest;
    }

    public boolean overlaps(TickWindow other) {
        return earliest <= other.latest && other.earliest <= latest;
    }
}
