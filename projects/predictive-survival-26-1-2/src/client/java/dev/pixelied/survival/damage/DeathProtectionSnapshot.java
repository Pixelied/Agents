package dev.pixelied.survival.damage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DeathProtectionSnapshot(
    Optional<ProtectionItem> mainHand,
    Optional<ProtectionItem> offHand
) {
    public DeathProtectionSnapshot {
        mainHand = Objects.requireNonNull(mainHand, "mainHand");
        offHand = Objects.requireNonNull(offHand, "offHand");
    }

    public DeathProtectionSnapshot(boolean mainHandAvailable, boolean offHandAvailable) {
        this(
            mainHandAvailable ? Optional.of(ProtectionItem.generic()) : Optional.empty(),
            offHandAvailable ? Optional.of(ProtectionItem.generic()) : Optional.empty()
        );
    }

    public static DeathProtectionSnapshot none() {
        return new DeathProtectionSnapshot(Optional.empty(), Optional.empty());
    }

    public static DeathProtectionSnapshot mainHand(ProtectionItem item) {
        return new DeathProtectionSnapshot(Optional.of(item), Optional.empty());
    }

    public static DeathProtectionSnapshot offHand(ProtectionItem item) {
        return new DeathProtectionSnapshot(Optional.empty(), Optional.of(item));
    }

    public static DeathProtectionSnapshot both(ProtectionItem main, ProtectionItem off) {
        return new DeathProtectionSnapshot(Optional.of(main), Optional.of(off));
    }

    public boolean mainHandAvailable() {
        return mainHand.isPresent();
    }

    public boolean offHandAvailable() {
        return offHand.isPresent();
    }

    public boolean anyHandAvailable() {
        return mainHandAvailable() || offHandAvailable();
    }

    public Optional<Consumption> consumeFirst() {
        if (mainHand.isPresent()) {
            return Optional.of(new Consumption(
                new DeathProtectionSnapshot(Optional.empty(), offHand), mainHand.get()
            ));
        }
        if (offHand.isPresent()) {
            return Optional.of(new Consumption(
                new DeathProtectionSnapshot(mainHand, Optional.empty()), offHand.get()
            ));
        }
        return Optional.empty();
    }

    public record ProtectionItem(
        boolean clearExistingEffects,
        List<EffectInstanceSnapshot> effects,
        boolean outcomeUncertain
    ) {
        public ProtectionItem {
            effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
        }

        /** Compatibility constructor for source-confirmed deterministic fixtures. */
        public ProtectionItem(boolean clearExistingEffects, List<EffectInstanceSnapshot> effects) {
            this(clearExistingEffects, effects, false);
        }

        /**
         * Generic DEATH_PROTECTION always guarantees the base one-health rescue, but its ordered
         * consume effects are not represented by this legacy snapshot shape. Treat the post-state
         * as uncertain instead of silently assuming the favorable no-effect case.
         */
        public static ProtectionItem generic() {
            return new ProtectionItem(false, List.of(), true);
        }

        public static ProtectionItem vanillaTotem() {
            return new ProtectionItem(
                true,
                List.of(
                    new EffectInstanceSnapshot("minecraft:regeneration", 900, 1),
                    new EffectInstanceSnapshot("minecraft:absorption", 100, 1),
                    new EffectInstanceSnapshot("minecraft:fire_resistance", 800, 0)
                ),
                false
            );
        }
    }

    public record Consumption(DeathProtectionSnapshot remaining, ProtectionItem item) {
    }
}
