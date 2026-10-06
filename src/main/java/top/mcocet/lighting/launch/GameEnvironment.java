package top.mcocet.lighting.launch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 游戏运行环境:描述"哪些字节码属于游戏侧"以及它们的来源。
 * <p>
 * 类路径由三部分组装(游戏类由此与 Mod 类汇入同一个
 * {@link GameClassLoader} 空间):
 * <ol>
 *   <li>游戏本体 jar(如 lib/Minecraft-26.1.2.jar)</li>
 *   <li>依赖库目录(启动器下载的 libraries 中的全部 jar)</li>
 *   <li>已发现的全部 Mod jar</li>
 * </ol>
 */
public final class GameEnvironment {

    private final Path gameDir;
    private final Path gameJar;
    private final Path loaderSelfPath;
    private final List<Path> libraries;
    private final List<Path> modJars;

    public GameEnvironment(Path gameDir, Path gameJar, List<Path> libraries, List<Path> modJars) {
        this(gameDir, gameJar, null, libraries, modJars);
    }

    /**
     * @param loaderSelfPath 加载器自身类路径根(jar 或 classes 目录),
     *                       供游戏侧加载器加载 {@code lighting.game} 包(UI/钩子代码)
     */
    public GameEnvironment(Path gameDir, Path gameJar, Path loaderSelfPath,
                           List<Path> libraries, List<Path> modJars) {
        this.gameDir = gameDir;
        this.gameJar = gameJar;
        this.loaderSelfPath = loaderSelfPath;
        this.libraries = List.copyOf(libraries);
        this.modJars = List.copyOf(modJars);
    }

    public Path gameDir() {
        return gameDir;
    }

    public Path gameJar() {
        return gameJar;
    }

    public Path loaderSelfPath() {
        return loaderSelfPath;
    }

    public List<Path> libraries() {
        return libraries;
    }

    public List<Path> modJars() {
        return modJars;
    }

    /** 组装类加载器用的 URL 列表 */
    public URL[] classpathUrls() {
        List<URL> urls = new ArrayList<>();
        try {
            if (Files.isRegularFile(gameJar)) {
                urls.add(gameJar.toUri().toURL());
            }
            if (loaderSelfPath != null && Files.exists(loaderSelfPath)) {
                urls.add(loaderSelfPath.toUri().toURL());
            }
            for (Path library : libraries) {
                urls.add(library.toUri().toURL());
            }
            for (Path modJar : modJars) {
                urls.add(modJar.toUri().toURL());
            }
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
        return urls.toArray(URL[]::new);
    }

    /** 从游戏 jar 中读取一个类的原始字节;不存在返回 null */
    public byte[] readGameClass(String className) throws IOException {
        if (!Files.isRegularFile(gameJar)) {
            return null;
        }
        String entryName = className.replace('.', '/') + ".class";
        try (ZipFile zip = new ZipFile(gameJar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    /** 读取游戏 jar 内 version.json 声明的游戏版本(如 "26.1.2");读不到返回 null */
    public String gameVersion() {
        if (!Files.isRegularFile(gameJar)) {
            return null;
        }
        try (ZipFile zip = new ZipFile(gameJar.toFile())) {
            ZipEntry entry = zip.getEntry("version.json");
            if (entry == null) {
                return null;
            }
            String json;
            try (InputStream in = zip.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("id")) {
                return root.get("id").getAsString();
            }
            return root.has("name") ? root.get("name").getAsString() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** 读取游戏 jar 内 version.json 声明的 Java 版本;读不到返回 null */
    public Integer requiredJavaVersion() {
        if (!Files.isRegularFile(gameJar)) {
            return null;
        }
        try (ZipFile zip = new ZipFile(gameJar.toFile())) {
            ZipEntry entry = zip.getEntry("version.json");
            if (entry == null) {
                return null;
            }
            String json;
            try (InputStream in = zip.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root.has("java_version") ? root.get("java_version").getAsInt() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** 当前 JVM 低于游戏要求时返回警告文本,否则返回 null */
    public String javaVersionWarning() {
        Integer required = requiredJavaVersion();
        if (required == null) {
            return null;
        }
        int current = Runtime.version().feature();
        if (current < required) {
            return "Game requires Java %d but the current JVM is Java %d; launch will be rejected".formatted(required, current);
        }
        return null;
    }
}
