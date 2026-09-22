package dev.adrien.naturalaim;

import net.minecraft.world.entity.MobCategory;

public final class TargetRules {
    private TargetRules() {
    }

    public static boolean allowsMobCategory(MobCategory category, boolean targetHostileMobs, boolean targetPassiveMobs) {
        return category == MobCategory.MONSTER ? targetHostileMobs : targetPassiveMobs;
    }
}
