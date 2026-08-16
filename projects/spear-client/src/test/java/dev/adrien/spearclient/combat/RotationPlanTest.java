package dev.adrien.spearclient.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RotationPlanTest {
    @Test
    void positiveZIsZeroYawAndLevelPitch() {
        RotationPlan plan = RotationPlan.toTarget(Vec3.ZERO, new Vec3(0, 0, 10));
        assertEquals(0.0f, plan.yaw(), 1e-4f);
        assertEquals(0.0f, plan.pitch(), 1e-4f);
    }

    @Test
    void positiveXIsNegativeNinetyYaw() {
        RotationPlan plan = RotationPlan.toTarget(Vec3.ZERO, new Vec3(10, 0, 0));
        assertEquals(-90.0f, plan.yaw(), 1e-4f);
        assertEquals(0.0f, plan.pitch(), 1e-4f);
    }

    @Test
    void targetAboveProducesNegativePitch() {
        RotationPlan plan = RotationPlan.toTarget(Vec3.ZERO, new Vec3(0, 10, 10));
        assertEquals(-45.0f, plan.pitch(), 1e-4f);
    }
}
