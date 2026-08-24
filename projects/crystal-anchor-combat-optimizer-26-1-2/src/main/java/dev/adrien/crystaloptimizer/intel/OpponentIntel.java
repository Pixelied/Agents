package dev.adrien.crystaloptimizer.intel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public record OpponentIntel(
    UUID opponentId,
    ResourceEstimate visibleMainhand,
    ResourceEstimate visibleOffhand,
    Map<EquipmentSlot, ResourceEstimate> visibleEquipment,
    Map<Item, Integer> observedPickups,
    int confirmedPops,
    ResourceEstimate totemReserves,
    List<OpponentObservation> observations,
    OpponentResponseProfile responseProfile
) {
    public OpponentIntel {
        Objects.requireNonNull(opponentId, "opponentId");
        Objects.requireNonNull(visibleMainhand, "visibleMainhand");
        Objects.requireNonNull(visibleOffhand, "visibleOffhand");
        Objects.requireNonNull(visibleEquipment, "visibleEquipment");
        Objects.requireNonNull(observedPickups, "observedPickups");
        Objects.requireNonNull(totemReserves, "totemReserves");
        Objects.requireNonNull(observations, "observations");
        Objects.requireNonNull(responseProfile, "responseProfile");
        if (confirmedPops < 0) {
            throw new IllegalArgumentException("confirmedPops must be non-negative");
        }
        visibleEquipment = Map.copyOf(visibleEquipment);
        observedPickups = Map.copyOf(observedPickups);
        observations = List.copyOf(observations);
    }

    public ResourceEstimate reserveEstimate(Item item) {
        Objects.requireNonNull(item, "item");
        if (item == Items.TOTEM_OF_UNDYING) {
            return totemReserves;
        }
        return ResourceEstimate.unknown(item);
    }
}
