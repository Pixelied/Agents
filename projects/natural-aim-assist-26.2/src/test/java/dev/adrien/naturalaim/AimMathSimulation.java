package dev.adrien.naturalaim;

import net.minecraft.world.entity.MobCategory;

public final class AimMathSimulation {
    private AimMathSimulation() {}

    public static void main(String[] args) {
        testWrapDegrees();
        testSmoothstep();
        testApproach();
        testBoundedStep();
        testIntentAlignment();
        testPullAway();
        testRegionClamp();
        testMobTargetRules();
        System.out.println("Natural Aim math simulations passed.");
    }

    private static void testWrapDegrees() {
        near(-179.0, AimMath.wrapDegrees(181.0), 1.0e-9, "181 wraps to -179");
        near(179.0, AimMath.wrapDegrees(-181.0), 1.0e-9, "-181 wraps to 179");
        near(-180.0, AimMath.wrapDegrees(180.0), 1.0e-9, "180 uses signed half-turn");
        near(0.0, AimMath.wrapDegrees(720.0), 1.0e-9, "two full turns wrap to zero");
    }

    private static void testSmoothstep() {
        near(0.0, AimMath.smoothstep(0.0, 10.0, -1.0), 1.0e-9, "smoothstep low clamp");
        near(0.5, AimMath.smoothstep(0.0, 10.0, 5.0), 1.0e-9, "smoothstep midpoint");
        near(1.0, AimMath.smoothstep(0.0, 10.0, 12.0), 1.0e-9, "smoothstep high clamp");
    }

    private static void testApproach() {
        near(3.0, AimMath.approach(0.0, 10.0, 3.0), 1.0e-9, "approach positive");
        near(-3.0, AimMath.approach(0.0, -10.0, 3.0), 1.0e-9, "approach negative");
        near(10.0, AimMath.approach(9.0, 10.0, 3.0), 1.0e-9, "approach does not overshoot");
    }

    private static void testBoundedStep() {
        near(2.0, AimMath.boundedStep(120.0, 1.0 / 60.0, 8.0), 1.0e-9, "normal integrated step");
        near(0.4, AimMath.boundedStep(120.0, 1.0 / 60.0, 0.4), 1.0e-9, "step clamps at remaining error");
        near(0.0, AimMath.boundedStep(-120.0, 1.0 / 60.0, 1.0), 1.0e-9, "opposing controller velocity is rejected");
    }

    private static void testIntentAlignment() {
        near(1.0, AimMath.intentAlignment(1.0, 0.0, 5.0, 0.0), 1.0e-9, "toward input alignment");
        near(-1.0, AimMath.intentAlignment(-1.0, 0.0, 5.0, 0.0), 1.0e-9, "away input alignment");
        near(0.0, AimMath.intentAlignment(0.0, 1.0, 5.0, 0.0), 1.0e-9, "orthogonal input alignment");
    }

    private static void testPullAway() {
        check(AimMath.isDeliberatePullAway(-0.3, 0.0, 3.0, 0.0, 0.05, -0.22), "clear pull-away should disengage");
        check(!AimMath.isDeliberatePullAway(0.01, 0.0, 3.0, 0.0, 0.05, -0.22), "tiny hand noise should not disengage");
        check(!AimMath.isDeliberatePullAway(0.3, 0.0, 3.0, 0.0, 0.05, -0.22), "movement toward target should stay assisted");
    }

    private static void testRegionClamp() {
        near(2.0, AimMath.clampToRegion(1.0, 2.0, 4.0), 1.0e-9, "region low clamp");
        near(3.0, AimMath.clampToRegion(3.0, 2.0, 4.0), 1.0e-9, "region preserves interior aim");
        near(4.0, AimMath.clampToRegion(5.0, 2.0, 4.0), 1.0e-9, "region high clamp");
    }

    private static void testMobTargetRules() {
        check(TargetRules.allowsMobCategory(MobCategory.MONSTER, true, false),
                "hostile mobs should be targetable when hostile targeting is enabled");
        check(!TargetRules.allowsMobCategory(MobCategory.MONSTER, false, true),
                "hostile mobs should not leak into passive targeting");
        check(TargetRules.allowsMobCategory(MobCategory.CREATURE, false, true),
                "passive creature category should be targetable when passive targeting is enabled");
        check(!TargetRules.allowsMobCategory(MobCategory.CREATURE, true, false),
                "passive creature category should not leak into hostile targeting");
    }

    private static void near(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
