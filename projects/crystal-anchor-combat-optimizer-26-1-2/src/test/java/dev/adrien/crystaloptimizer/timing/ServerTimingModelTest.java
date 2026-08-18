package dev.adrien.crystaloptimizer.timing;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTimingModelTest {
    @Test
    void jitterLowersSameTickConfidence() {
        var stable = modelWithAckMillis(40, 41, 39, 40);
        var jittery = modelWithAckMillis(20, 95, 35, 120);

        TimingEstimate stableEstimate = stable.estimateBurst(1_000_000_000L, 2);
        TimingEstimate jitteryEstimate = jittery.estimateBurst(1_000_000_000L, 2);

        assertTrue(stableEstimate.sameTickProbability() > jitteryEstimate.sameTickProbability());
        assertTrue(stableEstimate.jitterMillis() < jitteryEstimate.jitterMillis());
    }

    @Test
    void ackSamplesAreMatchedToTheirOwnInteractionSequence() {
        var model = new ServerTimingModel(16);
        model.recordSend(7, 1_000_000_000L);
        model.recordSend(8, 1_010_000_000L);
        model.recordAck(8, 1_050_000_000L);
        model.recordAck(7, 1_080_000_000L);

        TimingEstimate estimate = model.estimateBurst(1_100_000_000L, 1);

        assertEquals(60.0, estimate.medianAckDelayMillis(), 0.0001);
        assertEquals(2, estimate.sampleCount());
    }

    @Test
    void placeThenAttackNewCrystalIsNotZeroFeedback() {
        var graph = PacketDependencyGraph.of(List.of(
            new DependencyEdge(
                "place-crystal",
                "attack-new-crystal",
                PacketDependency.SERVER_FEEDBACK_FOR_NEW_ENTITY
            )
        ));

        assertFalse(graph.zeroFeedbackCriticalPath());
        assertEquals(1, graph.feedbackBoundaries());
    }

    @Test
    void clientPredictedBlockEdgeRemainsZeroFeedbackButCostsReliability() {
        var graph = PacketDependencyGraph.of(List.of(
            new DependencyEdge(
                "place-anchor",
                "charge-anchor",
                PacketDependency.CLIENT_PREDICTION
            )
        ));
        TimingEstimate timing = modelWithAckMillis(40, 40, 41, 39)
            .estimateBurst(1_000_000_000L, 2);

        CompletionDistribution adjusted = graph.completionDistribution(timing);

        assertTrue(graph.zeroFeedbackCriticalPath());
        assertEquals(1, graph.clientPredictionEdges());
        assertTrue(adjusted.sameTickProbability() < timing.sameTickProbability());
        assertEquals(1.0, adjusted.totalProbability(), 1.0e-9);
    }

    private static ServerTimingModel modelWithAckMillis(int... delaysMillis) {
        var model = new ServerTimingModel(32);
        long base = 100_000_000L;
        for (int index = 0; index < delaysMillis.length; index++) {
            int sequence = index + 1;
            long sent = base + index * 200_000_000L;
            model.recordSend(sequence, sent);
            model.recordAck(sequence, sent + delaysMillis[index] * 1_000_000L);
        }
        return model;
    }
}
