package top.mcocet.lighting.model;

/**
 * 依赖对加载顺序的约束(Forge/NeoForge 的 mods.toml 语义)。
 */
public enum OrderingType {
    NONE,
    BEFORE,
    AFTER;

    public static OrderingType fromToml(String value) {
        if (value == null) {
            return NONE;
        }
        return switch (value.toUpperCase()) {
            case "BEFORE" -> BEFORE;
            case "AFTER" -> AFTER;
            default -> NONE;
        };
    }
}
