package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Vec3Snapshot;

import java.util.Objects;

public record CoverCandidate(Vec3Snapshot blockPos, String blockId, int sourceInventoryIndex) {
    public CoverCandidate {
        blockPos = Objects.requireNonNull(blockPos, "blockPos");
        blockId = Objects.requireNonNull(blockId, "blockId");
        if (blockId.isBlank()) throw new IllegalArgumentException("blockId must not be blank");
        if (sourceInventoryIndex < 0 || sourceInventoryIndex > 40) {
            throw new IllegalArgumentException("sourceInventoryIndex must be in [0, 40]");
        }
    }
}
