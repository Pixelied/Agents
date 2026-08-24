package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.prediction.MovementSample;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class StrategicSnapshotTest {
    @Test
    void snapshotDefensivelyCopiesRevisionHistoryAndProtectionInputs() {
        UUID self = UUID.fromString("00000000-0000-0000-0000-000000008001");
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000008002");
        CombatSnapshot combat = new CombatSnapshot(
            4L,
            self,
            CombatRegion.empty(),
            Map.of(self, SimCombatant.testPlayer(20.0f), target, SimCombatant.testPlayer(20.0f)),
            List.of(),
            Map.of(),
            InventoryState.empty(),
            TimingState.unknown()
        );

        Map<UUID, Long> revisions = new HashMap<>();
        revisions.put(target, 7L);
        List<MovementSample> samples = new ArrayList<>();
        samples.add(new MovementSample(100L, Vec3.ZERO, Vec3.ZERO));
        Map<UUID, List<MovementSample>> history = new HashMap<>();
        history.put(target, samples);
        Set<UUID> protectedIds = new HashSet<>();
        protectedIds.add(target);

        StrategicSnapshot snapshot = new StrategicSnapshot(
            11L,
            4L,
            9L,
            13L,
            200L,
            self,
            revisions,
            combat,
            history,
            protectedIds,
            new TargetProtectionPolicyConfig(protectedIds, true, 0.5f),
            TimingSnapshot.empty(200L)
        );

        revisions.put(target, 99L);
        samples.add(new MovementSample(150L, Vec3.ZERO, Vec3.ZERO));
        protectedIds.clear();

        assertEquals(7L, snapshot.targetRevisions().get(target));
        assertEquals(1, snapshot.movementHistory().get(target).size());
        assertEquals(Set.of(target), snapshot.protectedPlayerIds());
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.targetRevisions().put(target, 100L));
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.movementHistory().get(target).add(
                new MovementSample(175L, Vec3.ZERO, Vec3.ZERO)
            ));
    }
}
