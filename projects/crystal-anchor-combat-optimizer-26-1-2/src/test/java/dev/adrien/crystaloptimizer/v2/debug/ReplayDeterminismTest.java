package dev.adrien.crystaloptimizer.v2.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ReplayDeterminismTest {
    @Test
    void replayRoundTripProducesSameChosenAction() throws Exception {
        ReplayFixture fixture = V3ReplayFixtures.popThenFinisher();
        ReplayCodec codec = new ReplayCodec();
        ReplayRunner runner = new ReplayRunner();

        byte[] encoded = codec.encode(fixture);
        ReplayFixture decoded = codec.decode(encoded);

        ReplayResult before = runner.run(fixture);
        ReplayResult after = runner.run(decoded);
        assertEquals(before.chosenDecisionKey(), after.chosenDecisionKey());
        assertEquals(before.decisionClass(), after.decisionClass());
        assertEquals(before.targetId(), after.targetId());
        assertFalse(before.chosenDecisionKey().isBlank());
    }

    @Test
    void replayEventNormalizesFieldAndEventOrdering() {
        ReplayEvent later = new ReplayEvent(
            20L,
            "combat.target_moved",
            Map.of("z", "3", "a", "1")
        );
        ReplayEvent earlier = new ReplayEvent(
            10L,
            "control.tick",
            Map.of("phase", "strategic")
        );
        ReplayFixture base = V3ReplayFixtures.popThenFinisher();
        ReplayFixture fixture = new ReplayFixture(
            base.snapshot(),
            base.config(),
            List.of(later, earlier)
        );

        assertEquals(List.of("a", "z"), new ArrayList<>(later.fields().keySet()));
        assertEquals(10L, fixture.events().getFirst().relativeNanos());
        assertEquals(20L, fixture.events().getLast().relativeNanos());
        assertThrows(IllegalArgumentException.class,
            () -> new ReplayEvent(-1L, "control.tick", Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new ReplayEvent(0L, " ", Map.of()));
    }

    @Test
    void decisionTraceBufferIsBoundedAndKeepsNewestEntries() {
        DecisionTraceBuffer buffer = new DecisionTraceBuffer(2);
        buffer.add(DecisionTrace.minimal("first"));
        buffer.add(DecisionTrace.minimal("second"));
        buffer.add(DecisionTrace.minimal("third"));

        assertEquals(List.of("second", "third"), buffer.snapshot().stream()
            .map(DecisionTrace::chosenDecisionKey)
            .toList());
    }
}
