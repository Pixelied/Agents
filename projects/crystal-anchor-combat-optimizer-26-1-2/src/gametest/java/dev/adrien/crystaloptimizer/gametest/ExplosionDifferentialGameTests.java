package dev.adrien.crystaloptimizer.gametest;

import dev.adrien.crystaloptimizer.sim.damage.DamageRequest;
import dev.adrien.crystaloptimizer.sim.damage.DamageResult;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionDamageCalculator26;
import dev.adrien.crystaloptimizer.sim.damage.VanillaDamageSimulator;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ExplosionDifferentialGameTests implements CustomTestMethodInvoker {
    @GameTest
    public void exposedCrystalDamageMatchesVanilla(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = GameTestCombatants.makeSurvivalPlayer(level);
        target.setHealth(target.getMaxHealth());
        helper.assertTrue(
            !target.getAbilities().invulnerable && !target.isCreative(),
            "differential fixture must be a damageable survival player"
        );
        Vec3 center = target.position().add(9.0, 0.0, 0.0);
        float before = target.getHealth();

        float raw = ExplosionDamageCalculator26.incoming(
            ExplosionContext.crystal(center),
            target.getBoundingBox(),
            target.position(),
            new ServerLevelBlockView(level)
        );
        DamageResult predicted = VanillaDamageSimulator.apply(
            GameTestCombatants.exactFirstHit(target),
            DamageRequest.explosion(raw)
                .withDifficulty(level.getDifficulty())
                .withSourcePosition(center)
        );

        level.explode(null, center.x, center.y, center.z, 6.0f, Level.ExplosionInteraction.NONE);
        float observedLoss = before - target.getHealth();
        helper.assertTrue(
            Math.abs(observedLoss - predicted.trace().healthDamage()) <= 1.0e-4f,
            "vanilla and simulator damage diverged: predicted="
                + predicted.trace().healthDamage() + ", observed=" + observedLoss
        );
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        method.invoke(this, context);
    }
}
