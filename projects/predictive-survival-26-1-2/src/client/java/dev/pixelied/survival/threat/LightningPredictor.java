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
import java.util.Objects;
import java.util.Optional;

public final class LightningPredictor implements ThreatPredictor {
    private static final int[] MAX_COOLDOWN_ELIGIBLE_HITS = {0, 10, 20, 30};

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        Objects.requireNonNull(context, "context");
        List<ThreatEvent> events = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!"minecraft:lightning_bolt".equals(entity.typeKey())) continue;
            if (!overlapsVanillaStrikeBox(context.player().boundingBox(), entity)) continue;

            DamageSourceSnapshot damage = new DamageSourceSnapshot(
                DamageRange.exact(5f),
                EnumSet.of(DamageFlag.BYPASSES_SHIELD, DamageFlag.IS_LIGHTNING),
                false,
                1f,
                false,
                Optional.of(entity.position()),
                "minecraft:lightning_bolt"
            );
            for (int i = 0; i < MAX_COOLDOWN_ELIGIBLE_HITS.length; i++) {
                int tick = MAX_COOLDOWN_ELIGIBLE_HITS[i];
                if (tick > context.limits().maxDecisionHistory()) continue;
                events.add(new ThreatEvent(
                    "lightning:" + entity.id() + ":" + i,
                    ThreatKind.ENVIRONMENT,
                    new TickWindow(tick, tick),
                    damage,
                    Confidence.POTENTIAL,
                    Optional.of(entity.position()),
                    Optional.of(context.player().position()),
                    true,
                    false,
                    true,
                    false
                ));
            }
        }
        return List.copyOf(events);
    }

    private static boolean overlapsVanillaStrikeBox(
        AabbSnapshot player,
        WorldSnapshot.EntitySnapshot lightning
    ) {
        double x = lightning.position().x();
        double y = lightning.position().y();
        double z = lightning.position().z();
        double minX = x - 3d;
        double minY = y - 3d;
        double minZ = z - 3d;
        double maxX = x + 3d;
        double maxY = y + 9d;
        double maxZ = z + 3d;
        return player.maxX() > minX && player.minX() < maxX
            && player.maxY() > minY && player.minY() < maxY
            && player.maxZ() > minZ && player.minZ() < maxZ;
    }
}
