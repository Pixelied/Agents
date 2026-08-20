package dev.adrien.crystaloptimizer.v2.reactive;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboardSnapshot;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class ReactiveCombatEngine {
    private static final int MAX_DUPLICATE_KEYS = 256;
    private static final long PROACTIVE_RETRY_NANOS = 250_000_000L;
    private static final List<ApprovalSlot> PRIORITY = List.of(
        ApprovalSlot.LETHAL,
        ApprovalSlot.FINISHER,
        ApprovalSlot.STAIRCASE,
        ApprovalSlot.RECYCLE,
        ApprovalSlot.BREAK,
        ApprovalSlot.PLACE,
        ApprovalSlot.PRESSURE,
        ApprovalSlot.PREPARE
    );

    private final AtomicLong nextActionId = new AtomicLong();
    private final ArrayDeque<EventKey> duplicateOrder = new ArrayDeque<>();
    private final Set<EventKey> duplicateKeys = new HashSet<>();
    private ProactiveKey lastProactiveKey;
    private long lastProactiveNanos = Long.MIN_VALUE;

    public synchronized Optional<ReactiveDecision> decide(
        CombatEvent event,
        CombatBlackboardSnapshot snapshot,
        long decisionCompleteNanos
    ) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(snapshot, "snapshot");
        if (decisionCompleteNanos < event.timestampNanos()) {
            throw new IllegalArgumentException("decision cannot precede event");
        }

        for (ApprovalSlot slot : PRIORITY) {
            ActionApproval approval = snapshot.approvals().get(slot);
            if (approval == null || !eventMatches(slot, event, approval.targetId())) {
                continue;
            }
            List<CombatAction> actions = approval.actionSpec().materialize(event);
            if (actions.isEmpty()) {
                continue;
            }

            EventKey key = EventKey.of(approval.approvalId(), event);
            if (duplicateKeys.contains(key)) {
                return Optional.empty();
            }
            remember(key);
            return Optional.of(new ReactiveDecision(
                nextActionId.getAndIncrement(),
                slot,
                approval,
                actions,
                event.timestampNanos(),
                decisionCompleteNanos
            ));
        }
        return Optional.empty();
    }

    public synchronized Optional<ReactiveDecision> decideProactive(
        CombatBlackboardSnapshot snapshot,
        long decisionCompleteNanos
    ) {
        Objects.requireNonNull(snapshot, "snapshot");

        for (ApprovalSlot slot : PRIORITY) {
            if (slot == ApprovalSlot.FINISHER || slot == ApprovalSlot.RECYCLE) {
                continue;
            }
            ActionApproval approval = snapshot.approvals().get(slot);
            if (approval == null || !(approval.actionSpec() instanceof FixedActionSequence fixed)) {
                continue;
            }

            List<CombatAction> actions = fixed.actions();
            ProactiveKey key = ProactiveKey.of(slot, approval, actions);
            if (key.equals(lastProactiveKey)
                && lastProactiveNanos != Long.MIN_VALUE
                && decisionCompleteNanos - lastProactiveNanos < PROACTIVE_RETRY_NANOS) {
                return Optional.empty();
            }

            lastProactiveKey = key;
            lastProactiveNanos = decisionCompleteNanos;
            return Optional.of(new ReactiveDecision(
                nextActionId.getAndIncrement(),
                slot,
                approval,
                actions,
                decisionCompleteNanos,
                decisionCompleteNanos
            ));
        }
        return Optional.empty();
    }

    public synchronized void clearDuplicateHistory() {
        duplicateOrder.clear();
        duplicateKeys.clear();
        lastProactiveKey = null;
        lastProactiveNanos = Long.MIN_VALUE;
    }

    private void remember(EventKey key) {
        duplicateKeys.add(key);
        duplicateOrder.addLast(key);
        while (duplicateOrder.size() > MAX_DUPLICATE_KEYS) {
            duplicateKeys.remove(duplicateOrder.removeFirst());
        }
    }

    private static boolean eventMatches(ApprovalSlot slot, CombatEvent event, UUID targetId) {
        if (slot == ApprovalSlot.FINISHER) {
            return event instanceof CombatEvent.TotemPopped pop && pop.targetId().equals(targetId);
        }
        if (slot == ApprovalSlot.RECYCLE) {
            return event instanceof CombatEvent.CrystalSpawned;
        }
        return switch (event) {
            case CombatEvent.TotemPopped pop -> pop.targetId().equals(targetId);
            case CombatEvent.EquipmentChanged equipment -> equipment.targetId().equals(targetId);
            case CombatEvent.TargetMoved moved -> moved.targetId().equals(targetId);
            default -> true;
        };
    }

    private record ProactiveKey(
        UUID targetId,
        ApprovalSlot slot,
        long targetRevision,
        long worldRevision,
        long inventoryRevision,
        long configRevision,
        List<CombatAction> actions
    ) {
        private ProactiveKey {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(slot, "slot");
            actions = List.copyOf(actions);
        }

        static ProactiveKey of(
            ApprovalSlot slot,
            ActionApproval approval,
            List<CombatAction> actions
        ) {
            return new ProactiveKey(
                approval.targetId(),
                slot,
                approval.targetRevision(),
                approval.worldRevision(),
                approval.inventoryRevision(),
                approval.configRevision(),
                actions
            );
        }
    }

    private record EventKey(long approvalId, int type, long high, long low) {
        static EventKey of(long approvalId, CombatEvent event) {
            return switch (event) {
                case CombatEvent.CrystalSpawned spawned ->
                    new EventKey(approvalId, 1, spawned.entityId(), 0L);
                case CombatEvent.CrystalRemoved removed ->
                    new EventKey(approvalId, 2, removed.entityId(), 0L);
                case CombatEvent.TotemPopped pop ->
                    new EventKey(
                        approvalId,
                        3,
                        pop.targetId().getMostSignificantBits(),
                        pop.timestampNanos()
                    );
                case CombatEvent.EquipmentChanged equipment ->
                    new EventKey(
                        approvalId,
                        4,
                        equipment.targetId().getLeastSignificantBits(),
                        equipment.timestampNanos()
                    );
                case CombatEvent.BlockAcked ack ->
                    new EventKey(approvalId, 5, ack.sequence(), 0L);
                case CombatEvent.BlockChanged changed ->
                    new EventKey(approvalId, 6, changed.pos().asLong(), changed.timestampNanos());
                case CombatEvent.InventoryChanged inventory ->
                    new EventKey(approvalId, 7, inventory.inventoryRevision(), 0L);
                case CombatEvent.TargetMoved moved ->
                    new EventKey(approvalId, 8, moved.targetRevision(), moved.timestampNanos());
                case CombatEvent.ConfigChanged config ->
                    new EventKey(approvalId, 9, config.configRevision(), 0L);
            };
        }
    }
}
