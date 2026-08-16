package dev.adrien.spearclient.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.spearclient.network.MovementPath;
import dev.adrien.spearclient.network.PacketSender;
import dev.adrien.spearclient.network.ServerStateTracker;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AttackSequencerTest {
    @Test
    void secondSequenceCannotCorruptActiveSequence() {
        AttackSequencer sequencer = fixture(new PacketSender(new ServerStateTracker()), new ServerStateTracker());
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
        assertEquals(0, packets.totalSends);
    }

    @Test
    void sequenceRejectsMovementBudgetAboveEight() {
        assertThrows(IllegalArgumentException.class,
            () -> sequence(9L, AttackSequence.Kind.REACH, 9));
    }

    private static AttackSequencer fixture(PacketSender packets, ServerStateTracker tracker) {
        return new AttackSequencer(packets, tracker);
    }

    private static AttackSequence sequence(long id, AttackSequence.Kind kind, int maxMovementPackets) {
        SpearContext context = new SpearContext(
            Vec3.ZERO,
            Vec3.ZERO,
            new Vec3(0, 0, 1),
            0.0f,
            0.0f,
            true,
            false,
            ItemStack.EMPTY,
            null,
            null,
            0,
            -1,
            Vec3.ZERO
        );
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

    private static final class CountingPacketSender extends PacketSender {
        private int totalSends;

        private CountingPacketSender(ServerStateTracker tracker) {
            super(tracker);
        }

        @Override
        public void move(Vec3 pos, boolean onGround, boolean horizontalCollision) {
            totalSends++;
        }

        @Override
        public void moveAndRotate(
            Vec3 pos,
            float yaw,
            float pitch,
            boolean onGround,
            boolean horizontalCollision
        ) {
            totalSends++;
        }

        @Override
        public void rotate(float yaw, float pitch, boolean onGround, boolean horizontalCollision) {
            totalSends++;
        }

        @Override
        public void stab() {
            totalSends++;
        }
    }
}
