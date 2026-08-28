package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.execution.DeathProtectionPopTracker;
import dev.pixelied.survival.execution.EquipmentAuthorityProjection;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.timeline.CausalThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.List;
import java.util.Objects;

/** Applies server-authority hand projection before delegating to the existing candidate generator. */
public final class AuthorityAwareCandidateGenerator {
    private final SurvivalCandidateGenerator delegate;

    public AuthorityAwareCandidateGenerator() {
        this(new SurvivalCandidateGenerator());
    }

    AuthorityAwareCandidateGenerator(SurvivalCandidateGenerator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        ThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy,
        EquipmentAuthorityProjection equipment
    ) {
        return generate(context, timeline, inventory, menu, policy, equipment, null);
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        CausalThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy,
        EquipmentAuthorityProjection equipment
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(equipment, "equipment");

        var earliestRelativeImpact = timeline.expandedTimeline().events().stream()
            .mapToLong(event -> event.impact().earliest())
            .min();
        long damageDeadline = earliestRelativeImpact.isPresent()
            ? saturatingAdd(context.timing().clientTick(), earliestRelativeImpact.getAsLong())
            : context.timing().nextPacketProcessingWindow().latest();

        InventorySnapshot projectedInventory = equipment.conservativeInventoryAt(inventory, damageDeadline);
        DeathProtectionSnapshot guaranteedProtection = equipment.guaranteedDeathProtectionAt(damageDeadline);
        PredictionContext projectedContext = withGuaranteedProtection(context, guaranteedProtection);
        return delegate.generate(projectedContext, timeline, projectedInventory, menu, policy);
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        CausalThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy,
        EquipmentAuthorityProjection equipment,
        DeathProtectionPopTracker pops
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(equipment, "equipment");

        var earliestRelativeImpact = timeline.expandedTimeline().events().stream()
            .mapToLong(event -> event.impact().earliest())
            .min();
        long damageDeadline = earliestRelativeImpact.isPresent()
            ? saturatingAdd(context.timing().clientTick(), earliestRelativeImpact.getAsLong())
            : context.timing().nextPacketProcessingWindow().latest();

        InventorySnapshot projectedInventory = pops == null
            ? equipment.conservativeInventoryAt(inventory, damageDeadline)
            : pops.conservativeInventoryAfterPop(inventory, equipment, damageDeadline);
        DeathProtectionSnapshot guaranteedProtection = pops == null
            ? equipment.guaranteedDeathProtectionAt(damageDeadline)
            : pops.projectedDeathProtectionAt(equipment, damageDeadline);
        PredictionContext projectedContext = withGuaranteedProtection(context, guaranteedProtection);
        return delegate.generate(projectedContext, timeline, projectedInventory, menu, policy);
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        ThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy,
        EquipmentAuthorityProjection equipment,
        DeathProtectionPopTracker pops
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(equipment, "equipment");

        var earliestRelativeImpact = timeline.events().stream()
            .mapToLong(event -> event.impact().earliest())
            .min();
        long damageDeadline = earliestRelativeImpact.isPresent()
            ? saturatingAdd(context.timing().clientTick(), earliestRelativeImpact.getAsLong())
            : context.timing().nextPacketProcessingWindow().latest();

        InventorySnapshot projectedInventory = pops == null
            ? equipment.conservativeInventoryAt(inventory, damageDeadline)
            : pops.conservativeInventoryAfterPop(inventory, equipment, damageDeadline);
        DeathProtectionSnapshot guaranteedProtection = pops == null
            ? equipment.guaranteedDeathProtectionAt(damageDeadline)
            : pops.projectedDeathProtectionAt(equipment, damageDeadline);
        PredictionContext projectedContext = withGuaranteedProtection(context, guaranteedProtection);
        return delegate.generate(projectedContext, timeline, projectedInventory, menu, policy);
    }

    private static PredictionContext withGuaranteedProtection(
        PredictionContext context,
        DeathProtectionSnapshot guaranteedProtection
    ) {
        PlayerSnapshot player = context.player();
        PlayerSnapshot projectedPlayer = new PlayerSnapshot(
            player.health(),
            player.absorption(),
            player.playerInvulnerable(),
            player.abilityInvulnerable(),
            player.deadOrDying(),
            player.difficulty(),
            player.mitigation(),
            player.statusEffects(),
            player.blocking(),
            player.hurtState(),
            Objects.requireNonNull(guaranteedProtection, "guaranteedProtection"),
            player.boundingBox(),
            player.position(),
            player.velocity(),
            player.equipmentItemKeys(),
            player.stateProperties()
        );
        return new PredictionContext(
            projectedPlayer,
            context.world(),
            context.timing(),
            context.limits(),
            context.safetyMode()
        );
    }

    private static long saturatingAdd(long value, long increment) {
        return increment > 0L && value > Long.MAX_VALUE - increment
            ? Long.MAX_VALUE
            : value + increment;
    }
}
