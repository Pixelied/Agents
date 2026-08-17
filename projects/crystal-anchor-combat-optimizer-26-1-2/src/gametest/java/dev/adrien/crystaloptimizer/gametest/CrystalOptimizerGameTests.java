package dev.adrien.crystaloptimizer.gametest;

import java.lang.reflect.Method;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class CrystalOptimizerGameTests implements CustomTestMethodInvoker {
    @GameTest
    public void bootstrap(GameTestHelper context) {
        context.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        method.invoke(this, context);
    }
}
