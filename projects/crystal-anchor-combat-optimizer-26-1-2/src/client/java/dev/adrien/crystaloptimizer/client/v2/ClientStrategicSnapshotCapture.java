package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.client.intel.TargetMotionTracker;
import dev.adrien.crystaloptimizer.client.world.ClientCombatSnapshotBuilder;
import dev.adrien.crystaloptimizer.prediction.MovementSample;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import dev.adrien.crystaloptimizer.v2.timing.TimingEngine;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

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

        ArrayList<CombatSnapshot> captured = new ArrayList<>(validTargets.size());
        for (AbstractClientPlayer target : validTargets) {
            combatSnapshots.build(target).ifPresent(captured::add);
        }
        if (captured.isEmpty()) {
            return Optional.empty();
        }

        long nowNanos = System.nanoTime();
        long worldRevision = revisions.worldRevision();
        CombatSnapshot combat = merge(captured, worldRevision);
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

    private static CombatSnapshot merge(List<CombatSnapshot> snapshots, long worldRevision) {
        CombatSnapshot first = snapshots.getFirst();
        LinkedHashMap<BlockPos, BlockState> states = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, VoxelShape> shapes = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, AnchorState> anchors = new LinkedHashMap<>();
        LinkedHashMap<UUID, SimCombatant> combatants = new LinkedHashMap<>();
        LinkedHashMap<UUID, CombatantSpatialState> spatial = new LinkedHashMap<>();
        LinkedHashMap<Integer, KnownCrystal> crystals = new LinkedHashMap<>();
        LinkedHashSet<AABB> occupied = new LinkedHashSet<>();

        for (CombatSnapshot snapshot : snapshots) {
            states.putAll(snapshot.region().states());
            shapes.putAll(snapshot.region().collisionShapes());
            anchors.putAll(snapshot.anchors());
            combatants.putAll(snapshot.combatants());
            spatial.putAll(snapshot.spatial());
            for (KnownCrystal crystal : snapshot.crystals()) {
                crystals.put(crystal.entityId(), crystal);
            }
            occupied.addAll(snapshot.legality().occupiedEntityBoxes());
        }

        LegalitySnapshot legality = new LegalitySnapshot(
            first.legality().eyePosition(),
            first.legality().blockInteractionRange(),
            first.legality().entityInteractionRange(),
            List.copyOf(occupied),
            first.legality().respawnAnchorWorks()
        );
        return new CombatSnapshot(
            worldRevision,
            first.selfId(),
            CombatRegion.of(states, shapes),
            combatants,
            List.copyOf(crystals.values()),
            anchors,
            first.inventory(),
            first.timing(),
            legality,
            spatial,
            first.difficulty()
        );
    }
}
