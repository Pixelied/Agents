package gg.vape.fabric.screen;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Fabric-native access to the recovered Vape module instances.
 *
 * The original injected OpenGL click GUI is not backend-neutral in Minecraft
 * 26.2. This menu only controls the existing modules; it does not duplicate
 * their module implementations or load any native injector code.
 */
public final class VapeFabricMenuScreen extends Screen {
    private static final List<String> CATEGORIES = List.of(
            "All", "Combat", "Render", "Utility", "World", "Inventory",
            "Network", "Other");
    private static final int PAGE_SIZE = 12;

    private final Screen previous;
    private final String category;
    private final int page;
    private final String recentError;
    private String status = "Loading Vape modules";
    private int shownCount;
    private int totalCount;

    private record ModuleView(Object original, String name, String category,
                              boolean enabled) { }
    private record ModuleLoad(List<ModuleView> modules, String error) { }

    public VapeFabricMenuScreen(Screen previous) {
        this(previous, "All", 0, null);
    }

    private VapeFabricMenuScreen(Screen previous, String category, int page, String recentError) {
        super(Component.literal("Vape 4.21"));
        this.previous = previous;
        this.category = category;
        this.page = Math.max(0, page);
        this.recentError = recentError;
    }

    /** Called from the actual 26.2 GLFW key callback, before legacy events. */
    public static boolean toggleFromKey() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.gui.screen();
        if (current instanceof VapeFabricMenuScreen menu) {
            minecraft.gui.setScreen(menu.previous);
            return true;
        }
        if (current != null) return false; // don't interrupt other menus or chat
        minecraft.gui.setScreen(new VapeFabricMenuScreen(null));
        return true;
    }

    @Override
    protected void init() {
        super.init();
        ModuleLoad result = loadModules();
        totalCount = result.modules().size();
        int contentWidth = Math.min(750, Math.max(280, this.width - 28));
        int left = (this.width - contentWidth) / 2;

        if (result.error() != null) {
            status = "Vape core not ready: " + result.error();
            addButton("Close", this.width / 2 - 90, this.height - 42, 180, 20,
                    button -> this.onClose());
            return;
        }

        int categoryWidth = (contentWidth - (CATEGORIES.size() - 1) * 4)
                / CATEGORIES.size();
        for (int i = 0; i < CATEGORIES.size(); i++) {
            String name = CATEGORIES.get(i);
            int x = left + i * (categoryWidth + 4);
            Button categoryButton = addButton(
                    name, x, 44, categoryWidth, 20,
                    button -> navigate(name, 0));
            categoryButton.active = !name.equals(category);
        }

        List<ModuleView> filtered = new ArrayList<>();
        for (ModuleView module : result.modules()) {
            if (category.equals("All") || module.category().equalsIgnoreCase(category)) {
                filtered.add(module);
            }
        }
        shownCount = filtered.size();
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.min(page, pages - 1);
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(filtered.size(), start + PAGE_SIZE);

        int columnGap = 12;
        int columnWidth = (contentWidth - columnGap) / 2;
        int firstRowY = 105;
        for (int i = start; i < end; i++) {
            ModuleView module = filtered.get(i);
            int position = i - start;
            int column = position % 2;
            int row = position / 2;
            int x = left + column * (columnWidth + columnGap);
            int y = firstRowY + row * 25;
            String label = (module.enabled() ? "[ON] " : "[OFF] ") + module.name();
            addButton(label, x, y, columnWidth, 20,
                    button -> toggleModule(module, currentPage));
        }
        if (filtered.isEmpty()) {
            status = "No modules in " + category;
        } else {
            status = category + "  |  " + filtered.size() + " modules  |  Page "
                    + (currentPage + 1) + "/" + pages;
        }

        int navY = Math.min(this.height - 42, firstRowY + 6 * 25 + 16);
        Button previousPage = addButton("< Previous", left, navY, 110, 20,
                button -> navigate(category, currentPage - 1));
        previousPage.active = currentPage > 0;
        Button nextPage = addButton("Next >", left + contentWidth - 110, navY, 110, 20,
                button -> navigate(category, currentPage + 1));
        nextPage.active = currentPage + 1 < pages;
        addButton("Close", this.width / 2 - 70, navY, 140, 20,
                button -> this.onClose());
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 19, -1);
        graphics.centeredText(this.font, Component.literal(status),
                this.width / 2, 79, 0xFFAAB3C2);
        if (recentError != null) {
            graphics.centeredText(this.font, Component.literal(recentError),
                    this.width / 2, this.height - 17, 0xFFFF7777);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(previous);
    }

    private Button addButton(String label, int x, int y, int width, int height,
                             Button.OnPress press) {
        return this.addRenderableWidget(Button.builder(Component.literal(label), press)
                .bounds(x, y, width, height).build());
    }

    private void navigate(String nextCategory, int nextPage) {
        this.minecraft.gui.setScreen(new VapeFabricMenuScreen(
                previous, nextCategory, nextPage, null));
    }

    private void toggleModule(ModuleView module, int currentPage) {
        String error = null;
        try {
            Method toggle = module.original().getClass().getMethod("toggle");
            toggle.invoke(module.original());
        } catch (Throwable failure) {
            Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
            error = "Could not toggle " + module.name() + ": "
                    + cause.getClass().getSimpleName();
        }
        this.minecraft.gui.setScreen(
                new VapeFabricMenuScreen(previous, category, currentPage, error));
    }

    /**
     * Stronger than checking that a Screen was merely instantiated: verifies
     * that the recovered core can enumerate real module instances and read
     * their state. Called by the CI Minecraft integration smoke test.
     */
    public static int requireModulesReady() {
        ModuleLoad loaded = loadModules();
        if (loaded.error() != null) {
            throw new IllegalStateException("Vape native menu module loading failed: " + loaded.error());
        }
        int size = loaded.modules().size();
        if (size < 50) {
            throw new IllegalStateException("Vape native menu only found " + size + " modules");
        }
        return size;
    }

    private static ModuleLoad loadModules() {
        try {
            Class<?> vapeClass = Class.forName("gg.vape.Vape");
            Object vape = vapeClass.getField("INSTANCE").get(null);
            if (vape == null) {
                return new ModuleLoad(List.of(), "Vape.INSTANCE is null");
            }
            Object manager = vapeClass.getMethod("getModManager").invoke(vape);
            Object profiles = vapeClass.getMethod("getProfilesManager").invoke(vape);
            if (manager == null || profiles == null) {
                return new ModuleLoad(List.of(),
                        "managers did not finish initializing (see latest.log)");
            }
            Object raw = manager.getClass().getMethod("collectMods").invoke(manager);
            if (!(raw instanceof Collection<?> collection)) {
                return new ModuleLoad(List.of(), "unexpected ModManager response");
            }
            List<ModuleView> modules = new ArrayList<>();
            for (Object module : collection) {
                Class<?> type = module.getClass();
                String name = String.valueOf(type.getMethod("getName").invoke(module));
                Object moduleCategory = type.getMethod("getCategory").invoke(module);
                String section = moduleCategory == null
                        ? "Other" : String.valueOf(moduleCategory);
                boolean enabled = Boolean.TRUE.equals(type.getMethod("isEnabled").invoke(module));
                modules.add(new ModuleView(module, name, section, enabled));
            }
            modules.sort(Comparator.comparing(ModuleView::name,
                    String.CASE_INSENSITIVE_ORDER));
            return new ModuleLoad(modules, null);
        } catch (Throwable failure) {
            return new ModuleLoad(List.of(), failure.getClass().getSimpleName());
        }
    }
}
