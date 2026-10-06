package top.mcocet.lighting.event;

import top.mcocet.api.registry.RegistryBuilder;
import top.mcocet.lighting.loader.ModContainer;
import top.mcocet.lighting.registry.RegistryManager;

import java.util.List;

/**
 * 新注册表事件——对应 FML 的 {@code NewRegistryEvent}:
 * 在 Mod 构造完成、注册表冻结之前发布,Mod 可经 {@link #create(String)}
 * 创建自己的命名注册表,随后经 {@link RegisterEvent} 向注册表登记条目;
 * 此时注册表仍未冻结。
 */
public final class NewRegistryEvent {

    private final List<ModContainer> mods;
    private final RegistryManager registry;

    public NewRegistryEvent(List<ModContainer> mods, RegistryManager registry) {
        this.mods = mods;
        this.registry = registry;
    }

    /** 当前已加载的全部 Mod */
    public List<ModContainer> mods() {
        return mods;
    }

    /**
     * 创建一个静态注册表并返回其构建器(冻结前有效;同名注册表幂等复用)。
     * 注册表立即建立,条目经 {@link RegisterEvent} 写入。
     *
     * @param name 注册表名(如 {@code examplemod:spell})
     */
    public <T> RegistryBuilder<T> create(String name) {
        registry.ensureRegistry(name);
        return new RegistryBuilder<>(name, RegistryBuilder.Kind.STATIC,
                (registryName, kind, synced, codec, networkCodec) -> registry.ensureRegistry(registryName));
    }
}
