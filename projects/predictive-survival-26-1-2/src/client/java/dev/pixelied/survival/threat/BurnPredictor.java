package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class BurnPredictor extends PeriodicDamagePredictor {
    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        List<ThreatEvent> events = new ArrayList<>();
        if (booleanState(context, "fire_immune")) return List.of();

        long horizon = horizon(context);
        boolean inLava = booleanState(context, "in_lava");
        if (inLava) {
            for (long tick = 1; tick <= horizon; tick++) {
                events.add(event(
                    "env:lava:" + tick,
                    tick,
                    4f,
                    "minecraft:lava",
                    EnumSet.of(DamageFlag.BYPASSES_SHIELD, DamageFlag.IS_FIRE),
                    0f,
                    Confidence.POTENTIAL
                ));
            }
        }

        int remaining = intState(context, "remaining_fire_ticks", 0);
        for (long tick = 1; tick <= horizon && remaining > 0; tick++, remaining--) {
            if (!inLava && remaining % 20 == 0) {
                events.add(event(
                    "env:on_fire:" + tick,
                    tick,
                    1f,
                    "minecraft:on_fire",
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD, DamageFlag.IS_FIRE),
                    0f,
                    Confidence.EXACT
                ));
            }
        }
        return List.copyOf(events);
    }
}
