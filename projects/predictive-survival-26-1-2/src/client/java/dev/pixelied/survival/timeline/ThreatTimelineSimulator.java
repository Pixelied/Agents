package dev.pixelied.survival.timeline;

import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.damage.DamageResult;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.damage.DamageStage;
import dev.pixelied.survival.damage.HurtState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ThreatTimelineSimulator {
    private static final int MAX_PERMUTATION_GROUP = 6;
    private static final Comparator<ThreatEvent> BASE_ORDER = Comparator
        .comparingLong((ThreatEvent event) -> event.impact().earliest())
        .thenComparingLong(event -> event.impact().latest())
        .thenComparing(ThreatEvent::id);
    private static final Comparator<ThreatEvent> CONSERVATIVE_FALLBACK_ORDER = Comparator
        .comparingDouble((ThreatEvent event) -> event.damage().rawDamage().max()).reversed()
        .thenComparing(ThreatEvent::id);

    private final DamageSimulator damageSimulator;

    public ThreatTimelineSimulator() {
        this(new DamageSimulator());
    }

    public ThreatTimelineSimulator(DamageSimulator damageSimulator) {
        this.damageSimulator = damageSimulator;
    }

    public TimelineResult simulate(PlayerSnapshot start, ThreatTimeline timeline) {
        List<ThreatEvent> sorted = new ArrayList<>(timeline.events());
        sorted.sort(BASE_ORDER);
        Set<String> timelineEventIds = new HashSet<>();
        for (ThreatEvent event : sorted) {
            if (!timelineEventIds.add(event.id())) {
                throw new IllegalArgumentException("duplicate threat event id: " + event.id());
            }
        }

        PlayerSnapshot working = start;
        long previousTick = 0;
        List<TimelineEventResult> allResults = new ArrayList<>();
        int consumed = 0;
        Optional<String> firstLethal = Optional.empty();
        boolean survivalGuaranteed = true;
        Set<String> acceptedEventIds = new HashSet<>();
        Set<String> processedEventIds = new HashSet<>();

        for (List<ThreatEvent> group : overlapGroups(sorted)) {
            GroupOutcome outcome = worstGroupOutcome(
                working,
                previousTick,
                group,
                acceptedEventIds,
                processedEventIds,
                timelineEventIds
            );
            working = outcome.player();
            previousTick = outcome.lastTick();
            acceptedEventIds = new HashSet<>(outcome.acceptedEventIds());
            processedEventIds = new HashSet<>(outcome.processedEventIds());
            allResults.addAll(outcome.results());
            consumed += outcome.consumedProtection();
            if (firstLethal.isEmpty() && outcome.firstLethalEventId().isPresent()) {
                firstLethal = outcome.firstLethalEventId();
            }
            if (!outcome.survived()) {
                survivalGuaranteed = false;
                break;
            }
        }

        return new TimelineResult(
            allResults,
            working.health(),
            working.absorption(),
            survivalGuaranteed && working.health() > 0f && firstLethal.isEmpty(),
            consumed,
            firstLethal
        );
    }

    private GroupOutcome worstGroupOutcome(
        PlayerSnapshot start,
        long previousTick,
        List<ThreatEvent> group,
        Set<String> acceptedBefore,
        Set<String> processedBefore,
        Set<String> timelineEventIds
    ) {
        if (group.size() == 1) {
            long[] schedule = schedule(group, previousTick);
            GroupOutcome outcome = simulateOrder(
                start,
                previousTick,
                group,
                schedule,
                acceptedBefore,
                processedBefore,
                timelineEventIds
            );
            if (outcome == null) {
                throw new IllegalArgumentException("No feasible ordering for dependent threat group");
            }
            return outcome;
        }

        if (group.size() > MAX_PERMUTATION_GROUP) {
            List<ThreatEvent> fallback = new ArrayList<>(group);
            fallback.sort(CONSERVATIVE_FALLBACK_ORDER);
            long[] schedule = schedule(fallback, previousTick);
            GroupOutcome outcome = schedule == null ? null : simulateOrder(
                start,
                previousTick,
                fallback,
                schedule,
                acceptedBefore,
                processedBefore,
                timelineEventIds
            );
            if (outcome != null) return outcome;

            fallback.sort(BASE_ORDER);
            schedule = schedule(fallback, previousTick);
            outcome = schedule == null ? null : simulateOrder(
                start,
                previousTick,
                fallback,
                schedule,
                acceptedBefore,
                processedBefore,
                timelineEventIds
            );
            if (outcome == null) {
                throw new IllegalArgumentException("No feasible ordering for dependent threat group");
            }
            return outcome;
        }

        List<List<ThreatEvent>> permutations = new ArrayList<>();
        permute(new ArrayList<>(group), 0, permutations);
        GroupOutcome worst = null;
        for (List<ThreatEvent> permutation : permutations) {
            long[] schedule = schedule(permutation, previousTick);
            if (schedule == null) continue;
            GroupOutcome candidate = simulateOrder(
                start,
                previousTick,
                permutation,
                schedule,
                acceptedBefore,
                processedBefore,
                timelineEventIds
            );
            if (candidate == null) continue;
            if (worst == null || isWorse(candidate, worst)) {
                worst = candidate;
            }
        }

        if (worst == null) {
            throw new IllegalArgumentException("No feasible ordering for overlapping threat group");
        }
        return worst;
    }

    private GroupOutcome simulateOrder(
        PlayerSnapshot start,
        long previousTick,
        List<ThreatEvent> order,
        long[] schedule,
        Set<String> acceptedBefore,
        Set<String> processedBefore,
        Set<String> timelineEventIds
    ) {
        if (schedule == null) throw new IllegalArgumentException("order has no feasible schedule");
        PlayerSnapshot working = start;
        long lastTick = previousTick;
        List<TimelineEventResult> results = new ArrayList<>();
        int consumed = 0;
        Optional<String> firstLethal = Optional.empty();
        boolean survivalGuaranteed = true;
        Set<String> acceptedEventIds = new HashSet<>(acceptedBefore);
        Set<String> processedEventIds = new HashSet<>(processedBefore);
        Set<String> groupEventIds = new HashSet<>();
        for (ThreatEvent event : order) groupEventIds.add(event.id());

        for (int i = 0; i < order.size(); i++) {
            ThreatEvent event = order.get(i);
            long eventTick = schedule[i];
            working = ageHurtState(working, eventTick - lastTick);
            lastTick = eventTick;

            Optional<String> requiredId = event.requiresAcceptedEventId();
            if (requiredId.isPresent() && !acceptedEventIds.contains(requiredId.get())) {
                String prerequisite = requiredId.get();
                if (!processedEventIds.contains(prerequisite)) {
                    if (groupEventIds.contains(prerequisite) || timelineEventIds.contains(prerequisite)) {
                        return null;
                    }
                    throw new IllegalArgumentException(
                        "threat event " + event.id() + " requires unknown event " + prerequisite
                    );
                }
                processedEventIds.add(event.id());
                continue;
            }

            DamageResult damageResult = damageSimulator.simulate(working, event.damage());
            damageResult = applyBlockingDisable(damageResult, event.damage());
            float finalDamage = damageResult.trace().has(DamageStage.HEALTH_DAMAGE)
                ? damageResult.trace().after(DamageStage.HEALTH_DAMAGE)
                : 0f;
            results.add(new TimelineEventResult(
                event,
                event.damage().rawDamage().max(),
                finalDamage,
                damageResult
            ));

            processedEventIds.add(event.id());
            if (!damageResult.rejected()) acceptedEventIds.add(event.id());
            if (damageResult.deathProtectionConsumed()) consumed++;
            working = damageResult.after();

            if (damageResult.postStateUncertain()) {
                // The DEATH_PROTECTION one-health rescue is known, but an ordered consume effect
                // (for example a random teleport) leaves the later survival state unknowable from
                // client-visible data. Fail closed instead of presenting that branch as guaranteed.
                survivalGuaranteed = false;
                break;
            }

            if (working.health() <= 0f && !damageResult.deathProtectionConsumed()) {
                firstLethal = Optional.of(event.id());
                break;
            }
        }

        return new GroupOutcome(
            working,
            lastTick,
            results,
            consumed,
            firstLethal,
            survivalGuaranteed && working.health() > 0f && firstLethal.isEmpty(),
            Set.copyOf(acceptedEventIds),
            Set.copyOf(processedEventIds)
        );
    }

    private static DamageResult applyBlockingDisable(DamageResult result, dev.pixelied.survival.damage.DamageSourceSnapshot source) {
        if (source.blockingDisableSeconds() <= 0f || source.has(dev.pixelied.survival.damage.DamageFlag.IS_PROJECTILE)) return result;
        if (!result.trace().has(DamageStage.BLOCKING)) return result;
        float blocked = result.trace().before(DamageStage.BLOCKING) - result.trace().after(DamageStage.BLOCKING);
        if (!(blocked > 0f)) return result;

        PlayerSnapshot after = result.after();
        dev.pixelied.survival.damage.BlockingSnapshot blocking = after.blocking();
        int ticks = blocking.profile()
            .map(profile -> profile.disableTicks(source.blockingDisableSeconds()))
            .orElseGet(() -> {
                double raw = Math.round(source.blockingDisableSeconds() * 20f);
                return raw >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) raw);
            });
        if (ticks <= 0) return result;
        PlayerSnapshot disabled = new PlayerSnapshot(
            after.health(), after.absorption(), after.playerInvulnerable(), after.abilityInvulnerable(), after.deadOrDying(),
            after.difficulty(), after.mitigation(), after.statusEffects(), blocking.disableForTicks(ticks),
            after.hurtState(), after.deathProtection(), after.boundingBox(), after.position(), after.velocity(),
            after.equipmentItemKeys(), after.stateProperties()
        );
        return new DamageResult(
            disabled, result.trace(), result.rejected(), result.deathProtectionConsumed(), result.postStateUncertain()
        );
    }

    private static PlayerSnapshot ageHurtState(PlayerSnapshot player, long elapsedTicks) {
        if (elapsedTicks <= 0) return player;
        HurtState hurt = player.hurtState();
        int elapsed = elapsedTicks > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsedTicks;
        HurtState aged = new HurtState(
            hurt.lastHurt(),
            Math.max(0, hurt.invulnerableTime() - elapsed),
            hurt.confidence()
        );
        return new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(),
            player.deadOrDying(), player.difficulty(), player.mitigation(), player.statusEffects(), player.blocking().age(elapsed),
            aged, player.deathProtection(), player.boundingBox(), player.position(), player.velocity(),
            player.equipmentItemKeys(), player.stateProperties()
        );
    }

    private static List<List<ThreatEvent>> overlapGroups(List<ThreatEvent> sorted) {
        List<List<ThreatEvent>> groups = new ArrayList<>();
        if (sorted.isEmpty()) return groups;

        List<ThreatEvent> current = new ArrayList<>();
        long currentLatest = Long.MIN_VALUE;
        for (ThreatEvent event : sorted) {
            if (!current.isEmpty() && event.impact().earliest() > currentLatest) {
                groups.add(List.copyOf(current));
                current.clear();
                currentLatest = Long.MIN_VALUE;
            }
            current.add(event);
            currentLatest = Math.max(currentLatest, event.impact().latest());
        }
        if (!current.isEmpty()) groups.add(List.copyOf(current));
        return groups;
    }

    private static long[] schedule(List<ThreatEvent> order, long previousTick) {
        long[] ticks = new long[order.size()];
        long nextUpper = Long.MAX_VALUE;
        for (int i = order.size() - 1; i >= 0; i--) {
            ThreatEvent event = order.get(i);
            long upper = Math.min(event.impact().latest(), nextUpper);
            long lower = Math.max(event.impact().earliest(), previousTick);
            if (upper < lower) return null;
            ticks[i] = upper;
            nextUpper = upper;
        }
        return ticks;
    }

    private static void permute(List<ThreatEvent> events, int index, List<List<ThreatEvent>> output) {
        if (index == events.size()) {
            output.add(List.copyOf(events));
            return;
        }
        for (int i = index; i < events.size(); i++) {
            swap(events, index, i);
            permute(events, index + 1, output);
            swap(events, index, i);
        }
    }

    private static void swap(List<ThreatEvent> events, int a, int b) {
        ThreatEvent tmp = events.get(a);
        events.set(a, events.get(b));
        events.set(b, tmp);
    }

    private static boolean isWorse(GroupOutcome candidate, GroupOutcome currentWorst) {
        if (candidate.survived() != currentWorst.survived()) {
            return !candidate.survived();
        }

        float candidateEffective = candidate.player().health() + candidate.player().absorption();
        float currentEffective = currentWorst.player().health() + currentWorst.player().absorption();
        int effectiveComparison = Float.compare(candidateEffective, currentEffective);
        if (effectiveComparison != 0) return effectiveComparison < 0;

        if (candidate.consumedProtection() != currentWorst.consumedProtection()) {
            return candidate.consumedProtection() > currentWorst.consumedProtection();
        }

        int count = Math.min(candidate.results().size(), currentWorst.results().size());
        for (int i = 0; i < count; i++) {
            float candidateRaw = candidate.results().get(i).preMitigationRaw();
            float currentRaw = currentWorst.results().get(i).preMitigationRaw();
            int rawComparison = Float.compare(candidateRaw, currentRaw);
            if (rawComparison != 0) return rawComparison > 0;

            int idComparison = candidate.results().get(i).event().id()
                .compareTo(currentWorst.results().get(i).event().id());
            if (idComparison != 0) return idComparison < 0;
        }
        return candidate.results().size() > currentWorst.results().size();
    }

    private record GroupOutcome(
        PlayerSnapshot player,
        long lastTick,
        List<TimelineEventResult> results,
        int consumedProtection,
        Optional<String> firstLethalEventId,
        boolean survived,
        Set<String> acceptedEventIds,
        Set<String> processedEventIds
    ) {
    }
}
