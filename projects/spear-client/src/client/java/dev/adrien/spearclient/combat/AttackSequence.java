package dev.adrien.spearclient.combat;

import dev.adrien.spearclient.network.MovementPath;
import java.util.Objects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record AttackSequence(
    long sequenceId,
    Kind kind,
    SpearContext context,
    MovementPath movementPath,
    boolean sendStab,
    int attackMovementIndex,
    double expectedForwardKnownMovement,
    int maxMovementPackets,
    int timeoutTicks,
    RotationPlan rotationPlan,
    boolean preRotateForOneServerTick
) {
    public static final int HARD_MAX_MOVEMENT_PACKETS = 8;

    public AttackSequence(
        long sequenceId,
        Kind kind,
        SpearContext context,
        MovementPath movementPath,
        boolean sendStab,
        int attackMovementIndex,
        double expectedForwardKnownMovement,
        int maxMovementPackets,
        int timeoutTicks
    ) {
        this(
            sequenceId,
            kind,
            context,
            movementPath,
            sendStab,
            attackMovementIndex,
            expectedForwardKnownMovement,
            maxMovementPackets,
            timeoutTicks,
            null,
            false
        );
    }

    public AttackSequence {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(movementPath, "movementPath");
        if (maxMovementPackets < 0 || maxMovementPackets > HARD_MAX_MOVEMENT_PACKETS) {
            throw new IllegalArgumentException("maxMovementPackets must be between 0 and 8");
        }
        if (movementPath.positions().size() > maxMovementPackets) {
            throw new IllegalArgumentException("movement path exceeds sequence packet budget");
        }
        if (timeoutTicks <= 0) {
            throw new IllegalArgumentException("timeoutTicks must be positive");
        }
        if (!Double.isFinite(expectedForwardKnownMovement) || expectedForwardKnownMovement < 0.0) {
            throw new IllegalArgumentException("expectedForwardKnownMovement must be finite and non-negative");
        }
        if (sendStab) {
            if (attackMovementIndex < 0 || attackMovementIndex >= movementPath.positions().size()) {
                throw new IllegalArgumentException("STAB movement index must point at a path position");
            }
        } else if (attackMovementIndex != -1) {
            throw new IllegalArgumentException("non-STAB sequences use attackMovementIndex = -1");
        }
        if (preRotateForOneServerTick && rotationPlan == null) {
            throw new IllegalArgumentException("pre-rotation requires a rotation plan");
        }
        if (!preRotateForOneServerTick && rotationPlan != null) {
            throw new IllegalArgumentException("rotation plan requires pre-rotation staging");
        }
        if (rotationPlan != null
            && (!Float.isFinite(rotationPlan.yaw()) || !Float.isFinite(rotationPlan.pitch()))) {
            throw new IllegalArgumentException("rotation plan must be finite");
        }
    }

    public boolean sendStabAtMovementIndex(int movementIndex) {
        return sendStab && attackMovementIndex == movementIndex;
    }

    public boolean targetMissing(ClientLevel level) {
        int targetId = context.targetId();
        if (targetId < 0) {
            return false;
        }
        Entity target = level.getEntity(targetId);
        return target == null || !target.isAlive();
    }

    public boolean stillHasRequiredSpear(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.get(DataComponents.PIERCING_WEAPON) == null) {
            return false;
        }
        if (!ItemStack.isSameItem(held, context.spear())) {
            return false;
        }
        return context.kinetic() == null || held.get(DataComponents.KINETIC_WEAPON) != null;
    }

    public enum Phase {
        PREPARE,
        STAGE,
        ROTATE,
        ATTACK,
        RETURN,
        RESTORE,
        VERIFY,
        CLEANUP,
        DONE,
        FAILED
    }

    public enum Kind {
        ONE_TAP,
        REACH,
        LUNGE
    }
}
