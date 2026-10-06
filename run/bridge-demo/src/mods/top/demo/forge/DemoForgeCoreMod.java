package top.demo.forge;

import top.mcocet.lighting.coremod.LightingCoreMod;
import top.mcocet.lighting.transform.ClassTransformer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 演示用核心模组(对应 FML coremod):由 META-INF/lighting_coremods.json 声明,
 * 在建立游戏类加载器之前实例化,为转换管线提供一个"作用于游戏主类"的转换器示例。
 * 当前转换器只做一次性日志标记并原样返回字节码(不改写,保证 dry-run 结果不变)。
 */
public final class DemoForgeCoreMod implements LightingCoreMod {

    private final AtomicBoolean logged = new AtomicBoolean();

    @Override
    public String name() {
        return "DemoForgeCoreMod";
    }

    @Override
    public List<ClassTransformer> transformers() {
        return List.of((className, classBytes, classLoader) -> {
            if ("net.minecraft.client.main.Main".equals(className) && logged.compareAndSet(false, true)) {
                System.out.println("[DemoForgeCoreMod] transformer active on " + className);
            }
            return classBytes;
        });
    }
}
