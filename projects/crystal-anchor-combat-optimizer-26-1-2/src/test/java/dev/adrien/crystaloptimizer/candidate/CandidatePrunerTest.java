package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidatePrunerTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000012");

    private final CandidatePruner pruner = new CandidatePruner();

    @Test
    void dominatedCandidateIsRemoved() {
        Candidate better = candidate(20.0, 4.0, 0, 1, 0.3, TacticalInterest.NONE);
        Candidate worse = candidate(15.0, 7.0, 1, 2, 0.1, TacticalInterest.NONE);

        var kept = pruner.prune(testState(), List.of(better, worse), budget());

        assertEquals(List.of(better), kept);
    }

    @Test
    void weakerCrystalSurvivesWhenItCreatesStrongerAnchorDelta() {
        Candidate shapedCrystal = candidate(18.0, 3.0, 0, 1, 0.95, TacticalInterest.DAMAGE_STAIRCASE);
        Candidate greedyCrystal = candidate(27.0, 3.0, 0, 1, 0.05, TacticalInterest.NONE);

        var kept = pruner.prune(testState(), List.of(shapedCrystal, greedyCrystal), budget());

        assertTrue(kept.contains(shapedCrystal));
        assertTrue(kept.contains(greedyCrystal));
    }

    @Test
    void supportDamageShapingCandidateSurvivesEvenThoughImmediateDamageIsLower() {
        Candidate greedy = new Candidate(
            new Wait(1),
            CandidateCategory.CRYSTAL_PLACEMENT,
            features(29.0, 3.0, 0, 0, 0.05),
            TacticalInterest.NONE
        );
        Candidate shapingSupport = new Candidate(
            new Wait(1),
            CandidateCategory.SUPPORT_OBSIDIAN,
            features(0.0, 0.0, 0, 1, 1.0),
            TacticalInterest.DAMAGE_SHAPING
        );

        var kept = pruner.prune(testState(), List.of(greedy, shapingSupport), budget());

        assertTrue(kept.contains(greedy));
        assertTrue(kept.contains(shapingSupport));
    }

    @Test
    void perCategoryQuotasPreventCrystalDamageFromStarvingAnchors() {
        Candidate crystalA = new Candidate(new Wait(1), CandidateCategory.CRYSTAL_ATTACK, features(40, 2, 0, 0, 0), TacticalInterest.NONE);
        Candidate crystalB = new Candidate(new Wait(1), CandidateCategory.CRYSTAL_ATTACK, features(39, 2, 0, 0, 0), TacticalInterest.NONE);
        Candidate anchor = new Candidate(new Wait(1), CandidateCategory.ANCHOR_DETONATION, features(20, 2, 0, 0, 1), TacticalInterest.ZERO_FEEDBACK_FINISHER);
        CandidateBudget tiny = new CandidateBudget(1, 1, 1, 1, 1, 1);

        var kept = pruner.prune(testState(), List.of(crystalA, crystalB, anchor), tiny);

        assertEquals(1, kept.stream().filter(c -> c.category() == CandidateCategory.CRYSTAL_ATTACK).count());
        assertTrue(kept.contains(anchor));
    }

    @Test
    void generatorEmitsExistingCrystalAnchorAndWaitCategories() {
        BlockPos anchorPos = new BlockPos(1, 64, 0);
        CombatState state = state(
            CombatRegion.singleBlock(anchorPos, Blocks.RESPAWN_ANCHOR.defaultBlockState()),
            List.of(new KnownCrystal(91, new Vec3(0.5, 65.0, 0.5))),
            Map.of(anchorPos, new AnchorState(1)),
            new InventoryState(0, Map.of(Items.END_CRYSTAL, 4))
        );
        CandidateGenerator generator = new CandidateGenerator(CandidateFeatureEstimator.conservative());

        var raw = generator.generate(state);

        assertTrue(raw.stream().anyMatch(c -> c.category() == CandidateCategory.CRYSTAL_ATTACK));
        assertTrue(raw.stream().anyMatch(c -> c.category() == CandidateCategory.ANCHOR_DETONATION));
        assertTrue(raw.stream().anyMatch(c -> c.category() == CandidateCategory.WAIT));
        assertFalse(raw.stream().anyMatch(c -> !c.action().check(state).legal()));
    }

    private static Candidate candidate(
        double targetDamage,
        double selfDamage,
        int feedbackBoundaries,
        int supportActions,
        double followup,
        TacticalInterest interest
    ) {
        return new Candidate(
            new Wait(1),
            CandidateCategory.CRYSTAL_ATTACK,
            features(targetDamage, selfDamage, feedbackBoundaries, supportActions, followup),
            interest
        );
    }

    private static CandidateFeatures features(
        double targetDamage,
        double selfDamage,
        int feedbackBoundaries,
        int supportActions,
        double followup
    ) {
        return new CandidateFeatures(
            2.0,
            3.0,
            0.8,
            true,
            10.0,
            false,
            feedbackBoundaries,
            supportActions,
            followup,
            targetDamage,
            selfDamage
        );
    }

    private static CandidateBudget budget() {
        return new CandidateBudget(4, 4, 4, 4, 4, 2);
    }

    private static CombatState testState() {
        return state(CombatRegion.empty(), List.of(), Map.of(), InventoryState.empty());
    }

    private static CombatState state(
        CombatRegion region,
        List<KnownCrystal> crystals,
        Map<BlockPos, AnchorState> anchors,
        InventoryState inventory
    ) {
        var snapshot = new CombatSnapshot(
            10L,
            SELF,
            region,
            Map.of(SELF, SimCombatant.testPlayer(20.0f), TARGET, SimCombatant.testPlayer(20.0f)),
            crystals,
            anchors,
            inventory,
            TimingState.unknown(),
            new LegalitySnapshot(new Vec3(0.5, 65.0, -2.0), 6.0, 6.0, List.of(), false)
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }
}
