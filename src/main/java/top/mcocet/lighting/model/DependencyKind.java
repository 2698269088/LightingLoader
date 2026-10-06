package top.mcocet.lighting.model;

/**
 * 统一的依赖关系类型。
 * <p>
 * Fabric 在 fabric.mod.json 中区分 depends/recommends/suggests/conflicts/breaks;
 * Forge/NeoForge 的 mods.toml 只有"是否强制(mandatory)"一种维度,
 * 映射规则:mandatory=true 归入 {@link #DEPENDS},false 归入 {@link #RECOMMENDS}。
 */
public enum DependencyKind {
    /** 必需依赖,缺失则拒绝加载 */
    DEPENDS,

    /** 推荐依赖,缺失仅警告 */
    RECOMMENDS,

    /** 建议依赖,缺失静默 */
    SUGGESTS,

    /** 冲突:存在则拒绝加载 */
    CONFLICTS,

    /** 破坏性依赖:指定版本范围内存在则拒绝加载 */
    BREAKS
}
