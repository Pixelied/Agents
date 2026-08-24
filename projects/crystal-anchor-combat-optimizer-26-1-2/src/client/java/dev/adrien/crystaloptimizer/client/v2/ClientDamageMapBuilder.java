package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.client.world.ClientCombatSnapshotBuilder;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import dev.adrien.crystaloptimizer.v2.strategy.StrategicDamageMapBuilder;
import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import dev.adrien.crystaloptimizer.v2.timing.TimingEngine;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Compatibility adapter for focused callers. Live V3 planning captures a
 * StrategicSnapshot once and runs StrategicDamageMapBuilder on the worker.
 */
public final class ClientDamageMapBuilder {
    private final ClientCombatSnapshotBuilder snapshots;
    private final StrategicDamageMapBuilder strategic = new StrategicDamageMapBuilder();
    private final TimingEngine timingEngine;

    public ClientDamageMapBuilder(Minecraft minecraft, TimingEngine timingEngine) {
        this.snapshots = new ClientCombatSnapshotBuilder(Objects.requireNonNull(minecraft, "minecraft"));
        this.timingEngine = Objects.requireNonNull(timingEngine, "timingEngine");
    }

    public DamageMap update(
        AbstractClientPlayer target,
        long worldRevision,
        long targetRevision,
        OptimizerConfig config
    ) {
        return update(
            target,
            worldRevision,
            targetRevision,
            config,
            Set.of(),
            TargetProtectionPolicyConfig.defaults()
        );
    }

    public DamageMap update(
        AbstractClientPlayer target,
        long worldRevision,
        long targetRevision,
        OptimizerConfig config,
        Set<UUID> protectedIds,
        TargetProtectionPolicyConfig protectionConfig
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(protectedIds, "protectedIds");
        Objects.requireNonNull(protectionConfig, "protectionConfig");
        if (protectedIds.contains(target.getUUID())) {
            return DamageMap.empty(target.getUUID(), targetRevision, worldRevision);
        }

        var combat = snapshots.build(target);
        if (combat.isEmpty()) {
            return DamageMap.empty(target.getUUID(), targetRevision, worldRevision);
        }
        long nowNanos = System.nanoTime();
        StrategicSnapshot snapshot = new StrategicSnapshot(
            0L,
            worldRevision,
            0L,
            0L,
            nowNanos,
            combat.orElseThrow().selfId(),
            Map.of(target.getUUID(), targetRevision),
            combat.orElseThrow(),
            Map.of(),
            protectedIds,
            protectionConfig,
            TimingSnapshot.capture(timingEngine, nowNanos)
        );
        return strategic.build(snapshot, target.getUUID(), config);
    }
}
