package top.demo.forge;

import net.minecraftforge.fml.common.Mod;
import top.mcocet.api.LightingLoader;

/**
 * 演示用 Forge 生态 Mod:@Mod 主类构造即入口(FML 语义)。
 * 由 Lighting 的 Forge 桥接扫描注解后反射实例化。
 */
@Mod("demo_bridge_forge")
public final class ForgeDemoMod {

    public ForgeDemoMod() {
        System.out.println("[DemoForgeMod] constructed via Lighting bridge");
        System.out.println("[DemoForgeMod] loader=" + LightingLoader.loaderName()
                + " " + LightingLoader.loaderVersion()
                + ", visible mods=" + LightingLoader.mods().size());
    }
}
