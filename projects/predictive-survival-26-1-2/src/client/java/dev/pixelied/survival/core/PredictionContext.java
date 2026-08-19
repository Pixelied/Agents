package dev.pixelied.survival.core;

import dev.pixelied.survival.timing.TimingSnapshot;

import java.util.Objects;

public record PredictionContext(
    PlayerSnapshot player,
    WorldSnapshot world,
    TimingSnapshot timing,
    EngineLimits limits
) {
    public PredictionContext {
        player = Objects.requireNonNull(player, "player");
        world = Objects.requireNonNull(world, "world");
        timing = Objects.requireNonNull(timing, "timing");
        limits = Objects.requireNonNull(limits, "limits");
    }
}
