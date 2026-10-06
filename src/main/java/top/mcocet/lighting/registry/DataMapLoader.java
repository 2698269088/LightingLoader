package top.mcocet.lighting.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.lighting.loader.ModContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 数据映射加载器——对应 NeoForge 的 {@code DataMapLoader}。
 * <p>
 * 扫描每个 Mod jar 的 {@code data/<ns>/data_maps/<registry>/<map>.json}
 * (结构 {@code {"replace":bool,"values":{key:value}}}),按映射路径
 * {@code <registry>/<map>} 合并;{@code replace=true} 会清空此前来源的同名映射。
 * 加载完成后统计上报,供注册表条目在 M4/M5 挂载真实数据。
 */
public final class DataMapLoader {

    private static final Logger LOGGER = LogManager.getLogger(DataMapLoader.class);

    private static final String DATA_PREFIX = "data/";
    private static final String DATAMAPS_SEGMENT = "/data_maps/";

    /** 加载结果:映射路径 -> (键 -> 值) */
    public record Result(Map<String, Map<String, String>> dataMaps, int files, int entries) {
    }

    private DataMapLoader() {
    }

    /** 扫描并合并全部 Mod jar 中的数据映射文件 */
    public static Result load(List<ModContainer> mods) {
        Map<String, Map<String, String>> merged = new LinkedHashMap<>();
        int files = 0;
        for (ModContainer mod : mods) {
            files += loadFromJar(mod, merged);
        }
        int entries = merged.values().stream().mapToInt(Map::size).sum();
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        merged.forEach((key, value) -> result.put(key, Collections.unmodifiableMap(value)));
        LOGGER.info("Data map loading finished: {} file(s), {} map(s), {} entrie(s)",
                files, result.size(), entries);
        return new Result(Collections.unmodifiableMap(result), files, entries);
    }

    private static int loadFromJar(ModContainer mod, Map<String, Map<String, String>> merged) {
        int files = 0;
        try (ZipFile zip = new ZipFile(mod.jarPath().toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(DATA_PREFIX) || !name.contains(DATAMAPS_SEGMENT)
                        || !name.endsWith(".json")) {
                    continue;
                }
                int mapsIndex = name.indexOf(DATAMAPS_SEGMENT);
                if (mapsIndex <= DATA_PREFIX.length()) {
                    continue;
                }
                // data/<ns>/data_maps/<registry>/<map>.json -> <registry>/<map>
                String namespace = name.substring(DATA_PREFIX.length(), mapsIndex);
                String mapPath = namespace + ":" + name.substring(mapsIndex + DATAMAPS_SEGMENT.length(),
                        name.length() - ".json".length());
                try (InputStream in = zip.getInputStream(entry)) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
                    JsonObject values = root.has("values") ? root.getAsJsonObject("values") : new JsonObject();
                    Map<String, String> target = merged.computeIfAbsent(mapPath, key -> new LinkedHashMap<>());
                    if (replace) {
                        target.clear();
                    }
                    for (String key : values.keySet()) {
                        target.put(key, values.get(key).getAsString());
                    }
                    files++;
                } catch (Exception e) {
                    LOGGER.warn("Invalid data map file {} in {}: {}", name, mod.id(), e.toString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Data map scan failed for {}: {}", mod.id(), e.toString());
        }
        return files;
    }
}
