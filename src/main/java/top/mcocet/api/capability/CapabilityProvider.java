package top.mcocet.api.capability;

/**
 * 能力提供器:给定上下文对象返回能力实现实例,
 * 不支持时返回 {@code null}(查询将继续尝试后续提供器)。
 *
 * @param <T> 能力接口类型(如 {@link EnergyStorage})
 */
@FunctionalInterface
public interface CapabilityProvider<T> {

    /**
     * @param context 查询上下文实例(方块实体 / 物品堆 / 实体;由查询侧传入)
     * @return 能力实现;不支持时返回 {@code null}
     */
    T get(Object context);
}
