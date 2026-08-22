package dev.adrien.crystaloptimizer.client;

import dev.adrien.crystaloptimizer.client.v2.ClientStrategicPlannerService;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientStrategicPlannerServiceTest {
    @Test
    void onlyNewestSubmissionMayPublishEvenWhenOlderWorkFinishesFirst() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);

        try (ClientStrategicPlannerService service = new ClientStrategicPlannerService((snapshot, config) -> {
            if (snapshot.snapshotId() == 1L) {
                firstStarted.countDown();
                try {
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            } else if (snapshot.snapshotId() == 2L) {
                secondFinished.countDown();
            }
            return StrategicPlannerServiceFixtures.result(snapshot);
        })) {
            StrategicSnapshot first = StrategicPlannerServiceFixtures.snapshot(1L);
            StrategicSnapshot second = StrategicPlannerServiceFixtures.snapshot(2L);
            service.submit(first, OptimizerConfig.defaults());
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

            service.submit(second, OptimizerConfig.defaults());
            releaseFirst.countDown();
            assertTrue(secondFinished.await(5, TimeUnit.SECONDS));

            var latest = service.pollLatest().orElseThrow();
            assertEquals(2L, latest.snapshotId());
            assertTrue(service.pollLatest().isEmpty());
        }
    }
}
