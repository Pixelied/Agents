package dev.pixelied.survival.execution;

import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Tracks local death-protection consumption observations (vanilla entity event 35) independently
 * from later inventory/equipment correction packets.
 *
 * <p>The event proves that the server already consumed one death-protection item, but it does not
 * itself identify the physical hand when equipment authority is ambiguous. Until authoritative
 * stack evidence reconciles that resource removal, decision-critical hand projection consumes one
 * item from every feasible pre-pop branch using vanilla MAIN_HAND -> OFF_HAND order.</p>
 */
public final class DeathProtectionPopTracker {
    private static final DeathProtectionPopTracker GLOBAL = new DeathProtectionPopTracker();

    private long generation;
    private boolean consumptionUnresolved;
    private long lastPopClientTick = -1L;
    private long lastReconciledEvidenceRevision;
    private long eventEvidenceLowerExclusive;
    private EquipmentAuthorityProjection lastEquipment;
    private InventorySnapshot lastInventory;
    private long lastServerTick;
    private List<DeathProtectionSnapshot> lastFeasible = List.of();
    private List<DeathProtectionSnapshot> postPopFeasible = List.of();
    private Set<Integer> possibleConsumedInventoryIndices = Set.of();
    private InventorySnapshot prePopInventory;

    public static DeathProtectionPopTracker global() {
        return GLOBAL;
    }

    public void observeLocalTotemPop(long clientTick) {
        observeLocalTotemPop(clientTick, MinecraftServerStateEvidence.snapshot().revision());
    }

    public void observeLocalTotemPop(long clientTick, long evidenceRevision) {
        if (clientTick < 0L || evidenceRevision < 0L) {
            throw new IllegalArgumentException("clientTick/evidenceRevision must be non-negative");
        }

        generation++;
        lastPopClientTick = clientTick;
        eventEvidenceLowerExclusive = lastReconciledEvidenceRevision;
        consumptionUnresolved = true;
        prePopInventory = lastInventory;

        List<DeathProtectionSnapshot> base = consumptionUnresolved && !postPopFeasible.isEmpty()
            ? postPopFeasible
            : lastFeasible;
        postPopFeasible = consumeOneFromEveryFeasibleBranch(base);
        possibleConsumedInventoryIndices = possibleConsumedIndices(base, lastEquipment);

        // A correction packet can be applied immediately before event 35 with no runtime capture
        // between the two. Reconciliation below therefore accepts any matching stack evidence newer
        // than the last reconciled revision, including evidence already present at event time.
        if (evidenceRevision < eventEvidenceLowerExclusive) {
            eventEvidenceLowerExclusive = evidenceRevision;
        }
    }

    public long generation() {
        return generation;
    }

    public boolean consumptionUnresolved() {
        return consumptionUnresolved;
    }

    public long lastPopClientTick() {
        return lastPopClientTick;
    }

    public DeathProtectionSnapshot projectedDeathProtectionAt(
        EquipmentAuthorityProjection equipment,
        long serverTick
    ) {
        Objects.requireNonNull(equipment, "equipment");
        if (serverTick < 0L) throw new IllegalArgumentException("serverTick must be non-negative");
        if (!consumptionUnresolved) return equipment.guaranteedDeathProtectionAt(serverTick);
        return guaranteed(postPopFeasible);
    }

    public InventorySnapshot conservativeInventoryAfterPop(
        InventorySnapshot observed,
        EquipmentAuthorityProjection equipment,
        long serverTick
    ) {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(equipment, "equipment");
        InventorySnapshot projected = equipment.conservativeInventoryAt(observed, serverTick);
        if (!consumptionUnresolved) return projected;

        DeathProtectionSnapshot guaranteed = projectedDeathProtectionAt(equipment, serverTick);
        java.util.LinkedHashMap<Integer, InventorySlotSnapshot> slots = new java.util.LinkedHashMap<>(projected.slots());

        if (!guaranteed.mainHandAvailable()) {
            for (int index : possibleConsumedInventoryIndices) {
                if (index < 0 || index > 8) continue;
                InventorySlotSnapshot slot = slots.get(index);
                if (slot != null && slot.deathProtection()) slots.put(index, empty(index));
            }
        }
        if (!guaranteed.offHandAvailable()) {
            InventorySlotSnapshot off = slots.get(40);
            if (off != null && off.deathProtection() && possibleConsumedInventoryIndices.contains(40)) {
                slots.put(40, empty(40));
            }
        }
        return new InventorySnapshot(projected.selectedHotbarIndex(), slots, projected.activeOffhandShield());
    }

    public void reconcile(
        EquipmentAuthorityProjection equipment,
        InventorySnapshot inventory,
        ServerStateEvidenceSnapshot evidence,
        long serverTick
    ) {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(evidence, "evidence");
        if (serverTick < 0L) throw new IllegalArgumentException("serverTick must be non-negative");

        if (consumptionUnresolved && hasAuthoritativeConsumptionEvidence(inventory, evidence)) {
            consumptionUnresolved = false;
            postPopFeasible = List.of();
            possibleConsumedInventoryIndices = Set.of();
            prePopInventory = null;
        }

        lastEquipment = equipment;
        lastInventory = inventory;
        lastServerTick = serverTick;
        lastFeasible = equipment.feasibleDeathProtectionAt(serverTick);
        if (evidence.known()) lastReconciledEvidenceRevision = Math.max(lastReconciledEvidenceRevision, evidence.revision());
    }

    /** Planned-interface compatibility for contexts that have no packet-evidence object. */
    public void reconcile(EquipmentAuthorityProjection equipment, InventorySnapshot inventory) {
        reconcile(equipment, inventory, MinecraftServerStateEvidence.snapshot(), lastServerTick < 0L ? 0L : lastServerTick);
    }

    public void reset() {
        generation = 0L;
        consumptionUnresolved = false;
        lastPopClientTick = -1L;
        lastReconciledEvidenceRevision = 0L;
        eventEvidenceLowerExclusive = 0L;
        lastEquipment = null;
        lastInventory = null;
        lastServerTick = 0L;
        lastFeasible = List.of();
        postPopFeasible = List.of();
        possibleConsumedInventoryIndices = Set.of();
        prePopInventory = null;
    }

    private boolean hasAuthoritativeConsumptionEvidence(
        InventorySnapshot inventory,
        ServerStateEvidenceSnapshot evidence
    ) {
        if (!evidence.known() || prePopInventory == null || possibleConsumedInventoryIndices.isEmpty()) return false;
        for (int index : possibleConsumedInventoryIndices) {
            Optional<InventorySlotSnapshot> beforeOpt = prePopInventory.slot(index);
            Optional<InventorySlotSnapshot> afterOpt = inventory.slot(index);
            if (beforeOpt.isEmpty() || afterOpt.isEmpty()) continue;
            InventorySlotSnapshot before = beforeOpt.get();
            InventorySlotSnapshot after = afterOpt.get();
            if (!before.deathProtection() || after.sameContents(before)) continue;

            if (evidence.inventoryChangedAfter(index, before, eventEvidenceLowerExclusive)) return true;
            if (index == 40 && equipmentChangedAfter(evidence, "offhand", before)) return true;
            if (index >= 0 && index <= 8 && equipmentChangedAfter(evidence, "mainhand", before)) return true;
        }
        return false;
    }

    private boolean equipmentChangedAfter(
        ServerStateEvidenceSnapshot evidence,
        String equipmentSlot,
        InventorySlotSnapshot before
    ) {
        ServerStateEvidenceSnapshot.StackEvidence stack = evidence.equipmentSlots().get(equipmentSlot);
        return stack != null
            && stack.revision() > eventEvidenceLowerExclusive
            && !stack.matches(before.stackKey(), before.componentFingerprint(), before.count());
    }

    private static List<DeathProtectionSnapshot> consumeOneFromEveryFeasibleBranch(
        List<DeathProtectionSnapshot> feasible
    ) {
        LinkedHashSet<DeathProtectionSnapshot> remaining = new LinkedHashSet<>();
        for (DeathProtectionSnapshot state : feasible) {
            state.consumeFirst().map(DeathProtectionSnapshot.Consumption::remaining).ifPresent(remaining::add);
        }
        return List.copyOf(remaining);
    }

    private static DeathProtectionSnapshot guaranteed(List<DeathProtectionSnapshot> feasible) {
        if (feasible.isEmpty()) return DeathProtectionSnapshot.none();
        Optional<DeathProtectionSnapshot.ProtectionItem> main = guaranteedItem(
            feasible.stream().map(DeathProtectionSnapshot::mainHand).toList()
        );
        Optional<DeathProtectionSnapshot.ProtectionItem> off = guaranteedItem(
            feasible.stream().map(DeathProtectionSnapshot::offHand).toList()
        );
        return new DeathProtectionSnapshot(main, off);
    }

    private static Optional<DeathProtectionSnapshot.ProtectionItem> guaranteedItem(
        List<Optional<DeathProtectionSnapshot.ProtectionItem>> candidates
    ) {
        if (candidates.isEmpty() || candidates.stream().anyMatch(Optional::isEmpty)) return Optional.empty();
        DeathProtectionSnapshot.ProtectionItem first = candidates.getFirst().orElseThrow();
        boolean identical = candidates.stream().allMatch(candidate -> first.equals(candidate.orElseThrow()));
        return Optional.of(identical ? first : DeathProtectionSnapshot.ProtectionItem.generic());
    }

    private static Set<Integer> possibleConsumedIndices(
        List<DeathProtectionSnapshot> feasible,
        EquipmentAuthorityProjection equipment
    ) {
        if (equipment == null || feasible.isEmpty()) return Set.of();
        boolean mainPossible = false;
        boolean offPossible = false;
        for (DeathProtectionSnapshot state : feasible) {
            if (state.mainHandAvailable()) mainPossible = true;
            else if (state.offHandAvailable()) offPossible = true;
        }

        LinkedHashSet<Integer> indices = new LinkedHashSet<>();
        if (mainPossible) {
            addProtectionIndex(indices, equipment.confirmedMainHand());
            for (PendingEquipmentMutation mutation : equipment.pending()) {
                if (mutation.hand() != SurvivalAction.Hand.MAIN_HAND) continue;
                addProtectionIndex(indices, mutation.before());
                addProtectionIndex(indices, mutation.after());
            }
        }
        if (offPossible) indices.add(40);
        return Set.copyOf(indices);
    }

    private static void addProtectionIndex(Set<Integer> indices, InventorySlotSnapshot slot) {
        if (slot.deathProtection() && slot.count() > 0) indices.add(slot.inventoryIndex());
    }

    private static InventorySlotSnapshot empty(int index) {
        return new InventorySlotSnapshot(
            index,
            "minecraft:air",
            0,
            0,
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false
        );
    }
}
