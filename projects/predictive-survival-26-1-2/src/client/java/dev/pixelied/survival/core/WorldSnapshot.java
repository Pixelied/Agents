package dev.pixelied.survival.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record WorldSnapshot(
    List<EntitySnapshot> entities,
    List<BlockSnapshot> blocks
) {
    public WorldSnapshot {
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }

    public static WorldSnapshot empty() {
        return new WorldSnapshot(List.of(), List.of());
    }

    public record EntitySnapshot(
        String id,
        String typeKey,
        Vec3Snapshot position,
        Vec3Snapshot velocity,
        AabbSnapshot boundingBox,
        Map<String, String> properties
    ) {
        public EntitySnapshot {
            id = Objects.requireNonNull(id, "id");
            typeKey = Objects.requireNonNull(typeKey, "typeKey");
            position = Objects.requireNonNull(position, "position");
            velocity = Objects.requireNonNull(velocity, "velocity");
            boundingBox = Objects.requireNonNull(boundingBox, "boundingBox");
            properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
            if (id.isBlank() || typeKey.isBlank()) throw new IllegalArgumentException("entity id/type must not be blank");
        }
    }

    public record BlockSnapshot(
        Vec3Snapshot position,
        String blockId,
        boolean collision,
        List<AabbSnapshot> collisionBoxes,
        Map<String, String> properties
    ) {
        public BlockSnapshot {
            position = Objects.requireNonNull(position, "position");
            blockId = Objects.requireNonNull(blockId, "blockId");
            collisionBoxes = List.copyOf(Objects.requireNonNull(collisionBoxes, "collisionBoxes"));
            properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
            if (blockId.isBlank()) throw new IllegalArgumentException("blockId must not be blank");
        }

        public BlockSnapshot(
            Vec3Snapshot position,
            String blockId,
            boolean collision,
            Map<String, String> properties
        ) {
            this(position, blockId, collision, List.of(), properties);
        }
    }
}
