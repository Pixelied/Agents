package studio.pixelied.pearlcatch.core;

public final class GeneralCatchSolverSelfTest {
    public static void main(String[] args) {
        testVanillaMarginUsesCollisionSegmentAge();
        testObservedCompletedSegmentMarginUsesPreIncrementAge();
        testStartingInsideWindBoxDoesNotCountAsProjectileHit();
        testCrossingWindBoxCountsAsProjectileHit();
        testSegmentInteriorClearanceGeometry();
        testProjectileDirectionRoundTrip();
        testVanillaLaunchRandomnessOrder();
        testVanillaInheritedMotionRules();
        testServerKnownMovementEstimatorTracksSentPositionDelta();
        testInferProjectileInheritedMovement();
        testReconstructPearlLaunchVelocity();
        testClosedFormPearlPositionMatchesSimulation();
        testInvertPearlLaunchVelocityForTarget();
        testWindLaunchVelocityForFuturePosition();
        testGeneralSolverFindsFlatTwelveBlockCatch();
        testGeneralSolverPrefersDeepCollisionClearance();
        testExistingWindHazardDetection();
        testGeneralSolverUsesDelayForStraightUp();
        testGeneralSolverFindsStraightDownWithoutPlannerModes();
        testGeneralSolverTargetDistanceMovesCatch();
        testGeneralSolverReplansKnownPearl();
        testGeneralSolverHandlesExtremeInheritedMotion();
        testFarTargetIsNotArtificiallyDelayCapped();
        testGeneralSolverInteractiveBudgetAfterWarmup();
        testMinimumWindDelayConstraint();
        testHardExecutionAcceptancePolicy();
        testSolveExecutableRejectsFragileRealPlan();
        testSolveExecutableFindsEligibleAlternative();
        testServerTimingWindow();
        System.out.println("PearlCatch general solver self-test: PASS");
    }

    private static void testVanillaMarginUsesCollisionSegmentAge() {
        assertNear(0.0, VanillaProjectilePhysics.collisionMargin(2), 1e-12, "age 2 margin");
        assertNear(0.05, VanillaProjectilePhysics.collisionMargin(3), 1e-12, "age 3 margin");
        assertNear(0.30, VanillaProjectilePhysics.collisionMargin(100), 1e-12, "margin cap");
    }

    private static void testObservedCompletedSegmentMarginUsesPreIncrementAge() {
        // END_CLIENT_TICK sees tickCount=3 after the segment that ran at tickCount=2; vanilla used zero margin.
        assertNear(0.0, VanillaProjectilePhysics.collisionMarginForCompletedSegment(3), 1e-12, "post-tick age 3 margin");
        // END_CLIENT_TICK sees tickCount=4 after the segment that ran at tickCount=3; vanilla used 0.05.
        assertNear(0.05, VanillaProjectilePhysics.collisionMarginForCompletedSegment(4), 1e-12, "post-tick age 4 margin");
    }

    private static void testStartingInsideWindBoxDoesNotCountAsProjectileHit() {
        Vec3d wind = new Vec3d(0.0, 0.0, 0.0);
        Aabb3d box = VanillaProjectilePhysics.windChargeBox(wind, 0.0);
        Vec3d from = new Vec3d(0.0, 0.0, 0.0);
        Vec3d to = new Vec3d(0.0, -1.0, 0.0);
        assertFalse(box.clipEntry(from, to).hit(), "AABB.clip must not turn start-inside into a hit");
    }

    private static void testCrossingWindBoxCountsAsProjectileHit() {
        Vec3d wind = new Vec3d(0.0, 0.0, 0.0);
        Aabb3d box = VanillaProjectilePhysics.windChargeBox(wind, 0.0);
        Vec3d from = new Vec3d(0.0, 0.5, 0.0);
        Vec3d to = new Vec3d(0.0, 0.0, 0.0);
        assertTrue(box.clipEntry(from, to).hit(), "segment entering wind box should hit");
    }


    private static void testSegmentInteriorClearanceGeometry() {
        Aabb3d unit = new Aabb3d(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0);

        double center = unit.segmentInteriorClearance(new Vec3d(-2.0, 0.0, 0.0), new Vec3d(2.0, 0.0, 0.0));
        assertNear(1.0, center, 1e-9, "center crossing clearance");

        double cornerGraze = unit.segmentInteriorClearance(new Vec3d(-2.0, 1.0, 1.0), new Vec3d(2.0, 1.0, 1.0));
        assertNear(0.0, cornerGraze, 1e-9, "corner graze clearance");

        double shallow = unit.segmentInteriorClearance(new Vec3d(-2.0, 0.8, 0.0), new Vec3d(2.0, 0.8, 0.0));
        assertNear(0.2, shallow, 1e-9, "shallow crossing clearance");

        double miss = unit.segmentInteriorClearance(new Vec3d(-2.0, 1.2, 0.0), new Vec3d(2.0, 1.2, 0.0));
        assertNear(0.0, miss, 1e-9, "miss clearance");
    }

    private static void testProjectileDirectionRoundTrip() {
        Rotation rotation = new Rotation(37.0, -42.0);
        Vec3d direction = VanillaProjectilePhysics.lookDirection(rotation);
        Rotation rebuilt = VanillaProjectilePhysics.rotationForDirection(direction);
        assertNear(rotation.yaw(), rebuilt.yaw(), 1e-9, "yaw round trip");
        assertNear(rotation.pitch(), rebuilt.pitch(), 1e-9, "pitch round trip");
    }

    private static void testVanillaLaunchRandomnessOrder() {
        Rotation r = new Rotation(31.0, -27.0);
        Vec3d inherited = new Vec3d(0.7, -0.4, 0.2);
        Vec3d noise = new Vec3d(0.01, -0.02, 0.005);
        Vec3d direction = VanillaProjectilePhysics.lookDirection(r);
        Vec3d expected = direction.add(noise).scale(VanillaProjectilePhysics.PROJECTILE_POWER).add(inherited);
        Vec3d actual = VanillaProjectilePhysics.perturbedLaunchVelocity(r, inherited, noise);
        assertNear(expected.x(), actual.x(), 1e-12, "vanilla random-order X");
        assertNear(expected.y(), actual.y(), 1e-12, "vanilla random-order Y");
        assertNear(expected.z(), actual.z(), 1e-12, "vanilla random-order Z");
    }

    private static void testServerKnownMovementEstimatorTracksSentPositionDelta() {
        ServerKnownMovementEstimator estimator = new ServerKnownMovementEstimator();
        estimator.beginTick(new Vec3d(10.0, 20.0, 30.0));
        estimator.endTick(new Vec3d(12.25, 19.5, 29.0));
        Vec3d movement = estimator.currentKnownMovement();
        assertNear(2.25, movement.x(), 1e-12, "server-known movement X");
        assertNear(-0.5, movement.y(), 1e-12, "server-known movement Y");
        assertNear(-1.0, movement.z(), 1e-12, "server-known movement Z");

        estimator.beginTick(new Vec3d(12.25, 19.5, 29.0));
        estimator.endTick(new Vec3d(12.25, 19.5, 29.0));
        assertNear(0.0, estimator.currentKnownMovement().length(), 1e-12,
                "stationary tick should estimate zero server-known movement");
    }

    private static void testInferProjectileInheritedMovement() {
        Rotation rotation = new Rotation(-106.0404012647098, -88.47207445813287);
        Vec3d actualLaunch = new Vec3d(0.031984374046267305, 1.482878593664164, -0.0058597326496978575);
        Vec3d inferred = VanillaProjectilePhysics.inferInheritedMotion(rotation, actualLaunch);
        assertTrue(inferred.length() < 0.04,
                "2.4.1 elytra trace should prove the projectile inherited almost zero movement: " + inferred);
    }

    private static void testVanillaInheritedMotionRules() {
        Vec3d movement = new Vec3d(4.25, -7.5, 2.0);
        Vec3d airborne = VanillaProjectilePhysics.inheritedMotion(movement, false);
        assertNear(4.25, airborne.x(), 1e-12, "airborne inherited X");
        assertNear(-7.5, airborne.y(), 1e-12, "airborne inherited Y");
        assertNear(2.0, airborne.z(), 1e-12, "airborne inherited Z");
        Vec3d grounded = VanillaProjectilePhysics.inheritedMotion(movement, true);
        assertNear(4.25, grounded.x(), 1e-12, "ground inherited X");
        assertNear(0.0, grounded.y(), 1e-12, "ground suppresses inherited Y exactly like Projectile#shootFromRotation");
        assertNear(2.0, grounded.z(), 1e-12, "ground inherited Z");
    }

    private static void testReconstructPearlLaunchVelocity() {
        Vec3d[] starts = new Vec3d[] {
                new Vec3d(1.2, 0.7, -0.4),
                new Vec3d(-2.0, 4.5, 3.0),
                new Vec3d(0.0, -10.0, 0.0)
        };
        int[] ticks = new int[] {1, 2, 8, 20};
        for (Vec3d start : starts) {
            for (int completed : ticks) {
                Vec3d observed = start;
                for (int i = 0; i < completed; i++) {
                    observed = VanillaProjectilePhysics.pearlVelocityAfterTick(observed);
                }
                Vec3d rebuilt = VanillaProjectilePhysics.reconstructPearlLaunchVelocity(observed, completed);
                assertNear(start.x(), rebuilt.x(), 1e-9, "reconstructed launch X at t=" + completed);
                assertNear(start.y(), rebuilt.y(), 1e-9, "reconstructed launch Y at t=" + completed);
                assertNear(start.z(), rebuilt.z(), 1e-9, "reconstructed launch Z at t=" + completed);
            }
        }
    }

    private static void testClosedFormPearlPositionMatchesSimulation() {
        Vec3d start = new Vec3d(4.0, -2.0, 8.0);
        Vec3d launch = new Vec3d(1.2, -3.4, 0.75);
        for (int ticks : new int[] {0, 1, 2, 7, 20, 60}) {
            Vec3d pos = start;
            Vec3d velocity = launch;
            for (int i = 0; i < ticks; i++) {
                velocity = VanillaProjectilePhysics.pearlVelocityAfterTick(velocity);
                pos = pos.add(velocity);
            }
            Vec3d closed = VanillaProjectilePhysics.pearlPositionAfterTicks(start, launch, ticks);
            assertNear(pos.x(), closed.x(), 1e-10, "closed-form pearl X t=" + ticks);
            assertNear(pos.y(), closed.y(), 1e-10, "closed-form pearl Y t=" + ticks);
            assertNear(pos.z(), closed.z(), 1e-10, "closed-form pearl Z t=" + ticks);
        }
    }

    private static void testInvertPearlLaunchVelocityForTarget() {
        Vec3d start = new Vec3d(-3.0, 11.0, 2.0);
        Vec3d launch = new Vec3d(0.8, -6.25, 2.4);
        for (int ticks : new int[] {1, 3, 9, 27}) {
            Vec3d target = VanillaProjectilePhysics.pearlPositionAfterTicks(start, launch, ticks);
            Vec3d rebuilt = VanillaProjectilePhysics.requiredPearlLaunchVelocity(start, target, ticks);
            assertNear(launch.x(), rebuilt.x(), 1e-10, "inverse pearl X t=" + ticks);
            assertNear(launch.y(), rebuilt.y(), 1e-10, "inverse pearl Y t=" + ticks);
            assertNear(launch.z(), rebuilt.z(), 1e-10, "inverse pearl Z t=" + ticks);
        }
    }

    private static void testWindLaunchVelocityForFuturePosition() {
        Vec3d start = new Vec3d(2.0, 3.0, 4.0);
        Vec3d velocity = new Vec3d(-1.25, 7.0, 0.5);
        for (int ticks : new int[] {1, 2, 11}) {
            Vec3d target = start.add(velocity.scale(ticks));
            Vec3d rebuilt = VanillaProjectilePhysics.requiredWindLaunchVelocity(start, target, ticks);
            assertNear(velocity.x(), rebuilt.x(), 1e-12, "inverse wind X t=" + ticks);
            assertNear(velocity.y(), rebuilt.y(), 1e-12, "inverse wind Y t=" + ticks);
            assertNear(velocity.z(), rebuilt.z(), 1e-12, "inverse wind Z t=" + ticks);
        }
    }

    private static void testGeneralSolverFindsFlatTwelveBlockCatch() {
        GeneralCatchSolver.Plan plan = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, 0.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 12.0));
        assertNotNull(plan, "general solver should find flat 12b catch");
        assertTrue(Math.abs(plan.crosshairRange() - 12.0) <= 2.0,
                "flat general catch should stay near 12b: " + plan.crosshairRange());
        assertTrue(plan.firstCollisionTick() == plan.pearlCatchTick(),
                "general solver must report its first collision as the planned collision");
    }


    private static void testGeneralSolverPrefersDeepCollisionClearance() {
        GeneralCatchSolver.Plan flat = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, 0.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 12.0));
        assertNotNull(flat, "clearance regression needs flat plan");
        assertTrue(flat.collisionClearance() >= 0.03,
                "flat 12b catch should cross materially inside the wind box: " + flat.collisionClearance());

        GeneralCatchSolver.Plan twenty = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, 0.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 20.0));
        assertNotNull(twenty, "20-block clearance regression should solve");
        assertTrue(twenty.collisionClearance() >= 0.03,
                "20-block target must not select the old face-graze branch: " + twenty.collisionClearance());
        assertTrue(Math.abs(twenty.crosshairRange() - 20.0) <= 1.0,
                "clearance preference must keep the 20b catch materially near target distance: " + twenty.crosshairRange());

        GeneralCatchSolver.Plan eighteen = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, 0.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 18.0));
        assertNotNull(eighteen, "18-block clearance regression should solve");
        assertTrue(Math.abs(eighteen.crosshairRange() - 18.0) <= 0.75,
                "already-safe 18b geometry should remain distance-accurate: " + eighteen.crosshairRange());
        assertTrue(eighteen.collisionClearance() >= 0.03,
                "18b plan should retain its non-grazing geometry: " + eighteen.collisionClearance());

        GeneralCatchSolver.Plan twentyFour = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, 0.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 24.0));
        assertNotNull(twentyFour, "24-block clearance regression should solve");
        assertTrue(Math.abs(twentyFour.crosshairRange() - 24.0) <= 2.0,
                "24b target may move slightly to avoid a graze but must remain near target: " + twentyFour.crosshairRange());
        assertTrue(twentyFour.collisionClearance() >= 0.03,
                "24b plan should avoid the near-zero-clearance branch: " + twentyFour.collisionClearance());
    }


    private static void testExistingWindHazardDetection() {
        Vec3d pearlStart = new Vec3d(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0);
        Vec3d pearlVelocity = VanillaProjectilePhysics.nominalLaunchVelocity(new Rotation(0.0, 0.0), Vec3d.ZERO);
        Vec3d pearlAtThree = VanillaProjectilePhysics.pearlPositionAfterTicks(pearlStart, pearlVelocity, 3);
        assertTrue(GeneralCatchSolver.pathHitsExistingWind(
                        pearlStart, pearlVelocity, pearlAtThree, Vec3d.ZERO, 6),
                "existing wind on the future pearl path must be detected");
        assertFalse(GeneralCatchSolver.pathHitsExistingWind(
                        pearlStart, pearlVelocity, pearlAtThree.add(10.0, 0.0, 0.0), Vec3d.ZERO, 6),
                "distant existing wind must not block a new catch");
    }

    private static void testGeneralSolverUsesDelayForStraightUp() {
        GeneralCatchSolver.Plan plan = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, -90.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 12.0));
        assertNotNull(plan, "general solver should solve straight-up target");
        assertTrue(plan.windDelayTicksFromNow() >= 1,
                "straight-up 12b catch should derive a delayed wind instead of colliding at spawn; delay=" + plan.windDelayTicksFromNow());
        assertTrue(Math.abs(plan.crosshairRange() - 12.0) <= 3.0,
                "straight-up delay should make 12b materially reachable: " + plan.crosshairRange());
    }

    private static void testGeneralSolverFindsStraightDownWithoutPlannerModes() {
        GeneralCatchSolver.Plan plan = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, 90.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 12.0));
        assertNotNull(plan, "general solver should solve straight-down target");
        assertTrue(plan.crosshairDistance() <= 2.0 + 1e-9,
                "straight-down general solver should still produce a real crosshair catch");
        assertTrue(plan.firstCollisionTick() == plan.pearlCatchTick(),
                "straight-down solution must not rely on an earlier accidental catch");
    }

    private static void testGeneralSolverTargetDistanceMovesCatch() {
        GeneralCatchSolver.Plan near = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, 0.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 5.0));
        GeneralCatchSolver.Plan far = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, 0.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 24.0));
        assertNotNull(near, "general near target should solve");
        assertNotNull(far, "general far target should solve");
        assertTrue(far.crosshairRange() - near.crosshairRange() >= 12.0,
                "one general solver must materially obey target range: near=" + near.crosshairRange() + " far=" + far.crosshairRange());
        assertTrue(Math.abs(near.crosshairRange() - 5.0) < Math.abs(far.crosshairRange() - 5.0),
                "near target should choose the near solution");
        assertTrue(Math.abs(far.crosshairRange() - 24.0) < Math.abs(near.crosshairRange() - 24.0),
                "far target should choose the far solution");
    }

    private static void testGeneralSolverReplansKnownPearl() {
        GeneralCatchSolver.Plan initial = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, -65.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 12.0));
        assertNotNull(initial, "known-pearl replan needs an initial plan");
        Vec3d inherited = Vec3d.ZERO;
        Vec3d actualLaunch = VanillaProjectilePhysics.perturbedLaunchVelocity(
                initial.pearlRotation(), inherited, new Vec3d(0.004, -0.003, 0.002));
        Vec3d pearlStart = new Vec3d(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0);
        Vec3d currentEye = Vec3d.ZERO;
        GeneralCatchSolver.Plan replanned = GeneralCatchSolver.solve(new GeneralCatchSolver.Request(
                pearlStart, inherited, actualLaunch, 1, currentEye, Vec3d.ZERO,
                new Rotation(0.0, -65.0), 48, 40.0, 1.5, 12.0, 64, 0));
        assertNotNull(replanned, "same general solver should replan a real known pearl");
        assertTrue(replanned.pearlLaunchKnown(), "replanned result must identify real-pearl input");
        assertTrue(replanned.firstCollisionTick() == replanned.pearlCatchTick(),
                "known-pearl replan must still reject early collisions");
        assertTrue(replanned.crosshairDistance() <= 1.5 + 1e-9,
                "known-pearl replan must stay in crosshair tube");
    }

    private static void testGeneralSolverHandlesExtremeInheritedMotion() {
        Object[][] cases = new Object[][] {
                { new Rotation(0.0, 0.0), new Vec3d(3.0, 0.0, 0.0) },
                { new Rotation(0.0, -90.0), new Vec3d(0.0, 3.0, 0.0) },
                { new Rotation(0.0, 90.0), new Vec3d(0.0, -3.0, 0.0) },
                { new Rotation(0.0, 90.0), new Vec3d(0.0, -10.0, 0.0) }
        };
        int solved = 0;
        for (Object[] c : cases) {
            Rotation target = (Rotation)c[0];
            Vec3d motion = (Vec3d)c[1];
            GeneralCatchSolver.Plan plan = GeneralCatchSolver.solve(generalRequest(
                    target, motion, null, 0, Vec3d.ZERO, 12.0));
            if (plan != null) {
                solved++;
                assertTrue(plan.crosshairDistance() <= 2.0 + 1e-9,
                        "extreme motion plan must remain in predicted crosshair tube");
                assertTrue(plan.firstCollisionTick() == plan.pearlCatchTick(),
                        "extreme motion plan must not have an earlier collision");
            }
        }
        assertTrue(solved == cases.length, "general solver should solve aligned extreme-motion probes; solved=" + solved);
    }

    private static GeneralCatchSolver.Request generalRequest(
            Rotation target, Vec3d motion, Vec3d knownPearlLaunch, int completedPearlTicks,
            Vec3d eye, double targetDistance
    ) {
        return new GeneralCatchSolver.Request(
                eye.add(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0),
                motion, knownPearlLaunch, completedPearlTicks, eye, motion,
                target, 48, 80.0, 2.0, targetDistance, 64, 0
        );
    }


    private static void testFarTargetIsNotArtificiallyDelayCapped() {
        Vec3d eye = Vec3d.ZERO;
        GeneralCatchSolver.Plan plan = GeneralCatchSolver.solve(new GeneralCatchSolver.Request(
                eye.add(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0),
                Vec3d.ZERO, null, 0, eye, Vec3d.ZERO, new Rotation(0.0, 0.0),
                76, 88.0, 1.5, 64.0, 32, 0));
        assertNotNull(plan, "far target should still produce the closest physical solution");
        assertTrue(plan.crosshairRange() >= 45.0,
                "general solver must not let a caller-side 4/6 tick cap truncate a far catch: " + plan.crosshairRange());
    }

    private static void testGeneralSolverInteractiveBudgetAfterWarmup() {
        GeneralCatchSolver.solve(generalRequest(new Rotation(0.0, -90.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 12.0));
        long start = System.nanoTime();
        GeneralCatchSolver.Plan plan = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, 0.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 12.0));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        assertNotNull(plan, "general performance probe must solve");
        assertTrue(elapsedMillis < 300L,
                "general interactive solve budget exceeded: " + elapsedMillis + "ms");
    }


    private static void testMinimumWindDelayConstraint() {
        Vec3d eye = Vec3d.ZERO;
        GeneralCatchSolver.Request constrained = new GeneralCatchSolver.Request(
                eye.add(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0),
                Vec3d.ZERO, null, 0, eye, Vec3d.ZERO, new Rotation(0.0, 0.0),
                48, 80.0, 2.0, 12.0, 64, 3);
        GeneralCatchSolver.Plan plan = GeneralCatchSolver.solve(constrained);
        assertNotNull(plan, "minimum-delay regression should still find a physical catch");
        assertTrue(plan.windDelayTicksFromNow() >= 3,
                "solver must never return wind earlier than execution can perform: " + plan.windDelayTicksFromNow());

        GeneralCatchSolver.Request invalid = new GeneralCatchSolver.Request(
                eye.add(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0),
                Vec3d.ZERO, null, 0, eye, Vec3d.ZERO, new Rotation(0.0, 0.0),
                48, 80.0, 2.0, 12.0, 64, -1);
        assertTrue(GeneralCatchSolver.solve(invalid) == null,
                "negative minimum execution delay must be rejected");
    }


    private static void testHardExecutionAcceptancePolicy() {
        GeneralCatchSolver.Plan base = GeneralCatchSolver.solve(generalRequest(
                new Rotation(0.0, -60.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 12.0));
        assertNotNull(base, "acceptance policy fixture must solve");
        assertFalse(GeneralCatchSolver.isExecutable(copyPlan(base, 0.00, 0.05)),
                "0% reliability must be rejected");
        assertFalse(GeneralCatchSolver.isExecutable(copyPlan(base, 0.79, 0.05)),
                "sub-80% reliability must be rejected");
        assertFalse(GeneralCatchSolver.isExecutable(copyPlan(base, 0.90, 0.029)),
                "sub-0.03 clearance must be rejected");
        assertTrue(GeneralCatchSolver.isExecutable(copyPlan(base, 0.90, 0.04)),
                "robust clear plan must be accepted");
    }

    private static void testSolveExecutableRejectsFragileRealPlan() {
        GeneralCatchSolver.Request fragile = generalRequest(
                new Rotation(0.0, 60.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 12.0);
        GeneralCatchSolver.Plan diagnostic = GeneralCatchSolver.solve(fragile);
        assertNotNull(diagnostic, "fragile diagnostic case must still be inspectable");
        assertFalse(GeneralCatchSolver.isExecutable(diagnostic),
                "reviewed fragile case must fail the hard execution policy");
        assertTrue(GeneralCatchSolver.solveExecutable(fragile) == null,
                "fragile case must never become an executable plan");

        GeneralCatchSolver.Plan robust = GeneralCatchSolver.solveExecutable(generalRequest(
                new Rotation(0.0, -60.0), Vec3d.ZERO, null, 0, Vec3d.ZERO, 12.0));
        assertNotNull(robust, "known robust nominal request must remain executable");
        assertTrue(GeneralCatchSolver.isExecutable(robust), "returned executable plan must satisfy hard gates");
    }

    private static void testSolveExecutableFindsEligibleAlternative() {
        GeneralCatchSolver.Request request = new GeneralCatchSolver.Request(
                Vec3d.ZERO, Vec3d.ZERO, null, 0, Vec3d.ZERO, Vec3d.ZERO,
                new Rotation(0.0, -85.0), 80, 80.0, 2.0, 12.0, 64, 0);
        GeneralCatchSolver.Plan diagnostic = GeneralCatchSolver.solve(request);
        assertNotNull(diagnostic, "alternative-search fixture must produce a diagnostic candidate");
        GeneralCatchSolver.Plan executable = GeneralCatchSolver.solveExecutable(request);
        assertNotNull(executable, "executable search must keep looking for an eligible finalist");
        assertTrue(GeneralCatchSolver.isExecutable(executable), "eligible alternative must satisfy hard gates");
    }

    private static void testServerTimingWindow() {
        ServerTimingWindow zero = ServerTimingWindow.fromRoundTripLatencyMs(0);
        assertTrue(zero.supported() && zero.minLeadTicks() == 0 && zero.maxLeadTicks() == 1,
                "zero RTT must still include one tick of quantization uncertainty");
        ServerTimingWindow fifty = ServerTimingWindow.fromRoundTripLatencyMs(50);
        assertTrue(fifty.supported() && fifty.minLeadTicks() == 1 && fifty.maxLeadTicks() == 2,
                "50ms RTT timing window");
        ServerTimingWindow hundred = ServerTimingWindow.fromRoundTripLatencyMs(100);
        assertTrue(hundred.supported() && hundred.minLeadTicks() == 2 && hundred.maxLeadTicks() == 3,
                "100ms RTT timing window");
        assertFalse(ServerTimingWindow.fromRoundTripLatencyMs(-1).supported(), "unknown latency must fail closed");
        assertFalse(ServerTimingWindow.fromRoundTripLatencyMs(1001).supported(), "extreme latency must fail closed");
    }

    private static GeneralCatchSolver.Plan copyPlan(GeneralCatchSolver.Plan p, double robust, double clearance) {
        return new GeneralCatchSolver.Plan(
                p.pearlRotation(), p.windRotation(), p.windDelayTicksFromNow(), p.pearlCatchTick(),
                p.windCompletedTicksAtCatch(), p.firstWindPearlSegment(), p.interceptPoint(), p.windPositionAtCatch(),
                p.crosshairDistance(), p.crosshairRange(), p.targetDistanceError(), clearance, p.firstCollisionTick(),
                robust, p.score(), p.pearlLaunchKnown());
    }

    private static void assertNear(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError(message);
    }

    private static void assertNotNull(Object value, String message) {
        if (value == null) throw new AssertionError(message);
    }
}
