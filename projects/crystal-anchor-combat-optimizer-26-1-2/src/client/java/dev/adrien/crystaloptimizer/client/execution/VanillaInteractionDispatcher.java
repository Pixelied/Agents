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
import dev.adrien.crystaloptimizer.execution.CrystalAttackCapability;
import dev.adrien.crystaloptimizer.execution.CrystalAttackRoutePolicy;
import dev.adrien.crystaloptimizer.execution.InteractionRoute;
import dev.adrien.crystaloptimizer.execution.InventoryCoordinator;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import dev.adrien.crystaloptimizer.execution.StatusEffectSnapshot;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class VanillaInteractionDispatcher {
    private final Minecraft minecraft;
    private final RotationController rotations;
    private final RotationMode rotationMode;
    private final InventoryCoordinator inventoryCoordinator = new InventoryCoordinator();
    private final CrystalAttackRoutePolicy crystalAttackRoutePolicy = new CrystalAttackRoutePolicy();
    private final CrystalAttackCapability crystalAttackCapability = CrystalAttackCapability.vanilla26_1_2();

    public VanillaInteractionDispatcher(
        Minecraft minecraft,
        RotationController rotations,
        RotationMode rotationMode
    ) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.rotations = Objects.requireNonNull(rotations, "rotations");
        this.rotationMode = Objects.requireNonNull(rotationMode, "rotationMode");
    }

    public DispatchReceipt dispatch(CombatAction action) {
        return dispatch(action, rotationMode, false);
    }

    public DispatchReceipt dispatch(CombatAction action, RotationMode mode, boolean critical) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(mode, "mode");

        if (action instanceof Rotate rotate) {
            boolean reached = rotations.applyAngles(
                rotate.yaw(),
                rotate.pitch(),
                mode,
                critical
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
            InteractionRoute route = crystalAttackRoutePolicy.route(
                observedInventory(player),
                attackEffects(player),
                crystalAttackCapability
            ).orElse(null);
            if (route == null) {
                return DispatchReceipt.failed("no main-hand route can positively damage crystal");
            }
            if (!aimAt(entity.getBoundingBox().getCenter(), mode, critical)) {
                return DispatchReceipt.deferred("real rotation still converging");
            }
            applySelectedSlot(player, route);
            minecraft.gameMode.attack(player, entity);
            player.swing(route.hand());
            return DispatchReceipt.sent("attacked known crystal " + attack.entityId());
        }
        if (action instanceof PlaceCrystal placeCrystal) {
            return useItemOn(
                action,
                player,
                topHit(placeCrystal.basePos()),
                "placed crystal interaction",
                mode,
                critical
            );
        }
        if (action instanceof PlaceObsidian placeObsidian) {
            BlockHitResult hit = placementHit(level, placeObsidian.pos());
            if (hit == null) {
                return DispatchReceipt.failed("no legal adjacent support face for obsidian placement");
            }
            return useItemOn(action, player, hit, "placed obsidian interaction", mode, critical);
        }
        if (action instanceof PlaceAnchor placeAnchor) {
            BlockHitResult hit = placementHit(level, placeAnchor.pos());
            if (hit == null) {
                return DispatchReceipt.failed("no legal adjacent support face for anchor placement");
            }
            return useItemOn(action, player, hit, "placed anchor interaction", mode, critical);
        }
        if (action instanceof ChargeAnchor chargeAnchor) {
            return useItemOn(
                action,
                player,
                topHit(chargeAnchor.pos()),
                "charged anchor interaction",
                mode,
                critical
            );
        }
        if (action instanceof DetonateAnchor detonateAnchor) {
            return useItemOn(
                action,
                player,
                topHit(detonateAnchor.pos()),
                "detonated anchor interaction",
                mode,
                critical
            );
        }

        return DispatchReceipt.failed("unsupported combat action: " + action.getClass().getSimpleName());
    }

    private DispatchReceipt useItemOn(
        CombatAction action,
        LocalPlayer player,
        BlockHitResult hit,
        String detail,
        RotationMode mode,
        boolean critical
    ) {
        InteractionRoute route = inventoryCoordinator
            .routeForObserved(action, observedInventory(player))
            .orElse(InteractionRoute.selectedMainhand());
        if (!aimAt(hit.getLocation(), mode, critical)) {
            return DispatchReceipt.deferred("real rotation still converging");
        }
        applySelectedSlot(player, route);
        InteractionHand hand = route.hand();
        minecraft.gameMode.useItemOn(player, hand, hit);
        player.swing(hand);
        return DispatchReceipt.sent(detail + " via " + hand.name().toLowerCase());
    }

    private static void applySelectedSlot(LocalPlayer player, InteractionRoute route) {
        if (route.selectedSlot().isPresent()) {
            player.getInventory().setSelectedSlot(route.selectedSlot().getAsInt());
        }
    }

    private static InventoryState observedInventory(LocalPlayer player) {
        LinkedHashMap<Item, Integer> counts = new LinkedHashMap<>();
        LinkedHashMap<Integer, Item> hotbar = new LinkedHashMap<>();
        LinkedHashMap<Integer, Integer> hotbarCounts = new LinkedHashMap<>();
        var inventory = player.getInventory();
        List<ItemStack> items = inventory.getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            if (slot < 9) {
                hotbar.put(slot, stack.getItem());
                hotbarCounts.put(slot, stack.getCount());
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            counts.merge(offhand.getItem(), offhand.getCount(), Integer::sum);
        }
        return new InventoryState(
            inventory.getSelectedSlot(),
            counts,
            hotbar,
            hotbarCounts,
            offhand.isEmpty() ? Optional.empty() : Optional.of(offhand.getItem())
        );
    }

    private static StatusEffectSnapshot attackEffects(LocalPlayer player) {
        var strength = player.getEffect(MobEffects.STRENGTH);
        var weakness = player.getEffect(MobEffects.WEAKNESS);
        return new StatusEffectSnapshot(
            strength == null ? OptionalInt.empty() : OptionalInt.of(strength.getAmplifier()),
            weakness == null ? OptionalInt.empty() : OptionalInt.of(weakness.getAmplifier())
        );
    }

    private boolean aimAt(Vec3 target, RotationMode mode, boolean critical) {
        return rotations.updateToward(target, mode, critical);
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
