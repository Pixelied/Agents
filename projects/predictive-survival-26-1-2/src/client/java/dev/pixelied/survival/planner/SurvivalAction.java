package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public interface SurvivalAction {
    int requiredServerTicks();
    boolean legal();
    boolean authoritativePrerequisitesSatisfied();
    double reliability();
    int consumableCost();
    int disruptionCost();
    boolean deliberateDamage();
    PlayerSnapshot apply(PlayerSnapshot player);

    default ThreatTimeline applyTimeline(ThreatTimeline timeline) {
        return Objects.requireNonNull(timeline, "timeline");
    }

    enum Hand {
        MAIN_HAND,
        OFF_HAND
    }

    record BlockTarget(int x, int y, int z, String itemKey) {
        public BlockTarget {
            itemKey = Objects.requireNonNull(itemKey, "itemKey");
            if (itemKey.isBlank()) throw new IllegalArgumentException("itemKey must not be blank");
        }
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

        @Override public boolean deliberateDamage() { return false; }

        @Override
        public PlayerSnapshot apply(PlayerSnapshot player) {
            DeathProtectionSnapshot current = player.deathProtection();
            DeathProtectionSnapshot next = hand == Hand.MAIN_HAND
                ? new DeathProtectionSnapshot(Optional.of(item), current.offHand())
                : new DeathProtectionSnapshot(current.mainHand(), Optional.of(item));
            return copy(
                player, player.health(), player.absorption(), player.mitigation(), player.statusEffects(),
                player.blocking(), next, player.equipmentItemKeys()
            );
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

        @Override public int consumableCost() { return 0; }
        @Override public boolean deliberateDamage() { return false; }

        @Override
        public PlayerSnapshot apply(PlayerSnapshot player) {
            BlockingSnapshot blocking = new BlockingSnapshot(
                guaranteedBlock, blockedFraction, elapsedUseTicks, requiredUseTicks
            );
            return copy(
                player, player.health(), player.absorption(), player.mitigation(), player.statusEffects(),
                blocking, player.deathProtection(), player.equipmentItemKeys()
            );
        }
    }

    record PlaceCover(
        Map<String, DamageRange> rawDamageOverrides,
        Optional<BlockTarget> target,
        int requiredServerTicks,
        boolean legal,
        boolean authoritativePrerequisitesSatisfied,
        double reliability,
        int consumableCost,
        int disruptionCost
    ) implements SurvivalAction {
        public PlaceCover(
            Map<String, DamageRange> rawDamageOverrides,
            int requiredServerTicks,
            boolean legal,
            boolean authoritativePrerequisitesSatisfied,
            double reliability,
            int consumableCost,
            int disruptionCost
        ) {
            this(rawDamageOverrides, Optional.empty(), requiredServerTicks, legal,
                authoritativePrerequisitesSatisfied, reliability, consumableCost, disruptionCost);
        }

        public PlaceCover {
            rawDamageOverrides = Map.copyOf(Objects.requireNonNull(rawDamageOverrides, "rawDamageOverrides"));
            target = Objects.requireNonNull(target, "target");
            for (Map.Entry<String, DamageRange> entry : rawDamageOverrides.entrySet()) {
                if (entry.getKey().isBlank()) throw new IllegalArgumentException("threat id must not be blank");
                Objects.requireNonNull(entry.getValue(), "raw damage override");
            }
            validateCommon(requiredServerTicks, reliability, consumableCost, disruptionCost);
        }

        @Override public boolean deliberateDamage() { return false; }
        @Override public PlayerSnapshot apply(PlayerSnapshot player) { return Objects.requireNonNull(player, "player"); }

        @Override
        public ThreatTimeline applyTimeline(ThreatTimeline timeline) {
            Objects.requireNonNull(timeline, "timeline");
            List<ThreatEvent> events = new ArrayList<>(timeline.events().size());
            for (ThreatEvent event : timeline.events()) {
                DamageRange replacement = rawDamageOverrides.get(event.id());
                events.add(replacement == null ? event : copyEvent(event, copyDamageSource(event.damage(), replacement)));
            }
            return new ThreatTimeline(events);
        }
    }

    record SwapEquipment(
        MitigationSnapshot mitigationAfter,
        Map<String, String> equipmentUpdates,
        int requiredServerTicks,
        boolean legal,
        boolean authoritativePrerequisitesSatisfied,
        double reliability,
        int consumableCost,
        int disruptionCost
    ) implements SurvivalAction {
        public SwapEquipment {
            mitigationAfter = Objects.requireNonNull(mitigationAfter, "mitigationAfter");
            equipmentUpdates = Map.copyOf(Objects.requireNonNull(equipmentUpdates, "equipmentUpdates"));
            validateCommon(requiredServerTicks, reliability, consumableCost, disruptionCost);
        }

        @Override public boolean deliberateDamage() { return false; }

        @Override
        public PlayerSnapshot apply(PlayerSnapshot player) {
            LinkedHashMap<String, String> equipment = new LinkedHashMap<>(player.equipmentItemKeys());
            equipment.putAll(equipmentUpdates);
            return copy(
                player, player.health(), player.absorption(), mitigationAfter, player.statusEffects(),
                player.blocking(), player.deathProtection(), equipment
            );
        }
    }

    record ApplyEffects(
        StatusEffectsSnapshot statusEffectsAfter,
        float healthGain,
        float absorptionGain,
        String itemKey,
        int requiredServerTicks,
        boolean legal,
        boolean authoritativePrerequisitesSatisfied,
        double reliability,
        int consumableCost,
        int disruptionCost
    ) implements SurvivalAction {
        public ApplyEffects(
            StatusEffectsSnapshot statusEffectsAfter,
            float healthGain,
            float absorptionGain,
            int requiredServerTicks,
            boolean legal,
            boolean authoritativePrerequisitesSatisfied,
            double reliability,
            int consumableCost,
            int disruptionCost
        ) {
            this(statusEffectsAfter, healthGain, absorptionGain, "", requiredServerTicks, legal,
                authoritativePrerequisitesSatisfied, reliability, consumableCost, disruptionCost);
        }

        public ApplyEffects {
            statusEffectsAfter = Objects.requireNonNull(statusEffectsAfter, "statusEffectsAfter");
            itemKey = Objects.requireNonNull(itemKey, "itemKey");
            if (!Float.isFinite(healthGain) || healthGain < 0f || !Float.isFinite(absorptionGain) || absorptionGain < 0f) {
                throw new IllegalArgumentException("health/absorption gains must be finite and non-negative");
            }
            validateCommon(requiredServerTicks, reliability, consumableCost, disruptionCost);
        }

        @Override public boolean deliberateDamage() { return false; }

        @Override
        public PlayerSnapshot apply(PlayerSnapshot player) {
            float health = Math.min(maxHealth(player), player.health() + healthGain);
            float absorption = player.absorption() + absorptionGain;
            return copy(
                player, health, absorption, player.mitigation(), statusEffectsAfter,
                player.blocking(), player.deathProtection(), player.equipmentItemKeys()
            );
        }
    }

    record PearlRescue(
        Set<String> removedThreatIds,
        long pearlImpactTick,
        Optional<Vec3Snapshot> targetPosition,
        int requiredServerTicks,
        boolean legal,
        boolean authoritativePrerequisitesSatisfied,
        double reliability,
        int consumableCost,
        int disruptionCost
    ) implements SurvivalAction {
        public PearlRescue(
            Set<String> removedThreatIds,
            long pearlImpactTick,
            int requiredServerTicks,
            boolean legal,
            boolean authoritativePrerequisitesSatisfied,
            double reliability,
            int consumableCost,
            int disruptionCost
        ) {
            this(removedThreatIds, pearlImpactTick, Optional.empty(), requiredServerTicks, legal,
                authoritativePrerequisitesSatisfied, reliability, consumableCost, disruptionCost);
        }

        public PearlRescue {
            removedThreatIds = Set.copyOf(Objects.requireNonNull(removedThreatIds, "removedThreatIds"));
            targetPosition = Objects.requireNonNull(targetPosition, "targetPosition");
            if (pearlImpactTick < 0) throw new IllegalArgumentException("pearlImpactTick must be non-negative");
            validateCommon(requiredServerTicks, reliability, consumableCost, disruptionCost);
        }

        @Override public boolean deliberateDamage() { return false; }
        @Override public PlayerSnapshot apply(PlayerSnapshot player) { return Objects.requireNonNull(player, "player"); }

        @Override
        public ThreatTimeline applyTimeline(ThreatTimeline timeline) {
            Objects.requireNonNull(timeline, "timeline");
            List<ThreatEvent> events = withoutThreats(timeline, removedThreatIds);
            DamageSourceSnapshot pearlDamage = new DamageSourceSnapshot(
                DamageRange.exact(5f), EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.IS_FALL),
                false, 1f, false, Optional.empty(), "minecraft:ender_pearl"
            );
            events.add(new ThreatEvent(
                "ender_pearl", ThreatKind.OTHER, new TickWindow(pearlImpactTick, pearlImpactTick), pearlDamage,
                Confidence.EXACT, Optional.empty(), targetPosition, false, false, false, false
            ));
            return new ThreatTimeline(events);
        }
    }

    record Relocate(
        Vec3Snapshot targetPosition,
        Set<String> removedThreatIds,
        int requiredServerTicks,
        boolean legal,
        boolean authoritativePrerequisitesSatisfied,
        double reliability,
        int consumableCost,
        int disruptionCost
    ) implements SurvivalAction {
        public Relocate {
            targetPosition = Objects.requireNonNull(targetPosition, "targetPosition");
            removedThreatIds = Set.copyOf(Objects.requireNonNull(removedThreatIds, "removedThreatIds"));
            validateCommon(requiredServerTicks, reliability, consumableCost, disruptionCost);
        }

        @Override public boolean deliberateDamage() { return false; }

        @Override
        public PlayerSnapshot apply(PlayerSnapshot player) {
            Objects.requireNonNull(player, "player");
            double dx = targetPosition.x() - player.position().x();
            double dy = targetPosition.y() - player.position().y();
            double dz = targetPosition.z() - player.position().z();
            AabbSnapshot box = player.boundingBox();
            AabbSnapshot moved = new AabbSnapshot(
                box.minX() + dx, box.minY() + dy, box.minZ() + dz,
                box.maxX() + dx, box.maxY() + dy, box.maxZ() + dz
            );
            return new PlayerSnapshot(
                player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(),
                player.deadOrDying(), player.difficulty(), player.mitigation(), player.statusEffects(), player.blocking(),
                player.hurtState(), player.deathProtection(), moved, targetPosition, player.velocity(),
                player.equipmentItemKeys(), player.stateProperties()
            );
        }

        @Override
        public ThreatTimeline applyTimeline(ThreatTimeline timeline) {
            return new ThreatTimeline(withoutThreats(timeline, removedThreatIds));
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
        @Override public boolean deliberateDamage() { return true; }
        @Override public PlayerSnapshot apply(PlayerSnapshot player) { return player; }
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

    private static void validateCommon(int requiredServerTicks, double reliability, int consumableCost, int disruptionCost) {
        if (requiredServerTicks < 0) throw new IllegalArgumentException("requiredServerTicks must be non-negative");
        if (!Double.isFinite(reliability) || reliability < 0d || reliability > 1d) {
            throw new IllegalArgumentException("reliability must be finite and in [0,1]");
        }
        if (consumableCost < 0 || disruptionCost < 0) throw new IllegalArgumentException("costs must be non-negative");
    }

    private static PlayerSnapshot copy(
        PlayerSnapshot player,
        float health,
        float absorption,
        MitigationSnapshot mitigation,
        StatusEffectsSnapshot statusEffects,
        BlockingSnapshot blocking,
        DeathProtectionSnapshot deathProtection,
        Map<String, String> equipmentItemKeys
    ) {
        Objects.requireNonNull(player, "player");
        return new PlayerSnapshot(
            health, absorption, player.playerInvulnerable(), player.abilityInvulnerable(), player.deadOrDying(),
            player.difficulty(), mitigation, statusEffects, blocking, player.hurtState(), deathProtection,
            player.boundingBox(), player.position(), player.velocity(), equipmentItemKeys, player.stateProperties()
        );
    }

    private static float maxHealth(PlayerSnapshot player) {
        String raw = player.state("max_health");
        if (raw != null) {
            try {
                float parsed = Float.parseFloat(raw);
                if (Float.isFinite(parsed) && parsed > 0f) return Math.max(player.health(), parsed);
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(player.health(), 20f);
    }

    private static List<ThreatEvent> withoutThreats(ThreatTimeline timeline, Set<String> removedThreatIds) {
        Objects.requireNonNull(timeline, "timeline");
        List<ThreatEvent> events = new ArrayList<>(timeline.events().size());
        for (ThreatEvent event : timeline.events()) {
            if (!removedThreatIds.contains(event.id())) events.add(event);
        }
        return events;
    }

    private static ThreatEvent copyEvent(ThreatEvent event, DamageSourceSnapshot damage) {
        return new ThreatEvent(
            event.id(), event.kind(), event.impact(), damage, event.confidence(), event.sourcePosition(),
            event.impactPosition(), event.avoidable(), event.blockable(), event.relocatable(), event.canDisableBlocking()
        );
    }

    private static DamageSourceSnapshot copyDamageSource(DamageSourceSnapshot source, DamageRange rawDamage) {
        return new DamageSourceSnapshot(
            rawDamage, source.flags(), source.scalesWithDifficulty(), source.freezingMultiplier(),
            source.piercingProjectile(), source.sourcePosition(), source.sourceKey(),
            source.applicationHealthThresholdExclusive()
        );
    }
}
