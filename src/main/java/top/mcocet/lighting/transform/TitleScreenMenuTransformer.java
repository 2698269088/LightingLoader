package top.mcocet.lighting.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 模组菜单入口注入器:向 {@code TitleScreen.init()} 注入
 * {@code ModMenuHook.onTitleScreenInit} 调用,在主菜单按钮组下方
 * 添加"模组"(Mods)按钮。
 * <p>
 * 参照 NeoForge 的实现思路(patches/…/TitleScreen.java.patch):
 * NeoForge 通过源码补丁在 {@code createNormalMenuOptions(...)} 之后
 * 添加 {@code ModsButton} 并把后续按钮下移;Lighting 不改游戏源文件,
 * 而是用 ASM 在字节码中相同锚点(调用 {@code createNormalMenuOptions}
 * 之后、返回值落栈变量之前)插入钩子调用,由钩子完成按钮创建。
 * <p>
 * 注入点栈安全性:锚点处栈上仅有 {@code createNormalMenuOptions} 的
 * 返回值(int),插入的 {@code ALOAD 0 + INVOKESTATIC(…)V} 序列
 * 净栈效果为零,原 {@code ISTORE} 不受影响,无需改写局部变量。
 */
public final class TitleScreenMenuTransformer implements ClassTransformer {

    /** 26.1.x 主菜单(不混淆,直接以官方名引用) */
    public static final String TARGET_CLASS = "net.minecraft.client.gui.screens.TitleScreen";
    public static final String TARGET_CLASS_INTERNAL = "net/minecraft/client/gui/screens/TitleScreen";

    /** 注入所在方法:初始化(每次界面重建/窗口缩放都会执行) */
    public static final String TARGET_METHOD = "init";

    /** 锚点方法:原版创建单人/多人/Realms 按钮组(参照 NeoForge 补丁的插入位置) */
    public static final String ANCHOR_METHOD = "createNormalMenuOptions";
    public static final String ANCHOR_DESC = "(II)I";

    public static final String HOOK_INTERNAL_NAME = "top/mcocet/lighting/game/ModMenuHook";
    public static final String HOOK_METHOD_NAME = "onTitleScreenInit";
    public static final String HOOK_METHOD_DESC = "(Lnet/minecraft/client/gui/screens/TitleScreen;)V";

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
                if (methodVisitor == null || !TARGET_METHOD.equals(name) || !"()V".equals(descriptor)) {
                    return methodVisitor;
                }
                return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        if (ANCHOR_METHOD.equals(name) && ANCHOR_DESC.equals(descriptor)
                                && TARGET_CLASS_INTERNAL.equals(owner)) {
                            // 原版按钮组创建完毕后,回落钩子添加模组入口
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    HOOK_INTERNAL_NAME, HOOK_METHOD_NAME, HOOK_METHOD_DESC, false);
                        }
                    }
                };
            }
        }, 0);

        return writer.toByteArray();
    }

    /** 检查给定字节码的 init 方法中是否已注入模组菜单钩子(供 dry-run 与测试使用) */
    public static boolean isMenuHookInjected(byte[] classBytes) {
        boolean[] found = {false};
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!TARGET_METHOD.equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        if (HOOK_INTERNAL_NAME.equals(owner) && HOOK_METHOD_NAME.equals(name)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }
}
