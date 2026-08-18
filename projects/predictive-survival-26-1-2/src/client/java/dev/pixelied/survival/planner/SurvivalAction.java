package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;

import java.util.Objects;
import java.util.Optional;

public interface SurvivalAction {
    int requiredServerTicks();
    boolean legal();
    boolean authoritativePrerequisitesSatisfied();
    double reliability();
    int consumableCost();
    int disruptionCost();
    boolean deliberateDamage();
    PlayerSnapshot apply(PlayerSnapshot player);

    enum Hand {
        MAIN_HAND,
        OFF_HAND
    }

    record EquipDeathProtection(
        DeathProtectionSnapshot.ProtectionItem item,
        Hand hand,
        int requiredServerTicks,
        boolean legal,
        boolean authoritativePrerequisitesSatisfied,
        double reliability,
        int consumableCost,
        int disruptionCost
    ) implements SurvivalAction {
        public EquipDeathProtection {
            item = Objects.requireNonNull(item, "item");
            hand = Objects.requireNonNull(hand, "hand");
            validateCommon(requiredServerTicks, reliability, consumableCost, disruptionCost);
        }

        @Override
        public boolean deliberateDamage() {
            return false;
        }

        @Override
        public PlayerSnapshot apply(PlayerSnapshot player) {
            DeathProtectionSnapshot current = player.deathProtection();
            DeathProtectionSnapshot next = hand == Hand.MAIN_HAND
                ? new DeathProtectionSnapshot(Optional.of(item), current.offHand())
                : new DeathProtectionSnapshot(current.mainHand(), Optional.of(item));
            return copy(player, player.blocking(), next);
        }
    }

    record RaiseShield(
        int requiredServerTicks,
        boolean legal,
        boolean authoritativePrerequisitesSatisfied,
        boolean guaranteedBlock,
        double reliability,
        float blockedFraction,
        int elapsedUseTicks,
        int requiredUseTicks,
        int disruptionCost
    ) implements SurvivalAction {
        public RaiseShield {
            validateCommon(requiredServerTicks, reliability, 0, disruptionCost);
            if (!Float.isFinite(blockedFraction) || blockedFraction < 0f || blockedFraction > 1f) {
                throw new IllegalArgumentException("blockedFraction must be finite and in [0,1]");
            }
            if (elapsedUseTicks < 0 || requiredUseTicks < 0) {
                throw new IllegalArgumentException("shield use ticks must be non-negative");
            }
        }

        @Override
        public int consumableCost() {
            return 0;
        }

        @Override
        public boolean deliberateDamage() {
            return false;
        }

        @Override
        public PlayerSnapshot apply(PlayerSnapshot player) {
            BlockingSnapshot blocking = new BlockingSnapshot(
                guaranteedBlock,
                blockedFraction,
                elapsedUseTicks,
                requiredUseTicks
            );
            return copy(player, blocking, player.deathProtection());
        }
    }

    record DeliberateDamage(
        int requiredServerTicks,
        boolean legal,
        boolean authoritativePrerequisitesSatisfied,
        double reliability,
        int consumableCost,
        int disruptionCost
    ) implements SurvivalAction {
        public DeliberateDamage {
            validateCommon(requiredServerTicks, reliability, consumableCost, disruptionCost);
        }

        @Override
        public boolean deliberateDamage() {
            return true;
        }

        @Override
        public PlayerSnapshot apply(PlayerSnapshot player) {
            return player;
        }
    }

    record NoAction() implements SurvivalAction {
        @Override public int requiredServerTicks() { return 0; }
        @Override public boolean legal() { return true; }
        @Override public boolean authoritativePrerequisitesSatisfied() { return true; }
        @Override public double reliability() { return 1d; }
        @Override public int consumableCost() { return 0; }
        @Override public int disruptionCost() { return 0; }
        @Override public boolean deliberateDamage() { return false; }
        @Override public PlayerSnapshot apply(PlayerSnapshot player) { return player; }
    }

    private static void validateCommon(
        int requiredServerTicks,
        double reliability,
        int consumableCost,
        int disruptionCost
    ) {
        if (requiredServerTicks < 0) throw new IllegalArgumentException("requiredServerTicks must be non-negative");
        if (!Double.isFinite(reliability) || reliability < 0d || reliability > 1d) {
            throw new IllegalArgumentException("reliability must be finite and in [0,1]");
        }
        if (consumableCost < 0 || disruptionCost < 0) {
            throw new IllegalArgumentException("costs must be non-negative");
        }
    }

    private static PlayerSnapshot copy(
        PlayerSnapshot player,
        BlockingSnapshot blocking,
        DeathProtectionSnapshot deathProtection
    ) {
        Objects.requireNonNull(player, "player");
        return new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(),
            player.deadOrDying(), player.difficulty(), player.mitigation(), player.statusEffects(), blocking,
            player.hurtState(), deathProtection, player.boundingBox(), player.position(), player.velocity(),
            player.equipmentItemKeys(), player.stateProperties()
        );
    }
}
