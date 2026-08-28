package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ServerAuthorityTracker {
    private static final double EPSILON = 1.0E-9d;

    private int confirmedSelectedSlot;
    private Integer pendingSelectedSlot;
    private long pendingSelectedConfirmTick = Long.MAX_VALUE;
    private SurvivalAction.Hand pendingUseHand;
    private long pendingUseServerStartTick = Long.MAX_VALUE;

    private boolean equipmentTrackingInitialized;
    private InventorySlotSnapshot confirmedMainHand;
    private InventorySlotSnapshot confirmedOffHand;
    private MitigationSnapshot confirmedMitigation = MitigationSnapshot.none();
    private final List<PendingEquipmentMutation> pendingEquipment = new ArrayList<>();
    private long equipmentEpoch;
    private long lastServerEvidenceRevision = -1L;

    public ServerAuthorityTracker(int initialSelectedSlot) {
        validateHotbar(initialSelectedSlot);
        this.confirmedSelectedSlot = initialSelectedSlot;
    }

    public ServerAuthorityTracker(InventorySnapshot initialInventory, MitigationSnapshot initialMitigation) {
        this(Objects.requireNonNull(initialInventory, "initialInventory").selectedHotbarIndex());
        initializeEquipment(initialInventory, initialMitigation);
    }

    public void sentHotbarSelection(int targetSlot, TimingSnapshot timing) {
        validateHotbar(targetSlot);
        Objects.requireNonNull(timing, "timing");
        pendingSelectedSlot = targetSlot;
        pendingSelectedConfirmTick = timing.nextPacketProcessingWindow().latest();
    }

    public void sentHotbarSelection(
        int targetSlot,
        TimingSnapshot timing,
        InventorySnapshot inventoryBeforeDispatch,
        PendingEquipmentMutation.Origin origin
    ) {
        validateHotbar(targetSlot);
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(inventoryBeforeDispatch, "inventoryBeforeDispatch");
        Objects.requireNonNull(origin, "origin");
        requireEquipmentTracking();

        InventorySlotSnapshot before = projectedMainHandAfterQueuedMutations();
        InventorySlotSnapshot after = requireSlot(inventoryBeforeDispatch, targetSlot);
        pendingEquipment.add(new PendingEquipmentMutation(
            SurvivalAction.Hand.MAIN_HAND,
            before,
            after,
            timing.nextPacketProcessingWindow(),
            origin,
            nextEquipmentEpoch()
        ));
        sentHotbarSelection(targetSlot, timing);
    }

    public void observeUntrackedLocalSelection(int localSelectedSlot, TimingSnapshot timing) {
        validateHotbar(localSelectedSlot);
        Objects.requireNonNull(timing, "timing");
        if (localSelectedSlot == confirmedSelectedSlot || pendingSelectedSlot != null) return;
        pendingSelectedSlot = localSelectedSlot;
        pendingSelectedConfirmTick = timing.nextPacketProcessingWindow().latest();
    }

    /**
     * Rich local hand observation used by the survival runtime. A selection change is bounded by
     * the outbound server-processing window. A same-slot main-hand or offhand content change is
     * different: 26.1.2 container prediction mutates the local menu before sending the click, an
     * exact accepted prediction can be silent, and a disagreement is learned only when the
     * correction returns. Therefore content changes remain uncertain through that return deadline.
     */
    public void observeUntrackedLocalSelection(InventorySnapshot localInventory, TimingSnapshot timing) {
        Objects.requireNonNull(localInventory, "localInventory");
        Objects.requireNonNull(timing, "timing");
        requireEquipmentTracking();
        int localSelectedSlot = localInventory.selectedHotbarIndex();
        validateHotbar(localSelectedSlot);

        InventorySlotSnapshot mainBefore = projectedMainHandAfterQueuedMutations();
        InventorySlotSnapshot mainAfter = requireSlot(localInventory, localSelectedSlot);
        boolean selectionChanged = localSelectedSlot != mainBefore.inventoryIndex();
        boolean sameSlotContentsChanged = !selectionChanged && !mainBefore.sameContents(mainAfter);
        if (selectionChanged || sameSlotContentsChanged) {
            pendingEquipment.add(new PendingEquipmentMutation(
                SurvivalAction.Hand.MAIN_HAND,
                mainBefore,
                mainAfter,
                selectionChanged ? timing.nextPacketProcessingWindow() : contentAuthorityWindow(timing),
                PendingEquipmentMutation.Origin.USER,
                nextEquipmentEpoch()
            ));

            if (selectionChanged) {
                // Preserve the legacy scalar projection for execution code. The newer user packet is
                // the latest selected-slot target, while the rich queue retains packets already sent.
                pendingSelectedSlot = localSelectedSlot;
                pendingSelectedConfirmTick = timing.nextPacketProcessingWindow().latest();
            }
        }

        InventorySlotSnapshot offBefore = projectedOffHandAfterQueuedMutations();
        InventorySlotSnapshot offAfter = requireSlot(localInventory, 40);
        if (!offBefore.sameContents(offAfter)) {
            pendingEquipment.add(new PendingEquipmentMutation(
                SurvivalAction.Hand.OFF_HAND,
                offBefore,
                offAfter,
                contentAuthorityWindow(timing),
                PendingEquipmentMutation.Origin.USER,
                nextEquipmentEpoch()
            ));
        }
    }

    /**
     * Tracks locally predicted equipment mitigation without crediting it as server-authoritative.
     * Container clicks are optimistic in 26.1.2 and accepted predictions can be silent, so a local
     * armor gain or removal remains a before/after authority branch through the correction-return
     * deadline. The hand snapshot is intentionally unchanged: this mutation carries mitigation only.
     */
    public void observeUntrackedLocalMitigation(MitigationSnapshot localMitigation, TimingSnapshot timing) {
        Objects.requireNonNull(localMitigation, "localMitigation");
        Objects.requireNonNull(timing, "timing");
        requireEquipmentTracking();

        MitigationSnapshot before = projectedMitigationAfterQueuedMutations();
        if (before.equals(localMitigation)) return;

        InventorySlotSnapshot hand = projectedMainHandAfterQueuedMutations();
        pendingEquipment.add(new PendingEquipmentMutation(
            SurvivalAction.Hand.MAIN_HAND,
            hand,
            hand,
            contentAuthorityWindow(timing),
            PendingEquipmentMutation.Origin.USER_MITIGATION,
            nextEquipmentEpoch(),
            Optional.of(before),
            Optional.of(localMitigation)
        ));
    }

    /**
     * Reconciles hand contents from clientbound evidence recorded after vanilla applied it. The
     * first observation establishes the revision already represented by the constructor snapshot.
     * Later matching selected-slot or offhand inventory evidence is authoritative for contents but
     * does not, by itself, prove a selected-slot index transition.
     */
    public void observeServerEvidence(
        ServerStateEvidenceSnapshot evidence,
        InventorySnapshot observedInventory
    ) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(observedInventory, "observedInventory");
        requireEquipmentTracking();

        if (lastServerEvidenceRevision < 0L) {
            lastServerEvidenceRevision = evidence.revision();
            return;
        }
        long previousRevision = lastServerEvidenceRevision;
        if (evidence.revision() <= previousRevision) return;
        lastServerEvidenceRevision = evidence.revision();
        if (!evidence.known()) return;

        boolean authorityChanged = false;

        ServerStateEvidenceSnapshot.StackEvidence mainEvidence = evidence.inventorySlots().get(confirmedSelectedSlot);
        if (mainEvidence != null && mainEvidence.revision() > previousRevision) {
            InventorySlotSnapshot observedMainHand = requireSlot(observedInventory, confirmedSelectedSlot);
            if (mainEvidence.matches(
                observedMainHand.stackKey(),
                observedMainHand.componentFingerprint(),
                observedMainHand.count()
            )) {
                int resolvedPrefix = resolvedUserContentPrefix(SurvivalAction.Hand.MAIN_HAND, observedMainHand);
                if (!confirmedMainHand.sameContents(observedMainHand)) {
                    confirmedMainHand = observedMainHand;
                    authorityChanged = true;
                }
                if (resolvedPrefix > 0) {
                    pendingEquipment.subList(0, resolvedPrefix).clear();
                    authorityChanged = true;
                }
            }
        }

        ServerStateEvidenceSnapshot.StackEvidence offhandEvidence = evidence.inventorySlots().get(40);
        if (offhandEvidence != null && offhandEvidence.revision() > previousRevision) {
            InventorySlotSnapshot observedOffHand = requireSlot(observedInventory, 40);
            if (offhandEvidence.matches(
                observedOffHand.stackKey(),
                observedOffHand.componentFingerprint(),
                observedOffHand.count()
            )) {
                int resolvedPrefix = resolvedUserContentPrefix(SurvivalAction.Hand.OFF_HAND, observedOffHand);
                if (!confirmedOffHand.sameContents(observedOffHand)) {
                    confirmedOffHand = observedOffHand;
                    authorityChanged = true;
                }
                if (resolvedPrefix > 0) {
                    pendingEquipment.subList(0, resolvedPrefix).clear();
                    authorityChanged = true;
                }
            }
        }

        if (authorityChanged) nextEquipmentEpoch();
    }

    public int confirmedSelectedSlot(int localSelectedSlot, long currentTick) {
        validateHotbar(localSelectedSlot);
        if (currentTick < 0L) throw new IllegalArgumentException("currentTick must be non-negative");

        if (pendingSelectedSlot == null) return confirmedSelectedSlot;
        if (currentTick < pendingSelectedConfirmTick) return confirmedSelectedSlot;

        int target = pendingSelectedSlot;
        pendingSelectedSlot = null;
        pendingSelectedConfirmTick = Long.MAX_VALUE;
        if (localSelectedSlot == target) confirmedSelectedSlot = target;
        return confirmedSelectedSlot;
    }

    /**
     * Current bounded server-authority equipment projection. Mutations whose conservative latest
     * server-effect tick has passed are committed in wire order before the projection is returned.
     */
    public EquipmentAuthorityProjection equipmentProjection(
        InventorySnapshot observedInventory,
        MitigationSnapshot observedMitigation,
        long currentServerTick
    ) {
        Objects.requireNonNull(observedInventory, "observedInventory");
        Objects.requireNonNull(observedMitigation, "observedMitigation");
        if (currentServerTick < 0L) throw new IllegalArgumentException("currentServerTick must be non-negative");
        requireEquipmentTracking();

        observeServerEvidence(MinecraftServerStateEvidence.snapshot(), observedInventory);
        advanceEquipmentAuthority(currentServerTick);
        return new EquipmentAuthorityProjection(
            confirmedSelectedSlot,
            confirmedMainHand,
            confirmedOffHand,
            List.copyOf(pendingEquipment),
            equipmentEpoch,
            confirmedMitigation
        );
    }

    public void sentUseItem(SurvivalAction.Hand hand, TimingSnapshot timing) {
        pendingUseHand = Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(timing, "timing");
        pendingUseServerStartTick = timing.nextPacketProcessingWindow().latest();
    }

    public boolean confirmedUsingItem(
        boolean localUsing,
        SurvivalAction.Hand localHand,
        long currentTick
    ) {
        if (currentTick < 0L) throw new IllegalArgumentException("currentTick must be non-negative");
        if (pendingUseHand == null) return false;

        // Once the conservative server-start tick has passed, a stopped or different local use
        // proves that this tracked use session is over. Do not leave the old start tick around for
        // a later use of the same hand or it would appear to have hundreds of warm-up ticks.
        if (currentTick >= pendingUseServerStartTick
            && (!localUsing || localHand == null || localHand != pendingUseHand)) {
            clearUseSession();
            return false;
        }

        return localUsing && localHand == pendingUseHand && currentTick >= pendingUseServerStartTick;
    }

    public int confirmedUseTicks(
        boolean localUsing,
        SurvivalAction.Hand localHand,
        long currentTick
    ) {
        if (!confirmedUsingItem(localUsing, localHand, currentTick)) return 0;
        long elapsed = currentTick - pendingUseServerStartTick;
        return elapsed >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsed;
    }

    public void reset() {
        pendingSelectedSlot = null;
        pendingSelectedConfirmTick = Long.MAX_VALUE;
        clearUseSession();
        pendingEquipment.clear();
        lastServerEvidenceRevision = -1L;
    }

    private void initializeEquipment(InventorySnapshot inventory, MitigationSnapshot mitigation) {
        confirmedMainHand = requireSlot(inventory, inventory.selectedHotbarIndex());
        confirmedOffHand = requireSlot(inventory, 40);
        confirmedMitigation = Objects.requireNonNull(mitigation, "mitigation");
        equipmentTrackingInitialized = true;
        equipmentEpoch = 0L;
        pendingEquipment.clear();
    }

    private void advanceEquipmentAuthority(long currentServerTick) {
        while (!pendingEquipment.isEmpty()) {
            PendingEquipmentMutation mutation = pendingEquipment.getFirst();
            if (currentServerTick < mutation.authorityWindow().latest()) return;
            applyConfirmed(mutation);
            pendingEquipment.removeFirst();
        }
    }

    private void applyConfirmed(PendingEquipmentMutation mutation) {
        if (mutation.hand() == SurvivalAction.Hand.MAIN_HAND) {
            confirmedSelectedSlot = mutation.after().inventoryIndex();
            confirmedMainHand = mutation.after();
        } else {
            confirmedOffHand = mutation.after();
        }
        mutation.mitigationAfter().ifPresent(value -> confirmedMitigation = value);
    }

    /**
     * Returns how many leading same-physical-hand USER content predictions are resolved by an
     * authoritative hand snapshot. Only a leading prefix is eligible: server packets and outbound
     * clicks are ordered, so evidence for an earlier click cannot erase a later click still in flight.
     */
    private int resolvedUserContentPrefix(
        SurvivalAction.Hand hand,
        InventorySlotSnapshot authoritativeHand
    ) {
        InventorySlotSnapshot confirmed = hand == SurvivalAction.Hand.MAIN_HAND
            ? confirmedMainHand
            : confirmedOffHand;
        int physicalIndex = hand == SurvivalAction.Hand.MAIN_HAND ? confirmedSelectedSlot : 40;
        int runLength = 0;
        int matchedPrefix = confirmed.sameContents(authoritativeHand) ? 0 : -1;
        for (PendingEquipmentMutation mutation : pendingEquipment) {
            if (mutation.origin() != PendingEquipmentMutation.Origin.USER
                || mutation.hand() != hand
                || mutation.before().inventoryIndex() != physicalIndex
                || mutation.after().inventoryIndex() != physicalIndex) {
                break;
            }
            runLength++;
            if (matchedPrefix < 0 && mutation.after().sameContents(authoritativeHand)) {
                matchedPrefix = runLength;
            }
        }
        if (matchedPrefix > 0) return matchedPrefix;
        if (matchedPrefix == 0 && runLength > 0) {
            // Old/pre-click evidence is conservative only when the confirmed state has no death
            // protection to over-credit. If protection is currently confirmed, a later removal may
            // still be in flight, so keep that mutation adverse until its correction-return settle.
            return confirmed.deathProtection() ? 0 : 1;
        }
        return 0;
    }

    private InventorySlotSnapshot projectedMainHandAfterQueuedMutations() {
        for (int i = pendingEquipment.size() - 1; i >= 0; i--) {
            PendingEquipmentMutation mutation = pendingEquipment.get(i);
            if (mutation.hand() == SurvivalAction.Hand.MAIN_HAND) return mutation.after();
        }
        return confirmedMainHand;
    }

    private InventorySlotSnapshot projectedOffHandAfterQueuedMutations() {
        for (int i = pendingEquipment.size() - 1; i >= 0; i--) {
            PendingEquipmentMutation mutation = pendingEquipment.get(i);
            if (mutation.hand() == SurvivalAction.Hand.OFF_HAND) return mutation.after();
        }
        return confirmedOffHand;
    }

    private MitigationSnapshot projectedMitigationAfterQueuedMutations() {
        for (int i = pendingEquipment.size() - 1; i >= 0; i--) {
            Optional<MitigationSnapshot> mitigation = pendingEquipment.get(i).mitigationAfter();
            if (mitigation.isPresent()) return mitigation.get();
        }
        return confirmedMitigation;
    }

    private static TickWindow contentAuthorityWindow(TimingSnapshot timing) {
        return new TickWindow(
            timing.nextPacketProcessingWindow().earliest(),
            timing.containerPredictionSettleTick()
        );
    }

    private long nextEquipmentEpoch() {
        if (equipmentEpoch == Long.MAX_VALUE) throw new IllegalStateException("equipment authority epoch exhausted");
        return ++equipmentEpoch;
    }

    private void requireEquipmentTracking() {
        if (!equipmentTrackingInitialized) {
            throw new IllegalStateException("rich equipment authority tracking was not initialized from an inventory snapshot");
        }
    }

    private static InventorySlotSnapshot requireSlot(InventorySnapshot inventory, int index) {
        return inventory.slot(index).orElseGet(() -> emptySlot(index));
    }

    private static InventorySlotSnapshot emptySlot(int index) {
        return new InventorySlotSnapshot(index, "minecraft:air", 0, false);
    }

    private void clearUseSession() {
        pendingUseHand = null;
        pendingUseServerStartTick = Long.MAX_VALUE;
    }

    public static boolean withinHorizontalBlockAngle(
        Vec3Snapshot playerPosition,
        float headYawDegrees,
        Vec3Snapshot sourcePosition,
        float blockAngleDegrees
    ) {
        Objects.requireNonNull(playerPosition, "playerPosition");
        Objects.requireNonNull(sourcePosition, "sourcePosition");
        if (!Float.isFinite(headYawDegrees) || !Float.isFinite(blockAngleDegrees)
            || blockAngleDegrees < 0f || blockAngleDegrees > 180f) {
            return false;
        }

        double dx = sourcePosition.x() - playerPosition.x();
        double dz = sourcePosition.z() - playerPosition.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length <= EPSILON) return false;
        dx /= length;
        dz /= length;

        double yaw = Math.toRadians(headYawDegrees);
        double viewX = -Math.sin(yaw);
        double viewZ = Math.cos(yaw);
        double dot = Math.max(-1d, Math.min(1d, viewX * dx + viewZ * dz));
        double minimumDot = Math.cos(Math.toRadians(blockAngleDegrees));
        return dot + EPSILON >= minimumDot;
    }

    private static void validateHotbar(int slot) {
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("hotbar slot must be in [0, 8]");
    }
}
