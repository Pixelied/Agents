package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

final class HurtingProjectileFollowups {
    private static final String SMALL_FIREBALL = "minecraft:small_fireball";
    private static final String WITHER_SKULL = "minecraft:wither_skull";
    private static final int PLAYER_EFFECT_TICK_DELAY = 1;
    private static final int SMALL_FIREBALL_IGNITION_TICKS = 100;
    private static final int PERIODIC_DAMAGE_INTERVAL = 20;
    private static final int WITHER_NORMAL_DURATION_TICKS = 200;
    private static final int WITHER_HARD_DURATION_TICKS = 800;

    private HurtingProjectileFollowups() {
    }

    static List<ThreatEvent> afterAcceptedDirectHit(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot impact,
        ThreatEvent direct
    ) {
        if (SMALL_FIREBALL.equals(entity.typeKey())) {
            return smallFireballBurns(context, entity, impact, direct);
        }
        if (WITHER_SKULL.equals(entity.typeKey())) {
            return witherSkullWither(context, entity, impact, direct);
        }
        return List.of();
    }

    private static List<ThreatEvent> smallFireballBurns(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot impact,
        ThreatEvent direct
    ) {
        if (Boolean.parseBoolean(context.player().state("fire_immune"))) return List.of();

        long horizon = context.limits().maxDecisionHistory();
        List<ThreatEvent> events = new ArrayList<>();
        int application = 0;
        for (int phase = 0; phase < SMALL_FIREBALL_IGNITION_TICKS; phase += PERIODIC_DAMAGE_INTERVAL) {
            long offset = PLAYER_EFFECT_TICK_DELAY + phase;
            long earliest = saturatingAdd(direct.impact().earliest(), offset);
            if (earliest > horizon) break;
            long latest = Math.min(horizon, saturatingAdd(direct.impact().latest(), offset));
            DamageSourceSnapshot source = new DamageSourceSnapshot(
                DamageRange.exact(1f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD, DamageFlag.IS_FIRE),
                false,
                1f,
                false,
                Optional.of(impact),
                "minecraft:on_fire"
            );
            events.add(new ThreatEvent(
                "projectile:" + entity.id() + ":on_fire:" + application,
                ThreatKind.ENVIRONMENT,
                new TickWindow(earliest, latest),
                source,
                earliest == latest ? Confidence.EXACT : Confidence.BOUNDED,
                Optional.of(entity.position()),
                Optional.of(impact),
                true,
                false,
                true,
                false,
                Optional.of(direct.id())
            ));
            application++;
        }
        return List.copyOf(events);
    }

    private static List<ThreatEvent> witherSkullWither(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot impact,
        ThreatEvent direct
    ) {
        int duration = switch (context.player().difficulty()) {
            case NORMAL -> WITHER_NORMAL_DURATION_TICKS;
            case HARD -> WITHER_HARD_DURATION_TICKS;
            default -> 0;
        };
        if (duration == 0) return List.of();

        long horizon = context.limits().maxDecisionHistory();
        List<ThreatEvent> events = new ArrayList<>();
        int application = 0;
        for (int phase = 0; phase < duration; phase += PERIODIC_DAMAGE_INTERVAL) {
            long offset = PLAYER_EFFECT_TICK_DELAY + phase;
            long earliest = saturatingAdd(direct.impact().earliest(), offset);
            if (earliest > horizon) break;
            long latest = Math.min(horizon, saturatingAdd(direct.impact().latest(), offset));
            DamageSourceSnapshot source = new DamageSourceSnapshot(
                DamageRange.exact(1f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                Optional.of(impact),
                "minecraft:wither",
                0f
            );
            events.add(new ThreatEvent(
                "projectile:" + entity.id() + ":wither_skull_wither:" + application,
                ThreatKind.PROJECTILE,
                new TickWindow(earliest, latest),
                source,
                earliest == latest ? Confidence.EXACT : Confidence.BOUNDED,
                Optional.of(entity.position()),
                Optional.of(impact),
                true,
                false,
                true,
                false,
                Optional.of(direct.id())
            ));
            application++;
        }
        return List.copyOf(events);
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }
}
