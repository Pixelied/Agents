package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class StrategicTargetSelector {
    public static final int MAX_EXACT_TARGETS = 8;
    private static final double CERTIFIED_CONFIDENCE = 0.80;

    public List<TargetPreScore> selectCandidates(
        CombatSnapshot snapshot,
        OptimizerConfig config,
        Set<UUID> protectedIds
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(protectedIds, "protectedIds");
        var selfSpatial = snapshot.spatial().get(snapshot.selfId());
        if (selfSpatial == null) {
            return List.of();
        }
        double rangeSquared = config.targetRange() * config.targetRange();
        ArrayList<TargetPreScore> scores = new ArrayList<>();
        for (UUID candidateId : snapshot.combatants().keySet()) {
            if (!TargetEligibilityPolicy.isEligible(snapshot, candidateId, protectedIds)) {
                continue;
            }
            var candidateSpatial = snapshot.spatial().get(candidateId);
            double distanceSquared = selfSpatial.position().distanceToSqr(candidateSpatial.position());
            if (distanceSquared > rangeSquared) {
                continue;
            }
            var candidate = snapshot.combatants().get(candidateId);
            scores.add(new TargetPreScore(
                candidateId,
                distanceSquared,
                candidate.health() + candidate.absorption(),
                HurtThresholdEstimate.MAX_CRYSTAL_HARD_INCOMING,
                false,
                false
            ));
        }
        return orderAndLimit(scores);
    }

    public Optional<Selection> selectBest(
        List<TargetPreScore> preScores,
        UUID stickyTarget,
        Set<UUID> recentAttackers,
        Function<UUID, DamageMap> exactMaps
    ) {
        Objects.requireNonNull(preScores, "preScores");
        Objects.requireNonNull(recentAttackers, "recentAttackers");
        Objects.requireNonNull(exactMaps, "exactMaps");

        LinkedHashMap<UUID, TargetPreScore> merged = new LinkedHashMap<>();
        for (TargetPreScore score : preScores) {
            Objects.requireNonNull(score, "target pre-score");
            boolean sticky = score.sticky() || score.targetId().equals(stickyTarget);
            boolean recent = score.recentAttacker() || recentAttackers.contains(score.targetId());
            merged.putIfAbsent(score.targetId(), new TargetPreScore(
                score.targetId(),
                score.distanceSquared(),
                score.effectiveHealthUpperBound(),
                score.cheapDamageUpperBound(),
                recent,
                sticky
            ));
        }

        List<TargetPreScore> ordered = orderAndLimit(new ArrayList<>(merged.values()));
        if (ordered.isEmpty()) {
            return Optional.empty();
        }
        boolean truncated = merged.size() > MAX_EXACT_TARGETS;
        TargetPreScore bestScore = null;
        DamageMap bestMap = null;
        ExactRank bestRank = null;
        int exactEvaluated = 0;
        for (TargetPreScore score : ordered) {
            DamageMap map = Objects.requireNonNull(
                exactMaps.apply(score.targetId()),
                "exact map for " + score.targetId()
            );
            exactEvaluated++;
            ExactRank rank = rank(map, score);
            if (bestRank == null || rank.compareTo(bestRank) > 0) {
                bestRank = rank;
                bestScore = score;
                bestMap = map;
            }
        }
        return Optional.of(new Selection(
            Objects.requireNonNull(bestScore).targetId(),
            Objects.requireNonNull(bestMap),
            exactEvaluated,
            truncated
        ));
    }

    private static List<TargetPreScore> orderAndLimit(List<TargetPreScore> scores) {
        return scores.stream()
            .sorted(Comparator
                .comparing(TargetPreScore::sticky).reversed()
                .thenComparing(TargetPreScore::recentAttacker, Comparator.reverseOrder())
                .thenComparing(TargetPreScore::cheapCouldFinish, Comparator.reverseOrder())
                .thenComparingDouble(TargetPreScore::effectiveHealthUpperBound)
                .thenComparingDouble(TargetPreScore::distanceSquared)
                .thenComparing(score -> score.targetId().toString()))
            .limit(MAX_EXACT_TARGETS)
            .toList();
    }

    private static ExactRank rank(DamageMap map, TargetPreScore preScore) {
        boolean certifiedLethal = false;
        double lethalP90 = Double.POSITIVE_INFINITY;
        float bestLower = 0.0f;
        float bestExpected = 0.0f;
        double fastestP90 = Double.POSITIVE_INFINITY;
        for (DamageOpportunity opportunity : map.opportunities().values()) {
            DamageEstimate estimate = opportunity.targetDamage();
            boolean lethal = opportunity.lethal()
                && estimate.killProbability() == 1.0
                && estimate.confidence() >= CERTIFIED_CONFIDENCE;
            if (lethal) {
                certifiedLethal = true;
                lethalP90 = Math.min(lethalP90, finiteOrMax(opportunity.timing().p90Millis()));
            }
            bestLower = Math.max(bestLower, estimate.lowerBound());
            bestExpected = Math.max(bestExpected, estimate.expected());
            fastestP90 = Math.min(fastestP90, finiteOrMax(opportunity.timing().p90Millis()));
        }
        return new ExactRank(
            certifiedLethal,
            -lethalP90,
            bestLower,
            bestExpected,
            -fastestP90,
            preScore.sticky(),
            preScore.recentAttacker(),
            -preScore.distanceSquared()
        );
    }

    private static double finiteOrMax(double value) {
        return Double.isFinite(value) && value >= 0.0 ? value : Double.MAX_VALUE;
    }

    public record Selection(
        UUID targetId,
        DamageMap damageMap,
        int exactEvaluated,
        boolean truncated
    ) {
        public Selection {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(damageMap, "damageMap");
            if (exactEvaluated <= 0 || exactEvaluated > MAX_EXACT_TARGETS) {
                throw new IllegalArgumentException("exactEvaluated outside bounded target budget");
            }
        }
    }

    private record ExactRank(
        boolean certifiedLethal,
        double negativeLethalP90,
        float lowerDamage,
        float expectedDamage,
        double negativeFastestP90,
        boolean sticky,
        boolean recentAttacker,
        double negativeDistanceSquared
    ) implements Comparable<ExactRank> {
        @Override
        public int compareTo(ExactRank other) {
            return Comparator
                .comparing(ExactRank::certifiedLethal)
                .thenComparingDouble(ExactRank::negativeLethalP90)
                .thenComparingDouble(ExactRank::lowerDamage)
                .thenComparingDouble(ExactRank::expectedDamage)
                .thenComparingDouble(ExactRank::negativeFastestP90)
                .thenComparing(ExactRank::sticky)
                .thenComparing(ExactRank::recentAttacker)
                .thenComparingDouble(ExactRank::negativeDistanceSquared)
                .compare(this, other);
        }
    }
}
