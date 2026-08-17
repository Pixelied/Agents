package dev.adrien.crystaloptimizer.sim.model;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public record KnownCrystal(int entityId, Vec3 position) {
    public KnownCrystal {
        if (entityId <= 0) {
            throw new IllegalArgumentException("Known crystal entityId must be a positive server-observed ID");
        }
        Objects.requireNonNull(position, "position");
    }
}
