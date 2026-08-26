package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class ThreatOverflowCondenser {
    private ThreatOverflowCondenser() {
    }

    public static List<ThreatEvent> cap(List<ThreatEvent> ordered, int maxThreats, String overflowId) {
        if (ordered.size() <= maxThreats) return List.copyOf(ordered);
        if (maxThreats <= 0) throw new IllegalArgumentException("maxThreats must be positive");

        int retainedCount = maxThreats - 1;
        List<ThreatEvent> result = new ArrayList<>(maxThreats);
        for (int i = 0; i < retainedCount; i++) result.add(ordered.get(i));
        result.add(overflowEvent(ordered.subList(retainedCount, ordered.size()), overflowId));
        return List.copyOf(result);
    }

    private static ThreatEvent overflowEvent(List<ThreatEvent> omitted, String overflowId) {
        long earliest = Long.MAX_VALUE;
        long latest = 0L;
        for (ThreatEvent event : omitted) {
            earliest = Math.min(earliest, event.impact().earliest());
            latest = Math.max(latest, event.impact().latest());
        }

        EnumSet<DamageFlag> flags = EnumSet.of(
            DamageFlag.BYPASSES_INVULNERABILITY,
            DamageFlag.BYPASSES_COOLDOWN,
            DamageFlag.BYPASSES_ARMOR,
            DamageFlag.BYPASSES_SHIELD,
            DamageFlag.BYPASSES_EFFECTS,
            DamageFlag.BYPASSES_RESISTANCE,
            DamageFlag.BYPASSES_ENCHANTMENTS
        );

        DamageSourceSnapshot source = new DamageSourceSnapshot(
            new DamageRange(0f, Float.MAX_VALUE),
            flags,
            false,
            1f,
            false,
            Optional.empty(),
            "predictive_survival:threat_overflow"
        );
        return new ThreatEvent(
            overflowId,
            ThreatKind.OTHER,
            new TickWindow(earliest, latest),
            source,
            Confidence.UNKNOWN,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            true
        );
    }
}
