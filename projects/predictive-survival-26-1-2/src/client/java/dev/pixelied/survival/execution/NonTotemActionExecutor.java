package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class NonTotemActionExecutor {
    private static final long CONFIRMATION_TIMEOUT_TICKS = 40L;
    private static final double POSITION_TOLERANCE = 0.75d;
    private static final float VALUE_EPSILON = 0.001f;

    private Pending pending;
    private RestorationCheckpoint restorationCheckpoint;
    private HotbarRestorationCandidate hotbarRestorationCandidate;
    private ContainerRestorationCandidate containerRestorationCandidate;

    public ExecutionStatus begin(SurvivalAction action, NonTotemExecutionContext context) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");
        pending = null;
        hotbarRestorationCandidate = null;
        containerRestorationCandidate = null;

        if (!action.legal() || !action.authoritativePrerequisitesSatisfied()) {
            return new ExecutionStatus.Failed("non-totem action is no longer legal", true);
        }

        if (action instanceof SurvivalAction.PlaceCover cover) {
            SurvivalAction.BlockTarget target = cover.target().orElse(null);
            if (target == null) return new ExecutionStatus.Failed("cover action has no executable block target", true);
            SurvivalAction.Hand hand = handHolding(context.base().inventory(), target.itemKey());
            if (hand == null) return missingHeldItem(target.itemKey());
            pending = directPending(action, context, action.apply(context.player()), hand);
            return new ExecutionStatus.WaitingForServer(
                "waiting for target cover block to be observed",
                new ExecutionCommand.PlaceBlock(target, hand)
            );
        }

        if (action instanceof SurvivalAction.SwapEquipment equipment) {
            String itemKey = singleEquipmentItem(equipment.equipmentUpdates());
            if (itemKey == null) {
                return new ExecutionStatus.Failed("equipment action must describe one concrete item swap", true);
            }
            if (equipmentSatisfied(equipment, context.player())) {
                return new ExecutionStatus.Confirmed("equipment state is already observed");
            }
            return beginStateAction(
                action,
                equipment.sourceItem(),
                itemKey,
                action.apply(context.player()),
                context
            );
        }

        if (action instanceof SurvivalAction.ApplyEffects effects) {
            if (effects.itemKey().isBlank()) {
                return new ExecutionStatus.Failed("effect action has no executable item key", true);
            }
            if (effectsSatisfied(effects, context.player(), context.player(), 0L)) {
                return new ExecutionStatus.Confirmed("effect state is already observed");
            }
            return beginStateAction(
                action,
                effects.sourceItem(),
                effects.itemKey(),
                effects.apply(context.player()),
                context
            );
        }

        if (action instanceof SurvivalAction.PearlRescue pearl) {
            Vec3Snapshot target = pearl.targetPosition().orElse(null);
            if (target == null) return new ExecutionStatus.Failed("pearl rescue has no predicted teleport target", true);
            SurvivalAction.Hand hand = handHolding(context.base().inventory(), "minecraft:ender_pearl");
            if (hand == null) return missingHeldItem("minecraft:ender_pearl");
            if (near(context.player().position(), target)) {
                return new ExecutionStatus.Confirmed("pearl destination is already observed");
            }
            pending = directPending(action, context, action.apply(context.player()), hand);
            return new ExecutionStatus.WaitingForServer(
                "waiting for server-observed pearl relocation",
                new ExecutionCommand.AimAndUseItem(hand, target)
            );
        }

        if (action instanceof SurvivalAction.Relocate relocate) {
            if (near(context.player().position(), relocate.targetPosition())) {
                return new ExecutionStatus.Confirmed("relocation target is already observed");
            }
            pending = directPending(action, context, relocate.apply(context.player()), null);
            return new ExecutionStatus.WaitingForServer(
                "waiting for server-observed relocation",
                new ExecutionCommand.MoveToward(relocate.targetPosition())
            );
        }

        return new ExecutionStatus.Failed("unsupported non-totem action type", true);
    }

    private ExecutionStatus beginStateAction(
        SurvivalAction action,
        Optional<SurvivalAction.HeldItemRef> sourceItem,
        String fallbackItemKey,
        PlayerSnapshot expected,
        NonTotemExecutionContext context
    ) {
        SurvivalAction.HeldItemRef source = sourceItem.orElse(null);
        SurvivalItemRoute route = source == null ? null : source.route().orElse(null);
        if (route == null) {
            SurvivalAction.Hand hand = source == null
                ? handHolding(context.base().inventory(), fallbackItemKey)
                : handHolding(context.base().inventory(), source);
            if (hand == null) return missingHeldItem(fallbackItemKey);
            pending = usingPending(action, context, expected, hand, null, action.requiredServerTicks());
            return new ExecutionStatus.WaitingForServer(
                "waiting for server-observed survival item use",
                new ExecutionCommand.UseItem(hand)
            );
        }

        if (!routeMatchesSource(route, source)) {
            return new ExecutionStatus.Failed("routed survival item identity changed before execution", true);
        }

        if (route instanceof SurvivalItemRoute.AlreadyHeld) {
            if (handHolding(context.base().inventory(), source) == null) {
                return missingHeldItem(source.itemKey());
            }
            int useTicks = useTicks(action, route);
            pending = usingPending(action, context, expected, route.destinationHand(), route, useTicks);
            return new ExecutionStatus.WaitingForServer(
                "waiting for server-observed survival item use",
                new ExecutionCommand.UseItem(route.destinationHand())
            );
        }

        if (route instanceof SurvivalItemRoute.HotbarSelect hotbar) {
            InventorySlotSnapshot sourceSlot = context.base().inventory().slot(hotbar.hotbarIndex()).orElse(null);
            if (!exact(sourceSlot, route)) {
                return new ExecutionStatus.Failed("routed hotbar survival stack changed before selection", true);
            }
            if (context.base().inventory().selectedHotbarIndex() == hotbar.hotbarIndex()) {
                int useTicks = useTicks(action, route);
                pending = usingPending(action, context, expected, route.destinationHand(), route, useTicks);
                return new ExecutionStatus.WaitingForServer(
                    "waiting for server-observed survival item use",
                    new ExecutionCommand.UseItem(route.destinationHand())
                );
            }
            int originalIndex = context.base().inventory().selectedHotbarIndex();
            context.base().inventory().slot(originalIndex).ifPresent(original ->
                hotbarRestorationCandidate = new HotbarRestorationCandidate(originalIndex, hotbar.hotbarIndex(), original));
            pending = routingPending(action, context, expected, route, useTicks(action, route));
            return new ExecutionStatus.WaitingForServer(
                "waiting for exact survival stack to become selected",
                new ExecutionCommand.SelectHotbar(hotbar.hotbarIndex())
            );
        }

        SurvivalItemRoute.ContainerSwap swap = (SurvivalItemRoute.ContainerSwap) route;
        InventorySlotSnapshot sourceSlot = context.base().inventory().slot(swap.sourceInventoryIndex()).orElse(null);
        InventorySlotSnapshot destinationBefore = context.base().inventory().slot(swap.destinationInventoryIndex()).orElse(null);
        if (!exact(sourceSlot, route)) {
            return new ExecutionStatus.Failed("routed container survival stack changed before swap", true);
        }
        if (destinationBefore == null) {
            return new ExecutionStatus.Failed("routed survival destination disappeared before swap", true);
        }
        if (context.base().menu().menuSlotForInventoryIndex(swap.sourceInventoryIndex()).orElse(-1) != swap.sourceMenuSlot()) {
            return new ExecutionStatus.Failed("routed container slot mapping changed before swap", true);
        }
        containerRestorationCandidate = new ContainerRestorationCandidate(
            context.base().menu().containerId(), swap, destinationBefore
        );
        pending = routingPending(action, context, expected, route, useTicks(action, route));
        return new ExecutionStatus.WaitingForServer(
            "waiting for exact survival stack container swap",
            new ExecutionCommand.SwapMenuSlot(
                context.base().menu().containerId(),
                context.base().menu().stateId(),
                swap.sourceMenuSlot(),
                swap.button()
            )
        );
    }

    public int remainingServerTicks(NonTotemExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (pending == null) return Integer.MAX_VALUE;

        long currentTick = context.base().currentServerTick();
        if (pending.stage() == Stage.ROUTING) {
            if (routeObserved(pending.route(), context)) return pending.useRequiredServerTicks();
            if (currentTick <= pending.latestServerStartTick()) {
                long remaining = pending.latestServerStartTick() - currentTick + pending.useRequiredServerTicks();
                return remaining >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
            }
            return Integer.MAX_VALUE;
        }

        SurvivalAction action = pending.action();
        if (action instanceof SurvivalAction.SwapEquipment equipment) {
            if (equipmentSatisfied(equipment, context.player())) return 0;
            return ticksUntilOrUnknown(currentTick, pending.latestServerStartTick());
        }

        if (action instanceof SurvivalAction.ApplyEffects effects) {
            long elapsed = Math.max(0L, currentTick - pending.useStartedAtServerTick());
            if (effectsSatisfied(effects, pending.expectedPlayer(), context.player(), elapsed)) return 0;

            ExecutionContext base = context.base();
            if (base.serverUsingItem() && base.usingHand() == pending.hand()) {
                return Math.max(0, pending.useRequiredServerTicks() - base.serverUseTicks());
            }
            if (currentTick <= pending.latestServerStartTick()) {
                long remaining = pending.latestServerStartTick() - currentTick + pending.useRequiredServerTicks();
                return remaining >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
            }
            return Integer.MAX_VALUE;
        }

        return Integer.MAX_VALUE;
    }

    public Optional<RestorationCheckpoint> takeRestorationCheckpoint() {
        RestorationCheckpoint checkpoint = restorationCheckpoint;
        restorationCheckpoint = null;
        return Optional.ofNullable(checkpoint);
    }

    public void reset() {
        pending = null;
        restorationCheckpoint = null;
        hotbarRestorationCandidate = null;
        containerRestorationCandidate = null;
    }

    public ExecutionStatus observe(NonTotemExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (pending == null) return new ExecutionStatus.Failed("no non-totem action is pending", true);

        if (context.base().currentServerTick() - pending.startedAtServerTick() > CONFIRMATION_TIMEOUT_TICKS) {
            pending = null;
            hotbarRestorationCandidate = null;
            containerRestorationCandidate = null;
            return new ExecutionStatus.Failed("server confirmation timed out", true);
        }

        if (pending.stage() == Stage.ROUTING) {
            ExecutionStatus routeStatus = observeRoute(context);
            if (routeStatus != null) return routeStatus;
        }

        SurvivalAction action = pending.action();
        boolean confirmed;
        if (action instanceof SurvivalAction.PlaceCover cover) {
            confirmed = cover.target().map(context.confirmedBlocks()::contains).orElse(false);
        } else if (action instanceof SurvivalAction.SwapEquipment equipment) {
            confirmed = equipmentSatisfied(equipment, context.player());
        } else if (action instanceof SurvivalAction.ApplyEffects effects) {
            confirmed = effectsSatisfied(
                effects,
                pending.expectedPlayer(),
                context.player(),
                Math.max(0L, context.base().currentServerTick() - pending.useStartedAtServerTick())
            );
        } else if (action instanceof SurvivalAction.PearlRescue pearl) {
            confirmed = pearl.targetPosition().map(target -> near(context.player().position(), target)).orElse(false);
        } else if (action instanceof SurvivalAction.Relocate relocate) {
            confirmed = near(context.player().position(), relocate.targetPosition());
        } else {
            pending = null;
            hotbarRestorationCandidate = null;
            containerRestorationCandidate = null;
            return new ExecutionStatus.Failed("pending action type became unsupported", true);
        }

        if (!confirmed) return new ExecutionStatus.WaitingForServer("waiting for authoritative action confirmation");
        capturePostActionRestoration(context.base());
        pending = null;
        return new ExecutionStatus.Confirmed("non-totem action confirmed by observed state");
    }

    /** Returns null when routing is complete and normal action observation should continue. */
    private ExecutionStatus observeRoute(NonTotemExecutionContext context) {
        SurvivalItemRoute route = pending.route();
        if (route == null) {
            pending = null;
            hotbarRestorationCandidate = null;
            containerRestorationCandidate = null;
            return new ExecutionStatus.Failed("routed action lost its route", true);
        }

        if (route instanceof SurvivalItemRoute.HotbarSelect hotbar) {
            InventorySlotSnapshot slot = context.base().inventory().slot(hotbar.hotbarIndex()).orElse(null);
            if (!exact(slot, route)) {
                pending = null;
                hotbarRestorationCandidate = null;
                return new ExecutionStatus.Failed("selected survival stack no longer matches exact planned components", true);
            }
            if (context.base().inventory().selectedHotbarIndex() != hotbar.hotbarIndex()) {
                return new ExecutionStatus.WaitingForServer("waiting for exact survival stack hotbar selection");
            }
            validateHotbarRestorationCandidate(context.base(), hotbar);
        } else if (route instanceof SurvivalItemRoute.ContainerSwap swap) {
            if (context.base().menu().containerId() != pending.containerId()) {
                pending = null;
                containerRestorationCandidate = null;
                return new ExecutionStatus.Failed("container changed before survival stack route confirmed", true);
            }
            if (context.base().menu().stateId() == pending.containerStateId()) {
                return new ExecutionStatus.WaitingForServer("waiting for authoritative container revision");
            }
            InventorySlotSnapshot destination = context.base().inventory().slot(swap.destinationInventoryIndex()).orElse(null);
            if (!exact(destination, route)) {
                pending = null;
                containerRestorationCandidate = null;
                return new ExecutionStatus.Failed("container revised without exact planned survival stack destination", true);
            }
            if (!validateContainerRestorationCandidate(context.base(), swap)) {
                pending = null;
                return new ExecutionStatus.Failed("container survival route did not preserve the displaced destination stack", true);
            }
        } else {
            pending = null;
            hotbarRestorationCandidate = null;
            containerRestorationCandidate = null;
            return new ExecutionStatus.Failed("unexpected route stage for already-held survival stack", true);
        }

        Pending routed = pending;
        pending = new Pending(
            routed.action(),
            routed.startedAtServerTick(),
            context.base().currentServerTick(),
            routed.expectedPlayer(),
            routed.hand(),
            context.base().timing().nextPacketProcessingWindow().latest(),
            routed.useRequiredServerTicks(),
            Stage.USING,
            routed.route(),
            routed.containerId(),
            routed.containerStateId()
        );
        return new ExecutionStatus.WaitingForServer(
            "survival stack route confirmed; waiting for server-observed item use",
            new ExecutionCommand.UseItem(routed.hand())
        );
    }

    private void validateHotbarRestorationCandidate(ExecutionContext context, SurvivalItemRoute.HotbarSelect hotbar) {
        HotbarRestorationCandidate candidate = hotbarRestorationCandidate;
        if (candidate == null || candidate.routedHotbarIndex() != hotbar.hotbarIndex()) {
            hotbarRestorationCandidate = null;
            return;
        }
        InventorySlotSnapshot originalNow = context.inventory().slot(candidate.originalSelectedIndex()).orElse(null);
        if (originalNow == null || !originalNow.sameContents(candidate.originalSelectedBefore())) {
            hotbarRestorationCandidate = null;
        }
    }

    private boolean validateContainerRestorationCandidate(
        ExecutionContext context,
        SurvivalItemRoute.ContainerSwap swap
    ) {
        ContainerRestorationCandidate candidate = containerRestorationCandidate;
        if (candidate == null
            || candidate.containerId() != context.menu().containerId()
            || !candidate.route().equals(swap)) {
            containerRestorationCandidate = null;
            return false;
        }
        if (swap.destinationInventoryIndex() >= 0 && swap.destinationInventoryIndex() <= 8
            && context.inventory().selectedHotbarIndex() != swap.destinationInventoryIndex()) {
            containerRestorationCandidate = null;
            return false;
        }
        InventorySlotSnapshot sourceNow = context.inventory().slot(swap.sourceInventoryIndex()).orElse(null);
        if (sourceNow == null || !sourceNow.sameContents(candidate.originalDestinationBefore())) {
            containerRestorationCandidate = null;
            return false;
        }
        return true;
    }

    private void capturePostActionRestoration(ExecutionContext context) {
        HotbarRestorationCandidate hotbar = hotbarRestorationCandidate;
        if (hotbar != null) {
            hotbarRestorationCandidate = null;
            containerRestorationCandidate = null;
            if (context.inventory().selectedHotbarIndex() != hotbar.routedHotbarIndex()) return;
            InventorySlotSnapshot originalNow = context.inventory().slot(hotbar.originalSelectedIndex()).orElse(null);
            InventorySlotSnapshot routedNow = context.inventory().slot(hotbar.routedHotbarIndex()).orElse(null);
            if (originalNow == null || routedNow == null || !originalNow.sameContents(hotbar.originalSelectedBefore())) return;
            restorationCheckpoint = new RestorationCheckpoint.Hotbar(
                hotbar.originalSelectedIndex(),
                hotbar.routedHotbarIndex(),
                hotbar.originalSelectedBefore(),
                routedNow,
                context.currentServerTick()
            );
            return;
        }

        ContainerRestorationCandidate container = containerRestorationCandidate;
        containerRestorationCandidate = null;
        if (container == null) return;
        SurvivalItemRoute.ContainerSwap route = container.route();
        if (context.menu().containerId() != container.containerId()
            || context.menu().menuSlotForInventoryIndex(route.sourceInventoryIndex()).orElse(-1) != route.sourceMenuSlot()) {
            return;
        }
        if (route.destinationInventoryIndex() >= 0 && route.destinationInventoryIndex() <= 8
            && context.inventory().selectedHotbarIndex() != route.destinationInventoryIndex()) {
            return;
        }
        InventorySlotSnapshot sourceAfter = context.inventory().slot(route.sourceInventoryIndex()).orElse(null);
        InventorySlotSnapshot destinationAfter = context.inventory().slot(route.destinationInventoryIndex()).orElse(null);
        if (sourceAfter == null || destinationAfter == null
            || !sourceAfter.sameContents(container.originalDestinationBefore())) {
            return;
        }
        restorationCheckpoint = new RestorationCheckpoint.RoutedContainer(
            container.containerId(),
            route.sourceInventoryIndex(),
            route.sourceMenuSlot(),
            route.destinationInventoryIndex(),
            route.button(),
            container.originalDestinationBefore(),
            sourceAfter,
            destinationAfter,
            context.menu().stateId(),
            context.currentServerTick()
        );
    }

    private static boolean routeObserved(SurvivalItemRoute route, NonTotemExecutionContext context) {
        if (route instanceof SurvivalItemRoute.HotbarSelect hotbar) {
            return context.base().inventory().selectedHotbarIndex() == hotbar.hotbarIndex()
                && exact(context.base().inventory().slot(hotbar.hotbarIndex()).orElse(null), route);
        }
        if (route instanceof SurvivalItemRoute.ContainerSwap swap) {
            return exact(context.base().inventory().slot(swap.destinationInventoryIndex()).orElse(null), route);
        }
        return true;
    }

    private static boolean routeMatchesSource(SurvivalItemRoute route, SurvivalAction.HeldItemRef source) {
        return route.destinationHand() == source.hand()
            && route.itemKey().equals(source.itemKey())
            && route.componentFingerprint() == source.componentFingerprint();
    }

    private static boolean exact(InventorySlotSnapshot slot, SurvivalItemRoute route) {
        return slot != null
            && slot.count() > 0
            && slot.stackKey().equals(route.itemKey())
            && slot.componentFingerprint() == route.componentFingerprint();
    }

    private static int useTicks(SurvivalAction action, SurvivalItemRoute route) {
        return Math.max(0, action.requiredServerTicks() - route.requiredServerTicks());
    }

    private static ExecutionStatus.Failed missingHeldItem(String itemKey) {
        return new ExecutionStatus.Failed("required item is not already in a server-recognized hand: " + itemKey, true);
    }

    private static SurvivalAction.Hand handHolding(InventorySnapshot inventory, String itemKey) {
        var selected = inventory.slot(inventory.selectedHotbarIndex());
        if (selected.isPresent() && selected.get().count() > 0 && selected.get().stackKey().equals(itemKey)) {
            return SurvivalAction.Hand.MAIN_HAND;
        }
        var offhand = inventory.slot(40);
        if (offhand.isPresent() && offhand.get().count() > 0 && offhand.get().stackKey().equals(itemKey)) {
            return SurvivalAction.Hand.OFF_HAND;
        }
        return null;
    }

    private static SurvivalAction.Hand handHolding(
        InventorySnapshot inventory,
        SurvivalAction.HeldItemRef source
    ) {
        int inventoryIndex = source.hand() == SurvivalAction.Hand.MAIN_HAND
            ? inventory.selectedHotbarIndex()
            : 40;
        var slot = inventory.slot(inventoryIndex);
        if (slot.isEmpty() || slot.get().count() <= 0) return null;
        if (!slot.get().stackKey().equals(source.itemKey())) return null;
        if (slot.get().componentFingerprint() != source.componentFingerprint()) return null;
        return source.hand();
    }

    private static String singleEquipmentItem(Map<String, String> equipmentUpdates) {
        if (equipmentUpdates.size() != 1) return null;
        String item = equipmentUpdates.values().iterator().next();
        return item == null || item.isBlank() ? null : item;
    }

    private static boolean equipmentSatisfied(SurvivalAction.SwapEquipment action, PlayerSnapshot player) {
        for (Map.Entry<String, String> update : action.equipmentUpdates().entrySet()) {
            if (!update.getValue().equals(player.equipmentItemKeys().get(update.getKey()))) return false;
        }
        return !action.equipmentUpdates().isEmpty();
    }

    private static boolean effectsSatisfied(
        SurvivalAction.ApplyEffects action,
        PlayerSnapshot expected,
        PlayerSnapshot current,
        long elapsedTicks
    ) {
        if (current.health() + VALUE_EPSILON < expected.health()) return false;
        if (current.absorption() + VALUE_EPSILON < expected.absorption()) return false;
        StatusEffectsSnapshot requiredEffects = action.appliedEffects().isEmpty()
            ? action.statusEffectsAfter()
            : StatusEffectsSnapshot.none().apply(action.appliedEffects());
        return statusEffectsContain(current.statusEffects(), requiredEffects, elapsedTicks);
    }

    private static boolean statusEffectsContain(
        StatusEffectsSnapshot current,
        StatusEffectsSnapshot expected,
        long elapsedTicks
    ) {
        if (expected.fireResistance() && !current.fireResistance()) return false;
        if (expected.resistanceAmplifier() >= 0 && current.resistanceAmplifier() < expected.resistanceAmplifier()) return false;
        for (Map.Entry<String, EffectInstanceSnapshot> entry : expected.effects().entrySet()) {
            EffectInstanceSnapshot actual = current.effects().get(entry.getKey());
            if (actual == null || actual.amplifier() < entry.getValue().amplifier()) return false;
            EffectInstanceSnapshot wanted = entry.getValue();
            if (wanted.infiniteDuration()) {
                if (!actual.infiniteDuration()) return false;
                continue;
            }
            if (!actual.infiniteDuration()) {
                long minimumRemaining = Math.max(1L, (long) wanted.durationTicks() - elapsedTicks);
                if (actual.durationTicks() < minimumRemaining) return false;
            }
        }
        return true;
    }

    private static boolean near(Vec3Snapshot actual, Vec3Snapshot target) {
        double dx = actual.x() - target.x();
        double dy = actual.y() - target.y();
        double dz = actual.z() - target.z();
        return dx * dx + dy * dy + dz * dz <= POSITION_TOLERANCE * POSITION_TOLERANCE;
    }

    private static Pending directPending(
        SurvivalAction action,
        NonTotemExecutionContext context,
        PlayerSnapshot expectedPlayer,
        SurvivalAction.Hand hand
    ) {
        return new Pending(
            action,
            context.base().currentServerTick(),
            context.base().currentServerTick(),
            expectedPlayer,
            hand,
            context.base().timing().nextPacketProcessingWindow().latest(),
            action.requiredServerTicks(),
            Stage.USING,
            null,
            context.base().menu().containerId(),
            context.base().menu().stateId()
        );
    }

    private static Pending usingPending(
        SurvivalAction action,
        NonTotemExecutionContext context,
        PlayerSnapshot expectedPlayer,
        SurvivalAction.Hand hand,
        SurvivalItemRoute route,
        int useRequiredServerTicks
    ) {
        return new Pending(
            action,
            context.base().currentServerTick(),
            context.base().currentServerTick(),
            expectedPlayer,
            hand,
            context.base().timing().nextPacketProcessingWindow().latest(),
            useRequiredServerTicks,
            Stage.USING,
            route,
            context.base().menu().containerId(),
            context.base().menu().stateId()
        );
    }

    private static Pending routingPending(
        SurvivalAction action,
        NonTotemExecutionContext context,
        PlayerSnapshot expectedPlayer,
        SurvivalItemRoute route,
        int useRequiredServerTicks
    ) {
        return new Pending(
            action,
            context.base().currentServerTick(),
            -1L,
            expectedPlayer,
            route.destinationHand(),
            context.base().timing().nextPacketProcessingWindow().latest(),
            useRequiredServerTicks,
            Stage.ROUTING,
            route,
            context.base().menu().containerId(),
            context.base().menu().stateId()
        );
    }

    private static int ticksUntilOrUnknown(long currentTick, long latestEffectTick) {
        if (currentTick > latestEffectTick) return Integer.MAX_VALUE;
        long remaining = latestEffectTick - currentTick;
        return remaining >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
    }

    private enum Stage { ROUTING, USING }

    private record Pending(
        SurvivalAction action,
        long startedAtServerTick,
        long useStartedAtServerTick,
        PlayerSnapshot expectedPlayer,
        SurvivalAction.Hand hand,
        long latestServerStartTick,
        int useRequiredServerTicks,
        Stage stage,
        SurvivalItemRoute route,
        int containerId,
        int containerStateId
    ) {
        private Pending {
            action = Objects.requireNonNull(action, "action");
            expectedPlayer = Objects.requireNonNull(expectedPlayer, "expectedPlayer");
            stage = Objects.requireNonNull(stage, "stage");
            if (useRequiredServerTicks < 0) throw new IllegalArgumentException("useRequiredServerTicks must be non-negative");
        }
    }

    private record HotbarRestorationCandidate(
        int originalSelectedIndex,
        int routedHotbarIndex,
        InventorySlotSnapshot originalSelectedBefore
    ) {
        private HotbarRestorationCandidate {
            if (originalSelectedIndex < 0 || originalSelectedIndex > 8
                || routedHotbarIndex < 0 || routedHotbarIndex > 8) {
                throw new IllegalArgumentException("hotbar indices must be in [0, 8]");
            }
            originalSelectedBefore = Objects.requireNonNull(originalSelectedBefore, "originalSelectedBefore");
        }
    }

    private record ContainerRestorationCandidate(
        int containerId,
        SurvivalItemRoute.ContainerSwap route,
        InventorySlotSnapshot originalDestinationBefore
    ) {
        private ContainerRestorationCandidate {
            if (containerId < 0) throw new IllegalArgumentException("containerId must be non-negative");
            route = Objects.requireNonNull(route, "route");
            originalDestinationBefore = Objects.requireNonNull(originalDestinationBefore, "originalDestinationBefore");
        }
    }
}
