package dev.adrien.crystaloptimizer.v2.execution;

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
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ActionArbiter {
    public ArbitrationResult evaluate(
        ActionApproval approval,
        List<CombatAction> actions,
        LiveCombatView view,
        PendingItemLedger pendingItems,
        OptimizerConfig config,
        long nowNanos
    ) {
        Objects.requireNonNull(approval, "approval");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(pendingItems, "pendingItems");
        Objects.requireNonNull(config, "config");
        if (nowNanos < 0L || actions.isEmpty()) {
            return ArbitrationResult.rejected(ArbitrationResult.Reason.ILLEGAL_TRANSITION);
        }

        if (!approval.isCurrent(
            view.worldRevision(),
            view.targetRevision(approval.targetId()),
            view.inventoryRevision(),
            view.configRevision(),
            nowNanos
        )) {
            return ArbitrationResult.rejected(ArbitrationResult.Reason.STALE_APPROVAL);
        }
        if (!view.targetValid(approval.targetId())) {
            return ArbitrationResult.rejected(ArbitrationResult.Reason.INVALID_TARGET);
        }
        if (approval.worstCaseSelfDamage() > config.maxSelfDamage()) {
            return ArbitrationResult.rejected(ArbitrationResult.Reason.SELF_DAMAGE_LIMIT);
        }

        Map<Item, Integer> burstDemand = new HashMap<>();
        Integer previousAttack = null;
        for (CombatAction action : actions) {
            if (action instanceof AttackKnownCrystal attack) {
                if (!config.crystals()) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.FEATURE_DISABLED);
                }
                if (!view.liveCrystal(attack.entityId())) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.ENTITY_GONE);
                }
                if (!view.withinEntityReach(attack.entityId())) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.ENTITY_OUT_OF_REACH);
                }
                previousAttack = attack.entityId();
                continue;
            }

            if (action instanceof PlaceCrystal place) {
                if (!config.crystals()) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.FEATURE_DISABLED);
                }
                if (!view.withinBlockReach(place.basePos())) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.BLOCK_OUT_OF_REACH);
                }
                if (previousAttack != null
                    && !view.crystalBaseCanFollowBreak(place.basePos(), previousAttack)) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.ILLEGAL_TRANSITION);
                }
                ArbitrationResult itemCheck = requireItem(
                    Items.END_CRYSTAL,
                    view,
                    pendingItems,
                    burstDemand
                );
                if (itemCheck != null) {
                    return itemCheck;
                }
                previousAttack = null;
                continue;
            }

            if (action instanceof PlaceObsidian place) {
                if (!view.withinBlockReach(place.pos())) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.BLOCK_OUT_OF_REACH);
                }
                ArbitrationResult itemCheck = requireItem(
                    Items.OBSIDIAN,
                    view,
                    pendingItems,
                    burstDemand
                );
                if (itemCheck != null) {
                    return itemCheck;
                }
                previousAttack = null;
                continue;
            }

            if (action instanceof PlaceAnchor place) {
                if (!config.anchors()) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.FEATURE_DISABLED);
                }
                if (!view.withinBlockReach(place.pos())) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.BLOCK_OUT_OF_REACH);
                }
                ArbitrationResult itemCheck = requireItem(
                    Items.RESPAWN_ANCHOR,
                    view,
                    pendingItems,
                    burstDemand
                );
                if (itemCheck != null) {
                    return itemCheck;
                }
                previousAttack = null;
                continue;
            }

            if (action instanceof ChargeAnchor charge) {
                if (!config.anchors()) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.FEATURE_DISABLED);
                }
                if (!view.withinBlockReach(charge.pos())) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.BLOCK_OUT_OF_REACH);
                }
                ArbitrationResult itemCheck = requireItem(
                    Items.GLOWSTONE,
                    view,
                    pendingItems,
                    burstDemand
                );
                if (itemCheck != null) {
                    return itemCheck;
                }
                previousAttack = null;
                continue;
            }

            if (action instanceof DetonateAnchor detonate) {
                if (!config.anchors()) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.FEATURE_DISABLED);
                }
                if (!view.withinBlockReach(detonate.pos())) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.BLOCK_OUT_OF_REACH);
                }
                previousAttack = null;
                continue;
            }

            if (action instanceof Rotate
                || action instanceof SelectHotbarSlot
                || action instanceof Wait) {
                previousAttack = null;
                continue;
            }

            return ArbitrationResult.rejected(ArbitrationResult.Reason.UNSUPPORTED_ACTION);
        }

        return ArbitrationResult.approved(actions);
    }

    private static ArbitrationResult requireItem(
        Item item,
        LiveCombatView view,
        PendingItemLedger pendingItems,
        Map<Item, Integer> burstDemand
    ) {
        int alreadyNeeded = burstDemand.getOrDefault(item, 0);
        int available = pendingItems.available(item, view.observedCount(item));
        if (available <= alreadyNeeded) {
            return ArbitrationResult.rejected(ArbitrationResult.Reason.ITEM_UNAVAILABLE);
        }
        burstDemand.put(item, alreadyNeeded + 1);
        return null;
    }
}
