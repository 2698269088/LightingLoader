package top.mcocet.api;

import top.mcocet.api.attachment.LightingAttachments;
import top.mcocet.api.capability.LightingCapabilities;

import java.util.List;
import java.util.Optional;

/**
 * Lighting 统一加载器的对外主 API 门面。
 * <p>
 * 三种生态(Fabric/Forge/NeoForge)的 Mod 都可以通过本类访问加载器服务,
 * 无需依赖任何一家加载器的具体实现——这是"统一加载器"语义的入口点:
 * <pre>{@code
 * if (LightingLoader.isAvailable() && LightingLoader.isModLoaded("other_mod")) {
 *     LightingLoader.mod("other_mod").ifPresent(mod -> ...);
 * }
 * }</pre>
 * 门面本身不持有状态;具体服务由加载器内核在启动阶段
 * {@link #install(LightingRuntime) 安装}。Mod 代码应在生命周期回调
 * (入口点)内部使用本类,而非静态初始化块。
 */
public final class LightingLoader {

    private static volatile LightingRuntime runtime;

    private LightingLoader() {
    }

    /** 由加载器内核安装运行时实现;重复安装视为错误 */
    public static void install(LightingRuntime implementation) {
        if (runtime != null) {
            throw new IllegalStateException("Lighting API is already installed");
        }
        runtime = implementation;
    }

    /** API 是否已就绪(内核安装后为 true) */
    public static boolean isAvailable() {
        return runtime != null;
    }

    public static String loaderName() {
        return runtime().loaderName();
    }

    public static String loaderVersion() {
        return runtime().loaderVersion();
    }

    /** 目标 Minecraft 版本(如 "26.1.2") */
    public static String gameVersion() {
        return runtime().gameVersion();
    }

    /** 当前运行侧(CLIENT / SERVER) */
    public static Dist dist() {
        return runtime().dist();
    }

    /** 全部已加载 Mod 的只读视图 */
    public static List<LightingMod> mods() {
        return runtime().mods();
    }

    /** 按 ID 查询 Mod */
    public static Optional<LightingMod> mod(String id) {
        return mods().stream().filter(mod -> mod.id().equals(id)).findFirst();
    }

    public static boolean isModLoaded(String id) {
        return mod(id).isPresent();
    }

    /** 统一事件总线 */
    public static LightingEventBus events() {
        return runtime().events();
    }

    /** 统一注册中心 */
    public static LightingRegistry registry() {
        return runtime().registry();
    }

    /** 统一配置视图(加载期已解析的配置项) */
    public static LightingConfig config() {
        return runtime().config();
    }

    /** 统一网络视图(载荷登记与网络注册锁) */
    public static LightingNetwork network() {
        return runtime().network();
    }

    /** 统一能力视图(typed 能力的注册与查询) */
    public static LightingCapabilities capabilities() {
        return runtime().capabilities();
    }

    /** 统一数据附件视图(附件类型登记与持有键读写) */
    public static LightingAttachments attachments() {
        return runtime().attachments();
    }

    private static LightingRuntime runtime() {
        LightingRuntime current = runtime;
        if (current == null) {
            throw new IllegalStateException(
                    "Lighting API is not installed yet; access it from an entrypoint, not from static init");
        }
        return current;
    }
}
