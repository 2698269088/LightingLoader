package top.mcocet.api.event;

/**
 * 可取消事件契约(Forge 语义):监听器调用 {@link #cancel()} 表达否决,
 * 事件总线随后跳过同级与更低优先级的剩余监听器。
 * <p>
 * 内核的加载器生命周期事件与游戏运行时事件共用本契约,
 * 因此 Mod 侧只需面对一种取消心智。
 */
public interface CancellableEvent {

    /** 事件是否已被取消 */
    boolean isCancelled();

    /** 取消事件(等同 {@code setCancelled(true)}) */
    default void cancel() {
        setCancelled(true);
    }

    /** 设置取消状态 */
    void setCancelled(boolean cancelled);
}
