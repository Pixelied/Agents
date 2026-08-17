# Task 10 Brief — Predictor contracts and bounded broad phase

Create the pure predictor framework shared by every threat family.

## Required types

```java
public interface ThreatPredictor {
    List<ThreatEvent> predict(PredictionContext context);
}
public record EngineLimits(int maxThreats, int maxPlannerCandidates,
                           int maxProjectileHorizonTicks, int maxDecisionHistory) {
    public static EngineLimits defaults() { return new EngineLimits(128, 32, 80, 128); }
}
```

`WorldSnapshot` is immutable and contains only nearby entity/block snapshots supplied by the future live broad phase; no predictor may scan a live Minecraft world directly. Nested immutable entity/block records are acceptable to avoid extra top-level files.

`PredictionContext` carries `PlayerSnapshot`, `WorldSnapshot`, `TimingSnapshot`, and `EngineLimits`.

`ThreatPredictorRegistry` owns an immutable list of predictors and exposes `predictAll(PredictionContext)`.

## Conservative merge/cap semantics

- Stable physical identity is `ThreatEvent.id()`; events with the same id are merged, not double-counted.
- Merge raw ranges by union: min of mins, max of maxes.
- Merge impact windows by union.
- Merge damage flags by union; preserve the larger freezing multiplier and any piercing/bypass capability.
- Confidence becomes the least certain of the inputs (`UNKNOWN` worst, then `POTENTIAL`, `BOUNDED`, `MATCHED`, `EXACT`).
- Capability booleans use OR so a safety action is not hidden by one predictor.
- After merging, order by earliest impact, then higher raw max, then stable id. Return at most `EngineLimits.maxThreats`.
- Reject non-positive limit values.
- Predictor exceptions are not silently swallowed; an implementation bug must surface during development rather than quietly removing threats.

## RED tests

- duplicate id raw 8..10 and 9..12 -> one event raw 8..12.
- duplicate id impact 2..3 and 3..5 -> 2..5.
- 20 unique threats with `maxThreats=8` -> exactly 8, choosing earlier/higher-risk threats deterministically.
- returned list is immutable.
- invalid zero/negative limits are rejected.

Use TDD: tests first, then minimum pure framework and full CI.
