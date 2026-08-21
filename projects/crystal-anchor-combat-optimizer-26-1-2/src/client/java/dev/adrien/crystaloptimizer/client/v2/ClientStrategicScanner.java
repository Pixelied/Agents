package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboard;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboardSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import dev.adrien.crystaloptimizer.v2.strategy.DamageOpportunity;
import dev.adrien.crystaloptimizer.v2.strategy.FastOpportunitySelector;
import dev.adrien.crystaloptimizer.v2.strategy.HurtThresholdEstimate;
import dev.adrien.crystaloptimizer.v2.strategy.HurtWindowTracker;
import dev.adrien.crystaloptimizer.v2.strategy.SelectionContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.client.player.AbstractClientPlayer;

public final class ClientStrategicScanner {
    private static final long APPROVAL_LIFETIME_NANOS = 250_000_000L;

    private final ClientDamageMapBuilder damageMaps;
    private final CombatBlackboard blackboard;
    private final FastOpportunitySelector selector;
    private final HurtWindowTracker hurtWindows;
    private final AtomicLong nextApprovalId = new AtomicLong();

    public ClientStrategicScanner(
        ClientDamageMapBuilder damageMaps,
        CombatBlackboard blackboard,
        FastOpportunitySelector selector,
        HurtWindowTracker hurtWindows
    ) {
        this.damageMaps = Objects.requireNonNull(damageMaps, "damageMaps");
        this.blackboard = Objects.requireNonNull(blackboard, "blackboard");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.hurtWindows = Objects.requireNonNull(hurtWindows, "hurtWindows");
    }

    public DamageMap scan(
        AbstractClientPlayer target,
        long worldRevision,
        long targetRevision,
        long inventoryRevision,
        long configRevision,
        OptimizerConfig config,
        long nowNanos
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        DamageMap map = damageMaps.update(target, worldRevision, targetRevision, config);
        HurtThresholdEstimate threshold = hurtWindows.estimate(
            target.getUUID(),
            target.invulnerableTime,
            nowNanos
        );
        SelectionContext context = new SelectionContext(
            threshold,
            target.getHealth(),
            config.strategy()
        );

        EnumMap<ApprovalSlot, ActionApproval> approvals = new EnumMap<>(ApprovalSlot.class);
        List<DamageOpportunity> all = List.copyOf(map.opportunities().values());
        putSelected(approvals, ApprovalSlot.LETHAL, all, DamageOpportunity::lethal, context,
            map, inventoryRevision, configRevision, nowNanos);
        putSelected(approvals, ApprovalSlot.FINISHER, all,
            opportunity -> direct(opportunity) && opportunity.timing().hardFeedbackBoundaries() == 0,
            context, map, inventoryRevision, configRevision, nowNanos);
        putSelected(approvals, ApprovalSlot.STAIRCASE, all,
            opportunity -> FastOpportunitySelector.usefulLowerBound(
                opportunity.targetDamage().lowerBound(), threshold) > 0.0f,
            context, map, inventoryRevision, configRevision, nowNanos);
        putSelected(approvals, ApprovalSlot.RECYCLE, all,
            opportunity -> opportunity.id().startsWith("recycle:"),
            context, map, inventoryRevision, configRevision, nowNanos);
        putSelected(approvals, ApprovalSlot.BREAK, all,
            opportunity -> opportunity.id().startsWith("break:"),
            context, map, inventoryRevision, configRevision, nowNanos);
        putSelected(approvals, ApprovalSlot.PLACE, all,
            opportunity -> opportunity.id().startsWith("place:"),
            context, map, inventoryRevision, configRevision, nowNanos);
        putSelected(approvals, ApprovalSlot.PRESSURE, all,
            opportunity -> !opportunity.id().startsWith("prepare:"),
            context, map, inventoryRevision, configRevision, nowNanos);
        putSelected(approvals, ApprovalSlot.PREPARE, all,
            opportunity -> opportunity.id().startsWith("prepare:"),
            context, map, inventoryRevision, configRevision, nowNanos);

        blackboard.publish(new CombatBlackboardSnapshot(
            target.getUUID(),
            targetRevision,
            worldRevision,
            inventoryRevision,
            configRevision,
            approvals
        ));
        return map;
    }

    private void putSelected(
        EnumMap<ApprovalSlot, ActionApproval> approvals,
        ApprovalSlot slot,
        List<DamageOpportunity> opportunities,
        Predicate<DamageOpportunity> filter,
        SelectionContext context,
        DamageMap map,
        long inventoryRevision,
        long configRevision,
        long nowNanos
    ) {
        Optional<DamageOpportunity> selected = selector.select(
            opportunities.stream().filter(filter).toList(),
            context
        );
        selected.ifPresent(opportunity -> approvals.put(
            slot,
            new ActionApproval(
                nextApprovalId.getAndIncrement(),
                map.targetId(),
                slot,
                opportunity.actionSpec(),
                opportunity.targetDamage(),
                opportunity.intent(),
                opportunity.selfDamage(),
                opportunity.resources(),
                opportunity.timing(),
                map.worldRevision(),
                map.targetRevision(),
                inventoryRevision,
                configRevision,
                saturatingAdd(nowNanos, APPROVAL_LIFETIME_NANOS)
            )
        ));
    }

    private static boolean direct(DamageOpportunity opportunity) {
        return opportunity.id().startsWith("break:")
            || opportunity.id().startsWith("anchor:");
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
