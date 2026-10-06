package top.mcocet.api.capability;

/**
 * 能量存储能力(Forge 语义):全部方法以"能量单位"为计量。
 * <p>
 * 由 Mod 在方块实体/物品堆/实体上实现并注册到
 * {@link LightingCapabilities};API 层不引用任何游戏类型,
 * 实现侧(游戏侧类加载器)可直接使用原版/模组能量类型换算。
 */
public interface EnergyStorage {

    /** 当前存储的能量 */
    int getEnergyStored();

    /** 最大可存储能量 */
    int getMaxEnergyStored();

    /** 是否允许提取 */
    boolean canExtract();

    /** 是否允许接收 */
    boolean canReceive();

    /** 接收能量,返回实际接收量;{@code simulate} 为 true 时不改变状态 */
    int receiveEnergy(int toReceive, boolean simulate);

    /** 提取能量,返回实际提取量;{@code simulate} 为 true 时不改变状态 */
    int extractEnergy(int toExtract, boolean simulate);

    /** 是否已满 */
    default boolean isFull() {
        return getEnergyStored() >= getMaxEnergyStored();
    }

    /** 是否已空 */
    default boolean isEmpty() {
        return getEnergyStored() <= 0;
    }
}
