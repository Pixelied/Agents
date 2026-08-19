package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class WardenSonicBoomPredictor implements ThreatPredictor {
    private static final int DAMAGE_DELAY_TICKS = 34;
    private static final double HORIZONTAL_RANGE = 15d;
    private static final double VERTICAL_RANGE = 20d;

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<ThreatEvent> events = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!"minecraft:warden".equals(entity.typeKey())) continue;
            Integer elapsed = nonNegativeInt(entity.properties().get("warden_sonic_ticks"));
            if (elapsed == null || elapsed > DAMAGE_DELAY_TICKS) continue;
            if (!withinSonicRange(context, entity)) continue;

            long remaining = Math.min(
                context.limits().maxDecisionHistory(),
                DAMAGE_DELAY_TICKS - elapsed
            );
            DamageSourceSnapshot damage = new DamageSourceSnapshot(
                DamageRange.exact(10f),
                EnumSet.of(
                    DamageFlag.BYPASSES_ARMOR,
                    DamageFlag.BYPASSES_SHIELD,
                    DamageFlag.BYPASSES_ENCHANTMENTS
                ),
                false,
                1f,
                false,
                Optional.of(entity.position()),
                "minecraft:sonic_boom"
            );
            events.add(new ThreatEvent(
                "warden_sonic:" + entity.id(),
                ThreatKind.OTHER,
                new TickWindow(0, remaining),
                damage,
                Confidence.POTENTIAL,
                Optional.of(entity.position()),
                Optional.empty(),
                true,
                false,
                true,
                false
            ));
        }
        return List.copyOf(events);
    }

    private static boolean withinSonicRange(PredictionContext context, WorldSnapshot.EntitySnapshot entity) {
        double dx = context.player().position().x() - entity.position().x();
        double dz = context.player().position().z() - entity.position().z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double vertical = Math.abs(context.player().position().y() - entity.position().y());
        return horizontal <= HORIZONTAL_RANGE && vertical <= VERTICAL_RANGE;
    }

    private static Integer nonNegativeInt(String value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
