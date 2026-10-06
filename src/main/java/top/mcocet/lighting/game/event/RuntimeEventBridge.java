package top.mcocet.lighting.game.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import top.mcocet.api.event.BlockBreakEvent;
import top.mcocet.api.event.BlockPlaceEvent;
import top.mcocet.api.event.ClientTickEvent;
import top.mcocet.api.event.EntityJoinLevelEvent;
import top.mcocet.api.event.EntityLeaveLevelEvent;
import top.mcocet.api.event.LevelTickEvent;
import top.mcocet.api.event.LivingDamageEvent;
import top.mcocet.api.event.LivingDeathEvent;
import top.mcocet.api.event.PlayerLoggedInEvent;
import top.mcocet.api.event.PlayerLoggedOutEvent;
import top.mcocet.api.event.PlayerTickEvent;
import top.mcocet.api.event.ServerTickEvent;
import top.mcocet.api.event.TickEvent;
import top.mcocet.lighting.transform.LightingHooks;

/**
 * 游戏侧运行时事件桥。
 * <p>
 * 由 {@code RuntimeEventHookTransformer} 注入的字节码在游戏主循环关键锚点
 * (服务端/维度/玩家/客户端 tick、玩家登录登出、方块破坏与放置、实体加入
 * 与离开、生物受伤与死亡)调用本类方法;本类把原版对象转换为
 * {@code top.mcocet.api.event} 事件并经 {@link LightingHooks#postRuntimeEvent}
 * 转发到内核事件总线。
 * <p>
 * 本类由游戏侧类加载器定义,可直接引用原版类型;所有方法都处于热路径,
 * 只做轻量对象转换,不做阻塞操作。派生的事件对象由 API 包定义,
 * 内核与 Mod 侧均可直接订阅。
 */
public final class RuntimeEventBridge {

    /** 受伤派发防重入:ServerPlayer 与 LivingEntity 双注入点在 super 链上共用一次派发 */
    private static final ThreadLocal<Boolean> IN_DAMAGE_DISPATCH = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private RuntimeEventBridge() {
    }

    // ---- tick 系列(锚点: MinecraftServer.tickServer / ServerLevel.tick / ServerPlayer.tick / Minecraft.tick) ----

    /** 服务端主循环 tick 开始 */
    public static void serverTickStart() {
        LightingHooks.postRuntimeEvent(new ServerTickEvent(TickEvent.Phase.START));
    }

    /** 服务端主循环 tick 结束 */
    public static void serverTickEnd() {
        LightingHooks.postRuntimeEvent(new ServerTickEvent(TickEvent.Phase.END));
    }

    /** 维度(关卡)tick 开始 */
    public static void levelTickStart(ServerLevel level) {
        LightingHooks.postRuntimeEvent(new LevelTickEvent(TickEvent.Phase.START, dimensionOf(level)));
    }

    /** 维度(关卡)tick 结束 */
    public static void levelTickEnd(ServerLevel level) {
        LightingHooks.postRuntimeEvent(new LevelTickEvent(TickEvent.Phase.END, dimensionOf(level)));
    }

    /** 服务端玩家 tick 开始 */
    public static void playerTickStart(ServerPlayer player) {
        LightingHooks.postRuntimeEvent(new PlayerTickEvent(TickEvent.Phase.START, player.getScoreboardName()));
    }

    /** 服务端玩家 tick 结束 */
    public static void playerTickEnd(ServerPlayer player) {
        LightingHooks.postRuntimeEvent(new PlayerTickEvent(TickEvent.Phase.END, player.getScoreboardName()));
    }

    /** 客户端主循环 tick 开始 */
    public static void clientTickStart() {
        LightingHooks.postRuntimeEvent(new ClientTickEvent(TickEvent.Phase.START));
    }

    /** 客户端主循环 tick 结束 */
    public static void clientTickEnd() {
        LightingHooks.postRuntimeEvent(new ClientTickEvent(TickEvent.Phase.END));
    }

    // ---- 玩家生命周期(锚点: PlayerList.placeNewPlayer / PlayerList.remove) ----

    /** 玩家完成登录进入世界 */
    public static void playerLogin(ServerPlayer player) {
        LightingHooks.postRuntimeEvent(
                new PlayerLoggedInEvent(player.getScoreboardName(), dimensionOf(player.level())));
    }

    /** 玩家从服务器移除 */
    public static void playerLogout(ServerPlayer player) {
        LightingHooks.postRuntimeEvent(new PlayerLoggedOutEvent(player.getScoreboardName()));
    }

    // ---- 方块交互(锚点: ServerPlayerGameMode.destroyBlock / BlockItem.place) ----

    /**
     * 方块破坏派发;返回 {@code true} 表示已否决,
     * 注入的早退分支将按原版失败语义返回 {@code false}。
     */
    public static boolean onBlockBreak(ServerPlayer player, BlockPos pos) {
        BlockBreakEvent event = new BlockBreakEvent(
                player.getScoreboardName(), pos.getX(), pos.getY(), pos.getZ());
        LightingHooks.postRuntimeEvent(event);
        return event.isCancelled();
    }

    /**
     * 方块放置派发;返回 {@code true} 表示已否决,
     * 注入的早退分支将返回 {@code InteractionResult.FAIL}。
     * 发射器/命令等无玩家上下文路径直接放行。
     */
    public static boolean onBlockPlace(Player player, BlockPos pos) {
        if (player == null) {
            return false;
        }
        BlockPlaceEvent event = new BlockPlaceEvent(
                player.getScoreboardName(), pos.getX(), pos.getY(), pos.getZ());
        LightingHooks.postRuntimeEvent(event);
        return event.isCancelled();
    }

    // ---- 实体生命周期(锚点: ServerLevel.addFreshEntity / Entity.remove) ----

    /** 实体加入服务端维度 */
    public static void onEntityJoinLevel(ServerLevel level, Entity entity) {
        LightingHooks.postRuntimeEvent(new EntityJoinLevelEvent(describe(entity), dimensionOf(level)));
    }

    /**
     * 实体从服务端维度移除。客户端实体、重复移除调用
     * (removalReason 已设置)与纯数据卸载均不派发。
     */
    public static void onEntityLeaveLevel(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (entity.getRemovalReason() != null) {
            return;
        }
        LightingHooks.postRuntimeEvent(new EntityLeaveLevelEvent(describe(entity), dimensionOf(level)));
    }

    // ---- 生物战斗(锚点: LivingEntity/ServerPlayer.hurtServer / LivingEntity.die) ----

    /**
     * 生物受伤派发。返回值约定:原样返回新伤害量;返回 {@link Float#NaN}
     * 表示事件被取消,注入的早退分支将按原版未受伤语义返回 {@code false}。
     * <p>
     * ServerPlayer 覆写路径与 LivingEntity.super 调用经 ThreadLocal 去重,
     * 单次受伤只派发一个事件。
     */
    public static float onLivingDamage(LivingEntity entity, ServerLevel level, DamageSource source, float amount) {
        if (IN_DAMAGE_DISPATCH.get()) {
            return amount;
        }
        IN_DAMAGE_DISPATCH.set(Boolean.TRUE);
        try {
            LivingDamageEvent event = new LivingDamageEvent(describe(entity), source.getMsgId(), amount);
            LightingHooks.postRuntimeEvent(event);
            if (event.isCancelled()) {
                return Float.NaN;
            }
            return event.amount();
        } finally {
            IN_DAMAGE_DISPATCH.set(Boolean.FALSE);
        }
    }

    /** 服务端生物死亡派发(客户端死亡动画路径不派发) */
    public static void onLivingDeath(LivingEntity entity, DamageSource source) {
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }
        LightingHooks.postRuntimeEvent(new LivingDeathEvent(describe(entity), source.getMsgId()));
    }

    // ---- 内部辅助 ----

    /** 维度标识(命名空间:路径),供事件携带上下文 */
    private static String dimensionOf(Level level) {
        return level.dimension().identifier().toString();
    }

    /** 实体描述:玩家名/实体显示名 */
    private static String describe(Entity entity) {
        return entity.getName().getString();
    }
}
