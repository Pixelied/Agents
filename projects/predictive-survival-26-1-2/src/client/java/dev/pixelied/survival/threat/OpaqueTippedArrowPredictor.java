package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Preserves safety for tipped-arrow potion payloads whose exact contents are not synchronized to remote clients.
 *
 * <p>Minecraft synchronizes Arrow's effect color, so the client can prove that a potion payload exists, but the
 * remote pickup stack does not expose the PotionContents, amplifier, duration, or duration scale. The predictor
 * therefore never guesses an effect from RGB. It reuses the ordinary projectile predictor solely to prove that
 * the arrow can hit the player, then attaches a collision-coupled unknown payload hazard.</p>
 */
public final class OpaqueTippedArrowPredictor implements ThreatPredictor {
    private static final String ARROW_TYPE = "minecraft:arrow";
    private final ProjectilePredictor projectilePredictor = new ProjectilePredictor();

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");

        List<ThreatEvent> result = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!ARROW_TYPE.equals(entity.typeKey())) continue;
            if (!Boolean.parseBoolean(entity.properties().getOrDefault("arrow_tipped", "false"))) continue;

            Optional<ThreatEvent> direct = projectilePredictor.predict(context).stream()
                .filter(event -> event.id().equals("projectile:" + entity.id() + ":direct"))
                .findFirst();
            if (direct.isEmpty()) continue;

            ThreatEvent arrowHit = direct.get();
            EnumSet<DamageFlag> flags = EnumSet.of(DamageFlag.BYPASSES_ARMOR);
            DamageSourceSnapshot source = new DamageSourceSnapshot(
                new DamageRange(0f, Float.MAX_VALUE),
                flags,
                false,
                1f,
                arrowHit.damage().piercingProjectile(),
                arrowHit.sourcePosition(),
                "minecraft:indirect_magic"
            );
            result.add(new ThreatEvent(
                "projectile:" + entity.id() + ":opaque_potion",
                ThreatKind.PROJECTILE,
                arrowHit.impact(),
                source,
                Confidence.UNKNOWN,
                arrowHit.sourcePosition(),
                arrowHit.impactPosition(),
                arrowHit.avoidable(),
                arrowHit.blockable(),
                arrowHit.relocatable(),
                false
            ));
        }
        return List.copyOf(result);
    }
}
