package top.mcocet.lighting.discovery;

import top.mcocet.lighting.loader.LoaderConfig;
import top.mcocet.lighting.loader.LoaderType;
import top.mcocet.lighting.model.ModMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mod 发现器:扫描 mods 目录,识别每个 Jar 的加载器归属并解析其清单,
 * 归一化为 {@link ModCandidate} 列表。
 * <p>
 * 识别策略(加载器不启动自身的情况下,由 Lighting 代替它们做"翻包"工作):
 * <ol>
 *   <li>Jar 内含 {@code META-INF/neoforge.mods.toml} → NeoForge</li>
 *   <li>Jar 内含 {@code META-INF/mods.toml} → Forge</li>
 *   <li>Jar 内含 {@code fabric.mod.json} → Fabric</li>
 * </ol>
 * 一个 Jar 携带多种清单时按 {@link LoaderType#primaryOf} 取主形态,
 * 其余记录为备选形态。重复 Mod ID 保留先扫描到的,其余记入警告。
 */
public final class ModDiscoverer {

    private final LoaderConfig config;
    private final List<String> warnings = new ArrayList<>();

    public ModDiscoverer(LoaderConfig config) {
        this.config = config;
    }

    /** 执行扫描;不抛异常,失败项以警告形式收集 */
    public List<ModCandidate> discover() {
        List<ModCandidate> result = new ArrayList<>();
        if (!Files.isDirectory(config.modsDir())) {
            warnings.add("Mods directory does not exist, skipping scan: " + config.modsDir());
            return result;
        }

        List<Path> jars;
        try (Stream<Path> stream = Files.list(config.modsDir())) {
            jars = stream
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            warnings.add("Failed to scan mods directory: " + e.getMessage());
            return result;
        }

        Map<String, Path> occupiedIds = new HashMap<>();
        for (Path jar : jars) {
            try {
                ModCandidate candidate = inspect(jar);
                if (candidate == null) {
                    warnings.add("No loader manifest found, ignored: " + jar.getFileName());
                    continue;
                }
                Path previous = occupiedIds.putIfAbsent(candidate.metadata().id(), jar);
                if (previous != null) {
                    warnings.add("Duplicate mod ID '%s': keeping %s, ignoring %s".formatted(
                            candidate.metadata().id(), previous.getFileName(), jar.getFileName()));
                    continue;
                }
                result.add(candidate);
            } catch (Exception e) {
                warnings.add("Failed to parse %s: %s".formatted(jar.getFileName(), e.getMessage()));
            }
        }
        return result;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    /** 检查单个 Jar;无任何加载器清单时返回 null */
    private ModCandidate inspect(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<LoaderType> present = new ArrayList<>();
            for (LoaderType type : List.of(LoaderType.NEOFORGE, LoaderType.FORGE, LoaderType.FABRIC)) {
                if (zip.getEntry(type.metadataFile()) != null) {
                    present.add(type);
                }
            }
            if (present.isEmpty()) {
                return null;
            }

            LoaderType primary = LoaderType.primaryOf(present);
            byte[] data = readEntry(zip, primary.metadataFile());
            ModMetadata metadata = switch (primary) {
                case FABRIC -> FabricMetadataParser.parse(data);
                case FORGE, NEOFORGE -> ForgeMetadataParser.parse(data, primary);
                default -> throw new IllegalStateException("Unsupported manifest type: " + primary);
            };
            return new ModCandidate(jar, metadata, List.copyOf(present));
        }
    }

    private static byte[] readEntry(ZipFile zip, String entryName) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }
}
