package dev.adrien.crystaloptimizer.intel;

import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpponentIntelServiceTest {
    private static final UUID OPPONENT = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private OpponentIntelService service;

    @BeforeEach
    void setUp() {
        service = new OpponentIntelService();
    }

    @Test
    void exactVisibleStackAndObservedPickupStayDistinctFromHiddenReserveEstimate() {
        service.onVisibleEquipment(
            OPPONENT,
            EquipmentSlot.OFFHAND,
            new ItemStack(Items.TOTEM_OF_UNDYING, 1),
            1_000L
        );
        service.onPickup(OPPONENT, Items.TOTEM_OF_UNDYING, 2, 1_100L);
        service.onProtectedFromDeath(OPPONENT, 1_200L);

        OpponentIntel intel = service.snapshot(OPPONENT);

        assertEquals(EvidenceKind.EXACT, intel.visibleOffhand().kind());
        assertEquals(Items.TOTEM_OF_UNDYING, intel.visibleOffhand().item());
        assertEquals(1, intel.visibleOffhand().lowerBound());
        assertEquals(1, intel.visibleOffhand().upperBound().orElseThrow());
        assertEquals(2, intel.observedPickups().get(Items.TOTEM_OF_UNDYING));
        assertEquals(1, intel.confirmedPops());
        assertEquals(EvidenceKind.ESTIMATED, intel.totemReserves().kind());
        assertTrue(intel.totemReserves().upperBound().isEmpty());
    }

    @Test
    void exactMainhandCrystalCountNeverBecomesAnInventedHotbarOrReserveCount() {
        service.onVisibleEquipment(
            OPPONENT,
            EquipmentSlot.MAINHAND,
            new ItemStack(Items.END_CRYSTAL, 27),
            2_000L
        );

        OpponentIntel intel = service.snapshot(OPPONENT);

        assertEquals(EvidenceKind.EXACT, intel.visibleMainhand().kind());
        assertEquals(27, intel.visibleMainhand().lowerBound());
        assertEquals(27, intel.visibleMainhand().upperBound().orElseThrow());
        assertTrue(intel.reserveEstimate(Items.END_CRYSTAL).upperBound().isEmpty());
        assertTrue(intel.observations().stream().anyMatch(observation ->
            observation.type() == OpponentObservation.Type.VISIBLE_EQUIPMENT
                && observation.kind() == EvidenceKind.EXACT
        ));
    }
}
