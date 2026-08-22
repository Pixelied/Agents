package dev.adrien.crystaloptimizer.prediction;

import dev.adrien.crystaloptimizer.world.CombatRegion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PredictionCollisionResolverTest {
    private final PredictionCollisionResolver resolver = new PredictionCollisionResolver();

    @Test
    void noCollisionPreservesRequestedDelta() {
        AABB box = V3PredictionFixtures.currentBox();
        CollisionMoveResult result = resolver.move(
            box,
            new Vec3(0.5, 0.25, -0.4),
            CombatRegion.empty()
        );

        assertEquals(new Vec3(0.5, 0.25, -0.4), result.resolvedDelta());
        assertEquals(box.move(result.resolvedDelta()), result.box());
        assertFalse(result.collidedX());
        assertFalse(result.collidedY());
        assertFalse(result.collidedZ());
    }

    @Test
    void solidWallClampsHorizontalMovementAtPlayerAabbEdge() {
        AABB box = V3PredictionFixtures.currentBox();
        CollisionMoveResult result = resolver.move(
            box,
            new Vec3(2.0, 0.0, 0.0),
            V3PredictionFixtures.geometryWithWallAtX(3)
        );

        assertEquals(0.9, result.resolvedDelta().x, 1.0e-7);
        assertEquals(3.0, result.box().maxX, 1.0e-7);
        assertTrue(result.collidedX());
    }

    @Test
    void floorStopsDownwardMovement() {
        AABB box = V3PredictionFixtures.currentBox();
        CollisionMoveResult result = resolver.move(
            box,
            new Vec3(0.0, -1.0, 0.0),
            V3PredictionFixtures.geometryWithFloorAtY(63)
        );

        assertEquals(0.0, result.resolvedDelta().y, 1.0e-7);
        assertEquals(64.0, result.box().minY, 1.0e-7);
        assertTrue(result.collidedY());
    }

    @Test
    void diagonalMovementSlidesAlongWallInsteadOfStoppingAllAxes() {
        CollisionMoveResult result = resolver.move(
            V3PredictionFixtures.currentBox(),
            new Vec3(2.0, 0.0, 1.0),
            V3PredictionFixtures.geometryWithWallAtX(3)
        );

        assertEquals(0.9, result.resolvedDelta().x, 1.0e-7);
        assertEquals(1.0, result.resolvedDelta().z, 1.0e-7);
        assertTrue(result.collidedX());
        assertFalse(result.collidedZ());
    }
}
