package dev.adrien.crystaloptimizer.v2.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class DamageMapTest {
    @Test
    void unrelatedGeometryChangePreservesEntriesAndTargetMoveDropsPositionEntries() {
        UUID target = UUID.randomUUID();
        BlockPos firstDependency = new BlockPos(4, 64, 7);
        BlockPos secondDependency = new BlockPos(8, 64, 7);
        DamageMap map = new DamageMap(
            target,
            7L,
            3L,
            Map.of(
                "first", opportunity("first", true, Set.of(firstDependency)),
                "second", opportunity("second", false, Set.of(secondDependency))
            )
        );

        DamageMap unaffected = map.invalidateGeometry(Set.of(new BlockPos(100, 20, 100)));
        assertEquals(2, unaffected.opportunities().size());

        DamageMap changed = map.invalidateGeometry(Set.of(firstDependency));
        assertFalse(changed.opportunities().containsKey("first"));
        assertTrue(changed.opportunities().containsKey("second"));

        DamageMap moved = map.withTargetRevision(map.targetRevision() + 1L);
        assertTrue(moved.opportunities().values().stream()
            .noneMatch(DamageOpportunity::positionDependent));
    }

    @Test
    void mapPreservesStableInsertionOrderForDiagnostics() {
        LinkedHashMap<String, DamageOpportunity> entries = new LinkedHashMap<>();
        entries.put("a", opportunity("a", false, Set.of()));
        entries.put("b", opportunity("b", false, Set.of()));
        DamageMap map = new DamageMap(UUID.randomUUID(), 1L, 1L, entries);
        assertEquals(List.of("a", "b"), List.copyOf(map.opportunities().keySet()));
    }

    private static DamageOpportunity opportunity(
        String id,
        boolean positionDependent,
        Set<BlockPos> dependencies
    ) {
        return new DamageOpportunity(
            id,
            new FixedActionSequence(List.of(new DetonateAnchor(BlockPos.ZERO))),
            DamageEstimate.exact(12.0f, 3L, 4L),
            3.0f,
            SequenceTiming.immediate(),
            false,
            false,
            positionDependent,
            dependencies
        );
    }
}
