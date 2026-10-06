package top.mcocet.lighting.transform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 原版注册表接管改写器:向
 * {@code net.minecraft.core.registries.BuiltInRegistries#bootStrap()}
 * 注入两个生命周期钩子,打开"全面接管方块/实体注册"的唯一合法窗口:
 * <ol>
 *   <li>在 {@code INVOKESTATIC freeze()} 之前注入
 *       {@link LightingHooks#onVanillaRegistryBootstrap()}——此时原版内容
 *       已通过 {@code createContents()} 装载、注册表尚未冻结,加载器可以
 *       写入自定义方块/实体条目;</li>
 *   <li>在方法 RETURN 之前注入 {@link LightingHooks#onVanillaRegistryReady()}
 *       ——原版注册表已冻结并通过校验(多次 RETURN 由内核一次性守卫去重)。</li>
 * </ol>
 * 26.1.x 的 {@code bootStrap()} 实际结构为
 * {@code createContents() → freeze() → validate(REGISTRY) → return},
 * 两个注入点均可精确定位;结构不符时原样放行并记录警告。
 */
public final class VanillaRegistryHookTransformer implements ClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger(VanillaRegistryHookTransformer.class);

    /** 26.1.x 原版注册表引导类(不混淆,直接以官方名引用) */
    public static final String TARGET_CLASS = "net.minecraft.core.registries.BuiltInRegistries";

    private static final String TARGET_INTERNAL_NAME = "net/minecraft/core/registries/BuiltInRegistries";
    private static final String TARGET_METHOD = "bootStrap";
    private static final String FREEZE_METHOD = "freeze";
    private static final String METHOD_DESC = "()V";

    public static final String HOOKS_INTERNAL_NAME = "top/mcocet/lighting/transform/LightingHooks";
    public static final String BOOTSTRAP_HOOK_NAME = "onVanillaRegistryBootstrap";
    public static final String READY_HOOK_NAME = "onVanillaRegistryReady";

    @Override
    public byte[] transform(String className, byte[] classBytes, ClassLoader classLoader) {
        if (!TARGET_CLASS.equals(className)) {
            return classBytes;
        }
        // 幂等保护:已注入过的字节码原样放行,避免重复调用钩子
        if (isBootstrapHookInjected(classBytes) && isReadyHookInjected(classBytes)) {
            return classBytes;
        }

        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        boolean[] injected = {false, false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (methodVisitor == null || !TARGET_METHOD.equals(name) || !METHOD_DESC.equals(descriptor)) {
                    return methodVisitor;
                }
                return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && TARGET_INTERNAL_NAME.equals(owner)
                                && FREEZE_METHOD.equals(name) && METHOD_DESC.equals(descriptor)) {
                            // 冻结前窗口:原版内容已装载,自定义条目仍可写入
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    HOOKS_INTERNAL_NAME, BOOTSTRAP_HOOK_NAME, METHOD_DESC, false);
                            injected[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            // 冻结与校验完成后:注册表就绪
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    HOOKS_INTERNAL_NAME, READY_HOOK_NAME, METHOD_DESC, false);
                            injected[1] = true;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        if (!injected[0] || !injected[1]) {
            LOGGER.warn("Vanilla registry hooks partially injected (bootstrap={}, ready={}); "
                    + "the registry takeover window may be unavailable on this game version",
                    injected[0], injected[1]);
        }
        return writer.toByteArray();
    }

    /** 检查给定字节码的 bootStrap 方法中是否已注入"冻结前接管"钩子(供 dry-run 与测试使用) */
    public static boolean isBootstrapHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, BOOTSTRAP_HOOK_NAME);
    }

    /** 检查给定字节码的 bootStrap 方法中是否已注入"注册表就绪"钩子(供 dry-run 与测试使用) */
    public static boolean isReadyHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, READY_HOOK_NAME);
    }

    private static boolean isHookInjected(byte[] classBytes, String hookName) {
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
                        if (HOOKS_INTERNAL_NAME.equals(owner) && hookName.equals(name)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }
}
