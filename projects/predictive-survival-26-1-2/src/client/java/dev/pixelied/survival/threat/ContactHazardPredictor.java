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
        List<ThreatEvent> events = new ArrayList<>(4);
        if (Boolean.parseBoolean(context.player().state("contact_cactus"))) {
            events.add(event("cactus", 1f, "minecraft:cactus", false));
        }
        if (Boolean.parseBoolean(context.player().state("contact_sweet_berry_bush"))) {
            events.add(event("sweet_berry_bush", 1f, "minecraft:sweet_berry_bush", false));
        }
        int campfireDamage = positiveInt(context.player().state("contact_campfire_damage"));
        if (campfireDamage > 0) {
            events.add(event("campfire", campfireDamage, "minecraft:campfire", true));
        }
        if (Boolean.parseBoolean(context.player().state("contact_hot_floor"))) {
            events.add(event("hot_floor", 1f, "minecraft:hot_floor", true));
        }
        return List.copyOf(events);
    }

    private static ThreatEvent event(String id, float rawDamage, String sourceKey, boolean fire) {
        EnumSet<DamageFlag> flags = EnumSet.of(DamageFlag.BYPASSES_SHIELD);
        if (fire) flags.add(DamageFlag.IS_FIRE);
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage), flags, false, 1f, false, Optional.empty(), sourceKey
        );
        return new ThreatEvent(
            "contact:" + id,
            ThreatKind.ENVIRONMENT,
            new TickWindow(0, 1),
            damage,
            Confidence.MATCHED,
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
