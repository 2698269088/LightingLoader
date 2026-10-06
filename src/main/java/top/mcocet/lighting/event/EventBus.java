package top.mcocet.lighting.event;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.api.EventPriority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * 统一事件总线(Forge 语义):
 * <ul>
 *   <li>监听优先级:{@link EventPriority},HIGHEST 最先调用,同级按注册顺序;</li>
 *   <li>取消机制:事件实现 {@link top.mcocet.api.event.CancellableEvent} 后,
 *       任一监听器取消即跳过同级与更低优先级的剩余监听器;</li>
 *   <li>异常隔离:单个监听器抛异常仅记录日志,不影响其余派发。</li>
 * </ul>
 * 同时承载 Fabric 风格的"回调注册"心智——不带优先级注册等价于 NORMAL。
 */
public final class EventBus {

    private static final Logger LOGGER = LogManager.getLogger(EventBus.class);

    private static final Comparator<Listener<?>> BY_PRIORITY =
            Comparator.comparingInt(listener -> listener.priority().ordinal());

    private final Map<Class<?>, List<Listener<?>>> listeners = new ConcurrentHashMap<>();

    /** 注册监听器(默认 NORMAL 优先级);同一事件类型同级按注册顺序调用 */
    public <T> void register(Class<T> eventType, Consumer<T> listener) {
        register(eventType, EventPriority.NORMAL, listener);
    }

    /** 注册带优先级的监听器 */
    public <T> void register(Class<T> eventType, EventPriority priority, Consumer<T> listener) {
        List<Listener<?>> forType = listeners.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>());
        forType.add(new Listener<>(priority, listener));
        forType.sort(BY_PRIORITY);
    }

    /** 发布事件,同步派发给该精确类型的所有监听器;事件被取消时中止后续派发 */
    @SuppressWarnings("unchecked")
    public <T> T post(T event) {
        List<Listener<?>> forType = listeners.get(event.getClass());
        if (forType != null) {
            for (Listener<?> listener : forType) {
                if (event instanceof top.mcocet.api.event.CancellableEvent cancellable && cancellable.isCancelled()) {
                    break;
                }
                try {
                    ((Listener<T>) listener).consumer().accept(event);
                } catch (RuntimeException e) {
                    LOGGER.error("Event listener for {} failed: {}",
                            event.getClass().getSimpleName(), e.toString());
                }
            }
        }
        return event;
    }

    /**
     * 并行派发(对应 FML 的 {@code ModLoader.dispatchParallelEvent}):
     * 每个监听器各提交一个任务到给定执行器,全部完成后返回。
     * 提交任务前检查取消状态,事件被取消后不再提交后续监听器;
     * 单监听器异常仅记录日志,不影响其它监听器与整体流程。
     */
    @SuppressWarnings("unchecked")
    public <T> T postParallel(T event, ExecutorService pool) {
        List<Listener<?>> forType = listeners.get(event.getClass());
        if (forType == null || forType.isEmpty()) {
            return event;
        }
        List<Future<?>> futures = new ArrayList<>(forType.size());
        for (Listener<?> listener : forType) {
            if (event instanceof top.mcocet.api.event.CancellableEvent cancellable && cancellable.isCancelled()) {
                break;
            }
            futures.add(pool.submit(() -> {
                try {
                    ((Listener<T>) listener).consumer().accept(event);
                } catch (RuntimeException e) {
                    LOGGER.error("Event listener for {} failed: {}",
                            event.getClass().getSimpleName(), e.toString());
                }
            }));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException ignored) {
                // 监听器异常已在任务内记录
            }
        }
        return event;
    }

    private record Listener<T>(EventPriority priority, Consumer<T> consumer) {
    }
}
