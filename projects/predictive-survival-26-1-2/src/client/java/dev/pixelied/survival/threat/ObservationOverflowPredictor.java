package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class ObservationOverflowPredictor implements ThreatPredictor {
    public static final String MARKER_TYPE = "predictive_survival:observation_overflow";

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        boolean overflowed = context.world().entities().stream()
            .anyMatch(entity -> MARKER_TYPE.equals(entity.typeKey()));
        if (!overflowed) return List.of();

        DamageSourceSnapshot source = new DamageSourceSnapshot(
            new DamageRange(0f, Float.MAX_VALUE),
            EnumSet.of(
                DamageFlag.BYPASSES_INVULNERABILITY,
                DamageFlag.BYPASSES_COOLDOWN,
                DamageFlag.BYPASSES_ARMOR,
                DamageFlag.BYPASSES_SHIELD,
                DamageFlag.BYPASSES_EFFECTS,
                DamageFlag.BYPASSES_RESISTANCE,
                DamageFlag.BYPASSES_ENCHANTMENTS
            ),
            false,
            1f,
            false,
            Optional.empty(),
            "predictive_survival:observation_overflow"
        );
        return List.of(new ThreatEvent(
            "predictive_survival:observation_overflow",
            ThreatKind.OTHER,
            new TickWindow(0, context.limits().maxDecisionHistory()),
            source,
            Confidence.UNKNOWN,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            true
        ));
    }
}
