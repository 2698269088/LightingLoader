package top.mcocet.lighting.bridge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.lighting.loader.LoaderType;
import top.mcocet.lighting.loader.ModContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 三生态入口调度器:把每个已加载 Mod 的入口点交给对应生态的
 * {@link EcosystemBridge},统一记录日志并汇总结果。
 * <p>
 * 调度时机:游戏侧类加载器就绪之后——入口类可能引用游戏类与生态 API,
 * 必须经 {@code GameClassLoader} 加载,因此由启动入口在建好类路径后调用。
 */
public final class EntrypointDispatcher {

    private static final Logger LOGGER = LogManager.getLogger(EntrypointDispatcher.class);

    private final Map<LoaderType, EcosystemBridge> bridges = new HashMap<>();

    public EntrypointDispatcher() {
        register(new FabricBridge());
        register(new AnnotationModBridge(LoaderType.FORGE, "Forge",
                "Lnet/minecraftforge/fml/common/Mod;", "net.minecraftforge.fml.common.Mod"));
        register(new AnnotationModBridge(LoaderType.NEOFORGE, "NeoForge",
                "Lnet/neoforged/fml/common/Mod;", "net.neoforged.fml.common.Mod"));
    }

    private void register(EcosystemBridge bridge) {
        bridges.put(bridge.loaderType(), bridge);
    }

    /** 探测各生态 API 是否已组装进类路径;仅日志上报,不影响流程 */
    public void probeEcosystemApis(ClassLoader loader) {
        for (EcosystemBridge bridge : bridges.values()) {
            if (isClassPresent(bridge.apiProbeClass(), loader)) {
                LOGGER.info("Ecosystem API ready: {} ({})",
                        bridge.displayName(), bridge.apiProbeClass());
            } else {
                LOGGER.warn("Ecosystem API not present: {} ({} missing; place the loader artifacts into .lighting/ecosystem to run this ecosystem natively)",
                        bridge.displayName(), bridge.apiProbeClass());
            }
        }
    }

    /**
     * 并行调度全部 Mod 的入口点(对应 FML 的并行 Mod 构造阶段);
     * 每个 Mod 各占一个并行任务,单个 Mod 失败不影响其它 Mod。
     *
     * @return 全部调度结果(按 Mod id 排序,便于确定性汇总)
     */
    public List<BridgeOutcome> dispatch(List<ModContainer> mods, ClassLoader loader, ExecutorService pool) {
        List<BridgeOutcome> outcomes = Collections.synchronizedList(new ArrayList<>(mods.size()));
        List<Future<?>> futures = new ArrayList<>(mods.size());
        for (ModContainer mod : mods) {
            futures.add(pool.submit(() -> {
                EcosystemBridge bridge = bridges.get(mod.loaderType());
                if (bridge == null) {
                    outcomes.add(new BridgeOutcome(mod.id(), "unknown", false,
                            "no entrypoint bridge for ecosystem " + mod.loaderType()));
                    return;
                }
                // 语言适配器选择(对应 FML ILanguageLoader:javafml / fabric / 其它声明语言)
                LanguageAdapters.Selection selection = LanguageAdapters.select(
                        mod, bridges.get(LoaderType.FABRIC), bridge);
                LOGGER.info("Language adapter: {} -> {}", mod.id(), selection.adapterId());
                if (!selection.supported()) {
                    outcomes.add(new BridgeOutcome(mod.id(), bridge.displayName(), false,
                            "language adapter '" + selection.adapterId() + "' is not supported in this build"));
                    return;
                }
                try {
                    outcomes.add(bridge.schedule(mod, loader));
                } catch (RuntimeException | LinkageError e) {
                    outcomes.add(new BridgeOutcome(mod.id(), bridge.displayName(), false, "dispatch error: " + e));
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
                // 单个任务异常已在其内部转换为失败结果
            }
        }

        List<BridgeOutcome> sorted = new ArrayList<>(outcomes);
        sorted.sort(Comparator.comparing(BridgeOutcome::modId));
        int invoked = 0;
        for (BridgeOutcome outcome : sorted) {
            if (outcome.invoked()) {
                invoked++;
                LOGGER.info("Entrypoints scheduled: {} [{}] {}",
                        outcome.modId(), outcome.ecosystem(), outcome.message());
            } else {
                LOGGER.warn("Entrypoints skipped: {} [{}] {}",
                        outcome.modId(), outcome.ecosystem(), outcome.message());
            }
        }
        LOGGER.info("Entrypoint dispatch complete: {}/{} mod(s) invoked entrypoints", invoked, mods.size());
        return sorted;
    }

    private static boolean isClassPresent(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
