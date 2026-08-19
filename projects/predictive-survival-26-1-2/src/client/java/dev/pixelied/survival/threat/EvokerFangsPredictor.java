package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
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

public final class EvokerFangsPredictor implements ThreatPredictor {
    private static final int EVENT_TO_DAMAGE_TICKS = 7;

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<ThreatEvent> events = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!"minecraft:evoker_fangs".equals(entity.typeKey())) continue;
            if (!overlapsDamageBox(context.player().boundingBox(), entity.boundingBox())) continue;

            boolean started = Boolean.parseBoolean(entity.properties().getOrDefault("evoker_fangs_started", "false"));
            TickWindow impact;
            Confidence confidence;
            if (started) {
                int elapsed = nonNegativeInt(entity.properties().get("evoker_fangs_elapsed_ticks"), 0);
                if (elapsed > EVENT_TO_DAMAGE_TICKS) continue;
                impact = new TickWindow(0, EVENT_TO_DAMAGE_TICKS - elapsed);
                confidence = Confidence.BOUNDED;
            } else {
                impact = new TickWindow(0, context.limits().maxDecisionHistory());
                confidence = Confidence.POTENTIAL;
            }

            DamageSourceSnapshot damage = new DamageSourceSnapshot(
                DamageRange.exact(6f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                Optional.of(entity.position()),
                "minecraft:indirect_magic"
            );
            events.add(new ThreatEvent(
                "evoker_fangs:" + entity.id(),
                ThreatKind.OTHER,
                impact,
                damage,
                confidence,
                Optional.of(entity.position()),
                Optional.of(entity.position()),
                true,
                false,
                true,
                false
            ));
        }
        return List.copyOf(events);
    }

    private static boolean overlapsDamageBox(AabbSnapshot player, AabbSnapshot fangs) {
        double minX = fangs.minX() - 0.2d;
        double maxX = fangs.maxX() + 0.2d;
        double minZ = fangs.minZ() - 0.2d;
        double maxZ = fangs.maxZ() + 0.2d;
        return player.maxX() > minX && player.minX() < maxX
            && player.maxY() > fangs.minY() && player.minY() < fangs.maxY()
            && player.maxZ() > minZ && player.minZ() < maxZ;
    }

    private static int nonNegativeInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
