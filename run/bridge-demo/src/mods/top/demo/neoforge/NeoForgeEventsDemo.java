package top.demo.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * 演示用 NeoForge 订阅类:以真实注解与真实事件签名编译
 * ({@code @EventBusSubscriber} + {@code @SubscribeEvent} + 本体事件类),
 * 对应"Mod 集成 NeoForge 属性 / 直接依赖本体"的典型形态。
 * <p>
 * 说明:当前已完成注解面与签名面的编译及解析(本类经真实 API 编译、
 * 随演示 jar 分发);这类订阅类的自动注册(扫描后挂到 mod 事件总线)
 * 属于订阅桥的后续工作,本类在运行时不会被实例化或加载。
 */
@EventBusSubscriber(modid = "demo_bridge_neoforge")
public final class NeoForgeEventsDemo {

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        System.out.println("[DemoNeoForgeEvents] game shutting down: " + event);
    }
}
