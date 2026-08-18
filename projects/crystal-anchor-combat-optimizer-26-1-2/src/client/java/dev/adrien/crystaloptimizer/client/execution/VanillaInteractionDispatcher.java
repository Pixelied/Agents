package dev.adrien.crystaloptimizer.client.execution;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.action.Rotate;
import dev.adrien.crystaloptimizer.action.SelectHotbarSlot;
import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.execution.CommitPhase;
import dev.adrien.crystaloptimizer.execution.CommitScheduler;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class VanillaInteractionDispatcher implements ActionDispatcher {
    private final Minecraft minecraft;
    private final RotationController rotations;
    private final CommitScheduler scheduler;
    private final RotationMode rotationMode;

    public VanillaInteractionDispatcher(
        Minecraft minecraft,
        RotationController rotations,
        CommitScheduler scheduler,
        RotationMode rotationMode
    ) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.rotations = Objects.requireNonNull(rotations, "rotations");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.rotationMode = Objects.requireNonNull(rotationMode, "rotationMode");
    }

    @Override
    public DispatchReceipt dispatch(CombatAction action) {
        Objects.requireNonNull(action, "action");

        if (action instanceof Rotate rotate) {
            boolean reached = rotations.applyAngles(
                rotate.yaw(),
                rotate.pitch(),
                rotationMode,
                scheduler.phase() == CommitPhase.COMMITTED
            );
            return reached
                ? DispatchReceipt.sent("real rotation applied")
                : DispatchReceipt.deferred("real rotation still converging");
        }
        if (action instanceof SelectHotbarSlot select) {
            LocalPlayer player = minecraft.player;
            if (player == null) {
                return DispatchReceipt.failed("no local player");
            }
            player.getInventory().setSelectedSlot(select.slot());
            return DispatchReceipt.sent("selected real hotbar slot " + select.slot());
        }
        if (action instanceof Wait wait) {
            return DispatchReceipt.waiting(wait.ticks());
        }

        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.gameMode == null) {
            return DispatchReceipt.failed("client world/player/game mode unavailable");
        }

        if (action instanceof AttackKnownCrystal attack) {
            Entity entity = level.getEntity(attack.entityId());
            if (!(entity instanceof EndCrystal)) {
                return DispatchReceipt.failed("server-observed crystal entity is no longer present");
            }
            minecraft.gameMode.attack(player, entity);
            player.swing(InteractionHand.MAIN_HAND);
            return DispatchReceipt.sent("attacked known crystal " + attack.entityId());
        }
        if (action instanceof PlaceCrystal placeCrystal) {
            return useItemOn(player, topHit(placeCrystal.basePos()), "placed crystal interaction");
        }
        if (action instanceof PlaceObsidian placeObsidian) {
            BlockHitResult hit = placementHit(level, placeObsidian.pos());
            if (hit == null) {
                return DispatchReceipt.failed("no legal adjacent support face for obsidian placement");
            }
            return useItemOn(player, hit, "placed obsidian interaction");
        }
        if (action instanceof PlaceAnchor placeAnchor) {
            BlockHitResult hit = placementHit(level, placeAnchor.pos());
            if (hit == null) {
                return DispatchReceipt.failed("no legal adjacent support face for anchor placement");
            }
            return useItemOn(player, hit, "placed anchor interaction");
        }
        if (action instanceof ChargeAnchor chargeAnchor) {
            return useItemOn(player, topHit(chargeAnchor.pos()), "charged anchor interaction");
        }
        if (action instanceof DetonateAnchor detonateAnchor) {
            return useItemOn(player, topHit(detonateAnchor.pos()), "detonated anchor interaction");
        }

        return DispatchReceipt.failed("unsupported combat action: " + action.getClass().getSimpleName());
    }

    private DispatchReceipt useItemOn(LocalPlayer player, BlockHitResult hit, String detail) {
        minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.swing(InteractionHand.MAIN_HAND);
        return DispatchReceipt.sent(detail);
    }

    private static BlockHitResult topHit(BlockPos pos) {
        Vec3 hit = Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0);
        return new BlockHitResult(hit, Direction.UP, pos, false);
    }

    private static BlockHitResult placementHit(ClientLevel level, BlockPos targetPos) {
        for (Direction towardSupport : Direction.values()) {
            BlockPos supportPos = targetPos.relative(towardSupport);
            var supportState = level.getBlockState(supportPos);
            if (supportState.isAir() || supportState.canBeReplaced()) {
                continue;
            }
            Direction clickedFace = towardSupport.getOpposite();
            Vec3 hit = Vec3.atCenterOf(supportPos).add(
                clickedFace.getStepX() * 0.5,
                clickedFace.getStepY() * 0.5,
                clickedFace.getStepZ() * 0.5
            );
            return new BlockHitResult(hit, clickedFace, supportPos, false);
        }
        return null;
    }
}
