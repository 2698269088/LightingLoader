package top.demo.capabilities;

import top.mcocet.api.gametest.GameTest;
import top.mcocet.api.gametest.GameTestHolder;

/**
 * GameTest 演示:发现阶段被加载器以字节级扫描登记
 * (启动日志 "GameTests discovered: ...");
 * 执行端由游戏侧测试桥接入后,按登记信息注册进游戏内测试框架。
 */
@GameTestHolder("demo_bridge_fabric")
public final class DemoGameTests {

    @GameTest(timeoutTicks = 40)
    public static void deferredHolderResolves() {
    }

    @GameTest(template = "demo_bridge_fabric:empty", batch = "demo")
    public static void registryBuilderCompletes() {
    }
}
