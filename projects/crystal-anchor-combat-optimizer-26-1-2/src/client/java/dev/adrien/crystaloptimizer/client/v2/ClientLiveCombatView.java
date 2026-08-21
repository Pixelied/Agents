package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.v2.execution.LiveCombatView;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class ClientLiveCombatView implements LiveCombatView {
    private final Minecraft minecraft;
    private final LongSupplier worldRevision;
    private final ToLongFunction<UUID> targetRevision;
    private final LongSupplier inventoryRevision;
    private final LongSupplier configRevision;

    public ClientLiveCombatView(
        Minecraft minecraft,
        LongSupplier worldRevision,
        ToLongFunction<UUID> targetRevision,
        LongSupplier inventoryRevision,
        LongSupplier configRevision
    ) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.worldRevision = Objects.requireNonNull(worldRevision, "worldRevision");
        this.targetRevision = Objects.requireNonNull(targetRevision, "targetRevision");
        this.inventoryRevision = Objects.requireNonNull(inventoryRevision, "inventoryRevision");
        this.configRevision = Objects.requireNonNull(configRevision, "configRevision");
    }

    @Override
    public long worldRevision() {
        return worldRevision.getAsLong();
    }

    @Override
    public long targetRevision(UUID targetId) {
        return targetRevision.applyAsLong(Objects.requireNonNull(targetId, "targetId"));
    }

    @Override
    public long inventoryRevision() {
        return inventoryRevision.getAsLong();
    }

    @Override
    public long configRevision() {
        return configRevision.getAsLong();
    }

    @Override
    public boolean targetValid(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        LocalPlayer self = minecraft.player;
        ClientLevel level = minecraft.level;
        if (self == null || level == null) {
            return false;
        }
        return level.players().stream()
            .filter(player -> player.getUUID().equals(targetId))
            .filter(player -> player != self)
            .filter(player -> !player.isRemoved())
            .filter(player -> !player.isDeadOrDying())
            .filter(player -> !player.isSpectator())
            .anyMatch(player -> !self.isAlliedTo(player));
    }

    @Override
    public boolean liveCrystal(int entityId) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return false;
        }
        Entity entity = level.getEntity(entityId);
        return entity instanceof EndCrystal && !entity.isRemoved();
    }

    @Override
    public boolean withinEntityReach(int entityId) {
        LocalPlayer self = minecraft.player;
        ClientLevel level = minecraft.level;
        if (self == null || level == null) {
            return false;
        }
        Entity entity = level.getEntity(entityId);
        return entity != null && self.isWithinEntityInteractionRange(entity, 0.0);
    }

    @Override
    public boolean withinBlockReach(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        LocalPlayer self = minecraft.player;
        return self != null && self.isWithinBlockInteractionRange(pos, 0.0);
    }

    @Override
    public boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId) {
        Objects.requireNonNull(basePos, "basePos");
        ClientLevel level = minecraft.level;
        if (level == null) {
            return false;
        }
        var baseState = level.getBlockState(basePos);
        if (!baseState.is(Blocks.OBSIDIAN) && !baseState.is(Blocks.BEDROCK)) {
            return false;
        }

        Entity broken = level.getEntity(brokenCrystalEntityId);
        if (!(broken instanceof EndCrystal) || broken.isRemoved()) {
            return false;
        }
        BlockPos observedBase = BlockPos.containing(
            broken.getX(),
            broken.getY() - 1.0,
            broken.getZ()
        );
        if (!observedBase.equals(basePos)) {
            return false;
        }

        BlockPos above = basePos.above();
        if (!level.isEmptyBlock(above)) {
            return false;
        }
        double x = above.getX();
        double y = above.getY();
        double z = above.getZ();
        AABB placementBox = new AABB(x, y, z, x + 1.0, y + 2.0, z + 1.0);
        return level.getEntities(null, placementBox).stream()
            .allMatch(entity -> entity.getId() == brokenCrystalEntityId);
    }

    @Override
    public int observedCount(Item item) {
        Objects.requireNonNull(item, "item");
        LocalPlayer self = minecraft.player;
        if (self == null) {
            return 0;
        }

        int total = 0;
        for (ItemStack stack : self.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        ItemStack offhand = self.getOffhandItem();
        if (!offhand.isEmpty() && offhand.is(item)) {
            total += offhand.getCount();
        }
        return total;
    }

    @Override
    public int selectedHotbarSlot() {
        LocalPlayer self = minecraft.player;
        return self == null ? -1 : self.getInventory().getSelectedSlot();
    }

    @Override
    public float selfEffectiveHealth() {
        LocalPlayer self = minecraft.player;
        return self == null ? 0.0f : self.getHealth() + self.getAbsorptionAmount();
    }

    @Override
    public boolean selfTotemAvailable() {
        LocalPlayer self = minecraft.player;
        if (self == null) {
            return false;
        }
        return self.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
            || self.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }
}
