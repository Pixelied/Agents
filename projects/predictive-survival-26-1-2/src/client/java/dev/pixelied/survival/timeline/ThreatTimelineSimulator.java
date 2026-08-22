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
        return simulateInternal(start, timeline, -1L, null);
    }

    /**
     * Simulates a state transition that becomes authoritative at a conservative relative tick.
     * Any threat window that begins before that tick is clipped to the pre-activation side so an
     * ambiguous hit is never allowed to benefit from protection that might not have existed yet.
     */
    public TimelineResult simulateWithActivation(
        PlayerSnapshot start,
        ThreatTimeline timeline,
        long activationTick,
        UnaryOperator<PlayerSnapshot> activation
    ) {
        if (activationTick < 0L) throw new IllegalArgumentException("activationTick must be non-negative");
        return simulateInternal(
            start,
            timeline,
            activationTick,
            java.util.Objects.requireNonNull(activation, "activation")
        );
    }

    private TimelineResult simulateInternal(
        PlayerSnapshot start,
        ThreatTimeline timeline,
        long activationTick,
        UnaryOperator<PlayerSnapshot> activation
    ) {
        java.util.Objects.requireNonNull(start, "start");
        java.util.Objects.requireNonNull(timeline, "timeline");
        List<ThreatEvent> sorted = new ArrayList<>(timeline.events());
        if (activation != null && activationTick > 0L) {
            List<ThreatEvent> conservative = new ArrayList<>(sorted.size());
            for (ThreatEvent event : sorted) {
                if (event.impact().earliest() < activationTick && event.impact().latest() >= activationTick) {
                    conservative.add(withImpact(
                        event,
                        new dev.pixelied.survival.core.TickWindow(
                            event.impact().earliest(),
                            activationTick - 1L
                        )
                    ));
                } else {
                    conservative.add(event);
                }
            }
            sorted = conservative;
        }
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
        boolean activated = activation == null;

        if (!activated && activationTick == 0L) {
            working = activation.apply(working);
            activated = true;
        }

        for (List<ThreatEvent> group : overlapGroups(sorted)) {
            if (!activated && group.getFirst().impact().earliest() >= activationTick) {
                working = agePlayerState(working, activationTick - previousTick);
                previousTick = activationTick;
                working = activation.apply(working);
                activated = true;
            }
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

        if (!activated && survivalGuaranteed) {
            working = agePlayerState(working, activationTick - previousTick);
            working = activation.apply(working);
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

    private static ThreatEvent withImpact(
        ThreatEvent event,
        dev.pixelied.survival.core.TickWindow impact
    ) {
        return new ThreatEvent(
            event.id(), event.kind(), impact, event.damage(), event.confidence(), event.sourcePosition(),
            event.impactPosition(), event.avoidable(), event.blockable(), event.relocatable(),
            event.canDisableBlocking(), event.requiresAcceptedEventId()
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
            GroupOutcome damageOrdered = schedule == null ? null : simulateOrder(
                start,
                previousTick,
                fallback,
                schedule,
                acceptedBefore,
                processedBefore,
                timelineEventIds
            );
            if (damageOrdered != null && !damageOrdered.survived()) return damageOrdered;

            fallback.sort(BASE_ORDER);
            schedule = schedule(fallback, previousTick);
            GroupOutcome baseOrdered = schedule == null ? null : simulateOrder(
                start,
                previousTick,
                fallback,
                schedule,
                acceptedBefore,
                processedBefore,
                timelineEventIds
            );
            if (baseOrdered != null && !baseOrdered.survived()) return baseOrdered;
            if (damageOrdered == null && baseOrdered == null) {
                throw new IllegalArgumentException("No feasible ordering for dependent threat group");
            }

            // Once the overlap exceeds the exhaustive permutation cap, neither heuristic ordering
            // is a proof that every legal ordering survives. Returning a positive guarantee here
            // would be unsafe because a low-damage state-changing hit (for example shield disable)
            // can make a later high-damage hit lethal. Preserve the worse modeled state for
            // diagnostics, but deliberately fail the guarantee closed.
            GroupOutcome modeled = damageOrdered == null
                ? baseOrdered
                : baseOrdered == null || isWorse(damageOrdered, baseOrdered) ? damageOrdered : baseOrdered;
            return failClosed(modeled);
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
            working = agePlayerState(working, eventTick - lastTick);
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

    private static PlayerSnapshot agePlayerState(PlayerSnapshot player, long elapsedTicks) {
        if (elapsedTicks <= 0) return player;
        HurtState hurt = player.hurtState();
        int elapsed = elapsedTicks > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsedTicks;
        HurtState aged = new HurtState(
            hurt.lastHurt(),
            Math.max(0, hurt.invulnerableTime() - elapsed),
            hurt.confidence()
        );

        StatusEffectsSnapshot effects = player.statusEffects();
        float health = player.health();
        EffectInstanceSnapshot regeneration = effects.effects().get("minecraft:regeneration");
        if (regeneration != null && health > 0f) {
            float maxHealth = observedMaxHealth(player);
            if (maxHealth > health) {
                long applications = regenerationApplications(regeneration, elapsed);
                if (applications > 0L) {
                    health = (float) Math.min((double) maxHealth, (double) health + applications);
                }
            }
        }

        float absorption = player.absorption();
        EffectInstanceSnapshot absorptionEffect = effects.effects().get("minecraft:absorption");
        if (absorptionEffect != null
            && !absorptionEffect.infiniteDuration()
            && absorptionEffect.durationTicks() <= elapsed) {
            float expiringHearts = 4f * (absorptionEffect.amplifier() + 1);
            absorption = Math.max(0f, absorption - expiringHearts);
        }

        Map<String, String> stateProperties = player.stateProperties();
        EffectInstanceSnapshot healthBoost = effects.effects().get("minecraft:health_boost");
        if (healthBoost != null
            && !healthBoost.infiniteDuration()
            && healthBoost.durationTicks() <= elapsed) {
            float expiringBonus = 4f * (healthBoost.amplifier() + 1);
            float maxAfterExpiry = Math.max(1f, observedMaxHealth(player) - expiringBonus);
            health = Math.min(health, maxAfterExpiry);
            LinkedHashMap<String, String> nextState = new LinkedHashMap<>(stateProperties);
            nextState.put("max_health", Float.toString(maxAfterExpiry));
            stateProperties = nextState;
        }

        StatusEffectsSnapshot agedEffects = effects.age(elapsed);

        return new PlayerSnapshot(
            health, absorption, player.playerInvulnerable(), player.abilityInvulnerable(),
            player.deadOrDying(), player.difficulty(), player.mitigation(), agedEffects, player.blocking().age(elapsed),
            aged, player.deathProtection(), player.boundingBox(), player.position(), player.velocity(),
            player.equipmentItemKeys(), stateProperties
        );
    }

    private static long regenerationApplications(EffectInstanceSnapshot regeneration, int elapsedTicks) {
        if (elapsedTicks <= 0) return 0L;
        int interval = 50 >> regeneration.amplifier();
        if (regeneration.infiniteDuration()) {
            // Infinite effects use the entity tickCount as their phase. Without snapshotting that
            // phase, count only the applications guaranteed in every possible alignment.
            return interval > 0 ? elapsedTicks / interval : elapsedTicks;
        }

        int duration = regeneration.durationTicks();
        int activeTicks = Math.min(elapsedTicks, duration);
        if (activeTicks <= 0) return 0L;
        if (interval <= 0) return activeTicks;

        // MobEffectInstance.tickServer tests the current remaining duration first and decrements
        // it afterward. Over activeTicks, the tested values are duration down to
        // duration-activeTicks+1, inclusive.
        return duration / interval - (duration - activeTicks) / interval;
    }

    private static float observedMaxHealth(PlayerSnapshot player) {
        String raw = player.state("max_health");
        if (raw == null) return player.health();
        try {
            float parsed = Float.parseFloat(raw);
            return Float.isFinite(parsed) && parsed > 0f
                ? Math.max(player.health(), parsed)
                : player.health();
        } catch (NumberFormatException ignored) {
            return player.health();
        }
    }

    private static GroupOutcome failClosed(GroupOutcome modeled) {
        return new GroupOutcome(
            modeled.player(), modeled.lastTick(), modeled.results(), modeled.consumedProtection(),
            modeled.firstLethalEventId(), false, modeled.acceptedEventIds(), modeled.processedEventIds()
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
