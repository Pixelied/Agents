package dev.adrien.spearclient.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.adrien.spearclient.config.SpearConfig;
import dev.adrien.spearclient.modules.InfiniteReachModule;
import dev.adrien.spearclient.modules.LungeBoostModule;
import dev.adrien.spearclient.modules.OneTapModule;
import dev.adrien.spearclient.network.MovementPath;
import dev.adrien.spearclient.network.PacketSender;
import dev.adrien.spearclient.network.ServerStateTracker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LifecycleAbortTest {
    @Test
    void correctionResetClearsActiveSequenceWithoutCleanupPackets() {
        ServerStateTracker tracker = new ServerStateTracker();
        CountingPacketSender packets = new CountingPacketSender(tracker);
        AttackSequencer sequencer = new AttackSequencer(packets, tracker);
        SpearController controller = controller(sequencer);
        sequencer.tryStart(rotatedReachSequence());
        sequencer.advanceReadySequence();

        controller.reset(ResetReason.CORRECTION);

        assertFalse(sequencer.isActive());
        assertEquals(List.of("rot:-90.0:0.0"), packets.events);
    }

    @Test
    void configDisableResetRestoresPacketOnlyRotationOnce() {
        ServerStateTracker tracker = new ServerStateTracker();
        CountingPacketSender packets = new CountingPacketSender(tracker);
        AttackSequencer sequencer = new AttackSequencer(packets, tracker);
        SpearController controller = controller(sequencer);
        sequencer.tryStart(rotatedReachSequence());
        sequencer.advanceReadySequence();

        controller.reset(ResetReason.CONFIG_DISABLED);

        assertFalse(sequencer.isActive());
        assertEquals(List.of("rot:-90.0:0.0", "rot:30.0:10.0"), packets.events);
    }

    @Test
    void disablingOnlyActiveReachModuleAbortsEvenWhenOneTapRemainsEnabled() {
        ServerStateTracker tracker = new ServerStateTracker();
        CountingPacketSender packets = new CountingPacketSender(tracker);
        AttackSequencer sequencer = new AttackSequencer(packets, tracker);
        SpearController controller = controller(sequencer);
        sequencer.tryStart(rotatedReachSequence());
        sequencer.advanceReadySequence();

        SpearConfig previous = config(false, false, true);
        SpearConfig next = config(true, false, false);
        controller.onConfigChanged(previous, next);

        assertFalse(sequencer.isActive());
        assertEquals(List.of("rot:-90.0:0.0", "rot:30.0:10.0"), packets.events);
    }

    private static SpearController controller(AttackSequencer sequencer) {
        return new SpearController(
            SpearConfig::defaults,
            sequencer,
            new OneTapModule(true),
            new LungeBoostModule(true),
            new InfiniteReachModule(true)
        );
    }

    private static SpearConfig config(boolean oneTap, boolean lunge, boolean reach) {
        return new SpearConfig(
            new SpearConfig.OneTapConfig(oneTap, SpearConfig.OneTapMode.SMART),
            new SpearConfig.LungeConfig(lunge, SpearConfig.LungeMode.SMART),
            new SpearConfig.ReachConfig(reach, SpearConfig.ReachMode.SMART, true),
            false
        );
    }

    private static AttackSequence rotatedReachSequence() {
        SpearContext context = new SpearContext(
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
        return new AttackSequence(
            91L,
            AttackSequence.Kind.REACH,
            context,
            MovementPath.of(Vec3.ZERO, List.of(
                new Vec3(-9, 0, 0),
                new Vec3(9, 0, 0),
                Vec3.ZERO
            )),
            true,
            1,
            18.0,
            3,
            20,
            new RotationPlan(-90.0f, 0.0f),
            true
        );
    }

    private static final class CountingPacketSender extends PacketSender {
        private final List<String> events = new ArrayList<>();

        private CountingPacketSender(ServerStateTracker tracker) {
            super(tracker);
        }

        @Override
        public void rotate(float yaw, float pitch, boolean onGround, boolean horizontalCollision) {
            events.add("rot:" + yaw + ":" + pitch);
        }
    }
}
