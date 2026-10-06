package top.mcocet.lighting.coremod;

import top.mcocet.lighting.transform.ClassTransformer;

import java.util.List;

/**
 * 类核心模组接口——对应 FML 的 {@code IFMLLoadingPlugin} 的"提供转换器"职责。
 * <p>
 * Mod 在 jar 内 {@code META-INF/lighting_coremods.json} 声明实现类名,
 * 加载器在建立游戏类加载器之前实例化(见 {@link CoreModLoader}),
 * 其提供的 {@link ClassTransformer} 进入统一转换管线,
 * 在启动早期即可改写游戏字节码(与 ASM transformer 生态一致)。
 */
public interface LightingCoreMod {

    /** 核心模组名称(日志用) */
    String name();

    /** 提供的类转换器列表(可返回空列表表示仅做初始化) */
    List<ClassTransformer> transformers();
}
