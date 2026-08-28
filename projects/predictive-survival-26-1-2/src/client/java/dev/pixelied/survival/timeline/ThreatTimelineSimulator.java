package dev.pixelied.survival.timeline;

import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.damage.DamageResult;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.damage.DamageStage;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class ThreatTimelineSimulator {
    private static final int MAX_PERMUTATION_GROUP = 6;
    private static final int MAX_SCHEDULE_EVALUATIONS = 4096;
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
        return simulateInternal(start, timeline, List.of());
    }

    public TimelineResult simulate(PlayerSnapshot start, CausalThreatTimeline timeline) {
        java.util.Objects.requireNonNull(timeline, "timeline");
        return simulate(start, deterministicSourceRemovalProjection(timeline));
    }

    private static ThreatTimeline deterministicSourceRemovalProjection(CausalThreatTimeline causal) {
        List<ThreatEvent> ordered = new ArrayList<>(causal.timeline().events());
        ordered.sort(BASE_ORDER);
        List<ThreatEvent> remaining = new ArrayList<>(ordered.size());
        Set<String> removedSources = new HashSet<>();

        for (ThreatEvent event : ordered) {
            if (removedSources.contains(causal.sourceId(event))) continue;
            remaining.add(event);

            // A transition attached to an accepted-damage prerequisite cannot be proven to occur in
            // this deterministic pre-pass. Preserve the later source until branch-local simulation
            // can establish that prerequisite.
            if (event.requiresAcceptedEventId().isPresent()) continue;
            for (ThreatTransition transition : causal.transitionsAfter(event.id())) {
                if (transition instanceof ThreatTransition.RemoveSource remove) {
                    if (removalGuaranteedBeforeTarget(causal, event, remove.sourceId())) {
                        removedSources.add(remove.sourceId());
                    }
                } else {
                    throw new IllegalArgumentException(
                        "causal transition requires branch-local simulation: " + transition.getClass().getSimpleName()
                    );
                }
            }
        }
        return new ThreatTimeline(remaining);
    }

    private static boolean removalGuaranteedBeforeTarget(
        CausalThreatTimeline causal,
        ThreatEvent trigger,
        String targetSourceId
    ) {
        for (ThreatEvent target : causal.timeline().events()) {
            if (!causal.sourceId(target).equals(targetSourceId)) continue;
            if (trigger.impact().latest() >= target.impact().earliest()) return false;
        }
        return true;
    }

    public TimelineResult simulateWithActivation(
        PlayerSnapshot start,
        ThreatTimeline timeline,
        long activationTick,
        UnaryOperator<PlayerSnapshot> activation
    ) {
        return simulateWithActivations(
            start,
            timeline,
            List.of(new TimedActivation(activationTick, activation))
        );
    }

    public TimelineResult simulateWithActivations(
        PlayerSnapshot start,
        ThreatTimeline timeline,
        List<TimedActivation> activations
    ) {
        java.util.Objects.requireNonNull(activations, "activations");
        List<TimedActivation> sortedActivations = new ArrayList<>(activations.size());
        for (TimedActivation activation : activations) {
            sortedActivations.add(java.util.Objects.requireNonNull(activation, "activation"));
        }
        sortedActivations.sort(Comparator.comparingLong(TimedActivation::tick));
        return simulateInternal(start, timeline, List.copyOf(sortedActivations));
    }

    private TimelineResult simulateInternal(
        PlayerSnapshot start,
        ThreatTimeline timeline,
        List<TimedActivation> activations
    ) {
        java.util.Objects.requireNonNull(start, "start");
        java.util.Objects.requireNonNull(timeline, "timeline");
        List<ThreatEvent> sorted = conservativeBeforeActivations(timeline.events(), activations);
        sorted.sort(BASE_ORDER);
        Set<String> timelineEventIds = new HashSet<>();
        for (ThreatEvent event : sorted) {
            if (!timelineEventIds.add(event.id())) throw new IllegalArgumentException("duplicate threat event id: " + event.id());
        }

        PlayerSnapshot working = start;
        long previousTick = 0;
        List<TimelineEventResult> allResults = new ArrayList<>();
        int consumed = 0;
        Optional<String> firstLethal = Optional.empty();
        boolean survivalGuaranteed = true;
        Set<String> acceptedEventIds = new HashSet<>();
        Set<String> processedEventIds = new HashSet<>();
        int activationIndex = 0;

        for (List<ThreatEvent> group : overlapGroups(sorted)) {
            long groupEarliest = group.getFirst().impact().earliest();
            while (activationIndex < activations.size() && activations.get(activationIndex).tick() <= groupEarliest) {
                TimedActivation activation = activations.get(activationIndex++);
                if (activation.tick() < previousTick) {
                    throw new IllegalStateException("activation ordering crossed an already-simulated threat schedule");
                }
                working = agePlayerState(working, activation.tick() - previousTick);
                previousTick = activation.tick();
                working = activation.activation().apply(working);
            }

            GroupOutcome outcome = worstGroupOutcome(working, previousTick, group, acceptedEventIds, processedEventIds, timelineEventIds);
            working = outcome.player();
            previousTick = outcome.lastTick();
            acceptedEventIds = new HashSet<>(outcome.acceptedEventIds());
            processedEventIds = new HashSet<>(outcome.processedEventIds());
            allResults.addAll(outcome.results());
            consumed += outcome.consumedProtection();
            if (firstLethal.isEmpty() && outcome.firstLethalEventId().isPresent()) firstLethal = outcome.firstLethalEventId();
            if (!outcome.survived()) {
                survivalGuaranteed = false;
                break;
            }
        }

        while (activationIndex < activations.size() && survivalGuaranteed) {
            TimedActivation activation = activations.get(activationIndex++);
            if (activation.tick() < previousTick) {
                throw new IllegalStateException("activation ordering crossed an already-simulated threat schedule");
            }
            working = agePlayerState(working, activation.tick() - previousTick);
            previousTick = activation.tick();
            working = activation.activation().apply(working);
        }

        return new TimelineResult(allResults, working.health(), working.absorption(),
            survivalGuaranteed && working.health() > 0f && firstLethal.isEmpty(), consumed, firstLethal);
    }

    private static List<ThreatEvent> conservativeBeforeActivations(
        List<ThreatEvent> events,
        List<TimedActivation> activations
    ) {
        List<ThreatEvent> conservative = new ArrayList<>(events.size());
        for (ThreatEvent event : events) {
            long latest = event.impact().latest();
            for (TimedActivation activation : activations) {
                if (event.impact().earliest() < activation.tick() && latest >= activation.tick()) {
                    latest = activation.tick() - 1L;
                    break;
                }
            }
            conservative.add(latest == event.impact().latest()
                ? event
                : withImpact(event, new dev.pixelied.survival.core.TickWindow(event.impact().earliest(), latest)));
        }
        return conservative;
    }

    public record TimedActivation(long tick, UnaryOperator<PlayerSnapshot> activation) {
        public TimedActivation {
            if (tick < 0L) throw new IllegalArgumentException("activation tick must be non-negative");
            activation = java.util.Objects.requireNonNull(activation, "activation");
        }
    }

    private static ThreatEvent withImpact(ThreatEvent event, dev.pixelied.survival.core.TickWindow impact) {
        return new ThreatEvent(event.id(), event.kind(), impact, event.damage(), event.confidence(), event.sourcePosition(),
            event.impactPosition(), event.avoidable(), event.blockable(), event.relocatable(),
            event.canDisableBlocking(), event.requiresAcceptedEventId());
    }

    private GroupOutcome worstGroupOutcome(
        PlayerSnapshot start,
        long previousTick,
        List<ThreatEvent> group,
        Set<String> acceptedBefore,
        Set<String> processedBefore,
        Set<String> timelineEventIds
    ) {
        ScheduleBudget budget = new ScheduleBudget(MAX_SCHEDULE_EVALUATIONS);
        if (group.size() == 1) {
            GroupOutcome outcome = worstOrderOutcome(start, previousTick, group, acceptedBefore, processedBefore, timelineEventIds, budget);
            if (outcome == null) throw new IllegalArgumentException("No feasible ordering for dependent threat group");
            return budget.truncated() && outcome.survived() ? failClosed(outcome) : outcome;
        }

        if (group.size() > MAX_PERMUTATION_GROUP) {
            List<ThreatEvent> fallback = new ArrayList<>(group);
            fallback.sort(CONSERVATIVE_FALLBACK_ORDER);
            GroupOutcome damageOrdered = worstOrderOutcome(start, previousTick, fallback, acceptedBefore, processedBefore, timelineEventIds, budget);
            if (damageOrdered != null && !damageOrdered.survived()) return damageOrdered;

            fallback.sort(BASE_ORDER);
            GroupOutcome baseOrdered = budget.truncated() ? null
                : worstOrderOutcome(start, previousTick, fallback, acceptedBefore, processedBefore, timelineEventIds, budget);
            if (baseOrdered != null && !baseOrdered.survived()) return baseOrdered;
            if (damageOrdered == null && baseOrdered == null) throw new IllegalArgumentException("No feasible ordering for dependent threat group");

            GroupOutcome modeled = damageOrdered == null ? baseOrdered
                : baseOrdered == null || isWorse(damageOrdered, baseOrdered) ? damageOrdered : baseOrdered;
            return failClosed(modeled);
        }

        List<List<ThreatEvent>> permutations = new ArrayList<>();
        permute(new ArrayList<>(group), 0, permutations);
        GroupOutcome worst = null;
        for (List<ThreatEvent> permutation : permutations) {
            GroupOutcome candidate = worstOrderOutcome(start, previousTick, permutation, acceptedBefore, processedBefore, timelineEventIds, budget);
            if (candidate != null && !candidate.survived()) return candidate;
            if (candidate != null && (worst == null || isWorse(candidate, worst))) worst = candidate;
            if (budget.truncated()) break;
        }
        if (worst == null) throw new IllegalArgumentException("No feasible ordering for overlapping threat group");
        return budget.truncated() ? failClosed(worst) : worst;
    }

    private GroupOutcome worstOrderOutcome(
        PlayerSnapshot start,
        long previousTick,
        List<ThreatEvent> order,
        Set<String> acceptedBefore,
        Set<String> processedBefore,
        Set<String> timelineEventIds,
        ScheduleBudget budget
    ) {
        long[] schedule = new long[order.size()];
        GroupOutcome[] worst = new GroupOutcome[1];
        enumerateSchedules(start, previousTick, order, schedule, 0, previousTick,
            acceptedBefore, processedBefore, timelineEventIds, budget, worst);
        return worst[0];
    }

    private boolean enumerateSchedules(
        PlayerSnapshot start,
        long previousTick,
        List<ThreatEvent> order,
        long[] schedule,
        int index,
        long minimumTick,
        Set<String> acceptedBefore,
        Set<String> processedBefore,
        Set<String> timelineEventIds,
        ScheduleBudget budget,
        GroupOutcome[] worst
    ) {
        if (budget.truncated()) return true;
        if (index == order.size()) {
            if (!budget.tryEvaluate()) return true;
            GroupOutcome candidate = simulateOrder(start, previousTick, order, schedule,
                acceptedBefore, processedBefore, timelineEventIds);
            if (candidate == null) return false;
            if (worst[0] == null || isWorse(candidate, worst[0])) worst[0] = candidate;
            return !candidate.survived();
        }
        ThreatEvent event = order.get(index);
        long lower = Math.max(event.impact().earliest(), minimumTick);
        long upper = event.impact().latest();
        if (upper < lower) return false;
        for (long tick = lower; ; tick++) {
            schedule[index] = tick;
            if (enumerateSchedules(start, previousTick, order, schedule, index + 1, tick,
                acceptedBefore, processedBefore, timelineEventIds, budget, worst)) return true;
            if (tick == upper) break;
        }
        return false;
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
            working = agePlayerState(working, eventTick - lastTick);
            lastTick = eventTick;

            Optional<String> requiredId = event.requiresAcceptedEventId();
            if (requiredId.isPresent() && !acceptedEventIds.contains(requiredId.get())) {
                String prerequisite = requiredId.get();
                if (!processedEventIds.contains(prerequisite)) {
                    if (groupEventIds.contains(prerequisite) || timelineEventIds.contains(prerequisite)) return null;
                    throw new IllegalArgumentException("threat event " + event.id() + " requires unknown event " + prerequisite);
                }
                processedEventIds.add(event.id());
                continue;
            }

            DamageResult damageResult = damageSimulator.simulate(working, event.damage());
            damageResult = applyBlockingDisable(damageResult, event.damage());
            float finalDamage = damageResult.trace().has(DamageStage.HEALTH_DAMAGE)
                ? damageResult.trace().after(DamageStage.HEALTH_DAMAGE) : 0f;
            results.add(new TimelineEventResult(event, event.damage().rawDamage().max(), finalDamage, damageResult));

            processedEventIds.add(event.id());
            if (!damageResult.rejected()) acceptedEventIds.add(event.id());
            if (damageResult.deathProtectionConsumed()) consumed++;
            working = damageResult.after();

            if (damageResult.postStateUncertain()) {
                survivalGuaranteed = false;
                break;
            }
            if (working.health() <= 0f && !damageResult.deathProtectionConsumed()) {
                firstLethal = Optional.of(event.id());
                break;
            }
        }

        return new GroupOutcome(working, lastTick, results, consumed, firstLethal,
            survivalGuaranteed && working.health() > 0f && firstLethal.isEmpty(),
            Set.copyOf(acceptedEventIds), Set.copyOf(processedEventIds));
    }

    private static DamageResult applyBlockingDisable(DamageResult result, dev.pixelied.survival.damage.DamageSourceSnapshot source) {
        if (source.blockingDisableSeconds() <= 0f || source.has(dev.pixelied.survival.damage.DamageFlag.IS_PROJECTILE)) return result;
        if (!result.trace().has(DamageStage.BLOCKING)) return result;
        float blocked = result.trace().before(DamageStage.BLOCKING) - result.trace().after(DamageStage.BLOCKING);
        if (!(blocked > 0f)) return result;
        PlayerSnapshot after = result.after();
        dev.pixelied.survival.damage.BlockingSnapshot blocking = after.blocking();
        int ticks = blocking.profile().map(profile -> profile.disableTicks(source.blockingDisableSeconds())).orElseGet(() -> {
            double raw = Math.round(source.blockingDisableSeconds() * 20f);
            return raw >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) raw);
        });
        if (ticks <= 0) return result;
        PlayerSnapshot disabled = new PlayerSnapshot(after.health(), after.absorption(), after.playerInvulnerable(),
            after.abilityInvulnerable(), after.deadOrDying(), after.difficulty(), after.mitigation(), after.statusEffects(),
            blocking.disableForTicks(ticks), after.hurtState(), after.deathProtection(), after.boundingBox(), after.position(),
            after.velocity(), after.equipmentItemKeys(), after.stateProperties());
        return new DamageResult(disabled, result.trace(), result.rejected(), result.deathProtectionConsumed(), result.postStateUncertain());
    }

    private static PlayerSnapshot agePlayerState(PlayerSnapshot player, long elapsedTicks) {
        if (elapsedTicks <= 0) return player;
        HurtState hurt = player.hurtState();
        int elapsed = elapsedTicks > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsedTicks;
        HurtState aged = new HurtState(hurt.lastHurt(), Math.max(0, hurt.invulnerableTime() - elapsed), hurt.confidence());

        StatusEffectsSnapshot effects = player.statusEffects();
        float health = player.health();
        EffectInstanceSnapshot regeneration = effects.effects().get("minecraft:regeneration");
        if (regeneration != null && health > 0f) {
            float maxHealth = observedMaxHealth(player);
            if (maxHealth > health) {
                long applications = regenerationApplications(regeneration, Math.max(0, elapsed - 1));
                if (applications > 0L) health = (float) Math.min((double) maxHealth, (double) health + applications);
            }
        }

        float absorption = player.absorption();
        EffectInstanceSnapshot absorptionEffect = effects.effects().get("minecraft:absorption");
        if (absorptionEffect != null && !absorptionEffect.infiniteDuration() && absorptionEffect.durationTicks() <= elapsed) {
            float expiringHearts = 4f * (absorptionEffect.amplifier() + 1);
            absorption = Math.max(0f, absorption - expiringHearts);
        }

        Map<String, String> stateProperties = player.stateProperties();
        EffectInstanceSnapshot healthBoost = effects.effects().get("minecraft:health_boost");
        if (healthBoost != null && !healthBoost.infiniteDuration() && healthBoost.durationTicks() <= elapsed) {
            float expiringBonus = 4f * (healthBoost.amplifier() + 1);
            float maxAfterExpiry = Math.max(1f, observedMaxHealth(player) - expiringBonus);
            health = Math.min(health, maxAfterExpiry);
            LinkedHashMap<String, String> nextState = new LinkedHashMap<>(stateProperties);
            nextState.put("max_health", Float.toString(maxAfterExpiry));
            stateProperties = nextState;
        }
        StatusEffectsSnapshot agedEffects = effects.age(elapsed);
        return new PlayerSnapshot(health, absorption, player.playerInvulnerable(), player.abilityInvulnerable(),
            player.deadOrDying(), player.difficulty(), player.mitigation(), agedEffects, player.blocking().age(elapsed),
            aged, player.deathProtection(), player.boundingBox(), player.position(), player.velocity(),
            player.equipmentItemKeys(), stateProperties);
    }

    private static long regenerationApplications(EffectInstanceSnapshot regeneration, int elapsedTicks) {
        if (elapsedTicks <= 0) return 0L;
        int interval = 50 >> regeneration.amplifier();
        if (regeneration.infiniteDuration()) return interval > 0 ? elapsedTicks / interval : elapsedTicks;
        int duration = regeneration.durationTicks();
        int activeTicks = Math.min(elapsedTicks, duration);
        if (activeTicks <= 0) return 0L;
        if (interval <= 0) return activeTicks;
        return duration / interval - (duration - activeTicks) / interval;
    }

    private static float observedMaxHealth(PlayerSnapshot player) {
        String raw = player.state("max_health");
        if (raw == null) return player.health();
        try {
            float parsed = Float.parseFloat(raw);
            return Float.isFinite(parsed) && parsed > 0f ? Math.max(player.health(), parsed) : player.health();
        } catch (NumberFormatException ignored) {
            return player.health();
        }
    }

    private static GroupOutcome failClosed(GroupOutcome modeled) {
        return new GroupOutcome(modeled.player(), modeled.lastTick(), modeled.results(), modeled.consumedProtection(),
            modeled.firstLethalEventId(), false, modeled.acceptedEventIds(), modeled.processedEventIds());
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
        if (candidate.survived() != currentWorst.survived()) return !candidate.survived();
        float candidateEffective = candidate.player().health() + candidate.player().absorption();
        float currentEffective = currentWorst.player().health() + currentWorst.player().absorption();
        int effectiveComparison = Float.compare(candidateEffective, currentEffective);
        if (effectiveComparison != 0) return effectiveComparison < 0;
        if (candidate.consumedProtection() != currentWorst.consumedProtection()) return candidate.consumedProtection() > currentWorst.consumedProtection();
        int count = Math.min(candidate.results().size(), currentWorst.results().size());
        for (int i = 0; i < count; i++) {
            float candidateRaw = candidate.results().get(i).preMitigationRaw();
            float currentRaw = currentWorst.results().get(i).preMitigationRaw();
            int rawComparison = Float.compare(candidateRaw, currentRaw);
            if (rawComparison != 0) return rawComparison > 0;
            int idComparison = candidate.results().get(i).event().id().compareTo(currentWorst.results().get(i).event().id());
            if (idComparison != 0) return idComparison < 0;
        }
        return candidate.results().size() > currentWorst.results().size();
    }

    private static final class ScheduleBudget {
        private final int maxEvaluations;
        private int evaluations;
        private boolean truncated;

        private ScheduleBudget(int maxEvaluations) {
            this.maxEvaluations = maxEvaluations;
        }

        private boolean tryEvaluate() {
            if (evaluations >= maxEvaluations) {
                truncated = true;
                return false;
            }
            evaluations++;
            return true;
        }

        private boolean truncated() {
            return truncated;
        }
    }

    private record GroupOutcome(PlayerSnapshot player, long lastTick, List<TimelineEventResult> results,
        int consumedProtection, Optional<String> firstLethalEventId, boolean survived,
        Set<String> acceptedEventIds, Set<String> processedEventIds) {
    }
}
