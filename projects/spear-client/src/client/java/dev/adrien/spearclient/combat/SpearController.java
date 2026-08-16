package dev.adrien.spearclient.combat;

import dev.adrien.spearclient.config.SpearConfig;
import dev.adrien.spearclient.modules.OneTapModule;
import dev.adrien.spearclient.network.CollisionProbe;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class SpearController {
    private static final double ONE_TAP_ACQUISITION_RANGE = 12.5;
    private static final double COLLISION_SAMPLE_STEP = 0.25;
    private static final int PENDING_USE_TIMEOUT_TICKS = 40;

    private final Supplier<SpearConfig> config;
    private final AttackSequencer sequencer;
    private final OneTapModule oneTap;

    private int pendingOneTapTargetId = -1;
    private int pendingUseTicks;
    private boolean releaseUseOnFinish;

    public SpearController(
        Supplier<SpearConfig> config,
        AttackSequencer sequencer,
        OneTapModule oneTap
    ) {
        this.config = config;
        this.sequencer = sequencer;
        this.oneTap = oneTap;
    }

    public boolean onAttackPressed(Minecraft client) {
        SpearConfig current = config.get().sanitized();
        if (!current.oneTap().enabled()) {
            return false;
        }
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return false;
        }
        if (sequencer.isActive() || pendingOneTapTargetId >= 0) {
            return true;
        }

        Player target = SpearTargeting.findTarget(client, ONE_TAP_ACQUISITION_RANGE, true);
        SpearContext context = SpearContext.capture(client, target);
        if (context == null || context.kinetic() == null) {
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

    public void tick(Minecraft client) {
        if (sequencer.isActive()) {
            boolean wasActive = true;
            sequencer.tick(client);
            if (wasActive && !sequencer.isActive()) {
                releaseOwnedUse(client);
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
            clearPending(client);
            return;
        }
        if (++pendingUseTicks > PENDING_USE_TIMEOUT_TICKS) {
            clearPending(client);
            return;
        }

        Entity entity = client.level.getEntity(pendingOneTapTargetId);
        if (!(entity instanceof Player target) || !target.isAlive()) {
            clearPending(client);
            return;
        }

        SpearContext context = SpearContext.capture(client, target);
        if (context == null || context.kinetic() == null) {
            clearPending(client);
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
        Vec3 backPosition = sequence.movementPath().positions().getFirst();
        if (!CollisionProbe.isPositionClear(client.player, backPosition)
            || !CollisionProbe.isSegmentClear(
                client.player,
                context.origin(),
                backPosition,
                COLLISION_SAMPLE_STEP
            )) {
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
