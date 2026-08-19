package dev.pixelied.survival.core;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class MinecraftContactHazardSnapshotAnnotator {
    public PlayerSnapshot annotate(LocalPlayer player, PlayerSnapshot snapshot) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");

        Map<String, String> state = new LinkedHashMap<>(snapshot.stateProperties());
        AABB box = player.getBoundingBox().deflate(1.0E-7d);
        boolean cactus = false;
        boolean berry = false;
        int campfireDamage = 0;
        Vec3 knownMovement = player.getKnownMovement();
        boolean movingEnoughForBerry = Math.abs(knownMovement.x) >= 0.003d
            || Math.abs(knownMovement.y) >= 0.003d
            || Math.abs(knownMovement.z) >= 0.003d;

        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState block = player.level().getBlockState(pos);
                    cactus |= block.is(Blocks.CACTUS);
                    berry |= movingEnoughForBerry && block.is(Blocks.SWEET_BERRY_BUSH);
                    if (block.getBlock() instanceof CampfireBlock
                        && block.getValue(CampfireBlock.LIT)) {
                        campfireDamage = Math.max(campfireDamage, block.is(Blocks.SOUL_CAMPFIRE) ? 2 : 1);
                    }
                }
            }
        }

        state.put("contact_cactus", Boolean.toString(cactus));
        state.put("contact_sweet_berry_bush", Boolean.toString(berry));
        state.put("contact_campfire_damage", Integer.toString(campfireDamage));
        state.put("contact_hot_floor", Boolean.toString(player.getBlockStateOn().is(Blocks.MAGMA_BLOCK)));
        return new PlayerSnapshot(
            snapshot.health(), snapshot.absorption(), snapshot.playerInvulnerable(), snapshot.abilityInvulnerable(),
            snapshot.deadOrDying(), snapshot.difficulty(), snapshot.mitigation(), snapshot.statusEffects(),
            snapshot.blocking(), snapshot.hurtState(), snapshot.deathProtection(), snapshot.boundingBox(),
            snapshot.position(), snapshot.velocity(), snapshot.equipmentItemKeys(), state
        );
    }
}
