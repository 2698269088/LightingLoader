package top.mcocet.lighting.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * 帧计算经目标类加载器解析类型层级的 ClassWriter。
 * <p>
 * 注入指令会改变方法的指令偏移,原有 StackMapTable 随之失效,必须用
 * {@code COMPUTE_FRAMES} 重算;而重算需要判定类型继承关系,默认的
 * {@code getCommonSuperClass} 使用错误类加载器(ClassWriter 自身归属),
 * 因此这里由目标类加载器解析,找不到时保守退化为 {@code java/lang/Object},
 * 保证类路径不完整时转换也不中断。
 */
public final class FrameSafeClassWriter extends ClassWriter {

    private final ClassLoader classLoader;

    public FrameSafeClassWriter(ClassReader reader, ClassLoader classLoader) {
        super(reader, ClassWriter.COMPUTE_FRAMES);
        this.classLoader = classLoader;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            Class<?> class1 = Class.forName(type1.replace('/', '.'), false, classLoader);
            Class<?> class2 = Class.forName(type2.replace('/', '.'), false, classLoader);
            if (class1.isAssignableFrom(class2)) {
                return type1;
            }
            if (class2.isAssignableFrom(class1)) {
                return type2;
            }
            if (class1.isInterface() || class2.isInterface()) {
                return "java/lang/Object";
            }
            do {
                class1 = class1.getSuperclass();
            } while (!class1.isAssignableFrom(class2));
            return class1.getName().replace('.', '/');
        } catch (ClassNotFoundException e) {
            return "java/lang/Object";
        }
    }
}
