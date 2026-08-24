package dev.adrien.spearclient.network;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CollisionProbe {
    private CollisionProbe() {}

    public static boolean isPositionClear(LocalPlayer player, Vec3 position) {
        if (player == null || position == null || !finite(position)) {
            return false;
        }
        Level level = player.level();
        if (level == null) {
            return false;
        }
        Vec3 offset = position.subtract(player.position());
        AABB translatedBox = player.getBoundingBox().move(offset);
        return level.noCollision(player, translatedBox);
    }

    public static boolean isSegmentClear(LocalPlayer player, Vec3 from, Vec3 to, double step) {
        if (player == null || from == null || to == null
            || !finite(from) || !finite(to)
            || !Double.isFinite(step) || step <= 0.0) {
            return false;
        }

        Vec3 delta = to.subtract(from);
        double distance = delta.length();
        int samples = Math.max(1, (int)Math.ceil(distance / step));
        for (int i = 0; i <= samples; i++) {
            double alpha = i / (double)samples;
            if (!isPositionClear(player, from.add(delta.scale(alpha)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
