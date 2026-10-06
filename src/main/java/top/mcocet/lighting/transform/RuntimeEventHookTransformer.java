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
 * 运行时事件改写器:向原版热路径注入对
 * {@code top.mcocet.lighting.game.event.RuntimeEventBridge} 的静态调用,
 * 把游戏主循环与交互关键锚点转为 {@code top.mcocet.api.event} 事件。
 * <p>
 * 注入锚点(26.1.x 官方名,均已按字节码核实):
 * <ul>
 *   <li>{@code MinecraftServer.tickServer}(入口/返回前)→ 服务端 tick 事件</li>
 *   <li>{@code ServerLevel.tick}(入口/返回前)→ 维度 tick 事件</li>
 *   <li>{@code ServerLevel.addFreshEntity}(入口)→ 实体加入维度事件</li>
 *   <li>{@code ServerPlayer.tick}(入口/返回前)→ 玩家 tick 事件</li>
 *   <li>{@code Minecraft.tick}(入口/返回前)→ 客户端 tick 事件</li>
 *   <li>{@code PlayerList.placeNewPlayer / remove}(返回前)→ 登录/登出事件</li>
 *   <li>{@code ServerPlayerGameMode.destroyBlock}(入口否决分支)→ 方块破坏事件</li>
 *   <li>{@code BlockItem.place}(入口否决分支)→ 方块放置事件</li>
 *   <li>{@code Entity.remove}(入口)→ 实体离开维度事件</li>
 *   <li>{@code LivingEntity / ServerPlayer.hurtServer}(入口伤害量改写/取消)→ 受伤事件</li>
 *   <li>{@code LivingEntity.die}(入口)→ 死亡事件</li>
 * </ul>
 * 所有注入均带幂等保护与公开检查方法(供 dry-run 与测试使用);
 * 注入不改变原方法的正常路径语义,仅在事件被取消时走原版失败语义的早退分支。
 */
public final class RuntimeEventHookTransformer implements ClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger(RuntimeEventHookTransformer.class);

    public static final String SERVER_CLASS = "net.minecraft.server.MinecraftServer";
    public static final String LEVEL_CLASS = "net.minecraft.server.level.ServerLevel";
    public static final String PLAYER_CLASS = "net.minecraft.server.level.ServerPlayer";
    public static final String CLIENT_CLASS = "net.minecraft.client.Minecraft";
    public static final String PLAYER_LIST_CLASS = "net.minecraft.server.players.PlayerList";
    public static final String GAME_MODE_CLASS = "net.minecraft.server.level.ServerPlayerGameMode";
    public static final String BLOCK_ITEM_CLASS = "net.minecraft.world.item.BlockItem";
    public static final String ENTITY_CLASS = "net.minecraft.world.entity.Entity";
    public static final String LIVING_CLASS = "net.minecraft.world.entity.LivingEntity";

    /** 游戏侧事件桥(由游戏侧类加载器定义) */
    private static final String BRIDGE = "top/mcocet/lighting/game/event/RuntimeEventBridge";

    private static final String LEVEL_INTERNAL = "net/minecraft/server/level/ServerLevel";
    private static final String PLAYER_INTERNAL = "net/minecraft/server/level/ServerPlayer";
    private static final String GAME_MODE_INTERNAL = "net/minecraft/server/level/ServerPlayerGameMode";
    private static final String BLOCK_PLACE_CONTEXT_INTERNAL = "net/minecraft/world/item/context/BlockPlaceContext";

    private static final String BOOLEAN_SUPPLIER_DESC = "(Ljava/util/function/BooleanSupplier;)V";
    private static final String REMOVAL_REASON_DESC = "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V";
    private static final String HURT_SERVER_DESC =
            "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    private static final String PLACE_NEW_PLAYER_DESC =
            "(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnet/minecraft/server/network/CommonListenerCookie;)V";
    private static final String REMOVE_PLAYER_DESC = "(Lnet/minecraft/server/level/ServerPlayer;)V";
    private static final String BLOCK_POS_DESC = "Lnet/minecraft/core/BlockPos;";
    private static final String ENTITY_DESC = "Lnet/minecraft/world/entity/Entity;";
    private static final String PLAYER_DESC = "Lnet/minecraft/server/level/ServerPlayer;";

    @Override
    public byte[] transform(String className, byte[] classBytes, ClassLoader classLoader) {
        try {
            return switch (className) {
                case SERVER_CLASS -> transformServer(classBytes, classLoader);
                case LEVEL_CLASS -> transformLevel(classBytes, classLoader);
                case PLAYER_CLASS -> transformPlayer(classBytes, classLoader);
                case CLIENT_CLASS -> transformClient(classBytes, classLoader);
                case PLAYER_LIST_CLASS -> transformPlayerList(classBytes, classLoader);
                case GAME_MODE_CLASS -> transformGameMode(classBytes, classLoader);
                case BLOCK_ITEM_CLASS -> transformBlockItem(classBytes, classLoader);
                case ENTITY_CLASS -> transformEntity(classBytes, classLoader);
                case LIVING_CLASS -> transformLiving(classBytes, classLoader);
                default -> classBytes;
            };
        } catch (RuntimeException e) {
            LOGGER.error("Runtime event hook injection failed for {}: {}", className, e.toString());
            return classBytes;
        }
    }

    // ---- 各类注入实现 ----

    private static byte[] transformServer(byte[] classBytes, ClassLoader classLoader) {
        if (isServerTickHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        reader.accept(tickHooks(writer, "tickServer", BOOLEAN_SUPPLIER_DESC, null, false,
                "serverTickStart", "serverTickEnd"), 0);
        return writer.toByteArray();
    }

    private static byte[] transformLevel(byte[] classBytes, ClassLoader classLoader) {
        if (isLevelTickHookInjected(classBytes) && isEntityJoinHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        ClassVisitor visitor = entryHook(writer, "addFreshEntity", "(Lnet/minecraft/world/entity/Entity;)Z",
                "onEntityJoinLevel", "(" + "L" + LEVEL_INTERNAL + ";" + ENTITY_DESC + ")V", 2);
        visitor = tickHooks(visitor, "tick", BOOLEAN_SUPPLIER_DESC, LEVEL_INTERNAL, true,
                "levelTickStart", "levelTickEnd");
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private static byte[] transformPlayer(byte[] classBytes, ClassLoader classLoader) {
        if (isPlayerTickHookInjected(classBytes) && isDamageHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        ClassVisitor visitor = hurtServerHook(writer);
        visitor = tickHooks(visitor, "tick", "()V", PLAYER_INTERNAL, true,
                "playerTickStart", "playerTickEnd");
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private static byte[] transformClient(byte[] classBytes, ClassLoader classLoader) {
        if (isClientTickHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        reader.accept(tickHooks(writer, "tick", "()V", null, false,
                "clientTickStart", "clientTickEnd"), 0);
        return writer.toByteArray();
    }

    private static byte[] transformPlayerList(byte[] classBytes, ClassLoader classLoader) {
        if (isLoginHookInjected(classBytes) && isLogoutHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        ClassVisitor visitor = returnHook(writer, "remove", REMOVE_PLAYER_DESC, 1,
                "playerLogout", "(" + PLAYER_DESC + ")V");
        visitor = returnHook(visitor, "placeNewPlayer", PLACE_NEW_PLAYER_DESC, 2,
                "playerLogin", "(" + PLAYER_DESC + ")V");
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private static byte[] transformGameMode(byte[] classBytes, ClassLoader classLoader) {
        if (isBlockBreakHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        reader.accept(blockBreakVeto(writer), 0);
        return writer.toByteArray();
    }

    private static byte[] transformBlockItem(byte[] classBytes, ClassLoader classLoader) {
        if (isBlockPlaceHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        reader.accept(blockPlaceVeto(writer), 0);
        return writer.toByteArray();
    }

    private static byte[] transformEntity(byte[] classBytes, ClassLoader classLoader) {
        if (isEntityLeaveHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        reader.accept(entryHook(writer, "remove", REMOVAL_REASON_DESC,
                "onEntityLeaveLevel", "(" + ENTITY_DESC + ")V", 1), 0);
        return writer.toByteArray();
    }

    private static byte[] transformLiving(byte[] classBytes, ClassLoader classLoader) {
        if (isDamageHookInjected(classBytes) && isDeathHookInjected(classBytes)) {
            return classBytes;
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new FrameSafeClassWriter(reader, classLoader);
        ClassVisitor visitor = entryHook(writer, "die", "(Lnet/minecraft/world/damagesource/DamageSource;)V",
                "onLivingDeath",
                "(Lnet/minecraft/world/entity/LivingEntity;"
                        + "Lnet/minecraft/world/damagesource/DamageSource;)V", 2);
        visitor = hurtServerHook(visitor);
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    // ---- 字节码注入原语 ----

    /**
     * 入口 + 每个 RETURN 前注入静态钩子(可选携带 this)。
     * 适用于"开始/结束"成对的 tick 语义锚点。
     */
    private static ClassVisitor tickHooks(ClassVisitor cv, String methodName, String methodDesc,
                                          String targetInternal, boolean withThis,
                                          String startHook, String endHook) {
        String hookDesc = withThis ? "(L" + targetInternal + ";)V" : "()V";
        return new ClassVisitor(Opcodes.ASM9, cv) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null || !methodName.equals(name) || !methodDesc.equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (withThis) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                        }
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, startHook, hookDesc, false);
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            if (withThis) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                            }
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, endHook, hookDesc, false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
    }

    /** 方法入口注入静态钩子,按 {@code argCount}(0/1/2)顺序加载前若干个引用参数 */
    private static ClassVisitor entryHook(ClassVisitor cv, String methodName, String methodDesc,
                                          String hookName, String hookDesc, int argCount) {
        return new ClassVisitor(Opcodes.ASM9, cv) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null || !methodName.equals(name) || !methodDesc.equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        for (int slot = 0; slot < argCount; slot++) {
                            super.visitVarInsn(Opcodes.ALOAD, slot);
                        }
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, hookName, hookDesc, false);
                    }
                };
            }
        };
    }

    /** 每个 RETURN 前加载指定局部变量槽并注入静态钩子(如登录/登出锚点) */
    private static ClassVisitor returnHook(ClassVisitor cv, String methodName, String methodDesc,
                                           int loadSlot, String hookName, String hookDesc) {
        return new ClassVisitor(Opcodes.ASM9, cv) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null || !methodName.equals(name) || !methodDesc.equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, loadSlot);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, hookName, hookDesc, false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
    }

    /**
     * 受伤锚点(LivingEntity/ServerPlayer.hurtServer):
     * 入口把 (this, level, source, amount) 交给桥,桥返回新伤害量;
     * 返回 NaN 视为事件被取消 → 按原版未受伤语义返回 false,
     * 否则把返回值写回 amount 参数槽(slot 3)。
     */
    private static ClassVisitor hurtServerHook(ClassVisitor cv) {
        return new ClassVisitor(Opcodes.ASM9, cv) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null || !"hurtServer".equals(name) || !HURT_SERVER_DESC.equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitVarInsn(Opcodes.FLOAD, 3);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "onLivingDamage",
                                "(Lnet/minecraft/world/entity/LivingEntity;"
                                        + "Lnet/minecraft/server/level/ServerLevel;"
                                        + "Lnet/minecraft/world/damagesource/DamageSource;F)F", false);
                        super.visitInsn(Opcodes.DUP);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "isNaN", "(F)Z", false);
                        Label proceed = new Label();
                        super.visitJumpInsn(Opcodes.IFEQ, proceed);
                        super.visitInsn(Opcodes.POP);
                        super.visitInsn(Opcodes.ICONST_0);
                        super.visitInsn(Opcodes.IRETURN);
                        super.visitLabel(proceed);
                        super.visitVarInsn(Opcodes.FSTORE, 3);
                    }
                };
            }
        };
    }

    /**
     * 方块破坏锚点(ServerPlayerGameMode.destroyBlock):
     * 入口派发事件,取消时返回 false(与原版"未能破坏"语义一致)。
     */
    private static ClassVisitor blockBreakVeto(ClassVisitor cv) {
        return new ClassVisitor(Opcodes.ASM9, cv) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null || !"destroyBlock".equals(name) || !("(" + BLOCK_POS_DESC + ")Z").equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitFieldInsn(Opcodes.GETFIELD, GAME_MODE_INTERNAL,
                                "player", PLAYER_DESC);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "onBlockBreak",
                                "(" + PLAYER_DESC + BLOCK_POS_DESC + ")Z", false);
                        Label proceed = new Label();
                        super.visitJumpInsn(Opcodes.IFEQ, proceed);
                        super.visitInsn(Opcodes.ICONST_0);
                        super.visitInsn(Opcodes.IRETURN);
                        super.visitLabel(proceed);
                    }
                };
            }
        };
    }

    /**
     * 方块放置锚点(BlockItem.place):
     * 入口经 {@code BlockPlaceContext} 取玩家与目标坐标派发事件,
     * 取消时返回 {@code InteractionResult.FAIL}。
     */
    private static ClassVisitor blockPlaceVeto(ClassVisitor cv) {
        return new ClassVisitor(Opcodes.ASM9, cv) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null || !"place".equals(name)
                        || !("(L" + BLOCK_PLACE_CONTEXT_INTERNAL + ";)Lnet/minecraft/world/InteractionResult;")
                                .equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BLOCK_PLACE_CONTEXT_INTERNAL,
                                "getPlayer", "()Lnet/minecraft/world/entity/player/Player;", false);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BLOCK_PLACE_CONTEXT_INTERNAL,
                                "getClickedPos", "()" + BLOCK_POS_DESC, false);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "onBlockPlace",
                                "(Lnet/minecraft/world/entity/player/Player;" + BLOCK_POS_DESC + ")Z", false);
                        Label proceed = new Label();
                        super.visitJumpInsn(Opcodes.IFEQ, proceed);
                        super.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/InteractionResult",
                                "FAIL", "Lnet/minecraft/world/InteractionResult$Fail;");
                        super.visitInsn(Opcodes.ARETURN);
                        super.visitLabel(proceed);
                    }
                };
            }
        };
    }

    // ---- 注入检查(供 dry-run / 测试 / 幂等保护使用) ----

    /** 检查字节码中是否已注入服务端 tick 钩子 */
    public static boolean isServerTickHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "serverTickStart");
    }

    /** 检查字节码中是否已注入维度 tick 钩子 */
    public static boolean isLevelTickHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "levelTickStart");
    }

    /** 检查字节码中是否已注入玩家 tick 钩子 */
    public static boolean isPlayerTickHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "playerTickStart");
    }

    /** 检查字节码中是否已注入客户端 tick 钩子 */
    public static boolean isClientTickHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "clientTickStart");
    }

    /** 检查字节码中是否已注入登录钩子 */
    public static boolean isLoginHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "playerLogin");
    }

    /** 检查字节码中是否已注入登出钩子 */
    public static boolean isLogoutHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "playerLogout");
    }

    /** 检查字节码中是否已注入方块破坏钩子 */
    public static boolean isBlockBreakHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "onBlockBreak");
    }

    /** 检查字节码中是否已注入方块放置钩子 */
    public static boolean isBlockPlaceHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "onBlockPlace");
    }

    /** 检查字节码中是否已注入实体加入维度钩子 */
    public static boolean isEntityJoinHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "onEntityJoinLevel");
    }

    /** 检查字节码中是否已注入实体离开维度钩子 */
    public static boolean isEntityLeaveHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "onEntityLeaveLevel");
    }

    /** 检查字节码中是否已注入受伤钩子 */
    public static boolean isDamageHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "onLivingDamage");
    }

    /** 检查字节码中是否已注入死亡钩子 */
    public static boolean isDeathHookInjected(byte[] classBytes) {
        return isHookInjected(classBytes, "onLivingDeath");
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
