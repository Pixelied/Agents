package dev.pixelied.survival.timeline;

import dev.pixelied.survival.core.Vec3Snapshot;

import java.util.Objects;

/** A world-state consequence that occurs when a modeled threat event itself occurs. */
public sealed interface ThreatTransition
    permits ThreatTransition.RemoveSource, ThreatTransition.SpawnThreat, ThreatTransition.PlayerImpulse {

    record RemoveSource(String sourceId) implements ThreatTransition {
        public RemoveSource {
            sourceId = Objects.requireNonNull(sourceId, "sourceId");
            if (sourceId.isBlank()) throw new IllegalArgumentException("sourceId must not be blank");
        }
    }

    record SpawnThreat(ThreatEvent event) implements ThreatTransition {
        public SpawnThreat {
            event = Objects.requireNonNull(event, "event");
        }
    }

    record PlayerImpulse(Vec3Snapshot minVelocity, Vec3Snapshot maxVelocity) implements ThreatTransition {
        public PlayerImpulse {
            minVelocity = Objects.requireNonNull(minVelocity, "minVelocity");
            maxVelocity = Objects.requireNonNull(maxVelocity, "maxVelocity");
        }
    }
}
