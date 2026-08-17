package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.block.Blocks;

public final class CandidateGenerator {
    private final CandidateFeatureEstimator estimator;
    private final TacticalInterestDetector interestDetector;

    public CandidateGenerator(CandidateFeatureEstimator estimator) {
        this(estimator, new TacticalInterestDetector());
    }

    public CandidateGenerator(CandidateFeatureEstimator estimator, TacticalInterestDetector interestDetector) {
        this.estimator = Objects.requireNonNull(estimator, "estimator");
        this.interestDetector = Objects.requireNonNull(interestDetector, "interestDetector");
    }

    public List<Candidate> generate(CombatState state) {
        List<Candidate> result = new ArrayList<>();

        for (var crystal : state.crystals()) {
            addIfLegal(result, state, new AttackKnownCrystal(crystal.entityId()), CandidateCategory.CRYSTAL_ATTACK);
        }

        for (var entry : state.base().region().states().entrySet()) {
            var block = entry.getValue();
            if (block.is(Blocks.OBSIDIAN) || block.is(Blocks.BEDROCK)) {
                addIfLegal(result, state, new PlaceCrystal(entry.getKey()), CandidateCategory.CRYSTAL_PLACEMENT);
            }
        }

        for (var entry : state.anchors().entrySet()) {
            if (entry.getValue().charged()) {
                addIfLegal(result, state, new DetonateAnchor(entry.getKey()), CandidateCategory.ANCHOR_DETONATION);
            }
            if (entry.getValue().charges() < 4) {
                addIfLegal(result, state, new ChargeAnchor(entry.getKey()), CandidateCategory.ANCHOR_SETUP);
            }
        }

        addIfLegal(result, state, new Wait(1), CandidateCategory.WAIT);
        return List.copyOf(result);
    }

    private void addIfLegal(
        List<Candidate> result,
        CombatState state,
        CombatAction action,
        CandidateCategory category
    ) {
        if (!action.check(state).legal()) {
            return;
        }
        CandidateFeatures features = estimator.estimate(state, action, category);
        TacticalInterest interest = interestDetector.detect(state, action, features);
        result.add(new Candidate(action, category, features, interest));
    }
}
