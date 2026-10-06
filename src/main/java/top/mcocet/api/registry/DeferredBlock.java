package top.mcocet.api.registry;

import java.util.function.Supplier;

/**
 * 方块延迟持有者——对应 NeoForge 的 {@code DeferredBlock<T>}。
 * 语义等同 {@link DeferredHolder},仅约束所属注册表为方块注册表
 * ({@code minecraft:block}),供迁移代码按类型区分。
 *
 * @param <T> 方块类型(API 层为不透明泛型)
 */
public final class DeferredBlock<T> extends DeferredHolder<T> {

    /** 方块注册表名 */
    public static final String REGISTRY_NAME = "minecraft:block";

    private DeferredBlock(String registryName, String id, Supplier<T> supplier) {
        super(registryName, id, supplier);
    }

    /** 在方块注册表上创建持有者 */
    public static <T> DeferredBlock<T> create(String id, Supplier<T> supplier) {
        return new DeferredBlock<>(REGISTRY_NAME, id, supplier);
    }

    /** 在指定注册表名上创建持有者(供延迟注册器内部使用) */
    public static <T> DeferredBlock<T> create(String registryName, String id, Supplier<T> supplier) {
        return new DeferredBlock<>(registryName, id, supplier);
    }
}
