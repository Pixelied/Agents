package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class SuffocationPredictor extends PeriodicDamagePredictor {
    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        boolean inWall = booleanState(context, "in_wall");
        boolean cramming = booleanState(context, "cramming");
        if (!inWall && !cramming) return List.of();

        List<ThreatEvent> events = new ArrayList<>();
        for (long tick = 1; tick <= horizon(context); tick++) {
            if (inWall) {
                events.add(event(
                    "env:in_wall:" + tick,
                    tick,
                    1f,
                    "minecraft:in_wall",
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                    0f,
                    Confidence.POTENTIAL
                ));
            }
            if (cramming) {
                events.add(event(
                    "env:cramming:" + tick,
                    tick,
                    6f,
                    "minecraft:cramming",
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                    0f,
                    Confidence.POTENTIAL
                ));
            }
        }
        return List.copyOf(events);
    }
}
