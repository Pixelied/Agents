package dev.adrien.spearclient.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class SpearTargeting {
    private SpearTargeting() {}

    public static Player findTarget(Minecraft client, double acquisitionRange, boolean teamCheck) {
        Player self = client.player;
        if (self == null || client.level == null || acquisitionRange <= 0.0) {
            return null;
        }

        Vec3 eye = self.getEyePosition();
        Vec3 look = self.getLookAngle().normalize();
        Vec3 rayEnd = eye.add(look.scale(acquisitionRange));
        double maxDistanceSquared = acquisitionRange * acquisitionRange;

        Player best = null;
        TargetScore bestScore = null;

        for (Player candidate : client.level.players()) {
            if (!valid(self, candidate, teamCheck)) {
                continue;
            }

            double distanceSquared = self.position().distanceToSqr(candidate.position());
            if (distanceSquared > maxDistanceSquared) {
                continue;
            }

            Vec3 toTarget = candidate.getEyePosition().subtract(eye);
            Vec3 direction = toTarget.lengthSqr() == 0.0 ? look : toTarget.normalize();
            boolean directRay = candidate.getBoundingBox().inflate(0.3).clip(eye, rayEnd).isPresent();
            double angleCost = 1.0 - look.dot(direction);
            TargetScore score = new TargetScore(directRay, angleCost, distanceSquared);

            if (best == null
                || score.compareTo(bestScore) < 0
                || (score.compareTo(bestScore) == 0 && candidate.getId() < best.getId())) {
                best = candidate;
                bestScore = score;
            }
        }

        return best;
    }

    private static boolean valid(Player self, Player candidate, boolean teamCheck) {
        if (candidate == self || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        return !teamCheck || !self.isAlliedTo(candidate);
    }
}
