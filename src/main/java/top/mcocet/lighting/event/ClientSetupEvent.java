package top.mcocet.lighting.event;

import top.mcocet.lighting.loader.ModContainer;

import java.util.List;

/**
 * 客户端设置阶段:公共设置完成后、移交游戏前发布(客户端进程)。
 * 对应 FML 的 ClientSetup 阶段——Mod 在此完成只在客户端进行的准备工作。
 * <p>
 * 可取消:任一监听器取消则启动流程中止,游戏不会被移交启动。
 */
public final class ClientSetupEvent extends CancellableEvent {

    private final List<ModContainer> mods;

    public ClientSetupEvent(List<ModContainer> mods) {
        this.mods = mods;
    }

    /** 当前已加载的全部 Mod */
    public List<ModContainer> mods() {
        return mods;
    }
}
