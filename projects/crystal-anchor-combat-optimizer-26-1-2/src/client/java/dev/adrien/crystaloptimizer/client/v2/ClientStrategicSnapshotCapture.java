package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.client.intel.TargetMotionTracker;
import dev.adrien.crystaloptimizer.client.world.ClientCombatSnapshotBuilder;
import dev.adrien.crystaloptimizer.prediction.MovementSample;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import dev.adrien.crystaloptimizer.v2.timing.TimingEngine;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/** Client-thread-only capture. Everything returned is immutable worker input. */
public final class ClientStrategicSnapshotCapture {
    private final Minecraft minecraft;
    private final ClientCombatSnapshotBuilder combatSnapshots;
    private final ClientRevisionTracker revisions;
    private final TimingEngine timingEngine;
    private final ClientTargetProtectionResolver protectionResolver;
    private final TargetProtectionPolicyConfig protectionConfig;
    private final AtomicLong snapshotIds = new AtomicLong();

    public ClientStrategicSnapshotCapture(
        Minecraft minecraft,
        ClientRevisionTracker revisions,
        TimingEngine timingEngine,
        ClientTargetProtectionResolver protectionResolver,
        TargetProtectionPolicyConfig protectionConfig
    ) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.combatSnapshots = new ClientCombatSnapshotBuilder(minecraft);
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.timingEngine = Objects.requireNonNull(timingEngine, "timingEngine");
        this.protectionResolver = Objects.requireNonNull(protectionResolver, "protectionResolver");
        this.protectionConfig = Objects.requireNonNull(protectionConfig, "protectionConfig");
    }

    public Optional<StrategicSnapshot> capture(
        List<AbstractClientPlayer> targets,
        long configRevision
    ) {
        Objects.requireNonNull(targets, "targets");
        if (configRevision < 0L || minecraft.player == null || minecraft.level == null) {
            return Optional.empty();
        }

        List<AbstractClientPlayer> validTargets = targets.stream()
            .filter(Objects::nonNull)
            .filter(target -> target != minecraft.player && !target.isRemoved() && !target.isDeadOrDying())
            .toList();
        if (validTargets.isEmpty()) {
            return Optional.empty();
        }

        Optional<CombatSnapshot> captured = combatSnapshots.build(validTargets);
        if (captured.isEmpty()) {
            return Optional.empty();
        }

        long nowNanos = System.nanoTime();
        long worldRevision = revisions.worldRevision();
        CombatSnapshot combat = withRevision(captured.orElseThrow(), worldRevision);
        LinkedHashMap<UUID, Long> targetRevisions = new LinkedHashMap<>();
        LinkedHashMap<UUID, List<MovementSample>> movementHistory = new LinkedHashMap<>();
        for (AbstractClientPlayer target : validTargets) {
            UUID targetId = target.getUUID();
            targetRevisions.put(targetId, revisions.targetRevision(targetId));
            List<MovementSample> history = TargetMotionTracker.instance().snapshot(targetId);
            if (!history.isEmpty()) {
                movementHistory.put(targetId, history);
            }
        }
        List<AbstractClientPlayer> observedPlayers = List.copyOf(minecraft.level.players());
        Set<UUID> protectedIds = protectionResolver.resolve(observedPlayers, protectionConfig);

        return Optional.of(new StrategicSnapshot(
            snapshotIds.incrementAndGet(),
            worldRevision,
            revisions.inventoryRevision(),
            configRevision,
            nowNanos,
            minecraft.player.getUUID(),
            targetRevisions,
            combat,
            movementHistory,
            protectedIds,
            protectionConfig,
            TimingSnapshot.capture(timingEngine, nowNanos)
        ));
    }

    private static CombatSnapshot withRevision(CombatSnapshot snapshot, long worldRevision) {
        return new CombatSnapshot(
            worldRevision,
            snapshot.selfId(),
            snapshot.region(),
            snapshot.combatants(),
            snapshot.crystals(),
            snapshot.anchors(),
            snapshot.inventory(),
            snapshot.timing(),
            snapshot.legality(),
            snapshot.spatial(),
            snapshot.difficulty()
        );
    }
}
