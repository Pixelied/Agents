package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.ArmorPieceSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.ConsumableSurvivalSnapshot;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.DeathProtectionRoutePlanner;
import dev.pixelied.survival.inventory.EquippableSurvivalSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import dev.pixelied.survival.inventory.SurvivalItemRoutePlanner;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTimelineSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SurvivalCandidateGenerator {
    private static final int SHIELD_WARMUP_TICKS = 5;

    private final DeathProtectionRoutePlanner routePlanner;
    private final SurvivalItemRoutePlanner itemRoutePlanner;
    private final ThreatTimelineSimulator timelineSimulator;

    public SurvivalCandidateGenerator() {
        this(new DeathProtectionRoutePlanner(), new SurvivalItemRoutePlanner(), new ThreatTimelineSimulator());
    }

    SurvivalCandidateGenerator(DeathProtectionRoutePlanner routePlanner) {
        this(routePlanner, new SurvivalItemRoutePlanner(), new ThreatTimelineSimulator());
    }

    SurvivalCandidateGenerator(DeathProtectionRoutePlanner routePlanner, ThreatTimelineSimulator timelineSimulator) {
        this(routePlanner, new SurvivalItemRoutePlanner(), timelineSimulator);
    }

    SurvivalCandidateGenerator(
        DeathProtectionRoutePlanner routePlanner,
        SurvivalItemRoutePlanner itemRoutePlanner,
        ThreatTimelineSimulator timelineSimulator
    ) {
        this.routePlanner = Objects.requireNonNull(routePlanner, "routePlanner");
        this.itemRoutePlanner = Objects.requireNonNull(itemRoutePlanner, "itemRoutePlanner");
        this.timelineSimulator = Objects.requireNonNull(timelineSimulator, "timelineSimulator");
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        ThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu
    ) {
        return generate(context, timeline, inventory, menu, RescuePolicy.smartDefaults());
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        ThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(policy, "policy");

        if (timeline.events().isEmpty()) return List.of();
        List<SurvivalAction> candidates = new ArrayList<>();

        if (policy.deathProtection() && policy.inventoryRouting()) {
            DeathProtectionSnapshot protection = context.player().deathProtection();
            if (!protection.anyHandAvailable()) {
                boolean dualFromEmpty = policy.proactiveDualProtection()
                    && policy.mainHandTakeover()
                    && dualProtectionRoutesAvailable(inventory, menu)
                    && needsMultipleProtectionsFromEmpty(context, timeline, inventory);
                if (dualFromEmpty) {
                    routePlanner.choose(inventory, menu, DeathProtectionRoute.Destination.MAIN_HAND)
                        .ifPresent(route -> addProtectionCandidate(candidates, inventory, menu, route));
                    routePlanner.choose(inventory, menu, DeathProtectionRoute.Destination.OFF_HAND)
                        .ifPresent(route -> addProtectionCandidate(candidates, inventory, menu, route));
                } else {
                    var route = policy.mainHandTakeover()
                        ? routePlanner.choose(inventory, menu)
                        : routePlanner.choose(inventory, menu, DeathProtectionRoute.Destination.OFF_HAND);
                    route.ifPresent(value -> addProtectionCandidate(candidates, inventory, menu, value));
                }
            } else if (policy.proactiveDualProtection() && needsAdditionalProtection(context, timeline)) {
                if (protection.offHand().isPresent() && protection.mainHand().isEmpty() && policy.mainHandTakeover()) {
                    routePlanner.choose(inventory, menu, DeathProtectionRoute.Destination.MAIN_HAND)
                        .ifPresent(route -> addProtectionCandidate(candidates, inventory, menu, route));
                } else if (protection.mainHand().isPresent() && protection.offHand().isEmpty()) {
                    routePlanner.choose(inventory, menu, DeathProtectionRoute.Destination.OFF_HAND)
                        .ifPresent(route -> addProtectionCandidate(candidates, inventory, menu, route));
                }
            }
        }

        if (policy.shields()) addShieldCandidate(candidates, context, timeline, inventory);
        addNonTotemCandidates(candidates, context, inventory, menu, policy);
        return List.copyOf(candidates);
    }

    private boolean needsAdditionalProtection(PredictionContext context, ThreatTimeline timeline) {
        var baseline = timelineSimulator.simulate(context.player(), timeline);
        return !baseline.survived() && baseline.consumedDeathProtectionCount() > 0;
    }

    private boolean needsMultipleProtectionsFromEmpty(
        PredictionContext context,
        ThreatTimeline timeline,
        InventorySnapshot inventory
    ) {
        InventorySlotSnapshot source = inventory.slots().values().stream()
            .filter(slot -> slot.count() > 0 && slot.deathProtection())
            .sorted(java.util.Comparator.comparingInt(InventorySlotSnapshot::inventoryIndex))
            .findFirst()
            .orElse(null);
        if (source == null) return false;

        DeathProtectionSnapshot.ProtectionItem item = protectionItem(source);
        SurvivalAction.EquipDeathProtection hypothetical = new SurvivalAction.EquipDeathProtection(
            item, SurvivalAction.Hand.MAIN_HAND, 0, true, true, 1d, 1, 0
        );
        var withOneProtection = timelineSimulator.simulate(hypothetical.apply(context.player()), timeline);
        return !withOneProtection.survived() && withOneProtection.consumedDeathProtectionCount() > 0;
    }

    private static boolean dualProtectionRoutesAvailable(InventorySnapshot inventory, MenuSlotMap menu) {
        List<InventorySlotSnapshot> sources = inventory.slots().values().stream()
            .filter(slot -> slot.count() > 0 && slot.deathProtection())
            .sorted(java.util.Comparator.comparingInt(InventorySlotSnapshot::inventoryIndex))
            .toList();
        if (sources.isEmpty()) return false;

        for (InventorySlotSnapshot mainSource : sources) {
            if (!canRouteToMainHand(mainSource, inventory, menu)) continue;
            for (InventorySlotSnapshot offSource : sources) {
                if (!canRouteToOffHand(offSource, menu)) continue;
                if (mainSource.inventoryIndex() != offSource.inventoryIndex() || mainSource.count() >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canRouteToMainHand(
        InventorySlotSnapshot source,
        InventorySnapshot inventory,
        MenuSlotMap menu
    ) {
        int index = source.inventoryIndex();
        if (index >= 0 && index <= 8 && index != inventory.selectedHotbarIndex()) return true;
        if (index == inventory.selectedHotbarIndex()) return true;
        return index != 40
            && menu.menuSlotForInventoryIndex(index).isPresent()
            && menu.menuSlotForInventoryIndex(inventory.selectedHotbarIndex()).isPresent();
    }

    private static boolean canRouteToOffHand(InventorySlotSnapshot source, MenuSlotMap menu) {
        int index = source.inventoryIndex();
        if (index == 40) return true;
        return menu.menuSlotForInventoryIndex(index).isPresent()
            && menu.menuSlotForInventoryIndex(40).isPresent();
    }

    private static void addProtectionCandidate(
        List<SurvivalAction> candidates,
        InventorySnapshot inventory,
        MenuSlotMap menu,
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

        InventorySlotSnapshot routedSlot = routedProtectionSlot(inventory, menu, route)
            .orElseThrow(() -> new IllegalStateException("death-protection route has no source inventory slot"));
        DeathProtectionSnapshot.ProtectionItem item = protectionItem(routedSlot);

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

    private static DeathProtectionSnapshot.ProtectionItem protectionItem(InventorySlotSnapshot slot) {
        return slot.deathProtectionItem().orElseGet(() ->
            "minecraft:totem_of_undying".equals(slot.stackKey())
                ? DeathProtectionSnapshot.ProtectionItem.vanillaTotem()
                : DeathProtectionSnapshot.ProtectionItem.generic()
        );
    }

    private static java.util.Optional<InventorySlotSnapshot> routedProtectionSlot(
        InventorySnapshot inventory,
        MenuSlotMap menu,
        DeathProtectionRoute route
    ) {
        if (route instanceof DeathProtectionRoute.HotbarSelect hotbar) {
            return inventory.slot(hotbar.hotbarIndex());
        }
        if (route instanceof DeathProtectionRoute.ContainerSwap swap) {
            return menu.inventoryIndexToMenuSlot().entrySet().stream()
                .filter(entry -> entry.getValue() == swap.sourceMenuSlot())
                .map(entry -> inventory.slot(entry.getKey()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .findFirst();
        }
        return java.util.Optional.empty();
    }

    private static void addShieldCandidate(
        List<SurvivalAction> candidates,
        PredictionContext context,
        ThreatTimeline timeline,
        InventorySnapshot inventory
    ) {
        boolean hasBlockableThreat = timeline.events().stream().anyMatch(ThreatEvent::blockable);
        if (!hasBlockableThreat) return;

        boolean activeOffhand = inventory.activeOffhandShield()
            && inventory.slot(40).map(slot -> !slot.blockingOnCooldown()).orElse(false);
        boolean selectedMainhandShield = inventory.slot(inventory.selectedHotbarIndex())
            .map(slot -> slot.count() > 0
                && "minecraft:shield".equals(slot.stackKey())
                && !slot.blockingOnCooldown())
            .orElse(false);
        if (!activeOffhand && !selectedMainhandShield) return;

        BlockingSnapshot blocking = context.player().blocking();
        int elapsed = activeOffhand ? blocking.elapsedUseTicks() : 0;
        int required = activeOffhand
            ? Math.max(blocking.requiredUseTicks(), SHIELD_WARMUP_TICKS)
            : SHIELD_WARMUP_TICKS;
        int requiredServerTicks = activeOffhand && elapsed >= required ? 0 : Math.max(0, required - elapsed);

        java.util.Optional<dev.pixelied.survival.damage.BlockingProfileSnapshot> profile = activeOffhand
            ? context.player().blocking().profile().or(() -> inventory.slot(40).flatMap(InventorySlotSnapshot::blockingProfile))
            : inventory.slot(inventory.selectedHotbarIndex()).flatMap(InventorySlotSnapshot::blockingProfile);

        candidates.add(new SurvivalAction.RaiseShield(
            requiredServerTicks,
            true,
            true,
            true,
            1d,
            profile.isPresent() ? 0f : 1f,
            elapsed,
            required,
            0,
            profile
        ));
    }

    private void addNonTotemCandidates(
        List<SurvivalAction> candidates,
        PredictionContext context,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy
    ) {
        List<InventorySlotSnapshot> slots = inventory.slots().values().stream()
            .filter(slot -> slot.count() > 0)
            .sorted(java.util.Comparator.comparingInt(InventorySlotSnapshot::inventoryIndex))
            .toList();
        for (InventorySlotSnapshot slot : slots) {
            itemRoutePlanner.route(
                inventory, menu, slot, policy.inventoryRouting(), policy.mainHandTakeover()
            ).ifPresent(route -> addRoutedItemCandidates(candidates, context, slot, route, policy));
        }
    }

    private static void addRoutedItemCandidates(
        List<SurvivalAction> candidates,
        PredictionContext context,
        InventorySlotSnapshot slot,
        SurvivalItemRoute route,
        RescuePolicy policy
    ) {
        if (policy.consumables()) {
            slot.consumable().ifPresent(consumable ->
                addConsumableCandidate(candidates, context, slot, route, consumable));
        }
        if (policy.equipment()) {
            slot.equippable().ifPresent(equippable ->
                addEquipmentCandidate(candidates, context, slot, route, equippable));
        }
    }

    private static void addConsumableCandidate(
        List<SurvivalAction> candidates,
        PredictionContext context,
        InventorySlotSnapshot slot,
        SurvivalItemRoute route,
        ConsumableSurvivalSnapshot consumable
    ) {
        if (!consumable.usable() || consumable.guaranteedEffects().isEmpty()) return;

        StatusEffectsSnapshot effectsAfter = context.player().statusEffects().apply(consumable.guaranteedEffects());
        float absorptionFloor = context.player().absorption();
        for (EffectInstanceSnapshot effect : consumable.guaranteedEffects()) {
            if ("minecraft:absorption".equals(effect.effectKey())) {
                absorptionFloor = Math.max(absorptionFloor, 4f * (effect.amplifier() + 1));
            }
        }
        float absorptionGain = Math.max(0f, absorptionFloor - context.player().absorption());
        int requiredServerTicks = saturatingTickAdd(consumable.consumeTicks(), route.requiredServerTicks());

        candidates.add(new SurvivalAction.ApplyEffects(
            effectsAfter,
            0f,
            absorptionGain,
            slot.stackKey(),
            requiredServerTicks,
            true,
            true,
            1d,
            1,
            1 + route.requiredServerTicks(),
            java.util.Optional.of(new SurvivalAction.HeldItemRef(
                route.destinationHand(), slot.stackKey(), slot.componentFingerprint(), java.util.Optional.of(route)
            )),
            consumable.guaranteedEffects(),
            absorptionFloor
        ));
    }

    private static void addEquipmentCandidate(
        List<SurvivalAction> candidates,
        PredictionContext context,
        InventorySlotSnapshot slot,
        SurvivalItemRoute route,
        EquippableSurvivalSnapshot equippable
    ) {
        if (!equippable.usable() || !equippable.armorPiece().present()) return;
        ArmorPieceSnapshot piece = equippable.armorPiece();
        MitigationSnapshot mitigationAfter = replaceArmorPiece(context.player().mitigation(), piece);
        String equipmentSlot = piece.slot().name().toLowerCase(Locale.ROOT);

        candidates.add(new SurvivalAction.SwapEquipment(
            mitigationAfter,
            Map.of(equipmentSlot, slot.stackKey()),
            route.requiredServerTicks(),
            true,
            true,
            1d,
            0,
            2 + route.requiredServerTicks(),
            java.util.Optional.of(new SurvivalAction.HeldItemRef(
                route.destinationHand(), slot.stackKey(), slot.componentFingerprint(), java.util.Optional.of(route)
            )),
            java.util.Optional.of(piece)
        ));
    }

    private static int saturatingTickAdd(int left, int right) {
        long total = (long) left + right;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static MitigationSnapshot replaceArmorPiece(
        MitigationSnapshot current,
        ArmorPieceSnapshot replacement
    ) {
        List<ArmorPieceSnapshot> pieces = new ArrayList<>(current.armorPieces().size() + 1);
        ArmorPieceSnapshot replaced = null;
        for (ArmorPieceSnapshot piece : current.armorPieces()) {
            if (piece.slot() == replacement.slot()) {
                replaced = piece;
            } else {
                pieces.add(piece);
            }
        }
        pieces.add(replacement);

        float armor = current.armor() - (replaced == null ? 0f : replaced.armor()) + replacement.armor();
        float toughness = current.toughness() - (replaced == null ? 0f : replaced.toughness()) + replacement.toughness();
        int protection = current.enchantmentProtection()
            - (replaced == null ? 0 : replaced.enchantmentProtection())
            + replacement.enchantmentProtection();
        protection = Math.max(0, Math.min(20, protection));

        boolean helmetPresent = current.helmetPresent();
        int helmetDurability = current.helmetDurability();
        if (replacement.slot() == ArmorPieceSnapshot.Slot.HEAD) {
            helmetPresent = replacement.present();
            helmetDurability = replacement.remainingDurability();
        }

        return new MitigationSnapshot(
            Math.max(0f, armor),
            Math.max(0f, toughness),
            current.armorEffectivenessMultiplier(),
            protection,
            helmetPresent,
            helmetDurability,
            pieces
        );
    }
}
