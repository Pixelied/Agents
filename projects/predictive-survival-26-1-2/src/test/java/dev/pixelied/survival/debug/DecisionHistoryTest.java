package dev.pixelied.survival.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionHistoryTest {
    @Test
    void decisionHistoryIsBounded() {
        DecisionHistory history = new DecisionHistory(128);
        for (int i = 0; i < 300; i++) {
            history.add(new DecisionRecord(i, "threat-" + i, "action-" + i, "status-" + i, "reason-" + i));
        }

        assertEquals(128, history.snapshot().size());
        assertEquals(172L, history.snapshot().getFirst().tick());
        assertEquals(299L, history.snapshot().getLast().tick());
    }
}
