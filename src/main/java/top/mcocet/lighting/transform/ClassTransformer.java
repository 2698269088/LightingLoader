package top.mcocet.lighting.transform;

/**
 * 单个类转换器:在类从字节码变为 {@code Class<?>} 之前,对字节码做改写。
 * <p>
 * 这是加载器修改游戏行为的统一入口(对应 Forge 的 coremod/ASM 转换
 * 与 Fabric 的 Mixin 底层机制):
 * <ul>
 *   <li>M1:启动流程改写(如向游戏主类注入启动钩子)</li>
 *   <li>M3:Mod 的 Mixin 配置编译为本接口的实现,进入同一管线</li>
 * </ul>
 * 实现必须是线程安全的,且只应处理目标类,对非目标类原样返回输入数组。
 */
@FunctionalInterface
public interface ClassTransformer {

    /**
     * 转换类字节码。
     *
     * @param className  点号形式的类名(如 net.minecraft.client.main.Main)
     * @param classBytes 原始类字节码
     * @param classLoader 类所属的类加载器(供转换器解析类型层级,如 ASM 的 COMPUTE_FRAMES)
     * @return 转换后的字节码;无改动时返回原数组
     */
    byte[] transform(String className, byte[] classBytes, ClassLoader classLoader);
}
