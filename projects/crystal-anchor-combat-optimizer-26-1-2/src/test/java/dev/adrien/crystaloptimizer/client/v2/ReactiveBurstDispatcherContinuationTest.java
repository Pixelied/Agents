package dev.adrien.crystaloptimizer.client.v2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class ReactiveBurstDispatcherContinuationTest {
    @Test
    void missingGroupReservationOnlyBlocksSuffixesThatStillConsumeItems() {
        BlockPos pos = new BlockPos(4, 64, 4);
        List<CombatAction> chain = List.of(
            new PlaceAnchor(pos),
            new ChargeAnchor(pos),
            new DetonateAnchor(pos)
        );

        assertTrue(ReactiveBurstDispatcher.continuationNeedsReservation(chain, 1));
        assertFalse(ReactiveBurstDispatcher.continuationNeedsReservation(chain, 2));
    }
}
