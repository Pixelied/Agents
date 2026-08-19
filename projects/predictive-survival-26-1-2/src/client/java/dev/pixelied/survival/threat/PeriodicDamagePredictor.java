package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public abstract class PeriodicDamagePredictor implements ThreatPredictor {
    protected static long horizon(PredictionContext context) {
        return context.limits().maxDecisionHistory();
    }

    protected static ThreatEvent event(
        String id,
        long tick,
        float rawDamage,
        String sourceKey,
        Set<DamageFlag> flags,
        float applicationHealthThresholdExclusive,
        Confidence confidence
    ) {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            EnumSet.copyOf(flags),
            false,
            1f,
            false,
            Optional.empty(),
            sourceKey,
            applicationHealthThresholdExclusive
        );
        return new ThreatEvent(
            id,
            ThreatKind.ENVIRONMENT,
            new TickWindow(tick, tick),
            source,
            confidence,
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true,
            false
        );
    }

    protected static String state(PredictionContext context, String key) {
        return context.player().state(key);
    }

    protected static boolean booleanState(PredictionContext context, String key) {
        return Boolean.parseBoolean(state(context, key));
    }

    protected static int intState(PredictionContext context, String key, int fallback) {
        String value = state(context, key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    protected static long longState(PredictionContext context, String key, long fallback) {
        String value = state(context, key);
        if (value == null) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    protected static double doubleState(PredictionContext context, String key, double fallback) {
        String value = state(context, key);
        if (value == null) return fallback;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
