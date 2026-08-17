# Task 2 Brief — Immutable simulation domain types

## Requirements

Create the core immutable primitives and normalized damage/player snapshot records used by later pure simulation tasks.

### Locked primitives

```java
public record DamageRange(float min, float max) {
    public static DamageRange exact(float value);
    public DamageRange scale(float factor);
    public DamageRange subtractFloorZero(float value);
}
public record TickWindow(long earliest, long latest) {
    public boolean contains(long tick);
    public boolean overlaps(TickWindow other);
}
public enum Confidence { EXACT, MATCHED, BOUNDED, POTENTIAL, UNKNOWN }
public enum DifficultySnapshot { PEACEFUL, EASY, NORMAL, HARD }
public record Vec3Snapshot(double x, double y, double z) {}
public record AabbSnapshot(double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ) {}
```

### Damage source

```java
public record DamageSourceSnapshot(
    DamageRange rawDamage,
    java.util.Set<DamageFlag> flags,
    boolean scalesWithDifficulty,
    float freezingMultiplier,
    boolean piercingProjectile,
    java.util.Optional<Vec3Snapshot> sourcePosition,
    String sourceKey
) {}
```

`DamageFlag` includes at least: `BYPASSES_INVULNERABILITY`, `BYPASSES_COOLDOWN`, `BYPASSES_ARMOR`, `BYPASSES_EFFECTS`, `BYPASSES_RESISTANCE`, `BYPASSES_ENCHANTMENTS`, `IS_FIRE`, `DAMAGES_HELMET`, `IS_FREEZING`.

Create source-independent immutable records for `BlockingSnapshot`, `MitigationSnapshot`, `StatusEffectsSnapshot`, `DeathProtectionSnapshot`, and `HurtState`. Task 3/4 may extend their fields when exact vanilla stages are implemented, but Task 2 must give later code stable immutable ownership and null/shape validation.

`PlayerSnapshot` carries health, absorption, player/ability invulnerability state, dead/dying state, difficulty, mitigation/effects/blocking, hurt state, death protection, AABB, position, velocity, and immutable equipment snapshot data. Do not store Minecraft entity/world references in these pure domain types.

## TDD RED tests

At minimum:

```java
@Test void damageRangeRejectsInvertedBounds() {
    assertThrows(IllegalArgumentException.class, () -> new DamageRange(8f, 4f));
}
@Test void subtractFloorsAtZero() {
    assertEquals(new DamageRange(0f, 3f), new DamageRange(2f, 5f).subtractFloorZero(2f));
}
@Test void tickWindowOverlapIncludesSharedBoundary() {
    assertTrue(new TickWindow(10, 12).overlaps(new TickWindow(12, 15)));
}
```

Also test defensive copying for mutable collections carried by snapshots.

First commit the tests without the production types and verify CI fails for the missing domain classes. Then implement the minimal records, re-run the focused test/full build, and keep all types client-independent.

## Verification

Authoritative runner: GitHub Actions on the feature branch. Focused local equivalent is:

```bash
./gradlew test --tests dev.pixelied.survival.core.DomainTypesTest
```

Full task verification also runs the complete existing test/build workflow.
