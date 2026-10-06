package top.mcocet.lighting.event;

import top.mcocet.lighting.loader.ModContainer;

import java.util.List;

/**
 * 公共设置阶段:全部 Mod 加载与入口调度完成后、移交游戏前发布。
 * 对应 FML 的 CommonSetup 阶段——Mod 在此完成不依赖客户端环境的准备工作。
 * <p>
 * 可取消:任一监听器取消则启动流程中止,游戏不会被移交启动。
 */
public final class CommonSetupEvent extends CancellableEvent {

    private final List<ModContainer> mods;

    public CommonSetupEvent(List<ModContainer> mods) {
        this.mods = mods;
    }

    /** 当前已加载的全部 Mod */
    public List<ModContainer> mods() {
        return mods;
    }
}
