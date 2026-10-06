package top.mcocet.lighting.event;

import top.mcocet.lighting.loader.ModContainer;

import java.util.List;

/**
 * 服务端设置阶段:在专用服务器进程(--dist=server)上替代 {@link ClientSetupEvent} 发布。
 * <p>
 * 对应 FML 的 DedicatedServerSetup 阶段——Mod 在此完成只在服务端进行的准备工作。
 * <p>
 * 可取消:任一监听器取消则启动流程中止,服务器不会被移交启动。
 */
public final class DedicatedServerSetupEvent extends CancellableEvent {

    private final List<ModContainer> mods;

    public DedicatedServerSetupEvent(List<ModContainer> mods) {
        this.mods = mods;
    }

    /** 当前已加载的全部 Mod */
    public List<ModContainer> mods() {
        return mods;
    }
}
