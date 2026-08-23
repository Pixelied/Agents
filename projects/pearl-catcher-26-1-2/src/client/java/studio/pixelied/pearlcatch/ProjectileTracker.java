package studio.pixelied.pearlcatch;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Exact local-projectile association. Proximity is only a tie-breaker after vanilla ownership is proven. */
final class ProjectileTracker {
    private ProjectileTracker() {}

    static boolean isOwnedByLocal(Entity entity, LocalPlayer player) {
        return entity instanceof Projectile projectile
                && player != null
                && projectile.getOwner() == player;
    }

    static <T extends Entity> T findNewOwned(
            ClientLevel level, LocalPlayer player, Set<Integer> oldIds, Class<T> type, Vec3 near
    ) {
        List<T> candidates = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (type.isInstance(entity)
                    && !oldIds.contains(entity.getId())
                    && entity.isAlive()
                    && isOwnedByLocal(entity, player)) {
                candidates.add(type.cast(entity));
            }
        }
        return candidates.stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(near)))
                .orElse(null);
    }

    static <T extends Entity> T findOwnedById(
            ClientLevel level, LocalPlayer player, int id, Class<T> type
    ) {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getId() == id && type.isInstance(entity) && isOwnedByLocal(entity, player)) {
                return type.cast(entity);
            }
        }
        return null;
    }
}
