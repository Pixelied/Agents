package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.prediction.MovementSample;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Task10PredictionFixtures {
    static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000010001");
    static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000010002");
    static final long TICK_NANOS = 50_000_000L;

    static StrategicSnapshot snapshot(
        long snapshotId,
        long capturedAtNanos,
        Vec3 targetPosition,
        List<MovementSample> history
    ) {
        CombatSnapshot combat = new CombatSnapshot(
            snapshotId,
            SELF,
            CombatRegion.empty(),
            Map.of(
                SELF, SimCombatant.testPlayer(20.0f),
                TARGET, SimCombatant.testPlayer(20.0f)
            ),
            List.of(),
            Map.of(),
            InventoryState.empty(),
            TimingState.unknown(),
            new LegalitySnapshot(Vec3.ZERO, 5.0, 5.0, List.of(), false),
            Map.of(
                SELF, spatial(new Vec3(0.0, 64.0, 0.0), Vec3.ZERO),
                TARGET, spatial(targetPosition, history.isEmpty() ? Vec3.ZERO : history.getLast().velocity())
            ),
            Difficulty.NORMAL
        );
        return new StrategicSnapshot(
            snapshotId,
            snapshotId,
            snapshotId,
            snapshotId,
            capturedAtNanos,
            SELF,
            Map.of(TARGET, snapshotId),
            combat,
            history.isEmpty() ? Map.of() : Map.of(TARGET, history),
            Set.of(),
            TargetProtectionPolicyConfig.defaults(),
            TimingSnapshot.empty(capturedAtNanos)
        );
    }

    static List<MovementSample> movingAwayHistory() {
        return List.of(
            new MovementSample(0L, new Vec3(2.0, 64.0, 0.0), new Vec3(0.40, 0.0, 0.0)),
            new MovementSample(TICK_NANOS, new Vec3(2.4, 64.0, 0.0), new Vec3(0.40, 0.0, 0.0)),
            new MovementSample(TICK_NANOS * 2L, new Vec3(2.8, 64.0, 0.0), new Vec3(0.40, 0.0, 0.0))
        );
    }

    static CombatantSpatialState spatial(Vec3 position, Vec3 velocity) {
        return new CombatantSpatialState(position, box(position), velocity);
    }

    static AABB box(Vec3 position) {
        return new AABB(
            position.x - 0.3,
            position.y,
            position.z - 0.3,
            position.x + 0.3,
            position.y + 1.8,
            position.z + 0.3
        );
    }

    private Task10PredictionFixtures() {
    }
}
