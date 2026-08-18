package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.action.SelectHotbarSlot;
import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class CandidateGenerator {
    private static final int SETUP_HORIZONTAL_RADIUS = 2;
    private static final int SETUP_VERTICAL_RADIUS = 1;
    private static final int SETUP_HORIZONTAL_DISTANCE_SQUARED = 5;

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

        addRelevantHotbarSelections(result, state);

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

        addTargetLocalSetupCandidates(result, state);
        addIfLegal(result, state, new Wait(1), CandidateCategory.WAIT);
        return List.copyOf(result);
    }

    private void addTargetLocalSetupCandidates(List<Candidate> result, CombatState state) {
        if (!state.hasSpatialState()) {
            return;
        }

        BlockPos targetBlock = BlockPos.containing(state.targetSpatial().position());
        for (int dy = -SETUP_VERTICAL_RADIUS; dy <= SETUP_VERTICAL_RADIUS; dy++) {
            for (int dx = -SETUP_HORIZONTAL_RADIUS; dx <= SETUP_HORIZONTAL_RADIUS; dx++) {
                for (int dz = -SETUP_HORIZONTAL_RADIUS; dz <= SETUP_HORIZONTAL_RADIUS; dz++) {
                    if (dx * dx + dz * dz > SETUP_HORIZONTAL_DISTANCE_SQUARED) {
                        continue;
                    }
                    BlockPos pos = targetBlock.offset(dx, dy, dz);
                    addIfLegal(result, state, new PlaceAnchor(pos), CandidateCategory.ANCHOR_SETUP);
                    addIfLegal(result, state, new PlaceObsidian(pos), CandidateCategory.SUPPORT_OBSIDIAN);
                }
            }
        }
    }

    private void addRelevantHotbarSelections(List<Candidate> result, CombatState state) {
        int selected = state.inventory().selectedHotbarSlot();
        for (var entry : state.inventory().hotbarItems().entrySet()) {
            int slot = entry.getKey();
            if (slot == selected) {
                continue;
            }
            CandidateCategory category = selectionCategory(entry.getValue());
            if (category != null) {
                addIfLegal(result, state, new SelectHotbarSlot(slot), category);
            }
        }

        boolean selectedGlowstone = state.inventory().selectedItem().filter(Items.GLOWSTONE::equals).isPresent();
        boolean offhandGlowstone = state.inventory().offhandItem().filter(Items.GLOWSTONE::equals).isPresent();
        boolean hasDetonatableAnchor = state.anchors().values().stream().anyMatch(anchor -> anchor.charged() && anchor.charges() < 4);
        if (selectedGlowstone && !offhandGlowstone && hasDetonatableAnchor) {
            for (int slot = 0; slot <= 8; slot++) {
                if (slot != selected && !state.inventory().hotbarItems().containsKey(slot)) {
                    addIfLegal(result, state, new SelectHotbarSlot(slot), CandidateCategory.ANCHOR_DETONATION);
                    break;
                }
            }
        }
    }

    private static CandidateCategory selectionCategory(Item item) {
        if (item == Items.END_CRYSTAL) {
            return CandidateCategory.CRYSTAL_PLACEMENT;
        }
        if (item == Items.GLOWSTONE || item == Items.RESPAWN_ANCHOR) {
            return CandidateCategory.ANCHOR_SETUP;
        }
        if (item == Items.OBSIDIAN) {
            return CandidateCategory.SUPPORT_OBSIDIAN;
        }
        return null;
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
