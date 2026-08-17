package dev.pixelied.survival.timeline;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;

import java.util.Objects;
import java.util.Optional;

public record ThreatEvent(
    String id,
    ThreatKind kind,
    TickWindow impact,
    DamageSourceSnapshot damage,
    Confidence confidence,
    Optional<Vec3Snapshot> sourcePosition,
    Optional<Vec3Snapshot> impactPosition,
    boolean avoidable,
    boolean blockable,
    boolean relocatable,
    boolean canDisableBlocking
) {
    public ThreatEvent {
        id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        kind = Objects.requireNonNull(kind, "kind");
        impact = Objects.requireNonNull(impact, "impact");
        if (impact.earliest() < 0) throw new IllegalArgumentException("impact tick offsets must be non-negative");
        damage = Objects.requireNonNull(damage, "damage");
        confidence = Objects.requireNonNull(confidence, "confidence");
        sourcePosition = Objects.requireNonNull(sourcePosition, "sourcePosition");
        impactPosition = Objects.requireNonNull(impactPosition, "impactPosition");
    }
}
