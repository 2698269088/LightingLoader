package top.mcocet.api.registry;

import java.util.function.Supplier;

/**
 * 实体类型延迟持有者——对应 NeoForge 的 {@code DeferredEntityType<T>}。
 * 语义等同 {@link DeferredHolder},仅约束所属注册表为实体类型注册表
 * ({@code minecraft:entity_type}),供迁移代码按类型区分。
 *
 * @param <T> 实体类型类型(API 层为不透明泛型)
 */
public final class DeferredEntityType<T> extends DeferredHolder<T> {

    /** 实体类型注册表名 */
    public static final String REGISTRY_NAME = "minecraft:entity_type";

    private DeferredEntityType(String registryName, String id, Supplier<T> supplier) {
        super(registryName, id, supplier);
    }

    /** 在实体类型注册表上创建持有者 */
    public static <T> DeferredEntityType<T> create(String id, Supplier<T> supplier) {
        return new DeferredEntityType<>(REGISTRY_NAME, id, supplier);
    }

    /** 在指定注册表名上创建持有者(供延迟注册器内部使用) */
    public static <T> DeferredEntityType<T> create(String registryName, String id, Supplier<T> supplier) {
        return new DeferredEntityType<>(registryName, id, supplier);
    }
}
