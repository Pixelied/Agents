package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class FreezePredictor extends PeriodicDamagePredictor {
    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (!booleanState(context, "fully_frozen") || !booleanState(context, "can_freeze")) return List.of();

        long currentTickCount = longState(context, "tick_count", 0L);
        List<ThreatEvent> events = new ArrayList<>();
        for (long tick = 1; tick <= horizon(context); tick++) {
            if (Math.floorMod(currentTickCount + tick, 40L) == 0L) {
                events.add(event(
                    "env:freeze:" + tick,
                    tick,
                    1f,
                    "minecraft:freeze",
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD, DamageFlag.IS_FREEZING),
                    0f,
                    Confidence.POTENTIAL
                ));
            }
        }
        return List.copyOf(events);
    }
}
