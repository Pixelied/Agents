package dev.pixelied.survival.inventory;

import dev.pixelied.survival.damage.EffectInstanceSnapshot;

import java.util.List;
import java.util.Objects;

public record ConsumableSurvivalSnapshot(
    int consumeTicks,
    boolean usable,
    List<EffectInstanceSnapshot> guaranteedEffects
) {
    public ConsumableSurvivalSnapshot {
        if (consumeTicks < 0) throw new IllegalArgumentException("consumeTicks must be non-negative");
        guaranteedEffects = List.copyOf(Objects.requireNonNull(guaranteedEffects, "guaranteedEffects"));
    }
}
