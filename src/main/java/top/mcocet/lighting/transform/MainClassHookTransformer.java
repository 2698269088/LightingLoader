package top.mcocet.lighting.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 启动流程改写器:向 {@code net.minecraft.client.main.Main#main(String[])}
 * 方法的最开始处注入 {@link LightingHooks#onGameMainEnter()} 调用。
 * <p>
 * 这是"加载器接管启动"的字节码层实质——不修改任何游戏源文件,
 * 而是在类被加载进 JVM 的瞬间改写其字节码,使游戏主流程一进入就被
 * Lighting 感知(后续里程碑将在此钩子处完成最后的 Mod 移交与桥接)。
 * <p>
 * 实现要点:
 * <ul>
 *   <li>插入指令会改变方法内指令偏移,原有 StackMapTable 帧表随之失效,
 *       因此必须使用 {@code COMPUTE_FRAMES} 重新计算栈帧(见 {@link FrameSafeClassWriter});</li>
 *   <li>帧计算需要判定类型继承关系,FrameSafeClassWriter 经目标类加载器
 *       解析,找不到的类型退化为 {@code java/lang/Object},不中断类加载。</li>
 * </ul>
 */
public final class MainClassHookTransformer implements ClassTransformer {

    /** 26.1.x 客户端主类(不混淆,直接以官方名引用) */
    public static final String TARGET_CLASS = "net.minecraft.client.main.Main";

    public static final String HOOKS_INTERNAL_NAME = "top/mcocet/lighting/transform/LightingHooks";
    public static final String HOOK_METHOD_NAME = "onGameMainEnter";
    public static final String HOOK_METHOD_DESC = "()V";

    @Override
    public byte[] transform(String className, byte[] classBytes, ClassLoader classLoader) {
        if (!TARGET_CLASS.equals(className)) {
            return classBytes;
        }

        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (methodVisitor == null || !"main".equals(name) || !"([Ljava/lang/String;)V".equals(descriptor)) {
                    return methodVisitor;
                }
                return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        visitMethodInsn(Opcodes.INVOKESTATIC,
                                HOOKS_INTERNAL_NAME, HOOK_METHOD_NAME, HOOK_METHOD_DESC, false);
                    }
                };
            }
        }, 0);

        return writer.toByteArray();
    }

    /** 检查给定字节码的 main 方法中是否已注入 Lighting 启动钩子(供 dry-run 与测试使用) */
    public static boolean isHookInjected(byte[] classBytes) {
        boolean[] found = {false};
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"main".equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        if (HOOKS_INTERNAL_NAME.equals(owner) && HOOK_METHOD_NAME.equals(name)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }
}
