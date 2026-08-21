package dev.adrien.crystaloptimizer.client.v2;

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
import dev.adrien.crystaloptimizer.client.world.ClientCombatSnapshotBuilder;
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
import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import dev.adrien.crystaloptimizer.v2.strategy.DamageOpportunity;
import dev.adrien.crystaloptimizer.v2.strategy.LethalEfficiencyPolicy;
import dev.adrien.crystaloptimizer.v2.strategy.OpportunityIntent;
import dev.adrien.crystaloptimizer.v2.strategy.ResourceChain;
import dev.adrien.crystaloptimizer.v2.strategy.SelfDamageEstimate;
import dev.adrien.crystaloptimizer.v2.strategy.StrategicPreparationPlanner;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import dev.adrien.crystaloptimizer.v2.timing.TimingEngine;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class ClientDamageMapBuilder {
    private static final int MAX_CANDIDATES = 96;

    private final ClientCombatSnapshotBuilder snapshots;
    private final CandidateGenerator candidates;
    private final StrategicPreparationPlanner preparation;
    private final DamageEngine damageEngine;
    private final ClientDamageScenarioFactory scenarios;
    private final TimingEngine timingEngine;

    public ClientDamageMapBuilder(Minecraft minecraft, TimingEngine timingEngine) {
        this.snapshots = new ClientCombatSnapshotBuilder(Objects.requireNonNull(minecraft, "minecraft"));
        this.candidates = new CandidateGenerator(CandidateFeatureEstimator.conservative());
        this.preparation = new StrategicPreparationPlanner(candidates);
        this.damageEngine = new DamageEngine();
        this.scenarios = new ClientDamageScenarioFactory();
        this.timingEngine = Objects.requireNonNull(timingEngine, "timingEngine");
    }

    public DamageMap update(
        AbstractClientPlayer target,
        long worldRevision,
        long targetRevision,
        OptimizerConfig config
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        var snapshotOptional = snapshots.build(target);
        if (snapshotOptional.isEmpty()) {
            return DamageMap.empty(target.getUUID(), targetRevision, worldRevision);
        }

        var snapshot = snapshotOptional.orElseThrow();
        CombatState state = CombatState.fromSnapshot(snapshot, target.getUUID());
        long nowNanos = System.nanoTime();
        LinkedHashMap<String, DamageOpportunity> result = new LinkedHashMap<>();

        int considered = 0;
        for (var candidate : candidates.generate(state)) {
            if (considered++ >= MAX_CANDIDATES) {
                break;
            }
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
                SequenceTiming placeTiming = timingEngine.estimateSequence(
                    List.of(TimingTransition.CRYSTAL_PLACE_TO_SPAWN),
                    nowNanos
                );
                addOpportunity(
                    result,
                    state,
                    "place:" + place.basePos().asLong(),
                    action,
                    new FixedActionSequence(List.of(action)),
                    futureExplosion,
                    placeTiming,
                    true,
                    Set.of(place.basePos(), BlockPos.containing(state.targetSpatial().position())),
                    config,
                    snapshot.worldRevision(),
                    ResourceChain.of(Map.of(Items.END_CRYSTAL, 1), 1.0)
                );
                addOpportunity(
                    result,
                    state,
                    "recycle:" + place.basePos().asLong(),
                    action,
                    new SpawnCrystalCycle(place.basePos(), true),
                    futureExplosion,
                    SequenceTiming.immediate(),
                    true,
                    Set.of(place.basePos(), BlockPos.containing(state.targetSpatial().position())),
                    config,
                    snapshot.worldRevision(),
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
                state,
                prefix + actionKey(action),
                action,
                new FixedActionSequence(List.of(action)),
                explosion,
                SequenceTiming.immediate(),
                true,
                dependencies,
                config,
                snapshot.worldRevision(),
                resources
            );
        }

        preparation.plan(state, config).ifPresent(actions -> {
            String id = "prepare:" + Integer.toHexString(actions.hashCode());
            result.put(id, new DamageOpportunity(
                id,
                new FixedActionSequence(actions),
                DamageEstimate.exact(0.0f, snapshot.worldRevision(), snapshot.worldRevision()),
                OpportunityIntent.PREPARE,
                new SelfDamageEstimate(0.0f, effectiveHealth(state.self()), false),
                ResourceChain.none(),
                SequenceTiming.immediate(),
                false,
                false,
                !preparationDependencies(actions).isEmpty(),
                preparationDependencies(actions)
            ));
        });

        return new DamageMap(target.getUUID(), targetRevision, worldRevision, result);
    }

    private void addOpportunity(
        Map<String, DamageOpportunity> result,
        CombatState state,
        String id,
        CombatAction action,
        dev.adrien.crystaloptimizer.v2.state.ReactiveActionSpec actionSpec,
        ExplosionContext explosion,
        SequenceTiming timing,
        boolean positionDependent,
        Set<BlockPos> dependencies,
        OptimizerConfig config,
        long geometryRevision,
        ResourceChain resources
    ) {
        DamageEstimate targetDamage = damageEngine.estimate(
            explosion,
            state,
            scenarios.targetScenarios(state),
            geometryRevision,
            state.base().worldRevision()
        );
        SelfDamageEstimate selfDamage = selfDamageEstimate(state, explosion);

        boolean wouldKillHealth = targetDamage.lowerBound() >= state.target().health();
        boolean popsTotem = wouldKillHealth && state.target().totem().available();
        boolean lethal = wouldKillHealth && !state.target().totem().available();
        OpportunityIntent intent = lethal
            ? OpportunityIntent.LETHAL
            : popsTotem
                ? OpportunityIntent.POP
                : state.target().hurtWindow().invulnerableTime() > 10
                    && targetDamage.lowerBound() > 0.0f
                        ? OpportunityIntent.STAIRCASE
                        : OpportunityIntent.PRESSURE;

        float targetEffectiveHealth = effectiveHealth(state.target());
        LethalEfficiencyPolicy.Decision admission = LethalEfficiencyPolicy.evaluate(
            selfDamage,
            intent,
            targetDamage.expected(),
            action instanceof AttackKnownCrystal,
            targetEffectiveHealth,
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

    private static Set<BlockPos> preparationDependencies(List<CombatAction> actions) {
        LinkedHashSet<BlockPos> dependencies = new LinkedHashSet<>();
        for (CombatAction action : actions) {
            if (action instanceof PlaceCrystal place) {
                dependencies.add(place.basePos());
            } else if (action instanceof PlaceObsidian place) {
                dependencies.add(place.pos());
            } else if (action instanceof PlaceAnchor place) {
                dependencies.add(place.pos());
            } else if (action instanceof ChargeAnchor charge) {
                dependencies.add(charge.pos());
            } else if (action instanceof DetonateAnchor detonate) {
                dependencies.add(detonate.pos());
            }
        }
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
