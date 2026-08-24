package dev.adrien.spearclient.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.spearclient.combat.AttackSequence;
import dev.adrien.spearclient.combat.SpearContext;
import dev.adrien.spearclient.network.MovementEnvelope;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LungeBoostModuleTest {
    @Test
    void smartLungeNeverPlansBeyondConservativeRadius() {
        AttackSequence sequence = new LungeBoostModule(true)
            .afterStab(contextWithLunge())
            .orElseThrow();

        assertEquals(AttackSequence.Kind.LUNGE, sequence.kind());
        assertEquals(1, sequence.movementPath().positions().size());
        assertTrue(sequence.movementPath().positions().stream()
            .allMatch(p -> p.distanceTo(sequence.context().origin())
                <= MovementEnvelope.CONSERVATIVE_RADIUS));
        assertEquals(8.5, sequence.expectedForwardKnownMovement(), 1e-9);
    }

    @Test
    void noLungeEffectMeansNoPacketLungePlan() {
        assertTrue(new LungeBoostModule(true).afterStab(contextWithoutLunge()).isEmpty());
    }

    @Test
    void vanillaConditionFailureMeansNoPacketLungePlan() {
        assertTrue(new LungeBoostModule(true).afterStab(contextWithBlockedLunge()).isEmpty());
    }

    private static SpearContext contextWithLunge() {
        return context(2, true);
    }

    private static SpearContext contextWithoutLunge() {
        return context(0, false);
    }

    private static SpearContext contextWithBlockedLunge() {
        return context(2, false);
    }

    private static SpearContext context(int lungeLevel, boolean lungeEligible) {
        return new SpearContext(
            Vec3.ZERO,
            new Vec3(0, 1.62, 0),
            new Vec3(0, 0, 1),
            0.0f,
            0.0f,
            true,
            false,
            ItemStack.EMPTY,
            new PiercingWeapon(true, false, Optional.empty(), Optional.empty()),
            null,
            0,
            -1,
            Vec3.ZERO,
            lungeLevel,
            lungeEligible
        );
    }
}
