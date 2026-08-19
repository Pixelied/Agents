package dev.adrien.crystaloptimizer.v2.diagnostics;

import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import java.util.Objects;

public record TimeToDamageTrace(
    long actionId,
    long eventObservedNanos,
    long decisionCompleteNanos,
    long dispatchCompleteNanos,
    long resultObservedNanos,
    DamageEstimate targetDamage
) {
    public static final long RESULT_PENDING = -1L;

    public TimeToDamageTrace {
        Objects.requireNonNull(targetDamage, "targetDamage");
        if (actionId < 0L || eventObservedNanos < 0L) {
            throw new IllegalArgumentException("actionId and event timestamp must be non-negative");
        }
        if (decisionCompleteNanos < eventObservedNanos) {
            throw new IllegalArgumentException("decision cannot complete before the event");
        }
        if (dispatchCompleteNanos < decisionCompleteNanos) {
            throw new IllegalArgumentException("dispatch cannot complete before the decision");
        }
        if (resultObservedNanos != RESULT_PENDING && resultObservedNanos < dispatchCompleteNanos) {
            throw new IllegalArgumentException("result cannot precede dispatch");
        }
    }

    public static TimeToDamageTrace dispatched(
        long actionId,
        long eventObservedNanos,
        long decisionCompleteNanos,
        long dispatchCompleteNanos
    ) {
        return dispatched(
            actionId,
            eventObservedNanos,
            decisionCompleteNanos,
            dispatchCompleteNanos,
            DamageEstimate.exact(0.0f, 0L, 0L)
        );
    }

    public static TimeToDamageTrace dispatched(
        long actionId,
        long eventObservedNanos,
        long decisionCompleteNanos,
        long dispatchCompleteNanos,
        DamageEstimate targetDamage
    ) {
        return new TimeToDamageTrace(
            actionId,
            eventObservedNanos,
            decisionCompleteNanos,
            dispatchCompleteNanos,
            RESULT_PENDING,
            targetDamage
        );
    }

    public TimeToDamageTrace withResult(long resultNanos) {
        return new TimeToDamageTrace(
            actionId,
            eventObservedNanos,
            decisionCompleteNanos,
            dispatchCompleteNanos,
            resultNanos,
            targetDamage
        );
    }

    public long eventToDecisionNanos() {
        return decisionCompleteNanos - eventObservedNanos;
    }

    public long decisionToDispatchNanos() {
        return dispatchCompleteNanos - decisionCompleteNanos;
    }

    public long eventToDispatchNanos() {
        return dispatchCompleteNanos - eventObservedNanos;
    }

    public boolean resultObserved() {
        return resultObservedNanos != RESULT_PENDING;
    }
}
