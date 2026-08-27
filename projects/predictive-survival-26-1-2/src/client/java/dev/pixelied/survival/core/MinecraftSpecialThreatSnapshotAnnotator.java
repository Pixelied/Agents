package dev.pixelied.survival.core;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.component.KineticWeapon;

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
        if (live instanceof Player remotePlayer && remotePlayer != player) {
            var mainHand = remotePlayer.getMainHandItem();
            properties.put(
                "piercing_weapon",
                Boolean.toString(mainHand.has(DataComponents.PIERCING_WEAPON))
            );

            // KINETIC_WEAPON is network-synchronized in 26.1.2, so these component values are
            // authoritative client-observable inputs. Deliberately do not invent the remote
            // player's server-only known speed, attack-damage base, or recent-contact map here.
            KineticWeapon kinetic = mainHand.get(DataComponents.KINETIC_WEAPON);
            if (kinetic != null) {
                properties.put("spear_kinetic", "true");
                properties.put(
                    "spear_kinetic_contact_cooldown_ticks",
                    Integer.toString(kinetic.contactCooldownTicks())
                );
                properties.put("spear_kinetic_delay_ticks", Integer.toString(kinetic.delayTicks()));
                properties.put("spear_damage_multiplier", Float.toString(kinetic.damageMultiplier()));
                kinetic.damageConditions().ifPresent(condition -> {
                    properties.put(
                        "spear_damage_max_use_ticks",
                        Integer.toString(condition.maxDurationTicks())
                    );
                    properties.put("spear_damage_min_speed", Float.toString(condition.minSpeed()));
                    properties.put(
                        "spear_damage_min_relative_speed",
                        Float.toString(condition.minRelativeSpeed())
                    );
                });
            }
        }
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
