package dev.pixelied.survival.execution;

import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Objects;
import java.util.Optional;

public final class ShieldActionExecutor implements ActionExecutor<SurvivalAction.RaiseShield> {
    private static final long CONFIRMATION_TIMEOUT_TICKS = 20L;

    private Pending pending;
    private RestorationCheckpoint restorationCheckpoint;
    private ContainerRestorationCandidate containerRestorationCandidate;

    @Override
    public ExecutionStatus begin(SurvivalAction.RaiseShield action, ExecutionContext context) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");
        pending = null;
        containerRestorationCandidate = null;

        if (!action.legal() || !action.authoritativePrerequisitesSatisfied()) {
            return new ExecutionStatus.Failed("shield action is no longer legal", true);
        }
        if (!action.guaranteedBlock()) {
            return new ExecutionStatus.Failed("shield block is not guaranteed", true);
        }

        SurvivalAction.HeldItemRef source = action.sourceItem().orElse(null);
        SurvivalItemRoute route = source == null ? null : source.route().orElse(null);
        if (route == null) {
            SurvivalAction.Hand hand = source == null
                ? (context.inventory().activeOffhandShield() ? SurvivalAction.Hand.OFF_HAND : SurvivalAction.Hand.MAIN_HAND)
                : handHolding(context.inventory(), source);
            if (hand == null) {
                return new ExecutionStatus.Failed("planned shield is no longer in the exact server-recognized hand", true);
            }
            pending = usingPending(action, context, hand, null, action.requiredUseTicks());
            return statusForUsing(context, true);
        }

        if (!routeMatchesSource(route, source)) {
            return new ExecutionStatus.Failed("routed shield identity changed before execution", true);
        }

        if (route instanceof SurvivalItemRoute.AlreadyHeld) {
            if (handHolding(context.inventory(), source) == null) {
                return new ExecutionStatus.Failed("planned shield is no longer in the exact server-recognized hand", true);
            }
            pending = usingPending(action, context, route.destinationHand(), route, action.requiredUseTicks());
            return statusForUsing(context, true);
        }

        if (route instanceof SurvivalItemRoute.HotbarSelect hotbar) {
            InventorySlotSnapshot sourceSlot = context.inventory().slot(hotbar.hotbarIndex()).orElse(null);
            if (!exact(sourceSlot, route)) {
                return new ExecutionStatus.Failed("routed hotbar shield changed before selection", true);
            }
            if (context.inventory().selectedHotbarIndex() == hotbar.hotbarIndex()) {
                pending = usingPending(action, context, route.destinationHand(), route, action.requiredUseTicks());
                return statusForUsing(context, true);
            }
            pending = routingPending(action, context, route, action.requiredUseTicks());
            return new ExecutionStatus.WaitingForServer(
                "waiting for exact shield to become selected",
                new ExecutionCommand.SelectHotbar(hotbar.hotbarIndex())
            );
        }

        SurvivalItemRoute.ContainerSwap swap = (SurvivalItemRoute.ContainerSwap) route;
        InventorySlotSnapshot sourceSlot = context.inventory().slot(swap.sourceInventoryIndex()).orElse(null);
        InventorySlotSnapshot destinationBefore = context.inventory().slot(swap.destinationInventoryIndex()).orElse(null);
        if (!exact(sourceSlot, route)) {
            return new ExecutionStatus.Failed("routed container shield changed before swap", true);
        }
        if (destinationBefore == null) {
            return new ExecutionStatus.Failed("shield route destination disappeared before swap", true);
        }
        if (context.menu().menuSlotForInventoryIndex(swap.sourceInventoryIndex()).orElse(-1) != swap.sourceMenuSlot()) {
            return new ExecutionStatus.Failed("routed shield container mapping changed before swap", true);
        }
        ContainerPredictionAuthority authority = new ContainerPredictionAuthority(
            context.menu().containerId(),
            context.menu().stateId(),
            swap.sourceInventoryIndex(),
            destinationBefore,
            swap.destinationInventoryIndex(),
            sourceSlot,
            context.serverStateEvidence().revision(),
            context.timing().containerPredictionSettleTick()
        );
        containerRestorationCandidate = new ContainerRestorationCandidate(
            context.menu().containerId(),
            swap,
            sourceSlot,
            destinationBefore,
            authority
        );
        pending = routingPending(action, context, route, action.requiredUseTicks());
        return new ExecutionStatus.WaitingForServer(
            "waiting for exact shield container swap",
            new ExecutionCommand.SwapMenuSlot(
                context.menu().containerId(),
                context.menu().stateId(),
                swap.sourceMenuSlot(),
                swap.button()
            )
        );
    }

    @Override
    public ExecutionStatus observe(ExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (pending == null) {
            return new ExecutionStatus.Failed("no shield action is pending", true);
        }
        if (context.currentServerTick() - pending.startedAtServerTick() > CONFIRMATION_TIMEOUT_TICKS) {
            pending = null;
            containerRestorationCandidate = null;
            return new ExecutionStatus.Failed("shield server confirmation timed out", true);
        }

        if (pending.stage() == Stage.ROUTING) {
            ExecutionStatus routeStatus = observeRoute(context);
            if (routeStatus != null) return routeStatus;
        }
        return statusForUsing(context, false);
    }

    public int remainingServerTicks(ExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (pending == null) return Integer.MAX_VALUE;

        long currentTick = context.currentServerTick();
        if (pending.stage() == Stage.ROUTING) {
            if (pending.route() instanceof SurvivalItemRoute.ContainerSwap swap) {
                ContainerPredictionAuthority.Verdict verdict = containerRouteVerdict(swap, context);
                if (verdict == ContainerPredictionAuthority.Verdict.ACCEPTED) {
                    return pending.useRequiredServerTicks();
                }
                if (verdict == ContainerPredictionAuthority.Verdict.CONTRADICTED) {
                    return Integer.MAX_VALUE;
                }
                ContainerRestorationCandidate candidate = containerRestorationCandidate;
                if (candidate == null) return Integer.MAX_VALUE;
                int waiting = ticksUntilOrUnknown(currentTick, candidate.authority().settleAtServerTick());
                if (waiting == Integer.MAX_VALUE) return Integer.MAX_VALUE;
                long total = (long) waiting + pending.useRequiredServerTicks();
                return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
            }
            if (routeAuthoritativelyObserved(pending.route(), context)) return pending.useRequiredServerTicks();
            if (currentTick <= pending.latestServerStartTick()) {
                long total = pending.latestServerStartTick() - currentTick + pending.useRequiredServerTicks();
                return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
            }
            return Integer.MAX_VALUE;
        }

        if (context.serverUsingItem()) {
            if (context.usingHand() != pending.hand()) return Integer.MAX_VALUE;
            return Math.max(0, pending.useRequiredServerTicks() - context.serverUseTicks());
        }

        if (currentTick <= pending.latestServerStartTick()) {
            long waitForStart = pending.latestServerStartTick() - currentTick;
            long total = waitForStart + pending.useRequiredServerTicks();
            return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
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
        containerRestorationCandidate = null;
    }

    /** Returns null only when routing finished and normal shield observation can continue immediately. */
    private ExecutionStatus observeRoute(ExecutionContext context) {
        SurvivalItemRoute route = pending.route();
        if (route == null) {
            pending = null;
            containerRestorationCandidate = null;
            return new ExecutionStatus.Failed("routed shield lost its route", true);
        }

        if (route instanceof SurvivalItemRoute.HotbarSelect hotbar) {
            InventorySlotSnapshot slot = context.inventory().slot(hotbar.hotbarIndex()).orElse(null);
            if (!exact(slot, route)) {
                pending = null;
                return new ExecutionStatus.Failed("selected shield no longer matches exact planned components", true);
            }
            if (context.inventory().selectedHotbarIndex() != hotbar.hotbarIndex()) {
                return new ExecutionStatus.WaitingForServer("waiting for exact shield hotbar selection");
            }
            captureHotbarRestoration(pending, hotbar, context);
        } else if (route instanceof SurvivalItemRoute.ContainerSwap swap) {
            ContainerPredictionAuthority.Verdict verdict = containerRouteVerdict(swap, context);
            if (verdict == ContainerPredictionAuthority.Verdict.WAITING) {
                return new ExecutionStatus.WaitingForServer("waiting for shield swap correction window to settle");
            }
            if (verdict == ContainerPredictionAuthority.Verdict.CONTRADICTED) {
                pending = null;
                containerRestorationCandidate = null;
                return new ExecutionStatus.Failed("server state contradicted the exact planned shield swap", true);
            }
            InventorySlotSnapshot destination = context.inventory().slot(swap.destinationInventoryIndex()).orElse(null);
            if (destination == null || !captureContainerRestoration(swap, destination, context)) {
                pending = null;
                containerRestorationCandidate = null;
                return new ExecutionStatus.Failed("confirmed shield route could not preserve displaced destination state", true);
            }
        } else {
            pending = null;
            containerRestorationCandidate = null;
            return new ExecutionStatus.Failed("unexpected shield route stage", true);
        }

        Pending routed = pending;
        pending = new Pending(
            routed.action(),
            routed.hand(),
            routed.startedAtServerTick(),
            context.timing().nextPacketProcessingWindow().latest(),
            routed.useRequiredServerTicks(),
            Stage.USING,
            routed.route(),
            routed.containerId(),
            routed.containerStateId(),
            routed.originalSelectedIndex(),
            routed.originalSelectedBefore()
        );
        return statusForUsing(context, true);
    }

    private void captureHotbarRestoration(
        Pending routed,
        SurvivalItemRoute.HotbarSelect hotbar,
        ExecutionContext context
    ) {
        InventorySlotSnapshot originalBefore = routed.originalSelectedBefore();
        if (originalBefore == null || routed.originalSelectedIndex() == hotbar.hotbarIndex()) return;
        InventorySlotSnapshot originalNow = context.inventory().slot(routed.originalSelectedIndex()).orElse(null);
        InventorySlotSnapshot routedNow = context.inventory().slot(hotbar.hotbarIndex()).orElse(null);
        if (originalNow == null || routedNow == null || !originalNow.sameContents(originalBefore)) return;
        restorationCheckpoint = new RestorationCheckpoint.Hotbar(
            routed.originalSelectedIndex(),
            hotbar.hotbarIndex(),
            originalBefore,
            routedNow,
            context.currentServerTick()
        );
    }

    private ContainerPredictionAuthority.Verdict containerRouteVerdict(
        SurvivalItemRoute.ContainerSwap swap,
        ExecutionContext context
    ) {
        ContainerRestorationCandidate candidate = containerRestorationCandidate;
        if (candidate == null
            || candidate.containerId() != context.menu().containerId()
            || !candidate.route().equals(swap)) {
            return ContainerPredictionAuthority.Verdict.CONTRADICTED;
        }
        if (swap.destinationInventoryIndex() >= 0 && swap.destinationInventoryIndex() <= 8
            && context.inventory().selectedHotbarIndex() != swap.destinationInventoryIndex()) {
            return ContainerPredictionAuthority.Verdict.CONTRADICTED;
        }
        return candidate.authority().evaluate(context);
    }

    private boolean captureContainerRestoration(
        SurvivalItemRoute.ContainerSwap swap,
        InventorySlotSnapshot destinationAfter,
        ExecutionContext context
    ) {
        ContainerRestorationCandidate candidate = containerRestorationCandidate;
        containerRestorationCandidate = null;
        if (candidate == null
            || candidate.containerId() != context.menu().containerId()
            || !candidate.route().equals(swap)) {
            return false;
        }
        InventorySlotSnapshot sourceAfter = context.inventory().slot(swap.sourceInventoryIndex()).orElse(null);
        if (sourceAfter == null || !sourceAfter.sameContents(candidate.originalDestinationBefore())) return false;
        restorationCheckpoint = new RestorationCheckpoint.RoutedContainer(
            candidate.containerId(),
            swap.sourceInventoryIndex(),
            swap.sourceMenuSlot(),
            swap.destinationInventoryIndex(),
            swap.button(),
            candidate.originalDestinationBefore(),
            sourceAfter,
            destinationAfter,
            context.menu().stateId(),
            context.currentServerTick()
        );
        return true;
    }

    private ExecutionStatus statusForUsing(ExecutionContext context, boolean mayEmitUseCommand) {
        if (context.serverUsingItem()) {
            if (context.usingHand() != pending.hand()) {
                pending = null;
                return new ExecutionStatus.Failed("server is using a different hand than the planned shield", true);
            }
            if (context.serverUseTicks() >= pending.useRequiredServerTicks()) {
                pending = null;
                return new ExecutionStatus.Confirmed("server-observed shield warmup reached required use time");
            }
            return new ExecutionStatus.WaitingForServer("waiting for server-observed shield warmup");
        }

        if (mayEmitUseCommand) {
            return new ExecutionStatus.WaitingForServer(
                "waiting for server to accept shield use",
                new ExecutionCommand.UseItem(pending.hand())
            );
        }
        if (context.currentServerTick() > pending.latestServerStartTick()) {
            pending = null;
            return new ExecutionStatus.Failed("server did not begin the planned shield use in time", true);
        }
        return new ExecutionStatus.WaitingForServer("waiting for server-observed shield use state");
    }

    private static Pending usingPending(
        SurvivalAction.RaiseShield action,
        ExecutionContext context,
        SurvivalAction.Hand hand,
        SurvivalItemRoute route,
        int useRequiredServerTicks
    ) {
        return new Pending(
            action,
            hand,
            context.currentServerTick(),
            context.timing().nextPacketProcessingWindow().latest(),
            useRequiredServerTicks,
            Stage.USING,
            route,
            context.menu().containerId(),
            context.menu().stateId(),
            -1,
            null
        );
    }

    private static Pending routingPending(
        SurvivalAction.RaiseShield action,
        ExecutionContext context,
        SurvivalItemRoute route,
        int useRequiredServerTicks
    ) {
        int originalIndex = context.inventory().selectedHotbarIndex();
        InventorySlotSnapshot originalBefore = route instanceof SurvivalItemRoute.HotbarSelect
            ? context.inventory().slot(originalIndex).orElse(null)
            : null;
        return new Pending(
            action,
            route.destinationHand(),
            context.currentServerTick(),
            context.timing().nextPacketProcessingWindow().latest(),
            useRequiredServerTicks,
            Stage.ROUTING,
            route,
            context.menu().containerId(),
            context.menu().stateId(),
            originalIndex,
            originalBefore
        );
    }

    private boolean routeAuthoritativelyObserved(SurvivalItemRoute route, ExecutionContext context) {
        if (route instanceof SurvivalItemRoute.HotbarSelect hotbar) {
            return context.inventory().selectedHotbarIndex() == hotbar.hotbarIndex()
                && exact(context.inventory().slot(hotbar.hotbarIndex()).orElse(null), route);
        }
        return !(route instanceof SurvivalItemRoute.ContainerSwap);
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

    private static SurvivalAction.Hand handHolding(InventorySnapshot inventory, SurvivalAction.HeldItemRef source) {
        int inventoryIndex = source.hand() == SurvivalAction.Hand.MAIN_HAND
            ? inventory.selectedHotbarIndex()
            : 40;
        InventorySlotSnapshot slot = inventory.slot(inventoryIndex).orElse(null);
        if (slot == null || slot.count() <= 0) return null;
        if (!slot.stackKey().equals(source.itemKey())) return null;
        if (slot.componentFingerprint() != source.componentFingerprint()) return null;
        return source.hand();
    }

    private static int ticksUntilOrUnknown(long currentTick, long latestEffectTick) {
        if (currentTick > latestEffectTick) return Integer.MAX_VALUE;
        long remaining = latestEffectTick - currentTick;
        return remaining >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
    }

    private enum Stage { ROUTING, USING }

    private record Pending(
        SurvivalAction.RaiseShield action,
        SurvivalAction.Hand hand,
        long startedAtServerTick,
        long latestServerStartTick,
        int useRequiredServerTicks,
        Stage stage,
        SurvivalItemRoute route,
        int containerId,
        int containerStateId,
        int originalSelectedIndex,
        InventorySlotSnapshot originalSelectedBefore
    ) {
        private Pending {
            action = Objects.requireNonNull(action, "action");
            hand = Objects.requireNonNull(hand, "hand");
            stage = Objects.requireNonNull(stage, "stage");
            if (useRequiredServerTicks < 0) {
                throw new IllegalArgumentException("useRequiredServerTicks must be non-negative");
            }
        }
    }

    private record ContainerRestorationCandidate(
        int containerId,
        SurvivalItemRoute.ContainerSwap route,
        InventorySlotSnapshot sourceBefore,
        InventorySlotSnapshot originalDestinationBefore,
        ContainerPredictionAuthority authority
    ) {
        private ContainerRestorationCandidate {
            if (containerId < 0) throw new IllegalArgumentException("containerId must be non-negative");
            route = Objects.requireNonNull(route, "route");
            sourceBefore = Objects.requireNonNull(sourceBefore, "sourceBefore");
            originalDestinationBefore = Objects.requireNonNull(originalDestinationBefore, "originalDestinationBefore");
            authority = Objects.requireNonNull(authority, "authority");
        }
    }
}
