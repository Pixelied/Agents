package dev.pixelied.survival.execution;

import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Objects;

public final class ShieldActionExecutor implements ActionExecutor<SurvivalAction.RaiseShield> {
    private static final long CONFIRMATION_TIMEOUT_TICKS = 20L;

    private Pending pending;

    @Override
    public ExecutionStatus begin(SurvivalAction.RaiseShield action, ExecutionContext context) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");
        pending = null;

        if (!action.legal() || !action.authoritativePrerequisitesSatisfied()) {
            return new ExecutionStatus.Failed("shield action is no longer legal", true);
        }
        if (!action.guaranteedBlock()) {
            return new ExecutionStatus.Failed("shield block is not guaranteed", true);
        }
        if (!context.shieldAngleValid()) {
            return new ExecutionStatus.Failed("shield angle is no longer valid", true);
        }

        SurvivalAction.Hand hand = context.inventory().activeOffhandShield()
            ? SurvivalAction.Hand.OFF_HAND
            : SurvivalAction.Hand.MAIN_HAND;
        pending = new Pending(action, hand, context.currentServerTick());
        return statusForObservation(context, true);
    }

    @Override
    public ExecutionStatus observe(ExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (pending == null) {
            return new ExecutionStatus.Failed("no shield action is pending", true);
        }
        if (context.currentServerTick() - pending.startedAtServerTick() > CONFIRMATION_TIMEOUT_TICKS) {
            pending = null;
            return new ExecutionStatus.Failed("shield server confirmation timed out", true);
        }
        return statusForObservation(context, false);
    }

    private ExecutionStatus statusForObservation(ExecutionContext context, boolean mayEmitUseCommand) {
        if (!context.shieldAngleValid()) {
            pending = null;
            return new ExecutionStatus.Failed("incoming threat moved outside the guaranteed block angle", true);
        }

        if (context.serverUsingItem()) {
            if (context.usingHand() != pending.hand()) {
                pending = null;
                return new ExecutionStatus.Failed("server is using a different hand than the planned shield", true);
            }
            if (context.serverUseTicks() >= pending.action().requiredUseTicks()) {
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
        return new ExecutionStatus.WaitingForServer("waiting for server-observed shield use state");
    }

    private record Pending(
        SurvivalAction.RaiseShield action,
        SurvivalAction.Hand hand,
        long startedAtServerTick
    ) {
    }
}
