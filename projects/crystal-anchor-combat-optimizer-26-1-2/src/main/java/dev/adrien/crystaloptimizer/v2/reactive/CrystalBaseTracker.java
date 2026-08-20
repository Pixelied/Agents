package dev.adrien.crystaloptimizer.v2.reactive;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public final class CrystalBaseTracker {
    private final Map<BlockPos, State> states = new HashMap<>();

    public synchronized void onPlaceSent(BlockPos basePos) {
        states.put(copy(basePos), new State(CrystalBasePhase.PLACE_SENT, -1));
    }

    public synchronized void onSpawn(BlockPos basePos, int entityId) {
        states.put(copy(basePos), new State(CrystalBasePhase.LIVE, entityId));
    }

    public synchronized void onBreakSent(BlockPos basePos, int entityId) {
        BlockPos base = copy(basePos);
        State current = states.get(base);
        if (current == null || current.entityId() != entityId) {
            states.put(base, new State(CrystalBasePhase.INVALID, -1));
            return;
        }
        states.put(base, new State(CrystalBasePhase.BREAK_SENT, entityId));
    }

    public synchronized void onRemoved(BlockPos basePos, int entityId) {
        BlockPos base = copy(basePos);
        State current = states.get(base);
        if (current != null && current.entityId() == entityId) {
            states.put(base, new State(CrystalBasePhase.EMPTY, -1));
        }
    }

    public synchronized void invalidate(BlockPos basePos) {
        states.put(copy(basePos), new State(CrystalBasePhase.INVALID, -1));
    }

    public synchronized CrystalBasePhase phase(BlockPos basePos) {
        State state = states.get(copy(basePos));
        return state == null ? CrystalBasePhase.EMPTY : state.phase();
    }

    public synchronized OptionalInt liveEntityId(BlockPos basePos) {
        State state = states.get(copy(basePos));
        if (state == null || state.phase() != CrystalBasePhase.LIVE || state.entityId() < 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(state.entityId());
    }

    public synchronized void clear() {
        states.clear();
    }

    private static BlockPos copy(BlockPos pos) {
        return Objects.requireNonNull(pos, "basePos").immutable();
    }

    private record State(CrystalBasePhase phase, int entityId) {
    }
}
