package top.mcocet.lighting.datagen;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.api.datagen.DataProvider;
import top.mcocet.api.datagen.DataWriter;
import top.mcocet.lighting.loader.ModContainer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据生成编排器——对应 NeoForge 的 {@code DataGenerator}(runData 任务核心):
 * 收集 {@code GatherDataEvent} 期间登记的全部 {@link DataProvider},
 * 为每个 provider 分配输出根 {@code <outputDir>/<modId>/<provider 名>}
 * 并依次执行,统计写入文件数。
 * <p>
 * 仅在 {@code --datagen} 模式下实例化并运行;输出目录默认
 * {@code <gameDir>/generated}(不写入源码树,由 Mod 作者按需拷贝)。
 */
public final class DataGenerator {

    private static final Logger LOGGER = LogManager.getLogger(DataGenerator.class);

    private final Set<String> modIds = new LinkedHashSet<>();
    private final Path outputDir;
    private final Map<String, List<DataProvider>> providers = new LinkedHashMap<>();

    public DataGenerator(List<ModContainer> mods, Path outputDir) {
        for (ModContainer mod : mods) {
            modIds.add(mod.id());
        }
        this.outputDir = outputDir;
    }

    /**
     * 登记一个数据生成器。
     *
     * @param modId    所属 Mod ID(必须已加载)
     * @param provider 数据生成器
     */
    public void addProvider(String modId, DataProvider provider) {
        if (!modIds.contains(modId)) {
            throw new IllegalArgumentException(
                    "Data provider " + provider.name() + " declared for unknown mod: " + modId);
        }
        providers.computeIfAbsent(modId, key -> new ArrayList<>()).add(provider);
    }

    /** 已登记的 provider 总数 */
    public int providerCount() {
        return providers.values().stream().mapToInt(List::size).sum();
    }

    public Path outputDir() {
        return outputDir;
    }

    /** 依次运行全部 provider,返回写入的文件总数 */
    public int run() throws IOException {
        int files = 0;
        for (Map.Entry<String, List<DataProvider>> entry : providers.entrySet()) {
            for (DataProvider provider : entry.getValue()) {
                DataWriter writer = new DataWriter(
                        outputDir.resolve(entry.getKey()).resolve(safeName(provider.name())));
                provider.run(writer);
                files += writer.writes();
                LOGGER.info("Data provider {} ({}): {} file(s) written to {}",
                        provider.name(), entry.getKey(), writer.writes(), writer.root());
            }
        }
        return files;
    }

    /** 输出目录名清理:provider 名中的路径分隔符与保留字符替换为下划线 */
    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
