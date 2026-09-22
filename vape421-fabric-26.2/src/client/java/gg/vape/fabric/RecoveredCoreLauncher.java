package gg.vape.fabric;

import gg.vape.fabric.chat.RecoveredChatAdapter;
import gg.vape.fabric.input.RecoveredInputAdapter;
import gg.vape.fabric.movement.RecoveredMovementAdapter;
import gg.vape.fabric.network.RecoveredPacketAdapter;
import gg.vape.fabric.render.RecoveredTabNameAdapter;
import gg.vape.fabric.tooltip.RecoveredTooltipAdapter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

public final class RecoveredCoreLauncher {
    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean();
    private static final String BOOTSTRAP_CLASS = "gg.vape.runtime.VapeBootstrap";

    private RecoveredCoreLauncher() { }

    public static void startIfPresent(Minecraft client, Logger logger) {
        if (!client.isSameThread()) {
            client.execute(() -> startIfPresent(client, logger));
            return;
        }
        if (!ATTEMPTED.compareAndSet(false, true)) return;

        try {
            Class<?> bootstrapClass = Class.forName(
                    BOOTSTRAP_CLASS, true, RecoveredCoreLauncher.class.getClassLoader());
            Method start = bootstrapClass.getMethod("start");
            start.invoke(null);

            RecoveredInputAdapter.install(logger);
            RecoveredLifecycleAdapter.install(logger);
            RecoveredPacketAdapter.enable(logger);
            RecoveredMovementAdapter.enable(logger);
            RecoveredTabNameAdapter.enable(logger);
            RecoveredChatAdapter.enable(logger);
            RecoveredTooltipAdapter.enable(logger);

            logger.info("Recovered Vape core started through Fabric lifecycle.");
        } catch (ClassNotFoundException missingDuringMigration) {
            logger.info("Recovered Vape core is not included in this migration build yet; Fabric shell remains active.");
        } catch (InvocationTargetException invocationFailure) {
            Throwable cause = invocationFailure.getCause() == null
                    ? invocationFailure : invocationFailure.getCause();
            logger.error("Recovered Vape core bootstrap failed", cause);
        } catch (ReflectiveOperationException reflectionFailure) {
            logger.error("Unable to invoke recovered Vape core bootstrap", reflectionFailure);
        }
    }
}
