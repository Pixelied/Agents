package dev.adrien.crystaloptimizer.v2.execution;

import java.util.Optional;
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

    default Optional<BlockPos> crystalBase(int entityId) {
        return Optional.empty();
    }

    boolean withinEntityReach(int entityId);

    boolean withinBlockReach(BlockPos pos);

    /**
     * True when an end crystal can be placed on this base in the current authoritative client
     * world using the 26.1.2 base, air-space, and entity-collision rules.
     *
     * <p>The compatibility default exists for legacy unit doubles. The real client view must
     * override this with current-world legality.</p>
     */
    default boolean crystalBaseCanPlace(BlockPos basePos) {
        return true;
    }

    boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId);

    int observedCount(Item item);

    int selectedHotbarSlot();

    /** True while the user is actively holding a combat interaction input. */
    default boolean userControllingCombatInput() {
        return false;
    }

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
