# Task 11 Brief — Exact explosion prediction and emergency cover

Implement the first source-faithful threat family: vanilla 26.1.2 explosions.

## Exact 26.1.2 source behavior

`ServerExplosion#getSeenPercent(center, entity)` samples the target AABB using:

```text
xs = 1 / (width * 2 + 1)
ys = 1 / (height * 2 + 1)
zs = 1 / (depth * 2 + 1)
xOffset = (1 - floor(1/xs)*xs) / 2
zOffset = (1 - floor(1/zs)*zs) / 2
```

For every `(xx,yy,zz)` from `0..1` inclusive in those increments, raycast from the lerped AABB point plus x/z offsets to the explosion center. Exposure is unobstructed/count.

Entity damage uses:

```text
doubleRadius = radius * 2
dist = distance(entity.position, center) / doubleRadius
pow = (1 - dist) * exposure
rawDamage = ((pow * pow + pow) / 2) * 7 * doubleRadius + 1
```

No damage is emitted when `dist > 1` or radius is effectively zero.

`ServerExplosion#explode()` calls `hurtEntities()` before `interactWithBlocks()`. A legal candidate block that exists when entity damage is evaluated therefore counts for exposure even if the same explosion destroys it afterward.

## Required interfaces

```java
public interface OcclusionView {
    boolean blocksExplosionRay(Vec3Snapshot from, Vec3Snapshot to);
    OcclusionView withCandidateBlock(CoverCandidate candidate);
}
public record CoverCandidate(Vec3Snapshot blockPos, String blockId, int sourceInventoryIndex) {}
public final class ExplosionExposure {
    public float seenPercent(AabbSnapshot target, Vec3Snapshot center, OcclusionView world);
    public float rawEntityDamage(float radius, double distance, float exposure);
}
```

`CoverCandidateEvaluator` evaluates a candidate by adding it to the occlusion view **before** exposure/damage calculation and returns exposure/raw damage.

`ExplosionPredictor` consumes compact `WorldSnapshot` data only. Entity/block snapshots become explosion candidates when their immutable property map supplies the source-observable explosion state (`explosion_radius`, and either `fuse_ticks` or `triggerable=true`). This keeps live game adaptation separate while supporting TNT/minecart TNT, creepers, crystals, fireworks, bed/anchor bad-respawn points, and other source-observable explosion families.

- exact fuse -> `EXACT` impact window at that tick offset;
- no-fuse but legal triggerable state -> `POTENTIAL` immediate window `0..2` ticks (bounded by engine horizon);
- raw source gets `IS_EXPLOSION` and source key from properties or `minecraft:explosion`.

## RED tests

- fully open AABB exposure is 1; fully blocked exposure is 0.
- radius 4, distance 0, exposure 1 -> raw 57.
- candidate cover lowers damage and can turn a tiny-health open-blast death into survival; cover is applied before entity-damage evaluation.
- TNT snapshot with fuse 80 emits exact `TickWindow(80,80)`.
- triggerable end crystal with radius 6 and no fuse emits `POTENTIAL` immediate threat.

Use TDD: tests first, verify explosion/cover classes are missing, then implement the minimum exact math/predictor and full CI.
