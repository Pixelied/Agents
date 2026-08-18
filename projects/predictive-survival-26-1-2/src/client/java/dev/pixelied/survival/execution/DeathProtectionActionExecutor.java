package dev.pixelied.survival.execution;

import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.DeathProtectionRoutePlanner;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Objects;

public final class DeathProtectionActionExecutor implements ActionExecutor<SurvivalAction.EquipDeathProtection> {
    private static final long CONFIRMATION_TIMEOUT_TICKS = 20L;

    private final DeathProtectionRoutePlanner routePlanner;
    private Pending pending;

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

        if (!action.legal() || !action.authoritativePrerequisitesSatisfied()) {
            return new ExecutionStatus.Failed("death-protection action is no longer legal", true);
        }

        DeathProtectionRoute route = routePlanner.choose(context.inventory(), context.menu()).orElse(null);
        if (route == null) {
            return new ExecutionStatus.Failed("no server-valid death-protection route remains", true);
        }

        if (route instanceof DeathProtectionRoute.AlreadyInHand already) {
            if (destinationHasProtection(context, already.destination(), context.inventory().selectedHotbarIndex())) {
                return new ExecutionStatus.Confirmed("death protection already observed in hand");
            }
            return new ExecutionStatus.Failed("hand state contradicted route selection", true);
        }

        if (route instanceof DeathProtectionRoute.HotbarSelect hotbar) {
            pending = new Pending.Hotbar(hotbar.hotbarIndex(), context.currentServerTick());
            return new ExecutionStatus.WaitingForServer(
                "waiting for server-observed held-slot selection",
                new ExecutionCommand.SelectHotbar(hotbar.hotbarIndex())
            );
        }

        DeathProtectionRoute.ContainerSwap swap = (DeathProtectionRoute.ContainerSwap) route;
        pending = new Pending.ContainerSwap(
            context.menu().containerId(),
            context.menu().stateId(),
            swap.sourceMenuSlot(),
            swap.button(),
            swap.destination(),
            context.inventory().selectedHotbarIndex(),
            context.currentServerTick()
        );
        return new ExecutionStatus.WaitingForServer(
            "waiting for server-observed container revision and destination contents",
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
            var slot = context.inventory().slot(hotbar.hotbarIndex());
            if (slot.isEmpty() || !slot.get().deathProtection()) {
                pending = null;
                return new ExecutionStatus.Failed("death-protection item left the requested hotbar slot", true);
            }
            if (context.inventory().selectedHotbarIndex() == hotbar.hotbarIndex()) {
                pending = null;
                return new ExecutionStatus.Confirmed("server-observed held slot now contains death protection");
            }
            return new ExecutionStatus.WaitingForServer("waiting for held-slot confirmation");
        }

        Pending.ContainerSwap swap = (Pending.ContainerSwap) pending;
        if (context.menu().containerId() != swap.containerId()) {
            pending = null;
            return new ExecutionStatus.Failed("container changed before swap confirmation", true);
        }

        int destinationIndex = swap.destination() == DeathProtectionRoute.Destination.OFF_HAND
            ? 40
            : swap.destinationHotbarIndex();
        boolean destinationProtected = context.inventory().slot(destinationIndex)
            .map(slot -> slot.deathProtection())
            .orElse(false);
        boolean revisionAdvanced = context.menu().stateId() != swap.initialStateId();

        if (destinationProtected && revisionAdvanced) {
            pending = null;
            return new ExecutionStatus.Confirmed("container revision and destination contents confirmed by server state");
        }
        if (revisionAdvanced && !destinationProtected) {
            pending = null;
            return new ExecutionStatus.Failed("server revised container without placing death protection in destination", true);
        }
        return new ExecutionStatus.WaitingForServer("waiting for authoritative container revision");
    }

    private static boolean destinationHasProtection(
        ExecutionContext context,
        DeathProtectionRoute.Destination destination,
        int selectedHotbarIndex
    ) {
        int inventoryIndex = destination == DeathProtectionRoute.Destination.OFF_HAND ? 40 : selectedHotbarIndex;
        return context.inventory().slot(inventoryIndex)
            .map(slot -> slot.deathProtection())
            .orElse(false);
    }

    private sealed interface Pending {
        long startedAtServerTick();

        record Hotbar(int hotbarIndex, long startedAtServerTick) implements Pending {
        }

        record ContainerSwap(
            int containerId,
            int initialStateId,
            int sourceMenuSlot,
            int button,
            DeathProtectionRoute.Destination destination,
            int destinationHotbarIndex,
            long startedAtServerTick
        ) implements Pending {
        }
    }
}
