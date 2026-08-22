package dev.adrien.crystaloptimizer.v2.debug;

import dev.adrien.crystaloptimizer.v2.state.StrategicResult;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.DamageOpportunity;
import dev.adrien.crystaloptimizer.v2.strategy.StrategicCombatPlanner;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Runs deterministic strategic decisions from immutable replay inputs. */
public final class ReplayRunner {
    public ReplayResult run(ReplayFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        StrategicSnapshot current = fixture.snapshot();
        StrategicResult lastResult = null;
        StrategicCombatPlanner planner = new StrategicCombatPlanner();
        boolean ticked = false;

        for (ReplayEvent event : fixture.events()) {
            current = applyEvent(current, event);
            if ("control.tick".equals(event.type())) {
                lastResult = planner.computeDeterministic(current, fixture.config());
                ticked = true;
            }
        }
        if (!ticked) {
            lastResult = planner.computeDeterministic(current, fixture.config());
        }

        if (lastResult == null) {
            UUID target = current.targetRevisions().keySet().stream()
                .sorted()
                .findFirst()
                .orElse(current.selfId());
            return new ReplayResult(target, "none", "no-decision", current.worldRevision());
        }

        String key;
        String decisionClass;
        if (lastResult.plannedOpportunity().isPresent()) {
            var planned = lastResult.plannedOpportunity().orElseThrow();
            key = planned.terminalOpportunity().id();
            decisionClass = "planned-sequence:" + planned.sequence().actions().stream()
                .map(action -> action.getClass().getSimpleName())
                .reduce((left, right) -> left + ">" + right)
                .orElse("empty");
        } else {
            DamageOpportunity direct = lastResult.damageMap().opportunities().values().stream()
                .sorted(Comparator
                    .comparing(DamageOpportunity::lethal).reversed()
                    .thenComparingDouble((DamageOpportunity opportunity) ->
                        opportunity.targetDamage().killProbability()).reversed()
                    .thenComparingDouble((DamageOpportunity opportunity) ->
                        opportunity.targetDamage().expected()).reversed()
                    .thenComparing(DamageOpportunity::id))
                .findFirst()
                .orElse(null);
            if (direct == null) {
                key = "none";
                decisionClass = "no-opportunity";
            } else {
                key = direct.id();
                decisionClass = "direct:" + direct.intent().name().toLowerCase();
            }
        }
        return new ReplayResult(
            lastResult.targetId(),
            key,
            decisionClass,
            current.worldRevision()
        );
    }

    private static StrategicSnapshot applyEvent(
        StrategicSnapshot snapshot,
        ReplayEvent event
    ) {
        return switch (event.type()) {
            case "combat.block_changed" -> withRevisions(
                snapshot,
                saturatingIncrement(snapshot.worldRevision()),
                snapshot.inventoryRevision(),
                snapshot.targetRevisions()
            );
            case "combat.inventory_changed" -> withRevisions(
                snapshot,
                snapshot.worldRevision(),
                parseLongOrIncrement(
                    event.fields().get("inventoryRevision"),
                    snapshot.inventoryRevision()
                ),
                snapshot.targetRevisions()
            );
            case "combat.target_moved" -> targetMoved(snapshot, event.fields());
            case "combat.crystal_removed" -> crystalRemoved(snapshot, event.fields());
            default -> snapshot;
        };
    }

    private static StrategicSnapshot crystalRemoved(
        StrategicSnapshot snapshot,
        Map<String, String> fields
    ) {
        String entityText = fields.get("entityId");
        if (entityText == null) {
            return snapshot;
        }
        int entityId;
        try {
            entityId = Integer.parseInt(entityText);
        } catch (NumberFormatException invalid) {
            return snapshot;
        }
        var combat = snapshot.combat();
        var remaining = combat.crystals().stream()
            .filter(crystal -> crystal.entityId() != entityId)
            .toList();
        if (remaining.size() == combat.crystals().size()) {
            return snapshot;
        }
        var revisedCombat = new dev.adrien.crystaloptimizer.world.CombatSnapshot(
            combat.worldRevision(),
            combat.selfId(),
            combat.region(),
            combat.combatants(),
            remaining,
            combat.anchors(),
            combat.inventory(),
            combat.timing(),
            combat.legality(),
            combat.spatial(),
            combat.difficulty()
        );
        return withCombat(snapshot, revisedCombat, snapshot.targetRevisions());
    }

    private static StrategicSnapshot targetMoved(
        StrategicSnapshot snapshot,
        Map<String, String> fields
    ) {
        String targetText = fields.get("targetId");
        if (targetText == null) {
            return snapshot;
        }
        UUID targetId;
        try {
            targetId = UUID.fromString(targetText);
        } catch (IllegalArgumentException invalid) {
            return snapshot;
        }
        long currentRevision = snapshot.targetRevisions().getOrDefault(targetId, 0L);
        long nextRevision = parseLongOrIncrement(fields.get("targetRevision"), currentRevision);
        LinkedHashMap<UUID, Long> updated = new LinkedHashMap<>(snapshot.targetRevisions());
        updated.put(targetId, nextRevision);
        return withRevisions(
            snapshot,
            snapshot.worldRevision(),
            snapshot.inventoryRevision(),
            updated
        );
    }

    private static StrategicSnapshot withRevisions(
        StrategicSnapshot snapshot,
        long worldRevision,
        long inventoryRevision,
        Map<UUID, Long> targetRevisions
    ) {
        var combat = snapshot.combat();
        var revisedCombat = new dev.adrien.crystaloptimizer.world.CombatSnapshot(
            worldRevision,
            combat.selfId(),
            combat.region(),
            combat.combatants(),
            combat.crystals(),
            combat.anchors(),
            combat.inventory(),
            combat.timing(),
            combat.legality(),
            combat.spatial(),
            combat.difficulty()
        );
        return new StrategicSnapshot(
            snapshot.snapshotId(),
            worldRevision,
            inventoryRevision,
            snapshot.configRevision(),
            snapshot.capturedAtNanos(),
            snapshot.selfId(),
            targetRevisions,
            revisedCombat,
            snapshot.movementHistory(),
            snapshot.protectedPlayerIds(),
            snapshot.targetProtection(),
            snapshot.timing()
        );
    }

    private static StrategicSnapshot withCombat(
        StrategicSnapshot snapshot,
        dev.adrien.crystaloptimizer.world.CombatSnapshot combat,
        Map<UUID, Long> targetRevisions
    ) {
        return new StrategicSnapshot(
            snapshot.snapshotId(),
            snapshot.worldRevision(),
            snapshot.inventoryRevision(),
            snapshot.configRevision(),
            snapshot.capturedAtNanos(),
            snapshot.selfId(),
            targetRevisions,
            combat,
            snapshot.movementHistory(),
            snapshot.protectedPlayerIds(),
            snapshot.targetProtection(),
            snapshot.timing()
        );
    }

    private static long parseLongOrIncrement(String value, long current) {
        if (value != null) {
            try {
                long parsed = Long.parseLong(value);
                if (parsed >= current) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Deterministic fallback below.
            }
        }
        return saturatingIncrement(current);
    }

    private static long saturatingIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }
}
