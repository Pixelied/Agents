package gg.vape.fabric;

import gg.vape.Vape;
import gg.vape.runtime.NativeBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;

/** Register through Fabric and delay the recovered runtime until a world exists. */
public final class FabricClient implements ClientModInitializer {
    private static final AtomicBoolean started = new AtomicBoolean();
    private static volatile boolean ready;
    private static volatile Object minecraft;
    @Override public void onInitializeClient() {
        try {
            ClassLoader loader = FabricClient.class.getClassLoader();
            Class<?> events = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents", true, loader);
            Class<?> listener = Class.forName(events.getName() + "$EndTick", true, loader);
            Class<?> event = Class.forName("net.fabricmc.fabric.api.event.Event", true, loader);
            Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{listener}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    if (method.getName().equals("toString")) return "Vape421 world-ready callback";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                }
                if (args != null && args.length == 1) tick(args[0]);
                return null;
            });
            event.getMethod("register", Object.class).invoke(events.getField("END_CLIENT_TICK").get(null), callback);
            System.out.println("[Vape421] Fabric entrypoint registered; waiting for a world");
        } catch (Throwable error) {
            throw new IllegalStateException("Vape421 requires Fabric API client lifecycle events", error);
        }
    }
    private static void tick(Object client) {
        if (started.get()) return;
        try {
            if (field(client, "level") == null || field(client, "player") == null) return;
            if (!started.compareAndSet(false, true)) return;
            minecraft = client;
            long window = windowHandle(client);
            FabricInput.install(window);
            Thread worker = new Thread(() -> {
                try {
                    InProcessBytecode.load();
                    NativeBridge.start();
                    if (Vape.INSTANCE == null || Vape.INSTANCE.getModManager() == null) {
                        throw new IllegalStateException("Recovered managers were not initialized");
                    }
                    execute(() -> {
                        NativeBridge.wh(window);
                        ready = true;
                        System.out.println("[Vape421] Fabric initialization completed; default GUI key is Right Shift");
                    });
                } catch (Throwable error) {
                    System.err.println("[Vape421] initialization failed");
                    error.printStackTrace();
                }
            }, "Vape421 Fabric initialization");
            worker.setDaemon(true);
            worker.setContextClassLoader(FabricClient.class.getClassLoader());
            worker.start();
        } catch (Throwable error) {
            started.set(true);
            System.err.println("[Vape421] Fabric startup failed");
            error.printStackTrace();
        }
    }
    public static boolean isActive() { return minecraft != null; }
    public static void runAndWait(Runnable task) {
        java.util.concurrent.FutureTask<Void> future = new java.util.concurrent.FutureTask<>(task, null);
        execute(future);
        try { future.get(30, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); future.cancel(false); throw new java.util.concurrent.CompletionException(e); }
        catch (java.util.concurrent.ExecutionException e) { throw new java.util.concurrent.CompletionException(e.getCause()); }
        catch (java.util.concurrent.TimeoutException e) { future.cancel(false); throw new java.util.concurrent.CompletionException(e); }
    }
    static boolean isReady() { return ready; }
    static void execute(Runnable task) {
        Object client = minecraft;
        if (client == null) return;
        if (client instanceof Executor) { ((Executor)client).execute(task); return; }
        try { client.getClass().getMethod("execute", Runnable.class).invoke(client, task); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot schedule on client thread", e); }
    }
    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch (NoSuchFieldException absent) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static long windowHandle(Object client) throws ReflectiveOperationException {
        Object window;
        try { window = client.getClass().getMethod("getWindow").invoke(client); }
        catch (NoSuchMethodException e) { window = field(client, "window"); }
        for (String name : new String[]{"handle", "getWindow", "getHandle"}) {
            try {
                Method method = window.getClass().getMethod(name);
                Object value = method.invoke(window);
                if (value instanceof Number && ((Number)value).longValue() != 0) return ((Number)value).longValue();
            } catch (NoSuchMethodException ignored) {}
            try { Object value = field(window, name); if (value instanceof Number && ((Number)value).longValue() != 0) return ((Number)value).longValue(); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new IllegalStateException("Minecraft GLFW window handle not found");
    }
}
