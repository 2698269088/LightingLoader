package top.mcocet.api;

import java.util.function.Consumer;

/**
 * 统一事件总线视图:承载 Forge 风格的"订阅 + 发布"心智,
 * 同时适配 Fabric 风格的"回调注册"。
 * <p>
 * 事件语义(与加载器内核一致):
 * <ul>
 *   <li>监听优先级 {@link EventPriority}:HIGHEST 最先调用,同级按注册顺序;</li>
 *   <li>可取消事件(如加载器生命周期事件):任一监听器取消后,
 *       总线跳过同级与更低优先级的剩余监听器。</li>
 * </ul>
 */
public interface LightingEventBus {

    /** 注册带优先级的监听器 */
    <T> void subscribe(Class<T> eventType, EventPriority priority, Consumer<T> listener);

    /** 注册监听器(默认 NORMAL 优先级) */
    default <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        subscribe(eventType, EventPriority.NORMAL, listener);
    }

    /** 发布事件,同步派发给该精确类型的所有监听器 */
    <T> T post(T event);
}
