package dev.adrien.crystaloptimizer.v2.damage;

import dev.adrien.crystaloptimizer.v2.diagnostics.TimeToDamageTrace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageCalibrationTest {
    @Test
    void outOfIntervalObservedDamageProducesMismatchWithoutCorrectionState() {
        DamageCalibration calibration = new DamageCalibration();
        DamageEstimate estimate = new DamageEstimate(
            14.0f, 16.0f, 18.0f, 0.9, Set.of(), 7L, 9L
        );
        calibration.observePrediction(
            44L,
            TimeToDamageTrace.dispatched(44L, 100L, 110L, 120L, estimate)
        );

        DamageMismatch mismatch = calibration.observeResult(
            44L,
            new ObservedDamageResult(5.0f, false, false, 9L)
        ).orElseThrow();

        assertNotEquals(DamageMismatch.Kind.NONE, mismatch.kind());
        assertEquals(44L, mismatch.actionId());
        assertTrue(mismatch.error() > 0.0f);
        assertFalse(calibration.observeResult(
            44L,
            new ObservedDamageResult(5.0f, false, false, 9L)
        ).isPresent(), "prediction must be evicted after one attributable result");
    }

    @Test
    void knownUncertaintyDrivesMismatchTaxonomy() {
        DamageCalibration calibration = new DamageCalibration();
        DamageEstimate estimate = new DamageEstimate(
            10.0f,
            14.0f,
            18.0f,
            0.5,
            Set.of(DamageUncertainty.HURT_THRESHOLD_UNKNOWN),
            3L,
            4L
        );
        calibration.observePrediction(
            7L,
            TimeToDamageTrace.dispatched(7L, 1L, 2L, 3L, estimate)
        );

        DamageMismatch mismatch = calibration.observeResult(
            7L,
            new ObservedDamageResult(3.0f, false, false, 4L)
        ).orElseThrow();
        assertEquals(DamageMismatch.Kind.HURT_THRESHOLD_UNKNOWN, mismatch.kind());
    }

    @Test
    void calibrationSourceContainsNoDamageFudgeState() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageCalibration.java"
        )).toLowerCase();
        assertFalse(source.contains("damagemultiplier"));
        assertFalse(source.contains("damageoffset"));
        assertFalse(source.contains("fudge"));
    }
}
