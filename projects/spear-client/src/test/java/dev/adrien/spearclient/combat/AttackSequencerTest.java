package dev.adrien.spearclient.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.spearclient.network.MovementPath;
import dev.adrien.spearclient.network.PacketSender;
import dev.adrien.spearclient.network.ServerStateTracker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AttackSequencerTest {
    @Test
    void secondSequenceCannotCorruptActiveSequence() {
        ServerStateTracker tracker = new ServerStateTracker();
        AttackSequencer sequencer = fixture(new PacketSender(tracker), tracker);
        assertTrue(sequencer.tryStart(sequence(1L, AttackSequence.Kind.ONE_TAP, 2)));
        assertFalse(sequencer.tryStart(sequence(2L, AttackSequence.Kind.REACH, 3)));
        assertEquals(1L, sequencer.activeSequenceId());
    }

    @Test
    void correctionAbortsBeforeFurtherPackets() {
        ServerStateTracker tracker = new ServerStateTracker();
        CountingPacketSender packets = new CountingPacketSender(tracker);
        AttackSequencer sequencer = fixture(packets, tracker);
        sequencer.tryStart(sequence(7L, AttackSequence.Kind.REACH, 3));
        tracker.onCorrection(Vec3.ZERO);

        sequencer.tick(null);

        assertEquals(AttackSequence.Phase.FAILED, sequencer.phase());
        assertEquals(0, packets.events.size());
    }

    @Test
    void sequenceRejectsMovementBudgetAboveEight() {
        assertThrows(IllegalArgumentException.class,
            () -> sequence(9L, AttackSequence.Kind.REACH, 9));
    }

    @Test
    void preRotationWaitsOneTickThenUsesPosRotAndRestoresOriginalRotation() {
        ServerStateTracker tracker = new ServerStateTracker();
        CountingPacketSender packets = new CountingPacketSender(tracker);
        AttackSequencer sequencer = fixture(packets, tracker);
        AttackSequence sequence = rotatedReachSequence();
        sequencer.tryStart(sequence);

        sequencer.advanceReadySequence();
        assertEquals(List.of("rot:-90.0:0.0"), packets.events);
        assertEquals(AttackSequence.Phase.ROTATE, sequencer.phase());

        sequencer.advanceReadySequence();
        assertEquals(List.of(
            "rot:-90.0:0.0",
            "posrot:-90.0:0.0:-9.0",
            "posrot:-90.0:0.0:9.0",
            "stab",
            "posrot:-90.0:0.0:0.0",
            "rot:30.0:10.0"
        ), packets.events);
        assertEquals(AttackSequence.Phase.VERIFY, sequencer.phase());
    }

    @Test
    void abortAfterPreRotationRestoresOriginalRotationExactlyOnce() {
        ServerStateTracker tracker = new ServerStateTracker();
        CountingPacketSender packets = new CountingPacketSender(tracker);
        AttackSequencer sequencer = fixture(packets, tracker);
        sequencer.tryStart(rotatedReachSequence());
        sequencer.advanceReadySequence();

        sequencer.abort("target lost");
        sequencer.abort("duplicate abort");

        assertEquals(List.of("rot:-90.0:0.0", "rot:30.0:10.0"), packets.events);
        assertEquals(AttackSequence.Phase.FAILED, sequencer.phase());
    }

    private static AttackSequencer fixture(PacketSender packets, ServerStateTracker tracker) {
        return new AttackSequencer(packets, tracker);
    }

    private static AttackSequence sequence(long id, AttackSequence.Kind kind, int maxMovementPackets) {
        SpearContext context = context();
        MovementPath path = MovementPath.of(Vec3.ZERO, List.of());
        return new AttackSequence(
            id,
            kind,
            context,
            path,
            false,
            -1,
            0.0,
            maxMovementPackets,
            20
        );
    }

    private static AttackSequence rotatedReachSequence() {
        SpearContext context = context();
        MovementPath path = MovementPath.of(Vec3.ZERO, List.of(
            new Vec3(-9, 0, 0),
            new Vec3(9, 0, 0),
            Vec3.ZERO
        ));
        return new AttackSequence(
            33L,
            AttackSequence.Kind.REACH,
            context,
            path,
            true,
            1,
            18.0,
            3,
            20,
            new RotationPlan(-90.0f, 0.0f),
            true
        );
    }

    private static SpearContext context() {
        return new SpearContext(
            Vec3.ZERO,
            Vec3.ZERO,
            new Vec3(0, 0, 1),
            30.0f,
            10.0f,
            true,
            false,
            ItemStack.EMPTY,
            null,
            null,
            0,
            -1,
            Vec3.ZERO
        );
    }

    private static final class CountingPacketSender extends PacketSender {
        private final List<String> events = new ArrayList<>();

        private CountingPacketSender(ServerStateTracker tracker) {
            super(tracker);
        }

        @Override
        public void move(Vec3 pos, boolean onGround, boolean horizontalCollision) {
            events.add("pos:" + pos.x);
        }

        @Override
        public void moveAndRotate(
            Vec3 pos,
            float yaw,
            float pitch,
            boolean onGround,
            boolean horizontalCollision
        ) {
            events.add("posrot:" + yaw + ":" + pitch + ":" + pos.x);
        }

        @Override
        public void rotate(float yaw, float pitch, boolean onGround, boolean horizontalCollision) {
            events.add("rot:" + yaw + ":" + pitch);
        }

        @Override
        public void stab() {
            events.add("stab");
        }
    }
}
