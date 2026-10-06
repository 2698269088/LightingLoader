package top.mcocet.lighting.transform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 数据附件持久化改写器:向 {@code net.minecraft.world.entity.Entity} 的
 * 顶层存读方法注入对
 * {@code top.mcocet.lighting.game.attachment.AttachmentBridge} 的静态调用,
 * 使任意实体(原版与自定义)的附件数据随实体存档自动往返:
 * <ul>
 *   <li>{@code save(ValueOutput)Z}:每个 {@code IRETURN} 前,仅当返回 true
 *       (实体确已保存)时调用 {@code onEntitySave} 写入附件子节点;</li>
 *   <li>{@code load(ValueInput)V}:每个 {@code RETURN} 前调用
 *       {@code onEntityLoad} 读回附件子节点。</li>
 * </ul>
 * 26.x 中 {@code save/load} 为 Entity 的顶层模板方法(子类覆写
 * {@code addAdditionalSaveData/readAdditionalSaveData}),注于此即覆盖
 * 全部实体;带幂等保护与公开检查方法(供 dry-run 与测试使用),
 * 注入不改变原方法的正常路径语义。
 */
public final class AttachmentHookTransformer implements ClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger(AttachmentHookTransformer.class);

    /** 26.1.x 实体基类(顶层 save/load 所在;不混淆,直接以官方名引用) */
    public static final String ENTITY_CLASS = "net.minecraft.world.entity.Entity";

    /** 游戏侧附件持久化桥(由游戏侧类加载器定义) */
    private static final String BRIDGE = "top/mcocet/lighting/game/attachment/AttachmentBridge";

    private static final String ENTITY_DESC = "Lnet/minecraft/world/entity/Entity;";
    private static final String VALUE_OUTPUT_DESC = "Lnet/minecraft/world/level/storage/ValueOutput;";
    private static final String VALUE_INPUT_DESC = "Lnet/minecraft/world/level/storage/ValueInput;";
    private static final String SAVE_METHOD = "save";
    private static final String SAVE_DESC = "(" + VALUE_OUTPUT_DESC + ")Z";
    private static final String LOAD_METHOD = "load";
    private static final String LOAD_DESC = "(" + VALUE_INPUT_DESC + ")V";
    private static final String SAVE_HOOK = "onEntitySave";
    private static final String LOAD_HOOK = "onEntityLoad";

    @Override
    public byte[] transform(String className, byte[] classBytes, ClassLoader classLoader) {
        if (!ENTITY_CLASS.equals(className)) {
            return classBytes;
        }
        // 幂等保护:两个钩子都已注入时原样放行,避免重复调用
        if (isEntitySaveHookInjected(classBytes) && isEntityLoadHookInjected(classBytes)) {
            return classBytes;
        }
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
            ClassVisitor visitor = saveHook(writer);
            visitor = loadHook(visitor);
            reader.accept(visitor, 0);
            return writer.toByteArray();
        } catch (RuntimeException e) {
            LOGGER.error("Attachment persistence hook injection failed for {}: {}", className, e.toString());
            return classBytes;
        }
    }

    /**
     * save 锚点:每个 {@code IRETURN} 前插入
     * {@code DUP → IFEQ skip → ALOAD 0 → ALOAD 1 → INVOKESTATIC onEntitySave → skip},
     * 仅在方法返回 true(实体确已保存)时持久化附件。
     */
    private static ClassVisitor saveHook(ClassVisitor cv) {
        return new ClassVisitor(Opcodes.ASM9, cv) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null || !SAVE_METHOD.equals(name) || !SAVE_DESC.equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.IRETURN) {
                            Label skip = new Label();
                            super.visitInsn(Opcodes.DUP);
                            super.visitJumpInsn(Opcodes.IFEQ, skip);
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, SAVE_HOOK,
                                    "(" + ENTITY_DESC + VALUE_OUTPUT_DESC + ")V", false);
                            super.visitLabel(skip);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
    }

    /** load 锚点:每个 {@code RETURN} 前加载 (this, input) 并注入静态钩子 */
    private static ClassVisitor loadHook(ClassVisitor cv) {
        return new ClassVisitor(Opcodes.ASM9, cv) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null || !LOAD_METHOD.equals(name) || !LOAD_DESC.equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, LOAD_HOOK,
                                    "(" + ENTITY_DESC + VALUE_INPUT_DESC + ")V", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
    }

    /** 检查给定字节码的 save 方法是否已注入附件持久化钩子(供 dry-run 与测试使用) */
    public static boolean isEntitySaveHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, SAVE_METHOD, SAVE_DESC, SAVE_HOOK);
    }

    /** 检查给定字节码的 load 方法是否已注入附件读取钩子(供 dry-run 与测试使用) */
    public static boolean isEntityLoadHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, LOAD_METHOD, LOAD_DESC, LOAD_HOOK);
    }

    private static boolean isHookInjected(byte[] classBytes, String methodName, String methodDesc,
                                          String hookName) {
        boolean[] found = {false};
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!methodName.equals(name) || !methodDesc.equals(descriptor)) {
                    return null;
                }
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
