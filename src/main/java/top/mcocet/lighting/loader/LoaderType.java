package top.mcocet.lighting.loader;

/**
 * 支持的三大 Mod 加载器生态。
 * <p>
 * 每种加载器通过其 Mod 清单文件来识别:
 * Fabric 使用 {@code fabric.mod.json},Forge 使用 {@code META-INF/mods.toml},
 * NeoForge(1.20.5+)使用 {@code META-INF/neoforge.mods.toml}。
 */
public enum LoaderType {
    /** Fabric 生态 */
    FABRIC("fabric.mod.json"),

    /** 经典 Forge 生态 */
    FORGE("META-INF/mods.toml"),

    /** NeoForge 生态(Forge 的官方继任分支) */
    NEOFORGE("META-INF/neoforge.mods.toml"),

    /** 未识别出元数据的 Jar */
    UNKNOWN("");

    private final String metadataFile;

    LoaderType(String metadataFile) {
        this.metadataFile = metadataFile;
    }

    /** 该加载器的 Mod 清单文件在 Jar 内的路径 */
    public String metadataFile() {
        return metadataFile;
    }

    /**
     * 一个 Jar 同时携带多种清单时的默认选择优先级。
     * 现实中极少发生;NeoForge 优先于 Forge 是因为 26.x 世代的 Forge 补丁几乎停摆,
     * 双清单 Jar 基本是 NeoForge 兼容包。
     */
    public static LoaderType primaryOf(Iterable<LoaderType> present) {
        boolean hasNeoForge = false;
        boolean hasForge = false;
        boolean hasFabric = false;
        for (LoaderType type : present) {
            switch (type) {
                case NEOFORGE -> hasNeoForge = true;
                case FORGE -> hasForge = true;
                case FABRIC -> hasFabric = true;
                default -> { }
            }
        }
        if (hasNeoForge) {
            return NEOFORGE;
        }
        if (hasForge) {
            return FORGE;
        }
        if (hasFabric) {
            return FABRIC;
        }
        return UNKNOWN;
    }
}
