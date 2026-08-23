package studio.pixelied.pearlcatch.core;

public record Aabb3d(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    private static final double EPSILON = 1.0e-7;

    public Aabb3d inflate(double amount) {
        return new Aabb3d(minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount);
    }

    /**
     * Maximum guaranteed axis-aligned translation clearance of this box relative to a segment.
     *
     * <p>At a point on the segment, clearance is the minimum distance to any of the six box faces. The
     * minimum of those six affine functions is concave and piecewise linear, so its exact maximum over
     * t in [0,1] occurs at an endpoint or where two face-distance functions cross. A face/edge/corner
     * graze therefore returns zero, while a center crossing approaches the box half extent.</p>
     */
    public double segmentInteriorClearance(Vec3d from, Vec3d to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double[] intercept = {
                from.x() - minX, maxX - from.x(),
                from.y() - minY, maxY - from.y(),
                from.z() - minZ, maxZ - from.z()
        };
        double[] slope = {dx, -dx, dy, -dy, dz, -dz};

        double best = Math.max(clearanceAt(intercept, slope, 0.0), clearanceAt(intercept, slope, 1.0));
        for (int i = 0; i < intercept.length; i++) {
            for (int j = i + 1; j < intercept.length; j++) {
                double denominator = slope[i] - slope[j];
                if (Math.abs(denominator) <= 1.0e-15) continue;
                double t = (intercept[j] - intercept[i]) / denominator;
                if (t < 0.0 || t > 1.0) continue;
                best = Math.max(best, clearanceAt(intercept, slope, t));
            }
        }
        return Math.max(0.0, best);
    }

    private static double clearanceAt(double[] intercept, double[] slope, double t) {
        double clearance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < intercept.length; i++) {
            clearance = Math.min(clearance, intercept[i] + slope[i] * t);
        }
        return clearance;
    }

    /**
     * Mirrors Minecraft 26.1.2 AABB#clip semantics for projectile entity hits:
     * only an entry face with 0 < t < 1 counts. A segment starting inside has no entry and returns miss.
     */
    public Clip clipEntry(Vec3d from, Vec3d to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double best = 1.0;
        Vec3d hit = null;

        if (dx > EPSILON) {
            Clip c = face(from, dx, dy, dz, minX, 0, best);
            if (c.hit()) { best = c.t(); hit = c.point(); }
        } else if (dx < -EPSILON) {
            Clip c = face(from, dx, dy, dz, maxX, 0, best);
            if (c.hit()) { best = c.t(); hit = c.point(); }
        }

        if (dy > EPSILON) {
            Clip c = face(from, dy, dz, dx, minY, 1, best);
            if (c.hit()) { best = c.t(); hit = c.point(); }
        } else if (dy < -EPSILON) {
            Clip c = face(from, dy, dz, dx, maxY, 1, best);
            if (c.hit()) { best = c.t(); hit = c.point(); }
        }

        if (dz > EPSILON) {
            Clip c = face(from, dz, dx, dy, minZ, 2, best);
            if (c.hit()) { best = c.t(); hit = c.point(); }
        } else if (dz < -EPSILON) {
            Clip c = face(from, dz, dx, dy, maxZ, 2, best);
            if (c.hit()) { best = c.t(); hit = c.point(); }
        }

        return hit == null ? Clip.MISS : new Clip(true, best, hit);
    }

    private Clip face(Vec3d from, double da, double db, double dc, double plane, int axis, double best) {
        double fromA;
        double fromB;
        double fromC;
        double minB;
        double maxB;
        double minC;
        double maxC;

        if (axis == 0) {
            fromA = from.x(); fromB = from.y(); fromC = from.z();
            minB = minY; maxB = maxY; minC = minZ; maxC = maxZ;
        } else if (axis == 1) {
            fromA = from.y(); fromB = from.z(); fromC = from.x();
            minB = minZ; maxB = maxZ; minC = minX; maxC = maxX;
        } else {
            fromA = from.z(); fromB = from.x(); fromC = from.y();
            minB = minX; maxB = maxX; minC = minY; maxC = maxY;
        }

        double t = (plane - fromA) / da;
        if (!(0.0 < t && t < best)) return Clip.MISS;
        double pb = fromB + t * db;
        double pc = fromC + t * dc;
        if (!(minB - EPSILON < pb && pb < maxB + EPSILON && minC - EPSILON < pc && pc < maxC + EPSILON)) {
            return Clip.MISS;
        }
        Vec3d point = from.add((toAxisVector(axis, da, db, dc)).scale(t));
        return new Clip(true, t, point);
    }

    private static Vec3d toAxisVector(int axis, double da, double db, double dc) {
        return switch (axis) {
            case 0 -> new Vec3d(da, db, dc);
            case 1 -> new Vec3d(dc, da, db);
            default -> new Vec3d(db, dc, da);
        };
    }

    public record Clip(boolean hit, double t, Vec3d point) {
        public static final Clip MISS = new Clip(false, Double.NaN, null);
    }
}
