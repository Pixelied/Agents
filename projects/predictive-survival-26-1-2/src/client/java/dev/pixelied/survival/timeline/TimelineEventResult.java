package dev.pixelied.survival.timeline;

import dev.pixelied.survival.damage.DamageResult;

import java.util.Objects;

public record TimelineEventResult(
    ThreatEvent event,
    float preMitigationRaw,
    float finalDamage,
    DamageResult damageResult
) {
    public TimelineEventResult {
        event = Objects.requireNonNull(event, "event");
        damageResult = Objects.requireNonNull(damageResult, "damageResult");
    }
}
