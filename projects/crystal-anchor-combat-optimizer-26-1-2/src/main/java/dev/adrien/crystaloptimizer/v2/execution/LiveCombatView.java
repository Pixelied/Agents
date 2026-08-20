package dev.adrien.crystaloptimizer.v2.execution;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

public interface LiveCombatView {
    long worldRevision();

    long targetRevision(UUID targetId);

    long inventoryRevision();

    long configRevision();

    boolean targetValid(UUID targetId);

    boolean liveCrystal(int entityId);

    boolean withinEntityReach(int entityId);

    boolean withinBlockReach(BlockPos pos);

    boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId);

    int observedCount(Item item);

    int selectedHotbarSlot();
}
