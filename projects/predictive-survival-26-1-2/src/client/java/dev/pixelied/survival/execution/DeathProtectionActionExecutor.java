package dev.pixelied.survival.execution;

import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.DeathProtectionRoutePlanner;
import dev.pixelied.survival.inventory.EmergencyInventoryTransaction;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Objects;
import java.util.Optional;

public final class DeathProtectionActionExecutor implements ActionExecutor<SurvivalAction.EquipDeathProtection> {
    private static final long CONFIRMATION_TIMEOUT_TICKS = 20L;

    private final DeathProtectionRoutePlanner routePlanner;
    private Pending pending;
    private RestorationCheckpoint confirmedRestoration;

    public DeathProtectionActionExecutor() {
        this(new DeathProtectionRoutePlanner());
    }

    DeathProtectionActionExecutor(DeathProtectionRoutePlanner routePlanner) {
        this.routePlanner = Objects.requireNonNull(routePlanner, "routePlanner");
    }

    @Override
    public ExecutionStatus begin(SurvivalAction.EquipDeathProtection action, ExecutionContext context) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");
        pending = null;
        confirmedRestoration = null;

        if (!action.legal() || !action.authoritativePrerequisitesSatisfied()) {
            return new ExecutionStatus.Failed("death-protection action is no longer legal", true);
        }

        DeathProtectionRoute.Destination requestedDestination = action.hand() == SurvivalAction.Hand.OFF_HAND
            ? DeathProtectionRoute.Destination.OFF_HAND
            : DeathProtectionRoute.Destination.MAIN_HAND;
        SurvivalAction.DeathProtectionSourceRef plannedSource = action.sourceItem().orElse(null);
        DeathProtectionRoute route = plannedSource == null
            ? routePlanner.choose(context.inventory(), context.menu(), requestedDestination).orElse(null)
            : plannedSource.route();
        if (route == null) {
            return new ExecutionStatus.Failed("no server-valid death-protection route remains", true);
        }
        if (!routeTargets(route, requestedDestination)) {
            return new ExecutionStatus.Failed("planned death-protection route targets the wrong hand", true);
        }

        if (route instanceof DeathProtectionRoute.AlreadyInHand already) {
            int destinationIndex = already.destination() == DeathProtectionRoute.Destination.OFF_HAND
                ? 40
                : context.inventory().selectedHotbarIndex();
            InventorySlotSnapshot destination = context.inventory().slot(destinationIndex).orElse(null);
            if (plannedSource != null) {
                if (plannedSource.sourceInventoryIndex() != destinationIndex
                    || !exactProtection(destination, plannedSource)) {
                    return new ExecutionStatus.Failed("planned in-hand protection source changed before execution", true);
                }
                return new ExecutionStatus.Confirmed("exact planned death protection already observed in hand");
            }
            if (destination != null && destination.deathProtection()) {
                return new ExecutionStatus.Confirmed("death protection already observed in hand");
            }
            return new ExecutionStatus.Failed("hand state contradicted route selection", true);
        }

        if (route instanceof DeathProtectionRoute.HotbarSelect hotbar) {
            int originalIndex = context.inventory().selectedHotbarIndex();
            InventorySlotSnapshot originalBefore = context.inventory().slot(originalIndex).orElse(null);
            InventorySlotSnapshot protectionBefore = context.inventory().slot(hotbar.hotbarIndex()).orElse(null);
            if (originalBefore == null
                || protectionBefore == null
                || !protectionBefore.deathProtection()
                || (plannedSource != null && !exactProtection(protectionBefore, plannedSource))) {
                return new ExecutionStatus.Failed("planned hotbar protection source changed before selection", true);
            }
            pending = new Pending.Hotbar(
                originalIndex,
                hotbar.hotbarIndex(),
                originalBefore,
                protectionBefore,
                context.currentServerTick(),
                context.timing().nextPacketProcessingWindow().latest()
            );
            return new ExecutionStatus.WaitingForServer(
                "waiting for server-observed held-slot selection",
                new ExecutionCommand.SelectHotbar(hotbar.hotbarIndex())
            );
        }

        DeathProtectionRoute.ContainerSwap swap = (DeathProtectionRoute.ContainerSwap) route;
        int sourceInventoryIndex = plannedSource == null
            ? sourceInventoryIndex(context, swap.sourceMenuSlot())
            : plannedSource.sourceInventoryIndex();
        int destinationInventoryIndex = swap.destination() == DeathProtectionRoute.Destination.OFF_HAND
            ? 40
            : context.inventory().selectedHotbarIndex();
        InventorySlotSnapshot sourceBefore = context.inventory().slot(sourceInventoryIndex).orElse(null);
        InventorySlotSnapshot destinationBefore = context.inventory().slot(destinationInventoryIndex).orElse(null);
        if (context.menu().menuSlotForInventoryIndex(sourceInventoryIndex).orElse(-1) != swap.sourceMenuSlot()) {
            return new ExecutionStatus.Failed("planned protection container mapping changed before swap", true);
        }
        if (sourceBefore == null
            || destinationBefore == null
            || !sourceBefore.deathProtection()
            || (plannedSource != null && !exactProtection(sourceBefore, plannedSource))) {
            return new ExecutionStatus.Failed("planned container protection source changed before swap", true);
        }

        EmergencyInventoryTransaction transaction = EmergencyInventoryTransaction.planned(
            swap,
            context.menu().containerId(),
            context.menu().stateId(),
            sourceBefore,
            destinationBefore,
            context.currentServerTick(),
            saturatingAdd(context.currentServerTick(), CONFIRMATION_TIMEOUT_TICKS)
        ).markSent();
        ContainerPredictionAuthority authority = new ContainerPredictionAuthority(
            context.menu().containerId(),
            context.menu().stateId(),
            sourceInventoryIndex,
            destinationBefore,
            destinationInventoryIndex,
            sourceBefore,
            context.serverStateEvidence().revision(),
            context.timing().containerPredictionSettleTick()
        );
        pending = new Pending.ContainerSwap(
            transaction,
            sourceInventoryIndex,
            destinationInventoryIndex,
            context.currentServerTick(),
            context.timing().nextPacketProcessingWindow().latest(),
            authority
        );
        return new ExecutionStatus.WaitingForServer(
            "waiting for server reconciliation of optimistic Totem swap",
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
            return new ExecutionStatus.Failed("no death-protection action is pending", true);
        }
        if (context.currentServerTick() - pending.startedAtServerTick() > CONFIRMATION_TIMEOUT_TICKS) {
            pending = null;
            return new ExecutionStatus.Failed("server confirmation timed out", true);
        }

        if (pending instanceof Pending.Hotbar hotbar) {
            InventorySlotSnapshot currentProtection = context.inventory().slot(hotbar.protectionHotbarIndex()).orElse(null);
            if (currentProtection == null
                || !currentProtection.deathProtection()
                || !currentProtection.sameContents(hotbar.protectionBefore())) {
                pending = null;
                return new ExecutionStatus.Failed("exact planned death-protection stack changed before held-slot confirmation", true);
            }
            if (context.inventory().selectedHotbarIndex() == hotbar.protectionHotbarIndex()) {
                InventorySlotSnapshot currentOriginal = context.inventory().slot(hotbar.originalSelectedIndex()).orElse(null);
                if (currentOriginal != null
                    && currentOriginal.sameContents(hotbar.originalSelectedBefore())) {
                    confirmedRestoration = new RestorationCheckpoint.Hotbar(
                        hotbar.originalSelectedIndex(),
                        hotbar.protectionHotbarIndex(),
                        hotbar.originalSelectedBefore(),
                        currentProtection,
                        context.currentServerTick()
                    );
                }
                pending = null;
                return new ExecutionStatus.Confirmed("server-observed held slot now contains exact planned death protection");
            }
            return new ExecutionStatus.WaitingForServer("waiting for held-slot confirmation");
        }

        Pending.ContainerSwap swap = (Pending.ContainerSwap) pending;
        EmergencyInventoryTransaction transaction = swap.transaction();
        ContainerPredictionAuthority.Verdict verdict = swap.authority().evaluate(context);
        if (verdict == ContainerPredictionAuthority.Verdict.WAITING) {
            return new ExecutionStatus.WaitingForServer("waiting for Totem swap correction window to settle");
        }
        if (verdict == ContainerPredictionAuthority.Verdict.CONTRADICTED) {
            pending = null;
            return new ExecutionStatus.Failed("server state contradicted the exact planned Totem swap", true);
        }

        InventorySlotSnapshot source = context.inventory().slot(swap.sourceInventoryIndex()).orElse(null);
        InventorySlotSnapshot destination = context.inventory().slot(swap.destinationInventoryIndex()).orElse(null);
        if (source == null || destination == null) {
            pending = null;
            return new ExecutionStatus.Failed("inventory slots disappeared during Totem reconciliation", true);
        }

        transaction = transaction.reconcile(source, destination);
        pending = null;
        if (transaction.state() == EmergencyInventoryTransaction.State.CONFIRMED) {
            confirmedRestoration = new RestorationCheckpoint.Container(
                transaction,
                swap.sourceInventoryIndex(),
                swap.destinationInventoryIndex(),
                context.menu().stateId(),
                context.currentServerTick()
            );
            return new ExecutionStatus.Confirmed("Totem swap accepted after exact server reconciliation");
        }
        return new ExecutionStatus.Failed("server state contradicted the exact planned Totem swap", true);
    }

    public int remainingServerTicks(ExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (pending == null) return Integer.MAX_VALUE;

        if (pending instanceof Pending.Hotbar hotbar) {
            InventorySlotSnapshot current = context.inventory().slot(hotbar.protectionHotbarIndex()).orElse(null);
            if (current != null
                && current.deathProtection()
                && current.sameContents(hotbar.protectionBefore())
                && context.inventory().selectedHotbarIndex() == hotbar.protectionHotbarIndex()) {
                return 0;
            }
            return ticksUntilOrUnknown(context.currentServerTick(), hotbar.latestServerEffectTick());
        }

        Pending.ContainerSwap swap = (Pending.ContainerSwap) pending;
        ContainerPredictionAuthority.Verdict verdict = swap.authority().evaluate(context);
        if (verdict == ContainerPredictionAuthority.Verdict.ACCEPTED) return 0;
        if (verdict == ContainerPredictionAuthority.Verdict.CONTRADICTED) return Integer.MAX_VALUE;
        return ticksUntilOrUnknown(context.currentServerTick(), swap.authority().settleAtServerTick());
    }

    public void reset() {
        pending = null;
        confirmedRestoration = null;
    }

    public Optional<RestorationCheckpoint> takeRestorationCheckpoint() {
        RestorationCheckpoint result = confirmedRestoration;
        confirmedRestoration = null;
        return Optional.ofNullable(result);
    }

    private static boolean routeTargets(
        DeathProtectionRoute route,
        DeathProtectionRoute.Destination destination
    ) {
        if (route instanceof DeathProtectionRoute.HotbarSelect) {
            return destination == DeathProtectionRoute.Destination.MAIN_HAND;
        }
        if (route instanceof DeathProtectionRoute.AlreadyInHand already) {
            return already.destination() == destination;
        }
        return ((DeathProtectionRoute.ContainerSwap) route).destination() == destination;
    }

    private static boolean exactProtection(
        InventorySlotSnapshot slot,
        SurvivalAction.DeathProtectionSourceRef source
    ) {
        return slot != null
            && slot.count() > 0
            && slot.deathProtection()
            && slot.stackKey().equals(source.itemKey())
            && slot.componentFingerprint() == source.componentFingerprint();
    }

    private static int sourceInventoryIndex(ExecutionContext context, int sourceMenuSlot) {
        return context.menu().inventoryIndexToMenuSlot().entrySet().stream()
            .filter(entry -> entry.getValue() == sourceMenuSlot)
            .mapToInt(java.util.Map.Entry::getKey)
            .findFirst()
            .orElse(-1);
    }

    private static long saturatingAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private static int ticksUntilOrUnknown(long currentTick, long latestEffectTick) {
        if (currentTick > latestEffectTick) return Integer.MAX_VALUE;
        long remaining = latestEffectTick - currentTick;
        return remaining >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
    }

    private sealed interface Pending {
        long startedAtServerTick();

        record Hotbar(
            int originalSelectedIndex,
            int protectionHotbarIndex,
            InventorySlotSnapshot originalSelectedBefore,
            InventorySlotSnapshot protectionBefore,
            long startedAtServerTick,
            long latestServerEffectTick
        ) implements Pending {
        }

        record ContainerSwap(
            EmergencyInventoryTransaction transaction,
            int sourceInventoryIndex,
            int destinationInventoryIndex,
            long startedAtServerTick,
            long latestServerEffectTick,
            ContainerPredictionAuthority authority
        ) implements Pending {
            public ContainerSwap {
                authority = Objects.requireNonNull(authority, "authority");
            }
        }
    }
}
