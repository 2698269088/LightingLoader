package top.mcocet.lighting.game;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import top.mcocet.lighting.game.nbt.NbtBridge;
import top.mcocet.lighting.game.registry.VanillaRegistryInjector;
import top.mcocet.lighting.game.world.PositionBridge;
import top.mcocet.lighting.handoff.GameSideBridge;
import top.mcocet.lighting.handoff.RegistryTakeoverReport;
import top.mcocet.lighting.registry.RegistryManager;

/**
 * 游戏侧生命周期管理器——{@link GameSideBridge} 的实现,由启动入口在
 * 游戏移交前反射实例化并绑定到内核侧 {@code LightingHooks}。
 * <p>
 * 跨类加载器契约:内核(父加载器)与游戏侧(GameClassLoader)通过父可见
 * 接口通信——内核注入到 {@code BuiltInRegistries.bootStrap()} 的钩子
 * 触发时经由本类把注册中心里冻结的描述符转写为真实游戏对象(方块/
 * 实体注册全面接管);注册表就绪后执行 NBT 与位置桥自检。
 */
public final class GameSideLifecycle implements GameSideBridge {

    private static final Logger LOGGER = LogManager.getLogger(GameSideLifecycle.class);

    private final RegistryManager registryManager;

    public GameSideLifecycle(RegistryManager registryManager) {
        this.registryManager = registryManager;
    }

    @Override
    public RegistryTakeoverReport onVanillaRegistryBootstrap() {
        return VanillaRegistryInjector.inject(registryManager);
    }

    @Override
    public void onVanillaRegistryReady() {
        LOGGER.info("Nbt bridge self-check: {}", NbtBridge.selfCheck() ? "OK" : "FAILED");
        LOGGER.info("Position bridge self-check: {}", PositionBridge.selfCheck() ? "OK" : "FAILED");
    }
}
