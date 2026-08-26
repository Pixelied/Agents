package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTimelineSimulator;
import dev.pixelied.survival.timeline.TimelineResult;

import java.util.Objects;

/** Shared post-mitigation/timeline facade for observed threats and hypothetical opportunities. */
public final class VanillaDamageOracle {
    private final ThreatTimelineSimulator timelineSimulator;

    public VanillaDamageOracle() {
        this(new ThreatTimelineSimulator());
    }

    public VanillaDamageOracle(ThreatTimelineSimulator timelineSimulator) {
        this.timelineSimulator = Objects.requireNonNull(timelineSimulator, "timelineSimulator");
    }

    public TimelineResult simulate(PlayerSnapshot player, ThreatTimeline timeline) {
        return timelineSimulator.simulate(
            Objects.requireNonNull(player, "player"),
            Objects.requireNonNull(timeline, "timeline")
        );
    }

    public boolean lethalWithoutDeathProtection(PlayerSnapshot player, ThreatTimeline timeline) {
        Objects.requireNonNull(player, "player");
        PlayerSnapshot unprotected = new PlayerSnapshot(
            player.health(),
            player.absorption(),
            player.playerInvulnerable(),
            player.abilityInvulnerable(),
            player.deadOrDying(),
            player.difficulty(),
            player.mitigation(),
            player.statusEffects(),
            player.blocking(),
            player.hurtState(),
            DeathProtectionSnapshot.none(),
            player.boundingBox(),
            player.position(),
            player.velocity(),
            player.equipmentItemKeys(),
            player.stateProperties()
        );
        return !timelineSimulator.simulate(unprotected, Objects.requireNonNull(timeline, "timeline")).survived();
    }
}
