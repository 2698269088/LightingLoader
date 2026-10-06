package top.mcocet.lighting.bridge;

import top.mcocet.lighting.loader.LoaderType;
import top.mcocet.lighting.loader.ModContainer;

/**
 * 单个加载器生态的 API 缝合桥:把该生态 Mod 的入口点接入 Lighting
 * 生命周期(反射调用,不依赖编译期的生态 API)。
 * <p>
 * 生态的真实 API(如 {@code net.fabricmc.api.ModInitializer}、FML 的
 * {@code @Mod})由用户本地组装进 libraries;本层只要求它们在运行时类路径
 * 存在,缺失时以警告形式上报而非中断加载——这正是"三家加载器启动代码
 * 不运行、由 Lighting 履行其职责"的落点。
 */
public interface EcosystemBridge {

    /** 对应的生态枚举 */
    LoaderType loaderType();

    /** 生态展示名(日志用) */
    String displayName();

    /** 生态 API 的标志类:存在性探测用(如 net.fabricmc.api.ModInitializer) */
    String apiProbeClass();

    /**
     * 调度单个 Mod 的入口点。
     *
     * @param mod    已加载的 Mod 容器
     * @param loader 游戏侧类加载器(能加载 Mod 类与生态 API)
     * @return 调度结果(含调用情况与说明)
     */
    BridgeOutcome schedule(ModContainer mod, ClassLoader loader);
}
