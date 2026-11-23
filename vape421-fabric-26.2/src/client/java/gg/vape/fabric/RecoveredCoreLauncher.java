package gg.vape.fabric;

import gg.vape.fabric.chat.RecoveredChatAdapter;
import gg.vape.fabric.input.RecoveredInputAdapter;
import gg.vape.fabric.movement.RecoveredMovementAdapter;
import gg.vape.fabric.network.RecoveredPacketAdapter;
import gg.vape.fabric.render.RecoveredEntityRenderAdapter;
import gg.vape.fabric.render.RecoveredRenderTaskAdapter;
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

            // The old NativeBridge intentionally logs and swallows exceptions
            // from initializeManagers(). A returned start() is NOT proof that
            // modules, profiles or the GUI are usable.
            requireInitializedCore(logger);

            RecoveredInputAdapter.install(logger);
            RecoveredLifecycleAdapter.install(logger);
            RecoveredPacketAdapter.enable(logger);
            RecoveredMovementAdapter.enable(logger);
            RecoveredTabNameAdapter.enable(logger);
            RecoveredChatAdapter.enable(logger);
            RecoveredTooltipAdapter.enable(logger);
            RecoveredRenderTaskAdapter.enable(logger);
            RecoveredEntityRenderAdapter.enable(logger);

            logger.info("Recovered Vape core started through Fabric lifecycle.");
        } catch (ClassNotFoundException missingDuringMigration) {
            logger.info("Recovered Vape core is not included in this migration build yet; Fabric shell remains active.");
        } catch (InvocationTargetException invocationFailure) {
            Throwable cause = invocationFailure.getCause() == null
                    ? invocationFailure : invocationFailure.getCause();
            logger.error("Recovered Vape core bootstrap failed", cause);
        } catch (ReflectiveOperationException reflectionFailure) {
            logger.error("Unable to invoke recovered Vape core bootstrap", reflectionFailure);
        } catch (IllegalStateException incompleteCore) {
            logger.error("Recovered Vape core initialization INCOMPLETE; input and tick hooks were not enabled", incompleteCore);
        } catch (LinkageError missingRuntimeDependency) {
            logger.error("Recovered Vape core is missing a runtime dependency", missingRuntimeDependency);
        }
    }

    private static void requireInitializedCore(Logger logger)
            throws ReflectiveOperationException {
        Class<?> vapeClass = Class.forName(
                "gg.vape.Vape", false, RecoveredCoreLauncher.class.getClassLoader());
        Object instance = vapeClass.getField("INSTANCE").get(null);
        if (instance == null) {
            throw new IllegalStateException("Vape.INSTANCE was not created");
        }
        for (String getter : new String[]{
                "getProfilesManager", "getModManager", "getClientSettings",
                "getNotificationManager"
        }) {
            Object manager = vapeClass.getMethod(getter).invoke(instance);
            if (manager == null) {
                throw new IllegalStateException(getter + "() is null; initializeManagers failed");
            }
        }
        logger.info("Recovered Vape managers initialized (profiles, modules, settings, notifications).");
    }
}
