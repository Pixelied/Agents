package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Exact-runtime proof that an accepted server hit survives into the next production hurt-state frame. */
final class RepeatedDamageStateValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private RepeatedDamageStateValidationScenarios() {
    }

    static void validateFullExplosionHitReconcilesAcrossClientFrames(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 330d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);

            BurstSequenceValidationSupport.prepareVictim(victim, 20f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);
            EndCrystal crystal = new EndCrystal(
                level,
                center.getX() + 0.5d,
                center.getY() + 0.5d,
                center.getZ() + 10.5d
            );
            level.addFreshEntity(crystal);
            return new Setup(victim.getUUID(), originalPosition, center, crystal.getId(), originals);
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && Math.abs(minecraft.player.getX() - (setup.center().getX() + 0.5d)) <= 0.05d
                && Math.abs(minecraft.player.getY() - setup.center().getY()) <= 0.05d
                && Math.abs(minecraft.player.getZ() - (setup.center().getZ() + 0.5d)) <= 0.05d
                && minecraft.level.getEntity(setup.crystalId()) instanceof EndCrystal);

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            Expected expected = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                ThreatEvent event = frame.actualTimeline().events().stream()
                    .filter(candidate -> candidate.id().equals("explosion:" + setup.crystalId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                        "full-hit reconciliation fixture produced no observed crystal explosion event"
                    ));
                if (Float.compare(event.damage().rawDamage().min(), event.damage().rawDamage().max()) != 0) {
                    throw new AssertionError("full-hit reconciliation fixture requires exact pre-armor explosion damage");
                }
                return new Expected(event.damage().rawDamage().max());
            });

            float serverHealth = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel) victim.level();
                Entity entity = level.getEntity(setup.crystalId());
                if (!(entity instanceof EndCrystal crystal)) {
                    throw new AssertionError("reconciliation source crystal disappeared before authoritative hit");
                }
                victim.invulnerableTime = 0;
                boolean accepted = crystal.hurtServer(level, victim.damageSources().playerAttack(victim), 1f);
                if (!accepted) throw new AssertionError("authoritative EndCrystal hit was rejected");
                if (!(victim.getHealth() < 20f) || victim.isDeadOrDying()) {
                    throw new AssertionError("reconciliation fixture expected a nonlethal accepted explosion; health=" + victim.getHealth());
                }
                return victim.getHealth();
            });

            context.waitFor(minecraft -> minecraft.player != null
                && Math.abs(minecraft.player.getHealth() - serverHealth) <= EPSILON);

            Observed observed = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                return new Observed(
                    frame.context().player().hurtState().lastHurt().min(),
                    frame.context().player().hurtState().lastHurt().max(),
                    frame.context().player().hurtState().invulnerableTime(),
                    frame.context().player().hurtState().confidence()
                );
            });

            if (observed.confidence() != Confidence.MATCHED) {
                throw new AssertionError(
                    "accepted server explosion must reconcile into the next production frame; hurtState=" + observed
                );
            }
            SurvivalValidationClientGameTest.assertClose(
                "reconciled_pre_armor_last_hurt_min",
                expected.preArmorDamage(),
                observed.lastHurtMin(),
                EPSILON
            );
            SurvivalValidationClientGameTest.assertClose(
                "reconciled_pre_armor_last_hurt_max",
                expected.preArmorDamage(),
                observed.lastHurtMax(),
                EPSILON
            );
            if (observed.invulnerableTime() <= 10) {
                throw new AssertionError("reconciled full hit must retain active server hurt cooldown; hurtState=" + observed);
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim == null) return;
                ServerLevel level = (ServerLevel) victim.level();
                Entity crystal = level.getEntity(setup.crystalId());
                if (crystal != null) crystal.discard();
                restore(level, setup.originals());
                SurvivalValidationClientGameTest.reset(victim, 20f);
                victim.setNoGravity(false);
                victim.teleportTo(
                    setup.originalPosition().x,
                    setup.originalPosition().y,
                    setup.originalPosition().z
                );
            });
            context.waitTick();
        }
    }

    private static Map<BlockPos, BlockState> clearArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 13; dz++) {
                for (int dy = -2; dy <= 4; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return Map.copyOf(originals);
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> originals) {
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
    }

    private record Setup(
        UUID victimId,
        Vec3 originalPosition,
        BlockPos center,
        int crystalId,
        Map<BlockPos, BlockState> originals
    ) {
    }

    private record Expected(float preArmorDamage) {
    }

    private record Observed(float lastHurtMin, float lastHurtMax, int invulnerableTime, Confidence confidence) {
    }
}
