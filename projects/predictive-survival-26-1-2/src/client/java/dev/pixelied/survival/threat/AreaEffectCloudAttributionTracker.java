package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AreaEffectCloudAttributionTracker {
    private static final String CLOUD_TYPE = "minecraft:area_effect_cloud";
    private static final String DRAGON_BREATH_MARKER = ":dragon_breath:0";
    private static final String LINGERING_POTION_MARKER = ":lingering_cloud:0";
    private static final String LINGERING_STATUS_MARKER = ":lingering_status:";
    private static final String LINGERING_STACKED_STATUS_MARKER = ":lingering_stacked_status:";
    private static final double MATCH_DISTANCE_SQUARED = 4d;
    private static final long NETWORK_GRACE_TICKS = 2L;
    private static final int DEFAULT_REAPPLICATION_DELAY_TICKS = 20;

    private final Map<String, PendingAttribution> pendingByProjectile = new HashMap<>();
    private final Map<String, CloudAttributions> activeByCloud = new HashMap<>();

    public void reset() {
        pendingByProjectile.clear();
        activeByCloud.clear();
    }

    public void observePredictedThreats(long clientTick, List<ThreatEvent> threats) {
        if (threats == null) throw new NullPointerException("threats");
        expirePending(clientTick);

        for (ThreatEvent threat : threats) {
            if (threat == null || threat.impactPosition().isEmpty()) continue;

            String projectileKey;
            CloudAttribution attribution;
            if (threat.id().endsWith(DRAGON_BREATH_MARKER)) {
                projectileKey = threat.id().substring(0, threat.id().length() - DRAGON_BREATH_MARKER.length());
                attribution = CloudAttribution.dragonBreath(threat);
            } else if (threat.id().endsWith(LINGERING_POTION_MARKER)) {
                projectileKey = threat.id().substring(0, threat.id().length() - LINGERING_POTION_MARKER.length());
                attribution = CloudAttribution.lingeringPotion(threat);
                if (attribution.rawDamage() <= 0f) continue;
            } else {
                StatusMarker statusMarker = statusMarker(threat.id());
                if (statusMarker == null || !threat.id().endsWith(":0")) continue;
                projectileKey = threat.id().substring(0, statusMarker.markerIndex());
                String statusKey = statusKey(threat.id(), statusMarker.statusStart());
                if (statusKey.isEmpty()) continue;
                attribution = CloudAttribution.lingeringStatus(
                    clientTick,
                    statusKey,
                    statusMarker.attributionKey(),
                    threat
                );
                if (attribution.rawDamage() <= 0f) continue;
            }

            long expiresAt = saturatingAdd(
                clientTick,
                saturatingAdd(Math.max(0L, threat.impact().latest()), NETWORK_GRACE_TICKS)
            );
            PendingAttribution candidate = PendingAttribution.single(
                threat.impactPosition().get(),
                expiresAt,
                attribution
            );
            pendingByProjectile.merge(projectileKey, candidate, PendingAttribution::merge);
        }
    }

    public WorldSnapshot annotate(long clientTick, WorldSnapshot world) {
        if (world == null) throw new NullPointerException("world");
        expirePending(clientTick);

        Set<String> visibleCloudIds = new HashSet<>();
        for (WorldSnapshot.EntitySnapshot entity : world.entities()) {
            if (CLOUD_TYPE.equals(entity.typeKey())) visibleCloudIds.add(entity.id());
        }
        activeByCloud.keySet().removeIf(id -> !visibleCloudIds.contains(id));

        Set<String> consumedPending = new HashSet<>();
        for (WorldSnapshot.EntitySnapshot entity : world.entities()) {
            if (!CLOUD_TYPE.equals(entity.typeKey()) || activeByCloud.containsKey(entity.id())) continue;

            String bestKey = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (Map.Entry<String, PendingAttribution> entry : pendingByProjectile.entrySet()) {
                if (consumedPending.contains(entry.getKey())) continue;
                double distance = distanceSquared(entity.position(), entry.getValue().origin());
                if (distance <= MATCH_DISTANCE_SQUARED && distance < bestDistance) {
                    bestDistance = distance;
                    bestKey = entry.getKey();
                }
            }
            if (bestKey != null) {
                PendingAttribution matched = pendingByProjectile.get(bestKey);
                activeByCloud.put(entity.id(), new CloudAttributions(List.copyOf(matched.hazards().values())));
                consumedPending.add(bestKey);
            }
        }
        for (String key : consumedPending) pendingByProjectile.remove(key);

        List<WorldSnapshot.EntitySnapshot> annotated = new ArrayList<>(world.entities().size());
        for (WorldSnapshot.EntitySnapshot entity : world.entities()) {
            CloudAttributions attributions = activeByCloud.get(entity.id());
            if (attributions == null || attributions.hazards().isEmpty() || !CLOUD_TYPE.equals(entity.typeKey())) {
                annotated.add(entity);
                continue;
            }

            Map<String, String> properties = new LinkedHashMap<>(entity.properties());
            properties.put("cloud_hazard_count", Integer.toString(attributions.hazards().size()));
            for (int i = 0; i < attributions.hazards().size(); i++) {
                writeHazardProperties(properties, "cloud_hazard_" + i + "_", attributions.hazards().get(i), clientTick);
            }

            writeHazardProperties(properties, "cloud_", attributions.hazards().getFirst(), clientTick);
            annotated.add(new WorldSnapshot.EntitySnapshot(
                entity.id(),
                entity.typeKey(),
                entity.position(),
                entity.velocity(),
                entity.boundingBox(),
                properties
            ));
        }
        return new WorldSnapshot(annotated, world.blocks());
    }

    private static void writeHazardProperties(
        Map<String, String> properties,
        String prefix,
        CloudAttribution attribution,
        long clientTick
    ) {
        properties.put(prefix + "instant_damage", Float.toString(attribution.rawDamage()));
        properties.put(prefix + "source_key", attribution.sourceKey());
        properties.put(prefix + "scales_with_difficulty", Boolean.toString(attribution.scalesWithDifficulty()));
        properties.put(prefix + "reapplication_delay_ticks", Integer.toString(attribution.reapplicationDelayTicks()));
        properties.put(prefix + "attribution", attribution.attributionKey());
        properties.put(
            prefix + "application_health_threshold_exclusive",
            Float.toString(attribution.applicationHealthThresholdExclusive())
        );
        if (attribution.firstDamageEarliestTick() >= 0L) {
            long earliest = Math.max(0L, attribution.firstDamageEarliestTick() - clientTick);
            long latest = Math.max(earliest, attribution.firstDamageLatestTick() - clientTick);
            properties.put(prefix + "first_damage_earliest_ticks", Long.toString(earliest));
            properties.put(prefix + "first_damage_latest_ticks", Long.toString(latest));
        }
    }

    private void expirePending(long clientTick) {
        pendingByProjectile.entrySet().removeIf(entry -> entry.getValue().expiresAt() < clientTick);
    }

    private static StatusMarker statusMarker(String threatId) {
        int stacked = threatId.indexOf(LINGERING_STACKED_STATUS_MARKER);
        if (stacked >= 0) {
            return new StatusMarker(
                stacked,
                stacked + LINGERING_STACKED_STATUS_MARKER.length(),
                "lingering_stacked_status"
            );
        }
        int regular = threatId.indexOf(LINGERING_STATUS_MARKER);
        if (regular >= 0) {
            return new StatusMarker(
                regular,
                regular + LINGERING_STATUS_MARKER.length(),
                "lingering_status"
            );
        }
        return null;
    }

    private static String statusKey(String threatId, int start) {
        int end = threatId.lastIndexOf(':');
        if (end <= start) return "";
        return threatId.substring(start, end);
    }

    private static double distanceSquared(Vec3Snapshot first, Vec3Snapshot second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
    }

    private record StatusMarker(int markerIndex, int statusStart, String attributionKey) {
    }

    private record PendingAttribution(
        Vec3Snapshot origin,
        long expiresAt,
        LinkedHashMap<String, CloudAttribution> hazards
    ) {
        private static PendingAttribution single(
            Vec3Snapshot origin,
            long expiresAt,
            CloudAttribution attribution
        ) {
            LinkedHashMap<String, CloudAttribution> hazards = new LinkedHashMap<>();
            hazards.put(attribution.hazardKey(), attribution);
            return new PendingAttribution(origin, expiresAt, hazards);
        }

        private static PendingAttribution merge(PendingAttribution previous, PendingAttribution next) {
            LinkedHashMap<String, CloudAttribution> merged = new LinkedHashMap<>(previous.hazards());
            for (Map.Entry<String, CloudAttribution> entry : next.hazards().entrySet()) {
                merged.put(entry.getKey(), entry.getValue());
            }
            return new PendingAttribution(
                next.expiresAt() >= previous.expiresAt() ? next.origin() : previous.origin(),
                Math.max(previous.expiresAt(), next.expiresAt()),
                merged
            );
        }
    }

    private record CloudAttributions(List<CloudAttribution> hazards) {
    }

    private record CloudAttribution(
        String hazardKey,
        float rawDamage,
        String sourceKey,
        boolean scalesWithDifficulty,
        int reapplicationDelayTicks,
        String attributionKey,
        float applicationHealthThresholdExclusive,
        long firstDamageEarliestTick,
        long firstDamageLatestTick
    ) {
        private static CloudAttribution dragonBreath(ThreatEvent threat) {
            return new CloudAttribution(
                "dragon_breath",
                threat.damage().rawDamage().max(),
                threat.damage().sourceKey(),
                threat.damage().scalesWithDifficulty(),
                DEFAULT_REAPPLICATION_DELAY_TICKS,
                "dragon_breath",
                threat.damage().applicationHealthThresholdExclusive(),
                -1L,
                -1L
            );
        }

        private static CloudAttribution lingeringPotion(ThreatEvent threat) {
            return new CloudAttribution(
                "lingering_potion",
                threat.damage().rawDamage().max(),
                threat.damage().sourceKey(),
                threat.damage().scalesWithDifficulty(),
                DEFAULT_REAPPLICATION_DELAY_TICKS,
                "lingering_potion",
                threat.damage().applicationHealthThresholdExclusive(),
                -1L,
                -1L
            );
        }

        private static CloudAttribution lingeringStatus(
            long clientTick,
            String statusKey,
            String attributionKey,
            ThreatEvent threat
        ) {
            return new CloudAttribution(
                attributionKey + ":" + statusKey,
                threat.damage().rawDamage().max(),
                threat.damage().sourceKey(),
                threat.damage().scalesWithDifficulty(),
                DEFAULT_REAPPLICATION_DELAY_TICKS,
                attributionKey,
                threat.damage().applicationHealthThresholdExclusive(),
                saturatingAdd(clientTick, Math.max(0L, threat.impact().earliest())),
                saturatingAdd(clientTick, Math.max(0L, threat.impact().latest()))
            );
        }
    }
}
