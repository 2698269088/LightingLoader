package top.mcocet.api.registry;

import java.util.function.Supplier;

/**
 * 物品延迟持有者——对应 NeoForge 的 {@code DeferredItem<T>}。
 * 语义等同 {@link DeferredHolder},仅约束所属注册表为物品注册表
 * ({@code minecraft:item}),供迁移代码按类型区分。
 *
 * @param <T> 物品类型(API 层为不透明泛型)
 */
public final class DeferredItem<T> extends DeferredHolder<T> {

    /** 物品注册表名 */
    public static final String REGISTRY_NAME = "minecraft:item";

    private DeferredItem(String registryName, String id, Supplier<T> supplier) {
        super(registryName, id, supplier);
    }

    /** 在物品注册表上创建持有者 */
    public static <T> DeferredItem<T> create(String id, Supplier<T> supplier) {
        return new DeferredItem<>(REGISTRY_NAME, id, supplier);
    }

    /** 在指定注册表名上创建持有者(供延迟注册器内部使用) */
    public static <T> DeferredItem<T> create(String registryName, String id, Supplier<T> supplier) {
        return new DeferredItem<>(registryName, id, supplier);
    }
}
