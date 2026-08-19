package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class ThreatCapReducer {
    private ThreatCapReducer() {
    }

    static List<ThreatEvent> reduce(
        PredictionContext context,
        List<ThreatEvent> orderedEvents,
        String overflowId
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(orderedEvents, "orderedEvents");
        Objects.requireNonNull(overflowId, "overflowId");

        int cap = context.limits().maxThreats();
        if (orderedEvents.size() <= cap) return List.copyOf(orderedEvents);

        int retainedCount = Math.max(0, cap - 1);
        List<ThreatEvent> result = new ArrayList<>(cap);
        result.addAll(orderedEvents.subList(0, retainedCount));
        result.add(collapseOverflow(context, orderedEvents.subList(retainedCount, orderedEvents.size()), overflowId));
        return List.copyOf(result);
    }

    private static ThreatEvent collapseOverflow(
        PredictionContext context,
        List<ThreatEvent> overflow,
        String overflowId
    ) {
        long earliest = Long.MAX_VALUE;
        long latest = 0L;
        float conservativeRaw = 0f;
        boolean canDisableBlocking = false;

        for (ThreatEvent event : overflow) {
            Objects.requireNonNull(event, "threat event");
            earliest = Math.min(earliest, event.impact().earliest());
            latest = Math.max(latest, event.impact().latest());
            conservativeRaw = saturatingAdd(conservativeRaw, conservativeRawUpper(context, event));
            canDisableBlocking |= event.canDisableBlocking();
        }

        EnumSet<DamageFlag> flags = EnumSet.of(
            DamageFlag.BYPASSES_INVULNERABILITY,
            DamageFlag.BYPASSES_COOLDOWN,
            DamageFlag.BYPASSES_ARMOR,
            DamageFlag.BYPASSES_EFFECTS,
            DamageFlag.BYPASSES_RESISTANCE,
            DamageFlag.BYPASSES_ENCHANTMENTS,
            DamageFlag.BYPASSES_SHIELD
        );
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(conservativeRaw),
            flags,
            false,
            1f,
            true,
            Optional.empty(),
            "predictive_survival:threat_cap_overflow"
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
            canDisableBlocking
        );
    }

    private static float conservativeRawUpper(PredictionContext context, ThreatEvent event) {
        double raw = event.damage().rawDamage().max();
        if (event.damage().scalesWithDifficulty()) {
            raw = switch (context.player().difficulty()) {
                case PEACEFUL -> 0d;
                case EASY -> Math.min(raw / 2d + 1d, raw);
                case NORMAL -> raw;
                case HARD -> raw * 1.5d;
            };
        }
        if (event.damage().has(DamageFlag.IS_FREEZING)) {
            raw *= Math.max(1d, event.damage().freezingMultiplier());
        }
        if (!Double.isFinite(raw) || raw >= Float.MAX_VALUE) return Float.MAX_VALUE;
        return Math.max(0f, (float) raw);
    }

    private static float saturatingAdd(float first, float second) {
        if (first >= Float.MAX_VALUE || second >= Float.MAX_VALUE) return Float.MAX_VALUE;
        double sum = (double) first + second;
        return sum >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) sum;
    }
}
