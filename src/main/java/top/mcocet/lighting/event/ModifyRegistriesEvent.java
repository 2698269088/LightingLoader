package top.mcocet.lighting.event;

import top.mcocet.lighting.loader.ModContainer;
import top.mcocet.lighting.registry.RegistryManager;

import java.util.List;

/**
 * 注册表修改事件——对应 NeoForge 的 {@code ModifyRegistriesEvent}。
 * <p>
 * 在 RegisterEvent 之后、冻结之前发布:此阶段允许对已登记的条目做整表级
 * 调整(如移除被替换的占位条目)。冻结(加载完成)后任何修改都会抛出异常,
 * 保证"注册表在玩家进入游戏前已固定"。
 */
public final class ModifyRegistriesEvent {

    private final List<ModContainer> mods;
    private final RegistryManager registry;

    public ModifyRegistriesEvent(List<ModContainer> mods, RegistryManager registry) {
        this.mods = mods;
        this.registry = registry;
    }

    /** 当前已加载的全部 Mod */
    public List<ModContainer> mods() {
        return mods;
    }

    /** 可修改的注册中心视图(仅冻结前可写) */
    public RegistryManager registry() {
        return registry;
    }
}
