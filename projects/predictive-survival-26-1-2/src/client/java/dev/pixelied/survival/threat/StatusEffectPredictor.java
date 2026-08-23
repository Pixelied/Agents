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
        long totalHorizon = horizon(context);
        int interval = interval(poison, effect.amplifier());
        if (effect.infiniteDuration()) {
            addInfiniteEffectTicks(context, poison, interval, 0L, output);
            return;
        }

        int duration = effect.durationTicks();
        long visibleHorizon = Math.min(totalHorizon, duration);
        for (long tick = 1; tick <= visibleHorizon; tick++) {
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

        if (effect.hiddenTailUnknown() && effect.amplifier() > 0 && duration < totalHorizon) {
            // ClientboundUpdateMobEffectPacket in 26.1.2 does not serialize hiddenEffect. A live
            // amplifier-N effect can therefore conceal an arbitrarily longer lower-amplifier tail
            // that the client cannot distinguish until the server later sends the promotion update.
            // Amplifier 0 cannot hide a weaker effect, so only N>0 needs this fail-closed branch.
            addUnknownHiddenTail(
                context,
                poison,
                interval(poison, effect.amplifier() - 1),
                duration,
                output
            );
        }
    }

    private static void addUnknownHiddenTail(
        PredictionContext context,
        boolean poison,
        int interval,
        long visibleDuration,
        List<ThreatEvent> output
    ) {
        long remainingHorizon = horizon(context) - visibleDuration;
        if (remainingHorizon <= 0L) return;

        String idPrefix = poison ? "env:poison:hidden-unknown:" : "env:wither:hidden-unknown:";
        String sourceKey = poison ? "minecraft:magic" : "minecraft:wither";
        float healthFloor = poison ? 1f : 0f;

        if (interval <= 0) {
            for (long relativeTick = 1; relativeTick <= remainingHorizon; relativeTick++) {
                long absoluteTick = visibleDuration + relativeTick;
                output.add(windowEvent(
                    idPrefix + absoluteTick,
                    new TickWindow(absoluteTick, absoluteTick),
                    new DamageRange(0f, 1f),
                    sourceKey,
                    healthFloor,
                    Confidence.POTENTIAL
                ));
            }
            return;
        }

        int application = 0;
        for (long start = 1L; start <= remainingHorizon; start += interval) {
            long end = Math.min(remainingHorizon, start + interval - 1L);
            output.add(windowEvent(
                idPrefix + application++,
                new TickWindow(visibleDuration + start, visibleDuration + end),
                new DamageRange(0f, 1f),
                sourceKey,
                healthFloor,
                Confidence.POTENTIAL
            ));
        }
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

    private static int interval(boolean poison, int amplifier) {
        int intervalBase = poison ? 25 : 40;
        return amplifier >= 31 ? 0 : intervalBase >> Math.max(0, amplifier);
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
