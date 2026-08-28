package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.threat.ExplosionPredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTransition;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exact-runtime proofs for explosion source causality in production planning. */
final class ExplosionCausalityValidationScenarios {
    private ExplosionCausalityValidationScenarios() {
    }

    static void validateAdjacentObservedCrystalsNeedOnlyOneProtection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 280d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);

            BurstSequenceValidationSupport.prepareVictim(victim, 5f);
            victim.getInventory().setItem(2, new ItemStack(Items.TOTEM_OF_UNDYING));
            victim.containerMenu.broadcastChanges();
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);

            double x = center.getX() + 0.5d;
            double y = center.getY();
            EndCrystal near = new EndCrystal(level, x, y, center.getZ() + 2.5d);
            EndCrystal far = new EndCrystal(level, x, y, center.getZ() + 5.5d);
            level.addFreshEntity(near);
            level.addFreshEntity(far);

            return new Setup(
                victim.getUUID(),
                originalPosition,
                center,
                near.getId(),
                far.getId(),
                originals
            );
        });

        try {
            // Carried-slot selection is client-authoritative in 26.1.2. A prior validation may leave
            // the real client on slot 1 even if the server fixture writes selectedSlot=0 directly.
            BurstSequenceValidationSupport.ensureSelectedSlot(
                context,
                singleplayer,
                setup.victimId(),
                0,
                "adjacent_crystal_causality"
            );
            waitForFixture(context, singleplayer, setup);

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            Diagnostics diagnostics = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture(RescuePolicy.totemOnly());
                String nearId = "explosion:" + setup.nearCrystalId();
                String farId = "explosion:" + setup.farCrystalId();
                boolean sawNear = frame.actualTimeline().events().stream().anyMatch(event -> event.id().equals(nearId));
                boolean sawFar = frame.actualTimeline().events().stream().anyMatch(event -> event.id().equals(farId));
                long protectionCandidates = frame.candidates().stream()
                    .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
                    .count();
                return new Diagnostics(
                    sawNear,
                    sawFar,
                    protectionCandidates,
                    frame.actualTimeline().events().stream()
                        .map(event -> event.id() + "=" + event.damage().rawDamage())
                        .toList()
                        .toString(),
                    frame.candidates().toString()
                );
            });

            if (!diagnostics.sawNear() || !diagnostics.sawFar()) {
                throw new AssertionError(
                    "production frame did not contain both observed crystal threats; actual=" + diagnostics.actualThreats()
                );
            }
            if (diagnostics.protectionCandidates() != 1L) {
                throw new AssertionError(
                    "adjacent observed crystals that destroy one another must require exactly one protection route; "
                        + "protectionCandidates=" + diagnostics.protectionCandidates()
                        + " actual=" + diagnostics.actualThreats()
                        + " candidates=" + diagnostics.candidates()
                );
            }

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                "adjacent_crystal_causal_dispatch"
            );
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim == null) return;
                ServerLevel level = (ServerLevel) victim.level();
                Entity near = level.getEntity(setup.nearCrystalId());
                Entity far = level.getEntity(setup.farCrystalId());
                if (near != null) near.discard();
                if (far != null) far.discard();
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

    static void validateExplosionCreatedTntMatchesPredictedShortFuseWindow(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        TntSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 300d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearTntArena(level, center);
            boolean originalTntExplodes = level.getGameRules().get(GameRules.TNT_EXPLODES);
            level.getGameRules().set(GameRules.TNT_EXPLODES, true, server);

            BurstSequenceValidationSupport.prepareVictim(victim, 20f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);

            BlockPos tntPos = center.offset(0, 0, 9);
            level.setBlockAndUpdate(tntPos, Blocks.TNT.defaultBlockState());
            EndCrystal crystal = new EndCrystal(
                level,
                center.getX() + 0.5d,
                center.getY() + 0.5d,
                center.getZ() + 8.5d
            );
            level.addFreshEntity(crystal);

            return new TntSetup(
                victim.getUUID(),
                originalPosition,
                center,
                tntPos,
                crystal.getId(),
                originalTntExplodes,
                originals
            );
        });

        try {
            waitForTntFixture(context, setup);
            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            TntPrediction prediction = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture(RescuePolicy.totemOnly());
                String sourceEventId = "explosion:" + setup.crystalId();
                ThreatEvent source = frame.actualTimeline().events().stream()
                    .filter(event -> event.id().equals(sourceEventId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                        "production frame did not contain TNT-chain source crystal; actual="
                            + frame.actualTimeline().events()
                    ));
                var causal = new ExplosionPredictor().causalize(frame.context(), frame.actualTimeline());
                List<ThreatTransition.SpawnThreat> spawns = causal.transitionsAfter(source.id()).stream()
                    .filter(ThreatTransition.SpawnThreat.class::isInstance)
                    .map(ThreatTransition.SpawnThreat.class::cast)
                    .filter(spawn -> spawn.event().sourcePosition().map(position ->
                        Math.abs(position.x() - (setup.tntPos().getX() + 0.5d)) < 1.0E-9d
                            && Math.abs(position.y() - (setup.tntPos().getY() + 0.5d)) < 1.0E-9d
                            && Math.abs(position.z() - (setup.tntPos().getZ() + 0.5d)) < 1.0E-9d
                    ).orElse(false))
                    .toList();
                if (spawns.size() != 1) {
                    throw new AssertionError(
                        "production causalizer did not declare exactly one TNT spawn branch; transitions="
                            + causal.transitionsAfter(source.id())
                    );
                }
                ThreatEvent spawned = spawns.getFirst().event();
                return new TntPrediction(
                    spawned.impact(),
                    spawned.confidence(),
                    spawned.damage().rawDamage()
                );
            });

            if (!prediction.impact().equals(new TickWindow(10, 29))) {
                throw new AssertionError("spawned TNT prediction expected fuse [10,29], got " + prediction.impact());
            }
            if (prediction.confidence() != Confidence.POTENTIAL) {
                throw new AssertionError("spawned TNT prediction must preserve hidden branch uncertainty");
            }
            if (prediction.rawDamage().min() != 0f || Math.abs(prediction.rawDamage().max() - 57f) > 0.0001f) {
                throw new AssertionError(
                    "spawned TNT prediction expected fail-closed raw range [0,57], got " + prediction.rawDamage()
                );
            }

            TntOutcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerLevel level = (ServerLevel) victim.level();
                Entity entity = level.getEntity(setup.crystalId());
                if (!(entity instanceof EndCrystal crystal)) {
                    throw new AssertionError("TNT-chain source crystal disappeared before detonation");
                }
                victim.invulnerableTime = 0;
                boolean accepted = crystal.hurtServer(level, victim.damageSources().playerAttack(victim), 1f);
                AABB search = new AABB(
                    setup.tntPos().getX() - 1d,
                    setup.tntPos().getY() - 1d,
                    setup.tntPos().getZ() - 1d,
                    setup.tntPos().getX() + 2d,
                    setup.tntPos().getY() + 2d,
                    setup.tntPos().getZ() + 2d
                );
                List<PrimedTnt> primed = level.getEntitiesOfClass(PrimedTnt.class, search);
                if (primed.size() != 1) {
                    throw new AssertionError(
                        "real crystal explosion expected exactly one explosion-primed TNT, found " + primed.size()
                    );
                }
                return new TntOutcome(
                    accepted,
                    level.getBlockState(setup.tntPos()).isAir(),
                    primed.getFirst().getFuse()
                );
            });

            if (!outcome.crystalAttackAccepted()) {
                throw new AssertionError("real EndCrystal detonation was rejected in TNT-chain fixture");
            }
            if (!outcome.sourceTntRemoved()) {
                throw new AssertionError("real explosion did not replace the hit TNT block with PrimedTnt");
            }
            if (outcome.fuse() < prediction.impact().earliest() || outcome.fuse() > prediction.impact().latest()) {
                throw new AssertionError(
                    "actual explosion-primed TNT fuse fell outside predicted [10,29] window; actual=" + outcome.fuse()
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim == null) return;
                ServerLevel level = (ServerLevel) victim.level();
                Entity crystal = level.getEntity(setup.crystalId());
                if (crystal != null) crystal.discard();
                AABB cleanup = new AABB(
                    setup.center().getX() - 4d,
                    setup.center().getY() - 3d,
                    setup.center().getZ() - 4d,
                    setup.center().getX() + 5d,
                    setup.center().getY() + 5d,
                    setup.center().getZ() + 13d
                );
                for (PrimedTnt tnt : level.getEntitiesOfClass(PrimedTnt.class, cleanup)) {
                    tnt.discard();
                }
                level.getGameRules().set(GameRules.TNT_EXPLODES, setup.originalTntExplodes(), server);
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

    private static void waitForFixture(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        Setup setup
    ) {
        ClientFixture last = null;
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            last = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    return new ClientFixture(null, -1, false, false, false, false, "player-or-level-null");
                }
                return new ClientFixture(
                    minecraft.player.position(),
                    minecraft.player.getInventory().getSelectedSlot(),
                    minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING),
                    minecraft.player.getInventory().getItem(2).is(Items.TOTEM_OF_UNDYING),
                    minecraft.level.getEntity(setup.nearCrystalId()) instanceof EndCrystal,
                    minecraft.level.getEntity(setup.farCrystalId()) instanceof EndCrystal,
                    "slot1=" + minecraft.player.getInventory().getItem(1)
                        + ",slot2=" + minecraft.player.getInventory().getItem(2)
                );
            });
            if (last.ready()) return;
            context.waitTick();
        }

        String server = singleplayer.getServer().computeOnServer(minecraftServer -> {
            ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(minecraftServer, setup.victimId());
            ServerLevel level = (ServerLevel) victim.level();
            Entity near = level.getEntity(setup.nearCrystalId());
            Entity far = level.getEntity(setup.farCrystalId());
            return "pos=" + victim.position()
                + ",selected=" + victim.getInventory().getSelectedSlot()
                + ",slot1=" + victim.getInventory().getItem(1)
                + ",slot2=" + victim.getInventory().getItem(2)
                + ",near=" + describeEntity(near)
                + ",far=" + describeEntity(far);
        });
        throw new AssertionError("adjacent-crystal fixture did not synchronize; client=" + last + "; server={" + server + "}");
    }

    private static void waitForTntFixture(ClientGameTestContext context, TntSetup setup) {
        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.level != null
            && Math.abs(minecraft.player.getX() - (setup.center().getX() + 0.5d)) <= 0.05d
            && Math.abs(minecraft.player.getY() - setup.center().getY()) <= 0.05d
            && Math.abs(minecraft.player.getZ() - (setup.center().getZ() + 0.5d)) <= 0.05d
            && minecraft.level.getBlockState(setup.tntPos()).is(Blocks.TNT)
            && minecraft.level.getEntity(setup.crystalId()) instanceof EndCrystal);
    }

    private static String describeEntity(Entity entity) {
        return entity == null ? "null" : entity.getClass().getSimpleName() + "@" + entity.position();
    }

    private static Map<BlockPos, BlockState> clearArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 8; dz++) {
                for (int dy = -2; dy <= 4; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return Map.copyOf(originals);
    }

    private static Map<BlockPos, BlockState> clearTntArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 11; dz++) {
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
        int nearCrystalId,
        int farCrystalId,
        Map<BlockPos, BlockState> originals
    ) {
    }

    private record TntSetup(
        UUID victimId,
        Vec3 originalPosition,
        BlockPos center,
        BlockPos tntPos,
        int crystalId,
        boolean originalTntExplodes,
        Map<BlockPos, BlockState> originals
    ) {
    }

    private record ClientFixture(
        Vec3 position,
        int selectedSlot,
        boolean slot1Totem,
        boolean slot2Totem,
        boolean nearCrystal,
        boolean farCrystal,
        String inventory
    ) {
        private boolean ready() {
            return selectedSlot == 0 && slot1Totem && slot2Totem && nearCrystal && farCrystal;
        }
    }

    private record Diagnostics(
        boolean sawNear,
        boolean sawFar,
        long protectionCandidates,
        String actualThreats,
        String candidates
    ) {
    }

    private record TntPrediction(TickWindow impact, Confidence confidence, DamageRange rawDamage) {
    }

    private record TntOutcome(boolean crystalAttackAccepted, boolean sourceTntRemoved, int fuse) {
    }
}
