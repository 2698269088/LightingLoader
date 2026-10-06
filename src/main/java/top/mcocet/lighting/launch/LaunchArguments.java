package top.mcocet.lighting.launch;

import top.mcocet.api.Dist;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动参数解析。
 * <p>
 * 已知的 {@code --key=value} 开关由 Lighting 消费;其余参数原样收集,
 * 在 {@code --launch} 模式下透传给游戏主类(如 --username、--version 等)。
 *
 * @param gameDir      游戏目录,默认 run
 * @param modsDir      Mod 目录,默认 gameDir/mods
 * @param gameJar      游戏本体 jar,默认 lib/Minecraft-26.1.2.jar
 * @param librariesDir 依赖库目录(启动器下载的 libraries),默认 gameDir/libraries
 * @param mainClass    游戏主类,dry-run 模式无需真实存在
 * @param launch       true 时真实移交游戏启动,否则执行 dry-run 转换验证
 * @param dist         运行侧(CLIENT/SERVER),由 --dist=server 切换,默认 CLIENT
 * @param datagen      true 时在加载收尾后执行数据生成并退出(对应 NeoForge 的 runData 任务)
 * @param gameArgs     透传给游戏主类的其余参数
 */
public record LaunchArguments(
        Path gameDir,
        Path modsDir,
        Path gameJar,
        Path librariesDir,
        String mainClass,
        boolean launch,
        Dist dist,
        boolean datagen,
        List<String> gameArgs) {

    public static final String DEFAULT_MAIN_CLASS = "net.minecraft.client.main.Main";

    public static LaunchArguments parse(String[] args) {
        Path gameDir = Path.of("run");
        Path modsDir = null;
        Path gameJar = Path.of("lib", "Minecraft-26.1.2.jar");
        Path librariesDir = null;
        String mainClass = DEFAULT_MAIN_CLASS;
        boolean launch = false;
        Dist dist = Dist.CLIENT;
        boolean datagen = false;
        List<String> gameArgs = new ArrayList<>();

        for (String arg : args) {
            if (arg.startsWith("--gameDir=")) {
                gameDir = Path.of(arg.substring("--gameDir=".length()));
            } else if (arg.startsWith("--modsDir=")) {
                modsDir = Path.of(arg.substring("--modsDir=".length()));
            } else if (arg.startsWith("--gameJar=")) {
                gameJar = Path.of(arg.substring("--gameJar=".length()));
            } else if (arg.startsWith("--libraries=")) {
                librariesDir = Path.of(arg.substring("--libraries=".length()));
            } else if (arg.startsWith("--mainClass=")) {
                mainClass = arg.substring("--mainClass=".length());
            } else if ("--launch".equals(arg)) {
                launch = true;
            } else if ("--datagen".equals(arg)) {
                datagen = true;
            } else if (arg.startsWith("--dist=")) {
                dist = "server".equalsIgnoreCase(arg.substring("--dist=".length()))
                        ? Dist.SERVER : Dist.CLIENT;
            } else {
                gameArgs.add(arg);
            }
        }

        if (modsDir == null) {
            modsDir = gameDir.resolve("mods");
        }
        if (librariesDir == null) {
            librariesDir = gameDir.resolve("libraries");
        }
        return new LaunchArguments(gameDir, modsDir, gameJar, librariesDir, mainClass, launch, dist,
                datagen, List.copyOf(gameArgs));
    }
}
