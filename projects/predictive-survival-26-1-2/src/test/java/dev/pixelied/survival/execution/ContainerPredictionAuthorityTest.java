package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerPredictionAuthorityTest {
    private static final InventorySlotSnapshot SWORD_AT_0 = slot(0, "minecraft:diamond_sword", 101, 1);
    private static final InventorySlotSnapshot CHEST_AT_10 = slot(10, "minecraft:netherite_chestplate", 202, 1);
    private static final InventorySlotSnapshot CHEST_AT_0 = slot(0, "minecraft:netherite_chestplate", 202, 1);
    private static final InventorySlotSnapshot SWORD_AT_10 = slot(10, "minecraft:diamond_sword", 101, 1);

    @Test
    void exactLocalPredictionWaitsUntilCorrectionWindowSettlesWhenVanillaSendsNoAck() {
        ContainerPredictionAuthority authority = new ContainerPredictionAuthority(
            7, 4, 10, SWORD_AT_10, 0, CHEST_AT_0, 20, 22
        );

        assertEquals(ContainerPredictionAuthority.Verdict.WAITING,
            authority.evaluate(context(4, 21, predictedInventory(), ServerStateEvidenceSnapshot.unknown())));
        assertEquals(ContainerPredictionAuthority.Verdict.ACCEPTED,
            authority.evaluate(context(4, 22, predictedInventory(), ServerStateEvidenceSnapshot.unknown())));
    }

    @Test
    void inboundExactSlotEvidenceCanAcceptBeforeSilenceDeadline() {
        ContainerPredictionAuthority authority = new ContainerPredictionAuthority(
            7, 4, 10, SWORD_AT_10, 0, CHEST_AT_0, 20, 30
        );
        ServerStateEvidenceSnapshot evidence = new ServerStateEvidenceSnapshot(
            true,
            22,
            Map.of(
                10, new ServerStateEvidenceSnapshot.StackEvidence("minecraft:diamond_sword", 101, 1, 21),
                0, new ServerStateEvidenceSnapshot.StackEvidence("minecraft:netherite_chestplate", 202, 1, 22)
            ),
            Map.of(),
            Map.of()
        );

        assertEquals(ContainerPredictionAuthority.Verdict.ACCEPTED,
            authority.evaluate(context(4, 21, predictedInventory(), evidence)));
    }

    @Test
    void authoritativeRevisionWithDifferentContentsContradictsImmediately() {
        ContainerPredictionAuthority authority = new ContainerPredictionAuthority(
            7, 4, 10, SWORD_AT_10, 0, CHEST_AT_0, 20, 30
        );
        InventorySnapshot corrected = new InventorySnapshot(
            0,
            Map.of(0, SWORD_AT_0, 10, CHEST_AT_10),
            false
        );

        assertEquals(ContainerPredictionAuthority.Verdict.CONTRADICTED,
            authority.evaluate(context(5, 21, corrected, ServerStateEvidenceSnapshot.unknown())));
    }

    @Test
    void inboundContradictingSlotEvidenceFailsBeforeSettleDeadline() {
        ContainerPredictionAuthority authority = new ContainerPredictionAuthority(
            7, 4, 10, SWORD_AT_10, 0, CHEST_AT_0, 20, 30
        );
        ServerStateEvidenceSnapshot evidence = new ServerStateEvidenceSnapshot(
            true,
            21,
            Map.of(0, new ServerStateEvidenceSnapshot.StackEvidence("minecraft:diamond_sword", 101, 1, 21)),
            Map.of(),
            Map.of()
        );

        assertEquals(ContainerPredictionAuthority.Verdict.CONTRADICTED,
            authority.evaluate(context(4, 21, predictedInventory(), evidence)));
    }

    private static ExecutionContext context(
        int menuState,
        long tick,
        InventorySnapshot inventory,
        ServerStateEvidenceSnapshot evidence
    ) {
        return new ExecutionContext(
            inventory,
            new MenuSlotMap(7, menuState, Map.of(0, 36, 10, 10, 40, 45)),
            new TimingSnapshot(tick, 0d, 0d, new TickWindow(tick, tick + 1)),
            tick,
            false,
            null,
            0,
            true,
            evidence
        );
    }

    private static InventorySnapshot predictedInventory() {
        return new InventorySnapshot(0, Map.of(0, CHEST_AT_0, 10, SWORD_AT_10), false);
    }

    private static InventorySlotSnapshot slot(int index, String key, int fingerprint, int count) {
        return new InventorySlotSnapshot(
            index, key, fingerprint, count, false,
            Optional.empty(), Optional.empty(), Optional.empty()
        );
    }
}
