package dev.adrien.spearclient.network;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public record MovementPath(Vec3 origin, List<Vec3> positions) {
    private static final int MAX_PACKET_POSITIONS = 8;

    public MovementPath {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(positions, "positions");
        requireFinite(origin, "origin");
        if (positions.size() > MAX_PACKET_POSITIONS) {
            throw new IllegalArgumentException("movement path exceeds 8 packet positions");
        }
        positions = List.copyOf(positions);
        for (Vec3 position : positions) {
            Objects.requireNonNull(position, "position");
            requireFinite(position, "position");
            if (origin.distanceToSqr(position)
                > MovementEnvelope.CONSERVATIVE_RADIUS * MovementEnvelope.CONSERVATIVE_RADIUS + 1e-9) {
                throw new IllegalArgumentException("conservative movement position exceeds radius");
            }
        }
    }

    public static MovementPath of(Vec3 origin, List<Vec3> packetPositions) {
        return new MovementPath(origin, packetPositions);
    }

    public static MovementPath conservativeBackReturn(Vec3 origin, Vec3 look, double backDistance) {
        Vec3 direction = normalizedDirection(look);
        if (!Double.isFinite(backDistance) || backDistance < 0.0) {
            throw new IllegalArgumentException("backDistance must be finite and non-negative");
        }
        return of(origin, List.of(origin.subtract(direction.scale(backDistance)), origin));
    }

    public static MovementPath conservativeReach(Vec3 origin, Vec3 look, double stageDistance) {
        Vec3 direction = normalizedDirection(look);
        if (!Double.isFinite(stageDistance) || stageDistance < 0.0) {
            throw new IllegalArgumentException("stageDistance must be finite and non-negative");
        }
        Vec3 offset = direction.scale(stageDistance);
        return of(origin, List.of(origin.subtract(offset), origin.add(offset), origin));
    }

    private static Vec3 normalizedDirection(Vec3 look) {
        Objects.requireNonNull(look, "look");
        requireFinite(look, "look");
        if (look.lengthSqr() == 0.0) {
            throw new IllegalArgumentException("look must be non-zero");
        }
        return look.normalize();
    }

    private static void requireFinite(Vec3 value, String name) {
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must contain finite coordinates");
        }
    }
}
