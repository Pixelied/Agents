package dev.adrien.spearclient.combat;

import dev.adrien.spearclient.config.SpearConfig;
import dev.adrien.spearclient.modules.InfiniteReachModule;
import dev.adrien.spearclient.modules.LungeBoostModule;
import dev.adrien.spearclient.modules.OneTapModule;
import dev.adrien.spearclient.network.CollisionProbe;
import dev.adrien.spearclient.network.MovementPath;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class SpearController {
    private static final double ONE_TAP_ACQUISITION_RANGE = 12.5;
    private static final double REACH_ACQUISITION_RANGE = 31.5;
    private static final double COLLISION_SAMPLE_STEP = 0.25;
    private static final int PENDING_USE_TIMEOUT_TICKS = 40;

    private final Supplier<SpearConfig> config;
    private final AttackSequencer sequencer;
    private final OneTapModule oneTap;
    private final LungeBoostModule lungeBoost;
    private final InfiniteReachModule infiniteReach;

    private int pendingOneTapTargetId = -1;
    private int pendingUseTicks;
    private boolean releaseUseOnFinish;

    public SpearController(
        Supplier<SpearConfig> config,
        AttackSequencer sequencer,
        OneTapModule oneTap,
        LungeBoostModule lungeBoost,
        InfiniteReachModule infiniteReach
    ) {
        this.config = config;
        this.sequencer = sequencer;
        this.oneTap = oneTap;
        this.lungeBoost = lungeBoost;
        this.infiniteReach = infiniteReach;
    }

    public boolean onAttackPressed(Minecraft client) {
        SpearConfig current = config.get().sanitized();
        if (!current.oneTap().enabled()
            && !current.lungeBoost().enabled()
            && !current.infiniteReach().enabled()) {
            return false;
        }
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return false;
        }
        if (sequencer.isActive() || pendingOneTapTargetId >= 0) {
            return true;
        }

        Player oneTapTarget = null;
        SpearContext oneTapContext = null;
        boolean oneTapAvailable = false;
        if (current.oneTap().enabled()) {
            oneTapTarget = SpearTargeting.findTarget(client, ONE_TAP_ACQUISITION_RANGE, true);
            oneTapContext = SpearContext.capture(client, oneTapTarget);
            oneTapAvailable = oneTapContext != null
                && oneTapContext.kinetic() != null
                && (!client.player.isUsingItem()
                    || client.player.getUsedItemHand() == InteractionHand.MAIN_HAND);
        }

        SpearContext lungeContext = null;
        boolean lungeAvailable = false;
        if (current.lungeBoost().enabled()) {
            lungeContext = SpearContext.capture(client, client.player);
            lungeAvailable = lungeContext != null
                && lungeContext.vanillaLungeEligible()
                && !client.player.cannotAttackWithItem(client.player.getMainHandItem(), 5);
        }

        SpearControllerPolicy.Action action = SpearControllerPolicy.choose(
            current.oneTap().enabled(),
            current.lungeBoost().enabled(),
            current.infiniteReach().enabled(),
            oneTapAvailable,
            lungeAvailable
        );

        if (action == SpearControllerPolicy.Action.ONE_TAP) {
            return beginOneTapUse(client, oneTapTarget);
        }
        if (action == SpearControllerPolicy.Action.LUNGE) {
            return tryStartLunge(client, lungeContext);
        }
        if (action == SpearControllerPolicy.Action.REACH) {
            return tryStartReach(client, current);
        }
        return false;
    }

    public void tick(Minecraft client) {
        if (client != null && client.player != null && !client.player.isAlive()) {
            reset(ResetReason.DEATH);
            return;
        }

        if (sequencer.isActive()) {
            sequencer.tick(client);
            if (!sequencer.isActive()) {
                if (sequencer.lastTerminationAllowsCleanupPackets()) {
                    releaseOwnedUse(client);
                } else {
                    releaseUseOnFinish = false;
                }
            }
            return;
        }

        if (pendingOneTapTargetId < 0) {
            return;
        }
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            clearPending(client);
            return;
        }
        if (!config.get().sanitized().oneTap().enabled()) {
            reset(ResetReason.CONFIG_DISABLED);
            return;
        }
        if (++pendingUseTicks > PENDING_USE_TIMEOUT_TICKS) {
            clearPending(client);
            return;
        }

        Entity entity = client.level.getEntity(pendingOneTapTargetId);
        if (!(entity instanceof Player target) || !target.isAlive()) {
            reset(ResetReason.TARGET_LOST);
            return;
        }

        SpearContext context = SpearContext.capture(client, target);
        if (context == null || context.kinetic() == null) {
            reset(ResetReason.LOST_SPEAR);
            return;
        }
        if (!client.player.isUsingItem()
            || client.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            clearPending(client);
            return;
        }
        if (context.ticksUsingItem() < context.kinetic().delayTicks()) {
            return;
        }

        Optional<AttackSequence> prepared = oneTap.prepare(context);
        if (prepared.isEmpty()) {
            clearPending(client);
            return;
        }

        AttackSequence sequence = prepared.get();
        if (!isPathClear(client.player, sequence.movementPath())) {
            clearPending(client);
            return;
        }

        pendingOneTapTargetId = -1;
        pendingUseTicks = 0;
        if (!sequencer.tryStart(sequence)) {
            releaseOwnedUse(client);
            return;
        }
        sequencer.tick(client);
    }

    public void reset(ResetReason reason) {
        if (reason.shouldReleaseOwnedUse()) {
            releaseOwnedUse(Minecraft.getInstance());
        } else {
            releaseUseOnFinish = false;
        }
        pendingOneTapTargetId = -1;
        pendingUseTicks = 0;

        if (reason.shouldAbortWithoutPackets()) {
            sequencer.abortWithoutPackets(reason.name());
        } else {
            sequencer.abort(reason.name());
        }
    }

    public void onConfigChanged(SpearConfig previous, SpearConfig current) {
        SpearConfig next = current == null ? SpearConfig.defaults() : current.sanitized();
        if (pendingOneTapTargetId >= 0 && !next.oneTap().enabled()) {
            reset(ResetReason.CONFIG_DISABLED);
            return;
        }

        AttackSequence.Kind activeKind = sequencer.activeKind();
        if (activeKind != null && !isModuleEnabled(activeKind, next)) {
            reset(ResetReason.CONFIG_DISABLED);
        }
    }

    private boolean beginOneTapUse(Minecraft client, Player target) {
        if (target == null) {
            return false;
        }

        if (client.player.isUsingItem()) {
            if (client.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
                return false;
            }
            releaseUseOnFinish = false;
        } else {
            client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
            releaseUseOnFinish = true;
        }

        pendingOneTapTargetId = target.getId();
        pendingUseTicks = 0;
        return true;
    }

    private boolean tryStartLunge(Minecraft client, SpearContext context) {
        Optional<AttackSequence> prepared = lungeBoost.afterStab(context);
        if (prepared.isEmpty()) {
            return false;
        }
        AttackSequence sequence = prepared.get();
        if (!isPathClear(client.player, sequence.movementPath())) {
            return false;
        }
        return sequencer.tryStartAfterStab(sequence);
    }

    private boolean tryStartReach(Minecraft client, SpearConfig current) {
        Player target = SpearTargeting.findTarget(
            client,
            REACH_ACQUISITION_RANGE,
            current.infiniteReach().teamCheck()
        );
        SpearContext context = SpearContext.capture(client, target);
        if (context == null) {
            return false;
        }

        Optional<AttackSequence> prepared = infiniteReach.prepare(context);
        if (prepared.isEmpty()) {
            return false;
        }

        AttackSequence sequence = prepared.get();
        if (!isPathClear(client.player, sequence.movementPath())) {
            return false;
        }
        return sequencer.tryStart(sequence);
    }

    private static boolean isModuleEnabled(AttackSequence.Kind kind, SpearConfig config) {
        return switch (kind) {
            case ONE_TAP -> config.oneTap().enabled();
            case LUNGE -> config.lungeBoost().enabled();
            case REACH -> config.infiniteReach().enabled();
        };
    }

    private boolean isPathClear(LocalPlayer player, MovementPath path) {
        Vec3 previous = path.origin();
        for (Vec3 position : path.positions()) {
            if (!CollisionProbe.isPositionClear(player, position)
                || !CollisionProbe.isSegmentClear(
                    player,
                    previous,
                    position,
                    COLLISION_SAMPLE_STEP
                )) {
                return false;
            }
            previous = position;
        }
        return true;
    }

    private void clearPending(Minecraft client) {
        pendingOneTapTargetId = -1;
        pendingUseTicks = 0;
        releaseOwnedUse(client);
    }

    private void releaseOwnedUse(Minecraft client) {
        if (releaseUseOnFinish
            && client != null
            && client.player != null
            && client.gameMode != null
            && client.player.isUsingItem()
            && client.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            client.gameMode.releaseUsingItem(client.player);
        }
        releaseUseOnFinish = false;
    }
}
