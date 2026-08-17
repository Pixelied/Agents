package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class WorldBorderPredictor extends PeriodicDamagePredictor {
    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        double signedDistance = doubleState(context, "border_distance_plus_safe_zone", Double.POSITIVE_INFINITY);
        if (!(signedDistance < 0d)) return List.of();
        double damagePerBlock = Math.max(0d, doubleState(context, "border_damage_per_block", 0.2d));
        double rawDouble = Math.max(1d, Math.floor(-signedDistance * damagePerBlock));
        float raw = !Double.isFinite(rawDouble) || rawDouble >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) rawDouble;

        List<ThreatEvent> events = new ArrayList<>();
        for (long tick = 1; tick <= horizon(context); tick++) {
            events.add(event(
                "env:outside_border:" + tick,
                tick,
                raw,
                "minecraft:outside_border",
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                0f,
                Confidence.POTENTIAL
            ));
        }
        return List.copyOf(events);
    }
}
