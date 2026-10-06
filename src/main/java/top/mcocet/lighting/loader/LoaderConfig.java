package top.mcocet.lighting.loader;

import java.nio.file.Path;

/**
 * 加载器运行配置。
 *
 * @param gameDir 游戏目录(.minecraft 所在目录或整合包运行目录)
 * @param modsDir Mod 存放目录,默认 gameDir/mods
 * @param configDir 配置目录,默认 gameDir/config(对应 FMLPaths.CONFIGDIR)
 */
public record LoaderConfig(Path gameDir, Path modsDir, Path configDir) {

    public LoaderConfig(Path gameDir, Path modsDir) {
        this(gameDir, modsDir, gameDir.resolve("config"));
    }

    public static LoaderConfig of(Path gameDir) {
        return new LoaderConfig(gameDir, gameDir.resolve("mods"));
    }

    public LoaderConfig {
        gameDir = gameDir.toAbsolutePath().normalize();
        modsDir = modsDir.toAbsolutePath().normalize();
        configDir = configDir.toAbsolutePath().normalize();
    }
}
