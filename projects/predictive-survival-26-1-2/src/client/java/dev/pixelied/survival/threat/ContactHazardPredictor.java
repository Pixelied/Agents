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

public final class ContactHazardPredictor implements ThreatPredictor {
    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<ThreatEvent> events = new ArrayList<>();
        if (Boolean.parseBoolean(context.player().state("contact_cactus"))) {
            addSustained(events, context, "cactus", 1f, "minecraft:cactus", false);
        }
        if (Boolean.parseBoolean(context.player().state("contact_sweet_berry_bush"))) {
            addSustained(events, context, "sweet_berry_bush", 1f, "minecraft:sweet_berry_bush", false);
        }
        int campfireDamage = positiveInt(context.player().state("contact_campfire_damage"));
        if (campfireDamage > 0) {
            addSustained(events, context, "campfire", campfireDamage, "minecraft:campfire", true);
        }
        if (Boolean.parseBoolean(context.player().state("contact_hot_floor"))) {
            addSustained(events, context, "hot_floor", 1f, "minecraft:hot_floor", true);
        }
        return List.copyOf(events);
    }

    private static void addSustained(
        List<ThreatEvent> output,
        PredictionContext context,
        String id,
        float rawDamage,
        String sourceKey,
        boolean fire
    ) {
        // Preserve the established immediate event identity/window: the frame proves contact now,
        // while the server may process the corresponding contact callback on the current or next
        // tick. Future continued contact is only potential because the player can still move away.
        output.add(event(id, rawDamage, sourceKey, fire, new TickWindow(0, 1), Confidence.MATCHED));
        long horizon = context.limits().maxDecisionHistory();
        for (long tick = 2L; tick <= horizon; tick++) {
            output.add(event(
                id + ":future:" + tick,
                rawDamage,
                sourceKey,
                fire,
                new TickWindow(tick, tick),
                Confidence.POTENTIAL
            ));
        }
    }

    private static ThreatEvent event(
        String id,
        float rawDamage,
        String sourceKey,
        boolean fire,
        TickWindow impact,
        Confidence confidence
    ) {
        EnumSet<DamageFlag> flags = EnumSet.of(DamageFlag.BYPASSES_SHIELD);
        if (fire) flags.add(DamageFlag.IS_FIRE);
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage), flags, false, 1f, false, Optional.empty(), sourceKey
        );
        return new ThreatEvent(
            "contact:" + id,
            ThreatKind.ENVIRONMENT,
            impact,
            damage,
            confidence,
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true,
            false
        );
    }

    private static int positiveInt(String value) {
        if (value == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
