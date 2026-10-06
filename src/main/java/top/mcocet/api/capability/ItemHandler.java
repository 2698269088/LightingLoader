package top.mcocet.api.capability;

/**
 * 物品槽能力(Forge 语义):以"槽位"为单位的物品容器契约。
 * <p>
 * 方法中的 {@code Object} 参数/返回值实为游戏侧的物品堆实例
 * ({@code ItemStack})——API 层由父类加载器定义,不能引用游戏类型
 * (跨类加载器约束);实现侧(游戏侧类加载器中的 Mod 代码)可安全
 * 做类型转换。契约约定:永远不返回 {@code null},空槽返回空堆。
 */
public interface ItemHandler {

    /** 槽位总数 */
    int getSlots();

    /** 指定槽位的物品堆(空槽返回空堆) */
    Object getStackInSlot(int slot);

    /**
     * 向指定槽位插入物品,返回未能放入的剩余部分
     * (全部放入时返回空堆);{@code simulate} 为 true 时不改变状态。
     */
    Object insertItem(int slot, Object stack, boolean simulate);

    /**
     * 从指定槽位提取至多 {@code amount} 个物品,
     * 返回实际提取的堆;{@code simulate} 为 true 时不改变状态。
     */
    Object extractItem(int slot, int amount, boolean simulate);

    /** 指定槽位是否接受给定物品堆 */
    boolean isItemValid(int slot, Object stack);

    /** 指定槽位的容量上限(默认 64) */
    default int getSlotLimit(int slot) {
        return 64;
    }
}
