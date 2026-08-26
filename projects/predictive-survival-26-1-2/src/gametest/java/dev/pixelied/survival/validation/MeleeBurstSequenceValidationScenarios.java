package dev.pixelied.survival.validation;

import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import dev.pixelied.survival.timeline.ThreatKind;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Exact-runtime proof that approaching player melee paths arm protection before first legal attack range. */
final class MeleeBurstSequenceValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    private static final double RANGE_SCAN_STEP = 0.05d;
    private static final double APPROACH_PER_TICK = 0.25d;
    private static final double VELOCITY_EPSILON = 0.02d;
    private static final int PREARM_RANGE_TICKS = 4;

    private MeleeBurstSequenceValidationScenarios() {
    }

    static void validatePlayerCrossesRangeAndAttacksAtFirstLegalTick(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        validatePlayerWeaponRangeEntry(
            context,
            singleplayer,
            new WeaponVariant("melee", Items.NETHERITE_SWORD, 0f, false)
        );
    }

    static void validateMaceCrossesRangeAndSmashesAtFirstLegalTick(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        validatePlayerWeaponRangeEntry(
            context,
            singleplayer,
            new WeaponVariant("mace_smash", Items.MACE, 2f, true)
        );
    }

    private static void validatePlayerWeaponRangeEntry(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        WeaponVariant variant
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel)victim.level();
            Vec3 originalPosition = victim.position();
            BlockPos center = BlockPos.containing(victim.getX(), 300d, victim.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);

            BurstSequenceValidationSupport.prepareVictim(victim, 1f);
            victim.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);

            BurstSequenceValidationSupport.AttackerHandle handle =
                BurstSequenceValidationSupport.createMockAttacker(server, victim);
            ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, handle);
            attacker.getInventory().clearContent();
            attacker.getInventory().setSelectedSlot(0);
            attacker.getInventory().setItem(0, new ItemStack(variant.item()));
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.setNoGravity(true);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.fallDistance = variant.fallDistance();

            Vec3 approachVelocity = new Vec3(0d, 0d, -APPROACH_PER_TICK);
            Vec3 initialPosition = findTicksOutsideAttackRange(
                attacker,
                victim,
                center,
                approachVelocity,
                PREARM_RANGE_TICKS
            );
            attacker.teleportTo(initialPosition.x, initialPosition.y, initialPosition.z);
            attacker.setDeltaMovement(approachVelocity);
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
                return remote.getMainHandItem().is(variant.item())
                    && Math.abs(remote.getX() - setup.initialPosition().x) <= POSITION_EPSILON
                    && Math.abs(remote.getY() - setup.initialPosition().y) <= POSITION_EPSILON
                    && Math.abs(remote.getZ() - setup.initialPosition().z) <= POSITION_EPSILON
                    && Math.abs(remote.getDeltaMovement().z - setup.approachVelocity().z) <= VELOCITY_EPSILON;
            });

            BurstSequenceValidationSupport.RuntimeHarness harness = BurstSequenceValidationSupport.newHarness(context);
            Precursor precursor = context.computeOnClient(minecraft -> {
                var frame = harness.runtime().capture();
                boolean activeMelee = frame.actualTimeline().events().stream().anyMatch(event ->
                    event.kind() == ThreatKind.MELEE
                        && (event.id().equals("melee:" + setup.attacker().entityId())
                            || event.id().equals("spear:" + setup.attacker().entityId()))
                );
                LethalOpportunity opportunity = frame.opportunities().stream()
                    .filter(candidate -> candidate.family() == OpportunityFamily.MELEE)
                    .filter(candidate -> Integer.toString(setup.attacker().entityId())
                        .equals(candidate.evidence().get("attacker_id")))
                    .findFirst()
                    .orElse(null);
                long fastestProtectionAuthorityTick = Math.max(
                    0L,
                    frame.context().timing().deadline(1).completionWindow().latest()
                        - frame.context().timing().clientTick()
                );
                return new Precursor(activeMelee, opportunity, fastestProtectionAuthorityTick);
            });
            if (precursor.activeMelee()) {
                throw new AssertionError(variant.id() + " approach test began with an already-active melee threat");
            }
            if (precursor.opportunity() == null) {
                throw new AssertionError(variant.id() + " approaching attacker produced no melee opportunity before range entry");
            }
            int entryTick = Integer.parseInt(precursor.opportunity().evidence().getOrDefault("entry_tick", "-1"));
            if (entryTick != PREARM_RANGE_TICKS) {
                throw new AssertionError(
                    variant.id() + " approach predicted entry tick " + entryTick
                        + " but vanilla fixture boundary is " + PREARM_RANGE_TICKS
                );
            }
            if (entryTick < precursor.fastestProtectionAuthorityTick()) {
                throw new AssertionError(
                    variant.id() + " precursor arrived after the fastest Totem guarantee was already lost; entry="
                        + entryTick + " authority=" + precursor.fastestProtectionAuthorityTick()
                );
            }

            BurstSequenceValidationSupport.armTotemFromPrecursor(
                context,
                singleplayer,
                setup.victimId(),
                harness,
                variant.id() + "_range_entry"
            );

            Outcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = BurstSequenceValidationSupport.requireVictim(server, setup.victimId());
                ServerPlayer attacker = BurstSequenceValidationSupport.requireAttacker(server, setup.attacker());
                if (!BurstSequenceValidationSupport.protectedInHand(victim)) {
                    throw new AssertionError(
                        "server lost precursor-established protection before " + variant.id() + " range entry"
                    );
                }

                attacker.teleportTo(
                    setup.initialPosition().x,
                    setup.initialPosition().y,
                    setup.initialPosition().z
                );
                attacker.setDeltaMovement(setup.approachVelocity());
                attacker.fallDistance = variant.fallDistance();
                if (attacker.isWithinAttackRange(attacker.getMainHandItem(), victim.getBoundingBox(), 3.0d)) {
                    throw new AssertionError("server attacker was already attackable before projected " + variant.id() + " range entry");
                }

                for (int tick = 1; tick <= entryTick; tick++) {
                    Vec3 projected = setup.initialPosition().add(setup.approachVelocity().scale(tick));
                    attacker.teleportTo(projected.x, projected.y, projected.z);
                    boolean inRange = attacker.isWithinAttackRange(
                        attacker.getMainHandItem(),
                        victim.getBoundingBox(),
                        3.0d
                    );
                    if (tick < entryTick && inRange) {
                        throw new AssertionError(
                            "server " + variant.id() + " range became legal before predictor entry tick: tick=" + tick
                                + " predicted=" + entryTick
                        );
                    }
                    if (tick == entryTick && !inRange) {
                        throw new AssertionError(
                            "server " + variant.id() + " range was still illegal at predictor entry tick " + entryTick
                        );
                    }
                }

                victim.invulnerableTime = 0;
                victim.setHealth(1f);
                attacker.fallDistance = variant.fallDistance();
                boolean maceSmashReady = variant.expectMaceSmash() && MaceItem.canSmashAttack(attacker);
                float attackStrength = attacker.getAttackStrengthScale(0.5f);
                attacker.attack(victim);
                return new Outcome(
                    victim.getHealth(),
                    BurstSequenceValidationSupport.protectionConsumed(victim),
                    attackStrength,
                    maceSmashReady
                );
            });

            if (variant.expectMaceSmash() && !outcome.maceSmashReady()) {
                throw new AssertionError("mace range-entry fixture did not satisfy vanilla smash preconditions");
            }
            SurvivalValidationClientGameTest.assertClose(variant.id() + "_range_entry_pop", 1f, outcome.health(), EPSILON);
            if (!outcome.protectionConsumed()) {
                throw new AssertionError(
                    "first-legal-tick " + variant.id()
                        + " attack did not consume server-authoritative protection; attackStrength="
                        + outcome.attackStrength()
                );
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

    private static Vec3 findTicksOutsideAttackRange(
        ServerPlayer attacker,
        ServerPlayer victim,
        BlockPos center,
        Vec3 approachVelocity,
        int entryTicks
    ) {
        double x = center.getX() + 0.5d;
        double y = center.getY();
        double victimZ = center.getZ() + 0.5d;
        for (double distance = 3.0d; distance <= 12.0d; distance += RANGE_SCAN_STEP) {
            double outsideZ = victimZ + distance;
            attacker.teleportTo(x, y, outsideZ);
            if (attacker.isWithinAttackRange(attacker.getMainHandItem(), victim.getBoundingBox(), 3.0d)) continue;

            boolean valid = true;
            for (int tick = 1; tick <= entryTicks; tick++) {
                Vec3 projected = new Vec3(x, y, outsideZ).add(approachVelocity.scale(tick));
                attacker.teleportTo(projected.x, projected.y, projected.z);
                boolean inRange = attacker.isWithinAttackRange(
                    attacker.getMainHandItem(),
                    victim.getBoundingBox(),
                    3.0d
                );
                if (tick < entryTicks && inRange) {
                    valid = false;
                    break;
                }
                if (tick == entryTicks && !inRange) valid = false;
            }
            if (valid) return new Vec3(x, y, outsideZ);
        }
        throw new AssertionError(
            "could not resolve a " + entryTicks + "-tick player attack-range boundary from vanilla server rules"
        );
    }

    private static Map<BlockPos, BlockState> clearArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 13; dz++) {
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

    private record WeaponVariant(
        String id,
        Item item,
        float fallDistance,
        boolean expectMaceSmash
    ) {
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
        boolean activeMelee,
        LethalOpportunity opportunity,
        long fastestProtectionAuthorityTick
    ) {
    }

    private record Outcome(
        float health,
        boolean protectionConsumed,
        float attackStrength,
        boolean maceSmashReady
    ) {
    }
}
