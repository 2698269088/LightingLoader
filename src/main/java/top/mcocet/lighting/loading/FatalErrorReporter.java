package top.mcocet.lighting.loading;

import top.mcocet.lighting.loader.LoaderConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 致命错误报告器——对应 FML 的 {@code FatalErrorReporting}/{@code CrashReport}。
 * <p>
 * 当加载过程因致命问题中止时,把完整的问题清单与环境信息落盘到
 * {@code gameDir/crash-reports/lighting-fatal-时间戳.txt},
 * 供玩家与整合包作者定位;图形化错误屏(LoadingErrorScreen 对应物)
 * 在接入真实游戏环境后基于同一份数据呈现。
 */
public final class FatalErrorReporter {

    private FatalErrorReporter() {
    }

    /**
     * 写入致命错误报告。
     *
     * @return 报告文件路径
     */
    public static Path write(LoaderConfig config, String loaderVersion, String gameVersion,
                             List<ModLoadingIssue> fatalIssues) throws IOException {
        Path dir = config.gameDir().resolve("crash-reports");
        Files.createDirectories(dir);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        Path file = dir.resolve("lighting-fatal-" + stamp + ".txt");

        StringBuilder report = new StringBuilder();
        report.append("========== Lighting fatal mod loading report ==========\n");
        report.append("Time: ").append(LocalDateTime.now()).append('\n');
        report.append("Loader: Lighting ").append(loaderVersion).append('\n');
        report.append("Game version: ").append(gameVersion).append('\n');
        report.append("OS: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(" (")
                .append(System.getProperty("os.arch")).append(")\n");
        report.append("Java: ").append(System.getProperty("java.version")).append('\n');
        report.append("Game directory: ").append(config.gameDir()).append('\n');
        report.append('\n');
        report.append("Fatal issues (").append(fatalIssues.size()).append("):\n");
        for (ModLoadingIssue issue : fatalIssues) {
            report.append("  - [").append(issue.severity()).append("] ")
                    .append(issue.message()).append('\n');
        }
        report.append('\n');
        report.append("The game was not started. Fix the issues above and relaunch.\n");

        Files.writeString(file, report.toString(), StandardCharsets.UTF_8);
        return file;
    }
}
