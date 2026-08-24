package dev.adrien.spearclient.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SequenceEvidenceTest {
    @Test
    void structuredLineSeparatesObservedResultFromSourcePredictions() {
        SequenceEvidence evidence = new SequenceEvidence(
            31L,
            "REACH",
            "corrected",
            7,
            3,
            new Vec3(1.0, 64.0, 2.0),
            9.0,
            18.0,
            Double.NaN,
            31.5,
            1
        );

        assertEquals(
            "sequence=31 kind=REACH result=corrected target=7 packets=3 "
                + "origin=1.000,64.000,2.000 maxRequestedDelta=9.000 "
                + "predictedKnownForward=18.000 predictedRawDamage=n/a "
                + "predictedReach=31.500 corrections=1",
            evidence.format()
        );
    }
}
