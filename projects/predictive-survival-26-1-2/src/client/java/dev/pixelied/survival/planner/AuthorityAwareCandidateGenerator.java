package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.execution.EquipmentAuthorityProjection;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
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
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(equipment, "equipment");

        long damageDeadline = timeline.events().stream()
            .mapToLong(event -> event.impact().earliest())
            .min()
            .orElse(context.timing().nextPacketProcessingWindow().latest());
        InventorySnapshot projectedInventory = equipment.conservativeInventoryAt(inventory, damageDeadline);
        return delegate.generate(context, timeline, projectedInventory, menu, policy);
    }
}
