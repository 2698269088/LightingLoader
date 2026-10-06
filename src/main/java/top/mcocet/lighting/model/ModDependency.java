package top.mcocet.lighting.model;

/**
 * 统一化的 Mod 依赖条目。
 * 三种加载器的依赖声明都会归一化到本结构。
 *
 * @param modId        依赖目标的 Mod ID(如 minecraft、fabricloader、neoforge)
 * @param versionRange 版本范围表达式;空串表示不限制。
 *                     Fabric 风格为 ">=1.0";Forge 风格为 "[1.0,)" 等 Maven 区间
 * @param kind         依赖类型
 * @param mandatory    是否为强制依赖
 * @param ordering     加载顺序约束
 * @param side         生效侧
 */
public record ModDependency(
        String modId,
        String versionRange,
        DependencyKind kind,
        boolean mandatory,
        OrderingType ordering,
        DependencySide side) {

    public static ModDependency depends(String modId, String versionRange) {
        return new ModDependency(modId, versionRange, DependencyKind.DEPENDS,
                true, OrderingType.NONE, DependencySide.BOTH);
    }

    public static ModDependency of(String modId, String versionRange, DependencyKind kind) {
        boolean mandatory = kind == DependencyKind.DEPENDS || kind == DependencyKind.CONFLICTS;
        return new ModDependency(modId, versionRange, kind,
                mandatory, OrderingType.NONE, DependencySide.BOTH);
    }
}
