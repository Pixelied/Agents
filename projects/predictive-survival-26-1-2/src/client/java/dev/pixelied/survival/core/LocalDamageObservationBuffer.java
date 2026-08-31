package dev.pixelied.survival.core;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Process-wide evidence captured only from clientbound packets after vanilla applies them.
 * Damage-event packets retain exact source identity; health and absorption are tracked separately
 * because 26.1.2 synchronizes them through different packet families.
 */
public final class LocalDamageObservationBuffer {
    private static final int MAX_DAMAGE_EVENTS = 16;

    private static long generation;
    private static long revision;
    private static long droppedDamageEventThroughRevision;
    private static final ArrayDeque<DamageEventEvidence> DAMAGE_EVENTS = new ArrayDeque<>();
    private static ValueEvidence health;
    private static ValueEvidence absorption;
    private static int pendingEntityDataId = Integer.MIN_VALUE;
    private static float pendingAbsorption;

    private LocalDamageObservationBuffer() {
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(
            generation,
            revision,
            droppedDamageEventThroughRevision,
            List.copyOf(DAMAGE_EVENTS),
            health,
            absorption
        );
    }

    public static synchronized void observeDamageEvent(ClientboundDamageEventPacket packet, LocalPlayer player) {
        if (packet == null || player == null || packet.entityId() != player.getId()) return;
        long next = nextRevision();
        DAMAGE_EVENTS.addLast(new DamageEventEvidence(
            packet.sourceType().getRegisteredName(),
            player.tickCount,
            next
        ));
        while (DAMAGE_EVENTS.size() > MAX_DAMAGE_EVENTS) {
            DamageEventEvidence dropped = DAMAGE_EVENTS.removeFirst();
            droppedDamageEventThroughRevision = Math.max(droppedDamageEventThroughRevision, dropped.revision());
        }
    }

    public static synchronized void observeHealth(ClientboundSetHealthPacket packet, LocalPlayer player) {
        if (packet == null || player == null) return;
        health = new ValueEvidence(packet.getHealth(), player.tickCount, nextRevision());
    }

    /** Capture the local absorption value before vanilla assigns synchronized entity data. */
    public static synchronized void beforeEntityData(ClientboundSetEntityDataPacket packet, LocalPlayer player) {
        if (packet == null || player == null || packet.id() != player.getId()) {
            pendingEntityDataId = Integer.MIN_VALUE;
            return;
        }
        pendingEntityDataId = packet.id();
        pendingAbsorption = player.getAbsorptionAmount();
    }

    /**
     * Records absorption only when this exact entity-data packet changed it. This avoids relying on
     * Player's private DATA_PLAYER_ABSORPTION_ID or treating unrelated metadata as absorption proof.
     */
    public static synchronized boolean afterEntityData(ClientboundSetEntityDataPacket packet, LocalPlayer player) {
        if (packet == null || player == null || packet.id() != player.getId() || pendingEntityDataId != packet.id()) {
            pendingEntityDataId = Integer.MIN_VALUE;
            return false;
        }
        pendingEntityDataId = Integer.MIN_VALUE;
        float current = player.getAbsorptionAmount();
        if (Float.compare(current, pendingAbsorption) == 0) return false;
        absorption = new ValueEvidence(current, player.tickCount, nextRevision());
        return true;
    }

    /** Invalidates every observation across respawn/dimension/authoritative relocation boundaries. */
    public static synchronized void invalidate() {
        generation = generation == Long.MAX_VALUE ? 0L : generation + 1L;
        revision = 0L;
        droppedDamageEventThroughRevision = 0L;
        DAMAGE_EVENTS.clear();
        health = null;
        absorption = null;
        pendingEntityDataId = Integer.MIN_VALUE;
    }

    private static long nextRevision() {
        if (revision == Long.MAX_VALUE) {
            generation = generation == Long.MAX_VALUE ? 0L : generation + 1L;
            revision = 0L;
            droppedDamageEventThroughRevision = 0L;
            DAMAGE_EVENTS.clear();
            health = null;
            absorption = null;
        }
        return ++revision;
    }

    public record DamageEventEvidence(String sourceKey, int playerTick, long revision) {
        public DamageEventEvidence {
            sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
            if (revision <= 0L) throw new IllegalArgumentException("revision must be positive");
        }
    }

    public record ValueEvidence(float value, int playerTick, long revision) {
        public ValueEvidence {
            if (!Float.isFinite(value) || value < 0f) {
                throw new IllegalArgumentException("authoritative value must be finite and non-negative");
            }
            if (revision <= 0L) throw new IllegalArgumentException("revision must be positive");
        }
    }

    public record Snapshot(
        long generation,
        long revision,
        long droppedDamageEventThroughRevision,
        List<DamageEventEvidence> damageEvents,
        ValueEvidence health,
        ValueEvidence absorption
    ) {
        public Snapshot {
            damageEvents = List.copyOf(Objects.requireNonNull(damageEvents, "damageEvents"));
        }

        public List<DamageEventEvidence> damageEventsAfter(long afterRevision) {
            List<DamageEventEvidence> result = new ArrayList<>();
            for (DamageEventEvidence event : damageEvents) {
                if (event.revision() > afterRevision) result.add(event);
            }
            return List.copyOf(result);
        }

        public boolean damageEventsCompleteSince(long afterRevision) {
            return afterRevision >= droppedDamageEventThroughRevision;
        }
    }
}
