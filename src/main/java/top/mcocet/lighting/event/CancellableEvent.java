package top.mcocet.lighting.event;

/**
 * 可取消事件基类(Forge 语义):监听器可调用 {@link #cancel()} 表达否决,
 * 事件总线随后跳过同级与更低优先级剩余监听器的派发。
 * <p>
 * 实现 {@link top.mcocet.api.event.CancellableEvent} 契约,使内核生命周期
 * 事件与游戏运行时事件共用同一取消检查。
 */
public abstract class CancellableEvent implements top.mcocet.api.event.CancellableEvent {

    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
