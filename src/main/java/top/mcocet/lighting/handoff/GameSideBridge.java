package top.mcocet.lighting.handoff;

/**
 * 游戏侧生命周期桥——"接管动作"的抽象接口。
 * <p>
 * 内核(父加载器)不能直接引用 Minecraft 类,而游戏内钩子必须在游戏
 * 类加载器空间里执行才能真正操作原版注册表。两者的契约就是本接口:
 * <ul>
 *   <li>实现类必须位于 {@code top.mcocet.lighting.game} 包下(由游戏侧
 *       类加载器自身优先定义,可直接引用 Minecraft 类);</li>
 *   <li>内核在移交前通过反射实例化实现类并绑定到
 *       {@link top.mcocet.lighting.transform.LightingHooks},由注入到
 *       游戏字节码中的钩子转发调用;</li>
 *   <li>接口本身只依赖父侧可见的类型,保证跨类加载器转换安全。</li>
 * </ul>
 */
public interface GameSideBridge {

    /**
     * 原版注册表冻结前的接管窗口(由注入到
     * {@code BuiltInRegistries.bootStrap()} 的钩子调用,位于
     * {@code createContents()} 之后、{@code freeze()} 之前)。
     * <p>
     * 实现方应把加载器注册中心里的描述符实例化为真实游戏对象并写入
     * 原版注册表(方块 → {@code BuiltInRegistries.BLOCK},实体 →
     * {@code ENTITY_TYPE}),这是自定义条目进入原版注册表的唯一合法时机。
     *
     * @return 本次接管的统计(方块数/实体数/失败数)
     */
    RegistryTakeoverReport onVanillaRegistryBootstrap();

    /**
     * 原版注册表冻结并通过校验之后(由注入到 {@code bootStrap()} 返回前的
     * 钩子调用)。实现方可在此做回查验证、NBT/坐标自检等就绪动作。
     */
    void onVanillaRegistryReady();
}
