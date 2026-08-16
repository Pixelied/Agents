package dev.adrien.spearclient.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MovementPathTest {
    @Test
    void backReturnEndsAtOriginAndCreatesForwardKnownMovement() {
        Vec3 origin = Vec3.ZERO;
        Vec3 look = new Vec3(0, 0, 1);
        MovementPath path = MovementPath.conservativeBackReturn(origin, look, 6.0);

        assertEquals(new Vec3(0, 0, -6), path.positions().get(0));
        assertEquals(origin, path.positions().get(1));
        assertEquals(6.0,
            path.positions().get(1).subtract(path.positions().get(0)).dot(look), 1e-9);
    }

    @Test
    void conservativeReachUsesBackForwardAttackReturnShape() {
        MovementPath path = MovementPath.conservativeReach(Vec3.ZERO, new Vec3(1, 0, 0), 9.0);

        assertEquals(3, path.positions().size());
        assertEquals(new Vec3(-9, 0, 0), path.positions().get(0));
        assertEquals(new Vec3(9, 0, 0), path.positions().get(1));
        assertEquals(Vec3.ZERO, path.positions().get(2));
    }

    @Test
    void pathRejectsMoreThanEightPackets() {
        List<Vec3> positions = List.of(
            Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
            Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO
        );
        assertThrows(IllegalArgumentException.class, () -> MovementPath.of(Vec3.ZERO, positions));
    }

    @Test
    void conservativePathRejectsPositionsOutsideNineBlockBudget() {
        assertThrows(IllegalArgumentException.class,
            () -> MovementPath.of(Vec3.ZERO, List.of(new Vec3(9.001, 0, 0))));
    }

    @Test
    void pathRejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,
            () -> MovementPath.of(Vec3.ZERO, List.of(new Vec3(Double.NaN, 0, 0))));
    }
}
