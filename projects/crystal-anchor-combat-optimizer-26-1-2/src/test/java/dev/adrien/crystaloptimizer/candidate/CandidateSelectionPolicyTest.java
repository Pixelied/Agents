package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CandidateSelectionPolicyTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-00000000c501");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-00000000c502");

    @Test
    void disabledCrystalCandidatesCannotConsumeAnchorQuota() {
        CandidateSelectionPolicy policy = CandidateSelectionPolicy.v3Defaults();
        List<Candidate> generated = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            generated.add(candidate(CandidateCategory.CRYSTAL_PLACEMENT, 30.0 - i * 0.01));
        }
        Candidate anchor = candidate(CandidateCategory.ANCHOR_DETONATION, 12.0);
        generated.add(anchor);

        List<Candidate> selected = policy.select(testState(), generated, false, true);

        assertTrue(selected.contains(anchor));
        assertTrue(selected.stream().noneMatch(
            candidate -> candidate.category() == CandidateCategory.CRYSTAL_PLACEMENT
        ));
    }

    @Test
    void enabledAnchorKeepsProtectedQuotaAfterMoreThanNinetySixCrystalCandidates() {
        CandidateSelectionPolicy policy = CandidateSelectionPolicy.v3Defaults();
        List<Candidate> generated = denseMixedCandidates(120, 1);

        List<Candidate> selected = policy.select(testState(), generated, true, true);

        assertEquals(
            1,
            selected.stream()
                .filter(candidate -> candidate.category() == CandidateCategory.ANCHOR_DETONATION)
                .count()
        );
    }

    private static List<Candidate> denseMixedCandidates(int crystals, int anchors) {
        ArrayList<Candidate> result = new ArrayList<>();
        for (int i = 0; i < crystals; i++) {
            result.add(candidate(CandidateCategory.CRYSTAL_PLACEMENT, 40.0 - i * 0.01));
        }
        for (int i = 0; i < anchors; i++) {
            result.add(candidate(CandidateCategory.ANCHOR_DETONATION, 15.0 - i * 0.01));
        }
        return List.copyOf(result);
    }

    private static Candidate candidate(CandidateCategory category, double damage) {
        return new Candidate(
            new Wait(1),
            category,
            new CandidateFeatures(
                2.0,
                3.0,
                0.8,
                true,
                10.0,
                false,
                0,
                0,
                0.0,
                damage,
                1.0
            ),
            TacticalInterest.NONE
        );
    }

    private static CombatState testState() {
        CombatSnapshot snapshot = new CombatSnapshot(
            10L,
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
            new LegalitySnapshot(new Vec3(0.5, 65.0, -2.0), 6.0, 6.0, List.of(), false)
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }
}
