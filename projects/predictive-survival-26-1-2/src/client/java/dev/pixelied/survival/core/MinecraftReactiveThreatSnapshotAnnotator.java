package dev.pixelied.survival.core;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MinecraftReactiveThreatSnapshotAnnotator {
    public AnnotatedSnapshot annotate(Minecraft minecraft, PlayerSnapshot player, WorldSnapshot world) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");
        if (!(minecraft.hitResult instanceof EntityHitResult entityHit)) {
            return new AnnotatedSnapshot(player, world);
        }
        Entity target = entityHit.getEntity();
        if (!(target instanceof LivingEntity living) || target == minecraft.player) {
            return new AnnotatedSnapshot(player, world);
        }

        int thornsLevels = visibleThornsLevels(living);
        Map<String, String> playerState = new LinkedHashMap<>(player.stateProperties());
        playerState.put("outgoing_attack_target_id", Integer.toString(target.getId()));
        PlayerSnapshot annotatedPlayer = copyPlayer(player, playerState);

        List<WorldSnapshot.EntitySnapshot> entities = new ArrayList<>(world.entities().size());
        String targetId = Integer.toString(target.getId());
        for (WorldSnapshot.EntitySnapshot entity : world.entities()) {
            if (!targetId.equals(entity.id())) {
                entities.add(entity);
                continue;
            }
            Map<String, String> properties = new LinkedHashMap<>(entity.properties());
            properties.put("thorns_levels", Integer.toString(thornsLevels));
            entities.add(new WorldSnapshot.EntitySnapshot(
                entity.id(), entity.typeKey(), entity.position(), entity.velocity(), entity.boundingBox(), properties
            ));
        }
        return new AnnotatedSnapshot(annotatedPlayer, new WorldSnapshot(entities, world.blocks()));
    }

    private static int visibleThornsLevels(LivingEntity target) {
        Holder<Enchantment> thorns = target.level().registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.THORNS);
        int total = 0;
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack stack = target.getItemBySlot(slot);
            total = Math.addExact(total, EnchantmentHelper.getItemEnchantmentLevel(thorns, stack));
        }
        return total;
    }

    private static PlayerSnapshot copyPlayer(PlayerSnapshot player, Map<String, String> state) {
        return new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(),
            player.deadOrDying(), player.difficulty(), player.mitigation(), player.statusEffects(), player.blocking(),
            player.hurtState(), player.deathProtection(), player.boundingBox(), player.position(), player.velocity(),
            player.equipmentItemKeys(), state
        );
    }

    public record AnnotatedSnapshot(PlayerSnapshot player, WorldSnapshot world) {
        public AnnotatedSnapshot {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(world, "world");
        }
    }
}
