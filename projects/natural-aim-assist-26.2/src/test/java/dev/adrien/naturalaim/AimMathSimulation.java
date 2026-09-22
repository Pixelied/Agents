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
        testPullAwayEvidence();
        testMicroResistanceMath();
        testRegionClamp();
        testMobTargetRules();
        testCombatIntentWindow();
        testLerp();
        testAdaptiveFlickRetention();
        testTargetAwareActionPause();
        testPvpProximityScaling();
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

    private static void testPullAwayEvidence() {
        double evidence = 0.0;

        evidence = AimMath.updatePullAwayEvidence(
                evidence, -0.03, 0.0, 3.0, 0.0,
                -0.34, 0.008, 3.25, 1.0 / 120.0
        );
        check(evidence < 0.05, "one micro movement should not look like deliberate pull-away");

        for (int i = 0; i < 16; i++) {
            evidence = AimMath.updatePullAwayEvidence(
                    evidence, -0.08, 0.0, 3.0, 0.0,
                    -0.34, 0.008, 3.25, 1.0 / 120.0
            );
        }
        check(evidence >= 0.80, "sustained movement away should accumulate enough intent to release");

        double recovered = AimMath.updatePullAwayEvidence(
                evidence, 0.30, 0.0, 3.0, 0.0,
                -0.34, 0.008, 3.25, 0.10
        );
        check(recovered < evidence, "movement back toward the target should rapidly clear pull-away evidence");

        double towardOnly = AimMath.updatePullAwayEvidence(
                0.0, 0.25, 0.0, 3.0, 0.0,
                -0.34, 0.008, 3.25, 1.0 / 60.0
        );
        near(0.0, towardOnly, 1.0e-9, "movement toward target should not build pull-away evidence");
    }

    private static void testMicroResistanceMath() {
        near(0.10, AimMath.opposingProjection(-0.10, 0.0, 3.0, 0.0), 1.0e-9,
                "opposing yaw should project fully away from target");
        near(0.0, AimMath.opposingProjection(0.10, 0.0, 3.0, 0.0), 1.0e-9,
                "toward-target yaw should not create resistance");
        near(0.25, AimMath.boundedCorrection(0.40, 0.25), 1.0e-9,
                "micro resistance must not overshoot target error");
        near(0.0, AimMath.boundedCorrection(-0.10, 0.25), 1.0e-9,
                "correction pointing away from target must be rejected");
    }

    private static void testRegionClamp() {
        near(2.0, AimMath.clampToRegion(1.0, 2.0, 4.0), 1.0e-9, "region low clamp");
        near(3.0, AimMath.clampToRegion(3.0, 2.0, 4.0), 1.0e-9, "region preserves interior aim");
        near(4.0, AimMath.clampToRegion(5.0, 2.0, 4.0), 1.0e-9, "region high clamp");
    }

    private static void testCombatIntentWindow() {
        long click = 1_000_000_000L;
        long hold = 900_000_000L;

        check(AimMath.combatIntentActive(click, click, hold),
                "attack intent should be active on the click");
        check(AimMath.combatIntentActive(click + 100_000_000L, click, hold),
                "attack intent should survive normal fast click gaps");
        check(AimMath.combatIntentActive(click + 625_000_000L, click, hold),
                "attack intent should survive a full sword-cooldown-sized gap");
        check(AimMath.combatIntentActive(click + 900_000_000L, click, hold),
                "attack intent should include the hold-window boundary");
        check(!AimMath.combatIntentActive(click + 900_000_001L, click, hold),
                "attack intent should expire immediately after the hold window");
        check(!AimMath.combatIntentActive(click - 1L, click, hold),
                "future timestamps must not count as active combat intent");
        check(!AimMath.combatIntentActive(click, 0L, hold),
                "no attack history must not activate combat intent");
    }

    private static void testAdaptiveFlickRetention() {
        near(1.0, AimMath.adaptiveFlickFactor(300.0, 0.20, 500.0, 1400.0), 1.0e-9,
                "normal tracking speed should retain full assistance");

        double freshFast = AimMath.adaptiveFlickFactor(1600.0, 0.0, 500.0, 1400.0);
        double committedFast = AimMath.adaptiveFlickFactor(1600.0, 0.25, 500.0, 1400.0);

        near(0.58, freshFast, 1.0e-9,
                "fresh high-speed acquisition should retain more than half strength");
        near(0.80, committedFast, 1.0e-9,
                "committed PvP tracking should retain 80 percent strength at extreme camera speed");
        check(committedFast > freshFast,
                "target commitment should reduce flick suppression");
    }

    private static void testTargetAwareActionPause() {
        long grace = 280_000_000L;

        check(!AimMath.shouldPauseForAction(false, false, true, false, true, 50_000_000L, grace),
                "disabled action pausing should never block assistance");
        check(AimMath.shouldPauseForAction(true, true, false, true, false, Long.MAX_VALUE, grace),
                "item use should still pause assistance");
        check(AimMath.shouldPauseForAction(true, false, true, false, true, 50_000_000L, grace),
                "real mining without a combat target should pause assistance");
        check(!AimMath.shouldPauseForAction(true, false, true, true, true, 80_000_000L, grace),
                "brief PvP miss onto a block should not pause when a combat target is available");
        check(AimMath.shouldPauseForAction(true, false, true, true, true, 500_000_000L, grace),
                "sustained block breaking should become a deliberate mining pause");
        check(AimMath.shouldPauseForAction(true, false, true, true, false, Long.MAX_VALUE, grace),
                "stale destroying state without active attack should not bypass mining pause");
    }

    private static void testPvpProximityScaling() {
        double closeStrength = AimMath.pvpProximityStrength(1.2);
        double midStrength = AimMath.pvpProximityStrength(3.2);
        double closeTurn = AimMath.pvpTurnSpeedScale(1.2);
        double midTurn = AimMath.pvpTurnSpeedScale(3.3);

        check(closeStrength > 1.0,
                "close PvP should not retain the old close-range strength penalty");
        near(1.0, midStrength, 1.0e-9,
                "mid-range PvP strength should return to neutral scaling");
        check(closeTurn > 1.0,
                "close PvP should allow faster angular tracking");
        near(1.0, midTurn, 1.0e-9,
                "mid-range PvP turn speed should return to preset limits");
    }

    private static void testLerp() {
        near(0.42, AimMath.lerp(0.42, 0.20, -1.0), 1.0e-9,
                "lerp should clamp low");
        near(0.31, AimMath.lerp(0.42, 0.20, 0.5), 1.0e-9,
                "lerp midpoint");
        near(0.20, AimMath.lerp(0.42, 0.20, 2.0), 1.0e-9,
                "lerp should clamp high");
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
