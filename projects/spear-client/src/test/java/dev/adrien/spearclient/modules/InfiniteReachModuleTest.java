package dev.adrien.spearclient.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.spearclient.combat.AttackSequence;
import dev.adrien.spearclient.combat.SpearContext;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class InfiniteReachModuleTest {
    @Test
    void smartReachProducesBackForwardStabReturn() {
        InfiniteReachModule module = new InfiniteReachModule(true);
        AttackSequence sequence = module.prepare(contextFacingPositiveZ()).orElseThrow();

        assertEquals(AttackSequence.Kind.REACH, sequence.kind());
        assertEquals(List.of(
            new Vec3(0, 64, -9),
            new Vec3(0, 64, 9),
            new Vec3(0, 64, 0)
        ), sequence.movementPath().positions());
        assertTrue(sequence.sendStabAtMovementIndex(1));
        assertEquals(18.0, sequence.expectedForwardKnownMovement(), 1e-9);
        assertTrue(sequence.preRotateForOneServerTick());
        assertEquals(0.0f, sequence.rotationPlan().yaw(), 1e-4f);
    }

    @Test
    void offCrosshairReachStagesAlongTargetDirectionNotOldCameraLook() {
        InfiniteReachModule module = new InfiniteReachModule(true);
        SpearContext context = new SpearContext(
            new Vec3(0, 64, 0),
            new Vec3(0, 65.62, 0),
            new Vec3(0, 0, 1),
            0.0f,
            0.0f,
            true,
            false,
            ItemStack.EMPTY,
            new PiercingWeapon(true, false, Optional.empty(), Optional.empty()),
            null,
            0,
            4,
            new Vec3(20, 65.62, 0)
        );

        AttackSequence sequence = module.prepare(context).orElseThrow();

        assertEquals(new Vec3(-9, 64, 0), sequence.movementPath().positions().get(0));
        assertEquals(new Vec3(9, 64, 0), sequence.movementPath().positions().get(1));
        assertEquals(-90.0f, sequence.rotationPlan().yaw(), 1e-4f);
    }

    @Test
    void disabledReachDoesNotPrepareSequence() {
        assertTrue(new InfiniteReachModule(false).prepare(contextFacingPositiveZ()).isEmpty());
    }

    private static SpearContext contextFacingPositiveZ() {
        return new SpearContext(
            new Vec3(0, 64, 0),
            new Vec3(0, 65.62, 0),
            new Vec3(0, 0, 1),
            0.0f,
            0.0f,
            true,
            false,
            ItemStack.EMPTY,
            new PiercingWeapon(true, false, Optional.empty(), Optional.empty()),
            null,
            0,
            4,
            new Vec3(0, 65.62, 20)
        );
    }
}
