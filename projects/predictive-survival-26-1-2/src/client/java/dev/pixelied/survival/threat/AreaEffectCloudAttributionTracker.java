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
    private static final double MATCH_DISTANCE_SQUARED = 4d;
    private static final long NETWORK_GRACE_TICKS = 2L;
    private static final int DEFAULT_REAPPLICATION_DELAY_TICKS = 20;

    private final Map<String, PendingAttribution> pendingByProjectile = new HashMap<>();
    private final Map<String, CloudAttribution> activeByCloud = new HashMap<>();

    public void observePredictedThreats(long clientTick, List<ThreatEvent> threats) {
        if (threats == null) throw new NullPointerException("threats");
        expirePending(clientTick);

        for (ThreatEvent threat : threats) {
            if (threat == null || threat.impactPosition().isEmpty()) continue;

            String marker;
            CloudAttribution attribution;
            if (threat.id().endsWith(DRAGON_BREATH_MARKER)) {
                marker = DRAGON_BREATH_MARKER;
                attribution = CloudAttribution.dragonBreath();
            } else if (threat.id().endsWith(LINGERING_POTION_MARKER)) {
                marker = LINGERING_POTION_MARKER;
                attribution = CloudAttribution.lingeringPotion(threat);
                if (attribution.rawDamage() <= 0f) continue;
            } else {
                continue;
            }

            String projectileKey = threat.id().substring(0, threat.id().length() - marker.length());
            long expiresAt = saturatingAdd(
                clientTick,
                saturatingAdd(Math.max(0L, threat.impact().latest()), NETWORK_GRACE_TICKS)
            );
            PendingAttribution candidate = new PendingAttribution(
                threat.impactPosition().get(),
                expiresAt,
                attribution
            );
            pendingByProjectile.merge(
                projectileKey,
                candidate,
                (previous, next) -> next.expiresAt() >= previous.expiresAt() ? next : previous
            );
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
                activeByCloud.put(entity.id(), matched.cloud());
                consumedPending.add(bestKey);
            }
        }
        for (String key : consumedPending) pendingByProjectile.remove(key);

        List<WorldSnapshot.EntitySnapshot> annotated = new ArrayList<>(world.entities().size());
        for (WorldSnapshot.EntitySnapshot entity : world.entities()) {
            CloudAttribution attribution = activeByCloud.get(entity.id());
            if (attribution == null || !CLOUD_TYPE.equals(entity.typeKey())) {
                annotated.add(entity);
                continue;
            }

            Map<String, String> properties = new LinkedHashMap<>(entity.properties());
            properties.put("cloud_instant_damage", Float.toString(attribution.rawDamage()));
            properties.put("cloud_source_key", attribution.sourceKey());
            properties.put("cloud_reapplication_delay_ticks", Integer.toString(attribution.reapplicationDelayTicks()));
            properties.put("cloud_attribution", attribution.attributionKey());
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

    private void expirePending(long clientTick) {
        pendingByProjectile.entrySet().removeIf(entry -> entry.getValue().expiresAt() < clientTick);
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

    private record PendingAttribution(
        Vec3Snapshot origin,
        long expiresAt,
        CloudAttribution cloud
    ) {
    }

    private record CloudAttribution(
        float rawDamage,
        String sourceKey,
        int reapplicationDelayTicks,
        String attributionKey
    ) {
        private static CloudAttribution dragonBreath() {
            return new CloudAttribution(
                6f,
                "minecraft:indirect_magic",
                DEFAULT_REAPPLICATION_DELAY_TICKS,
                "dragon_breath"
            );
        }

        private static CloudAttribution lingeringPotion(ThreatEvent threat) {
            return new CloudAttribution(
                threat.damage().rawDamage().max(),
                threat.damage().sourceKey(),
                DEFAULT_REAPPLICATION_DELAY_TICKS,
                "lingering_potion"
            );
        }
    }
}
