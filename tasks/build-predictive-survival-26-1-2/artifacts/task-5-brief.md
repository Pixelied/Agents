# Task 5 Brief — Conservative server hurt-state tracker

Implement a pure `ServerHurtStateTracker` that represents the server's raw pre-armor `lastHurt` and hurt-cooldown timing without ever inferring raw damage from the client's post-mitigation health delta.

## Interface

```java
public final class ServerHurtStateTracker {
    public HurtState current();
    public void tick(int elapsedServerTicks);
    public void recordPredictedApplied(float preArmorLastHurt, TickWindow appliedAt);
    public void recordObservedHealthDelta(float healthDelta, TickWindow observedAt);
    public void invalidate();
    public HurtState conservativeForLethalDecision();
}
```

## Required semantics

- `recordPredictedApplied` sets raw `lastHurt` from the source-faithful prediction and starts a 20-tick hurt timer. An exact one-tick window is `EXACT`; a wider application window is `BOUNDED`.
- An observed health delta whose estimated server-event window overlaps the pending predicted application can promote the state to `MATCHED`, preserving the predicted raw `lastHurt`; the health delta itself never replaces raw `lastHurt`.
- An observed health delta that does not match the pending predicted application invalidates raw hurt state to `UNKNOWN`.
- Observation without a prediction is `UNKNOWN`.
- `tick` only ages the server timer; it never synthesizes a new raw damage value.
- `conservativeForLethalDecision` credits nonzero `lastHurt` only when confidence is `EXACT` or `MATCHED` and `invulnerableTime > 10`. Otherwise it returns zero `lastHurt` so lethal decisions do not gamble on uncertain hurt-cooldown state.
- `invalidate()` clears all credit immediately.

## RED tests

Cover unexpected observation invalidation, exact predicted state, matching observation preserving raw damage, broad timing producing BOUNDED/no lethal credit, countdown across the `>10` strong-cooldown boundary, and observation-without-prediction.

Do not read `LocalPlayer.lastHurt` or any Minecraft classes in this task; Task 8 owns runtime adapters.
