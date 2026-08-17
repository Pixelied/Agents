package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ThreatPredictorRegistry {
    private static final Comparator<ThreatEvent> RISK_ORDER = Comparator
        .comparingLong((ThreatEvent event) -> event.impact().earliest())
        .thenComparing(Comparator.comparingDouble((ThreatEvent event) -> event.damage().rawDamage().max()).reversed())
        .thenComparing(ThreatEvent::id);

    private final List<ThreatPredictor> predictors;

    public ThreatPredictorRegistry(List<ThreatPredictor> predictors) {
        this.predictors = List.copyOf(Objects.requireNonNull(predictors, "predictors"));
    }

    public List<ThreatEvent> predictAll(PredictionContext context) {
        Objects.requireNonNull(context, "context");
        Map<String, ThreatEvent> mergedById = new LinkedHashMap<>();

        for (ThreatPredictor predictor : predictors) {
            List<ThreatEvent> predicted = Objects.requireNonNull(predictor.predict(context), "predictor result");
            for (ThreatEvent event : predicted) {
                Objects.requireNonNull(event, "threat event");
                mergedById.merge(event.id(), event, ThreatPredictorRegistry::merge);
            }
        }

        List<ThreatEvent> merged = new ArrayList<>(mergedById.values());
        merged.sort(RISK_ORDER);
        if (merged.size() > context.limits().maxThreats()) {
            merged = new ArrayList<>(merged.subList(0, context.limits().maxThreats()));
        }
        return List.copyOf(merged);
    }

    private static ThreatEvent merge(ThreatEvent first, ThreatEvent second) {
        DamageSourceSnapshot damage = mergeDamage(first.id(), first.damage(), second.damage());
        ThreatKind kind = first.kind() == second.kind() ? first.kind() : ThreatKind.OTHER;

        return new ThreatEvent(
            first.id(),
            kind,
            new TickWindow(
                Math.min(first.impact().earliest(), second.impact().earliest()),
                Math.max(first.impact().latest(), second.impact().latest())
            ),
            damage,
            lessCertain(first.confidence(), second.confidence()),
            mergeOptional(first.sourcePosition(), second.sourcePosition()),
            mergeOptional(first.impactPosition(), second.impactPosition()),
            first.avoidable() || second.avoidable(),
            first.blockable() || second.blockable(),
            first.relocatable() || second.relocatable(),
            first.canDisableBlocking() || second.canDisableBlocking()
        );
    }

    private static DamageSourceSnapshot mergeDamage(String threatId, DamageSourceSnapshot first, DamageSourceSnapshot second) {
        EnumSet<DamageFlag> flags = EnumSet.noneOf(DamageFlag.class);
        flags.addAll(first.flags());
        flags.addAll(second.flags());

        return new DamageSourceSnapshot(
            new DamageRange(
                Math.min(first.rawDamage().min(), second.rawDamage().min()),
                Math.max(first.rawDamage().max(), second.rawDamage().max())
            ),
            flags,
            first.scalesWithDifficulty() || second.scalesWithDifficulty(),
            Math.max(first.freezingMultiplier(), second.freezingMultiplier()),
            first.piercingProjectile() || second.piercingProjectile(),
            mergeOptional(first.sourcePosition(), second.sourcePosition()),
            first.sourceKey().equals(second.sourceKey())
                ? first.sourceKey()
                : "merged:" + threatId
        );
    }

    private static Confidence lessCertain(Confidence first, Confidence second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private static <T> Optional<T> mergeOptional(Optional<T> first, Optional<T> second) {
        if (first.isEmpty()) return second;
        if (second.isEmpty()) return first;
        return first.equals(second) ? first : Optional.empty();
    }
}
