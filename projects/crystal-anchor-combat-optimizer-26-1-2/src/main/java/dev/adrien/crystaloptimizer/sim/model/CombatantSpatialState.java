package dev.adrien.crystaloptimizer.sim.model;

import java.util.Objects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record CombatantSpatialState(
    Vec3 position,
    AABB boundingBox,
    Vec3 velocity
) {
    public CombatantSpatialState {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(boundingBox, "boundingBox");
        Objects.requireNonNull(velocity, "velocity");
    }
}
