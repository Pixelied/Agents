# Task 3 Brief — Vanilla preprocessing, blocking, and hurt-cooldown order

Implement only the source-confirmed stages before armor/effect/enchantment/absorption processing.

## Exact order

1. player/gamerule invulnerability;
2. ability invulnerability unless `BYPASSES_INVULNERABILITY`;
3. dead/dying rejection;
4. difficulty scaling when the source scales;
5. zero rejection;
6. Fire Resistance early rejection for `IS_FIRE`;
7. clamp negative damage to zero;
8. item blocking if active and not bypassed/piercing;
9. `IS_FREEZING` multiplier;
10. `DAMAGES_HELMET` reduction (`* 0.75`) when a helmet is present;
11. sanitize non-finite damage to `Float.MAX_VALUE`;
12. hurt cooldown / raw pre-armor `lastHurt`:
   - while `invulnerableTime > 10`, unless `BYPASSES_COOLDOWN`, reject damage `<= lastHurt`; otherwise process only `damage - lastHurt` and update `lastHurt` to the new pre-armor value;
   - outside that strong window, process full damage, set `lastHurt = damage`, and set `invulnerableTime = 20`.

Unknown hurt-state confidence must not grant lethal iframe reduction; use zero credit until Task 5 can maintain a trusted server shadow state.

## Files

Create `DamageStage`, `DamageTrace`, `DamageResult`, `VanillaDamageMath`, `DamageSimulator`, and `DamageSimulatorPreprocessingTest`.

`DamageResult` contract:

```java
public record DamageResult(PlayerSnapshot after, DamageTrace trace,
                           boolean rejected, boolean deathProtectionConsumed) {}
```

`DamageTrace` exposes `before(DamageStage)` and `after(DamageStage)`.

## Required RED tests

Cover at least:
- Easy difficulty raw 10 -> 6 after `DIFFICULTY`;
- Fire Resistance rejects `IS_FIRE` before hurt cooldown changes state;
- strong cooldown `lastHurt=5`, incoming raw 12 -> 7 after `HURT_COOLDOWN`, then raw `lastHurt=12`;
- a fully blocked hit establishes zero `lastHurt`, not the original raw value;
- synthetic `BYPASSES_COOLDOWN` processes full incoming damage;
- freezing multiplier precedes helmet `*0.75` reduction;
- non-finite input is sanitized to `Float.MAX_VALUE` before hurt-cooldown handling.

The test must fail first because Task 3 simulator/trace classes do not exist, then the minimal implementation makes the focused and full CI build green.

Task 4 owns armor/toughness, Resistance, enchantments, absorption, durability and death protection; do not implement those early.
