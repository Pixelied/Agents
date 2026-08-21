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

    /**
     * Current local health plus absorption. Implementations used only in old unit fixtures may
     * inherit the conservative compatibility default; the real client view overrides this.
     */
    default float selfEffectiveHealth() {
        return Float.MAX_VALUE;
    }

    /** True only when a totem is currently in a hand where vanilla can activate it. */
    default boolean selfTotemAvailable() {
        return false;
    }
}
