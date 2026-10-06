package top.mcocet.lighting.discovery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Jar-in-Jar 嵌套 jar 扫描器——对应 NeoForge 的 {@code JarJar} 与
 * Fabric 的 {@code jar-in-jar} 机制:提取 Mod jar 内两种布局的嵌套
 * 依赖 jar 并并入游戏类路径:
 * <ul>
 *   <li>Fabric 布局:{@code META-INF/jars/*.jar} 下的全部条目;</li>
 *   <li>NeoForge 布局:{@code META-INF/jarjar/metadata.json} 中
 *       {@code jars[].path} 指向的条目。</li>
 * </ul>
 * 提取结果按内容 SHA-256 去重,缓存于 {@code <gameDir>/.lighting/jij},
 * 文件名为“内容哈希前 16 位 + 原始名”;仅在缓存缺失或长度不符时写盘。
 * 嵌套 jar 仅扩展类路径,不登记为独立 Mod(与 NeoForge JarJar 的
 * “依附于宿主 Mod”语义一致)。
 */
public final class JarInJarScanner {

    private static final Logger LOGGER = LogManager.getLogger(JarInJarScanner.class);

    /** Fabric 布局嵌套 jar 前缀 */
    public static final String NESTED_PREFIX = "META-INF/jars/";

    /** NeoForge 布局嵌套 jar 元数据 */
    public static final String JARJAR_METADATA = "META-INF/jarjar/metadata.json";

    private JarInJarScanner() {
    }

    /**
     * 提取全部 Mod jar 的嵌套 jar。
     *
     * @param modJars  已发现的 Mod jar 路径
     * @param cacheDir 提取缓存目录(通常为 {@code <gameDir>/.lighting/jij})
     * @return 提取出的嵌套 jar 路径(按发现顺序;单个 jar 失败时跳过并记录日志)
     */
    public static List<Path> extractAll(List<Path> modJars, Path cacheDir) {
        if (modJars.isEmpty()) {
            return List.of();
        }
        List<Path> extracted = new ArrayList<>();
        Set<String> seenHashes = new LinkedHashSet<>();
        for (Path jar : modJars) {
            try {
                extractFrom(jar, cacheDir, seenHashes, extracted);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Failed to scan nested jars in {}: {}", jar.getFileName(), e.toString());
            }
        }
        if (!extracted.isEmpty()) {
            LOGGER.info("Nested (jar-in-jar) jars extracted: {} from {} mod jar(s)",
                    extracted.size(), modJars.size());
        }
        return extracted;
    }

    private static void extractFrom(Path jar, Path cacheDir, Set<String> seenHashes, List<Path> extracted)
            throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String path : nestedEntryPaths(zip)) {
                ZipEntry entry = zip.getEntry(path);
                if (entry == null) {
                    LOGGER.warn("Nested jar entry declared but missing: {} (in {})", path, jar.getFileName());
                    continue;
                }
                byte[] data;
                try (InputStream in = zip.getInputStream(entry)) {
                    data = in.readAllBytes();
                }
                String hash = sha256Prefix(data);
                if (!seenHashes.add(hash)) {
                    continue; // 与先前嵌套 jar 内容相同(重复打包),跳过
                }
                String fileName = hash + "-" + sanitize(entry.getName());
                Path target = cacheDir.resolve(fileName);
                if (Files.isRegularFile(target) && Files.size(target) == data.length) {
                    extracted.add(target); // 缓存命中
                    continue;
                }
                Files.createDirectories(cacheDir);
                Files.write(target, data);
                extracted.add(target);
                LOGGER.info("Nested jar extracted: {} (from {})", target.getFileName(), jar.getFileName());
            }
        }
    }

    /** 收集单个 jar 内两种布局声明的嵌套 jar 条目路径(去重,保序) */
    private static Set<String> nestedEntryPaths(ZipFile zip) {
        Set<String> paths = new LinkedHashSet<>();
        zip.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.startsWith(NESTED_PREFIX)
                        && name.endsWith(".jar") && name.length() > NESTED_PREFIX.length())
                .sorted()
                .forEach(paths::add);
        ZipEntry metadata = zip.getEntry(JARJAR_METADATA);
        if (metadata != null) {
            try (InputStream in = zip.getInputStream(metadata)) {
                JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                JsonArray jars = root.getAsJsonArray("jars");
                if (jars != null) {
                    for (JsonElement element : jars) {
                        JsonObject nested = element.getAsJsonObject();
                        JsonElement path = nested.get("path");
                        if (path != null) {
                            paths.add(path.getAsString());
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Failed to read {} inside {}: {}", JARJAR_METADATA, zip.getName(), e.toString());
            }
        }
        return paths;
    }

    /** 内容 SHA-256 的前 16 个 hex 字符(前 8 字节),作为缓存文件名前缀 */
    private static String sha256Prefix(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** 清理文件名:取路径最后一段并把保留字符替换为下划线 */
    private static String sanitize(String name) {
        String base = name.substring(name.lastIndexOf('/') + 1);
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
