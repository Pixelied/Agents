package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class StatusEffectPredictor extends PeriodicDamagePredictor {
    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        List<ThreatEvent> events = new ArrayList<>();
        context.player().statusEffects().effect("minecraft:poison")
            .ifPresent(effect -> addEffectTicks(context, effect, true, events));
        context.player().statusEffects().effect("minecraft:wither")
            .ifPresent(effect -> addEffectTicks(context, effect, false, events));
        return List.copyOf(events);
    }

    private static void addEffectTicks(
        PredictionContext context,
        EffectInstanceSnapshot effect,
        boolean poison,
        List<ThreatEvent> output
    ) {
        int duration = effect.durationTicks();
        int intervalBase = poison ? 25 : 40;
        int interval = effect.amplifier() >= 31 ? 0 : intervalBase >> Math.max(0, effect.amplifier());
        long horizon = Math.min(horizon(context), duration);

        for (long tick = 1; tick <= horizon; tick++) {
            int remainingDuration = duration - (int) tick;
            boolean applies = interval <= 0 || remainingDuration % interval == 0;
            if (!applies) continue;

            if (poison) {
                output.add(event(
                    "env:poison:" + tick,
                    tick,
                    1f,
                    "minecraft:magic",
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                    1f,
                    Confidence.EXACT
                ));
            } else {
                output.add(event(
                    "env:wither:" + tick,
                    tick,
                    1f,
                    "minecraft:wither",
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                    0f,
                    Confidence.EXACT
                ));
            }
        }
    }
}
