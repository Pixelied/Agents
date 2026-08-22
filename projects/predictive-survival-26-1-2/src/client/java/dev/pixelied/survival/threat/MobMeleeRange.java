package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;

import java.util.Optional;

/** Mirrors Mob.isWithinMeleeAttackRange/getAttackBoundingBox for immutable snapshots. */
public final class MobMeleeRange {
    private MobMeleeRange() {}

    public static boolean isWithin(
        AabbSnapshot mobBox,
        Optional<AabbSnapshot> vehicleBox,
        AabbSnapshot targetHitbox,
        double minRange,
        double maxRange,
        double postExpansionDeflate
    ) {
        if (maxRange < 0d || minRange < 0d || maxRange < minRange) return false;
        AabbSnapshot base = vehicleBox.map(vehicle -> horizontalUnion(mobBox, vehicle)).orElse(mobBox);
        AabbSnapshot max = expandHorizontal(base, maxRange, postExpansionDeflate);
        if (!intersects(max, targetHitbox)) return false;
        if (minRange <= 0d) return true;
        AabbSnapshot min = expandHorizontal(base, minRange, postExpansionDeflate);
        return !intersects(min, targetHitbox);
    }

    private static AabbSnapshot horizontalUnion(AabbSnapshot own, AabbSnapshot vehicle) {
        return new AabbSnapshot(
            Math.min(own.minX(), vehicle.minX()), own.minY(), Math.min(own.minZ(), vehicle.minZ()),
            Math.max(own.maxX(), vehicle.maxX()), own.maxY(), Math.max(own.maxZ(), vehicle.maxZ())
        );
    }

    private static AabbSnapshot expandHorizontal(AabbSnapshot box, double expansion, double deflate) {
        double amount = expansion - Math.max(0d, deflate);
        return new AabbSnapshot(
            box.minX() - amount, box.minY(), box.minZ() - amount,
            box.maxX() + amount, box.maxY(), box.maxZ() + amount
        );
    }

    private static boolean intersects(AabbSnapshot a, AabbSnapshot b) {
        return a.minX() < b.maxX() && a.maxX() > b.minX()
            && a.minY() < b.maxY() && a.maxY() > b.minY()
            && a.minZ() < b.maxZ() && a.maxZ() > b.minZ();
    }
}
