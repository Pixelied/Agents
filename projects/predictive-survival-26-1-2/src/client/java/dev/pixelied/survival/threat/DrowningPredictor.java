package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class DrowningPredictor extends PeriodicDamagePredictor {
    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (!booleanState(context, "eye_in_water")
            || booleanState(context, "eye_in_bubble_column")
            || booleanState(context, "can_breathe_underwater")
            || context.player().abilityInvulnerable()) {
            return List.of();
        }

        int air = intState(context, "air_supply", 300);
        double oxygenBonus = Math.max(0d, doubleState(context, "oxygen_bonus", 0d));
        Confidence confidence = oxygenBonus > 0d ? Confidence.BOUNDED : Confidence.EXACT;
        List<ThreatEvent> events = new ArrayList<>();

        for (long tick = 1; tick <= horizon(context); tick++) {
            // Survival planning uses the worst client-observable outcome for Respiration:
            // every tick may consume air. With zero bonus this is exact vanilla behavior.
            air--;
            if (air <= -20) {
                air = 0;
                events.add(event(
                    "env:drown:" + tick,
                    tick,
                    2f,
                    "minecraft:drown",
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD, DamageFlag.IS_DROWNING),
                    0f,
                    confidence
                ));
            }
        }
        return List.copyOf(events);
    }
}
