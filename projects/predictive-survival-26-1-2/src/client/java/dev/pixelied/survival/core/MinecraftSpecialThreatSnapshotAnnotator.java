package dev.pixelied.survival.core;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.EvokerFangs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MinecraftSpecialThreatSnapshotAnnotator {
    public WorldSnapshot annotate(ClientLevel level, LocalPlayer player, WorldSnapshot snapshot) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");

        List<WorldSnapshot.EntitySnapshot> entities = new ArrayList<>(snapshot.entities().size());
        for (WorldSnapshot.EntitySnapshot entity : snapshot.entities()) {
            entities.add(annotateEntity(level, player, entity));
        }
        return new WorldSnapshot(entities, snapshot.blocks());
    }

    private static WorldSnapshot.EntitySnapshot annotateEntity(
        ClientLevel level,
        LocalPlayer player,
        WorldSnapshot.EntitySnapshot snapshot
    ) {
        Integer entityId = parseEntityId(snapshot.id());
        if (entityId == null) return snapshot;
        Entity live = level.getEntity(entityId);
        if (live == null) return snapshot;

        Map<String, String> properties = new LinkedHashMap<>(snapshot.properties());
        if (live instanceof Guardian guardian) {
            LivingEntity target = guardian.getActiveAttackTarget();
            properties.put(
                "guardian_beam_target_local",
                Boolean.toString(target != null && target.getUUID().equals(player.getUUID()))
            );
            int conservativeAttackTicks = Math.max(0, (int) Math.floor(guardian.getClientSideAttackTime()));
            properties.put("guardian_attack_ticks", Integer.toString(conservativeAttackTicks));
            properties.put("guardian_attack_duration", Integer.toString(Math.max(1, guardian.getAttackDuration())));
        }
        if (live instanceof Warden warden && warden.sonicBoomAnimationState.isStarted()) {
            long elapsedMillis = Math.max(0L, warden.sonicBoomAnimationState.getTimeInMillis(warden.tickCount));
            int elapsedTicks = elapsedMillis >= Integer.MAX_VALUE * 50L
                ? Integer.MAX_VALUE
                : (int) Math.floor(elapsedMillis / 50.0d);
            properties.put("warden_sonic_ticks", Integer.toString(elapsedTicks));
        }
        if (live instanceof EvokerFangs fangs) {
            float currentProgress = Math.max(0f, fangs.getAnimationProgress(0f));
            boolean started = fangs.getAnimationProgress(1f) > 0f;
            int elapsedTicks = started ? Math.max(0, (int) Math.floor(currentProgress * 20f + 1.0E-4f)) : 0;
            properties.put("evoker_fangs_started", Boolean.toString(started));
            properties.put("evoker_fangs_elapsed_ticks", Integer.toString(elapsedTicks));
        }
        if (properties.equals(snapshot.properties())) return snapshot;
        return new WorldSnapshot.EntitySnapshot(
            snapshot.id(), snapshot.typeKey(), snapshot.position(), snapshot.velocity(), snapshot.boundingBox(), properties
        );
    }

    private static Integer parseEntityId(String id) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
