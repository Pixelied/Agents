package dev.adrien.crystaloptimizer.client;

import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.v2.state.StrategicResult;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class StrategicPlannerServiceFixtures {
    static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000008101");
    static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000008102");

    static StrategicSnapshot snapshot(long snapshotId) {
        CombatSnapshot combat = new CombatSnapshot(
            snapshotId,
            SELF,
            CombatRegion.empty(),
            Map.of(SELF, SimCombatant.testPlayer(20.0f), TARGET, SimCombatant.testPlayer(20.0f)),
            List.of(),
            Map.of(),
            InventoryState.empty(),
            TimingState.unknown()
        );
        return new StrategicSnapshot(
            snapshotId,
            snapshotId,
            snapshotId,
            snapshotId,
            snapshotId * 100L,
            SELF,
            Map.of(TARGET, snapshotId),
            combat,
            Map.of(),
            Set.of(),
            TargetProtectionPolicyConfig.defaults(),
            TimingSnapshot.empty(snapshotId * 100L)
        );
    }

    static StrategicResult result(StrategicSnapshot snapshot) {
        return new StrategicResult(
            snapshot.snapshotId(),
            snapshot.worldRevision(),
            snapshot.inventoryRevision(),
            snapshot.configRevision(),
            TARGET,
            DamageMap.empty(
                TARGET,
                snapshot.targetRevisions().getOrDefault(TARGET, 0L),
                snapshot.worldRevision()
            )
        );
    }

    private StrategicPlannerServiceFixtures() {
    }
}
