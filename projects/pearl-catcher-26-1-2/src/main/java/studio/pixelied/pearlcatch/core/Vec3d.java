package studio.pixelied.pearlcatch.core;

public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);

    public Vec3d add(Vec3d other) { return new Vec3d(x + other.x, y + other.y, z + other.z); }
    public Vec3d add(double dx, double dy, double dz) { return new Vec3d(x + dx, y + dy, z + dz); }
    public Vec3d subtract(Vec3d other) { return new Vec3d(x - other.x, y - other.y, z - other.z); }
    public Vec3d scale(double s) { return new Vec3d(x * s, y * s, z * s); }
    public double dot(Vec3d other) { return x * other.x + y * other.y + z * other.z; }
    public double lengthSquared() { return dot(this); }
    public double length() { return Math.sqrt(lengthSquared()); }
    public double distanceTo(Vec3d other) { return subtract(other).length(); }
    public Vec3d normalize() {
        double len = length();
        return len < 1.0e-12 ? ZERO : scale(1.0 / len);
    }
}
