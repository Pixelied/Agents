package dev.pixelied.survival.damage;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure ordered state machine for vanilla DEATH_PROTECTION consume effects.
 * Minecraft-facing adapters translate visible effect components into these operations.
 */
final class DeathProtectionEffectAccumulator {
    private boolean clearExistingEffects;
    private boolean knownEmptyEffectBase;
    private boolean statusOutcomeUncertain;
    private boolean nonStatusOutcomeUncertain;
    private final List<EffectInstanceSnapshot> effects = new ArrayList<>();

    boolean hasKnownEmptyEffectBase() {
        return knownEmptyEffectBase;
    }

    void clearAllStatusEffects() {
        clearExistingEffects = true;
        knownEmptyEffectBase = true;
        effects.clear();
        // Vanilla applies consume effects in list order. A later clear-all erases uncertainty
        // caused only by earlier status mutations, but cannot undo position/other state changes.
        statusOutcomeUncertain = false;
    }

    void markStatusOutcomeUncertain() {
        statusOutcomeUncertain = true;
    }

    void addKnownStatusEffects(List<EffectInstanceSnapshot> knownEffects) {
        effects.addAll(knownEffects);
    }

    void removeStatusEffects() {
        // With a known-empty base this is provably a no-op. Otherwise the remaining live status
        // set is unknown until a later clear-all establishes an exact empty base.
        if (!knownEmptyEffectBase) statusOutcomeUncertain = true;
    }

    void markNonStatusOutcomeUncertain() {
        nonStatusOutcomeUncertain = true;
    }

    DeathProtectionSnapshot.ProtectionItem snapshot() {
        return new DeathProtectionSnapshot.ProtectionItem(
            clearExistingEffects,
            List.copyOf(effects),
            statusOutcomeUncertain || nonStatusOutcomeUncertain
        );
    }
}
