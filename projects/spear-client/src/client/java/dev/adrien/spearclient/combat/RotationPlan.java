package dev.adrien.spearclient.combat;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public record RotationPlan(float yaw, float pitch) {
    public static RotationPlan toTarget(Vec3 eye, Vec3 target) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(target, "target");
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) {
            throw new IllegalArgumentException("rotation target must use finite coordinates");
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new RotationPlan(yaw, pitch);
    }
}
