package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
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
import dev.adrien.crystaloptimizer.v2.damage.DamageEngine;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.state.SpawnCrystalCycle;
import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import dev.adrien.crystaloptimizer.v2.strategy.DamageOpportunity;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import dev.adrien.crystaloptimizer.v2.timing.TimingEngine;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class ClientDamageMapBuilder {
    private static final int MAX_CANDIDATES = 96;

    private final ClientCombatSnapshotBuilder snapshots;
    private final CandidateGenerator candidates;
    private final DamageEngine damageEngine;
    private final ClientDamageScenarioFactory scenarios;
    private final TimingEngine timingEngine;

    public ClientDamageMapBuilder(Minecraft minecraft, TimingEngine timingEngine) {
        this.snapshots = new ClientCombatSnapshotBuilder(Objects.requireNonNull(minecraft, "minecraft"));
        this.candidates = new CandidateGenerator(CandidateFeatureEstimator.conservative());
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
                    snapshot.worldRevision()
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
                    snapshot.worldRevision()
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
                snapshot.worldRevision()
            );
        }

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
        long geometryRevision
    ) {
        DamageEstimate targetDamage = damageEngine.estimate(
            explosion,
            state,
            scenarios.targetScenarios(state),
            geometryRevision,
            state.base().worldRevision()
        );
        float selfDamage = totalSelfDamage(state, explosion);
        float requiredDamage = state.target().health() <= config.facePlaceHealth()
            ? 0.0f
            : config.minDamage();
        if (targetDamage.expected() < requiredDamage || selfDamage > config.maxSelfDamage()) {
            return;
        }

        boolean wouldKillHealth = targetDamage.lowerBound() >= state.target().health();
        boolean popsTotem = wouldKillHealth && state.target().totem().available();
        boolean lethal = wouldKillHealth && !state.target().totem().available();
        result.put(id, new DamageOpportunity(
            id,
            actionSpec,
            targetDamage,
            selfDamage,
            timing,
            lethal,
            popsTotem,
            positionDependent,
            dependencies
        ));
    }

    private static float totalSelfDamage(CombatState state, ExplosionContext explosion) {
        var self = state.selfSpatial();
        float incoming = ExplosionDamageCalculator26.incoming(
            explosion,
            self.boundingBox(),
            self.position(),
            state.geometry()
        );
        var result = VanillaDamageSimulator.apply(
            state.self(),
            DamageRequest.explosion(incoming)
                .withDifficulty(state.base().difficulty())
                .withSourcePosition(explosion.center())
        );
        return result.uncertain() ? incoming : result.trace().postMagic();
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
