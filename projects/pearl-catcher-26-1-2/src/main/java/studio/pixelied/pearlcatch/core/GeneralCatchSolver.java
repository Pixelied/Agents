package studio.pixelied.pearlcatch.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One catch solver for both the pre-pearl and already-observed-pearl states.
 *
 * <p>Instead of selecting among planner modes, wind throw delay is a normal search variable. Pearl launch
 * directions are derived by inverting the exact 26.1.2 pearl drag/gravity recurrence at candidate points on the
 * target ray. Wind directions are then derived from the fixed-speed reachable sphere at the chosen future tick.
 * Every candidate is finally checked with the exact pearl-segment -> wind AABB entry test, including all earlier
 * segments where the wind already exists.</p>
 */
public final class GeneralCatchSolver {
    private static final double[] PEARL_SEGMENT_FRACTIONS = {
            0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0
    };
    private static final int WIND_SEGMENT_SAMPLES = 48;
    private static final int PER_TIMING_CANDIDATES = 12;
    private static final int CLEARANCE_FINALISTS = 128;
    private static final int FULL_ROBUSTNESS_CANDIDATES = 48;
    public static final double MIN_ROBUST_HIT_FRACTION = 0.80;
    public static final double MIN_GEOMETRIC_CLEARANCE = 0.03;
    private static final double WIND_BOX_CENTER_Y = VanillaProjectilePhysics.WIND_BOX_Y_OFFSET
            + VanillaProjectilePhysics.WIND_HEIGHT * 0.5;
    private static final Vec3d WIND_BOX_CENTER_OFFSET = new Vec3d(0.0, WIND_BOX_CENTER_Y, 0.0);

    private GeneralCatchSolver() {}

    public static Plan solve(Request request) {
        return solve(request, false);
    }

    /** Returns only a plan that is safe enough for execution. Diagnostics may use {@link #solve(Request)}. */
    public static Plan solveExecutable(Request request) {
        return solve(request, true);
    }

    public static boolean isExecutable(Plan plan) {
        return plan != null
                && plan.robustHitFraction() + 1.0e-12 >= MIN_ROBUST_HIT_FRACTION
                && plan.collisionClearance() + 1.0e-12 >= MIN_GEOMETRIC_CLEARANCE;
    }

    private static Plan solve(Request request, boolean executableOnly) {
        if (!valid(request)) return null;

        Vec3d targetRay = VanillaProjectilePhysics.lookDirection(request.targetRotation());
        List<Candidate> top = request.knownPearlLaunchVelocity() == null
                ? solveUnknownPearl(request, targetRay)
                : solveKnownPearl(request, targetRay);
        if (top.isEmpty()) return null;

        List<Candidate> clearanceRanked = top.stream()
                .map(GeneralCatchSolver::withClearance)
                .filter(c -> !executableOnly
                        || (c.collisionClearance() + 1.0e-12 >= MIN_GEOMETRIC_CLEARANCE
                        && c.targetDistanceError() <= Math.max(2.0, request.preferredCatchDistance() * 0.15)))
                .sorted(Comparator.comparingDouble(Candidate::clearanceScore))
                .limit(CLEARANCE_FINALISTS)
                .toList();
        if (clearanceRanked.isEmpty()) return null;

        int requestedSamples = request.spreadSamples();
        int coarseSamples = Math.min(requestedSamples, 16);
        List<Candidate> finalists = clearanceRanked.stream()
                .map(c -> withRobustness(c, coarseSamples))
                .sorted(Comparator.comparingDouble(Candidate::robustScore))
                .limit(FULL_ROBUSTNESS_CANDIDATES)
                .toList();

        Candidate winner = finalists.stream()
                .map(c -> requestedSamples <= coarseSamples
                        ? c : withRobustness(c, coarseSamples, requestedSamples, executableOnly))
                .filter(c -> !executableOnly
                        || (c.robustHitFraction() + 1.0e-12 >= MIN_ROBUST_HIT_FRACTION
                        && c.collisionClearance() + 1.0e-12 >= MIN_GEOMETRIC_CLEARANCE))
                .min(Comparator.comparingDouble(Candidate::robustScore))
                .orElse(null);
        return winner == null ? null : toPlan(winner);
    }

    private static boolean valid(Request r) {
        return r != null
                && r.pearlLaunchPosition() != null
                && r.pearlInheritedMotion() != null
                && r.currentEyePosition() != null
                && r.currentInheritedMotion() != null
                && r.targetRotation() != null
                && r.completedPearlTicks() >= 0
                && r.maxPredictionTicks() >= 2
                && r.maxSearchDistance() > 0.0
                && r.maxCrosshairDistance() >= 0.0
                && r.preferredCatchDistance() > 0.0
                && r.spreadSamples() >= 0
                && r.minimumWindDelayTicks() >= 0;
    }

    private static List<Candidate> solveUnknownPearl(Request request, Vec3d targetRay) {
        CandidatePool top = new CandidatePool();
        if (request.completedPearlTicks() != 0) return List.of();

        List<Vec3d> lateralOffsets = crosshairOffsets(targetRay, request.maxCrosshairDistance());
        for (int catchTick = 2; catchTick <= request.maxPredictionTicks(); catchTick++) {
            Vec3d targetOrigin = predictedTargetOrigin(request, catchTick);
            for (double fraction : PEARL_SEGMENT_FRACTIONS) {
                PointCoefficients coefficients = pearlPointCoefficients(catchTick, fraction);
                for (Vec3d lateralOffset : lateralOffsets) {
                    Vec3d targetBase = targetOrigin.add(lateralOffset);
                    for (double range : positivePearlRanges(request, targetRay, targetBase, coefficients)) {
                        if (range < 0.25 || range > request.maxSearchDistance()) continue;
                        Vec3d targetPoint = targetBase.add(targetRay.scale(range));
                        Vec3d requiredLaunch = targetPoint.subtract(request.pearlLaunchPosition())
                                .subtract(coefficients.gravityDisplacement())
                                .scale(1.0 / coefficients.velocityScale());
                        Vec3d shooting = requiredLaunch.subtract(request.pearlInheritedMotion());
                        if (Math.abs(shooting.length() - VanillaProjectilePhysics.PROJECTILE_POWER) > 1.0e-6) continue;
                        Rotation pearlRotation = VanillaProjectilePhysics.rotationForDirection(shooting);
                        Vec3d nominalPearlLaunch = VanillaProjectilePhysics.nominalLaunchVelocity(
                                pearlRotation, request.pearlInheritedMotion());
                        // Candidate generation never reads beyond the proposed catch tick.
                        // Robustness sampling simulates its own perturbed path later, so extending every nominal
                        // candidate to the global prediction horizon here only allocates and advances unused states.
                        List<Vec3d> pearlPath = simulatePearl(
                                request.pearlLaunchPosition(), nominalPearlLaunch, catchTick);

                        evaluateWindDelays(request, targetRay, pearlRotation, pearlPath, catchTick, top);
                    }
                }
            }
        }
        return top.values();
    }

    private static List<Candidate> solveKnownPearl(Request request, Vec3d targetRay) {
        CandidatePool top = new CandidatePool();
        List<Vec3d> pearlPath = simulatePearl(
                request.pearlLaunchPosition(), request.knownPearlLaunchVelocity(), request.maxPredictionTicks());
        int firstPossible = Math.max(1, request.completedPearlTicks() + 1);
        for (int catchTick = firstPossible; catchTick <= request.maxPredictionTicks(); catchTick++) {
            Vec3d from = pearlPath.get(catchTick - 1);
            Vec3d to = pearlPath.get(catchTick);
            Vec3d targetOrigin = predictedTargetOrigin(request, catchTick);
            if (segmentToRayMinDistance(targetOrigin, targetRay, from, to) > request.maxCrosshairDistance() + 0.75) {
                continue;
            }
            evaluateWindDelays(request, targetRay, null, pearlPath, catchTick, top);
        }
        return top.values();
    }

    private static int timingSearchLimit(Request request) {
        // Timing is a solver variable, not a caller-selected mode. Nearby catches need only a few delay ticks;
        // farther targets may need the wind to chase a pearl that has already slowed under drag/gravity.
        int distanceDriven = Math.max(4, (int)Math.ceil(request.preferredCatchDistance() / 3.0));
        return Math.min(request.maxPredictionTicks() - request.completedPearlTicks() - 1, Math.min(32, distanceDriven));
    }

    private static void evaluateWindDelays(
            Request request,
            Vec3d targetRay,
            Rotation pearlRotation,
            List<Vec3d> pearlPath,
            int catchTick,
            CandidatePool top
    ) {
        int maxDelay = Math.min(timingSearchLimit(request), catchTick - request.completedPearlTicks() - 1);
        for (int delay = Math.max(0, request.minimumWindDelayTicks()); delay <= maxDelay; delay++) {
            int firstWindPearlSegment = request.completedPearlTicks() + delay + 1;
            if (firstWindPearlSegment > catchTick) continue;
            int windCompletedTicks = catchTick - firstWindPearlSegment;
            Vec3d predictedWindStart = request.currentEyePosition().add(request.currentInheritedMotion().scale(delay));
            List<WindSolution> winds = deriveWindSolutions(
                    request, pearlPath.get(catchTick - 1), pearlPath.get(catchTick), catchTick,
                    firstWindPearlSegment, windCompletedTicks, predictedWindStart);

            for (WindSolution wind : winds) {
                if (firstCollision(pearlPath, predictedWindStart, wind.velocity(), firstWindPearlSegment,
                        catchTick - 1) != null) continue;

                Aabb3d.Clip plannedClip = VanillaProjectilePhysics.windChargeBox(
                                wind.positionAtCatch(), VanillaProjectilePhysics.collisionMargin(catchTick - 1))
                        .clipEntry(pearlPath.get(catchTick - 1), pearlPath.get(catchTick));
                if (!plannedClip.hit()) continue;

                Vec3d targetOrigin = predictedTargetOrigin(request, catchTick);
                RayMetrics ray = rayMetrics(targetOrigin, targetRay, plannedClip.point());
                if (ray.along() < 0.25 || ray.along() > request.maxSearchDistance()) continue;
                if (ray.distance() > request.maxCrosshairDistance()) continue;

                double targetError = Math.abs(ray.along() - request.preferredCatchDistance());
                double score = targetError * 12.0
                        + ray.distance() * 24.0
                        + delay * 0.35
                        + catchTick * 0.015;
                if (pearlRotation != null) {
                    score += angularDistance(request.targetRotation(), pearlRotation) * 0.002;
                }
                score += angularDistance(request.targetRotation(), wind.rotation()) * 0.001;

                Candidate candidate = new Candidate(
                        pearlRotation,
                        wind.rotation(),
                        delay,
                        catchTick,
                        windCompletedTicks,
                        firstWindPearlSegment,
                        plannedClip.point(),
                        wind.positionAtCatch(),
                        ray.distance(),
                        ray.along(),
                        targetError,
                        0.0,
                        score,
                        Double.POSITIVE_INFINITY,
                        pearlPath,
                        predictedWindStart,
                        wind.velocity(),
                        request,
                        0.0,
                        Double.POSITIVE_INFINITY
                );
                addTop(top, candidate);
            }
        }
    }

    private static List<WindSolution> deriveWindSolutions(
            Request request,
            Vec3d pearlFrom,
            Vec3d pearlTo,
            int catchTick,
            int firstWindPearlSegment,
            int windCompletedTicks,
            Vec3d windStart
    ) {
        List<WindSolution> out = new ArrayList<>();
        double margin = VanillaProjectilePhysics.collisionMargin(catchTick - 1);
        if (windCompletedTicks == 0) {
            Aabb3d.Clip clip = VanillaProjectilePhysics.windChargeBox(windStart, margin).clipEntry(pearlFrom, pearlTo);
            if (clip.hit()) {
                Rotation rotation = request.targetRotation();
                Vec3d velocity = VanillaProjectilePhysics.nominalLaunchVelocity(rotation, request.currentInheritedMotion());
                out.add(new WindSolution(rotation, velocity, windStart));
            }
            return out;
        }

        Vec3d sphereOrigin = windStart
                .add(request.currentInheritedMotion().scale(windCompletedTicks))
                .add(WIND_BOX_CENTER_OFFSET);
        double radius = VanillaProjectilePhysics.PROJECTILE_POWER * windCompletedTicks;
        double halfX = VanillaProjectilePhysics.WIND_WIDTH * 0.5 + margin;
        double halfY = VanillaProjectilePhysics.WIND_HEIGHT * 0.5 + margin;
        List<Vec3d> faceOffsets = boxBoundaryOffsets(halfX, halfY);
        Vec3d segment = pearlTo.subtract(pearlFrom);

        for (Vec3d faceOffset : faceOffsets) {
            // If Q is the point where the pearl crosses an AABB face, the AABB center is Q + faceOffset.
            // Wind centers reachable after m completed ticks lie on the sphere:
            // |(pearlFrom + t*segment + faceOffset) - sphereOrigin| = 1.5*m.
            Vec3d p = pearlFrom.add(faceOffset).subtract(sphereOrigin);
            double qa = segment.dot(segment);
            double qb = 2.0 * p.dot(segment);
            double qc = p.dot(p) - radius * radius;
            for (double t : quadraticUnitIntervalRoots(qa, qb, qc)) {
                Vec3d facePoint = pearlFrom.add(segment.scale(t));
                Vec3d boxCenter = facePoint.add(faceOffset);
                Vec3d direction = boxCenter.subtract(sphereOrigin).normalize();
                if (direction.lengthSquared() < 1.0e-16) continue;
                Vec3d windPosition = boxCenter.subtract(WIND_BOX_CENTER_OFFSET);
                Aabb3d.Clip clip = VanillaProjectilePhysics.windChargeBox(windPosition, margin)
                        .clipEntry(pearlFrom, pearlTo);
                if (!clip.hit()) continue;

                Rotation rotation = VanillaProjectilePhysics.rotationForDirection(direction);
                Vec3d velocity = VanillaProjectilePhysics.nominalLaunchVelocity(rotation, request.currentInheritedMotion());
                Vec3d recomputed = windStart.add(velocity.scale(windCompletedTicks));
                if (recomputed.distanceTo(windPosition) > 1.0e-6) continue;
                out.add(new WindSolution(rotation, velocity, windPosition));
            }
        }
        return out;
    }

    private static List<Vec3d> boxBoundaryOffsets(double halfXZ, double halfY) {
        List<Vec3d> offsets = new ArrayList<>(26);
        double[] xs = {-halfXZ, 0.0, halfXZ};
        double[] ys = {-halfY, 0.0, halfY};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : xs) {
                    if (Math.abs(x) < 1.0e-12 && Math.abs(y) < 1.0e-12 && Math.abs(z) < 1.0e-12) continue;
                    offsets.add(new Vec3d(x, y, z));
                }
            }
        }
        return offsets;
    }

    private static List<Double> quadraticUnitIntervalRoots(double a, double b, double c) {
        if (a <= 1.0e-18) return List.of();
        double d = b * b - 4.0 * a * c;
        if (d < -1.0e-10) return List.of();
        d = Math.max(0.0, d);
        double sqrt = Math.sqrt(d);
        double t1 = (-b - sqrt) / (2.0 * a);
        double t2 = (-b + sqrt) / (2.0 * a);
        boolean ok1 = t1 > 1.0e-8 && t1 < 1.0 - 1.0e-8;
        boolean ok2 = t2 > 1.0e-8 && t2 < 1.0 - 1.0e-8;
        if (ok1 && ok2 && Math.abs(t1 - t2) > 1.0e-9) return List.of(t1, t2);
        if (ok1) return List.of(t1);
        if (ok2) return List.of(t2);
        return List.of();
    }

    private static List<Double> positivePearlRanges(
            Request request, Vec3d targetRay, Vec3d targetBase, PointCoefficients coefficients
    ) {
        double s = coefficients.velocityScale();
        Vec3d base = targetBase.subtract(request.pearlLaunchPosition())
                .subtract(coefficients.gravityDisplacement())
                .scale(1.0 / s)
                .subtract(request.pearlInheritedMotion());
        Vec3d q = targetRay.scale(1.0 / s);
        double qa = q.dot(q);
        double qb = 2.0 * base.dot(q);
        double qc = base.dot(base) - VanillaProjectilePhysics.PROJECTILE_POWER * VanillaProjectilePhysics.PROJECTILE_POWER;
        double discriminant = qb * qb - 4.0 * qa * qc;
        if (discriminant < -1.0e-10 || qa <= 1.0e-18) return List.of();
        discriminant = Math.max(0.0, discriminant);
        double sqrt = Math.sqrt(discriminant);
        double r1 = (-qb + sqrt) / (2.0 * qa);
        double r2 = (-qb - sqrt) / (2.0 * qa);
        if (r1 > 0.0 && r2 > 0.0 && Math.abs(r1 - r2) > 1.0e-9) return List.of(r1, r2);
        if (r1 > 0.0) return List.of(r1);
        if (r2 > 0.0) return List.of(r2);
        return List.of();
    }

    private static List<Vec3d> crosshairOffsets(Vec3d ray, double maxCrosshairDistance) {
        List<Vec3d> offsets = new ArrayList<>();
        offsets.add(Vec3d.ZERO);
        if (maxCrosshairDistance <= 1.0e-9) return offsets;

        Vec3d reference = Math.abs(ray.y()) < 0.9 ? new Vec3d(0.0, 1.0, 0.0) : new Vec3d(1.0, 0.0, 0.0);
        Vec3d u = cross(ray, reference).normalize();
        Vec3d v = cross(ray, u).normalize();
        double[] radii = {Math.min(0.05, maxCrosshairDistance), Math.min(0.15, maxCrosshairDistance)};
        for (double radius : radii) {
            if (radius <= 1.0e-9) continue;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0;
                offsets.add(u.scale(Math.cos(angle) * radius).add(v.scale(Math.sin(angle) * radius)));
            }
        }
        return offsets;
    }

    private static Vec3d cross(Vec3d a, Vec3d b) {
        return new Vec3d(
                a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x()
        );
    }

    private static PointCoefficients pearlPointCoefficients(int tick, double fraction) {
        ScalarAndGravity before = pearlEndCoefficients(tick - 1);
        ScalarAndGravity after = pearlEndCoefficients(tick);
        double velocityScale = before.velocityScale() * (1.0 - fraction) + after.velocityScale() * fraction;
        Vec3d gravity = before.gravityDisplacement().scale(1.0 - fraction)
                .add(after.gravityDisplacement().scale(fraction));
        return new PointCoefficients(velocityScale, gravity);
    }

    private static ScalarAndGravity pearlEndCoefficients(int ticks) {
        if (ticks <= 0) return new ScalarAndGravity(0.0, Vec3d.ZERO);
        double a = VanillaProjectilePhysics.PEARL_AIR_INERTIA;
        double aPow = Math.pow(a, ticks);
        double velocityScale = a * (1.0 - aPow) / (1.0 - a);
        double gravityScale = a / (1.0 - a) * (ticks - velocityScale);
        return new ScalarAndGravity(velocityScale,
                new Vec3d(0.0, -VanillaProjectilePhysics.PEARL_GRAVITY * gravityScale, 0.0));
    }

    private static Candidate withClearance(Candidate candidate) {
        int tick = candidate.pearlCatchTick();
        Aabb3d box = VanillaProjectilePhysics.windChargeBox(
                candidate.windPositionAtCatch(), VanillaProjectilePhysics.collisionMargin(tick - 1));
        double clearance = box.segmentInteriorClearance(
                candidate.pearlPath().get(tick - 1), candidate.pearlPath().get(tick));
        double deficit = Math.max(0.0, MIN_GEOMETRIC_CLEARANCE - clearance);
        double adjusted = candidate.score()
                + deficit * 1500.0
                - Math.min(clearance, 0.15) * 2.0;
        return candidate.withClearance(clearance, adjusted);
    }

    private static Candidate withRobustness(Candidate candidate, int samples) {
        return withRobustness(candidate, 0, samples, false);
    }

    private static Candidate withRobustness(
            Candidate candidate, int alreadySampled, int samples, boolean executableOnly
    ) {
        Request request = candidate.request();
        if (samples <= 0) return candidate.withRobustness(1.0, candidate.clearanceScore());

        int prefix = Math.max(0, Math.min(alreadySampled, samples));
        int hits = prefix == 0 ? 0 : (int)Math.round(candidate.robustHitFraction() * prefix);
        int requiredHits = (int)Math.ceil(MIN_ROBUST_HIT_FRACTION * samples - 1.0e-12);
        int throughTick = Math.min(request.maxPredictionTicks(), candidate.pearlCatchTick() + 2);
        double distanceTolerance = Math.max(2.0, request.preferredCatchDistance() * 0.15);
        Vec3d targetRay = VanillaProjectilePhysics.lookDirection(request.targetRotation());

        for (int i = prefix; i < samples; i++) {
            Vec3d pearlVelocity = request.knownPearlLaunchVelocity() != null
                    ? request.knownPearlLaunchVelocity()
                    : VanillaProjectilePhysics.perturbedLaunchVelocity(
                            candidate.pearlRotation(), request.pearlInheritedMotion(), VanillaSpreadSampler.perturbation(i, 0));
            List<Vec3d> pearlPath = simulatePearl(request.pearlLaunchPosition(), pearlVelocity, throughTick);
            Vec3d windVelocity = VanillaProjectilePhysics.perturbedLaunchVelocity(
                    candidate.windRotation(), request.currentInheritedMotion(), VanillaSpreadSampler.perturbation(i, 1));
            Collision collision = firstCollision(pearlPath, candidate.windStart(), windVelocity,
                    candidate.firstWindPearlSegment(), throughTick);
            if (collision != null) {
                Vec3d targetOrigin = predictedTargetOrigin(request, collision.tick());
                RayMetrics ray = rayMetrics(targetOrigin, targetRay, collision.point());
                if (ray.distance() <= request.maxCrosshairDistance()
                        && ray.along() >= 0.25 && ray.along() <= request.maxSearchDistance()
                        && Math.abs(ray.along() - request.preferredCatchDistance()) <= distanceTolerance) {
                    hits++;
                }
            }

            if (executableOnly && hits + (samples - i - 1) < requiredHits) {
                double fraction = (double)hits / samples;
                double penalty = (1.0 - fraction) * (1.0 - fraction) * 15.0;
                return candidate.withRobustness(fraction, candidate.clearanceScore() + penalty);
            }
        }
        double fraction = (double)hits / samples;
        double reliabilityPenalty = (1.0 - fraction) * (1.0 - fraction) * 15.0;
        return candidate.withRobustness(fraction, candidate.clearanceScore() + reliabilityPenalty);
    }

    private static Vec3d predictedTargetOrigin(Request request, int absolutePearlTick) {
        int futureTicks = Math.max(0, absolutePearlTick - request.completedPearlTicks());
        return request.currentEyePosition().add(request.currentInheritedMotion().scale(futureTicks));
    }

    private static List<Vec3d> simulatePearl(Vec3d start, Vec3d launchVelocity, int ticks) {
        List<Vec3d> path = new ArrayList<>(ticks + 1);
        Vec3d position = start;
        Vec3d velocity = launchVelocity;
        path.add(position);
        for (int i = 0; i < ticks; i++) {
            velocity = VanillaProjectilePhysics.pearlVelocityAfterTick(velocity);
            position = position.add(velocity);
            path.add(position);
        }
        return path;
    }

    private static Collision firstCollision(
            List<Vec3d> pearlPath,
            Vec3d windStart,
            Vec3d windVelocity,
            int firstWindPearlSegment,
            int throughPearlTick
    ) {
        int maxTick = Math.min(throughPearlTick, pearlPath.size() - 1);
        if (firstWindPearlSegment < 1 || maxTick < firstWindPearlSegment) return null;
        for (int tick = firstWindPearlSegment; tick <= maxTick; tick++) {
            int windTicks = tick - firstWindPearlSegment;
            Vec3d windPosition = windStart.add(windVelocity.scale(windTicks));
            Aabb3d.Clip clip = VanillaProjectilePhysics.windChargeBox(
                            windPosition, VanillaProjectilePhysics.collisionMargin(tick - 1))
                    .clipEntry(pearlPath.get(tick - 1), pearlPath.get(tick));
            if (clip.hit()) return new Collision(tick, clip.point());
        }
        return null;
    }

    /**
     * Conservative hazard check for a previously-existing wind charge. Existing projectiles have no pairing
     * ownership server-side, so a new pearl must not cross an older wind AABB before its intended catch.
     * Both the pre-move and post-move wind positions for each future tick are checked to avoid depending on
     * client-side entity iteration order.
     */
    public static boolean pathHitsExistingWind(
            Vec3d pearlStart,
            Vec3d pearlLaunchVelocity,
            Vec3d windPositionNow,
            Vec3d windVelocity,
            int throughPearlTick
    ) {
        if (pearlStart == null || pearlLaunchVelocity == null || windPositionNow == null || windVelocity == null) return false;
        if (throughPearlTick < 1) return false;
        Vec3d previousPearl = pearlStart;
        for (int tick = 1; tick <= throughPearlTick; tick++) {
            Vec3d currentPearl = VanillaProjectilePhysics.pearlPositionAfterTicks(pearlStart, pearlLaunchVelocity, tick);
            double margin = VanillaProjectilePhysics.collisionMargin(tick - 1);
            Vec3d windBefore = windPositionNow.add(windVelocity.scale(tick - 1));
            Vec3d windAfter = windPositionNow.add(windVelocity.scale(tick));
            if (VanillaProjectilePhysics.windChargeBox(windBefore, margin).clipEntry(previousPearl, currentPearl).hit()
                    || VanillaProjectilePhysics.windChargeBox(windAfter, margin).clipEntry(previousPearl, currentPearl).hit()) {
                return true;
            }
            previousPearl = currentPearl;
        }
        return false;
    }

    private static Plan toPlan(Candidate c) {
        return new Plan(
                c.pearlRotation(),
                c.windRotation(),
                c.windDelayTicksFromNow(),
                c.pearlCatchTick(),
                c.windCompletedTicksAtCatch(),
                c.firstWindPearlSegment(),
                c.interceptPoint(),
                c.windPositionAtCatch(),
                c.crosshairDistance(),
                c.crosshairRange(),
                c.targetDistanceError(),
                c.collisionClearance(),
                c.pearlCatchTick(),
                c.robustHitFraction(),
                c.robustScore(),
                c.request().knownPearlLaunchVelocity() != null
        );
    }

    private static void addTop(CandidatePool pool, Candidate candidate) {
        pool.add(candidate);
    }

    private static double angularDistance(Rotation a, Rotation b) {
        double yaw = Rotation.wrapYaw(b.yaw() - a.yaw());
        double pitch = b.pitch() - a.pitch();
        return Math.sqrt(yaw * yaw + pitch * pitch);
    }

    private static double segmentToRayMinDistance(Vec3d eye, Vec3d ray, Vec3d a, Vec3d b) {
        Vec3d segment = b.subtract(a);
        double best = Math.min(rayDistanceSquared(eye, ray, a), rayDistanceSquared(eye, ray, b));
        for (int i = 1; i < 16; i++) {
            Vec3d p = a.add(segment.scale(i / 16.0));
            best = Math.min(best, rayDistanceSquared(eye, ray, p));
        }
        return Math.sqrt(best);
    }

    private static double rayDistanceSquared(Vec3d eye, Vec3d ray, Vec3d point) {
        Vec3d delta = point.subtract(eye);
        double along = delta.dot(ray);
        if (along <= 0.0) return delta.dot(delta);
        Vec3d perpendicular = delta.subtract(ray.scale(along));
        return perpendicular.dot(perpendicular);
    }

    private static RayMetrics rayMetrics(Vec3d eye, Vec3d ray, Vec3d point) {
        Vec3d delta = point.subtract(eye);
        double along = delta.dot(ray);
        Vec3d nearest = eye.add(ray.scale(Math.max(0.0, along)));
        return new RayMetrics(point.distanceTo(nearest), along);
    }

    public record Request(
            Vec3d pearlLaunchPosition,
            Vec3d pearlInheritedMotion,
            Vec3d knownPearlLaunchVelocity,
            int completedPearlTicks,
            Vec3d currentEyePosition,
            Vec3d currentInheritedMotion,
            Rotation targetRotation,
            int maxPredictionTicks,
            double maxSearchDistance,
            double maxCrosshairDistance,
            double preferredCatchDistance,
            int spreadSamples,
            int minimumWindDelayTicks
    ) {}

    public record Plan(
            Rotation pearlRotation,
            Rotation windRotation,
            int windDelayTicksFromNow,
            int pearlCatchTick,
            int windCompletedTicksAtCatch,
            int firstWindPearlSegment,
            Vec3d interceptPoint,
            Vec3d windPositionAtCatch,
            double crosshairDistance,
            double crosshairRange,
            double targetDistanceError,
            double collisionClearance,
            int firstCollisionTick,
            double robustHitFraction,
            double score,
            boolean pearlLaunchKnown
    ) {}

    private record Candidate(
            Rotation pearlRotation,
            Rotation windRotation,
            int windDelayTicksFromNow,
            int pearlCatchTick,
            int windCompletedTicksAtCatch,
            int firstWindPearlSegment,
            Vec3d interceptPoint,
            Vec3d windPositionAtCatch,
            double crosshairDistance,
            double crosshairRange,
            double targetDistanceError,
            double collisionClearance,
            double score,
            double clearanceScore,
            List<Vec3d> pearlPath,
            Vec3d windStart,
            Vec3d windVelocity,
            Request request,
            double robustHitFraction,
            double robustScore
    ) {
        Candidate withClearance(double clearance, double newClearanceScore) {
            return new Candidate(pearlRotation, windRotation, windDelayTicksFromNow, pearlCatchTick,
                    windCompletedTicksAtCatch, firstWindPearlSegment, interceptPoint, windPositionAtCatch,
                    crosshairDistance, crosshairRange, targetDistanceError, clearance, score, newClearanceScore, pearlPath, windStart,
                    windVelocity, request, robustHitFraction, robustScore);
        }

        Candidate withRobustness(double fraction, double newScore) {
            return new Candidate(pearlRotation, windRotation, windDelayTicksFromNow, pearlCatchTick,
                    windCompletedTicksAtCatch, firstWindPearlSegment, interceptPoint, windPositionAtCatch,
                    crosshairDistance, crosshairRange, targetDistanceError, collisionClearance, score, clearanceScore, pearlPath, windStart,
                    windVelocity, request, fraction, newScore);
        }
    }

    private static final class CandidatePool {
        private final Map<Long, List<Candidate>> buckets = new HashMap<>();

        void add(Candidate candidate) {
            long key = (((long)candidate.pearlCatchTick()) << 32) ^ (candidate.windDelayTicksFromNow() & 0xffffffffL);
            List<Candidate> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (bucket.size() < PER_TIMING_CANDIDATES) {
                bucket.add(candidate);
                return;
            }
            int worstIndex = 0;
            double worstScore = bucket.get(0).score();
            for (int i = 1; i < bucket.size(); i++) {
                double score = bucket.get(i).score();
                if (score > worstScore) {
                    worstScore = score;
                    worstIndex = i;
                }
            }
            if (candidate.score() < worstScore) bucket.set(worstIndex, candidate);
        }

        List<Candidate> values() {
            List<Candidate> out = new ArrayList<>();
            for (List<Candidate> bucket : buckets.values()) out.addAll(bucket);
            return out;
        }
    }

    private record WindSolution(Rotation rotation, Vec3d velocity, Vec3d positionAtCatch) {}
    private record Collision(int tick, Vec3d point) {}
    private record RayMetrics(double distance, double along) {}
    private record PointCoefficients(double velocityScale, Vec3d gravityDisplacement) {}
    private record ScalarAndGravity(double velocityScale, Vec3d gravityDisplacement) {}
}
