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
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

        if (policy.deathProtection() && !context.player().deathProtection().anyHandAvailable()) {
            routePlanner.choose(inventory, menu).ifPresent(route -> addProtectionCandidate(candidates, inventory, menu, route));
        }

        if (policy.shields()) addShieldCandidate(candidates, context, timeline, inventory);
        addHeldNonTotemCandidates(candidates, context, inventory, policy);
        return List.copyOf(candidates);
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
        DeathProtectionSnapshot.ProtectionItem item = routedSlot.deathProtectionItem().orElseGet(() ->
            "minecraft:totem_of_undying".equals(routedSlot.stackKey())
                ? DeathProtectionSnapshot.ProtectionItem.vanillaTotem()
                : DeathProtectionSnapshot.ProtectionItem.generic()
        );

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

    private static void addHeldNonTotemCandidates(
        List<SurvivalAction> candidates,
        PredictionContext context,
        InventorySnapshot inventory,
        RescuePolicy policy
    ) {
        inventory.slot(inventory.selectedHotbarIndex()).ifPresent(slot ->
            addHeldItemCandidates(candidates, context, slot, SurvivalAction.Hand.MAIN_HAND, policy));
        inventory.slot(40).ifPresent(slot ->
            addHeldItemCandidates(candidates, context, slot, SurvivalAction.Hand.OFF_HAND, policy));
    }

    private static void addHeldItemCandidates(
        List<SurvivalAction> candidates,
        PredictionContext context,
        InventorySlotSnapshot slot,
        SurvivalAction.Hand hand,
        RescuePolicy policy
    ) {
        if (policy.consumables()) {
            slot.consumable().ifPresent(consumable -> addConsumableCandidate(candidates, context, slot, hand, consumable));
        }
        if (policy.equipment()) {
            slot.equippable().ifPresent(equippable -> addEquipmentCandidate(candidates, context, slot, hand, equippable));
        }
    }

    private static void addConsumableCandidate(
        List<SurvivalAction> candidates,
        PredictionContext context,
        InventorySlotSnapshot slot,
        SurvivalAction.Hand hand,
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

        candidates.add(new SurvivalAction.ApplyEffects(
            effectsAfter,
            0f,
            absorptionGain,
            slot.stackKey(),
            consumable.consumeTicks(),
            true,
            true,
            1d,
            1,
            1,
            java.util.Optional.of(new SurvivalAction.HeldItemRef(
                hand, slot.stackKey(), slot.componentFingerprint()
            )),
            consumable.guaranteedEffects(),
            absorptionFloor
        ));
    }

    private static void addEquipmentCandidate(
        List<SurvivalAction> candidates,
        PredictionContext context,
        InventorySlotSnapshot slot,
        SurvivalAction.Hand hand,
        EquippableSurvivalSnapshot equippable
    ) {
        if (!equippable.usable() || !equippable.armorPiece().present()) return;
        ArmorPieceSnapshot piece = equippable.armorPiece();
        MitigationSnapshot mitigationAfter = replaceArmorPiece(context.player().mitigation(), piece);
        String equipmentSlot = piece.slot().name().toLowerCase(Locale.ROOT);

        candidates.add(new SurvivalAction.SwapEquipment(
            mitigationAfter,
            Map.of(equipmentSlot, slot.stackKey()),
            0,
            true,
            true,
            1d,
            0,
            2,
            java.util.Optional.of(new SurvivalAction.HeldItemRef(
                hand, slot.stackKey(), slot.componentFingerprint()
            )),
            java.util.Optional.of(piece)
        ));
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
