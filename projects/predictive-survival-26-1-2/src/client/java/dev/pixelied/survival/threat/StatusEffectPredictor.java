package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

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
        int intervalBase = poison ? 25 : 40;
        int interval = effect.amplifier() >= 31 ? 0 : intervalBase >> Math.max(0, effect.amplifier());
        if (effect.infiniteDuration()) {
            addInfiniteEffectTicks(context, poison, interval, output);
            return;
        }

        int duration = effect.durationTicks();
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

    private static void addInfiniteEffectTicks(
        PredictionContext context,
        boolean poison,
        int interval,
        List<ThreatEvent> output
    ) {
        long horizon = horizon(context);
        String idPrefix = poison ? "env:poison:infinite:" : "env:wither:infinite:";
        String sourceKey = poison ? "minecraft:magic" : "minecraft:wither";
        float healthFloor = poison ? 1f : 0f;

        if (interval <= 0) {
            for (long tick = 1; tick <= horizon; tick++) {
                output.add(event(
                    idPrefix + tick,
                    tick,
                    1f,
                    sourceKey,
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                    healthFloor,
                    Confidence.EXACT
                ));
            }
            return;
        }

        int application = 0;
        for (long start = 1; start <= horizon; start += interval) {
            long naturalEnd = start + interval - 1L;
            long end = Math.min(horizon, naturalEnd);
            boolean fullCadenceWindow = end == naturalEnd;
            DamageRange damage = fullCadenceWindow ? DamageRange.exact(1f) : new DamageRange(0f, 1f);
            output.add(windowEvent(
                idPrefix + application++,
                new TickWindow(start, end),
                damage,
                sourceKey,
                healthFloor,
                fullCadenceWindow ? Confidence.BOUNDED : Confidence.POTENTIAL
            ));
        }
    }

    private static ThreatEvent windowEvent(
        String id,
        TickWindow impact,
        DamageRange damage,
        String sourceKey,
        float healthFloor,
        Confidence confidence
    ) {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            damage,
            EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
            false,
            1f,
            false,
            Optional.empty(),
            sourceKey,
            healthFloor
        );
        return new ThreatEvent(
            id,
            ThreatKind.ENVIRONMENT,
            impact,
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
}
