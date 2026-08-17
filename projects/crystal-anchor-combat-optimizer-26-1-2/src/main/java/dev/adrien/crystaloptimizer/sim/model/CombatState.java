package dev.adrien.crystaloptimizer.sim.model;

import dev.adrien.crystaloptimizer.world.BlockDeltaOverlay;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public record CombatState(
    CombatSnapshot base,
    SimCombatant self,
    SimCombatant target,
    BlockDeltaOverlay geometry,
    List<KnownCrystal> crystals,
    Map<BlockPos, AnchorState> anchors,
    InventoryState inventory,
    TimingState timing
) {
    public CombatState {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(crystals, "crystals");
        Objects.requireNonNull(anchors, "anchors");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(timing, "timing");
        crystals = List.copyOf(crystals);
        LinkedHashMap<BlockPos, AnchorState> anchorCopy = new LinkedHashMap<>();
        anchors.forEach((pos, state) -> anchorCopy.put(pos.immutable(), Objects.requireNonNull(state)));
        anchors = Map.copyOf(anchorCopy);
    }

    public static CombatState fromSnapshot(CombatSnapshot snapshot, UUID targetId) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(targetId, "targetId");
        SimCombatant self = requireCombatant(snapshot, snapshot.selfId(), "self");
        SimCombatant target = requireCombatant(snapshot, targetId, "target");
        return new CombatState(
            snapshot,
            self,
            target,
            new BlockDeltaOverlay(snapshot.region()),
            snapshot.crystals(),
            snapshot.anchors(),
            snapshot.inventory(),
            snapshot.timing()
        );
    }

    private static SimCombatant requireCombatant(CombatSnapshot snapshot, UUID id, String label) {
        SimCombatant combatant = snapshot.combatants().get(id);
        if (combatant == null) {
            throw new IllegalArgumentException(label + " combatant is absent from snapshot: " + id);
        }
        return combatant;
    }
}
