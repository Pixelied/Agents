package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

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
        if (remaining > 0) {
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
        } else if (!inLava && booleanState(context, "on_fire")) {
            // The client receives the synchronized on-fire flag, but vanilla clears its local
            // remainingFireTicks counter every client tick. The server phase is therefore unknown.
            // Represent each possible 20-tick fire pulse as a bounded potential window instead of
            // silently dropping the threat or inventing an exact countdown.
            for (long earliest = 1; earliest <= horizon; earliest += 20) {
                long latest = Math.min(horizon, earliest + 19);
                events.add(unknownPhaseFireEvent(earliest, latest));
            }
        }
        return List.copyOf(events);
    }

    private static ThreatEvent unknownPhaseFireEvent(long earliest, long latest) {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(1f),
            EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD, DamageFlag.IS_FIRE),
            false,
            1f,
            false,
            Optional.empty(),
            "minecraft:on_fire"
        );
        return new ThreatEvent(
            "env:on_fire:unknown_phase:" + earliest,
            ThreatKind.ENVIRONMENT,
            new TickWindow(earliest, latest),
            source,
            Confidence.POTENTIAL,
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true,
            false
        );
    }
}
