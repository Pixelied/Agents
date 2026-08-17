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

    public CombatState withGeometry(BlockDeltaOverlay nextGeometry) {
        return copy(self, target, nextGeometry, crystals, anchors, inventory, timing);
    }

    public CombatState withCrystals(List<KnownCrystal> nextCrystals) {
        return copy(self, target, geometry, nextCrystals, anchors, inventory, timing);
    }

    public CombatState withAnchors(Map<BlockPos, AnchorState> nextAnchors) {
        return copy(self, target, geometry, crystals, nextAnchors, inventory, timing);
    }

    public CombatState withInventory(InventoryState nextInventory) {
        return copy(self, target, geometry, crystals, anchors, nextInventory, timing);
    }

    public CombatState withSelfAndTarget(SimCombatant nextSelf, SimCombatant nextTarget) {
        return copy(nextSelf, nextTarget, geometry, crystals, anchors, inventory, timing);
    }

    public CombatState withTiming(TimingState nextTiming) {
        return copy(self, target, geometry, crystals, anchors, inventory, nextTiming);
    }

    private CombatState copy(
        SimCombatant nextSelf,
        SimCombatant nextTarget,
        BlockDeltaOverlay nextGeometry,
        List<KnownCrystal> nextCrystals,
        Map<BlockPos, AnchorState> nextAnchors,
        InventoryState nextInventory,
        TimingState nextTiming
    ) {
        return new CombatState(
            base,
            nextSelf,
            nextTarget,
            nextGeometry,
            nextCrystals,
            nextAnchors,
            nextInventory,
            nextTiming
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
