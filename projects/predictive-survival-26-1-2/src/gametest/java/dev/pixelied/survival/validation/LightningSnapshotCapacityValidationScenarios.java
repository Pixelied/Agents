package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.ObservationOverflowPredictor;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class LightningSnapshotCapacityValidationScenarios {
    private static final EngineLimits SMALL_LIMITS = new EngineLimits(2, 32, 80, 128);
    private static final int ENTITY_BUDGET = SMALL_LIMITS.maxThreats() * 4;

    private LightningSnapshotCapacityValidationScenarios() {
    }

    static void validateLightningCannotBeSilentlyDroppedByRelevantEntityBudget(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();

            List<Integer> fillerIds = new ArrayList<>();
            for (int i = 0; i < ENTITY_BUDGET; i++) {
                Arrow filler = new Arrow(
                    level,
                    player.getX() + 0.5d + i * 0.1d,
                    player.getY() + 1d,
                    player.getZ() + 1d,
                    new ItemStack(Items.ARROW),
                    null
                );
                filler.setNoGravity(true);
                filler.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(filler);
                fillerIds.add(filler.getId());
            }

            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
            bolt.snapTo(player.getX() + 2.5d, player.getY(), player.getZ(), 0f, 0f);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
            return new Setup(bolt.getId(), List.copyOf(fillerIds));
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.boltId()) instanceof LightningBolt
                && setup.fillerIds().stream().allMatch(id -> minecraft.level.getEntity(id) instanceof Arrow));

            Observation observation = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for lightning capacity validation");
                }
                WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(
                    minecraft.level,
                    minecraft.player,
                    SMALL_LIMITS
                );
                boolean boltPresent = world.entities().stream()
                    .anyMatch(entity -> entity.id().equals(Integer.toString(setup.boltId())));
                boolean overflowPresent = world.entities().stream()
                    .anyMatch(entity -> ObservationOverflowPredictor.MARKER_TYPE.equals(entity.typeKey()));
                return new Observation(world.entities().size(), boltPresent, overflowPresent);
            });

            if (observation.entityCount() > ENTITY_BUDGET) {
                throw new AssertionError("lightning capacity snapshot exceeded entity budget: " + observation);
            }
            if (!observation.boltPresent() && !observation.overflowPresent()) {
                throw new AssertionError(
                    "threat-capable lightning was silently dropped without an observation-overflow marker: "
                        + observation
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity bolt = player.level().getEntity(setup.boltId());
                if (bolt != null) bolt.discard();
                for (int fillerId : setup.fillerIds()) {
                    Entity filler = player.level().getEntity(fillerId);
                    if (filler != null) filler.discard();
                }
            });
            context.waitTick();
        }
    }

    private record Setup(int boltId, List<Integer> fillerIds) {
    }

    private record Observation(int entityCount, boolean boltPresent, boolean overflowPresent) {
    }
}
