package top.mcocet.lighting.transform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 资源/数据包挂载改写器:向
 * {@code net.minecraft.server.packs.repository.PackRepository} 注入对
 * {@code top.mcocet.lighting.game.resource.GameResourceBridge} 的静态调用,
 * 使 Mod jar 的 {@code assets/} + {@code data/} 成为真实资源包/数据包:
 * <ul>
 *   <li>{@code <init>([LRepositorySource;)V} 入口:把 Mod 来源合并进
 *       构造参数数组(替换局部变量 1,任何 PackRepository 创建路径均覆盖);</li>
 *   <li>{@code reload()V} 与 {@code setSelected(Collection)V} 的 RETURN 前:
 *       把全部 Mod 包补充进选中列表(Mod 优先级最高,后加载覆盖原版)。</li>
 * </ul>
 * 带幂等保护与公开检查方法(供 dry-run 与测试使用),注入不改变
 * 原方法的正常路径语义。
 */
public final class PackSourceInjectTransformer implements ClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger(PackSourceInjectTransformer.class);

    /** 26.1.x 原版包仓库(不混淆,直接以官方名引用) */
    public static final String TARGET_CLASS = "net.minecraft.server.packs.repository.PackRepository";

    /** 游戏侧资源挂载桥(由游戏侧类加载器定义) */
    private static final String BRIDGE = "top/mcocet/lighting/game/resource/GameResourceBridge";

    private static final String REPOSITORY_INTERNAL = "net/minecraft/server/packs/repository/PackRepository";
    private static final String SOURCE_ARRAY_DESC = "([Lnet/minecraft/server/packs/repository/RepositorySource;)V";
    private static final String INIT_DESC = SOURCE_ARRAY_DESC;
    private static final String RELOAD_DESC = "()V";
    private static final String SET_SELECTED_DESC = "(Ljava/util/Collection;)V";
    private static final String EXPAND_HOOK = "expandRepositorySources";
    private static final String SELECT_HOOK = "selectModPacks";

    @Override
    public byte[] transform(String className, byte[] classBytes, ClassLoader classLoader) {
        if (!TARGET_CLASS.equals(className)) {
            return classBytes;
        }
        // 幂等保护:两个钩子都已注入时原样放行,避免重复调用
        if (isSourceHookInjected(classBytes) && isSelectHookInjected(classBytes)) {
            return classBytes;
        }
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (mv == null) {
                        return null;
                    }
                    if ("<init>".equals(name) && INIT_DESC.equals(descriptor)) {
                        return sourceArrayHook(mv);
                    }
                    if (("reload".equals(name) && RELOAD_DESC.equals(descriptor))
                            || ("setSelected".equals(name) && SET_SELECTED_DESC.equals(descriptor))) {
                        return selectReturnHook(mv);
                    }
                    return mv;
                }
            }, 0);
            return writer.toByteArray();
        } catch (RuntimeException e) {
            LOGGER.error("Pack repository hook injection failed for {}: {}", className, e.toString());
            return classBytes;
        }
    }

    /**
     * 构造器入口:替换来源数组参数
     * ({@code ALOAD 1 → expandRepositorySources → ASTORE 1}),
     * 使 Mod 来源在任何 PackRepository 创建路径下都生效。
     */
    private static MethodVisitor sourceArrayHook(MethodVisitor mv) {
        return new MethodVisitor(Opcodes.ASM9, mv) {
            @Override
            public void visitCode() {
                super.visitCode();
                super.visitVarInsn(Opcodes.ALOAD, 1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, EXPAND_HOOK,
                        "(" + SOURCE_ARRAY_DESC + "[Lnet/minecraft/server/packs/repository/RepositorySource;)",
                        false);
                super.visitVarInsn(Opcodes.ASTORE, 1);
            }
        };
    }

    /** reload/setSelected 的每个 RETURN 前:补充选中全部 Mod 包 */
    private static MethodVisitor selectReturnHook(MethodVisitor mv) {
        return new MethodVisitor(Opcodes.ASM9, mv) {
            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.RETURN) {
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, SELECT_HOOK,
                            "(L" + REPOSITORY_INTERNAL + ";)V", false);
                }
                super.visitInsn(opcode);
            }
        };
    }

    /** 检查给定字节码是否已注入来源合并钩子(供 dry-run 与测试使用) */
    public static boolean isSourceHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, EXPAND_HOOK);
    }

    /** 检查给定字节码是否已注入选中补充钩子(供 dry-run 与测试使用) */
    public static boolean isSelectHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, SELECT_HOOK);
    }

    private static boolean isHookInjected(byte[] classBytes, String hookName) {
        boolean[] found = {false};
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        if (BRIDGE.equals(owner) && hookName.equals(name)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }
}
