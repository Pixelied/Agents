package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class StrategicEpochCacheTest {
    @Test
    void exactMapIsBuiltOncePerTargetPerEpoch() {
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000006101");
        AtomicInteger builds = new AtomicInteger();
        StrategicEpoch epoch = new StrategicEpoch(targetId -> {
            builds.incrementAndGet();
            return DamageMap.empty(targetId, 4L, 9L);
        });

        DamageMap first = epoch.damageMap(target);
        DamageMap second = epoch.damageMap(target);

        assertSame(first, second);
        assertEquals(1, builds.get());
        assertEquals(1, epoch.buildCount(target));
        assertEquals(Map.of(target, 1), epoch.buildCounts());
    }
}
