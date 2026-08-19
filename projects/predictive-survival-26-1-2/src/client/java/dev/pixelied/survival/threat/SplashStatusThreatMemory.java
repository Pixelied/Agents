package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Carries already-observed stacked splash status hazards across projectile removal.
 *
 * <p>Vanilla does not synchronize a MobEffectInstance's hidden-effect chain. Before a splash hits,
 * the thrown ItemStack is observable and {@link StackedPotionStatusPredictor} can model that chain.
 * After impact the projectile disappears and the client normally receives only the currently active
 * effect. Remembering the pre-impact absolute schedule prevents the hidden tail from becoming
 * false-safe while still allowing the live active-effect predictor to own the portion it can see.</p>
 */
public final class SplashStatusThreatMemory implements ThreatPredictor {
    private static final String PREFIX = "projectile:";
    private static final String MARKER = ":stacked_status:";
    private static final long APPLICATION_CONFIRMATION_GRACE_TICKS = 2L;

    private final Map<String, SourceMemory> bySource = new LinkedHashMap<>();

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");

        long clientTick = context.timing().clientTick();
        Set<String> visibleEntityIds = visibleEntityIds(context);
        List<ThreatEvent> replayed = new ArrayList<>();

        Iterator<Map.Entry<String, SourceMemory>> sources = bySource.entrySet().iterator();
        while (sources.hasNext()) {
            Map.Entry<String, SourceMemory> entry = sources.next();
            String sourceId = entry.getKey();
            SourceMemory memory = entry.getValue();

            memory.events.values().removeIf(event -> event.absoluteLatest() < clientTick);
            if (memory.events.isEmpty()) {
                sources.remove();
                continue;
            }

            if (visibleEntityIds.contains(sourceId)) {
                memory.missingSinceTick = -1L;
                memory.confirmedKinds.clear();
                continue;
            }
            if (memory.missingSinceTick < 0L) memory.missingSinceTick = clientTick;

            List<String> removeIds = new ArrayList<>();
            for (RememberedEvent remembered : memory.events.values()) {
                Optional<EffectInstanceSnapshot> active = context.player().statusEffects().effect(remembered.effectKey());
                boolean activeNow = active.isPresent();
                boolean confirmed = memory.confirmedKinds.contains(remembered.effectKey());
                if (activeNow) {
                    memory.confirmedKinds.add(remembered.effectKey());
                    confirmed = true;
                }

                if (confirmed && !activeNow) {
                    removeIds.add(remembered.event().id());
                    continue;
                }
                if (!confirmed
                    && !activeNow
                    && clientTick - memory.missingSinceTick > APPLICATION_CONFIRMATION_GRACE_TICKS) {
                    removeIds.add(remembered.event().id());
                    continue;
                }

                long absoluteEarliest = remembered.absoluteEarliest();
                long absoluteLatest = remembered.absoluteLatest();
                if (activeNow) {
                    EffectInstanceSnapshot effect = active.orElseThrow();
                    if (effect.infiniteDuration()) continue;
                    long visibleCoverageEnd = saturatingAdd(clientTick, effect.durationTicks());
                    if (absoluteLatest <= visibleCoverageEnd) continue;
                    absoluteEarliest = Math.max(absoluteEarliest, saturatingAdd(visibleCoverageEnd, 1L));
                }
                if (absoluteLatest < absoluteEarliest || absoluteLatest < clientTick) continue;

                long relativeEarliest = Math.max(0L, absoluteEarliest - clientTick);
                long relativeLatest = Math.max(relativeEarliest, absoluteLatest - clientTick);
                replayed.add(asAppliedStatusThreat(
                    remembered.event(),
                    new TickWindow(relativeEarliest, relativeLatest),
                    activeNow || confirmed
                ));
            }
            for (String id : removeIds) memory.events.remove(id);
            if (memory.events.isEmpty()) sources.remove();
        }

        return List.copyOf(replayed);
    }

    /**
     * Refreshes memory from the current authoritative prediction set after all predictors have run.
     * Visible sources replace their previous schedule rather than accumulating stale trajectory guesses.
     */
    public void observePredictedThreats(PredictionContext context, List<ThreatEvent> predicted) {
        if (context == null) throw new NullPointerException("context");
        if (predicted == null) throw new NullPointerException("predicted");

        long clientTick = context.timing().clientTick();
        Set<String> visibleEntityIds = visibleEntityIds(context);
        Map<String, List<RememberedEvent>> currentBySource = new LinkedHashMap<>();

        for (ThreatEvent event : predicted) {
            ParsedId parsed = parse(event.id());
            if (parsed == null || !visibleEntityIds.contains(parsed.sourceId())) continue;
            long absoluteEarliest = saturatingAdd(clientTick, event.impact().earliest());
            long absoluteLatest = saturatingAdd(clientTick, event.impact().latest());
            currentBySource.computeIfAbsent(parsed.sourceId(), ignored -> new ArrayList<>())
                .add(new RememberedEvent(event, parsed.effectKey(), absoluteEarliest, absoluteLatest));
        }

        for (String visibleId : visibleEntityIds) {
            if (bySource.containsKey(visibleId) && !currentBySource.containsKey(visibleId)) {
                bySource.remove(visibleId);
            }
        }
        for (Map.Entry<String, List<RememberedEvent>> entry : currentBySource.entrySet()) {
            SourceMemory memory = new SourceMemory();
            for (RememberedEvent event : entry.getValue()) memory.events.put(event.event().id(), event);
            bySource.put(entry.getKey(), memory);
        }
    }

    private static ThreatEvent asAppliedStatusThreat(ThreatEvent original, TickWindow impact, boolean confirmed) {
        DamageSourceSnapshot damage = original.damage();
        DamageSourceSnapshot appliedDamage = new DamageSourceSnapshot(
            damage.rawDamage(),
            damage.flags(),
            damage.scalesWithDifficulty(),
            damage.freezingMultiplier(),
            damage.piercingProjectile(),
            Optional.empty(),
            damage.sourceKey(),
            damage.applicationHealthThresholdExclusive()
        );
        Confidence confidence = confirmed
            ? original.confidence()
            : lessCertain(original.confidence(), Confidence.POTENTIAL);
        return new ThreatEvent(
            original.id(),
            ThreatKind.ENVIRONMENT,
            impact,
            appliedDamage,
            confidence,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            false
        );
    }

    private static Set<String> visibleEntityIds(PredictionContext context) {
        Set<String> ids = new HashSet<>();
        context.world().entities().forEach(entity -> ids.add(entity.id()));
        return ids;
    }

    private static ParsedId parse(String id) {
        if (id == null || !id.startsWith(PREFIX)) return null;
        int marker = id.indexOf(MARKER, PREFIX.length());
        if (marker < 0) return null;
        String sourceId = id.substring(PREFIX.length(), marker);
        if (sourceId.isEmpty()) return null;
        int kindStart = marker + MARKER.length();
        int kindEnd = id.indexOf(':', kindStart);
        if (kindEnd < 0) return null;
        String kind = id.substring(kindStart, kindEnd);
        String effectKey = switch (kind) {
            case "poison" -> "minecraft:poison";
            case "wither" -> "minecraft:wither";
            default -> null;
        };
        return effectKey == null ? null : new ParsedId(sourceId, effectKey);
    }

    private static Confidence lessCertain(Confidence first, Confidence second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        if (increment < 0L && value < Long.MIN_VALUE - increment) return Long.MIN_VALUE;
        return value + increment;
    }

    private static final class SourceMemory {
        private final Map<String, RememberedEvent> events = new LinkedHashMap<>();
        private final Set<String> confirmedKinds = new HashSet<>();
        private long missingSinceTick = -1L;
    }

    private record RememberedEvent(
        ThreatEvent event,
        String effectKey,
        long absoluteEarliest,
        long absoluteLatest
    ) {
    }

    private record ParsedId(String sourceId, String effectKey) {
    }
}
