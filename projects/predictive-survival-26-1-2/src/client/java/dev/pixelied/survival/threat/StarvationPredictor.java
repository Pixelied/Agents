package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class StarvationPredictor extends PeriodicDamagePredictor {
    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (intState(context, "food_level", 20) > 0) return List.of();

        float threshold = threshold(context.player().difficulty());
        if (context.player().health() <= threshold) return List.of();

        int timer = Math.max(0, intState(context, "food_tick_timer", 0));
        List<ThreatEvent> events = new ArrayList<>();
        for (long tick = 1; tick <= horizon(context); tick++) {
            timer++;
            if (timer >= 80) {
                timer = 0;
                events.add(event(
                    "env:starve:" + tick,
                    tick,
                    1f,
                    "minecraft:starve",
                    EnumSet.of(
                        DamageFlag.BYPASSES_ARMOR,
                        DamageFlag.BYPASSES_SHIELD,
                        DamageFlag.BYPASSES_EFFECTS
                    ),
                    threshold,
                    Confidence.EXACT
                ));
            }
        }
        return List.copyOf(events);
    }

    private static float threshold(DifficultySnapshot difficulty) {
        return switch (difficulty) {
            case HARD -> 0f;
            case NORMAL -> 1f;
            case EASY, PEACEFUL -> 10f;
        };
    }
}
