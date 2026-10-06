package top.mcocet.lighting.loading;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.api.Dist;
import top.mcocet.api.LightingDeferredRegister;
import top.mcocet.lighting.bridge.EntrypointDispatcher;
import top.mcocet.lighting.discovery.ModCandidate;
import top.mcocet.lighting.event.CancellableEvent;
import top.mcocet.lighting.event.ClientSetupEvent;
import top.mcocet.lighting.event.CommonSetupEvent;
import top.mcocet.lighting.event.DataPackRegistryEvent;
import top.mcocet.lighting.event.DedicatedServerSetupEvent;
import top.mcocet.lighting.event.EventBus;
import top.mcocet.lighting.event.InterModEnqueueEvent;
import top.mcocet.lighting.event.InterModProcessEvent;
import top.mcocet.lighting.event.LoadCompleteEvent;
import top.mcocet.lighting.event.ModConstructEvent;
import top.mcocet.lighting.event.ModLoadedEvent;
import top.mcocet.lighting.event.ModLoadingEvent;
import top.mcocet.lighting.event.ModifyRegistriesEvent;
import top.mcocet.lighting.event.NewRegistryEvent;
import top.mcocet.lighting.event.RegisterCapabilitiesEvent;
import top.mcocet.lighting.event.RegisterEvent;
import top.mcocet.lighting.loader.LoadResult;
import top.mcocet.lighting.loader.ModContainer;
import top.mcocet.lighting.loader.ModLoader;
import top.mcocet.lighting.registry.DataMapLoader;
import top.mcocet.lighting.registry.TagLoader;
import top.mcocet.lighting.resource.ModResources;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 加载阶段编排器——NeoForge {@code FMLLoader} + {@code CommonModLoader} 阶段模型的 Lighting 移植:
 * <ol>
 *   <li>发现({@link #discover()}):扫描 Mod 候选,为组装类路径提供输入</li>
 *   <li>解析({@link #initialize}):依赖拓扑排序 + 版本范围校验,致命问题聚合抛出</li>
 *   <li>构造({@link #initialize}):并行调度三生态入口点(对应 FML 的并行 Mod 构造)</li>
 *   <li>注册表初始化({@link #initialize}):NewRegistryEvent → RegisterEvent → 冻结(对应 GameData 序列)</li>
 *   <li>收尾({@link #finish}):Common setup → Sided setup → IMC 入队/处理 → 加载完成(对应 CommonModLoader.load)</li>
 * </ol>
 * 可取消的阶段事件(Common/Client/DedicatedServer setup)保持顺序派发,保证"优先级 + 取消即中止"语义的确定性;
 * IMC 与加载完成事件并行派发,与 FML {@code dispatchParallelEvent} 一致。
 */
public final class ModLoadingPipeline {

    private static final Logger LOGGER = LogManager.getLogger(ModLoadingPipeline.class);

    /** 进度总阶段数(发现/解析/构造/注册表/配置/数据扫描 + 收尾 6 段) */
    private static final int TOTAL_STAGES = 12;

    private final ModLoader loader;
    private final EventBus eventBus;
    private final LoadingProgress progress;
    private final ModWorkManager work = new ModWorkManager();
    private final EntrypointDispatcher dispatcher = new EntrypointDispatcher();
    private final ModResources modResources = new ModResources();
    private final long startMillis = System.currentTimeMillis();

    private TagLoader.Result tagResult;
    private DataMapLoader.Result dataMapResult;

    public ModLoadingPipeline(ModLoader loader) {
        this(loader, LoadingProgress.console());
    }

    public ModLoadingPipeline(ModLoader loader, LoadingProgress progress) {
        this.loader = loader;
        this.eventBus = loader.eventBus();
        this.progress = progress;
    }

    /** 阶段一:Mod 发现(扫描 mods 目录,解析三种清单并归一化) */
    public List<ModCandidate> discover() {
        List<ModCandidate> candidates = work.runInitTask("Mod discovery", loader::discover);
        progress.stage("Mod discovery", 1, TOTAL_STAGES);
        return candidates;
    }

    /**
     * 阶段二至四:解析 → 构造 → 注册表初始化。
     *
     * @param candidates         发现阶段产出的候选 Mod
     * @param gameClassLoader    游戏侧类加载器(构造入口点所需)
     * @param beforeConstruction 构造开始前的接线回调(启动入口在此安装 API 门面,使 Mod 构造时即可访问加载器服务)
     * @throws ModLoadingException 存在致命加载问题时抛出(全部致命问题已聚合在异常内)
     */
    public LoadResult initialize(List<ModCandidate> candidates, ClassLoader gameClassLoader,
                                 Consumer<List<ModContainer>> beforeConstruction) {
        List<ModCandidate> ordered = work.runInitTask("Mod resolution", () -> loader.resolve(candidates));
        progress.stage("Mod resolution", 2, TOTAL_STAGES);
        List<ModLoadingIssue> fatal = loader.issues().stream().filter(ModLoadingIssue::fatal).toList();
        if (!fatal.isEmpty()) {
            throw new ModLoadingException(fatal);
        }

        List<ModContainer> mods = new ArrayList<>(ordered.size());
        for (ModCandidate candidate : ordered) {
            mods.add(new ModContainer(candidate));
        }

        beforeConstruction.accept(List.copyOf(mods));
        work.runInitTask("Mod construction", () -> construct(ordered, mods, gameClassLoader));
        progress.stage("Mod construction", 3, TOTAL_STAGES);
        work.runInitTask("Registry initialization", () -> initializeRegistries(mods));
        progress.stage("Registry initialization", 4, TOTAL_STAGES);
        work.runInitTask("Config loading", () -> loader.configTracker().loadAll(mods, eventBus));
        progress.stage("Config loading", 5, TOTAL_STAGES);
        work.runInitTask("Data file scanning", () -> scanDataFiles(mods));
        progress.stage("Data file scanning", 6, TOTAL_STAGES);

        eventBus.post(new ModConstructEvent(List.copyOf(mods), elapsedMillis()));
        return new LoadResult(List.copyOf(mods), List.copyOf(loader.issues()));
    }

    /**
     * 阶段五:收尾阶段序(对应 CommonModLoader.load)。
     * 可取消阶段被取消即立即返回 false,调用方应中止移交(游戏不启动)。
     *
     * @param dist 运行侧:CLIENT 派发 ClientSetupEvent,SERVER 派发 DedicatedServerSetupEvent
     * @return true 表示全部阶段完成;false 表示被 Mod 取消
     */
    public boolean finish(List<ModContainer> mods, Dist dist) {
        LOGGER.info("========== Finish phase: common setup -> sided setup -> registration events -> IMC -> complete ==========");
        try {
            if (!postSetupStage("Common setup", new CommonSetupEvent(mods))) {
                return false;
            }
            progress.stage("Common setup", 7, TOTAL_STAGES);
            CancellableEvent sided = dist == Dist.SERVER
                    ? new DedicatedServerSetupEvent(mods)
                    : new ClientSetupEvent(mods);
            if (!postSetupStage("Sided setup (%s)".formatted(
                    dist == Dist.SERVER ? "dedicated server" : "client"), sided)) {
                return false;
            }
            progress.stage("Sided setup", 8, TOTAL_STAGES);
            work.runInitTask("Registration events", () -> registrationEvents(mods));
            progress.stage("Registration events", 9, TOTAL_STAGES);
            runParallelEvent("Enqueue IMC", new InterModEnqueueEvent(mods));
            progress.stage("Enqueue IMC", 10, TOTAL_STAGES);
            runParallelEvent("Process IMC", new InterModProcessEvent(mods));
            progress.stage("Process IMC", 11, TOTAL_STAGES);
            runParallelEvent("Complete loading of %d mod(s)".formatted(mods.size()),
                    new LoadCompleteEvent(mods, elapsedMillis()));
            progress.stage("Complete loading", 12, TOTAL_STAGES);
            int locked = loader.networkManager().lock();
            LOGGER.info("Network registry locked ({} payload(s))", locked);
            LOGGER.info("Loading pipeline finished in {} ms", elapsedMillis());
            return true;
        } finally {
            work.close();
        }
    }

    /** 并行构造:三生态入口点各占一个并行任务,单个 Mod 失败不影响其它 Mod */
    private void construct(List<ModCandidate> ordered, List<ModContainer> mods, ClassLoader gameClassLoader) {
        dispatcher.probeEcosystemApis(gameClassLoader);
        for (int i = 0; i < ordered.size(); i++) {
            eventBus.post(new ModLoadingEvent(ordered.get(i), i + 1, ordered.size()));
        }
        dispatcher.dispatch(mods, gameClassLoader, work.parallelExecutor());
        for (ModContainer mod : mods) {
            // 并行构造不逐 Mod 计时,统一标记为已加载
            mod.markLoaded(0);
            eventBus.post(new ModLoadedEvent(mod));
        }
    }

    /** 注册表初始化序列(对应 NeoForge GameData:NewRegistry → Register → 延迟注册写入 → DataPack 声明 → ModifyRegistries → Freeze+快照) */
    private void initializeRegistries(List<ModContainer> mods) {
        eventBus.post(new NewRegistryEvent(List.copyOf(mods), loader.registryManager()));
        eventBus.post(new RegisterEvent(List.copyOf(mods)));
        int flushed = LightingDeferredRegister.flushAll(loader.registryManager()::register);
        if (flushed > 0) {
            LOGGER.info("Deferred registrations flushed: {} entrie(s)", flushed);
        }
        DataPackRegistryEvent dataPackEvent = new DataPackRegistryEvent(List.copyOf(mods), loader.registryManager());
        eventBus.post(dataPackEvent);
        dataPackEvent.completePending();
        eventBus.post(new ModifyRegistriesEvent(List.copyOf(mods), loader.registryManager()));
        loader.registryManager().freeze();
        LOGGER.info("Registry snapshot: {}; world-data registry(ies): {}",
                loader.registryManager().snapshot().summary(),
                loader.registryManager().worldRegistries().size());
    }

    /** 收尾阶段的 Registration events(对应 FML RegistrationEvents.init:能力/附件注册等) */
    private void registrationEvents(List<ModContainer> mods) {
        eventBus.post(new RegisterCapabilitiesEvent(List.copyOf(mods),
                loader.capabilityManager(), loader.attachmentManager()));
        loader.capabilityManager().lock();
        loader.attachmentManager().lock();
        LOGGER.info("Capabilities declared: {} type(s), {} provider(s)",
                loader.capabilityManager().capabilityNames().size(),
                loader.capabilityManager().totalProviderCount());
        LOGGER.info("Attachments declared: {} type(s)", loader.attachmentManager().typeCount());
    }

    /** 数据文件扫描:标签 → 数据映射 → Mod 资源/语言 */
    private void scanDataFiles(List<ModContainer> mods) {
        tagResult = TagLoader.load(mods);
        dataMapResult = DataMapLoader.load(mods);
        modResources.scan(mods);
    }

    /** 标签加载结果(供游戏侧 Tag 容器消费) */
    public TagLoader.Result tags() {
        return tagResult;
    }

    /** 数据映射加载结果(供注册表条目消费) */
    public DataMapLoader.Result dataMaps() {
        return dataMapResult;
    }

    /** Mod 资源(资源包来源与内置语言表) */
    public ModResources resources() {
        return modResources;
    }

    /** 可取消阶段事件:顺序派发保证优先级与"取消即中止"语义 */
    private boolean postSetupStage(String name, CancellableEvent event) {
        long start = System.currentTimeMillis();
        eventBus.post(event);
        LOGGER.info("{} stage finished in {} ms", name, System.currentTimeMillis() - start);
        if (event.isCancelled()) {
            LOGGER.warn("{} was cancelled by a mod listener", name);
            return false;
        }
        return true;
    }

    /** 并行阶段事件:监听器各占一个并行任务(对应 FML 的 dispatchParallelEvent) */
    private <T> void runParallelEvent(String name, T event) {
        long start = System.currentTimeMillis();
        eventBus.postParallel(event, work.parallelExecutor());
        LOGGER.info("{} stage finished in {} ms", name, System.currentTimeMillis() - start);
    }

    private long elapsedMillis() {
        return System.currentTimeMillis() - startMillis;
    }
}
