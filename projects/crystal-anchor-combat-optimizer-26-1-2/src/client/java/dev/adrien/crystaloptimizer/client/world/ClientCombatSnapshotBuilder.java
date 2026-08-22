package dev.adrien.crystaloptimizer.client.world;

import dev.adrien.crystaloptimizer.client.execution.InteractionTimingRecorder;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.ArmorPieceState;
import dev.adrien.crystaloptimizer.sim.model.BlockingState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.EquipmentState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.timing.TimingEstimate;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import dev.adrien.crystaloptimizer.world.ObservedCombatantAssembler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ClientCombatSnapshotBuilder {
    private static final int MIN_GEOMETRY_MARGIN = 6;

    private final Minecraft minecraft;

    public ClientCombatSnapshotBuilder(Minecraft minecraft) {
        this.minecraft = java.util.Objects.requireNonNull(minecraft, "minecraft");
    }

    public Optional<CombatSnapshot> build(AbstractClientPlayer target) {
        LocalPlayer self = minecraft.player;
        ClientLevel level = minecraft.level;
        if (self == null || level == null || target == null || target == self || target.isRemoved()) {
            return Optional.empty();
        }

        int margin = Math.max(
            MIN_GEOMETRY_MARGIN,
            (int)Math.ceil(Math.max(self.blockInteractionRange(), self.entityInteractionRange())) + 2
        );
        BlockPos selfPos = self.blockPosition();
        BlockPos targetPos = target.blockPosition();
        BlockPos min = new BlockPos(
            Math.min(selfPos.getX(), targetPos.getX()) - margin,
            Math.min(selfPos.getY(), targetPos.getY()) - margin,
            Math.min(selfPos.getZ(), targetPos.getZ()) - margin
        );
        BlockPos max = new BlockPos(
            Math.max(selfPos.getX(), targetPos.getX()) + margin,
            Math.max(selfPos.getY(), targetPos.getY()) + margin,
            Math.max(selfPos.getZ(), targetPos.getZ()) + margin
        );
        AABB scanBox = new AABB(
            min.getX(), min.getY(), min.getZ(),
            max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0
        );

        LinkedHashMap<BlockPos, BlockState> blockStates = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, VoxelShape> collisionShapes = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, AnchorState> anchors = new LinkedHashMap<>();
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            BlockPos pos = cursor.immutable();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            blockStates.put(pos, state);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                collisionShapes.put(pos, shape);
            }
            if (state.is(Blocks.RESPAWN_ANCHOR)) {
                anchors.put(pos, new AnchorState(state.getValue(RespawnAnchorBlock.CHARGE)));
            }
        }
        CombatRegion region = CombatRegion.of(blockStates, collisionShapes);

        List<KnownCrystal> crystals = new ArrayList<>();
        List<AABB> occupiedBoxes = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isRemoved() || !entity.getBoundingBox().intersects(scanBox)) {
                continue;
            }
            occupiedBoxes.add(entity.getBoundingBox());
            if (entity instanceof EndCrystal) {
                crystals.add(new KnownCrystal(entity.getId(), entity.position()));
            }
        }

        EffectState selfEffects = effects(self);
        EffectState targetEffects = effects(target);
        SimCombatant selfState = ObservedCombatantAssembler.self(
            self.getHealth(),
            self.getAbsorptionAmount(),
            equipment(self),
            selfEffects,
            blocking(self),
            self.invulnerableTime,
            self.getMainHandItem().is(Items.TOTEM_OF_UNDYING),
            self.getOffhandItem().is(Items.TOTEM_OF_UNDYING),
            self.isDeadOrDying()
        );
        SimCombatant targetState = observedRemote(target, targetEffects);

        UUID selfId = self.getUUID();
        UUID targetId = target.getUUID();
        LinkedHashMap<UUID, SimCombatant> combatants = new LinkedHashMap<>();
        LinkedHashMap<UUID, CombatantSpatialState> spatial = new LinkedHashMap<>();
        combatants.put(selfId, selfState);
        combatants.put(targetId, targetState);
        spatial.put(
            selfId,
            new CombatantSpatialState(self.position(), self.getBoundingBox(), self.getDeltaMovement())
        );
        spatial.put(
            targetId,
            new CombatantSpatialState(target.position(), target.getBoundingBox(), target.getDeltaMovement())
        );
        for (AbstractClientPlayer player : level.players()) {
            if (player == self || player == target || player.isRemoved()) {
                continue;
            }
            combatants.put(player.getUUID(), observedRemote(player, effects(player)));
            spatial.put(
                player.getUUID(),
                new CombatantSpatialState(
                    player.position(),
                    player.getBoundingBox(),
                    player.getDeltaMovement()
                )
            );
        }

        boolean respawnAnchorWorks = level.environmentAttributes()
            .getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, selfPos);
        LegalitySnapshot legality = new LegalitySnapshot(
            self.getEyePosition(),
            self.blockInteractionRange(),
            self.entityInteractionRange(),
            occupiedBoxes,
            respawnAnchorWorks
        );

        TimingEstimate timingEstimate = InteractionTimingRecorder.instance().estimateBurst(System.nanoTime(), 1);
        TimingState timing = new TimingState(
            -1L,
            timingEstimate.confidence(),
            timingEstimate.medianAckDelayMillis(),
            timingEstimate.jitterMillis()
        );

        return Optional.of(new CombatSnapshot(
            Math.max(0L, level.getGameTime()),
            selfId,
            region,
            combatants,
            crystals,
            anchors,
            inventory(self),
            timing,
            legality,
            spatial,
            level.getDifficulty()
        ));
    }

    private static SimCombatant observedRemote(
        AbstractClientPlayer player,
        EffectState effects
    ) {
        return ObservedCombatantAssembler.target(
            player.getHealth(),
            equipment(player),
            effects,
            blocking(player),
            player.invulnerableTime,
            player.getMainHandItem().is(Items.TOTEM_OF_UNDYING),
            player.getOffhandItem().is(Items.TOTEM_OF_UNDYING),
            player.isDeadOrDying()
        );
    }

    private static InventoryState inventory(LocalPlayer self) {
        LinkedHashMap<Item, Integer> counts = new LinkedHashMap<>();
        LinkedHashMap<Integer, Item> hotbar = new LinkedHashMap<>();
        LinkedHashMap<Integer, Integer> hotbarCounts = new LinkedHashMap<>();
        var inventory = self.getInventory();
        List<ItemStack> items = inventory.getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            if (slot < 9) {
                hotbar.put(slot, stack.getItem());
                hotbarCounts.put(slot, stack.getCount());
            }
        }
        ItemStack offhand = self.getOffhandItem();
        if (!offhand.isEmpty()) {
            counts.merge(offhand.getItem(), offhand.getCount(), Integer::sum);
        }
        return new InventoryState(
            inventory.getSelectedSlot(),
            counts,
            hotbar,
            hotbarCounts,
            offhand.isEmpty() ? Optional.empty() : Optional.of(offhand.getItem())
        );
    }

    private static EquipmentState equipment(LivingEntity entity) {
        EquipmentState equipment = EquipmentState.empty();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            double[] armor = {0.0};
            double[] toughness = {0.0};
            stack.forEachModifier(slot, (attribute, modifier) -> {
                if (attribute.is(Attributes.ARMOR)) {
                    armor[0] += modifier.amount();
                } else if (attribute.is(Attributes.ARMOR_TOUGHNESS)) {
                    toughness[0] += modifier.amount();
                }
            });

            float explosionProtection = 0.0f;
            for (var entry : stack.getEnchantments().entrySet()) {
                Holder<Enchantment> enchantment = entry.getKey();
                int level = entry.getIntValue();
                if (enchantment.is(Enchantments.PROTECTION)) {
                    explosionProtection += level;
                }
                if (enchantment.is(Enchantments.BLAST_PROTECTION)) {
                    explosionProtection += level * 2.0f;
                }
            }

            int durability = stack.isDamageableItem()
                ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue())
                : Integer.MAX_VALUE;
            equipment = equipment.withPiece(
                slot,
                new ArmorPieceState((float)armor[0], (float)toughness[0], durability, explosionProtection)
            );
        }
        return equipment;
    }

    private static EffectState effects(LivingEntity entity) {
        return new EffectState(
            effect(entity, MobEffects.RESISTANCE),
            effect(entity, MobEffects.REGENERATION),
            effect(entity, MobEffects.ABSORPTION),
            effect(entity, MobEffects.FIRE_RESISTANCE)
        );
    }

    private static Optional<EffectState.EffectInstance> effect(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        if (instance == null) {
            return Optional.empty();
        }
        return Optional.of(new EffectState.EffectInstance(instance.getAmplifier(), instance.getDuration()));
    }

    private static BlockingState blocking(LivingEntity entity) {
        ItemStack blockingItem = entity.getItemBlockingWith();
        if (blockingItem == null) {
            return BlockingState.none();
        }
        BlocksAttacks attacks = blockingItem.get(DataComponents.BLOCKS_ATTACKS);
        if (attacks == null || attacks.damageReductions().isEmpty()) {
            return BlockingState.none();
        }
        BlocksAttacks.DamageReduction reduction = attacks.damageReductions().stream()
            .filter(candidate -> candidate.type().isEmpty())
            .findFirst()
            .orElse(attacks.damageReductions().getFirst());
        return new BlockingState(
            true,
            entity.position(),
            entity.getYHeadRot(),
            reduction.horizontalBlockingAngle(),
            reduction.base(),
            reduction.factor()
        );
    }
}
