package dev.pixelied.survival.validation;

import com.mojang.authlib.GameProfile;
import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.execution.ExecutionCommand;
import dev.pixelied.survival.execution.MinecraftCommandDispatcher;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Exact-runtime authority probe for player-launched projectile families whose first spawned
 * projectile may be too late for a server-authoritative death-protection transaction.
 */
final class FirstFrameProjectileAuthorityValidationScenarios {
    private static final float SERVER_PROBE_HEALTH = 20f;
    private static final float CLIENT_LETHALITY_HEALTH = 1f;
    private static final double ATTACKER_DISTANCE = 1.2d;
    private static final int MAX_PROBE_TICKS = 20;

    private FirstFrameProjectileAuthorityValidationScenarios() {
    }

    static void measureFirstFrameAuthority(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        UUID victimId = singleplayer.getServer().computeOnServer(server ->
            SurvivalValidationClientGameTest.onlyPlayer(server).getUUID()
        );
        VictimOrigin origin = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = requirePlayer(server, victimId, "victim");
            return new VictimOrigin(victim.position(), victim.getYRot(), victim.getXRot());
        });
        AttackerHandle attacker = singleplayer.getServer().computeOnServer(server ->
            createMockAttacker(server, victimId)
        );

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(attacker.entityId()) instanceof Player);

            List<ProbeOutcome> outcomes = new ArrayList<>();
            for (LaunchFamily family : LaunchFamily.values()) {
                outcomes.add(runFamily(context, singleplayer, victimId, origin, attacker, family));
            }

            throw new AssertionError("FIRST_FRAME_PROJECTILE_AUTHORITY_RESULTS " + outcomes);
        } finally {
            cleanupAll(context, singleplayer, victimId, origin, attacker);
        }
    }

    private static ProbeOutcome runFamily(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        UUID victimId,
        VictimOrigin origin,
        AttackerHandle attacker,
        LaunchFamily family
    ) {
        prepareTrial(context, singleplayer, victimId, origin, attacker, family);
        int firstProjectileId = launch(singleplayer, victimId, attacker.playerId(), family, origin.position());
        RaceObservation firstEntity = measureActualLeadTime(
            context, singleplayer, victimId, firstProjectileId, family
        );
        cleanupTrial(context, singleplayer, victimId, origin, attacker);

        prepareTrial(context, singleplayer, victimId, origin, attacker, family);
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer victim = requirePlayer(server, victimId, "victim");
            victim.setHealth(1f);
            victim.invulnerableTime = 0;
            victim.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            victim.containerMenu.broadcastChanges();
        });
        boolean serverProtectedBeforeLaunch = singleplayer.getServer().computeOnServer(server ->
            requirePlayer(server, victimId, "victim").getOffhandItem().is(Items.TOTEM_OF_UNDYING)
        );
        if (!serverProtectedBeforeLaunch) {
            throw new AssertionError(family.id + " precursor path did not establish server-authoritative protection");
        }
        int protectedProjectileId = launch(singleplayer, victimId, attacker.playerId(), family, origin.position());
        boolean precursorProtected = awaitProtectedImpact(
            context, singleplayer, victimId, protectedProjectileId, family
        );
        cleanupTrial(context, singleplayer, victimId, origin, attacker);

        return new ProbeOutcome(family.id, firstEntity, precursorProtected);
    }

    private static void prepareTrial(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        UUID victimId,
        VictimOrigin origin,
        AttackerHandle attacker,
        LaunchFamily family
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer victim = requirePlayer(server, victimId, "victim");
            ServerPlayer remote = requirePlayer(server, attacker.playerId(), "attacker");
            resetVictim(victim, origin);
            resetAttacker(remote, victim, origin.position(), family);
        });
        ensureSelectedSlot(context, singleplayer, victimId, 0, family.id + "_selected_slot");
        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));

        // A real player has the source item equipped before the release/use packet. Waiting for
        // the tracked equipment first prevents a synthetic same-tick equipment/use-data race in
        // the embedded mock player from hiding a legitimately observable precursor.
        awaitHeldPrecursor(context, singleplayer, attacker, family);
        if (family == LaunchFamily.BOW) {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer victim = requirePlayer(server, victimId, "victim");
                ServerPlayer remote = requirePlayer(server, attacker.playerId(), "attacker");
                remote.startUsingItem(InteractionHand.MAIN_HAND);
                syncEntityDataToObserver(victim, remote);
            });
            awaitBowUsePrecursor(context, singleplayer, attacker);
        }
    }

    private static void resetVictim(ServerPlayer victim, VictimOrigin origin) {
        SurvivalValidationClientGameTest.reset(victim, SERVER_PROBE_HEALTH);
        victim.setNoGravity(true);
        victim.setDeltaMovement(Vec3.ZERO);
        victim.fallDistance = 0d;
        victim.teleportTo(origin.position().x, origin.position().y, origin.position().z);
        victim.setYRot(origin.yRot());
        victim.setXRot(origin.xRot());
        victim.getInventory().clearContent();
        victim.getInventory().setSelectedSlot(0);
        victim.getInventory().setItem(0, new ItemStack(Items.STICK));
        victim.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
        victim.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        victim.containerMenu.broadcastChanges();
    }

    private static void resetAttacker(
        ServerPlayer attacker,
        ServerPlayer victim,
        Vec3 victimPosition,
        LaunchFamily family
    ) {
        attacker.stopUsingItem();
        attacker.removeAllEffects();
        attacker.setHealth(20f);
        attacker.setNoGravity(true);
        attacker.setDeltaMovement(Vec3.ZERO);
        attacker.fallDistance = 0d;
        attacker.getInventory().clearContent();
        attacker.getInventory().setSelectedSlot(0);
        attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        positionAttacker(attacker, victim, victimPosition);

        ItemStack held = switch (family) {
            case BOW -> {
                attacker.getInventory().setItem(1, new ItemStack(Items.ARROW, 64));
                yield new ItemStack(Items.BOW);
            }
            case CROSSBOW_ARROW -> chargedCrossbow(new ItemStack(Items.ARROW));
            case CROSSBOW_FIREWORK -> chargedCrossbow(damagingFirework());
            case WIND_CHARGE -> new ItemStack(Items.WIND_CHARGE);
            case SPLASH_HARMING -> harmingPotion();
        };
        attacker.getInventory().setItem(0, held);
        attacker.containerMenu.broadcastChanges();
        syncEquipmentToObserver(victim, attacker);
    }

    private static void syncEquipmentToObserver(ServerPlayer observer, ServerPlayer remote) {
        observer.connection.send(new ClientboundSetEquipmentPacket(
            remote.getId(),
            List.of(
                com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND, remote.getMainHandItem().copy()),
                com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND, remote.getOffhandItem().copy())
            )
        ));
    }

    private static void syncEntityDataToObserver(ServerPlayer observer, ServerPlayer remote) {
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values =
            remote.getEntityData().getNonDefaultValues();
        if (values != null && !values.isEmpty()) {
            observer.connection.send(new ClientboundSetEntityDataPacket(remote.getId(), values));
        }
    }

    private static void positionAttacker(ServerPlayer attacker, ServerPlayer victim, Vec3 victimPosition) {
        victim.teleportTo(victimPosition.x, victimPosition.y, victimPosition.z);
        victim.setDeltaMovement(Vec3.ZERO);
        attacker.teleportTo(victimPosition.x, victimPosition.y, victimPosition.z + ATTACKER_DISTANCE);
        attacker.setDeltaMovement(Vec3.ZERO);
        attacker.lookAt(EntityAnchorArgument.Anchor.EYES, victim.getEyePosition());
    }

    private static void awaitHeldPrecursor(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        AttackerHandle attacker,
        LaunchFamily family
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean visible = context.computeOnClient(minecraft -> {
                if (minecraft.level == null) return false;
                if (!(minecraft.level.getEntity(attacker.entityId()) instanceof Player remote)) return false;
                ItemStack held = remote.getMainHandItem();
                return switch (family) {
                    case BOW -> held.is(Items.BOW);
                    case CROSSBOW_ARROW -> held.is(Items.CROSSBOW)
                        && held.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)
                            .contains(Items.ARROW);
                    case CROSSBOW_FIREWORK -> held.is(Items.CROSSBOW)
                        && held.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)
                            .contains(Items.FIREWORK_ROCKET);
                    case WIND_CHARGE -> held.is(Items.WIND_CHARGE);
                    case SPLASH_HARMING -> held.is(Items.SPLASH_POTION)
                        && held.get(DataComponents.POTION_CONTENTS) != null;
                };
            });
            if (visible) return;
            context.waitTick();
        }
        String diagnostics = precursorDiagnostics(context, singleplayer, attacker);
        throw new AssertionError(
            "client never observed held-item precursor for " + family.id + "; " + diagnostics
        );
    }

    private static void awaitBowUsePrecursor(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        AttackerHandle attacker
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean visible = context.computeOnClient(minecraft -> {
                if (minecraft.level == null) return false;
                if (!(minecraft.level.getEntity(attacker.entityId()) instanceof Player remote)) return false;
                return remote.getMainHandItem().is(Items.BOW) && remote.isUsingItem();
            });
            if (visible) return;
            context.waitTick();
        }
        String diagnostics = precursorDiagnostics(context, singleplayer, attacker);
        throw new AssertionError("client never observed Bow use precursor; " + diagnostics);
    }

    private static String precursorDiagnostics(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        AttackerHandle attacker
    ) {
        String client = context.computeOnClient(minecraft -> {
            if (minecraft.level == null) return "clientLevel=null";
            Entity entity = minecraft.level.getEntity(attacker.entityId());
            if (!(entity instanceof Player remote)) return "clientEntity=" + entity;
            return "clientHeld=" + remote.getMainHandItem()
                + " clientUsing=" + remote.isUsingItem()
                + " clientUsedHand=" + (remote.isUsingItem() ? remote.getUsedItemHand() : "none")
                + " clientUseTicks=" + remote.getTicksUsingItem()
                + " clientPos=" + remote.position();
        });
        String server = singleplayer.getServer().computeOnServer(minecraftServer -> {
            ServerPlayer remote = requirePlayer(minecraftServer, attacker.playerId(), "attacker");
            return "serverHeld=" + remote.getMainHandItem()
                + " serverUsing=" + remote.isUsingItem()
                + " serverUsedHand=" + (remote.isUsingItem() ? remote.getUsedItemHand() : "none")
                + " serverUseTicks=" + remote.getTicksUsingItem()
                + " serverPos=" + remote.position();
        });
        return client + " " + server;
    }

    private static int launch(
        TestSingleplayerContext singleplayer,
        UUID victimId,
        UUID attackerId,
        LaunchFamily family,
        Vec3 victimPosition
    ) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = requirePlayer(server, victimId, "victim");
            ServerPlayer attacker = requirePlayer(server, attackerId, "attacker");
            ServerLevel level = (ServerLevel)victim.level();
            positionAttacker(attacker, victim, victimPosition);
            Set<Integer> before = ownedProjectileIds(level, attacker);

            switch (family) {
                case BOW -> {
                    BowItem bow = (BowItem)Items.BOW;
                    ItemStack stack = attacker.getMainHandItem();
                    int remainingTime = bow.getUseDuration(stack, attacker) - 3;
                    if (!bow.releaseUsing(stack, level, attacker, remainingTime)) {
                        throw new AssertionError("minimum legal bow draw did not release a projectile");
                    }
                    attacker.stopUsingItem();
                }
                case CROSSBOW_ARROW, CROSSBOW_FIREWORK ->
                    Items.CROSSBOW.use(level, attacker, InteractionHand.MAIN_HAND);
                case WIND_CHARGE -> Items.WIND_CHARGE.use(level, attacker, InteractionHand.MAIN_HAND);
                case SPLASH_HARMING -> Items.SPLASH_POTION.use(level, attacker, InteractionHand.MAIN_HAND);
            }

            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Projectile projectile
                    && projectile.getOwner() == attacker
                    && !before.contains(entity.getId())) {
                    return entity.getId();
                }
            }
            throw new AssertionError(family.id + " launch produced no owned projectile");
        });
    }

    private static RaceObservation measureActualLeadTime(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        UUID victimId,
        int projectileId,
        LaunchFamily family
    ) {
        Long observedGameTime = null;
        for (int tick = 0; tick < MAX_PROBE_TICKS; tick++) {
            ServerState state = serverState(singleplayer, victimId);
            if (state.health() < SERVER_PROBE_HEALTH) {
                return new RaceObservation(false, -1L, -1L, state.gameTime(), false);
            }
            boolean visible = context.computeOnClient(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(projectileId) != null);
            if (visible) {
                observedGameTime = state.gameTime();
                break;
            }
            context.waitTick();
        }
        if (observedGameTime == null) {
            throw new AssertionError(family.id + " projectile was neither observed nor damaging within probe window");
        }

        RuntimeHarness harness = context.computeOnClient(minecraft -> {
            MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
            SurvivalEngine engine = new SurvivalEngine(
                SurvivalConfig.defaults(),
                runtime,
                new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
            );
            return new RuntimeHarness(runtime, engine);
        });

        long authorityGameTime = -1L;
        long damageGameTime = -1L;
        for (int tick = 0; tick < MAX_PROBE_TICKS; tick++) {
            context.runOnClient(minecraft -> {
                if (minecraft.player == null) throw new AssertionError("client victim disappeared during " + family.id);
                minecraft.player.setHealth(CLIENT_LETHALITY_HEALTH);
                harness.engine().tick();
            });
            context.waitTick();
            ServerState state = serverState(singleplayer, victimId);
            if (authorityGameTime < 0L && state.protectedInHand()) authorityGameTime = state.gameTime();
            if (damageGameTime < 0L && state.health() < SERVER_PROBE_HEALTH) damageGameTime = state.gameTime();
            if (damageGameTime >= 0L) break;
        }
        context.runOnClient(minecraft -> {
            if (minecraft.player != null) minecraft.player.setHealth(SERVER_PROBE_HEALTH);
        });
        if (damageGameTime < 0L) {
            throw new AssertionError(family.id + " did not damage the server victim within probe window");
        }
        boolean guaranteedLead = authorityGameTime >= 0L && authorityGameTime < damageGameTime;
        return new RaceObservation(true, observedGameTime, authorityGameTime, damageGameTime, guaranteedLead);
    }

    private static boolean awaitProtectedImpact(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        UUID victimId,
        int projectileId,
        LaunchFamily family
    ) {
        for (int tick = 0; tick < MAX_PROBE_TICKS; tick++) {
            context.waitTick();
            ProtectedState state = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer victim = requirePlayer(server, victimId, "victim");
                boolean projectileExists = victim.level().getEntity(projectileId) != null;
                return new ProtectedState(
                    victim.getHealth(),
                    victim.getOffhandItem().is(Items.TOTEM_OF_UNDYING),
                    projectileExists,
                    victim.isAlive()
                );
            });
            if (!state.totemPresent()) {
                if (!state.alive() || state.health() <= 0f) {
                    throw new AssertionError(family.id + " consumed protection but victim still died");
                }
                return true;
            }
            if (!state.projectileExists() && tick > 1) break;
        }
        throw new AssertionError(family.id + " pre-armed path did not consume death protection on impact");
    }

    private static void cleanupTrial(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        UUID victimId,
        VictimOrigin origin,
        AttackerHandle attacker
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer victim = requirePlayer(server, victimId, "victim");
            ServerPlayer remote = requirePlayer(server, attacker.playerId(), "attacker");
            discardOwnedProjectiles((ServerLevel)victim.level(), remote);
            remote.stopUsingItem();
            resetVictim(victim, origin);
        });
        ensureSelectedSlot(context, singleplayer, victimId, 0, "projectile_probe_cleanup");
        context.waitTick();
    }

    private static void cleanupAll(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        UUID victimId,
        VictimOrigin origin,
        AttackerHandle attacker
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
            ServerPlayer remote = server.getPlayerList().getPlayer(attacker.playerId());
            if (remote != null) {
                if (victim != null) discardOwnedProjectiles((ServerLevel)victim.level(), remote);
                server.getPlayerList().remove(remote);
            }
            if (victim != null) {
                SurvivalValidationClientGameTest.reset(victim, 20f);
                victim.setNoGravity(false);
                victim.getInventory().clearContent();
                victim.getInventory().setSelectedSlot(0);
                victim.teleportTo(origin.position().x, origin.position().y, origin.position().z);
                victim.setYRot(origin.yRot());
                victim.setXRot(origin.xRot());
                victim.containerMenu.broadcastChanges();
            }
        });
        context.runOnClient(minecraft -> {
            if (minecraft.player != null) minecraft.player.setHealth(20f);
        });
        for (int tick = 0; tick < 5; tick++) context.waitTick();
    }

    private static void ensureSelectedSlot(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        UUID victimId,
        int slot,
        String id
    ) {
        context.runOnClient(minecraft -> {
            if (minecraft.player == null) throw new AssertionError("client victim unavailable while selecting slot for " + id);
            if (!new MinecraftCommandDispatcher().dispatch(minecraft, new ExecutionCommand.SelectHotbar(slot))) {
                throw new AssertionError("could not dispatch hotbar selection for " + id);
            }
        });
        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.player.getInventory().getSelectedSlot() == slot);
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean confirmed = singleplayer.getServer().computeOnServer(server ->
                requirePlayer(server, victimId, "victim").getInventory().getSelectedSlot() == slot
            );
            if (confirmed) return;
            context.waitTick();
        }
        throw new AssertionError("server did not confirm slot " + slot + " for " + id);
    }

    private static AttackerHandle createMockAttacker(net.minecraft.server.MinecraftServer server, UUID victimId) {
        ServerPlayer victim = requirePlayer(server, victimId, "victim");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "burst-probe"),
            false
        );
        ServerPlayer attacker = new ServerPlayer(server, (ServerLevel)victim.level(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(new ChannelHandler[]{connection});
        server.getPlayerList().placeNewPlayer(connection, attacker, cookie);
        attacker.setNoGravity(true);
        return new AttackerHandle(attacker.getUUID(), attacker.getId());
    }

    private static ItemStack chargedCrossbow(ItemStack projectile) {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(projectile)));
        return crossbow;
    }

    private static ItemStack damagingFirework() {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(FireworkExplosion.DEFAULT)));
        return rocket;
    }

    private static ItemStack harmingPotion() {
        ItemStack potion = new ItemStack(Items.SPLASH_POTION);
        potion.set(
            DataComponents.POTION_CONTENTS,
            PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1))
        );
        return potion;
    }

    private static Set<Integer> ownedProjectileIds(ServerLevel level, ServerPlayer owner) {
        Set<Integer> result = new HashSet<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Projectile projectile && projectile.getOwner() == owner) result.add(entity.getId());
        }
        return result;
    }

    private static void discardOwnedProjectiles(ServerLevel level, ServerPlayer owner) {
        List<Entity> discard = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Projectile projectile && projectile.getOwner() == owner) discard.add(entity);
        }
        discard.forEach(Entity::discard);
    }

    private static ServerState serverState(TestSingleplayerContext singleplayer, UUID victimId) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer victim = requirePlayer(server, victimId, "victim");
            return new ServerState(
                victim.level().getGameTime(),
                victim.getHealth(),
                victim.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                    || victim.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
            );
        });
    }

    private static ServerPlayer requirePlayer(net.minecraft.server.MinecraftServer server, UUID id, String role) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) throw new AssertionError(role + " player disappeared during first-frame authority probe");
        return player;
    }

    private enum LaunchFamily {
        BOW("bow_min_draw"),
        CROSSBOW_ARROW("crossbow_arrow"),
        CROSSBOW_FIREWORK("crossbow_firework"),
        WIND_CHARGE("wind_charge"),
        SPLASH_HARMING("splash_harming");

        private final String id;

        LaunchFamily(String id) {
            this.id = id;
        }
    }

    private record AttackerHandle(UUID playerId, int entityId) {
    }

    private record VictimOrigin(Vec3 position, float yRot, float xRot) {
    }

    private record RuntimeHarness(MinecraftSurvivalRuntime runtime, SurvivalEngine engine) {
    }

    private record ServerState(long gameTime, float health, boolean protectedInHand) {
    }

    private record ProtectedState(float health, boolean totemPresent, boolean projectileExists, boolean alive) {
    }

    private record RaceObservation(
        boolean projectileObservedBeforeDamage,
        long projectileObservedGameTime,
        long authorityGameTime,
        long damageGameTime,
        boolean guaranteedAuthorityLead
    ) {
    }

    private record ProbeOutcome(String family, RaceObservation firstEntityPath, boolean precursorProtected) {
    }
}
