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
            .ifPresent(effect -> addEffectTicks(context, effect, true, 0L, events));
        context.player().statusEffects().effect("minecraft:wither")
            .ifPresent(effect -> addEffectTicks(context, effect, false, 0L, events));
        return List.copyOf(events);
    }

    private static void addEffectTicks(
        PredictionContext context,
        EffectInstanceSnapshot effect,
        boolean poison,
        long offset,
        List<ThreatEvent> output
    ) {
        long totalHorizon = horizon(context);
        if (offset >= totalHorizon) return;

        int intervalBase = poison ? 25 : 40;
        int interval = effect.amplifier() >= 31 ? 0 : intervalBase >> Math.max(0, effect.amplifier());
        if (effect.infiniteDuration()) {
            addInfiniteEffectTicks(context, poison, interval, offset, output);
            return;
        }

        int duration = effect.durationTicks();
        long relativeHorizon = Math.min(totalHorizon - offset, duration);
        for (long tick = 1; tick <= relativeHorizon; tick++) {
            int remainingDuration = duration - (int) tick;
            boolean applies = interval <= 0 || remainingDuration % interval == 0;
            if (!applies) continue;

            long absoluteTick = offset + tick;
            if (poison) {
                output.add(event(
                    "env:poison:" + absoluteTick,
                    absoluteTick,
                    1f,
                    "minecraft:magic",
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                    1f,
                    Confidence.EXACT
                ));
            } else {
                output.add(event(
                    "env:wither:" + absoluteTick,
                    absoluteTick,
                    1f,
                    "minecraft:wither",
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                    0f,
                    Confidence.EXACT
                ));
            }
        }

        if (duration > totalHorizon - offset || effect.hiddenEffect().isEmpty()) return;

        // MobEffectInstance.tickDownDuration recursively decrements hidden effects while the
        // stronger visible effect is active. Once the visible duration reaches zero, vanilla
        // promotes the already-aged hidden instance. Preserve exactly that concurrent aging before
        // continuing the established remaining-duration phase calculation for the promoted effect.
        EffectInstanceSnapshot hidden = ageWhileHidden(effect.hiddenEffect().get(), duration);
        if (hidden.infiniteDuration() || hidden.durationTicks() > 0) {
            addEffectTicks(context, hidden, poison, offset + duration, output);
        }
    }

    private static EffectInstanceSnapshot ageWhileHidden(EffectInstanceSnapshot effect, int elapsedTicks) {
        int duration = effect.infiniteDuration()
            ? -1
            : Math.max(0, effect.durationTicks() - elapsedTicks);
        return new EffectInstanceSnapshot(
            effect.effectKey(),
            duration,
            effect.amplifier(),
            effect.hiddenEffect().map(hidden -> ageWhileHidden(hidden, elapsedTicks))
        );
    }

    private static void addInfiniteEffectTicks(
        PredictionContext context,
        boolean poison,
        int interval,
        long offset,
        List<ThreatEvent> output
    ) {
        long totalHorizon = horizon(context);
        long remainingHorizon = totalHorizon - offset;
        String idPrefix = poison ? "env:poison:infinite:" : "env:wither:infinite:";
        String sourceKey = poison ? "minecraft:magic" : "minecraft:wither";
        float healthFloor = poison ? 1f : 0f;

        if (interval <= 0) {
            for (long tick = 1; tick <= remainingHorizon; tick++) {
                long absoluteTick = offset + tick;
                output.add(event(
                    idPrefix + absoluteTick,
                    absoluteTick,
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
        for (long start = 1; start <= remainingHorizon; start += interval) {
            long naturalEnd = start + interval - 1L;
            long end = Math.min(remainingHorizon, naturalEnd);
            boolean fullCadenceWindow = end == naturalEnd;
            DamageRange damage = fullCadenceWindow ? DamageRange.exact(1f) : new DamageRange(0f, 1f);
            output.add(windowEvent(
                idPrefix + offset + ":" + application++,
                new TickWindow(offset + start, offset + end),
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
