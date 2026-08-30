package dev.pixelied.survival.validation;

import dev.pixelied.survival.SurvivalStateInvalidationProbe;
import dev.pixelied.survival.core.SurvivalStateInvalidationReason;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Exact-runtime proof that selected 26.1.2 packet evidence invalidates in the same client tick. */
final class SurvivalStateInvalidationValidationScenarios {
    private static final double EPSILON = 0.0001d;

    private SurvivalStateInvalidationValidationScenarios() {
    }

    static void validateSameTickPacketEvidence(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        validateEffectRemoval(context, singleplayer);
        validateSyncableAttributeUpdate(context, singleplayer);
        validateWorldBorderUpdate(context, singleplayer);
        validateTntMinecartPrimeEvent(context, singleplayer);
        validateIrrelevantMetadataSpamIsFiltered(context, singleplayer);
    }

    private static void validateEffectRemoval(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 0));
        });
        context.waitFor(minecraft -> minecraft.player != null && minecraft.player.hasEffect(MobEffects.RESISTANCE));
        drain(context);

        singleplayer.getServer().runOnServer(server ->
            SurvivalValidationClientGameTest.onlyPlayer(server).removeEffect(MobEffects.RESISTANCE)
        );
        context.waitFor(minecraft -> minecraft.player != null && !minecraft.player.hasEffect(MobEffects.RESISTANCE));
        assertReason(context, SurvivalStateInvalidationReason.EFFECT_REMOVED, "Resistance removal");
    }

    private static void validateSyncableAttributeUpdate(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        double original = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            AttributeInstance attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            if (attribute == null) throw new AssertionError("server player missing entity interaction range attribute");
            return attribute.getBaseValue();
        });
        double changed = Math.min(64d, original + 0.75d);
        if (Math.abs(changed - original) <= EPSILON) changed = Math.max(0d, original - 0.75d);

        drain(context);
        double target = changed;
        singleplayer.getServer().runOnServer(server -> {
            AttributeInstance attribute = SurvivalValidationClientGameTest.onlyPlayer(server)
                .getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            if (attribute == null) throw new AssertionError("server player missing entity interaction range attribute");
            attribute.setBaseValue(target);
        });
        context.waitFor(minecraft -> {
            if (minecraft.player == null) return false;
            AttributeInstance attribute = minecraft.player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            return attribute != null && Math.abs(attribute.getBaseValue() - target) <= EPSILON;
        });
        assertReason(context, SurvivalStateInvalidationReason.ATTRIBUTE_UPDATE, "syncable interaction-range update");

        singleplayer.getServer().runOnServer(server -> {
            AttributeInstance attribute = SurvivalValidationClientGameTest.onlyPlayer(server)
                .getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            if (attribute != null) attribute.setBaseValue(original);
        });
        context.waitFor(minecraft -> {
            if (minecraft.player == null) return false;
            AttributeInstance attribute = minecraft.player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            return attribute != null && Math.abs(attribute.getBaseValue() - original) <= EPSILON;
        });
        drain(context);
    }

    private static void validateWorldBorderUpdate(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        BorderState original = singleplayer.getServer().computeOnServer(server -> {
            WorldBorder border = SurvivalValidationClientGameTest.onlyPlayer(server).level().getWorldBorder();
            return new BorderState(border.getCenterX(), border.getCenterZ());
        });
        BorderState changed = new BorderState(original.x() + 32d, original.z() - 32d);

        drain(context);
        singleplayer.getServer().runOnServer(server ->
            SurvivalValidationClientGameTest.onlyPlayer(server).level().getWorldBorder().setCenter(changed.x(), changed.z())
        );
        context.waitFor(minecraft -> minecraft.level != null
            && Math.abs(minecraft.level.getWorldBorder().getCenterX() - changed.x()) <= EPSILON
            && Math.abs(minecraft.level.getWorldBorder().getCenterZ() - changed.z()) <= EPSILON);
        assertReason(context, SurvivalStateInvalidationReason.WORLD_BORDER, "world-border center update");

        singleplayer.getServer().runOnServer(server ->
            SurvivalValidationClientGameTest.onlyPlayer(server).level().getWorldBorder().setCenter(original.x(), original.z())
        );
        context.waitFor(minecraft -> minecraft.level != null
            && Math.abs(minecraft.level.getWorldBorder().getCenterX() - original.x()) <= EPSILON
            && Math.abs(minecraft.level.getWorldBorder().getCenterZ() - original.z()) <= EPSILON);
        drain(context);
    }

    private static void validateTntMinecartPrimeEvent(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int entityId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            MinecartTNT minecart = new MinecartTNT(EntityType.TNT_MINECART, level);
            Vec3 spawn = player.position().add(0d, 0.5d, 7d);
            minecart.setPos(spawn.x, spawn.y, spawn.z);
            minecart.setNoGravity(true);
            minecart.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(minecart);
            return minecart.getId();
        });
        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof MinecartTNT);
            drain(context);

            singleplayer.getServer().runOnServer(server -> {
                Entity entity = ((ServerLevel) SurvivalValidationClientGameTest.onlyPlayer(server).level()).getEntity(entityId);
                if (!(entity instanceof MinecartTNT minecart)) {
                    throw new AssertionError("server TNT minecart disappeared before invalidation test");
                }
                minecart.primeFuse(null);
            });
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof MinecartTNT minecart
                && minecart.isPrimed());
            assertReason(context, SurvivalStateInvalidationReason.TNT_MINECART_PRIMED, "TNT minecart event 10");
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                Entity entity = ((ServerLevel) SurvivalValidationClientGameTest.onlyPlayer(server).level()).getEntity(entityId);
                if (entity != null) entity.discard();
            });
            context.waitTick();
            drain(context);
        }
    }

    private static void validateIrrelevantMetadataSpamIsFiltered(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        List<Integer> ids = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
                stand.setPos(player.getX() + 20d + i, player.getY(), player.getZ() + 20d);
                stand.setNoGravity(true);
                stand.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(stand);
                result.add(stand.getId());
            }
            return List.copyOf(result);
        });
        try {
            context.waitFor(minecraft -> minecraft.level != null
                && ids.stream().allMatch(id -> minecraft.level.getEntity(id) instanceof ArmorStand));
            drain(context);

            singleplayer.getServer().runOnServer(server -> {
                ServerLevel level = (ServerLevel) SurvivalValidationClientGameTest.onlyPlayer(server).level();
                for (int id : ids) {
                    Entity entity = level.getEntity(id);
                    if (entity instanceof ArmorStand stand) stand.setGlowingTag(true);
                }
            });
            context.waitFor(minecraft -> minecraft.level != null
                && ids.stream().allMatch(id -> {
                    Entity entity = minecraft.level.getEntity(id);
                    return entity instanceof ArmorStand stand && stand.isCurrentlyGlowing();
                }));
            context.runOnClient(minecraft -> {
                Set<SurvivalStateInvalidationReason> reasons = SurvivalStateInvalidationProbe.consumeReasons();
                if (reasons.contains(SurvivalStateInvalidationReason.RELEVANT_ENTITY_METADATA)) {
                    throw new AssertionError("irrelevant armor-stand metadata spam forced survival metadata invalidation: " + reasons);
                }
            });
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerLevel level = (ServerLevel) SurvivalValidationClientGameTest.onlyPlayer(server).level();
                for (int id : ids) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) entity.discard();
                }
            });
            context.waitTick();
            drain(context);
        }
    }

    private static void assertReason(
        ClientGameTestContext context,
        SurvivalStateInvalidationReason expected,
        String label
    ) {
        context.runOnClient(minecraft -> {
            Set<SurvivalStateInvalidationReason> reasons = SurvivalStateInvalidationProbe.consumeReasons();
            if (!reasons.contains(expected)) {
                throw new AssertionError(label + " did not produce " + expected + ": " + reasons);
            }
        });
    }

    private static void drain(ClientGameTestContext context) {
        context.runOnClient(minecraft -> SurvivalStateInvalidationProbe.consumeReasons());
    }

    private record BorderState(double x, double z) {
    }
}
