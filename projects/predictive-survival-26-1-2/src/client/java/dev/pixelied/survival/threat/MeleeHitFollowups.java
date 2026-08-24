package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
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

final class MeleeHitFollowups {
    private MeleeHitFollowups() {}

    static List<ThreatEvent> afterAcceptedDirectHit(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot attacker,
        ThreatEvent direct
    ) {
        List<ThreatEvent> out = new ArrayList<>();
        int witherTicks = intProperty(attacker, "wither_followup_ticks", 0);
        if (witherTicks > 0) addPeriodicStatus(context, attacker, direct, out, "wither", witherTicks, 40, 0f, Confidence.POTENTIAL);

        int poisonTicks = switch (context.player().difficulty()) {
            case NORMAL -> intProperty(attacker, "poison_followup_normal_ticks", 0);
            case HARD -> intProperty(attacker, "poison_followup_hard_ticks", 0);
            default -> 0;
        };
        if (poisonTicks > 0) addPeriodicStatus(context, attacker, direct, out, "poison", poisonTicks, 25, 1f, Confidence.POTENTIAL);

        int fireAspect = intProperty(attacker, "fire_aspect_level", 0);
        if (fireAspect > 0 && !Boolean.parseBoolean(context.player().state("fire_immune"))) {
            addFireTicks(context, attacker, direct, out, fireAspect * 80, Confidence.POTENTIAL, "fire_aspect");
        }
        if (Boolean.parseBoolean(attacker.properties().getOrDefault("zombie_fire_followup_possible", "false"))
            && !Boolean.parseBoolean(context.player().state("fire_immune"))) {
            int seconds = switch (context.player().difficulty()) {
                case NORMAL -> 4;
                case HARD -> 6;
                default -> 0;
            };
            if (seconds > 0) addFireTicks(context, attacker, direct, out, seconds * 20, Confidence.POTENTIAL, "zombie_fire");
        }
        return List.copyOf(out);
    }

    private static void addPeriodicStatus(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot attacker,
        ThreatEvent direct,
        List<ThreatEvent> out,
        String effect,
        int duration,
        int interval,
        float healthFloor,
        Confidence confidence
    ) {
        int first = duration % interval == 0 ? 1 : duration % interval + 1;
        long horizon = context.limits().maxDecisionHistory();
        int index = 0;
        for (int offset = first; offset <= duration; offset += interval) {
            long earliest = add(direct.impact().earliest(), offset);
            if (earliest > horizon) break;
            long latest = Math.min(horizon, add(direct.impact().latest(), offset));
            boolean poison = "poison".equals(effect);
            DamageSourceSnapshot source = new DamageSourceSnapshot(
                DamageRange.exact(1f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false, 1f, false, Optional.of(attacker.position()),
                poison ? "minecraft:magic" : "minecraft:wither",
                healthFloor
            );
            out.add(new ThreatEvent(
                "melee:" + attacker.id() + ":" + effect + ":" + index++,
                ThreatKind.ENVIRONMENT,
                new TickWindow(earliest, latest),
                source,
                earliest == latest ? Confidence.EXACT : confidence,
                Optional.of(attacker.position()), Optional.of(context.player().position()),
                true, false, true, false, Optional.of(direct.id())
            ));
        }
    }

    private static void addFireTicks(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot attacker,
        ThreatEvent direct,
        List<ThreatEvent> out,
        int duration,
        Confidence confidence,
        String reason
    ) {
        long horizon = context.limits().maxDecisionHistory();
        int index = 0;
        for (int offset = 1; offset <= duration; offset += 20) {
            long earliest = add(direct.impact().earliest(), offset);
            if (earliest > horizon) break;
            long latest = Math.min(horizon, add(direct.impact().latest(), offset));
            DamageSourceSnapshot source = new DamageSourceSnapshot(
                DamageRange.exact(1f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD, DamageFlag.IS_FIRE),
                false, 1f, false, Optional.of(attacker.position()), "minecraft:on_fire"
            );
            out.add(new ThreatEvent(
                "melee:" + attacker.id() + ":" + reason + ":" + index++, ThreatKind.ENVIRONMENT,
                new TickWindow(earliest, latest), source, confidence,
                Optional.of(attacker.position()), Optional.of(context.player().position()),
                true, false, true, false, Optional.of(direct.id())
            ));
        }
    }

    private static int intProperty(WorldSnapshot.EntitySnapshot entity, String key, int fallback) {
        try { return Math.max(0, Integer.parseInt(entity.properties().getOrDefault(key, Integer.toString(fallback)))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static long add(long value, long offset) {
        return value > Long.MAX_VALUE - offset ? Long.MAX_VALUE : value + offset;
    }
}
