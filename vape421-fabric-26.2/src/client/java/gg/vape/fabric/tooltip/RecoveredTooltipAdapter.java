package gg.vape.fabric.tooltip;

import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public final class RecoveredTooltipAdapter {
    private static volatile boolean enabled;
    private static volatile Logger logger;
    private static volatile Method applyFabric;

    private RecoveredTooltipAdapter() { }

    public static void enable(Logger eventLogger) {
        logger = eventLogger;
        enabled = true;
    }

    @SuppressWarnings("unchecked")
    public static List<Component> rewrite(
            Object itemStack,
            Object player,
            List<Component> vanillaTooltip) {
        if (!enabled || vanillaTooltip == null) return vanillaTooltip;

        try {
            Method current = applyFabric;
            if (current == null) {
                current = resolve();
                applyFabric = current;
            }

            Object result = current.invoke(null, itemStack, player, vanillaTooltip);
            return result instanceof List<?> list
                    ? (List<Component>) list
                    : vanillaTooltip;
        } catch (Throwable failure) {
            Logger currentLogger = logger;
            if (currentLogger != null) {
                currentLogger.error("Recovered ItemStack tooltip adapter failed", failure);
            }
            return vanillaTooltip;
        }
    }

    private static synchronized Method resolve() throws ReflectiveOperationException {
        if (applyFabric != null) return applyFabric;

        Class<?> type = Class.forName(
                "gg.vape.mapping.ItemStackTooltipCallback",
                true,
                RecoveredTooltipAdapter.class.getClassLoader());
        return type.getMethod(
                "applyFabric", Object.class, Object.class, List.class);
    }
}
