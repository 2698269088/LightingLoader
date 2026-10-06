package top.mcocet.lighting.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import top.mcocet.lighting.loader.ModContainer;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 游戏侧模组菜单钩子——参照 NeoForge 的
 * {@code net.neoforged.neoforge.client.gui.widget.ModsButton} + 其
 * TitleScreen 补丁的入口逻辑实现。
 * <p>
 * 本类必须由 {@code GameClassLoader} 定义(包 {@code top.mcocet.lighting.game.}
 * 自身优先加载),因此可以直接引用游戏 GUI 类;父加载器中的加载器内核
 * 通过 {@link #setLoadedMods(List)} 把三家生态的已加载 Mod 传入。
 * <p>
 * 调用链:{@code TitleScreen.init()} --(ASM 注入)--> 本类
 * {@link #onTitleScreenInit(TitleScreen)} --> 添加"模组"按钮 --> 打开
 * {@link ModListScreen}。
 */
public final class ModMenuHook {

    /** 按钮文案键;参照 NeoForge "fml.menu.mods",缺失语言资源时回退英文 */
    public static final String MODS_BUTTON_KEY = "lighting.menu.mods";
    public static final String MODS_BUTTON_FALLBACK = "Mods";

    /** 界面标题键;参照 NeoForge "fml.menu.mods.title" */
    public static final String MODS_TITLE_KEY = "lighting.menu.mods.title";
    public static final String MODS_TITLE_FALLBACK = "Mods";

    private static volatile List<ModContainer> loadedMods = List.of();
    private static volatile Method addRenderableWidgetMethod;

    private ModMenuHook() {
    }

    /** 由启动入口(父侧)在游戏移交前反射调用,注入已加载 Mod 列表 */
    public static void setLoadedMods(List<ModContainer> mods) {
        loadedMods = List.copyOf(mods);
    }

    public static List<ModContainer> loadedMods() {
        return loadedMods;
    }

    /**
     * 注入点:TitleScreen.init 创建完原版按钮组后调用。
     * 参照 NeoForge 的布局——"模组"按钮独立成行,位于按钮组下方;
     * 位置由现有组件实际底边 + 间距推算,不依赖固定行号,
     * 因此对 Realms 在线与否、测试世界按钮等布局差异天然兼容。
     */
    public static void onTitleScreenInit(TitleScreen screen) {
        int maxBottom = screen.children().stream()
                .filter(child -> child instanceof AbstractWidget)
                .mapToInt(child -> ((AbstractWidget) child).getBottom())
                .max()
                .orElse(screen.height / 4 + 72);

        Button modsButton = Button.builder(
                        Component.translatableWithFallback(MODS_BUTTON_KEY, MODS_BUTTON_FALLBACK),
                        button -> openModList(screen))
                .bounds(screen.width / 2 - 100, maxBottom + 12, 200, 20)
                .build();

        addWidget(screen, modsButton);
    }

    private static void openModList(Screen parent) {
        Minecraft.getInstance().setScreen(new ModListScreen(parent));
    }

    /**
     * {@code Screen.addRenderableWidget} 为 protected 成员,注入点位于
     * TitleScreen 类外,无法直接调用;游戏类位于无名模块,反射桥接安全。
     */
    static void addWidget(Screen screen, GuiEventListener widget) {
        try {
            Method method = addRenderableWidgetMethod;
            if (method == null) {
                method = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
                method.setAccessible(true);
                addRenderableWidgetMethod = method;
            }
            method.invoke(screen, widget);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Lighting: failed to add widget to screen", e);
        }
    }
}
