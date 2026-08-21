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
import dev.adrien.crystaloptimizer.v2.strategy.SelfDamageEstimate;
import dev.adrien.crystaloptimizer.v2.strategy.SelfSurvivalPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        return evaluateInternal(
            approval,
            actions,
            0,
            null,
            view,
            pendingItems,
            config,
            nowNanos
        );
    }

    public ArbitrationResult evaluateFrom(
        ActionApproval approval,
        List<CombatAction> actions,
        int startIndex,
        LiveCombatView view,
        PendingItemLedger pendingItems,
        OptimizerConfig config,
        long nowNanos
    ) {
        return evaluateInternal(
            approval,
            actions,
            startIndex,
            null,
            view,
            pendingItems,
            config,
            nowNanos
        );
    }

    public ArbitrationResult evaluateContinuation(
        ActionApproval approval,
        List<CombatAction> actions,
        int startIndex,
        long ownedReservationId,
        LiveCombatView view,
        PendingItemLedger pendingItems,
        OptimizerConfig config,
        long nowNanos
    ) {
        if (ownedReservationId < 0L) {
            return ArbitrationResult.rejected(ArbitrationResult.Reason.ILLEGAL_TRANSITION);
        }
        return evaluateInternal(
            approval,
            actions,
            startIndex,
            ownedReservationId,
            view,
            pendingItems,
            config,
            nowNanos
        );
    }

    private ArbitrationResult evaluateInternal(
        ActionApproval approval,
        List<CombatAction> actions,
        int startIndex,
        Long ownedReservationId,
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
        if (nowNanos < 0L
            || actions.isEmpty()
            || startIndex < 0
            || startIndex >= actions.size()) {
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

        float liveEffectiveHealth = view.selfEffectiveHealth();
        float liveRemaining = Math.max(
            0.0f,
            liveEffectiveHealth - approval.selfDamage().worstCaseDamage()
        );
        SelfDamageEstimate liveSelf = new SelfDamageEstimate(
            approval.selfDamage().worstCaseDamage(),
            liveRemaining,
            approval.selfDamage().totemTriggered()
        );
        SelfSurvivalPolicy.Decision survival = SelfSurvivalPolicy.evaluate(
            liveSelf,
            approval.intent(),
            approval.targetDamage().expected(),
            config
        );
        if (!survival.allowed()) {
            return ArbitrationResult.rejected(mapSurvivalReason(survival.reason()));
        }

        if (ownedReservationId == null) {
            for (Map.Entry<Item, Integer> demand : approval.resources().demand().entrySet()) {
                if (pendingItems.available(demand.getKey(), view.observedCount(demand.getKey())) < demand.getValue()) {
                    return ArbitrationResult.rejected(ArbitrationResult.Reason.ITEM_UNAVAILABLE);
                }
            }
        }

        Map<Item, Integer> burstDemand = new HashMap<>();
        Integer previousAttack = null;
        if (startIndex > 0 && actions.get(startIndex - 1) instanceof AttackKnownCrystal attack) {
            previousAttack = attack.entityId();
        }

        for (int actionIndex = startIndex; actionIndex < actions.size(); actionIndex++) {
            CombatAction action = actions.get(actionIndex);
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
                    burstDemand,
                    ownedReservationId
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
                    burstDemand,
                    ownedReservationId
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
                    burstDemand,
                    ownedReservationId
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
                    burstDemand,
                    ownedReservationId
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

        return ArbitrationResult.approved(actions.subList(startIndex, actions.size()));
    }

    private static ArbitrationResult.Reason mapSurvivalReason(SelfSurvivalPolicy.Reason reason) {
        return switch (reason) {
            case SELF_LETHAL -> ArbitrationResult.Reason.SELF_LETHAL;
            case SELF_TOTEM_POP -> ArbitrationResult.Reason.SELF_TOTEM_POP;
            case SELF_DAMAGE_LIMIT -> ArbitrationResult.Reason.SELF_DAMAGE_LIMIT;
            case BAD_TRADE -> ArbitrationResult.Reason.BAD_TRADE;
            case ALLOWED -> throw new IllegalArgumentException("ALLOWED cannot be mapped to rejection");
        };
    }

    private static ArbitrationResult requireItem(
        Item item,
        LiveCombatView view,
        PendingItemLedger pendingItems,
        Map<Item, Integer> burstDemand,
        Long ownedReservationId
    ) {
        int alreadyNeeded = burstDemand.getOrDefault(item, 0);
        int observed = view.observedCount(item);
        int available = ownedReservationId == null
            ? pendingItems.available(item, observed)
            : pendingItems.availableExcluding(ownedReservationId, item, observed);
        if (available <= alreadyNeeded) {
            return ArbitrationResult.rejected(ArbitrationResult.Reason.ITEM_UNAVAILABLE);
        }
        burstDemand.put(item, alreadyNeeded + 1);
        return null;
    }
}
