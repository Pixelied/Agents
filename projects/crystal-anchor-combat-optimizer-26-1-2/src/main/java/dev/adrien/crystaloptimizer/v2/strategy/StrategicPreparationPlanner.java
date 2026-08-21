package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.ActionStatus;
import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.action.SelectHotbarSlot;
import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.candidate.Candidate;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class StrategicPreparationPlanner {
    private static final int MAX_SETUP_SEQUENCES_PER_KIND = 24;

    private final CandidateGenerator candidates;

    public StrategicPreparationPlanner(CandidateGenerator candidates) {
        this.candidates = Objects.requireNonNull(candidates, "candidates");
    }

    /** Compatibility entry point used by older tests/callers. */
    public Optional<List<CombatAction>> plan(CombatState state, OptimizerConfig config) {
        return planSequences(state, config).stream()
            .findFirst()
            .map(PreparationSequence::actions);
    }

    public List<PreparationSequence> planSequences(CombatState state, OptimizerConfig config) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(config, "config");

        ArrayList<PreparationSequence> result = new ArrayList<>();
        if (config.crystals() && state.inventory().count(Items.END_CRYSTAL) > 0) {
            addExistingCrystalBaseSequences(result, state);
        }
        if (config.anchors()) {
            addExistingAnchorSequences(result, state);
        }
        if (config.crystals()
            && state.inventory().count(Items.OBSIDIAN) > 0
            && state.inventory().count(Items.END_CRYSTAL) > 0) {
            addNewCrystalSupportSequences(result, state);
        }
        if (config.anchors()
            && state.inventory().count(Items.RESPAWN_ANCHOR) > 0
            && state.inventory().count(Items.GLOWSTONE) > 0) {
            addNewAnchorSequences(result, state);
        }

        result.sort(Comparator
            .comparingDouble((PreparationSequence sequence) -> sequence.resources().cost())
            .thenComparingInt(sequence -> sequence.actions().size())
            .thenComparingInt(sequence -> sequence.geometryDependencies().hashCode()));
        return List.copyOf(result);
    }

    private void addExistingCrystalBaseSequences(
        List<PreparationSequence> result,
        CombatState state
    ) {
        Optional<SelectedState> crystalSelected = selectItem(state, Items.END_CRYSTAL);
        if (crystalSelected.isEmpty()) {
            return;
        }
        SelectedState selected = crystalSelected.orElseThrow();
        if (selected.actions().isEmpty()) {
            // Direct place opportunities are already built by ClientDamageMapBuilder.
            return;
        }

        int added = 0;
        for (Candidate candidate : candidates.generate(selected.state())) {
            if (!(candidate.action() instanceof PlaceCrystal place)) {
                continue;
            }
            ArrayList<CombatAction> actions = new ArrayList<>(selected.actions());
            actions.add(place);
            result.add(new PreparationSequence(
                actions,
                crystalExplosion(place.basePos()),
                ResourceChain.of(Map.of(Items.END_CRYSTAL, 1), 1.0),
                Set.of(place.basePos())
            ));
            if (++added >= MAX_SETUP_SEQUENCES_PER_KIND) {
                break;
            }
        }
    }

    private void addNewCrystalSupportSequences(
        List<PreparationSequence> result,
        CombatState state
    ) {
        Optional<SelectedState> obsidianSelected = selectItem(state, Items.OBSIDIAN);
        if (obsidianSelected.isEmpty()) {
            return;
        }
        SelectedState selected = obsidianSelected.orElseThrow();

        int added = 0;
        for (Candidate candidate : candidates.generate(selected.state())) {
            if (!(candidate.action() instanceof PlaceObsidian placeSupport)) {
                continue;
            }
            var placed = placeSupport.simulate(selected.state(), SimulationServices.defaults());
            if (placed.status() == ActionStatus.IMPOSSIBLE) {
                continue;
            }
            Optional<SelectedState> crystalSelected = selectItem(placed.state(), Items.END_CRYSTAL);
            if (crystalSelected.isEmpty()) {
                continue;
            }
            SelectedState afterCrystalSelection = crystalSelected.orElseThrow();
            PlaceCrystal placeCrystal = new PlaceCrystal(placeSupport.pos());
            if (!placeCrystal.check(afterCrystalSelection.state()).legal()) {
                continue;
            }

            ArrayList<CombatAction> actions = new ArrayList<>(selected.actions());
            actions.add(placeSupport);
            actions.addAll(afterCrystalSelection.actions());
            actions.add(placeCrystal);
            result.add(new PreparationSequence(
                actions,
                crystalExplosion(placeSupport.pos()),
                ResourceChain.of(Map.of(
                    Items.OBSIDIAN, 1,
                    Items.END_CRYSTAL, 1
                ), 2.0),
                Set.of(placeSupport.pos())
            ));
            if (++added >= MAX_SETUP_SEQUENCES_PER_KIND) {
                break;
            }
        }
    }

    private void addExistingAnchorSequences(
        List<PreparationSequence> result,
        CombatState state
    ) {
        for (var entry : state.anchors().entrySet()) {
            BlockPos pos = entry.getKey();
            int charges = entry.getValue().charges();
            if (charges > 0) {
                addExistingChargedAnchorSequence(result, state, pos);
            } else if (state.inventory().count(Items.GLOWSTONE) > 0) {
                addExistingUnchargedAnchorSequence(result, state, pos);
            }
        }
    }

    private void addExistingChargedAnchorSequence(
        List<PreparationSequence> result,
        CombatState state,
        BlockPos pos
    ) {
        Optional<SelectedState> detonating = selectNonGlowstoneHand(state);
        if (detonating.isEmpty() || detonating.orElseThrow().actions().isEmpty()) {
            // Already-detonatable anchors are direct opportunities.
            return;
        }
        SelectedState selected = detonating.orElseThrow();
        DetonateAnchor detonate = new DetonateAnchor(pos);
        if (!detonate.check(selected.state()).legal()) {
            return;
        }
        ArrayList<CombatAction> actions = new ArrayList<>(selected.actions());
        actions.add(detonate);
        result.add(new PreparationSequence(
            actions,
            ExplosionContext.anchor(pos, false),
            ResourceChain.of(Map.of(), 0.25),
            Set.of(pos)
        ));
    }

    private void addExistingUnchargedAnchorSequence(
        List<PreparationSequence> result,
        CombatState state,
        BlockPos pos
    ) {
        Optional<SelectedState> glowstoneSelected = selectItem(state, Items.GLOWSTONE);
        if (glowstoneSelected.isEmpty()) {
            return;
        }
        SelectedState selected = glowstoneSelected.orElseThrow();
        ChargeAnchor charge = new ChargeAnchor(pos);
        var charged = charge.simulate(selected.state(), SimulationServices.defaults());
        if (charged.status() == ActionStatus.IMPOSSIBLE) {
            return;
        }
        Optional<SelectedState> detonating = selectNonGlowstoneHand(charged.state());
        if (detonating.isEmpty()) {
            return;
        }
        SelectedState afterDetonationSelection = detonating.orElseThrow();
        DetonateAnchor detonate = new DetonateAnchor(pos);
        if (!detonate.check(afterDetonationSelection.state()).legal()) {
            return;
        }

        ArrayList<CombatAction> actions = new ArrayList<>(selected.actions());
        actions.add(charge);
        actions.addAll(afterDetonationSelection.actions());
        actions.add(detonate);
        result.add(new PreparationSequence(
            actions,
            ExplosionContext.anchor(pos, false),
            ResourceChain.of(Map.of(Items.GLOWSTONE, 1), 1.25),
            Set.of(pos)
        ));
    }

    private void addNewAnchorSequences(
        List<PreparationSequence> result,
        CombatState state
    ) {
        Optional<SelectedState> anchorSelected = selectItem(state, Items.RESPAWN_ANCHOR);
        if (anchorSelected.isEmpty()) {
            return;
        }
        SelectedState selected = anchorSelected.orElseThrow();

        int added = 0;
        for (Candidate candidate : candidates.generate(selected.state())) {
            if (!(candidate.action() instanceof PlaceAnchor placeAnchor)) {
                continue;
            }
            var placed = placeAnchor.simulate(selected.state(), SimulationServices.defaults());
            if (placed.status() == ActionStatus.IMPOSSIBLE) {
                continue;
            }
            Optional<SelectedState> glowstoneSelected = selectItem(placed.state(), Items.GLOWSTONE);
            if (glowstoneSelected.isEmpty()) {
                continue;
            }
            SelectedState afterGlowstoneSelection = glowstoneSelected.orElseThrow();
            ChargeAnchor charge = new ChargeAnchor(placeAnchor.pos());
            var charged = charge.simulate(afterGlowstoneSelection.state(), SimulationServices.defaults());
            if (charged.status() == ActionStatus.IMPOSSIBLE) {
                continue;
            }
            Optional<SelectedState> detonating = selectNonGlowstoneHand(charged.state());
            if (detonating.isEmpty()) {
                continue;
            }
            SelectedState afterDetonationSelection = detonating.orElseThrow();
            DetonateAnchor detonate = new DetonateAnchor(placeAnchor.pos());
            if (!detonate.check(afterDetonationSelection.state()).legal()) {
                continue;
            }

            ArrayList<CombatAction> actions = new ArrayList<>(selected.actions());
            actions.add(placeAnchor);
            actions.addAll(afterGlowstoneSelection.actions());
            actions.add(charge);
            actions.addAll(afterDetonationSelection.actions());
            actions.add(detonate);
            result.add(new PreparationSequence(
                actions,
                ExplosionContext.anchor(placeAnchor.pos(), false),
                ResourceChain.of(Map.of(
                    Items.RESPAWN_ANCHOR, 1,
                    Items.GLOWSTONE, 1
                ), 2.5),
                Set.of(placeAnchor.pos())
            ));
            if (++added >= MAX_SETUP_SEQUENCES_PER_KIND) {
                break;
            }
        }
    }

    private static Optional<SelectedState> selectItem(CombatState state, Item item) {
        if (state.inventory().selectedItem().filter(item::equals).isPresent()) {
            return Optional.of(new SelectedState(state, List.of()));
        }
        return state.inventory().hotbarItems().entrySet().stream()
            .filter(entry -> entry.getValue().equals(item))
            .sorted(Map.Entry.comparingByKey())
            .findFirst()
            .map(entry -> {
                SelectHotbarSlot selection = new SelectHotbarSlot(entry.getKey());
                var outcome = selection.simulate(state, SimulationServices.defaults());
                return new SelectedState(outcome.state(), List.of(selection));
            });
    }

    private static Optional<SelectedState> selectNonGlowstoneHand(CombatState state) {
        if (state.inventory().offhandItem().filter(Items.GLOWSTONE::equals).isPresent()) {
            return Optional.empty();
        }
        if (state.inventory().selectedItem().filter(Items.GLOWSTONE::equals).isEmpty()) {
            return Optional.of(new SelectedState(state, List.of()));
        }

        for (int slot = 0; slot <= 8; slot++) {
            Item item = state.inventory().hotbarItems().get(slot);
            if (item != Items.GLOWSTONE) {
                SelectHotbarSlot selection = new SelectHotbarSlot(slot);
                var outcome = selection.simulate(state, SimulationServices.defaults());
                return Optional.of(new SelectedState(outcome.state(), List.of(selection)));
            }
        }
        return Optional.empty();
    }

    private static ExplosionContext crystalExplosion(BlockPos basePos) {
        return ExplosionContext.crystal(new Vec3(
            basePos.getX() + 0.5,
            basePos.getY() + 1.0,
            basePos.getZ() + 0.5
        ));
    }

    private record SelectedState(CombatState state, List<CombatAction> actions) {
        private SelectedState {
            actions = List.copyOf(actions);
        }
    }
}
