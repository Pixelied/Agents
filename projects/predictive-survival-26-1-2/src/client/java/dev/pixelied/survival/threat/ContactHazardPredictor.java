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
        // Vanilla invokes these contact callbacks every game tick while contact continues. The
        // current frame proves the tick-0 contact; future contact is only potential because the
        // player may move away, but modeling a single hit can be falsely safe when hurt cooldown
        // later expires while the player remains trapped in the hazard.
        output.add(event(id + ":0", rawDamage, sourceKey, fire, 0L, Confidence.MATCHED));
        long horizon = context.limits().maxProjectileHorizonTicks();
        for (long tick = 1L; tick <= horizon; tick++) {
            output.add(event(id + ':' + tick, rawDamage, sourceKey, fire, tick, Confidence.POTENTIAL));
        }
    }

    private static ThreatEvent event(
        String id,
        float rawDamage,
        String sourceKey,
        boolean fire,
        long tick,
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
            new TickWindow(tick, tick),
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
