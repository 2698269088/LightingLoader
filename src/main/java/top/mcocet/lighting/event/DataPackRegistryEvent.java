package top.mcocet.lighting.event;

import top.mcocet.api.registry.RegistryBuilder;
import top.mcocet.lighting.loader.ModContainer;
import top.mcocet.lighting.registry.RegistryManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界数据注册表声明事件——对应 NeoForge 的 {@code DataPackRegistryEvent}。
 * <p>
 * 在注册表初始化阶段(冻结前)发布:Mod 通过 {@link #declare(String)} 或
 * {@link #dataPackRegistry(String)} 声明随世界存档加载/同步的数据注册表
 * (如自定义的世界规则表);后者返回 {@link RegistryBuilder},可补充
 * 序列化/网络编解码器与同步描述。声明的注册表名进入统一注册中心的世界数据表,
 * 由数据包加载与网络同步环节消费。
 */
public final class DataPackRegistryEvent {

    private final List<ModContainer> mods;
    private final RegistryManager registry;
    private final List<RegistryBuilder<?>> builders = new ArrayList<>();
    private int declared;

    public DataPackRegistryEvent(List<ModContainer> mods, RegistryManager registry) {
        this.mods = mods;
        this.registry = registry;
    }

    /** 当前已加载的全部 Mod */
    public List<ModContainer> mods() {
        return mods;
    }

    /** 声明一个随世界数据存档加载的数据注册表(冻结前有效) */
    public void declare(String name) {
        registry.declareWorldRegistry(name);
        declared++;
    }

    /**
     * 声明一个随世界数据存档加载的数据注册表并返回构建器:
     * 经 {@link RegistryBuilder#codec(Object)}/{@link RegistryBuilder#networkCodec(Object)}/
     * {@link RegistryBuilder#sync(boolean)} 补充序列化与同步描述;
     * {@link RegistryBuilder#create()} 提交(幂等;事件收尾时自动提交未显式调用者)。
     */
    public <T> RegistryBuilder<T> dataPackRegistry(String name) {
        RegistryBuilder<T> builder = new RegistryBuilder<>(name, RegistryBuilder.Kind.WORLD,
                (registryName, kind, synced, codec, networkCodec) ->
                        registry.declareWorldRegistry(registryName, synced, codec, networkCodec));
        builders.add(builder);
        declared++;
        return builder;
    }

    /** 提交所有尚未显式 {@code create()} 的构建器(由加载管线在事件派发收尾时调用) */
    public void completePending() {
        builders.forEach(RegistryBuilder::create);
    }

    /** 本事件处理期间声明的注册表数量 */
    public int declaredCount() {
        return declared;
    }
}
