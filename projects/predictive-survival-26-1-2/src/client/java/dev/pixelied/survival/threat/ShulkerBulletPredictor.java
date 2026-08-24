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

public final class ShulkerBulletPredictor implements ThreatPredictor {
    private static final String SHULKER_BULLET = "minecraft:shulker_bullet";
    private static final float RAW_DAMAGE = 4f;

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        if (context.player().difficulty() == DifficultySnapshot.PEACEFUL) return List.of();

        List<ThreatEvent> events = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!SHULKER_BULLET.equals(entity.typeKey())) continue;
            events.add(event(context, entity));
        }
        return List.copyOf(events);
    }

    private static ThreatEvent event(PredictionContext context, WorldSnapshot.EntitySnapshot entity) {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            damageRange(context.player().difficulty()),
            EnumSet.of(DamageFlag.IS_PROJECTILE),
            false,
            1f,
            false,
            Optional.of(entity.position()),
            "minecraft:mob_projectile"
        );
        return new ThreatEvent(
            "shulker_bullet:" + entity.id(),
            ThreatKind.PROJECTILE,
            new TickWindow(1, context.limits().maxProjectileHorizonTicks()),
            source,
            Confidence.POTENTIAL,
            Optional.of(entity.position()),
            Optional.empty(),
            true,
            true,
            true,
            false
        );
    }

    private static DamageRange damageRange(DifficultySnapshot difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> DamageRange.exact(0f);
            case EASY -> new DamageRange(3f, RAW_DAMAGE);
            case NORMAL -> DamageRange.exact(RAW_DAMAGE);
            case HARD -> new DamageRange(RAW_DAMAGE, 6f);
        };
    }
}
