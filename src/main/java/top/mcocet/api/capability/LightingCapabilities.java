package top.mcocet.api.capability;

/**
 * 统一能力视图:typed 能力的注册与查询(Forge/NeoForge capabilities 语义)。
 * <p>
 * 与注册中心({@code LightingRegistry})的区别:注册中心管理的是
 * "名称 → 值"的注册表条目;能力视图管理的是"上下文实例 → 能力接口"的
 * 动态查询——Mod 为方块实体/物品堆/实体注册能力提供器后,任意代码
 * 可以用接口类型查询该实例提供的能力实现。
 * <p>
 * 域(Domain)语法:{@link Domain} 区分上下文的三大来源;
 * {@code targetId} 为提供器声明的目标标识(如 {@code minecraft:chest},
 * 或 {@code *} 表示对所有目标生效)。
 */
public interface LightingCapabilities {

    /** 能力上下文来源域 */
    enum Domain {
        /** 方块实体实例 */
        BLOCK_ENTITY,
        /** 物品堆实例 */
        ITEM_STACK,
        /** 实体实例 */
        ENTITY
    }

    /**
     * 注册能力提供器。同一域/目标/接口可注册多个提供器,
     * 查询按注册顺序尝试,首个返回非 null 的结果生效。
     *
     * @param domain     上下文来源域
     * @param targetId   目标标识(如 {@code minecraft:chest};{@code *} 为通配)
     * @param capability 能力接口类型
     * @param provider   提供器实现
     */
    <T> void register(Domain domain, String targetId, Class<T> capability, CapabilityProvider<T> provider);

    /**
     * 查询能力实例:先尝试精确目标({@code targetId})的提供器,
     * 再尝试通配({@code *})提供器;全部不支持返回 {@code null}。
     *
     * @param context 上下文实例(方块实体 / 物品堆 / 实体)
     */
    <T> T query(Domain domain, String targetId, Class<T> capability, Object context);

    /**
     * 查询能力实例(不限定目标):按注册顺序遍历该域全部提供器,
     * 首个非 null 结果生效;无匹配返回 {@code null}。
     */
    <T> T queryAny(Domain domain, Class<T> capability, Object context);

    /** 某域已登记的提供器总数 */
    int providerCount(Domain domain);

    /** 能力注册是否已锁定(锁定后拒绝新注册) */
    boolean isLocked();
}
