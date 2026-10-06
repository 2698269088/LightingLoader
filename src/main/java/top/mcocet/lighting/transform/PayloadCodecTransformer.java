package top.mcocet.lighting.transform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 自定义载荷编解码改写器:把 {@code lighting:channel} 载荷类型接入原版线协议。
 * <p>
 * 26.1.x 中自定义载荷的解码列表在载荷包类的 {@code <clinit>} 里经
 * {@code CustomPacketPayload.codec(FallbackProvider, List)} 构建,未登记的
 * 类型会被解码为仅含 id 的 {@code DiscardedPayload}(字节被丢弃)。本改写器:
 * <ol>
 *   <li>在每个 {@code codec(...)} 调用之前插入
 *       {@code DUP + appendModdedPayloadTypes(List)}——把 modded 载荷类型
 *       追加进即将构建的列表(三个调用点:{@code ServerboundCustomPayloadPacket}
 *       一个、{@code ClientboundCustomPayloadPacket} 的 PLAY/CONFIG 两个);</li>
 *   <li>在 {@code ServerCommonPacketListenerImpl.handleCustomPayload} 入口注入
 *       服务端接收派发;</li>
 *   <li>在 {@code ClientCommonPacketListenerImpl.handleCustomPayload} 入口注入
 *       客户端接收派发(覆盖 PLAY 与 CONFIGURATION 两个阶段)。</li>
 * </ol>
 * 全部注入均带幂等保护;{@code appendModdedPayloadTypes} 内部对空注册表
 * 保持原样放行,不影响无自定义载荷的会话。
 */
public final class PayloadCodecTransformer implements ClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger(PayloadCodecTransformer.class);

    public static final String SERVERBOUND_PACKET_CLASS = "net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket";
    public static final String CLIENTBOUND_PACKET_CLASS = "net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket";
    public static final String SERVER_LISTENER_CLASS = "net.minecraft.server.network.ServerCommonPacketListenerImpl";
    public static final String CLIENT_LISTENER_CLASS = "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl";

    /** 游戏侧网络桥(由游戏侧类加载器定义) */
    private static final String BRIDGE = "top/mcocet/lighting/game/network/GameNetworkBridge";

    private static final String PAYLOAD_INTERNAL = "net/minecraft/network/protocol/common/custom/CustomPacketPayload";
    /** {@code CustomPacketPayload.codec(FallbackProvider, List)} 的精确描述符 */
    private static final String CODEC_DESC = "(Lnet/minecraft/network/protocol/common/custom/"
            + "CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;";
    private static final String APPEND_DESC = "(Ljava/util/List;)V";

    private static final String SERVERBOUND_PACKET_INTERNAL = "net/minecraft/network/protocol/common/ServerboundCustomPayloadPacket";
    private static final String CLIENTBOUND_PACKET_INTERNAL = "net/minecraft/network/protocol/common/ClientboundCustomPayloadPacket";

    private static final String HANDLE_SERVERBOUND_DESC =
            "(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V";
    private static final String HANDLE_CLIENTBOUND_DESC =
            "(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V";

    @Override
    public byte[] transform(String className, byte[] classBytes, ClassLoader classLoader) {
        try {
            return switch (className) {
                case SERVERBOUND_PACKET_CLASS -> transformCodecList(classBytes, classLoader, "serverbound");
                case CLIENTBOUND_PACKET_CLASS -> transformCodecList(classBytes, classLoader, "clientbound");
                case SERVER_LISTENER_CLASS -> transformServerListener(classBytes, classLoader);
                case CLIENT_LISTENER_CLASS -> transformClientListener(classBytes, classLoader);
                default -> classBytes;
            };
        } catch (RuntimeException e) {
            LOGGER.error("Payload codec hook injection failed for {}: {}", className, e.toString());
            return classBytes;
        }
    }

    // ---- 解码列表追加(载荷包 <clinit>) ----

    private static byte[] transformCodecList(byte[] classBytes, ClassLoader classLoader, String side) {
        if (isPayloadCodecInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        boolean[] injected = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && PAYLOAD_INTERNAL.equals(owner)
                                && "codec".equals(name) && CODEC_DESC.equals(descriptor)) {
                            // 调用点栈顶为即将交给 codec(...) 的解码列表:复制一份先追加 modded 类型
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE,
                                    "appendModdedPayloadTypes", APPEND_DESC, false);
                            injected[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (!injected[0]) {
            LOGGER.warn("Payload codec list hook not injected for the {} packet "
                    + "(the CustomPacketPayload.codec call site may have changed on this game version)", side);
        } else {
            LOGGER.info("Payload codec list hook injected for the {} packet", side);
        }
        return writer.toByteArray();
    }

    // ---- 接收派发(监听器 handleCustomPayload) ----

    private static byte[] transformServerListener(byte[] classBytes, ClassLoader classLoader) {
        if (isServerboundHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        boolean[] injected = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null || !"handleCustomPayload".equals(name)
                        || !HANDLE_SERVERBOUND_DESC.equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "onServerboundPayload",
                                "(Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;"
                                        + SERVERBOUND_PACKET_INTERNAL + ";)V", false);
                        injected[0] = true;
                    }
                };
            }
        }, 0);
        if (!injected[0]) {
            LOGGER.warn("Serverbound payload handler hook not injected "
                    + "(handleCustomPayload signature may have changed on this game version)");
        }
        return writer.toByteArray();
    }

    private static byte[] transformClientListener(byte[] classBytes, ClassLoader classLoader) {
        if (isClientboundHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        boolean[] injected = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null || !"handleCustomPayload".equals(name)
                        || !HANDLE_CLIENTBOUND_DESC.equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "onClientboundPayload",
                                "(" + CLIENTBOUND_PACKET_INTERNAL + ";)V", false);
                        injected[0] = true;
                    }
                };
            }
        }, 0);
        if (!injected[0]) {
            LOGGER.warn("Clientbound payload handler hook not injected "
                    + "(handleCustomPayload signature may have changed on this game version)");
        }
        return writer.toByteArray();
    }

    // ---- 注入检查(供 dry-run / 测试 / 幂等保护使用) ----

    /** 检查字节码中是否已注入解码列表追加钩子 */
    public static boolean isPayloadCodecInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "appendModdedPayloadTypes");
    }

    /** 检查字节码中是否已注入服务端接收钩子 */
    public static boolean isServerboundHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "onServerboundPayload");
    }

    /** 检查字节码中是否已注入客户端接收钩子 */
    public static boolean isClientboundHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "onClientboundPayload");
    }

    private static boolean isHookInjected(byte[] classBytes, String hookToFind) {
        boolean[] found = {false};
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                                boolean isInterface) {
                        if (BRIDGE.equals(owner) && hookToFind.equals(name)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }
}
