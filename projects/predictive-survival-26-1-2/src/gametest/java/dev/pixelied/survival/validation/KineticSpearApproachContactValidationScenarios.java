package dev.pixelied.survival.validation;

import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Exact-runtime proof that future spear approach can pre-arm protection before Kinetic contact. */
final class KineticSpearApproachContactValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double APPROACH_SPEED_PER_TICK = 0.5d;
    private static final double KINETIC_KNOWN_MOVEMENT_PER_TICK = 0.25d;

    private KineticSpearApproachContactValidationScenarios() {
    }

    static void validateFirstKineticDamageTickPopsWhileDiscreteStabIsUnready(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            Vec3 originalPosition = victim.position();
            BurstSequenceValidationSupport.prepareVictim(victim, 1f);
            Vec3 victimPosition = new Vec3(originalPosition.x, 322d, originalPosition.z);
            victim.teleportTo(victimPosition.x, victimPosition.y, victimPosition.z);
            victim.setKnownMovement(Vec3.ZERO);

            BurstSequenceValidationSupport.AttackerHandle handle =
                BurstSequenceValidationSupport.createMockAttacker(server, victim);
            ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, handle);
            attacker.getInventory().clearContent();
            attacker.getInventory().setSelectedSlot(0);
            attacker.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SPEAR));
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.setNoGravity(true);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.setKnownMovement(Vec3.ZERO);

            // Start outside current spear range. Production must pre-arm from the synchronized
            // future approach, not from an impossible tick-0 rescue against an already-legal STAB.
            Vec3 approachPosition = victimPosition.add(0d, 0d, 12d);
            attacker.teleportTo(approachPosition.x, approachPosition.y, approachPosition.z);
            faceVictim(attacker, victim);
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);

            attacker.startUsingItem(InteractionHand.MAIN_HAND);
            attacker.doTick();
            if (attacker.getTicksUsingItem() != 1) {
                throw new AssertionError(
                    "kinetic approach fixture did not reach exactly one authoritative use tick; ticks="
                        + attacker.getTicksUsingItem()
                );
            }
            if (victim.getHealth() != 1f) {
                throw new AssertionError("far Kinetic spear damaged victim before approach: health=" + victim.getHealth());
            }

            faceVictim(attacker, victim);
            Vec3 approachVelocity = attacker.getLookAngle().scale(APPROACH_SPEED_PER_TICK);
            attacker.setDeltaMovement(approachVelocity);
            attacker.setKnownMovement(approachVelocity);
            victim.connection.send(ClientboundEntityPositionSyncPacket.of(attacker));
            victim.connection.send(new ClientboundSetEntityMotionPacket(attacker));
            var values = attacker.getEntityData().getNonDefaultValues();
            if (values == null) {
                throw new AssertionError("kinetic approach use did not dirty synchronized living-entity state");
            }
            victim.connection.send(new ClientboundSetEntityDataPacket(attacker.getId(), values));

            return new Setup(victim.getUUID(), originalPosition, victimPosition, handle);
        });

        try {
            waitForClientApproachAndInventory(context, setup.attacker());
            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);

            PreArm preArm = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                String attackerId = Integer.toString(setup.attacker().entityId());
                var snapshot = frame.context().world().entities().stream()
                    .filter(candidate -> candidate.id().equals(attackerId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("kinetic approach attacker missing from production frame"));
                boolean currentSpearThreat = frame.actualTimeline().events().stream().anyMatch(event ->
                    event.id().equals("spear:" + attackerId) || event.id().equals("melee:" + attackerId)
                );
                LethalOpportunity approach = frame.opportunities().stream()
                    .filter(candidate -> candidate.family() == OpportunityFamily.MELEE)
                    .filter(candidate -> candidate.id().equals("opportunity:melee_approach:" + attackerId))
                    .findFirst()
                    .orElse(null);
                boolean planningContainsApproach = approach != null && frame.planningTimeline().events().stream()
                    .anyMatch(event -> event.id().equals(approach.projectedThreat().id()));
                boolean equipCandidate = frame.candidates().stream()
                    .anyMatch(SurvivalAction.EquipDeathProtection.class::isInstance);
                String inventory = minecraft.player == null
                    ? "player=null"
                    : "selected=" + minecraft.player.getInventory().getSelectedSlot()
                        + ",slot0=" + minecraft.player.getInventory().getItem(0)
                        + ",slot1=" + minecraft.player.getInventory().getItem(1)
                        + ",main=" + minecraft.player.getMainHandItem()
                        + ",off=" + minecraft.player.getOffhandItem();
                return new PreArm(
                    currentSpearThreat,
                    approach,
                    planningContainsApproach,
                    equipCandidate,
                    snapshot.properties().getOrDefault("spear_kinetic", "false"),
                    snapshot.properties().getOrDefault("spear_kinetic_delay_ticks", "missing"),
                    frame.actualTimeline().events().toString(),
                    frame.opportunities().toString(),
                    frame.candidates().toString(),
                    inventory
                );
            });

            if (!"true".equals(preArm.spearKinetic())) {
                throw new AssertionError("production frame lost synchronized Kinetic spear marker: " + preArm);
            }
            if (preArm.currentSpearThreat()) {
                throw new AssertionError("Kinetic pre-arm fixture started inside a current spear threat: " + preArm);
            }
            if (preArm.approach() == null || preArm.approach().projectedThreat().impact().earliest() < 1L) {
                throw new AssertionError("production frame produced no future spear-approach deadline: " + preArm);
            }
            if (!preArm.planningContainsApproach()) {
                throw new AssertionError("future spear approach was not carried into planning timeline: " + preArm);
            }
            if (!preArm.equipCandidate()) {
                throw new AssertionError("future spear approach produced no death-protection candidate: " + preArm);
            }

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                "kinetic_spear_approach"
            );

            // Once the production engine has established server authority, put the same vanilla
            // spear into exact Kinetic contact range. The mock connection does not auto-tick its
            // player, so authoritative use time is still exactly where the fixture left it.
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost production-armed protection before Kinetic contact setup");
                }
                Vec3 contactPosition = setup.victimPosition().add(0d, 0d, 3d);
                attacker.teleportTo(contactPosition.x, contactPosition.y, contactPosition.z);
                attacker.setDeltaMovement(Vec3.ZERO);
                faceVictim(attacker, victim);
                attacker.setKnownMovement(attacker.getLookAngle().scale(KINETIC_KNOWN_MOVEMENT_PER_TICK));
                victim.setKnownMovement(Vec3.ZERO);
                victim.connection.send(ClientboundEntityPositionSyncPacket.of(attacker));
                victim.connection.send(new ClientboundSetEntityMotionPacket(attacker));
                if (!canPiercingHit(attacker, victim)) {
                    throw new AssertionError("Kinetic contact setup is outside the real spear ray");
                }
            });

            Boundary boundary = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                ItemStack spear = attacker.getMainHandItem();
                KineticWeapon kinetic = spear.get(DataComponents.KINETIC_WEAPON);
                if (kinetic == null) throw new AssertionError("netherite spear lost KINETIC_WEAPON component");
                KineticWeapon.Condition damage = kinetic.damageConditions()
                    .orElseThrow(() -> new AssertionError("netherite spear lost Kinetic damage condition"));

                while (attacker.getTicksUsingItem() < kinetic.delayTicks()) {
                    attacker.setDeltaMovement(Vec3.ZERO);
                    faceVictim(attacker, victim);
                    attacker.setKnownMovement(attacker.getLookAngle().scale(KINETIC_KNOWN_MOVEMENT_PER_TICK));
                    victim.setKnownMovement(Vec3.ZERO);
                    attacker.doTick();
                    if (!BurstSequenceValidationSupport.protectedInHand(victim) || victim.getHealth() != 1f) {
                        throw new AssertionError(
                            "Kinetic spear affected victim before first legal damage tick; useTicks="
                                + attacker.getTicksUsingItem() + " health=" + victim.getHealth()
                        );
                    }
                }
                if (attacker.getTicksUsingItem() != kinetic.delayTicks()) {
                    throw new AssertionError(
                        "fixture failed to stop at Kinetic delay boundary; ticks=" + attacker.getTicksUsingItem()
                            + " delay=" + kinetic.delayTicks()
                    );
                }
                if (attacker.stabbedEntities(entity -> entity == victim) != 0) {
                    throw new AssertionError("Kinetic contact memory populated before first legal damage tick");
                }
                if (!canPiercingHit(attacker, victim)) {
                    throw new AssertionError("victim left the real spear ray before first Kinetic damage tick");
                }

                // Make ordinary STAB explicitly illegal. KineticWeapon does not consult attack
                // strength, so the next doTick can only prove the use-tick Kinetic damage path.
                attacker.resetAttackStrengthTicker();
                boolean discreteStabRejected = attacker.cannotAttackWithItem(spear, 5);
                attacker.setDeltaMovement(Vec3.ZERO);
                faceVictim(attacker, victim);
                attacker.setKnownMovement(attacker.getLookAngle().scale(KINETIC_KNOWN_MOVEMENT_PER_TICK));
                victim.setKnownMovement(Vec3.ZERO);
                Vec3 look = attacker.getLookAngle();
                double attackerSpeed = look.dot(KineticWeapon.getMotion(attacker));
                double targetSpeed = look.dot(KineticWeapon.getMotion(victim));
                double relativeSpeed = Math.max(0d, attackerSpeed - targetSpeed);
                boolean damageConditionReady = damage.test(
                    attacker.getTicksUsingItem(),
                    attackerSpeed,
                    relativeSpeed,
                    1d
                );
                return new Boundary(
                    attacker.getTicksUsingItem(),
                    discreteStabRejected,
                    damageConditionReady,
                    attackerSpeed,
                    targetSpeed,
                    relativeSpeed,
                    attacker.getAttackStrengthScale(0.5f)
                );
            });

            if (!boundary.discreteStabRejected()) {
                throw new AssertionError("discrete spear STAB was unexpectedly legal at Kinetic boundary: " + boundary);
            }
            if (!boundary.damageConditionReady()) {
                throw new AssertionError("Kinetic damage condition was not ready at first legal use tick: " + boundary);
            }

            Outcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost protection immediately before first Kinetic damage tick");
                }
                attacker.setDeltaMovement(Vec3.ZERO);
                faceVictim(attacker, victim);
                attacker.setKnownMovement(attacker.getLookAngle().scale(KINETIC_KNOWN_MOVEMENT_PER_TICK));
                victim.setKnownMovement(Vec3.ZERO);
                victim.invulnerableTime = 0;
                victim.setHealth(1f);
                attacker.doTick();
                return new Outcome(
                    attacker.getTicksUsingItem(),
                    victim.getHealth(),
                    victim.isAlive(),
                    BurstSequenceValidationSupport.protectionConsumed(victim),
                    attacker.stabbedEntities(entity -> entity == victim)
                );
            });

            if (outcome.serverUseTicks() != boundary.serverUseTicks() + 1) {
                throw new AssertionError("Kinetic damage did not occur on the immediate next use tick: " + outcome);
            }
            if (!outcome.alive()) {
                throw new AssertionError("victim died on first Kinetic damage tick despite production-armed protection: " + outcome);
            }
            if (!outcome.protectionConsumed()) {
                throw new AssertionError("first Kinetic damage tick did not consume server-authoritative protection: " + outcome);
            }
            if (outcome.kineticContacts() != 1) {
                throw new AssertionError("first Kinetic damage tick did not populate Kinetic contact memory: " + outcome);
            }
            SurvivalValidationClientGameTest.assertClose(
                "kinetic_spear_first_damage_tick_pop",
                1f,
                outcome.health(),
                EPSILON
            );
        } finally {
            cleanup(context, singleplayer, setup);
        }
    }

    private static void waitForClientApproachAndInventory(
        ClientGameTestContext context,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
        context.waitFor(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) return false;
            Entity entity = minecraft.level.getEntity(attacker.entityId());
            if (!(entity instanceof net.minecraft.world.entity.player.Player remote)) return false;
            return remote.getMainHandItem().is(Items.NETHERITE_SPEAR)
                && remote.getMainHandItem().has(DataComponents.KINETIC_WEAPON)
                && remote.isUsingItem()
                && remote.getUsedItemHand() == InteractionHand.MAIN_HAND
                && remote.getDeltaMovement().lengthSqr() >= APPROACH_SPEED_PER_TICK * APPROACH_SPEED_PER_TICK * 0.5d
                && minecraft.player.getInventory().getSelectedSlot() == 0
                && minecraft.player.getInventory().getItem(0).is(Items.STICK)
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                && minecraft.player.getOffhandItem().isEmpty();
        });
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

    private static void faceVictim(ServerPlayer attacker, ServerPlayer victim) {
        Vec3 eye = attacker.getEyePosition();
        AttackRange range = attacker.getMainHandItem().get(DataComponents.ATTACK_RANGE);
        double margin = range == null ? 0d : range.hitboxMargin();
        var targetBox = victim.getBoundingBox().inflate(margin);
        Vec3 target = new Vec3(
            Math.max(targetBox.minX, Math.min(eye.x, targetBox.maxX)),
            Math.max(targetBox.minY, Math.min(eye.y, targetBox.maxY)),
            Math.max(targetBox.minZ, Math.min(eye.z, targetBox.maxZ))
        );
        if (target.distanceToSqr(eye) <= 1.0E-12d) target = targetBox.getCenter();
        Vec3 delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90d);
        float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
        attacker.setYRot(yaw);
        attacker.setYHeadRot(yaw);
        attacker.setXRot(pitch);
    }

    private static void cleanup(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        Setup setup
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer victim = server.getPlayerList().getPlayer(setup.victimId());
            if (victim != null) {
                SurvivalValidationClientGameTest.reset(victim, 20f);
                victim.setNoGravity(false);
                victim.getInventory().clearContent();
                victim.getInventory().setSelectedSlot(0);
                victim.teleportTo(
                    setup.originalPosition().x,
                    setup.originalPosition().y,
                    setup.originalPosition().z
                );
                victim.containerMenu.broadcastChanges();
            }
            BurstSequenceValidationSupport.removeMockAttacker(server, setup.attacker());
        });
        context.waitTick();
    }

    private record Setup(
        UUID victimId,
        Vec3 originalPosition,
        Vec3 victimPosition,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
    }

    private record PreArm(
        boolean currentSpearThreat,
        LethalOpportunity approach,
        boolean planningContainsApproach,
        boolean equipCandidate,
        String spearKinetic,
        String kineticDelayTicks,
        String actualThreats,
        String opportunities,
        String candidates,
        String inventory
    ) {
    }

    private record Boundary(
        int serverUseTicks,
        boolean discreteStabRejected,
        boolean damageConditionReady,
        double attackerSpeed,
        double targetSpeed,
        double relativeSpeed,
        float attackStrength
    ) {
    }

    private record Outcome(
        int serverUseTicks,
        float health,
        boolean alive,
        boolean protectionConsumed,
        int kineticContacts
    ) {
    }
}
