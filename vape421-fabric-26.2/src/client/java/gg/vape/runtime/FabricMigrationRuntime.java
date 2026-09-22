package gg.vape.runtime;

/**
 * Runtime gate for the normal Fabric port. This deliberately detects only the
 * public Fabric Loader API and keeps platform checks out of Vape's core logic.
 */
public final class FabricMigrationRuntime {
    private static final boolean ACTIVE = classPresent("net.fabricmc.loader.api.FabricLoader");

    private FabricMigrationRuntime() {
    }

    public static boolean isActive() {
        return ACTIVE;
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, FabricMigrationRuntime.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
