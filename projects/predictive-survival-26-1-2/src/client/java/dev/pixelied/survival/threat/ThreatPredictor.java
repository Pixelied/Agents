package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.List;

@FunctionalInterface
public interface ThreatPredictor {
    List<ThreatEvent> predict(PredictionContext context);
}
