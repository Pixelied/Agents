package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

public final class ClientTargetProtectionResolver {
    private final Minecraft minecraft;

    public ClientTargetProtectionResolver(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    public Set<UUID> resolve(
        Collection<AbstractClientPlayer> players,
        TargetProtectionPolicyConfig config
    ) {
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(config, "config");
        LinkedHashSet<UUID> protectedIds = new LinkedHashSet<>(config.protectedPlayerIds());
        LocalPlayer self = minecraft.player;
        if (self != null && config.protectScoreboardTeam()) {
            for (AbstractClientPlayer player : players) {
                if (player != null && player != self && self.isAlliedTo(player)) {
                    protectedIds.add(player.getUUID());
                }
            }
        }
        if (self != null) {
            protectedIds.remove(self.getUUID());
        }
        return Set.copyOf(protectedIds);
    }
}
