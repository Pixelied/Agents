package dev.adrien.crystaloptimizer.v2.debug;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ReplayResourceTest {
    private static final List<String> FIXTURES = List.of(
        "candidate-budget-anchor-starvation.json",
        "fourth-target-lethal.json",
        "protected-window-single-application.json",
        "break-remove-place-continuation.json",
        "predicted-strafe-placement.json"
    );

    @Test
    void checkedInRegressionReplaysLoadAndRemainDeterministic() throws Exception {
        ReplayCodec codec = new ReplayCodec();
        ReplayRunner runner = new ReplayRunner();
        for (String name : FIXTURES) {
            ReplayFixture fixture = codec.readResource("replays/v3/" + name);
            ReplayResult first = runner.run(fixture);
            ReplayFixture roundTrip = codec.decode(codec.encode(fixture));
            ReplayResult second = runner.run(roundTrip);

            assertEquals(first, second, name + " changed after replay round-trip");
            assertFalse(first.chosenDecisionKey().isBlank(), name + " produced a blank decision key");
            assertFalse(first.decisionClass().isBlank(), name + " produced a blank decision class");
        }
    }
}
