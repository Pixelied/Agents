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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class ReactiveDamagePredictor implements ThreatPredictor {
    private static final Comparator<ThreatEvent> ORDER = Comparator
        .comparingLong((ThreatEvent event) -> event.impact().earliest())
        .thenComparing(ThreatEvent::id);

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<ThreatEvent> events = new ArrayList<>();
        addThorns(context, events);
        addOwnPearls(context, events);
        events.sort(ORDER);
        return List.copyOf(events);
    }

    private static void addThorns(PredictionContext context, List<ThreatEvent> events) {
        String targetId = context.player().state("outgoing_attack_target_id");
        if (targetId == null || targetId.isBlank()) return;

        WorldSnapshot.EntitySnapshot target = context.world().entities().stream()
            .filter(entity -> entity.id().equals(targetId))
            .findFirst()
            .orElse(null);
        if (target == null) return;

        String encodedLevels = target.properties().get("thorns_levels");
        if (encodedLevels == null || encodedLevels.isBlank()) return;
        String[] levels = encodedLevels.split(",");
        for (int piece = 0; piece < levels.length; piece++) {
            Integer level = positiveInt(levels[piece]);
            if (level == null) continue;

            DamageSourceSnapshot source = new DamageSourceSnapshot(
                new DamageRange(0f, 5f),
                EnumSet.noneOf(DamageFlag.class),
                false,
                1f,
                false,
                Optional.of(target.position()),
                "minecraft:thorns"
            );
            events.add(new ThreatEvent(
                "reactive:thorns:" + target.id() + ":" + piece,
                ThreatKind.REACTIVE,
                new TickWindow(0, 0),
                source,
                Confidence.BOUNDED,
                Optional.of(target.position()),
                Optional.of(context.player().position()),
                true,
                true,
                false,
                false
            ));
        }
    }

    private static void addOwnPearls(PredictionContext context, List<ThreatEvent> events) {
        long horizon = context.limits().maxProjectileHorizonTicks();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!"minecraft:ender_pearl".equals(entity.typeKey())) continue;
            if (!Boolean.parseBoolean(entity.properties().getOrDefault("owner_is_local_player", "false"))) continue;

            Long exactImpactTick = nonNegativeLong(entity.properties().get("predicted_impact_tick"));
            TickWindow impact;
            Confidence confidence;
            if (exactImpactTick != null && exactImpactTick <= horizon) {
                impact = new TickWindow(exactImpactTick, exactImpactTick);
                confidence = Confidence.MATCHED;
            } else {
                impact = new TickWindow(1L, horizon);
                confidence = Confidence.BOUNDED;
            }

            DamageSourceSnapshot source = new DamageSourceSnapshot(
                DamageRange.exact(5f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD, DamageFlag.IS_FALL),
                false,
                1f,
                false,
                Optional.of(entity.position()),
                "minecraft:ender_pearl"
            );
            events.add(new ThreatEvent(
                "reactive:ender_pearl:" + entity.id(),
                ThreatKind.REACTIVE,
                impact,
                source,
                confidence,
                Optional.of(entity.position()),
                Optional.empty(),
                false,
                false,
                false,
                false
            ));
        }
    }

    private static Integer positiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long nonNegativeLong(String value) {
        if (value == null) return null;
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
