package studio.pixelied.pearlcatch.core;

/**
 * Deterministic low-discrepancy samples of Minecraft's RandomSource#triangle(0, deviation).
 *
 * <p>RandomSource#triangle is deviation * (U - V), with independent U/V uniform doubles.
 * Different stream ids use disjoint Halton dimensions so paired pearl/wind samples approximate
 * independent vanilla launch noise while remaining perfectly reproducible in tests and scoring.</p>
 */
public final class VanillaSpreadSampler {
    public static final int DEFAULT_SAMPLE_COUNT = 128;
    private static final int[] BASES = {
            2, 3, 5, 7, 11, 13,
            17, 19, 23, 29, 31, 37,
            41, 43, 47, 53, 59, 61
    };

    private VanillaSpreadSampler() {}

    public static Vec3d perturbation(int sampleIndex, int stream) {
        if (sampleIndex < 0) throw new IllegalArgumentException("sampleIndex must be >= 0");
        if (stream < 0 || stream > 2) throw new IllegalArgumentException("stream must be 0..2");
        int n = sampleIndex + 1;
        int offset = stream * 6;
        double d = VanillaProjectilePhysics.RANDOM_TRIANGLE_DEVIATION;
        return new Vec3d(
                d * (radicalInverse(n, BASES[offset]) - radicalInverse(n, BASES[offset + 1])),
                d * (radicalInverse(n, BASES[offset + 2]) - radicalInverse(n, BASES[offset + 3])),
                d * (radicalInverse(n, BASES[offset + 4]) - radicalInverse(n, BASES[offset + 5]))
        );
    }

    private static double radicalInverse(int n, int base) {
        double inverseBase = 1.0 / base;
        double factor = inverseBase;
        double result = 0.0;
        while (n > 0) {
            result += (n % base) * factor;
            n /= base;
            factor *= inverseBase;
        }
        return result;
    }
}
