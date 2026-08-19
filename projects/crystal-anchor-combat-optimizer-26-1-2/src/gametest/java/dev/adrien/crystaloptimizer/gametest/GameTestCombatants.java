package dev.adrien.crystaloptimizer.gametest;

import com.mojang.authlib.GameProfile;
import dev.adrien.crystaloptimizer.sim.model.ArmorPieceState;
import dev.adrien.crystaloptimizer.sim.model.BlockingState;
import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.EquipmentState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

public final class GameTestCombatants {
    public static ServerPlayer makeSurvivalPlayer(ServerLevel level) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "crystaloptimizer-test-player");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
            level.getServer(),
            level,
            cookie.gameProfile(),
            cookie.clientInformation()
        );
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(new ChannelHandler[] {connection});
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    public static SimCombatant exactFirstHit(ServerPlayer player) {
        return exact(player, new HurtWindowState(0, 0.0f));
    }

    public static SimCombatant exact(ServerPlayer player, HurtWindowState hurtWindow) {
        return new SimCombatant(
            Math.max(0.0f, player.getHealth()),
            Math.max(0.0f, player.getAbsorptionAmount()),
            equipment(player),
            effects(player),
            blocking(player),
            hurtWindow,
            totem(player),
            player.isDeadOrDying()
        );
    }

    private static EquipmentState equipment(ServerPlayer player) {
        EquipmentState equipment = EquipmentState.empty();
        for (EquipmentSlot slot : List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        )) {
            ItemStack stack = player.getItemBySlot(slot);
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
                int enchantmentLevel = entry.getIntValue();
                if (enchantment.is(Enchantments.PROTECTION)) {
                    explosionProtection += enchantmentLevel;
                }
                if (enchantment.is(Enchantments.BLAST_PROTECTION)) {
                    explosionProtection += enchantmentLevel * 2.0f;
                }
            }

            int durability = stack.isDamageableItem()
                ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue())
                : Integer.MAX_VALUE;
            equipment = equipment.withPiece(
                slot,
                new ArmorPieceState((float) armor[0], (float) toughness[0], durability, explosionProtection)
            );
        }
        return equipment;
    }

    private static EffectState effects(ServerPlayer player) {
        return new EffectState(
            effect(player, MobEffects.RESISTANCE),
            effect(player, MobEffects.REGENERATION),
            effect(player, MobEffects.ABSORPTION),
            effect(player, MobEffects.FIRE_RESISTANCE)
        );
    }

    private static Optional<EffectState.EffectInstance> effect(
        ServerPlayer player,
        Holder<MobEffect> effect
    ) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance == null) {
            return Optional.empty();
        }
        return Optional.of(new EffectState.EffectInstance(instance.getAmplifier(), instance.getDuration()));
    }

    private static BlockingState blocking(ServerPlayer player) {
        ItemStack blockingItem = player.getItemBlockingWith();
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
            player.position(),
            player.getYHeadRot(),
            reduction.horizontalBlockingAngle(),
            reduction.base(),
            reduction.factor()
        );
    }

    private static TotemState totem(ServerPlayer player) {
        boolean main = player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
        boolean off = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        if (main && off) {
            return TotemState.BOTH;
        }
        if (main) {
            return TotemState.MAINHAND;
        }
        if (off) {
            return TotemState.OFFHAND;
        }
        return TotemState.NONE;
    }

    private GameTestCombatants() {
    }
}
