package top.mcocet.lighting.transform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import top.mcocet.lighting.attachment.AttachmentManager;
import top.mcocet.lighting.event.EventBus;
import top.mcocet.lighting.event.GameMainEnterEvent;
import top.mcocet.lighting.event.VanillaRegistryBootstrapEvent;
import top.mcocet.lighting.event.VanillaRegistryReadyEvent;
import top.mcocet.lighting.handoff.GameSideBridge;
import top.mcocet.lighting.handoff.RegistryTakeoverReport;
import top.mcocet.lighting.network.NetworkPayloadRegistry;
import top.mcocet.lighting.resource.ModResources;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 注入钩子宿主。
 * <p>
 * 被 {@link MainClassHookTransformer} 与
 * {@link VanillaRegistryHookTransformer} 注入的字节码会在游戏主类
 * {@code main} 开始时、{@code BuiltInRegistries.bootStrap()} 的
 * 冻结前/就绪后调用本类的方法。因为被转换的游戏类与本类处于不同的
 * 类加载器,此类必须保持极简、无重初始化逻辑,只做"转发到事件总线 /
 * 游戏侧接管层"这一件事(游戏侧接管层经 {@link #bindGameSide} 注入,
 * 由 {@code top.mcocet.lighting.game.GameSideLifecycle} 实现)。
 */
public final class LightingHooks {

    private static final Logger LOGGER = LogManager.getLogger(LightingHooks.class);

    /** 空接管报告:游戏侧接管层未绑定或接管失败时的回退值 */
    private static final RegistryTakeoverReport EMPTY_REPORT = new RegistryTakeoverReport(0, 0, 0);

    /** "注册表就绪"一次性守卫:多个 RETURN 注入点重复触发时去重 */
    private static final AtomicBoolean REGISTRY_READY = new AtomicBoolean();

    private static volatile EventBus eventBus;
    private static volatile GameSideBridge gameSide;
    private static volatile NetworkPayloadRegistry networkRegistry;
    private static volatile AttachmentManager attachmentManager;
    private static volatile ModResources modResources;
    private static volatile RegistryTakeoverReport lastTakeoverReport = EMPTY_REPORT;
    private static volatile long lastTakeoverMillis;

    private LightingHooks() {
    }

    /** 由启动入口在构建完总线后绑定 */
    public static void bindEventBus(EventBus bus) {
        eventBus = bus;
    }

    /** 由启动入口绑定游戏侧接管层(lighting.game 包,由游戏侧加载器定义) */
    public static void bindGameSide(GameSideBridge bridge) {
        gameSide = bridge;
    }

    /** 由启动入口绑定网络载荷注册中心(内核实例,供游戏侧收发桥构造/派发载荷) */
    public static void bindNetworkRegistry(NetworkPayloadRegistry registry) {
        networkRegistry = registry;
    }

    /** 网络载荷注册中心;启动入口未绑定时返回 null */
    public static NetworkPayloadRegistry networkRegistry() {
        return networkRegistry;
    }

    /** 由启动入口绑定数据附件注册中心(内核实例,供游戏侧持久化桥读写) */
    public static void bindAttachmentManager(AttachmentManager manager) {
        attachmentManager = manager;
    }

    /** 数据附件注册中心;启动入口未绑定时返回 null */
    public static AttachmentManager attachmentManager() {
        return attachmentManager;
    }

    /** 由启动入口绑定 Mod 资源扫描结果(供游戏侧资源包挂载桥消费) */
    public static void bindModResources(ModResources resources) {
        modResources = resources;
    }

    /** Mod 资源扫描结果(资源包来源与语言表);未绑定时返回 null */
    public static ModResources modResources() {
        return modResources;
    }

    /**
     * 派发游戏运行时事件(由游戏侧注入的字节码经 runtime 桥调用)。
     * 事件对象在 api.event 包中定义(两侧共享),此处仅转发到总线;
     * 总线未绑定时仅记录日志,不抛异常,避免影响游戏主循环。
     */
    public static void postRuntimeEvent(Object event) {
        EventBus bus = eventBus;
        if (bus == null) {
            LOGGER.warn("Runtime event {} arrived but the event bus is not bound", event.getClass().getName());
            return;
        }
        bus.post(event);
    }

    /** 游戏主类 main 进入钩子;由注入的字节码调用 */
    public static void onGameMainEnter() {
        EventBus bus = eventBus;
        if (bus == null) {
            LOGGER.warn("Game main entered but the event bus is not bound; startup stages were not tracked");
            return;
        }
        bus.post(new GameMainEnterEvent(System.currentTimeMillis()));
        LOGGER.info("Startup pipeline handshake complete; the game now runs under Lighting control");
    }

    /**
     * 原版注册表冻结前钩子:{@code BuiltInRegistries.bootStrap()} 的
     * {@code freeze()} 调用前触发。委托游戏侧接管层把注册中心中的
     * 方块/实体描述符实例化为真实对象并写入原版注册表(唯一合法窗口)。
     */
    public static void onVanillaRegistryBootstrap() {
        long start = System.nanoTime();
        GameSideBridge bridge = gameSide;
        RegistryTakeoverReport report = EMPTY_REPORT;
        if (bridge == null) {
            LOGGER.warn("Vanilla registry bootstrap reached but the game-side takeover layer is not bound; "
                    + "custom blocks/entities will not be injected");
        } else {
            try {
                report = bridge.onVanillaRegistryBootstrap();
                if (report == null) {
                    report = EMPTY_REPORT;
                }
            } catch (Throwable t) {
                LOGGER.error("Game-side registry takeover failed: {}", t.toString());
                report = EMPTY_REPORT;
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        lastTakeoverReport = report;
        lastTakeoverMillis = millis;
        EventBus bus = eventBus;
        if (bus != null) {
            bus.post(new VanillaRegistryBootstrapEvent(System.currentTimeMillis()));
        }
        LOGGER.info("Vanilla registry takeover: {} block(s), {} entity type(s), {} failure(s) in {} ms",
                report.blocks(), report.entities(), report.failures(), millis);
    }

    /**
     * 原版注册表就绪钩子:{@code bootStrap()} 的 RETURN 前触发,此时
     * 注册表已冻结并通过校验。多次触发由一次性守卫去重;委托游戏侧
     * 完成 NBT/位置桥自检。
     */
    public static void onVanillaRegistryReady() {
        if (!REGISTRY_READY.compareAndSet(false, true)) {
            return;
        }
        GameSideBridge bridge = gameSide;
        if (bridge != null) {
            try {
                bridge.onVanillaRegistryReady();
            } catch (Throwable t) {
                LOGGER.error("Game-side registry-ready callback failed: {}", t.toString());
            }
        }
        EventBus bus = eventBus;
        if (bus != null) {
            bus.post(new VanillaRegistryReadyEvent(lastTakeoverReport, lastTakeoverMillis));
        }
        LOGGER.info("Vanilla registry ready: the takeover layer is active for this session");
    }
}
