package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.candidate.CandidateFeatureEstimator;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.candidate.CandidateSelectionPolicy;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.sim.damage.DamageRequest;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionDamageCalculator26;
import dev.adrien.crystaloptimizer.sim.damage.VanillaDamageSimulator;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.v2.damage.DamageEngine;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.state.SpawnCrystalCycle;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Pure worker-side damage-map construction from an immutable strategic snapshot. */
public final class StrategicDamageMapBuilder {
    private static final double CERTIFIED_OUTCOME_CONFIDENCE = 0.80;

    private final CandidateGenerator candidates = new CandidateGenerator(CandidateFeatureEstimator.conservative());
    private final CandidateSelectionPolicy selectionPolicy = CandidateSelectionPolicy.v3Defaults();
    private final StrategicPreparationPlanner preparation = new StrategicPreparationPlanner(candidates);
    private final DamageEngine damageEngine = new DamageEngine();
    private final StrategicDamageScenarioFactory scenarios = new StrategicDamageScenarioFactory();

    public DamageMap build(
        StrategicSnapshot snapshot,
        UUID targetId,
        OptimizerConfig config
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(config, "config");
        long targetRevision = snapshot.targetRevisions().getOrDefault(targetId, 0L);
        if (snapshot.protectedPlayerIds().contains(targetId)
            || !snapshot.combat().combatants().containsKey(targetId)
            || !snapshot.combat().spatial().containsKey(targetId)) {
            return DamageMap.empty(targetId, targetRevision, snapshot.worldRevision());
        }

        CombatState state = CombatState.fromSnapshot(snapshot.combat(), targetId);
        LinkedHashMap<String, DamageOpportunity> result = new LinkedHashMap<>();
        var selectedCandidates = selectionPolicy.select(
            state,
            candidates.generate(state),
            config.crystals(),
            config.anchors()
        );
        for (var candidate : selectedCandidates) {
            CombatAction action = candidate.action();
            if (!enabled(action, config)) {
                continue;
            }

            if (action instanceof PlaceCrystal place) {
                ExplosionContext futureExplosion = ExplosionContext.crystal(new Vec3(
                    place.basePos().getX() + 0.5,
                    place.basePos().getY() + 1.0,
                    place.basePos().getZ() + 0.5
                ));
                SequenceTiming placeTiming = snapshot.timing().estimateSequence(
                    List.of(TimingTransition.CRYSTAL_PLACE_TO_SPAWN)
                );
                addOpportunity(
                    result,
                    snapshot,
                    state,
                    "place:" + place.basePos().asLong(),
                    action,
                    new FixedActionSequence(List.of(action)),
                    futureExplosion,
                    placeTiming,
                    true,
                    Set.of(place.basePos(), BlockPos.containing(state.targetSpatial().position())),
                    config,
                    ResourceChain.of(Map.of(Items.END_CRYSTAL, 1), 1.0)
                );
                addOpportunity(
                    result,
                    snapshot,
                    state,
                    "recycle:" + place.basePos().asLong(),
                    action,
                    new SpawnCrystalCycle(place.basePos(), true),
                    futureExplosion,
                    SequenceTiming.immediate(),
                    true,
                    Set.of(place.basePos(), BlockPos.containing(state.targetSpatial().position())),
                    config,
                    ResourceChain.of(Map.of(Items.END_CRYSTAL, 1), 1.0)
                );
                continue;
            }

            var outcome = action.simulate(state, SimulationServices.defaults());
            if (outcome.scheduledExplosions().isEmpty()) {
                continue;
            }
            ExplosionContext explosion = outcome.scheduledExplosions().getFirst();
            String prefix = action instanceof AttackKnownCrystal ? "break:" : "anchor:";
            Set<BlockPos> dependencies = Set.of(
                BlockPos.containing(explosion.center()),
                BlockPos.containing(state.targetSpatial().position())
            );
            ResourceChain resources = action instanceof DetonateAnchor
                ? ResourceChain.of(Map.of(), 0.25)
                : ResourceChain.none();
            addOpportunity(
                result,
                snapshot,
                state,
                prefix + actionKey(action),
                action,
                new FixedActionSequence(List.of(action)),
                explosion,
                SequenceTiming.immediate(),
                true,
                dependencies,
                config,
                resources
            );
        }

        for (PreparationSequence sequence : preparation.planSequences(state, config)) {
            List<CombatAction> actions = sequence.actions();
            addOpportunity(
                result,
                snapshot,
                state,
                "prepare:" + Integer.toHexString(actions.hashCode()),
                actions.getFirst(),
                new FixedActionSequence(actions),
                sequence.terminalExplosion(),
                preparationTiming(snapshot, sequence),
                !sequence.geometryDependencies().isEmpty(),
                withTargetDependency(sequence.geometryDependencies(), state),
                config,
                sequence.resources()
            );
        }

        return new DamageMap(targetId, targetRevision, snapshot.worldRevision(), result);
    }

    private void addOpportunity(
        Map<String, DamageOpportunity> result,
        StrategicSnapshot snapshot,
        CombatState state,
        String id,
        CombatAction action,
        dev.adrien.crystaloptimizer.v2.state.ReactiveActionSpec actionSpec,
        ExplosionContext explosion,
        SequenceTiming timing,
        boolean positionDependent,
        Set<BlockPos> dependencies,
        OptimizerConfig config,
        ResourceChain resources
    ) {
        long geometryRevision = snapshot.worldRevision();
        DamageEstimate targetDamage = damageEngine.estimate(
            explosion,
            state,
            scenarios.targetScenarios(state),
            geometryRevision,
            snapshot.worldRevision()
        );
        SelfDamageEstimate selfDamage = selfDamageEstimate(state, explosion);
        Map<UUID, DamageEstimate> protectedDamage = protectedDamage(
            snapshot,
            state,
            explosion,
            geometryRevision
        );
        if (!CollateralSafetyPolicy.accepts(
            protectedDamage,
            state.base(),
            snapshot.targetProtection().maxProtectedDamage()
        )) {
            return;
        }

        boolean popsTotem = targetDamage.popProbability() == 1.0
            && targetDamage.confidence() >= CERTIFIED_OUTCOME_CONFIDENCE;
        boolean lethal = targetDamage.killProbability() == 1.0
            && targetDamage.confidence() >= CERTIFIED_OUTCOME_CONFIDENCE;
        OpportunityIntent intent = lethal
            ? OpportunityIntent.LETHAL
            : popsTotem
                ? OpportunityIntent.POP
                : state.target().hurtWindow().invulnerableTime() > 10
                    && targetDamage.lowerBound() > 0.0f
                        ? OpportunityIntent.STAIRCASE
                        : OpportunityIntent.PRESSURE;

        LethalEfficiencyPolicy.Decision admission = LethalEfficiencyPolicy.evaluate(
            selfDamage,
            intent,
            targetDamage.expected(),
            action instanceof AttackKnownCrystal,
            effectiveHealth(state.target()),
            config
        );
        if (!admission.allowed()) {
            return;
        }

        result.put(id, new DamageOpportunity(
            id,
            actionSpec,
            targetDamage,
            intent,
            selfDamage,
            resources,
            timing,
            lethal,
            popsTotem,
            positionDependent,
            dependencies
        ));
    }

    private Map<UUID, DamageEstimate> protectedDamage(
        StrategicSnapshot snapshot,
        CombatState state,
        ExplosionContext explosion,
        long geometryRevision
    ) {
        LinkedHashMap<UUID, DamageEstimate> damage = new LinkedHashMap<>();
        for (UUID protectedId : snapshot.protectedPlayerIds()) {
            if (protectedId.equals(snapshot.selfId()) || protectedId.equals(state.targetId())) {
                continue;
            }
            if (!state.base().combatants().containsKey(protectedId)
                || !state.base().spatial().containsKey(protectedId)) {
                continue;
            }
            CombatState protectedState = CombatState.fromSnapshot(state.base(), protectedId);
            damage.put(protectedId, damageEngine.estimate(
                explosion,
                protectedState,
                scenarios.targetScenarios(protectedState),
                geometryRevision,
                snapshot.worldRevision()
            ));
        }
        return Map.copyOf(damage);
    }

    private static SequenceTiming preparationTiming(
        StrategicSnapshot snapshot,
        PreparationSequence sequence
    ) {
        ArrayList<TimingTransition> transitions = new ArrayList<>();
        for (CombatAction action : sequence.actions()) {
            if (action instanceof PlaceObsidian
                || action instanceof PlaceAnchor
                || action instanceof ChargeAnchor) {
                transitions.add(TimingTransition.BLOCK_INTERACTION_TO_ACK);
            } else if (action instanceof PlaceCrystal) {
                transitions.add(TimingTransition.CRYSTAL_PLACE_TO_SPAWN);
            }
        }
        return transitions.isEmpty()
            ? SequenceTiming.immediate()
            : snapshot.timing().estimateSequence(transitions);
    }

    private static Set<BlockPos> withTargetDependency(
        Set<BlockPos> sequenceDependencies,
        CombatState state
    ) {
        LinkedHashSet<BlockPos> dependencies = new LinkedHashSet<>(sequenceDependencies);
        dependencies.add(BlockPos.containing(state.targetSpatial().position()));
        return Set.copyOf(dependencies);
    }

    private static SelfDamageEstimate selfDamageEstimate(CombatState state, ExplosionContext explosion) {
        var selfSpatial = state.selfSpatial();
        float incoming = ExplosionDamageCalculator26.incoming(
            explosion,
            selfSpatial.boundingBox(),
            selfSpatial.position(),
            state.geometry()
        );
        float effectiveBefore = effectiveHealth(state.self());
        var pessimisticSelf = state.self().withHurtWindow(new HurtWindowState(0, 0.0f));
        var damageResult = VanillaDamageSimulator.apply(
            pessimisticSelf,
            DamageRequest.explosion(incoming)
                .withDifficulty(state.base().difficulty())
                .withSourcePosition(explosion.center())
        );
        if (damageResult.uncertain()) {
            return new SelfDamageEstimate(effectiveBefore, 0.0f, false);
        }

        float effectiveAfter = effectiveHealth(damageResult.target());
        float lostEffectiveHealth = damageResult.trace().totemTriggered()
            ? Math.min(effectiveBefore, damageResult.trace().postMagic())
            : Math.max(0.0f, effectiveBefore - effectiveAfter);
        return new SelfDamageEstimate(
            lostEffectiveHealth,
            damageResult.trace().totemTriggered() ? 1.0f : effectiveAfter,
            damageResult.trace().totemTriggered()
        );
    }

    private static float effectiveHealth(dev.adrien.crystaloptimizer.sim.model.SimCombatant combatant) {
        return combatant.health() + combatant.absorption();
    }

    private static boolean enabled(CombatAction action, OptimizerConfig config) {
        if (action instanceof AttackKnownCrystal || action instanceof PlaceCrystal) {
            return config.crystals();
        }
        if (action instanceof DetonateAnchor) {
            return config.anchors();
        }
        return true;
    }

    private static String actionKey(CombatAction action) {
        if (action instanceof AttackKnownCrystal attack) {
            return Integer.toString(attack.entityId());
        }
        if (action instanceof DetonateAnchor detonate) {
            return Long.toString(detonate.pos().asLong());
        }
        return Integer.toHexString(action.hashCode());
    }
}
