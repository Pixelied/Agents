package dev.adrien.spearclient.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TargetScoringTest {
    @Test
    void directRayAlwaysBeatsAngleFallback() {
        TargetScore direct = new TargetScore(true, 0.15, 20.0);
        TargetScore angled = new TargetScore(false, 0.01, 4.0);

        assertTrue(direct.compareTo(angled) < 0);
    }

    @Test
    void angleFallbackPrefersSmallerAngleThenDistance() {
        assertTrue(new TargetScore(false, 0.05, 30.0)
            .compareTo(new TargetScore(false, 0.10, 3.0)) < 0);

        assertTrue(new TargetScore(false, 0.05, 4.0)
            .compareTo(new TargetScore(false, 0.05, 30.0)) < 0);
    }
}
