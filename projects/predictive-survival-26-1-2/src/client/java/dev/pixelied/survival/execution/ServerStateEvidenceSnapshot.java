package dev.pixelied.survival.execution;

import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable evidence derived only from inbound server packets after vanilla has applied them.
 * Unit-only contexts use an unknown snapshot until real packet evidence exists, so deterministic
 * tests retain their synthetic semantics while production never credits optimistic local state.
 */
public record ServerStateEvidenceSnapshot(
    boolean known,
    long revision,
    Map<Integer, StackEvidence> inventorySlots,
    Map<String, StackEvidence> equipmentSlots,
    Map<String, EffectEvidence> effects
) {
    public ServerStateEvidenceSnapshot {
        if (revision < 0L) throw new IllegalArgumentException("revision must be non-negative");
        inventorySlots = Map.copyOf(Objects.requireNonNull(inventorySlots, "inventorySlots"));
        equipmentSlots = Map.copyOf(Objects.requireNonNull(equipmentSlots, "equipmentSlots"));
        effects = Map.copyOf(Objects.requireNonNull(effects, "effects"));
    }

    public static ServerStateEvidenceSnapshot unknown() {
        return new ServerStateEvidenceSnapshot(false, 0L, Map.of(), Map.of(), Map.of());
    }

    public boolean inventoryMatchesAfter(
        int inventoryIndex,
        InventorySlotSnapshot expected,
        long exclusiveRevision
    ) {
        Objects.requireNonNull(expected, "expected");
        if (!known) return true;
        StackEvidence evidence = inventorySlots.get(inventoryIndex);
        return evidence != null
            && evidence.revision() > exclusiveRevision
            && evidence.matches(expected.stackKey(), expected.componentFingerprint(), expected.count());
    }

    public boolean inventoryChangedAfter(
        int inventoryIndex,
        InventorySlotSnapshot before,
        long exclusiveRevision
    ) {
        Objects.requireNonNull(before, "before");
        if (!known) return true;
        StackEvidence evidence = inventorySlots.get(inventoryIndex);
        return evidence != null
            && evidence.revision() > exclusiveRevision
            && !evidence.matches(before.stackKey(), before.componentFingerprint(), before.count());
    }

    public boolean equipmentMatchesAfter(
        String equipmentSlot,
        String itemKey,
        int componentFingerprint,
        long exclusiveRevision
    ) {
        Objects.requireNonNull(equipmentSlot, "equipmentSlot");
        Objects.requireNonNull(itemKey, "itemKey");
        if (!known) return true;
        StackEvidence evidence = equipmentSlots.get(equipmentSlot);
        return evidence != null
            && evidence.revision() > exclusiveRevision
            && evidence.matches(itemKey, componentFingerprint, 1);
    }

    public boolean effectObservedAfter(EffectInstanceSnapshot expected, long exclusiveRevision) {
        Objects.requireNonNull(expected, "expected");
        if (!known) return true;
        EffectEvidence evidence = effects.get(expected.effectKey());
        return evidence != null
            && evidence.revision() > exclusiveRevision
            && evidence.amplifier() >= expected.amplifier();
    }

    public record StackEvidence(
        String itemKey,
        int componentFingerprint,
        int count,
        long revision
    ) {
        public StackEvidence {
            itemKey = Objects.requireNonNull(itemKey, "itemKey");
            if (itemKey.isBlank()) throw new IllegalArgumentException("itemKey must not be blank");
            if (count < 0 || revision < 0L) throw new IllegalArgumentException("count/revision must be non-negative");
        }

        public boolean matches(String key, int fingerprint, int expectedCount) {
            return itemKey.equals(key)
                && componentFingerprint == fingerprint
                && count == expectedCount;
        }
    }

    public record EffectEvidence(String effectKey, int amplifier, int durationTicks, long revision) {
        public EffectEvidence {
            effectKey = Objects.requireNonNull(effectKey, "effectKey");
            if (effectKey.isBlank()) throw new IllegalArgumentException("effectKey must not be blank");
            if (amplifier < 0 || durationTicks < -1 || revision < 0L) {
                throw new IllegalArgumentException("invalid effect evidence");
            }
        }
    }
}
