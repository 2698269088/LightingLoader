package top.mcocet.api.capability;

/**
 * 流体槽能力(Forge 语义):以"罐(tank)"为单位的流体容器契约。
 * <p>
 * 方法中的 {@code Object} 参数/返回值实为游戏侧的流体堆实例
 * ({@code FluidStack})——API 层不能引用游戏类型(跨类加载器约束);
 * 实现侧(游戏侧类加载器中的 Mod 代码)可安全做类型转换。
 * 契约约定:永远不返回 {@code null},空罐返回空堆。
 */
public interface FluidHandler {

    /** 罐总数 */
    int getTanks();

    /** 指定罐中的流体(空罐返回空堆) */
    Object getFluidInTank(int tank);

    /** 指定罐的容量(毫桶) */
    int getTankCapacity(int tank);

    /** 指定罐是否接受给定流体堆 */
    boolean isFluidValid(int tank, Object stack);

    /**
     * 向指定罐注入流体,返回实际注入量(毫桶);
     * {@code simulate} 为 true 时不改变状态。
     */
    int fill(int tank, Object resource, boolean simulate);

    /**
     * 从指定罐排出至多 {@code amount} 毫桶流体,返回排出的堆;
     * {@code simulate} 为 true 时不改变状态。
     */
    Object drain(int tank, int amount, boolean simulate);
}
