package dev.pixelied.survival.inventory;

import dev.pixelied.survival.damage.BlockingProfileSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;

import java.util.Objects;
import java.util.Optional;

public record InventorySlotSnapshot(
    int inventoryIndex,
    String stackKey,
    int componentFingerprint,
    int count,
    boolean deathProtection,
    Optional<ConsumableSurvivalSnapshot> consumable,
    Optional<EquippableSurvivalSnapshot> equippable,
    Optional<BlockingProfileSnapshot> blockingProfile,
    Optional<DeathProtectionSnapshot.ProtectionItem> deathProtectionItem,
    boolean blockingOnCooldown
) {
    public InventorySlotSnapshot {
        if (inventoryIndex < 0 || inventoryIndex > 40) {
            throw new IllegalArgumentException("inventoryIndex must be in [0, 40]");
        }
        stackKey = Objects.requireNonNull(stackKey, "stackKey");
        if (stackKey.isBlank()) throw new IllegalArgumentException("stackKey must not be blank");
        if (count < 0) throw new IllegalArgumentException("count must be non-negative");
        consumable = Objects.requireNonNull(consumable, "consumable");
        equippable = Objects.requireNonNull(equippable, "equippable");
        blockingProfile = Objects.requireNonNull(blockingProfile, "blockingProfile");
        deathProtectionItem = Objects.requireNonNull(deathProtectionItem, "deathProtectionItem");
        if (deathProtectionItem.isPresent() && !deathProtection) {
            throw new IllegalArgumentException("deathProtectionItem requires deathProtection capability");
        }
        if (blockingOnCooldown && blockingProfile.isEmpty()) {
            throw new IllegalArgumentException("blockingOnCooldown requires a blocking profile");
        }
        if (count == 0 && (deathProtection || consumable.isPresent() || equippable.isPresent()
            || blockingProfile.isPresent() || deathProtectionItem.isPresent() || blockingOnCooldown)) {
            throw new IllegalArgumentException("empty slot cannot provide survival capabilities");
        }
    }

    public InventorySlotSnapshot(
        int inventoryIndex,
        String stackKey,
        int componentFingerprint,
        int count,
        boolean deathProtection,
        Optional<ConsumableSurvivalSnapshot> consumable,
        Optional<EquippableSurvivalSnapshot> equippable,
        Optional<BlockingProfileSnapshot> blockingProfile
    ) {
        this(inventoryIndex, stackKey, componentFingerprint, count, deathProtection, consumable, equippable,
            blockingProfile, Optional.empty(), false);
    }

    public InventorySlotSnapshot(
        int inventoryIndex,
        String stackKey,
        int count,
        boolean deathProtection,
        Optional<ConsumableSurvivalSnapshot> consumable,
        Optional<EquippableSurvivalSnapshot> equippable
    ) {
        this(inventoryIndex, stackKey, stackKey.hashCode(), count, deathProtection, consumable, equippable,
            Optional.empty(), Optional.empty(), false);
    }

    public InventorySlotSnapshot(int inventoryIndex, String stackKey, int count, boolean deathProtection) {
        this(inventoryIndex, stackKey, stackKey.hashCode(), count, deathProtection,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    public boolean empty() {
        return count == 0;
    }

    public boolean sameContents(InventorySlotSnapshot other) {
        return other != null
            && stackKey.equals(other.stackKey)
            && componentFingerprint == other.componentFingerprint
            && count == other.count
            && deathProtection == other.deathProtection;
    }
}
