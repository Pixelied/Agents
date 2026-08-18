package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class EnvironmentPredictorRegistry {
    private static final Comparator<ThreatEvent> ORDER = Comparator
        .comparingLong((ThreatEvent event) -> event.impact().earliest())
        .thenComparing(ThreatEvent::id);

    private final List<ThreatPredictor> predictors;

    public EnvironmentPredictorRegistry(List<ThreatPredictor> predictors) {
        this.predictors = List.copyOf(Objects.requireNonNull(predictors, "predictors"));
    }

    public static EnvironmentPredictorRegistry defaults() {
        return new EnvironmentPredictorRegistry(List.of(
            new BurnPredictor(),
            new DrowningPredictor(),
            new SuffocationPredictor(),
            new FreezePredictor(),
            new WorldBorderPredictor(),
            new StarvationPredictor(),
            new StatusEffectPredictor(),
            new AreaEffectCloudPredictor()
        ));
    }

    public List<ThreatEvent> predict(PredictionContext context) {
        Objects.requireNonNull(context, "context");
        List<ThreatEvent> events = new ArrayList<>();
        for (ThreatPredictor predictor : predictors) {
            List<ThreatEvent> predicted = Objects.requireNonNull(predictor.predict(context), "predictor result");
            for (ThreatEvent event : predicted) events.add(Objects.requireNonNull(event, "threat event"));
        }
        events.sort(ORDER);
        if (events.size() > context.limits().maxThreats()) {
            events = new ArrayList<>(events.subList(0, context.limits().maxThreats()));
        }
        return List.copyOf(events);
    }
}
