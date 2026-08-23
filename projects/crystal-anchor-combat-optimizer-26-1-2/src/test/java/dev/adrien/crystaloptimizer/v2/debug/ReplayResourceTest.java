package dev.adrien.crystaloptimizer.v2.debug;

import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void checkedInRegressionReplaysMatchBuildersAndRemainDeterministic() throws Exception {
        ReplayCodec codec = new ReplayCodec();
        ReplayRunner runner = new ReplayRunner();
        Map<String, ReplayFixture> generated = V3ReplayFixtures.checkedInFixtures();
        assertEquals(FIXTURES.size(), generated.size(), "checked-in replay set drifted from builders");

        for (String name : FIXTURES) {
            ReplayFixture fixture = codec.readResource("replays/v3/" + name);
            assertArrayEquals(
                codec.encode(generated.get(name)),
                codec.encode(fixture),
                name + " drifted from its scenario builder"
            );

            ReplayResult first = runner.run(fixture);
            ReplayFixture roundTrip = codec.decode(codec.encode(fixture));
            ReplayResult second = runner.run(roundTrip);

            assertEquals(first, second, name + " changed after replay round-trip");
            assertFalse(first.chosenDecisionKey().isBlank(), name + " produced a blank decision key");
            assertFalse(first.decisionClass().isBlank(), name + " produced a blank decision class");
        }
    }

    @Test
    void replayRoundTripPreservesExactOffhandStackCountApartFromReserveInventory() {
        ReplayFixture base = V3ReplayFixtures.checkedInFixtures().values().iterator().next();
        InventoryState exactInventory = new InventoryState(
            0,
            Map.of(Items.END_CRYSTAL, 7),
            Map.of(),
            Map.of(),
            Optional.of(Items.END_CRYSTAL),
            2
        );
        CombatSnapshot combat = base.snapshot().combat();
        CombatSnapshot exactCombat = new CombatSnapshot(
            combat.worldRevision(),
            combat.selfId(),
            combat.region(),
            combat.combatants(),
            combat.crystals(),
            combat.anchors(),
            exactInventory,
            combat.timing(),
            combat.legality(),
            combat.spatial(),
            combat.difficulty()
        );
        StrategicSnapshot snapshot = base.snapshot();
        StrategicSnapshot exactSnapshot = new StrategicSnapshot(
            snapshot.snapshotId(),
            snapshot.worldRevision(),
            snapshot.inventoryRevision(),
            snapshot.configRevision(),
            snapshot.capturedAtNanos(),
            snapshot.selfId(),
            snapshot.targetRevisions(),
            exactCombat,
            snapshot.movementHistory(),
            snapshot.protectedPlayerIds(),
            snapshot.targetProtection(),
            snapshot.timing()
        );

        ReplayFixture roundTrip = new ReplayCodec().decode(new ReplayCodec().encode(
            new ReplayFixture(exactSnapshot, base.config(), base.events())
        ));

        InventoryState decoded = roundTrip.snapshot().combat().inventory();
        assertEquals(2, decoded.offhandCount(),
            "replay must not infer reserve inventory as extra offhand items");
        assertEquals(Items.END_CRYSTAL, decoded.offhandItem().orElseThrow());
        assertEquals(7, decoded.count(Items.END_CRYSTAL));
    }
}
