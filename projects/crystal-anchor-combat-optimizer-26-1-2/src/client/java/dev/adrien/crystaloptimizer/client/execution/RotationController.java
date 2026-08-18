package dev.adrien.crystaloptimizer.client.execution;

import dev.adrien.crystaloptimizer.execution.RotationMath;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import dev.adrien.crystaloptimizer.execution.RotationStep;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class RotationController {
    private static final float REACHED_EPSILON_DEGREES = 0.05f;

    private final Minecraft minecraft;
    private final float maxDegreesPerUpdate;

    public RotationController(Minecraft minecraft, float maxDegreesPerUpdate) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        if (!Float.isFinite(maxDegreesPerUpdate) || maxDegreesPerUpdate <= 0.0f) {
            throw new IllegalArgumentException("maxDegreesPerUpdate must be positive and finite");
        }
        this.maxDegreesPerUpdate = maxDegreesPerUpdate;
    }

    public boolean updateToward(Vec3 target, RotationMode mode, boolean committed) {
        Objects.requireNonNull(target, "target");
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }

        Vec3 delta = target.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return applyAngles(targetYaw, targetPitch, mode, committed);
    }

    public boolean applyAngles(float targetYaw, float targetPitch, RotationMode mode, boolean committed) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }

        RotationStep step = RotationMath.next(
            player.getYRot(),
            player.getXRot(),
            targetYaw,
            targetPitch,
            Objects.requireNonNull(mode, "mode"),
            committed,
            maxDegreesPerUpdate
        );
        player.setYRot(step.yaw());
        player.setXRot(step.pitch());

        return yawDistance(step.yaw(), targetYaw) <= REACHED_EPSILON_DEGREES
            && Math.abs(step.pitch() - targetPitch) <= REACHED_EPSILON_DEGREES;
    }

    private static float yawDistance(float left, float right) {
        float delta = (right - left) % 360.0f;
        if (delta > 180.0f) {
            delta -= 360.0f;
        } else if (delta <= -180.0f) {
            delta += 360.0f;
        }
        return Math.abs(delta);
    }
}
