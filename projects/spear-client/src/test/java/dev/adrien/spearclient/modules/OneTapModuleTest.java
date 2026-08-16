package dev.adrien.spearclient.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.spearclient.combat.AttackSequence;
import dev.adrien.spearclient.combat.SpearContext;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class OneTapModuleTest {
    @Test
    void conservativePlanUsesComponentDelayAndMinimumSafeBackDistance() {
        OneTapModule module = new OneTapModule(true);
        SpearContext context = contextWithKinetic(10, 1.5f, 10);

        AttackSequence sequence = module.prepare(context).orElseThrow();

        assertEquals(AttackSequence.Kind.ONE_TAP, sequence.kind());
        assertEquals(2, sequence.movementPath().positions().size());
        assertTrue(sequence.expectedForwardKnownMovement() >= 6.0);
        assertTrue(sequence.expectedForwardKnownMovement() <= 8.5);
    }

    @Test
    void noKineticComponentMeansNoOneTapSequence() {
        OneTapModule module = new OneTapModule(true);
        SpearContext context = new SpearContext(
            Vec3.ZERO,
            Vec3.ZERO,
            new Vec3(0, 0, 1),
            0.0f,
            0.0f,
            true,
            false,
            ItemStack.EMPTY,
            piercing(),
            null,
            20,
            4,
            new Vec3(0, 0, 3)
        );

        assertTrue(module.prepare(context).isEmpty());
    }

    @Test
    void chargeBeforeComponentDelayDoesNotPrepareMovement() {
        OneTapModule module = new OneTapModule(true);
        assertTrue(module.prepare(contextWithKinetic(10, 1.5f, 9)).isEmpty());
    }

    private static SpearContext contextWithKinetic(int delayTicks, float damageMultiplier, int ticksUsing) {
        return new SpearContext(
            Vec3.ZERO,
            Vec3.ZERO,
            new Vec3(0, 0, 1),
            0.0f,
            0.0f,
            true,
            false,
            ItemStack.EMPTY,
            piercing(),
            new KineticWeapon(
                10,
                delayTicks,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0.0f,
                damageMultiplier,
                Optional.empty(),
                Optional.empty()
            ),
            ticksUsing,
            4,
            new Vec3(0, 0, 3)
        );
    }

    private static PiercingWeapon piercing() {
        return new PiercingWeapon(true, false, Optional.empty(), Optional.empty());
    }
}
