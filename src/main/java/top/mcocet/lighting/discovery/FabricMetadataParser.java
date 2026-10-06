package top.mcocet.lighting.discovery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.mcocet.lighting.loader.LoaderType;
import top.mcocet.lighting.model.DependencyKind;
import top.mcocet.lighting.model.ModDependency;
import top.mcocet.lighting.model.ModMetadata;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * fabric.mod.json 解析器。
 * <p>
 * 将 Fabric 元数据归一化为加载器统一的 {@link ModMetadata} 模型。
 * 参考 Fabric Loader 的 schemaVersion 1 清单结构。
 */
public final class FabricMetadataParser {

    private static final Map<String, DependencyKind> DEPENDENCY_KINDS = Map.of(
            "depends", DependencyKind.DEPENDS,
            "recommends", DependencyKind.RECOMMENDS,
            "suggests", DependencyKind.SUGGESTS,
            "conflicts", DependencyKind.CONFLICTS,
            "breaks", DependencyKind.BREAKS);

    private FabricMetadataParser() {
    }

    public static ModMetadata parse(byte[] data) {
        JsonObject root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();

        JsonElement idElement = root.get("id");
        if (idElement == null || idElement.getAsString().isBlank()) {
            throw new IllegalArgumentException("fabric.mod.json is missing required field: id");
        }

        ModMetadata.Builder builder = ModMetadata.builder(idElement.getAsString(), LoaderType.FABRIC)
                .name(optString(root, "name"))
                .version(optString(root, "version"))
                .description(optString(root, "description"))
                .license(optString(root, "license"))
                .environment(optString(root, "environment"));

        // authors:元素可以是字符串,也可能是 {name: ...} 对象
        JsonElement authors = root.get("authors");
        if (authors != null && authors.isJsonArray()) {
            for (JsonElement author : authors.getAsJsonArray()) {
                if (author.isJsonObject() && author.getAsJsonObject().has("name")) {
                    builder.addAuthor(author.getAsJsonObject().get("name").getAsString());
                } else if (author.isJsonPrimitive()) {
                    builder.addAuthor(author.getAsString());
                }
            }
        }

        // entrypoints:类型 -> 声明列表(字符串或 {value: ...} 对象)
        JsonElement entrypoints = root.get("entrypoints");
        if (entrypoints != null && entrypoints.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : entrypoints.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonArray()) {
                    for (JsonElement value : entry.getValue().getAsJsonArray()) {
                        builder.addEntrypoint(entry.getKey(), entrypointValueOf(value));
                    }
                }
            }
        }

        // mixins:既可能是扁平字符串数组,也可能是 {common/client/server: [...]} 对象
        JsonElement mixins = root.get("mixins");
        if (mixins != null) {
            if (mixins.isJsonArray()) {
                for (JsonElement mixin : mixins.getAsJsonArray()) {
                    builder.addMixinConfig(mixin.getAsString());
                }
            } else if (mixins.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : mixins.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        for (JsonElement mixin : entry.getValue().getAsJsonArray()) {
                            builder.addMixinConfig(mixin.getAsString());
                        }
                    }
                }
            }
        }

        // 五类依赖声明统一归入 dependencies
        DEPENDENCY_KINDS.forEach((key, kind) -> {
            JsonElement deps = root.get(key);
            if (deps != null && deps.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : deps.getAsJsonObject().entrySet()) {
                    builder.addDependency(new ModDependency(
                            entry.getKey(),
                            versionRangeOf(entry.getValue()),
                            kind,
                            kind == DependencyKind.DEPENDS || kind == DependencyKind.CONFLICTS,
                            top.mcocet.lighting.model.OrderingType.NONE,
                            top.mcocet.lighting.model.DependencySide.BOTH));
                }
            }
        });

        return builder.build();
    }

    /** 入口点值可能是字符串,也可能是带 value 字段的对象 */
    private static String entrypointValueOf(JsonElement element) {
        if (element.isJsonObject() && element.getAsJsonObject().has("value")) {
            return element.getAsJsonObject().get("value").getAsString();
        }
        return element.getAsString();
    }

    /**
     * 版本范围值可能是:字符串、字符串数组、{version: ...} 对象或其数组。
     * 统一取首个可用的版本字符串;无版本信息时返回空串。
     */
    private static String versionRangeOf(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            return array.isEmpty() ? "" : versionRangeOf(array.get(0));
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("version")) {
            return versionRangeOf(element.getAsJsonObject().get("version"));
        }
        return "";
    }

    private static String optString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
