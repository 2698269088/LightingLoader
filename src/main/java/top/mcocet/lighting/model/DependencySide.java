package top.mcocet.lighting.model;

/**
 * 依赖生效的物理侧(Forge/NeoForge 的 mods.toml 语义)。
 * Fabric 的清单不区分依赖侧,统一记为 {@link #BOTH}。
 */
public enum DependencySide {
    CLIENT,
    SERVER,
    BOTH;

    public static DependencySide fromToml(String value) {
        if (value == null) {
            return BOTH;
        }
        return switch (value.toUpperCase()) {
            case "CLIENT" -> CLIENT;
            case "SERVER" -> SERVER;
            default -> BOTH;
        };
    }
}
