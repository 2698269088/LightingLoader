package top.mcocet.lighting.game;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.mcocet.lighting.loader.ModContainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 模组列表界面——参照 NeoForge ModListScreen(26.1.x)的极简首版:
 * <ul>
 *   <li>结构对照:标题({@code fml.menu.mods.title} → {@code lighting.menu.mods.title})、
 *       底部 Done 按钮(参照 {@code gui.done} 的布局:底部居中、宽 200、边距 6)、
 *       模组清单逐行绘制(名称/版本/来源生态,数据取自三生态统一模型)</li>
 *   <li>绘制方式:26.1.x 的界面输出走 {@code extractRenderState(GuiGraphicsExtractor, …)},
 *       与 NeoForge 的用法一致</li>
 *   <li>后续里程碑对照 NeoForge 扩展:搜索框(EditBox)、配置按钮、
 *       打开 Mods 文件夹按钮、可滚动列表(ObjectSelectionList)与详情面板</li>
 * </ul>
 */
public final class ModListScreen extends Screen {

    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 12;

    private final Screen parentScreen;
    private List<ModContainer> mods = List.of();

    public ModListScreen(Screen parentScreen) {
        super(Component.translatableWithFallback(ModMenuHook.MODS_TITLE_KEY, ModMenuHook.MODS_TITLE_FALLBACK));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        List<ModContainer> sorted = new ArrayList<>(ModMenuHook.loadedMods());
        sorted.sort(Comparator.comparing(mod -> mod.metadata().name().toLowerCase(Locale.ROOT)));
        this.mods = List.copyOf(sorted);

        int doneWidth = Math.min(200, this.width - PADDING * 2);
        this.addRenderableWidget(Button.builder(
                        Component.translatableWithFallback("gui.done", "Done"),
                        button -> this.onClose())
                .bounds((this.width - doneWidth) / 2, this.height - 20 - PADDING, doneWidth, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // 标题居中
        String title = this.title.getString();
        graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 16, 0xFFFFFFFF);

        // 模组清单:名称 / 版本 / 来源生态
        int x = this.width / 2 - 160;
        int y = 40;
        int maxY = this.height - 40;
        for (ModContainer mod : this.mods) {
            if (y > maxY) {
                graphics.text(this.font, "…(%d mods total)".formatted(this.mods.size()),
                        x, y, 0xFFA0A0A0);
                return;
            }
            String line = "%s  v%s  [%s]".formatted(
                    mod.metadata().name(), mod.metadata().version(), mod.loaderType());
            graphics.text(this.font, line, x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
        }
        if (this.mods.isEmpty()) {
            graphics.text(this.font, "No mods loaded", x, y, 0xFFA0A0A0);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }
}
