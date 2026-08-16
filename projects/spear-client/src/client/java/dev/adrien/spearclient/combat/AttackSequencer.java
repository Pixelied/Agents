package dev.adrien.spearclient.combat;

import dev.adrien.spearclient.network.PacketSender;
import dev.adrien.spearclient.network.ServerStateTracker;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class AttackSequencer {
    private static final int MAX_PHASE_TRANSITIONS_PER_TICK = 8;

    private final PacketSender packets;
    private final ServerStateTracker tracker;

    private AttackSequence active;
    private AttackSequence.Phase phase = AttackSequence.Phase.DONE;
    private int ticksAlive;
    private int movementPacketsSent;
    private int postStabDelayTicks;
    private boolean serverRotationStaged;

    public AttackSequencer(PacketSender packets, ServerStateTracker tracker) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    public boolean tryStart(AttackSequence sequence) {
        Objects.requireNonNull(sequence, "sequence");
        if (active != null) {
            return false;
        }

        active = sequence;
        phase = AttackSequence.Phase.PREPARE;
        ticksAlive = 0;
        movementPacketsSent = 0;
        postStabDelayTicks = 0;
        serverRotationStaged = false;
        tracker.beginSequence(sequence.sequenceId(), sequence.context().origin());
        tracker.setTargetId(sequence.context().targetId());
        tracker.setPhase(phase.name());
        return true;
    }

    public boolean tryStartAfterStab(AttackSequence sequence) {
        Objects.requireNonNull(sequence, "sequence");
        if (sequence.kind() != AttackSequence.Kind.LUNGE || active != null) {
            return false;
        }
        if (!tryStart(sequence)) {
            return false;
        }
        packets.stab();
        postStabDelayTicks = 1;
        return true;
    }

    public void tick(Minecraft client) {
        if (active == null) {
            return;
        }

        ticksAlive++;
        if (tracker.snapshot().corrected()) {
            abort("server correction");
            return;
        }
        if (client == null || client.player == null || client.level == null) {
            abort("client world unavailable");
            return;
        }
        if (!client.player.isAlive()) {
            abort("player not alive");
            return;
        }
        if (active.targetMissing(client.level)) {
            abort("target missing");
            return;
        }
        if (!active.stillHasRequiredSpear(client.player)) {
            abort("required spear changed");
            return;
        }
        if (ticksAlive > active.timeoutTicks()) {
            abort("sequence timeout");
            return;
        }

        advanceReadySequence();
    }

    void advanceReadySequence() {
        if (active == null) {
            return;
        }
        if (postStabDelayTicks > 0) {
            postStabDelayTicks--;
            return;
        }

        if (phase == AttackSequence.Phase.VERIFY) {
            setPhase(AttackSequence.Phase.CLEANUP);
            finishDone();
            return;
        }

        for (int transitions = 0;
             active != null && transitions < MAX_PHASE_TRANSITIONS_PER_TICK;
             transitions++) {
            switch (phase) {
                case PREPARE -> {
                    if (active.preRotateForOneServerTick()) {
                        RotationPlan rotation = active.rotationPlan();
                        packets.rotate(
                            rotation.yaw(),
                            rotation.pitch(),
                            active.context().onGround(),
                            active.context().horizontalCollision()
                        );
                        serverRotationStaged = true;
                        setPhase(AttackSequence.Phase.ROTATE);
                        return;
                    }
                    setPhase(AttackSequence.Phase.STAGE);
                }
                case ROTATE -> setPhase(AttackSequence.Phase.STAGE);
                case STAGE -> {
                    int lastStageIndex = active.sendStab()
                        ? active.attackMovementIndex()
                        : active.movementPath().positions().size() - 1;
                    if (!sendRange(0, lastStageIndex)) {
                        return;
                    }
                    setPhase(AttackSequence.Phase.ATTACK);
                }
                case ATTACK -> {
                    if (active.sendStab()) {
                        packets.stab();
                    }
                    setPhase(AttackSequence.Phase.RETURN);
                }
                case RETURN -> {
                    if (active.sendStab()
                        && !sendRange(active.attackMovementIndex() + 1,
                            active.movementPath().positions().size() - 1)) {
                        return;
                    }
                    setPhase(AttackSequence.Phase.RESTORE);
                }
                case RESTORE -> {
                    restoreServerRotation();
                    setPhase(AttackSequence.Phase.VERIFY);
                    return;
                }
                case VERIFY -> {
                    return;
                }
                case CLEANUP -> {
                    finishDone();
                    return;
                }
                case DONE, FAILED -> {
                    return;
                }
            }
        }
    }

    public void abort(String reason) {
        if (active == null) {
            return;
        }
        restoreServerRotation();
        phase = AttackSequence.Phase.FAILED;
        tracker.setPhase(phase.name());
        tracker.endSequence(phase.name());
        active = null;
    }

    public AttackSequence.Phase phase() {
        return phase;
    }

    public long activeSequenceId() {
        return active == null ? -1L : active.sequenceId();
    }

    public boolean isActive() {
        return active != null;
    }

    private boolean sendRange(int startInclusive, int endInclusive) {
        if (startInclusive > endInclusive) {
            return true;
        }
        for (int index = startInclusive; index <= endInclusive; index++) {
            if (movementPacketsSent >= active.maxMovementPackets()) {
                abort("movement packet budget exhausted");
                return false;
            }
            Vec3 position = active.movementPath().positions().get(index);
            if (serverRotationStaged) {
                RotationPlan rotation = active.rotationPlan();
                packets.moveAndRotate(
                    position,
                    rotation.yaw(),
                    rotation.pitch(),
                    active.context().onGround(),
                    active.context().horizontalCollision()
                );
            } else {
                packets.move(
                    position,
                    active.context().onGround(),
                    active.context().horizontalCollision()
                );
            }
            movementPacketsSent++;
        }
        return true;
    }

    private void restoreServerRotation() {
        if (!serverRotationStaged || active == null) {
            return;
        }
        packets.rotate(
            active.context().yaw(),
            active.context().pitch(),
            active.context().onGround(),
            active.context().horizontalCollision()
        );
        serverRotationStaged = false;
    }

    private void setPhase(AttackSequence.Phase next) {
        phase = next;
        tracker.setPhase(next.name());
    }

    private void finishDone() {
        restoreServerRotation();
        phase = AttackSequence.Phase.DONE;
        tracker.setPhase(phase.name());
        tracker.endSequence(phase.name());
        active = null;
    }
}
