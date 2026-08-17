package dev.adrien.crystaloptimizer.world;

import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;

public record CombatSnapshot(
    long worldRevision,
    UUID selfId,
    CombatRegion region,
    Map<UUID, SimCombatant> combatants,
    List<KnownCrystal> crystals,
    Map<BlockPos, AnchorState> anchors,
    InventoryState inventory,
    TimingState timing,
    LegalitySnapshot legality,
    Map<UUID, CombatantSpatialState> spatial,
    Difficulty difficulty
) {
    public CombatSnapshot(
        long worldRevision,
        UUID selfId,
        CombatRegion region,
        Map<UUID, SimCombatant> combatants,
        List<KnownCrystal> crystals,
        Map<BlockPos, AnchorState> anchors,
        InventoryState inventory,
        TimingState timing
    ) {
        this(
            worldRevision,
            selfId,
            region,
            combatants,
            crystals,
            anchors,
            inventory,
            timing,
            LegalitySnapshot.unavailable(),
            Map.of(),
            Difficulty.NORMAL
        );
    }

    public CombatSnapshot(
        long worldRevision,
        UUID selfId,
        CombatRegion region,
        Map<UUID, SimCombatant> combatants,
        List<KnownCrystal> crystals,
        Map<BlockPos, AnchorState> anchors,
        InventoryState inventory,
        TimingState timing,
        LegalitySnapshot legality
    ) {
        this(
            worldRevision,
            selfId,
            region,
            combatants,
            crystals,
            anchors,
            inventory,
            timing,
            legality,
            Map.of(),
            Difficulty.NORMAL
        );
    }

    public CombatSnapshot {
        if (worldRevision < 0L) {
            throw new IllegalArgumentException("worldRevision must be non-negative");
        }
        Objects.requireNonNull(selfId, "selfId");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(combatants, "combatants");
        Objects.requireNonNull(crystals, "crystals");
        Objects.requireNonNull(anchors, "anchors");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(legality, "legality");
        Objects.requireNonNull(spatial, "spatial");
        Objects.requireNonNull(difficulty, "difficulty");

        combatants = Map.copyOf(combatants);
        crystals = List.copyOf(crystals);
        spatial = Map.copyOf(spatial);
        LinkedHashMap<BlockPos, AnchorState> anchorCopy = new LinkedHashMap<>();
        anchors.forEach((pos, state) -> anchorCopy.put(pos.immutable(), Objects.requireNonNull(state)));
        anchors = Map.copyOf(anchorCopy);

        if (!combatants.containsKey(selfId)) {
            throw new IllegalArgumentException("combatants must contain selfId");
        }
    }
}
