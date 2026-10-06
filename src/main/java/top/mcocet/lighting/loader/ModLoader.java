package top.mcocet.lighting.loader;

import top.mcocet.lighting.attachment.AttachmentManager;
import top.mcocet.lighting.capability.CapabilityManager;
import top.mcocet.lighting.config.ConfigTracker;
import top.mcocet.lighting.discovery.ModCandidate;
import top.mcocet.lighting.discovery.ModDiscoverer;
import top.mcocet.lighting.event.DiscoveryFinishedEvent;
import top.mcocet.lighting.event.EventBus;
import top.mcocet.lighting.loading.DependencyValidator;
import top.mcocet.lighting.loading.ModLoadingIssue;
import top.mcocet.lighting.network.NetworkPayloadRegistry;
import top.mcocet.lighting.registry.RegistryManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 加载器内核:负责"发现 → 解析"的启动前段,并归集加载问题。
 * <p>
 * 构造、注册表初始化与全部阶段事件由 {@code top.mcocet.lighting.loading.ModLoadingPipeline}
 * 按 NeoForge {@code gatherAndInitializeMods} / {@code CommonModLoader} 的阶段模型编排;
 * 本类对应 Fabric Loader / Forge FML / NeoForge 加载器各自启动流程的"统一替代实现"——
 * 三家加载器自身的启动代码不会被运行,由 Lighting 履行其职责;
 * Mod 调用的 API 仍是各家的真实 API 代码(M4/M5 起随发行一起组装)。
 */
public final class ModLoader {

    private final LoaderConfig config;
    private final EventBus eventBus;
    private final RegistryManager registryManager;
    private final NetworkPayloadRegistry networkManager = new NetworkPayloadRegistry();
    private final CapabilityManager capabilityManager = new CapabilityManager();
    private final AttachmentManager attachmentManager = new AttachmentManager();
    private final ConfigTracker configTracker;
    private final ModDiscoverer discoverer;
    private final List<ModLoadingIssue> issues = new ArrayList<>();

    public ModLoader(LoaderConfig config) {
        this(config, new EventBus(), new RegistryManager());
    }

    public ModLoader(LoaderConfig config, EventBus eventBus, RegistryManager registryManager) {
        this.config = config;
        this.eventBus = eventBus;
        this.registryManager = registryManager;
        this.configTracker = new ConfigTracker(config);
        this.discoverer = new ModDiscoverer(config);
    }

    /**
     * 阶段一:发现——扫描 mods 目录,解析三种清单并归一化。
     * 由加载管线经 {@code ModWorkManager.runInitTask("Mod discovery")} 调用。
     */
    public List<ModCandidate> discover() {
        List<ModCandidate> candidates = discoverer.discover();
        discoverer.warnings().forEach(warning -> issues.add(ModLoadingIssue.warning(warning)));
        eventBus.post(new DiscoveryFinishedEvent(List.copyOf(candidates)));
        return candidates;
    }

    /**
     * 阶段二:解析——依据强制依赖与 BEFORE/AFTER 声明做拓扑排序(FML ModSorter 语义),
     * 随后执行版本范围校验(DependencyValidator);全部问题归集到 {@link #issues()}。
     */
    public List<ModCandidate> resolve(List<ModCandidate> candidates) {
        List<String> sorterWarnings = new ArrayList<>();
        List<ModCandidate> ordered = DependencySorter.sort(candidates, sorterWarnings);
        sorterWarnings.forEach(warning -> issues.add(ModLoadingIssue.warning(warning)));
        issues.addAll(DependencyValidator.validate(ordered));
        return ordered;
    }

    /** 累积的加载问题(警告与致命);由加载管线统一上报或聚合抛出 */
    public List<ModLoadingIssue> issues() {
        return List.copyOf(issues);
    }

    /** 追加一个加载问题(Mixin 接入层等在流水线外产生的问题也汇聚于此) */
    public synchronized void addIssue(ModLoadingIssue issue) {
        issues.add(issue);
    }

    public LoaderConfig config() {
        return config;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public RegistryManager registryManager() {
        return registryManager;
    }

    /** 网络载荷注册中心(收尾阶段上锁) */
    public NetworkPayloadRegistry networkManager() {
        return networkManager;
    }

    /** 能力注册中心(Registration events 阶段开放) */
    public CapabilityManager capabilityManager() {
        return capabilityManager;
    }

    /** 数据附件注册中心(Registration events 阶段开放) */
    public AttachmentManager attachmentManager() {
        return attachmentManager;
    }

    /** Mod 配置加载器(构造之后由管线触发加载) */
    public ConfigTracker configTracker() {
        return configTracker;
    }
}
