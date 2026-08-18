package dev.pixelied.survival.debug;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.planner.SurvivalPlan;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class SurvivalDebugHud {
    private static final int MAX_THREAT_LINES = 3;

    private SurvivalDebugHud() {
    }

    public static List<String> lines(
        SurvivalConfig config,
        SurvivalEngine.EngineFrame frame,
        Optional<SurvivalPlan> plan,
        Optional<ExecutionStatus> executionStatus
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(executionStatus, "executionStatus");
        if (!config.debugEnabled()) return List.of();

        List<String> lines = new ArrayList<>();
        lines.add("Predictive Survival [" + config.safetyMode() + "]");
        lines.add(String.format(
            Locale.ROOT,
            "HP %.2f + %.2f | Hurt %s | RTT %.0fms +/- %.0f",
            frame.context().player().health(),
            frame.context().player().absorption(),
            frame.context().player().hurtState().confidence(),
            frame.context().timing().rttMs(),
            frame.context().timing().jitterMs()
        ));

        int count = Math.min(MAX_THREAT_LINES, frame.timeline().events().size());
        for (int i = 0; i < count; i++) {
            ThreatEvent event = frame.timeline().events().get(i);
            lines.add(String.format(
                Locale.ROOT,
                "Threat %s @ %d-%dt | raw %.2f-%.2f | %s",
                event.id(),
                event.impact().earliest(),
                event.impact().latest(),
                event.damage().rawDamage().min(),
                event.damage().rawDamage().max(),
                event.confidence()
            ));
        }
        if (frame.timeline().events().size() > count) {
            lines.add("+" + (frame.timeline().events().size() - count) + " more threats");
        }

        if (plan.isPresent()) {
            SurvivalPlan current = plan.get();
            lines.add(String.format(
                Locale.ROOT,
                "Action %s | predicted %.2f + %.2f | candidates %d",
                current.action().getClass().getSimpleName(),
                current.simulation().result().finalHealth(),
                current.simulation().result().finalAbsorption(),
                current.evaluatedCandidateCount()
            ));
        } else {
            lines.add("Action none");
        }

        executionStatus.ifPresent(status -> lines.add("Execution " + status.getClass().getSimpleName() + ": " + reason(status)));
        return List.copyOf(lines);
    }

    private static String reason(ExecutionStatus status) {
        if (status instanceof ExecutionStatus.WaitingForServer waiting) return waiting.reason();
        if (status instanceof ExecutionStatus.Confirmed confirmed) return confirmed.detail();
        if (status instanceof ExecutionStatus.Failed failed) return failed.reason();
        return status.getClass().getSimpleName();
    }
}
