package top.mcocet.api.registry;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 延迟注册持有者——对应 NeoForge 的 {@code DeferredHolder<R, T>}。
 * <p>
 * Mod 构造期经 {@link top.mcocet.api.LightingDeferredRegister} 的
 * typed 登记方法获得本类实例(或其特化子类 {@link DeferredBlock}/
 * {@link DeferredItem}/{@link DeferredEntityType}),此时注册值尚未解析;
 * 加载器在 RegisterEvent 阶段写入注册中心并触发解析,
 * 之后 {@link #get()} 返回确定值。
 * <p>
 * API 层不引用游戏类型,注册值为不透明泛型;{@link #id()} 为补全命名
 * 空间后的全限定 ID。
 *
 * @param <T> 注册值类型
 */
public class DeferredHolder<T> {

    private final String registryName;
    private final String id;
    private final Supplier<T> supplier;
    private T value;
    private boolean bound;

    protected DeferredHolder(String registryName, String id, Supplier<T> supplier) {
        this.registryName = Objects.requireNonNull(registryName, "registryName");
        this.id = Objects.requireNonNull(id, "id");
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    /** 创建一个通用延迟持有者 */
    public static <T> DeferredHolder<T> create(String registryName, String id, Supplier<T> supplier) {
        return new DeferredHolder<>(registryName, id, supplier);
    }

    /** 所属注册表名(如 {@code minecraft:item}) */
    public String registryName() {
        return registryName;
    }

    /** 全限定条目 ID(如 {@code examplemod:ruby}) */
    public String id() {
        return id;
    }

    /** 是否已解析出注册值(写入注册中心之后为 true) */
    public boolean isBound() {
        return bound;
    }

    /** 解析注册值:首次调用触发供应器,之后返回缓存 */
    public T get() {
        if (!bound) {
            value = supplier.get();
            bound = true;
        }
        return value;
    }

    /** 已解析时返回值;供应器抛异常或返回 null 时为空 */
    public Optional<T> asOptional() {
        try {
            return Optional.ofNullable(get());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** 已解析时返回值,否则回退;供应器抛异常时同样回退 */
    public T orElse(T fallback) {
        return asOptional().orElse(fallback);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + registryName + " " + id + (bound ? "" : ", unbound") + "]";
    }
}
