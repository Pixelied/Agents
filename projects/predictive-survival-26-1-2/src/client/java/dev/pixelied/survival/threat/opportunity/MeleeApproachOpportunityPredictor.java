package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.VanillaDamageOracle;
import dev.pixelied.survival.threat.MeleePredictor;
import dev.pixelied.survival.threat.ServerPlayerAttackRange;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Predicts a lethal player melee/mace/spear hit at the first legal future server-range entry. */
public final class MeleeApproachOpportunityPredictor implements LethalOpportunityPredictor {
    private final VanillaDamageOracle damageOracle = new VanillaDamageOracle();

    @Override
    public List<LethalOpportunity> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<LethalOpportunity> result = new ArrayList<>();

        for (WorldSnapshot.EntitySnapshot attacker : context.world().entities()) {
            if (!"minecraft:player".equals(attacker.typeKey())) continue;
            Map<String, String> properties = attacker.properties();
            if (!Boolean.parseBoolean(properties.getOrDefault("melee_capable", "false"))) continue;
            if ("false".equalsIgnoreCase(properties.getOrDefault("line_of_sight", "unknown"))) continue;
            if ("mob".equals(properties.get("melee_model"))) continue;

            var eyeResult = ServerPlayerAttackRange.eyePosition(attacker);
            var profileResult = ServerPlayerAttackRange.attackProfile(properties);
            if (eyeResult.isEmpty() || profileResult.isEmpty()) continue;
            Vec3Snapshot eye = eyeResult.get();
            ServerPlayerAttackRange.AttackProfile attackProfile = profileResult.get();

            // The actual-threat timeline owns attackers that are already server-attackable now.
            if (ServerPlayerAttackRange.isWithin(eye, context.player().boundingBox(), attackProfile)) continue;

            int entryTick = firstEntryTick(context, attacker, eye, attackProfile);
            if (entryTick < 1) continue;

            Vec3Snapshot attackerDelta = scale(attacker.velocity(), entryTick);
            WorldSnapshot.EntitySnapshot projectedAttacker = new WorldSnapshot.EntitySnapshot(
                attacker.id(),
                attacker.typeKey(),
                add(attacker.position(), attackerDelta),
                attacker.velocity(),
                translate(attacker.boundingBox(), attackerDelta),
                attacker.properties()
            );
            PlayerSnapshot projectedPlayer = projectPlayer(context.player(), entryTick);
            PredictionContext projectedContext = new PredictionContext(
                projectedPlayer,
                context.world(),
                context.timing(),
                context.limits(),
                context.safetyMode()
            );

            String id = "opportunity:melee_approach:" + attacker.id();
            TickWindow impact = new TickWindow(entryTick, entryTick);
            ThreatEvent projected = MeleePredictor.buildProjectedThreatWithoutRange(
                projectedContext,
                projectedAttacker,
                impact,
                Confidence.POTENTIAL,
                id
            ).orElse(null);
            if (projected == null) continue;
            if (!damageOracle.lethalWithoutDeathProtection(
                projectedPlayer,
                new ThreatTimeline(List.of(projected))
            )) continue;

            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put("attacker_id", attacker.id());
            evidence.put("entry_tick", Integer.toString(entryTick));
            evidence.put("server_attack_range_buffer", Double.toString(ServerPlayerAttackRange.ATTACK_PACKET_BUFFER));
            evidence.put("attack_min_range", Double.toString(attackProfile.minRange()));
            evidence.put("attack_max_range", Double.toString(attackProfile.maxRange()));
            evidence.put("attack_hitbox_margin", Double.toString(attackProfile.hitboxMargin()));
            evidence.put("attack_profile", attackProfile.source());
            evidence.put("weapon_key", properties.getOrDefault("weapon_key", "minecraft:air"));
            evidence.put("source_key", projected.damage().sourceKey());

            result.add(new LethalOpportunity(
                id,
                OpportunityFamily.MELEE,
                projected,
                Confidence.POTENTIAL,
                1,
                evidence
            ));
        }
        return List.copyOf(result);
    }

    private static int firstEntryTick(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot attacker,
        Vec3Snapshot eye,
        ServerPlayerAttackRange.AttackProfile profile
    ) {
        for (int tick = 1; tick <= context.limits().maxProjectileHorizonTicks(); tick++) {
            Vec3Snapshot projectedEye = add(eye, scale(attacker.velocity(), tick));
            AabbSnapshot projectedTarget = translate(
                context.player().boundingBox(),
                scale(context.player().velocity(), tick)
            );
            if (ServerPlayerAttackRange.isWithin(projectedEye, projectedTarget, profile)) return tick;
        }
        return -1;
    }

    private static PlayerSnapshot projectPlayer(PlayerSnapshot player, int ticks) {
        Vec3Snapshot delta = scale(player.velocity(), ticks);
        return new PlayerSnapshot(
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
            player.deathProtection(),
            translate(player.boundingBox(), delta),
            add(player.position(), delta),
            player.velocity(),
            player.equipmentItemKeys(),
            player.stateProperties()
        );
    }

    private static AabbSnapshot translate(AabbSnapshot box, Vec3Snapshot delta) {
        return new AabbSnapshot(
            box.minX() + delta.x(), box.minY() + delta.y(), box.minZ() + delta.z(),
            box.maxX() + delta.x(), box.maxY() + delta.y(), box.maxZ() + delta.z()
        );
    }

    private static Vec3Snapshot scale(Vec3Snapshot vector, long ticks) {
        return new Vec3Snapshot(vector.x() * ticks, vector.y() * ticks, vector.z() * ticks);
    }

    private static Vec3Snapshot add(Vec3Snapshot first, Vec3Snapshot second) {
        return new Vec3Snapshot(first.x() + second.x(), first.y() + second.y(), first.z() + second.z());
    }
}
