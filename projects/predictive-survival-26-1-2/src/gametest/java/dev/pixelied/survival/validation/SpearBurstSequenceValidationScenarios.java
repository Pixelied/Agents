package dev.pixelied.survival.validation;

import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import dev.pixelied.survival.timeline.ThreatKind;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exact-runtime proof for the 26.1.2 ServerboundPlayerActionPacket.STAB spear path. */
final class SpearBurstSequenceValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    private static final double RANGE_SCAN_STEP = 0.025d;
    private static final double APPROACH_PER_TICK = 0.25d;
    private static final double VELOCITY_EPSILON = 0.02d;

    private SpearBurstSequenceValidationScenarios() {
    }

    static void validatePiercingSpearCrossesRayAndStabsAtFirstLegalTick(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel)victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 320d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);

            BurstSequenceValidationSupport.prepareVictim(victim, 1f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);

            BurstSequenceValidationSupport.AttackerHandle handle =
                BurstSequenceValidationSupport.createMockAttacker(server, victim);
            ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, handle);
            attacker.getInventory().clearContent();
            attacker.getInventory().setSelectedSlot(0);
            attacker.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SPEAR));
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.setNoGravity(true);
            attacker.setDeltaMovement(Vec3.ZERO);
            faceVictim(attacker);

            Vec3 approachVelocity = new Vec3(0d, 0d, -APPROACH_PER_TICK);
            Vec3 initialPosition = findOneTickOutsideStabRange(attacker, victim, center, approachVelocity);
            attacker.teleportTo(initialPosition.x, initialPosition.y, initialPosition.z);
            attacker.setDeltaMovement(approachVelocity);
            attacker.setKnownMovement(approachVelocity);
            faceVictim(attacker);
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);
            victim.connection.send(ClientboundEntityPositionSyncPacket.of(attacker));
            victim.connection.send(new ClientboundSetEntityMotionPacket(attacker));

            return new Setup(
                victim.getUUID(),
                originalPosition,
                center,
                initialPosition,
                approachVelocity,
                originals,
                handle
            );
        });

        try {
            waitForClientPosition(context, setup.center());
            BurstSequenceValidationSupport.waitForClientAttacker(context, setup.attacker());
            context.waitFor(minecraft -> {
                if (minecraft.level == null) return false;
                if (!(minecraft.level.getEntity(setup.attacker().entityId()) instanceof net.minecraft.world.entity.player.Player remote)) {
                    return false;
                }
                return remote.getMainHandItem().is(Items.NETHERITE_SPEAR)
                    && Math.abs(remote.getX() - setup.initialPosition().x) <= POSITION_EPSILON
                    && Math.abs(remote.getY() - setup.initialPosition().y) <= POSITION_EPSILON
                    && Math.abs(remote.getZ() - setup.initialPosition().z) <= POSITION_EPSILON
                    && Math.abs(remote.getDeltaMovement().z - setup.approachVelocity().z) <= VELOCITY_EPSILON;
            });

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            Precursor precursor = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                boolean activeSpear = frame.actualTimeline().events().stream().anyMatch(event ->
                    event.kind() == ThreatKind.MELEE
                        && event.id().equals("spear:" + setup.attacker().entityId())
                );
                LethalOpportunity opportunity = frame.opportunities().stream()
                    .filter(candidate -> candidate.family() == OpportunityFamily.MELEE)
                    .filter(candidate -> Integer.toString(setup.attacker().entityId())
                        .equals(candidate.evidence().get("attacker_id")))
                    .findFirst()
                    .orElse(null);
                boolean piercingObserved = frame.context().world().entities().stream()
                    .filter(candidate -> candidate.id().equals(Integer.toString(setup.attacker().entityId())))
                    .anyMatch(candidate -> Boolean.parseBoolean(
                        candidate.properties().getOrDefault("piercing_weapon", "false")
                    ));
                return new Precursor(activeSpear, opportunity, piercingObserved);
            });
            if (!precursor.piercingObserved()) {
                throw new AssertionError("production runtime did not expose the visible spear's PIERCING_WEAPON component");
            }
            if (precursor.activeSpear()) {
                throw new AssertionError("spear approach test began with an already-active STAB threat outside the real weapon ray");
            }
            if (precursor.opportunity() == null) {
                throw new AssertionError("approaching spear produced no pre-range-entry opportunity");
            }
            if (!"piercing_weapon".equals(precursor.opportunity().evidence().get("attack_profile"))) {
                throw new AssertionError(
                    "spear approach did not use piercing reach: " + precursor.opportunity().evidence()
                );
            }
            int entryTick = Integer.parseInt(precursor.opportunity().evidence().getOrDefault("entry_tick", "-1"));
            if (entryTick < 1) {
                throw new AssertionError("spear approach opportunity exposed invalid entry tick " + entryTick);
            }

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                "piercing_spear_range_entry"
            );

            Outcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                ItemStack spear = attacker.getMainHandItem();
                PiercingWeapon piercing = spear.get(DataComponents.PIERCING_WEAPON);
                if (piercing == null) throw new AssertionError("netherite spear lost PIERCING_WEAPON component");
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost precursor-established protection before spear range entry");
                }

                attacker.teleportTo(
                    setup.initialPosition().x,
                    setup.initialPosition().y,
                    setup.initialPosition().z
                );
                attacker.setDeltaMovement(setup.approachVelocity());
                attacker.setKnownMovement(setup.approachVelocity());
                faceVictim(attacker);
                if (canPiercingHit(attacker, victim)) {
                    throw new AssertionError("server spear could already STAB before projected range entry");
                }

                for (int tick = 1; tick <= entryTick; tick++) {
                    Vec3 projected = setup.initialPosition().add(setup.approachVelocity().scale(tick));
                    attacker.teleportTo(projected.x, projected.y, projected.z);
                    attacker.setKnownMovement(setup.approachVelocity());
                    faceVictim(attacker);
                    boolean inRange = canPiercingHit(attacker, victim);
                    if (tick < entryTick && inRange) {
                        throw new AssertionError(
                            "server spear STAB became legal before predictor entry tick: tick=" + tick
                                + " predicted=" + entryTick
                        );
                    }
                    if (tick == entryTick && !inRange) {
                        throw new AssertionError(
                            "server spear STAB was still illegal at predictor entry tick " + entryTick
                        );
                    }
                }

                if (attacker.cannotAttackWithItem(spear, 5)) {
                    throw new AssertionError("server spear attack charge was not ready at first legal STAB tick");
                }
                victim.invulnerableTime = 0;
                victim.setHealth(1f);
                piercing.attack(attacker, EquipmentSlot.MAINHAND);
                return new Outcome(
                    victim.getHealth(),
                    BurstSequenceValidationSupport.protectionConsumed(victim)
                );
            });

            SurvivalValidationClientGameTest.assertClose("piercing_spear_range_entry_pop", 1f, outcome.health(), EPSILON);
            if (!outcome.protectionConsumed()) {
                throw new AssertionError("first-legal-tick spear STAB did not consume server-authoritative protection");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
                if (victim != null) {
                    ServerLevel level = (ServerLevel)victim.level();
                    restore(level, setup.originals());
                    SurvivalValidationClientGameTest.reset(victim, 20f);
                    victim.setNoGravity(false);
                    victim.teleportTo(
                        setup.originalPosition().x,
                        setup.originalPosition().y,
                        setup.originalPosition().z
                    );
                }
                BurstSequenceValidationSupport.removeMockAttacker(server, setup.attacker());
            });
            context.waitTick();
        }
    }

    private static Vec3 findOneTickOutsideStabRange(
        ServerPlayer attacker,
        ServerPlayer victim,
        BlockPos center,
        Vec3 approachVelocity
    ) {
        double x = center.getX() + 0.5d;
        double y = center.getY();
        double victimZ = center.getZ() + 0.5d;
        for (double distance = 4.0d; distance <= 9.0d; distance += RANGE_SCAN_STEP) {
            double outsideZ = victimZ + distance;
            attacker.teleportTo(x, y, outsideZ);
            attacker.setKnownMovement(approachVelocity);
            faceVictim(attacker);
            boolean outside = !canPiercingHit(attacker, victim);

            attacker.teleportTo(x, y, outsideZ + approachVelocity.z);
            attacker.setKnownMovement(approachVelocity);
            faceVictim(attacker);
            boolean oneTickInRange = canPiercingHit(attacker, victim);
            if (outside && oneTickInRange) return new Vec3(x, y, outsideZ);
        }
        throw new AssertionError("could not resolve a one-tick spear STAB boundary from vanilla ray rules");
    }

    private static boolean canPiercingHit(ServerPlayer attacker, ServerPlayer victim) {
        ItemStack spear = attacker.getMainHandItem();
        PiercingWeapon piercing = spear.get(DataComponents.PIERCING_WEAPON);
        AttackRange range = spear.get(DataComponents.ATTACK_RANGE);
        if (piercing == null || range == null) return false;
        List<EntityHitResult> hits = ProjectileUtil.getHitEntitiesAlong(
            attacker,
            range,
            target -> PiercingWeapon.canHitEntity(attacker, target),
            ClipContext.Block.COLLIDER
        ).map(ignored -> List.of(), result -> List.copyOf(result));
        return hits.stream().anyMatch(hit -> hit.getEntity() == victim);
    }

    private static void faceVictim(ServerPlayer attacker) {
        attacker.setYRot(180f);
        attacker.setYHeadRot(180f);
        attacker.setXRot(0f);
    }

    private static Map<BlockPos, BlockState> clearArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 10; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
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

    private static void waitForClientPosition(ClientGameTestContext context, BlockPos center) {
        context.waitFor(minecraft -> minecraft.player != null
            && Math.abs(minecraft.player.getX() - (center.getX() + 0.5d)) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - center.getY()) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - (center.getZ() + 0.5d)) <= POSITION_EPSILON);
    }

    private record Setup(
        UUID victimId,
        Vec3 originalPosition,
        BlockPos center,
        Vec3 initialPosition,
        Vec3 approachVelocity,
        Map<BlockPos, BlockState> originals,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
    }

    private record Precursor(
        boolean activeSpear,
        LethalOpportunity opportunity,
        boolean piercingObserved
    ) {
    }

    private record Outcome(
        float health,
        boolean protectionConsumed
    ) {
    }
}
