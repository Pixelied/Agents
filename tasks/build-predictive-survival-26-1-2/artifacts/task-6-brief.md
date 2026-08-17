# Task 6 Brief — Ordered and uncertain multi-hit timelines

Build the pure timeline layer on top of `DamageSimulator`.

## Locked types

```java
public record ThreatEvent(
    String id,
    ThreatKind kind,
    TickWindow impact,
    DamageSourceSnapshot damage,
    Confidence confidence,
    Optional<Vec3Snapshot> sourcePosition,
    Optional<Vec3Snapshot> impactPosition,
    boolean avoidable,
    boolean blockable,
    boolean relocatable,
    boolean canDisableBlocking
) {}
public record TimelineEventResult(ThreatEvent event, float preMitigationRaw, float finalDamage, DamageResult damageResult) {}
public record TimelineResult(List<TimelineEventResult> eventResults, float finalHealth, float finalAbsorption, boolean survived, int consumedDeathProtectionCount, Optional<String> firstLethalEventId) {
    public TimelineEventResult eventResult(String id);
}
public record ThreatTimeline(List<ThreatEvent> events) {}
public final class ThreatTimelineSimulator {
    public TimelineResult simulate(PlayerSnapshot start, ThreatTimeline timeline);
}
```

`ThreatKind` must at least distinguish `EXPLOSION`, `PROJECTILE`, `MELEE`, `FALL`, `ENVIRONMENT`, `REACTIVE`, and `OTHER`.

## Timeline semantics

- `TickWindow` values are server-tick offsets relative to simulation start.
- Age `HurtState.invulnerableTime` by elapsed ticks before each event.
- Exact non-overlapping windows have fixed order.
- For overlapping windows, evaluate feasible permutations up to 6 events and choose the worst survival result. Above the cap, use descending raw maximum then stable id.
- Schedule a permutation as late as possible while preserving order; reject permutations that cannot fit every event window.
- Worse means dead before alive; then lower `health + absorption`; then more death-protection consumed; then stable id order.
- Continue after a successful death-protection pop; stop after an unsaved lethal event.
- Carry forward health, absorption, hurt state, armor durability/effective mitigation, effects, and remaining protection.
- `finalDamage` is `DamageStage.HEALTH_DAMAGE` when recorded, else zero. Add `DamageTrace.has(DamageStage)` if needed.
- `firstLethalEventId` is the first event leaving health 0 without protection consumption.
- Do not tick regeneration/periodic effects yet; later tasks own cadence.

## RED tests

1. health 10, raw 6 at tick 0 and raw 6 at tick 21 -> dead.
2. same exact impact window raw 4 and raw 12 -> raw 12 is chosen first as the worse order.
3. health 5 with one generic protection, raw 10 at tick 0 and raw 10 at tick 21 -> one pop, then dead.
4. raw 12 then raw 4 in the same strong-cooldown moment -> second hit rejected.
5. armor durability damage from event 1 changes event 2 mitigation after cooldown expiry.

Use TDD: tests first, verify timeline classes are missing, then implement minimum pure code and run full CI.
