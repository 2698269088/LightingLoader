package top.mcocet.lighting.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.lighting.loader.ModContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 标签加载器——对应加载器侧对原版 Tag 数据包的汇聚(Vanilla Tags 的 Mod 扩展)。
 * <p>
 * 扫描每个 Mod jar 的 {@code data/<ns>/tags/<registry>/<name>.json}
 * (结构 {@code {"replace":bool,"values":[...]}}),按标签路径
 * {@code <registry>/<name>} 合并全部来源的条目并去重;
 * {@code #引用} 条目按原始字符串保留。加载完成后统计上报,
 * 供游戏侧真实 Tag 容器(M4/M5)消费。
 */
public final class TagLoader {

    private static final Logger LOGGER = LogManager.getLogger(TagLoader.class);

    private static final String DATA_PREFIX = "data/";
    private static final String TAGS_SEGMENT = "/tags/";

    /** 加载结果 */
    public record Result(Map<String, List<String>> tags, int files, int entries) {
    }

    private TagLoader() {
    }

    /** 扫描并合并全部 Mod jar 中的标签文件 */
    public static Result load(List<ModContainer> mods) {
        Map<String, Set<String>> merged = new LinkedHashMap<>();
        int files = 0;
        for (ModContainer mod : mods) {
            files += loadFromJar(mod, merged);
        }
        Map<String, List<String>> tags = new LinkedHashMap<>();
        int entries = 0;
        for (Map.Entry<String, Set<String>> tag : merged.entrySet()) {
            List<String> values = List.copyOf(tag.getValue());
            tags.put(tag.getKey(), values);
            entries += values.size();
        }
        LOGGER.info("Tag loading finished: {} file(s), {} tag(s), {} entrie(s)", files, tags.size(), entries);
        return new Result(Collections.unmodifiableMap(tags), files, entries);
    }

    private static int loadFromJar(ModContainer mod, Map<String, Set<String>> merged) {
        int files = 0;
        try (ZipFile zip = new ZipFile(mod.jarPath().toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(DATA_PREFIX) || !name.contains(TAGS_SEGMENT)
                        || !name.endsWith(".json")) {
                    continue;
                }
                int tagsIndex = name.indexOf(TAGS_SEGMENT);
                if (tagsIndex <= DATA_PREFIX.length()) {
                    continue;
                }
                // data/<ns>/tags/<registry>/<name>.json -> <registry>/<name>
                String namespace = name.substring(DATA_PREFIX.length(), tagsIndex);
                String tagPath = namespace + ":" + name.substring(tagsIndex + TAGS_SEGMENT.length(),
                        name.length() - ".json".length());
                try (InputStream in = zip.getInputStream(entry)) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
                    JsonArray values = root.has("values") ? root.getAsJsonArray("values") : new JsonArray();
                    Set<String> target = merged.computeIfAbsent(tagPath, key -> new LinkedHashSet<>());
                    if (replace) {
                        target.clear();
                    }
                    for (int i = 0; i < values.size(); i++) {
                        target.add(values.get(i).getAsString());
                    }
                    files++;
                } catch (Exception e) {
                    LOGGER.warn("Invalid tag file {} in {}: {}", name, mod.id(), e.toString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Tag scan failed for {}: {}", mod.id(), e.toString());
        }
        return files;
    }

    /** 便于调试:标签路径列表(如 minecraft:item/rubies) */
    public static List<String> tagPaths(Result result) {
        return new ArrayList<>(result.tags().keySet());
    }
}
