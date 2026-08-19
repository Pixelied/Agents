package dev.pixelied.survival.damage;

public record BlockingSnapshot(
    boolean usingBlockingItem,
    float blockedFraction,
    int elapsedUseTicks,
    int requiredUseTicks
) {
    public BlockingSnapshot {
        if (blockedFraction < 0f || blockedFraction > 1f || Float.isNaN(blockedFraction)) {
            throw new IllegalArgumentException("blockedFraction must be between 0 and 1");
        }
        if (elapsedUseTicks < 0 || requiredUseTicks < 0) {
            throw new IllegalArgumentException("use ticks must be non-negative");
        }
    }

    public static BlockingSnapshot none() {
        return new BlockingSnapshot(false, 0f, 0, 0);
    }

    public boolean active() {
        return usingBlockingItem && elapsedUseTicks >= requiredUseTicks;
    }
}
