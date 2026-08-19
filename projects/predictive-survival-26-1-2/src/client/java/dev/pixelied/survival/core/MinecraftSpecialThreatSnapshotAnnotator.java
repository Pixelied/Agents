package dev.pixelied.survival.core;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;

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
        if (!(live instanceof Guardian guardian)) return snapshot;

        Map<String, String> properties = new LinkedHashMap<>(snapshot.properties());
        LivingEntity target = guardian.getActiveAttackTarget();
        properties.put(
            "guardian_beam_target_local",
            Boolean.toString(target != null && target.getUUID().equals(player.getUUID()))
        );
        properties.put("guardian_attack_ticks", Integer.toString(Math.max(0, guardian.getClientSideAttackTime())));
        properties.put("guardian_attack_duration", Integer.toString(Math.max(1, guardian.getAttackDuration())));
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
