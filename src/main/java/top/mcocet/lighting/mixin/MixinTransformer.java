package top.mcocet.lighting.mixin;

import top.mcocet.lighting.transform.ClassTransformer;

/**
 * Mixin 转换器接入点(管线中的 M3 位点)。
 * <p>
 * 当前为直通实现:配置在加载期已由 {@link MixinModule} 解析并登记进
 * {@link MixinRegistry},本转换器把本注册表暴露给转换管线。
 * Mixin 引擎(org.spongepowered:mixin 服务实现)随发行组装后,
 * 在此处把配置交给引擎完成实际字节码改写,并保持"引擎未装配时
 * 原样返回输入"的降级语义——与其它生态 API 的组装策略一致。
 */
public final class MixinTransformer implements ClassTransformer {

    private final MixinRegistry registry;

    public MixinTransformer(MixinRegistry registry) {
        this.registry = registry;
    }

    @Override
    public byte[] transform(String className, byte[] classBytes, ClassLoader classLoader) {
        if (registry.configCount() == 0) {
            return classBytes;
        }
        // 引擎装配位:此处交给 Mixin 服务实现处理(当前直通)
        return classBytes;
    }

    /** 供引擎装配方取用配置清单 */
    public MixinRegistry registry() {
        return registry;
    }
}
