package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Exact-runtime proof that accepted/rejected server hits remain trustworthy across production frames. */
final class RepeatedDamageStateValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    private static final double VELOCITY_EPSILON = 1.0E-6d;

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
            victim.connection.send(new ClientboundSetEntityMotionPacket(victim));
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
                && minecraft.player.getDeltaMovement().lengthSqr() <= VELOCITY_EPSILON
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
                    throw new AssertionError(
                        "full-hit reconciliation fixture requires exact pre-armor explosion damage; raw="
                            + event.damage().rawDamage() + " velocity=" + frame.context().player().velocity()
                    );
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

            Observed observed = context.computeOnClient(minecraft -> observed(harness.runtime().capture()));
            assertMatched("accepted_server_explosion", expected.preArmorDamage(), observed);
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

    static void validateRepeatedMeleeCooldownStaysTrustedAcrossClientFrames(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        MobSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 340d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);

            BurstSequenceValidationSupport.prepareVictim(victim, 4f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);
            victim.connection.send(new ClientboundSetEntityMotionPacket(victim));

            Zombie attacker = EntityType.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (attacker == null) throw new AssertionError("could not create repeated-melee Zombie fixture");
            attacker.setBaby(false);
            attacker.setNoAi(true);
            attacker.setNoGravity(true);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.setPos(center.getX() + 0.5d, center.getY(), center.getZ() + 1.4d);
            level.addFreshEntity(attacker);
            return new MobSetup(victim.getUUID(), originalPosition, center, attacker.getId(), originals);
        });

        try {
            waitForVictimAndMob(context, setup, "minecraft:zombie");
            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            float rawDamage = context.computeOnClient(minecraft -> exactMeleeDamage(
                harness.runtime().capture(),
                setup.attackerId(),
                "repeated_melee"
            ));
            SurvivalValidationClientGameTest.assertClose("repeated_melee_raw", 3f, rawDamage, EPSILON);

            context.waitTick();
            MeleeServerResult first = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                Entity entity = ((ServerLevel) victim.level()).getEntity(setup.attackerId());
                if (!(entity instanceof Zombie attacker)) {
                    throw new AssertionError("repeated-melee Zombie disappeared before first hit");
                }
                victim.invulnerableTime = 0;
                boolean accepted = attacker.doHurtTarget((ServerLevel) victim.level(), victim);
                return new MeleeServerResult(accepted, victim.getHealth(), victim.invulnerableTime);
            });
            if (!first.accepted()) throw new AssertionError("source-faithful first Zombie hit was rejected");
            SurvivalValidationClientGameTest.assertClose("repeated_melee_first_health", 1f, first.health(), EPSILON);
            if (first.invulnerableTime() != 20) {
                throw new AssertionError("full Zombie hit must establish vanilla 20-tick cooldown; state=" + first);
            }
            context.waitFor(minecraft -> minecraft.player != null
                && Math.abs(minecraft.player.getHealth() - first.health()) <= EPSILON);

            Observed afterFirst = context.computeOnClient(minecraft -> observed(harness.runtime().capture()));
            assertMatched("repeated_melee_first", rawDamage, afterFirst);
            if (afterFirst.invulnerableTime() <= 10) {
                throw new AssertionError("first reconciled melee hit lost active cooldown; state=" + afterFirst);
            }

            context.waitTick();
            MeleeServerResult second = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                Entity entity = ((ServerLevel) victim.level()).getEntity(setup.attackerId());
                if (!(entity instanceof Zombie attacker)) {
                    throw new AssertionError("repeated-melee Zombie disappeared before second hit");
                }
                if (victim.invulnerableTime <= 10) {
                    throw new AssertionError("fixture reached second Zombie hit after cooldown boundary; invulnerableTime="
                        + victim.invulnerableTime);
                }
                boolean accepted = attacker.doHurtTarget((ServerLevel) victim.level(), victim);
                return new MeleeServerResult(accepted, victim.getHealth(), victim.invulnerableTime);
            });
            if (second.accepted()) {
                throw new AssertionError("equal second Zombie hit inside vanilla cooldown must be rejected; state=" + second);
            }
            SurvivalValidationClientGameTest.assertClose("repeated_melee_rejected_health", first.health(), second.health(), EPSILON);

            context.waitTick();
            PostRejectedMelee afterSecond = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                Observed hurt = observed(frame);
                boolean protectionCandidate = frame.candidates().stream()
                    .anyMatch(SurvivalAction.EquipDeathProtection.class::isInstance);
                return new PostRejectedMelee(hurt, protectionCandidate);
            });
            assertMatched("repeated_melee_rejected", rawDamage, afterSecond.hurt());
            if (afterSecond.hurt().invulnerableTime() <= 10) {
                throw new AssertionError("rejected equal melee hit must preserve trusted active cooldown; state=" + afterSecond.hurt());
            }
            if (afterSecond.protectionCandidate()) {
                throw new AssertionError(
                    "trusted server cooldown should suppress premature Totem arming for an equal lethal-on-paper melee hit"
                );
            }
        } finally {
            cleanupMobFixture(singleplayer, setup, false);
            context.waitTick();
        }
    }

    static void validateLavaAndMixedSourceContinuityAcrossClientFrames(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        MobSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 280d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);

            BurstSequenceValidationSupport.prepareVictim(victim, 20f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);
            victim.connection.send(new ClientboundSetEntityMotionPacket(victim));

            // Establish a high comparison amount so ordinary lava contact cannot race the baseline.
            // The production runtime is created only after this setup evidence has already arrived.
            if (!victim.hurtServer(level, victim.damageSources().generic(), 8f)) {
                throw new AssertionError("could not establish lava fixture setup cooldown");
            }
            victim.setHealth(20f);
            victim.invulnerableTime = 200;
            victim.setDeltaMovement(Vec3.ZERO);

            Vindicator attacker = EntityType.VINDICATOR.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (attacker == null) throw new AssertionError("could not create mixed-source Vindicator fixture");
            attacker.setNoAi(true);
            attacker.setNoGravity(true);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.setPos(center.getX() + 1.5d, center.getY(), center.getZ() + 0.5d);
            level.addFreshEntity(attacker);
            level.setBlockAndUpdate(center, Blocks.LAVA.defaultBlockState());
            return new MobSetup(victim.getUUID(), originalPosition, center, attacker.getId(), originals);
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && Math.abs(minecraft.player.getX() - (setup.center().getX() + 0.5d)) <= POSITION_EPSILON
                && Math.abs(minecraft.player.getY() - setup.center().getY()) <= POSITION_EPSILON
                && Math.abs(minecraft.player.getZ() - (setup.center().getZ() + 0.5d)) <= POSITION_EPSILON
                && Math.abs(minecraft.player.getHealth() - 20f) <= EPSILON
                && minecraft.player.isInLava()
                && minecraft.level.getEntity(setup.attackerId()) instanceof Vindicator);

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            MixedBaseline baseline = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                ThreatEvent lava = frame.actualTimeline().events().stream()
                    .filter(event -> event.id().equals("env:lava:1"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("live lava contact produced no tick-1 production threat"));
                if (Float.compare(lava.damage().rawDamage().min(), lava.damage().rawDamage().max()) != 0) {
                    throw new AssertionError("lava reconciliation fixture requires exact raw damage; raw=" + lava.damage().rawDamage());
                }
                float melee = exactMeleeDamage(frame, setup.attackerId(), "mixed_source");
                if (!(melee > lava.damage().rawDamage().max())) {
                    throw new AssertionError("mixed-source fixture requires stronger melee after lava; lava="
                        + lava.damage().rawDamage().max() + " melee=" + melee);
                }
                return new MixedBaseline(lava.damage().rawDamage().max(), melee);
            });
            SurvivalValidationClientGameTest.assertClose("lava_raw", 4f, baseline.lavaRaw(), EPSILON);
            SurvivalValidationClientGameTest.assertClose("vindicator_raw", 5f, baseline.meleeRaw(), EPSILON);

            context.waitTick();
            float lavaHealth = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                victim.invulnerableTime = 10;
                victim.lavaHurt();
                return victim.getHealth();
            });
            SurvivalValidationClientGameTest.assertClose("lava_full_hit_health", 16f, lavaHealth, EPSILON);
            context.waitFor(minecraft -> minecraft.player != null
                && Math.abs(minecraft.player.getHealth() - lavaHealth) <= EPSILON);

            Observed afterLava = context.computeOnClient(minecraft -> observed(harness.runtime().capture()));
            assertMatched("full_lava_contact", baseline.lavaRaw(), afterLava);
            if (afterLava.invulnerableTime() <= 10) {
                throw new AssertionError("full lava hit must establish trusted active cooldown; state=" + afterLava);
            }

            float afterRejectedLava = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                if (victim.invulnerableTime <= 10) {
                    throw new AssertionError("fixture reached repeated lava attempt after cooldown boundary; invulnerableTime="
                        + victim.invulnerableTime);
                }
                float before = victim.getHealth();
                victim.lavaHurt();
                if (Math.abs(victim.getHealth() - before) > EPSILON) {
                    throw new AssertionError("equal repeated lava contact bypassed vanilla hurt cooldown");
                }
                return victim.getHealth();
            });
            SurvivalValidationClientGameTest.assertClose("lava_rejected_health", lavaHealth, afterRejectedLava, EPSILON);

            context.waitTick();
            MixedBaseline current = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                Observed hurt = observed(frame);
                assertMatched("repeated_lava_contact", baseline.lavaRaw(), hurt);
                if (hurt.invulnerableTime() <= 10) {
                    throw new AssertionError("rejected lava contact must preserve trusted active cooldown; state=" + hurt);
                }
                return new MixedBaseline(baseline.lavaRaw(), exactMeleeDamage(frame, setup.attackerId(), "mixed_source_after_lava"));
            });

            context.waitTick();
            DifferentialServerResult differential = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                Entity entity = ((ServerLevel) victim.level()).getEntity(setup.attackerId());
                if (!(entity instanceof Vindicator attacker)) {
                    throw new AssertionError("mixed-source Vindicator disappeared before differential hit");
                }
                int beforeInvulnerable = victim.invulnerableTime;
                float beforeHealth = victim.getHealth();
                if (beforeInvulnerable <= 10) {
                    throw new AssertionError("fixture reached mixed-source hit after cooldown boundary; invulnerableTime="
                        + beforeInvulnerable);
                }
                boolean accepted = attacker.doHurtTarget((ServerLevel) victim.level(), victim);
                return new DifferentialServerResult(
                    accepted,
                    beforeHealth,
                    victim.getHealth(),
                    beforeInvulnerable,
                    victim.invulnerableTime
                );
            });
            if (!differential.accepted()) {
                throw new AssertionError("stronger mixed-source Vindicator hit was rejected; state=" + differential);
            }
            if (!(differential.healthAfter() < differential.healthBefore())) {
                throw new AssertionError("stronger mixed-source hit applied no differential damage; state=" + differential);
            }
            if (differential.invulnerableAfter() != differential.invulnerableBefore()) {
                throw new AssertionError(
                    "vanilla differential hit must not reset hurt cooldown to 20; state=" + differential
                );
            }
            context.waitFor(minecraft -> minecraft.player != null
                && Math.abs(minecraft.player.getHealth() - differential.healthAfter()) <= EPSILON);

            Observed afterMixed = context.computeOnClient(minecraft -> observed(harness.runtime().capture()));
            assertMatched("mixed_source_differential", current.meleeRaw(), afterMixed);
            if (afterMixed.invulnerableTime() <= 10) {
                throw new AssertionError("mixed-source differential lost trusted active cooldown; state=" + afterMixed);
            }
        } finally {
            cleanupMobFixture(singleplayer, setup, true);
            context.waitTick();
        }
    }

    private static void waitForVictimAndMob(ClientGameTestContext context, MobSetup setup, String expectedType) {
        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.level != null
            && Math.abs(minecraft.player.getX() - (setup.center().getX() + 0.5d)) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - setup.center().getY()) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - (setup.center().getZ() + 0.5d)) <= POSITION_EPSILON
            && minecraft.player.getDeltaMovement().lengthSqr() <= VELOCITY_EPSILON
            && minecraft.level.getEntity(setup.attackerId()) instanceof Mob mob
            && mob.getType().builtInRegistryHolder().getRegisteredName().equals(expectedType));
    }

    private static float exactMeleeDamage(
        dev.pixelied.survival.core.SurvivalEngine.EngineFrame frame,
        int attackerId,
        String fixture
    ) {
        ThreatEvent melee = frame.actualTimeline().events().stream()
            .filter(event -> event.id().equals("melee:" + attackerId))
            .findFirst()
            .orElseThrow(() -> new AssertionError(fixture + " produced no active exact-runtime melee event"));
        if (Float.compare(melee.damage().rawDamage().min(), melee.damage().rawDamage().max()) != 0) {
            throw new AssertionError(fixture + " requires exact melee raw damage; raw=" + melee.damage().rawDamage());
        }
        return melee.damage().rawDamage().max();
    }

    private static Observed observed(dev.pixelied.survival.core.SurvivalEngine.EngineFrame frame) {
        return new Observed(
            frame.context().player().hurtState().lastHurt().min(),
            frame.context().player().hurtState().lastHurt().max(),
            frame.context().player().hurtState().invulnerableTime(),
            frame.context().player().hurtState().confidence()
        );
    }

    private static void assertMatched(String id, float expectedLastHurt, Observed observed) {
        if (observed.confidence() != Confidence.MATCHED) {
            throw new AssertionError(id + " must reconcile into a trusted production hurt state; hurtState=" + observed);
        }
        SurvivalValidationClientGameTest.assertClose(id + "_last_hurt_min", expectedLastHurt, observed.lastHurtMin(), EPSILON);
        SurvivalValidationClientGameTest.assertClose(id + "_last_hurt_max", expectedLastHurt, observed.lastHurtMax(), EPSILON);
    }

    private static void cleanupMobFixture(TestSingleplayerContext singleplayer, MobSetup setup, boolean clearFire) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
            if (victim == null) return;
            ServerLevel level = (ServerLevel) victim.level();
            Entity attacker = level.getEntity(setup.attackerId());
            if (attacker != null) attacker.discard();
            restore(level, setup.originals());
            SurvivalValidationClientGameTest.reset(victim, 20f);
            if (clearFire) victim.clearFire();
            victim.setNoGravity(false);
            victim.teleportTo(
                setup.originalPosition().x,
                setup.originalPosition().y,
                setup.originalPosition().z
            );
        });
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

    private record MobSetup(
        UUID victimId,
        Vec3 originalPosition,
        BlockPos center,
        int attackerId,
        Map<BlockPos, BlockState> originals
    ) {
    }

    private record Expected(float preArmorDamage) {
    }

    private record MixedBaseline(float lavaRaw, float meleeRaw) {
    }

    private record MeleeServerResult(boolean accepted, float health, int invulnerableTime) {
    }

    private record DifferentialServerResult(
        boolean accepted,
        float healthBefore,
        float healthAfter,
        int invulnerableBefore,
        int invulnerableAfter
    ) {
    }

    private record PostRejectedMelee(Observed hurt, boolean protectionCandidate) {
    }

    private record Observed(float lastHurtMin, float lastHurtMax, int invulnerableTime, Confidence confidence) {
    }
}
