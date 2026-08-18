package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.DeathProtectionRoutePlanner;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SurvivalCandidateGenerator {
    private static final int SHIELD_WARMUP_TICKS = 5;

    private final DeathProtectionRoutePlanner routePlanner;

    public SurvivalCandidateGenerator() {
        this(new DeathProtectionRoutePlanner());
    }

    SurvivalCandidateGenerator(DeathProtectionRoutePlanner routePlanner) {
        this.routePlanner = Objects.requireNonNull(routePlanner, "routePlanner");
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        ThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(menu, "menu");

        if (timeline.events().isEmpty()) return List.of();
        List<SurvivalAction> candidates = new ArrayList<>();

        if (!context.player().deathProtection().anyHandAvailable()) {
            routePlanner.choose(inventory, menu).ifPresent(route -> addProtectionCandidate(candidates, inventory, route));
        }

        addShieldCandidate(candidates, context, timeline, inventory);
        return List.copyOf(candidates);
    }

    private static void addProtectionCandidate(
        List<SurvivalAction> candidates,
        InventorySnapshot inventory,
        DeathProtectionRoute route
    ) {
        if (route instanceof DeathProtectionRoute.AlreadyInHand) return;

        SurvivalAction.Hand hand;
        if (route instanceof DeathProtectionRoute.HotbarSelect) {
            hand = SurvivalAction.Hand.MAIN_HAND;
        } else {
            DeathProtectionRoute.ContainerSwap swap = (DeathProtectionRoute.ContainerSwap) route;
            hand = swap.destination() == DeathProtectionRoute.Destination.OFF_HAND
                ? SurvivalAction.Hand.OFF_HAND
                : SurvivalAction.Hand.MAIN_HAND;
        }

        boolean vanillaTotem = inventory.slots().values().stream()
            .anyMatch(slot -> slot.deathProtection() && "minecraft:totem_of_undying".equals(slot.stackKey()));
        DeathProtectionSnapshot.ProtectionItem item = vanillaTotem
            ? DeathProtectionSnapshot.ProtectionItem.vanillaTotem()
            : DeathProtectionSnapshot.ProtectionItem.generic();

        candidates.add(new SurvivalAction.EquipDeathProtection(
            item,
            hand,
            0,
            true,
            true,
            1d,
            1,
            hand == SurvivalAction.Hand.OFF_HAND ? 1 : 2
        ));
    }

    private static void addShieldCandidate(
        List<SurvivalAction> candidates,
        PredictionContext context,
        ThreatTimeline timeline,
        InventorySnapshot inventory
    ) {
        boolean guaranteedBlock = timeline.events().stream().allMatch(event -> event.blockable() && !event.canDisableBlocking());
        if (!guaranteedBlock) return;

        boolean activeOffhand = inventory.activeOffhandShield();
        boolean selectedMainhandShield = inventory.slot(inventory.selectedHotbarIndex())
            .map(slot -> slot.count() > 0 && "minecraft:shield".equals(slot.stackKey()))
            .orElse(false);
        if (!activeOffhand && !selectedMainhandShield) return;

        BlockingSnapshot blocking = context.player().blocking();
        int elapsed = activeOffhand ? blocking.elapsedUseTicks() : 0;
        int required = activeOffhand
            ? Math.max(blocking.requiredUseTicks(), SHIELD_WARMUP_TICKS)
            : SHIELD_WARMUP_TICKS;
        int requiredServerTicks = activeOffhand && elapsed >= required ? 0 : Math.max(0, required - elapsed);

        candidates.add(new SurvivalAction.RaiseShield(
            requiredServerTicks,
            true,
            true,
            true,
            1d,
            1f,
            elapsed,
            required,
            0
        ));
    }
}
