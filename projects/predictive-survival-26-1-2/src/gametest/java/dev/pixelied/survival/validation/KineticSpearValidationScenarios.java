package dev.pixelied.survival.validation;

import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timeline.ThreatKind;
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
import java.util.Map;
import java.util.UUID;

/** Exact-runtime validation for synchronized 26.1.2 KINETIC_WEAPON spear state. */
final class KineticSpearValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    private static final double KINETIC_KNOWN_MOVEMENT_PER_TICK = 0.25d;

    private KineticSpearValidationScenarios() {
    }

    static void validateSynchronizedKineticMetadataReachesProductionFrame(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            Vec3 originalPosition = victim.position();
            BurstSequenceValidationSupport.prepareVictim(victim, 20f);
            Vec3 victimPosition = new Vec3(originalPosition.x, 322d, originalPosition.z);
            victim.teleportTo(victimPosition.x, victimPosition.y, victimPosition.z);

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
            attacker.setXRot(0f);
            attacker.setYRot(0f);
            attacker.setYHeadRot(0f);

            Vec3 attackerPosition = victimPosition.add(0d, 0d, 6d);
            attacker.teleportTo(attackerPosition.x, attackerPosition.y, attackerPosition.z);
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);
            victim.connection.send(ClientboundEntityPositionSyncPacket.of(attacker));
            victim.connection.send(new ClientboundSetEntityMotionPacket(attacker));

            attacker.startUsingItem(InteractionHand.MAIN_HAND);
            attacker.doTick();
            if (attacker.getTicksUsingItem() != 1) {
                throw new AssertionError(
                    "kinetic spear fixture did not reach exactly one authoritative use tick; ticks="
                        + attacker.getTicksUsingItem()
                );
            }
            var values = attacker.getEntityData().getNonDefaultValues();
            if (values == null) {
                throw new AssertionError("kinetic spear use did not dirty synchronized living-entity state");
            }
            victim.connection.send(new ClientboundSetEntityDataPacket(attacker.getId(), values));

            return new Setup(victim.getUUID(), originalPosition, attackerPosition, handle);
        });

        try {
            waitForClientKineticUse(context, setup);

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            Capture capture = context.computeOnClient(minecraft -> {
                if (minecraft.level == null) throw new AssertionError("client level unavailable for kinetic capture");
                Entity entity = minecraft.level.getEntity(setup.attacker().entityId());
                if (!(entity instanceof net.minecraft.world.entity.player.Player remote)) {
                    throw new AssertionError("remote kinetic spear attacker missing before production capture");
                }
                KineticWeapon kinetic = remote.getMainHandItem().get(DataComponents.KINETIC_WEAPON);
                if (kinetic == null) {
                    throw new AssertionError("26.1.2 synchronized KINETIC_WEAPON component missing on remote spear");
                }
                KineticWeapon.Condition damage = kinetic.damageConditions()
                    .orElseThrow(() -> new AssertionError("netherite spear lacks kinetic damage condition"));

                var frame = harness.runtime().capture();
                var snapshot = frame.context().world().entities().stream()
                    .filter(candidate -> candidate.id().equals(Integer.toString(setup.attacker().entityId())))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("kinetic spear attacker missing from production frame"));
                return new Capture(
                    kinetic,
                    damage,
                    snapshot.properties(),
                    snapshot.properties().getOrDefault("using_item", "missing"),
                    snapshot.properties().getOrDefault("used_hand", "missing")
                );
            });

            if (!"true".equals(capture.usingItem()) || !"main_hand".equals(capture.usedHand())) {
                throw new AssertionError("production frame lost synchronized kinetic spear use state: " + capture.properties());
            }
            assertProperty(capture.properties(), "spear_kinetic", "true");
            assertProperty(
                capture.properties(),
                "spear_kinetic_contact_cooldown_ticks",
                Integer.toString(capture.kinetic().contactCooldownTicks())
            );
            assertProperty(
                capture.properties(),
                "spear_kinetic_delay_ticks",
                Integer.toString(capture.kinetic().delayTicks())
            );
            assertProperty(
                capture.properties(),
                "spear_damage_multiplier",
                Float.toString(capture.kinetic().damageMultiplier())
            );
            assertProperty(
                capture.properties(),
                "spear_damage_max_use_ticks",
                Integer.toString(capture.damage().maxDurationTicks())
            );
            assertProperty(
                capture.properties(),
                "spear_damage_min_speed",
                Float.toString(capture.damage().minSpeed())
            );
            assertProperty(
                capture.properties(),
                "spear_damage_min_relative_speed",
                Float.toString(capture.damage().minRelativeSpeed())
            );
        } finally {
            cleanup(context, singleplayer, setup);
        }
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

            Vec3 attackerPosition = victimPosition.add(0d, 0d, 3d);
            attacker.teleportTo(attackerPosition.x, attackerPosition.y, attackerPosition.z);
            faceVictim(attacker, victim);
            attacker.setKnownMovement(attacker.getLookAngle().scale(KINETIC_KNOWN_MOVEMENT_PER_TICK));
            attacker.resetAttackStrengthTicker();
            attacker.containerMenu.broadcastChanges();
            BurstSequenceValidationSupport.syncEquipment(victim, attacker);
            victim.connection.send(ClientboundEntityPositionSyncPacket.of(attacker));
            victim.connection.send(new ClientboundSetEntityMotionPacket(attacker));

            if (!canPiercingHit(attacker, victim)) {
                throw new AssertionError("kinetic fixture is outside the real spear ray before use begins");
            }

            attacker.startUsingItem(InteractionHand.MAIN_HAND);
            attacker.doTick();
            if (attacker.getTicksUsingItem() != 1) {
                throw new AssertionError(
                    "kinetic contact fixture did not reach exactly one authoritative use tick; ticks="
                        + attacker.getTicksUsingItem()
                );
            }
            if (victim.getHealth() != 1f) {
                throw new AssertionError("kinetic spear dealt damage before its synchronized delay: health=" + victim.getHealth());
            }
            var values = attacker.getEntityData().getNonDefaultValues();
            if (values == null) {
                throw new AssertionError("kinetic contact use did not dirty synchronized living-entity state");
            }
            victim.connection.send(new ClientboundSetEntityDataPacket(attacker.getId(), values));

            return new Setup(victim.getUUID(), originalPosition, attackerPosition, handle);
        });

        try {
            waitForClientKineticUse(context, setup);
            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);

            PreArm preArm = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                String attackerId = Integer.toString(setup.attacker().entityId());
                var snapshot = frame.context().world().entities().stream()
                    .filter(candidate -> candidate.id().equals(attackerId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("kinetic contact attacker missing from production frame"));
                boolean spearThreat = frame.actualTimeline().events().stream().anyMatch(event ->
                    event.kind() == ThreatKind.MELEE
                        && (event.id().equals("spear:" + attackerId) || event.id().equals("melee:" + attackerId))
                );
                boolean equipCandidate = frame.candidates().stream()
                    .anyMatch(SurvivalAction.EquipDeathProtection.class::isInstance);
                return new PreArm(
                    spearThreat,
                    equipCandidate,
                    snapshot.properties().getOrDefault("spear_kinetic", "false"),
                    snapshot.properties().getOrDefault("spear_kinetic_delay_ticks", "missing"),
                    frame.actualTimeline().events().toString(),
                    frame.candidates().toString()
                );
            });
            if (!"true".equals(preArm.spearKinetic())) {
                throw new AssertionError("production frame lost synchronized Kinetic spear marker: " + preArm);
            }
            if (!preArm.spearThreat()) {
                throw new AssertionError("in-range Kinetic spear produced no conservative melee threat: " + preArm);
            }
            if (!preArm.equipCandidate()) {
                throw new AssertionError("in-range Kinetic spear produced no death-protection candidate: " + preArm);
            }

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                "kinetic_spear_use_tick"
            );

            Boundary boundary = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                ItemStack spear = attacker.getMainHandItem();
                KineticWeapon kinetic = spear.get(DataComponents.KINETIC_WEAPON);
                if (kinetic == null) throw new AssertionError("netherite spear lost KINETIC_WEAPON component");
                KineticWeapon.Condition damage = kinetic.damageConditions()
                    .orElseThrow(() -> new AssertionError("netherite spear lost Kinetic damage condition"));
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError("server lost production-armed protection before Kinetic delay boundary");
                }

                while (attacker.getTicksUsingItem() < kinetic.delayTicks()) {
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
                    throw new AssertionError("Kinetic contact memory was populated before the first legal damage tick");
                }
                if (!canPiercingHit(attacker, victim)) {
                    throw new AssertionError("victim left the real spear ray before first Kinetic damage tick");
                }

                // Reset only the discrete attack charge. KineticWeapon never consults this ticker,
                // so a pop on the next doTick proves the use-tick damage path is independent of STAB.
                attacker.resetAttackStrengthTicker();
                boolean discreteStabRejected = attacker.cannotAttackWithItem(spear, 5);
                faceVictim(attacker, victim);
                attacker.setKnownMovement(attacker.getLookAngle().scale(KINETIC_KNOWN_MOVEMENT_PER_TICK));
                victim.setKnownMovement(Vec3.ZERO);
                Vec3 look = attacker.getLookAngle();
                double attackerSpeed = look.dot(KineticWeapon.getMotion(attacker));
                double targetSpeed = look.dot(KineticWeapon.getMotion(victim));
                double relativeSpeed = Math.max(0d, attackerSpeed - targetSpeed);
                boolean damageConditionReady = damage.test(0, attackerSpeed, relativeSpeed, 1d);
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
            // Same server call: victim has not ticked Regeneration II yet, so the transient post-pop
            // health must still be the vanilla 1.0 established by death protection.
            SurvivalValidationClientGameTest.assertClose("kinetic_spear_first_damage_tick_pop", 1f, outcome.health(), EPSILON);
        } finally {
            cleanup(context, singleplayer, setup);
        }
    }

    private static void waitForClientKineticUse(ClientGameTestContext context, Setup setup) {
        context.waitFor(minecraft -> {
            if (minecraft.level == null) return false;
            Entity entity = minecraft.level.getEntity(setup.attacker().entityId());
            if (!(entity instanceof net.minecraft.world.entity.player.Player remote)) return false;
            return remote.getMainHandItem().is(Items.NETHERITE_SPEAR)
                && remote.getMainHandItem().has(DataComponents.KINETIC_WEAPON)
                && remote.isUsingItem()
                && remote.getUsedItemHand() == InteractionHand.MAIN_HAND
                && Math.abs(remote.getX() - setup.attackerPosition().x) <= POSITION_EPSILON
                && Math.abs(remote.getY() - setup.attackerPosition().y) <= POSITION_EPSILON
                && Math.abs(remote.getZ() - setup.attackerPosition().z) <= POSITION_EPSILON;
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

    private static void assertProperty(Map<String, String> properties, String key, String expected) {
        String actual = properties.get(key);
        if (!expected.equals(actual)) {
            throw new AssertionError(
                "production frame omitted/mismatched synchronized kinetic spear property "
                    + key + "; expected=" + expected + " actual=" + actual + " properties=" + properties
            );
        }
    }

    private record Setup(
        UUID victimId,
        Vec3 originalPosition,
        Vec3 attackerPosition,
        BurstSequenceValidationSupport.AttackerHandle attacker
    ) {
    }

    private record Capture(
        KineticWeapon kinetic,
        KineticWeapon.Condition damage,
        Map<String, String> properties,
        String usingItem,
        String usedHand
    ) {
    }

    private record PreArm(
        boolean spearThreat,
        boolean equipCandidate,
        String spearKinetic,
        String kineticDelayTicks,
        String threats,
        String candidates
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
