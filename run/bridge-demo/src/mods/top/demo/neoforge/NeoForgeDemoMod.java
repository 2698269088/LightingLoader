package top.demo.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import top.mcocet.api.LightingLoader;

/**
 * 演示用 NeoForge 生态 Mod:与真实 NeoForge API 库(libs/neoforge)同签名编译。
 * {@code @Mod} 的 value/dist 属性由桥接在扫描阶段读取;单参
 * {@code (IEventBus)} 构造器由 Lighting 注入真实事件总线实例
 * (由生态库中的 BusBuilder 创建),对应 FML 的 mod 构造语义。
 */
@Mod(value = "demo_bridge_neoforge", dist = { Dist.CLIENT, Dist.DEDICATED_SERVER })
public final class NeoForgeDemoMod {

    public NeoForgeDemoMod(IEventBus modBus) {
        System.out.println("[DemoNeoForgeMod] constructed via Lighting bridge, modBus=" + modBus);
        System.out.println("[DemoNeoForgeMod] loader=" + LightingLoader.loaderName()
                + " " + LightingLoader.loaderVersion()
                + ", visible mods=" + LightingLoader.mods().size());
        probeNeoForgeBody();
    }

    /** 探测"NeoForge 本体"类面是否已在类路径(只加载不初始化,避免触发未支持的静态链) */
    private static void probeNeoForgeBody() {
        try {
            ClassLoader loader = NeoForgeDemoMod.class.getClassLoader();
            Class<?> facade = Class.forName("net.neoforged.neoforge.common.NeoForge", false, loader);
            boolean eventBusField = false;
            for (var field : facade.getDeclaredFields()) {
                if ("EVENT_BUS".equals(field.getName())) {
                    eventBusField = true;
                }
            }
            System.out.println("[DemoNeoForgeMod] NeoForge body resolved=" + facade.getName()
                    + ", EVENT_BUS field=" + eventBusField);
        } catch (ClassNotFoundException | LinkageError e) {
            System.out.println("[DemoNeoForgeMod] NeoForge body not resolvable: " + e);
        }
    }
}
