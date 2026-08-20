package dev.adrien.crystaloptimizer.v2.reactive;

import java.util.UUID;
import net.minecraft.core.BlockPos;

public sealed interface CombatEvent {
    long timestampNanos();

    record CrystalSpawned(int entityId, BlockPos basePos, long timestampNanos) implements CombatEvent {
        public CrystalSpawned {
            basePos = basePos.immutable();
        }
    }

    record CrystalRemoved(int entityId, BlockPos basePos, long timestampNanos) implements CombatEvent {
        public CrystalRemoved {
            basePos = basePos.immutable();
        }
    }

    record TotemPopped(UUID targetId, long timestampNanos) implements CombatEvent {}

    record EquipmentChanged(UUID targetId, long timestampNanos) implements CombatEvent {}

    record BlockAcked(int sequence, long timestampNanos) implements CombatEvent {}

    record BlockChanged(BlockPos pos, long timestampNanos) implements CombatEvent {
        public BlockChanged {
            pos = pos.immutable();
        }
    }

    record InventoryChanged(long inventoryRevision, long timestampNanos) implements CombatEvent {}

    record TargetMoved(UUID targetId, long targetRevision, long timestampNanos) implements CombatEvent {}

    record ConfigChanged(long configRevision, long timestampNanos) implements CombatEvent {}
}
