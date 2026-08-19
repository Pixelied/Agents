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

public final class GuardianBeamPredictor implements ThreatPredictor {
    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<ThreatEvent> events = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!isGuardian(entity.typeKey())) continue;
            if (!Boolean.parseBoolean(entity.properties().get("guardian_beam_target_local"))) continue;

            int attackTicks = nonNegativeInt(entity.properties().get("guardian_attack_ticks"), 0);
            int duration = positiveInt(entity.properties().get("guardian_attack_duration"), 80);
            long latest = Math.min(
                context.limits().maxDecisionHistory(),
                Math.max(0, duration - attackTicks)
            );
            TickWindow impact = new TickWindow(0, latest);
            boolean elder = "minecraft:elder_guardian".equals(entity.typeKey());
            float magicDamage = 1f;
            if (context.player().difficulty() == DifficultySnapshot.HARD) magicDamage += 2f;
            if (elder) magicDamage += 2f;

            DamageSourceSnapshot magic = new DamageSourceSnapshot(
                DamageRange.exact(magicDamage),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                Optional.of(entity.position()),
                "minecraft:indirect_magic"
            );
            events.add(new ThreatEvent(
                "guardian_beam:" + entity.id() + ":magic",
                ThreatKind.OTHER,
                impact,
                magic,
                Confidence.BOUNDED,
                Optional.of(entity.position()),
                Optional.empty(),
                true,
                false,
                true,
                false
            ));

            float attackDamage = positiveFloat(entity.properties().get("attack_damage"), 0f);
            if (attackDamage > 0f) {
                DamageSourceSnapshot melee = new DamageSourceSnapshot(
                    DamageRange.exact(attackDamage),
                    EnumSet.noneOf(DamageFlag.class),
                    true,
                    1f,
                    false,
                    Optional.of(entity.position()),
                    "minecraft:mob_attack"
                );
                events.add(new ThreatEvent(
                    "guardian_beam:" + entity.id() + ":melee",
                    ThreatKind.MELEE,
                    impact,
                    melee,
                    Confidence.BOUNDED,
                    Optional.of(entity.position()),
                    Optional.empty(),
                    true,
                    true,
                    true,
                    false
                ));
            }
        }
        return List.copyOf(events);
    }

    private static boolean isGuardian(String typeKey) {
        return "minecraft:guardian".equals(typeKey) || "minecraft:elder_guardian".equals(typeKey);
    }

    private static int nonNegativeInt(String value, int fallback) {
        try {
            return value == null ? fallback : Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int positiveInt(String value, int fallback) {
        int parsed = nonNegativeInt(value, fallback);
        return parsed > 0 ? parsed : fallback;
    }

    private static float positiveFloat(String value, float fallback) {
        try {
            float parsed = value == null ? fallback : Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed > 0f ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
