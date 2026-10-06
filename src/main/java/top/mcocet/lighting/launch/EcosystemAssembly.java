package top.mcocet.lighting.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 生态 API 库装配:把上游加载器的完整 API 面组件(NeoForge 本体的
 * universal jar、FML loader、事件总线 bus、distmarker 所在的 mergetool-api
 * 等)并入游戏侧类路径,使"集成上游属性或直接依赖上游本体"的 Mod
 * 可以在 Lighting 上原生解析、加载并进入构造流程。
 * <p>
 * 查找位置(按序合并,绝对路径去重):
 * <ol>
 *   <li>{@code <gameDir>/.lighting/ecosystem}:运行时生态库目录
 *       (与 JiJ 缓存的 {@code .lighting} 布局一致)</li>
 *   <li>加载器 jar 同级的 {@code ecosystem/} 目录:发布形态下
 *       启动器把生态库放置在加载器旁</li>
 * </ol>
 * 与 {@code libraries} 目录的分工:libraries 放 Minecraft 运行库
 * (启动器下载,含游戏依赖如 brigadier),这里只放"模拟上游加载器 API 面"
 * 的组件;两者在 {@link GameEnvironment} 中汇入同一类加载空间。
 */
public final class EcosystemAssembly {

    /** 运行时生态库目录的相对路径(gameDir 下) */
    public static final String RUNTIME_DIR = ".lighting/ecosystem";

    /** 发布形态下加载器 jar 同级的生态库目录名 */
    public static final String DIST_DIR = "ecosystem";

    private EcosystemAssembly() {
    }

    /**
     * 解析全部生态库 jar。
     *
     * @param gameDir        游戏目录(可为 null)
     * @param loaderSelfPath 加载器自身类路径根(jar 或 classes 目录,可为 null)
     * @return 排序后的 jar 列表(无生态库时为空列表)
     */
    public static List<Path> resolve(Path gameDir, Path loaderSelfPath) {
        Set<Path> jars = new LinkedHashSet<>();
        if (gameDir != null) {
            addJars(jars, gameDir.resolve(".lighting").resolve("ecosystem"));
        }
        if (loaderSelfPath != null) {
            Path root = Files.isDirectory(loaderSelfPath) ? loaderSelfPath : loaderSelfPath.getParent();
            if (root != null) {
                addJars(jars, root.resolve(DIST_DIR));
            }
        }
        return List.copyOf(jars);
    }

    private static void addJars(Set<Path> jars, Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .forEach(jars::add);
        } catch (IOException ignored) {
            // 目录不可读时跳过:生态库是可选组件,缺失时对应生态探测保持未组装状态
        }
    }

    /** 装配摘要(日志用):jar 文件名列表 */
    public static String summary(List<Path> jars) {
        if (jars.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (Path jar : jars) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(jar.getFileName());
        }
        return builder.toString();
    }
}
