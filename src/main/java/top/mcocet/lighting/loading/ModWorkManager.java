package top.mcocet.lighting.loading;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 加载工作管理器——移植自 NeoForge {@code ModWorkManager} 的同步/并行双执行器模型。
 * <p>
 * NeoForge 的加载流程把每个阶段(阶段名 + 任务)交给工作管理器执行:
 * 串行阶段(依赖解析、注册表初始化)在单线程同步执行器上按序运行,
 * 互不相干的并行阶段(Mod 构造、并行事件派发)交给并行执行器,
 * 主线程通过 {@code runInitTask} 等待阶段完成后再进入下一阶段。
 * 加载屏的周期 tick(FML 的 periodicTask)在本骨架中由阶段日志替代。
 */
public final class ModWorkManager implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(ModWorkManager.class);

    private final ExecutorService syncExecutor;
    private final ExecutorService parallelExecutor;
    private final AtomicInteger workerIds = new AtomicInteger();

    public ModWorkManager() {
        int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        this.syncExecutor = Executors.newSingleThreadExecutor(
                runnable -> newThread(runnable, "LightingSyncThread"));
        this.parallelExecutor = Executors.newFixedThreadPool(parallelism,
                runnable -> newThread(runnable, "LightingParallelWorker-" + workerIds.incrementAndGet()));
        LOGGER.info("Work manager ready: parallel workers = {}", parallelism);
    }

    /** 工作线程设为守护线程:致命中止路径不会因线程未回收而挂住 JVM */
    private static Thread newThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    public ExecutorService syncExecutor() {
        return syncExecutor;
    }

    public ExecutorService parallelExecutor() {
        return parallelExecutor;
    }

    /** 在同步执行器上运行具名初始化任务并等待完成,记录耗时(对应 FML 的 runInitTask) */
    public <T> T runInitTask(String name, Supplier<T> task) {
        long start = System.currentTimeMillis();
        LOGGER.info("{} started", name);
        T result = await(syncExecutor.submit(task::get), name);
        LOGGER.info("{} finished in {} ms", name, System.currentTimeMillis() - start);
        return result;
    }

    public void runInitTask(String name, Runnable task) {
        runInitTask(name, () -> {
            task.run();
            return null;
        });
    }

    private <T> T await(Future<T> future, String name) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for task: " + name, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Task failed: " + name, cause);
        }
    }

    /** 加载流程结束:回收两个执行器 */
    @Override
    public void close() {
        syncExecutor.shutdown();
        parallelExecutor.shutdown();
    }
}
