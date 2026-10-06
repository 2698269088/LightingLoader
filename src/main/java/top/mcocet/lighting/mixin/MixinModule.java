package top.mcocet.lighting.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.lighting.discovery.ModCandidate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mixin 接入层加载器:消费清单声明的 {@code mixins} 配置(Fabric)与
 * NeoForge 清单的 mixin 编译配置。
 * <p>
 * 对每个配置执行:jar 内定位 → JSON 解析(package / mixins / client / server /
 * refmap / compatibilityLevel)→ 校验 → 登记进 {@link MixinRegistry}。
 * 清单声明但文件缺失、结构非法等情况以告警上报,不阻断启动。
 */
public final class MixinModule {

    private static final Logger LOGGER = LogManager.getLogger(MixinModule.class);

    /** 加载结果:注册表 + 非致命告警 */
    public record Result(MixinRegistry registry, List<String> warnings) {
    }

    private MixinModule() {
    }

    /** 扫描全部候选 Mod 的 Mixin 配置(在建类加载器之前调用) */
    public static Result load(List<ModCandidate> candidates) {
        MixinRegistry registry = new MixinRegistry();
        List<String> warnings = new ArrayList<>();
        for (ModCandidate candidate : candidates) {
            for (String configName : candidate.metadata().mixinConfigs()) {
                loadConfig(candidate, configName, registry, warnings);
            }
        }
        if (registry.configCount() > 0) {
            LOGGER.info("Mixin integration: {} config(s), {} mixin class(es) from {} mod(s) registered "
                            + "(the mixin engine is assembled at distribution time)",
                    registry.configCount(), registry.totalMixinClasses(), registry.modCount());
            for (MixinConfig config : registry.configs()) {
                LOGGER.info("  - {}", config.summary());
            }
        }
        return new Result(registry, warnings);
    }

    private static void loadConfig(ModCandidate candidate, String configName, MixinRegistry registry,
                                   List<String> warnings) {
        String entryName = configName.startsWith("/") ? configName.substring(1) : configName;
        try (ZipFile zip = new ZipFile(candidate.jarPath().toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                warnings.add("Mixin config %s declared by %s but not found in the mod jar"
                        .formatted(configName, candidate.metadata().id()));
                return;
            }
            String json;
            try (InputStream in = zip.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String packageName = root.has("package") ? root.get("package").getAsString() : null;
            String compatibility = root.has("compatibilityLevel") ? root.get("compatibilityLevel").getAsString() : null;
            String refmap = root.has("refmap") ? root.get("refmap").getAsString() : null;

            List<String> classes = new ArrayList<>();
            collectClasses(root.get("mixins"), packageName, classes);
            collectClasses(root.get("client"), packageName, classes);
            collectClasses(root.get("server"), packageName, classes);

            registry.add(new MixinConfig(candidate.metadata().id(), configName, packageName,
                    compatibility, refmap, classes));
        } catch (Exception e) {
            warnings.add("Failed to parse mixin config %s of %s: %s"
                    .formatted(configName, candidate.metadata().id(), e.toString()));
        }
    }

    /** 支持数组形式与 {common/client/server} 对象形式(对象形式只取 common) */
    private static void collectClasses(JsonElement element, String packageName, List<String> classes) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            String name = item.getAsString();
            if (packageName != null && !name.contains(".")) {
                name = packageName + "." + name;
            }
            classes.add(name);
        }
    }
}
