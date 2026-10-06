package top.mcocet.lighting.discovery;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import top.mcocet.lighting.loader.LoaderType;
import top.mcocet.lighting.model.DependencyKind;
import top.mcocet.lighting.model.DependencySide;
import top.mcocet.lighting.model.ModDependency;
import top.mcocet.lighting.model.ModMetadata;
import top.mcocet.lighting.model.OrderingType;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Forge 系 TOML 清单解析器,同时服务 Forge(mode 为 FORGE)与
 * NeoForge(mode 为 NEOFORGE)。
 * <p>
 * 两者清单格式一致(NeoForge 1.20.5+ 仅更换了文件名并新增少量字段),
 * 因此共用一个实现,由调用方传入 {@link LoaderType} 区分归属。
 * <p>
 * 注意:tomlj 的文本 API 遇到错误输入返回 null 而不抛异常,
 * 本解析器对缺失字段采取"可选字段容忍、核心字段报错"的策略。
 */
public final class ForgeMetadataParser {

    private ForgeMetadataParser() {
    }

    public static ModMetadata parse(byte[] data, LoaderType loaderType) {
        if (loaderType != LoaderType.FORGE && loaderType != LoaderType.NEOFORGE) {
            throw new IllegalArgumentException("Forge-family manifest parser only supports FORGE/NEOFORGE, got: " + loaderType);
        }

        TomlParseResult result;
        try {
            result = Toml.parse(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read TOML: " + e.getMessage(), e);
        }
        if (result.hasErrors()) {
            throw new IllegalArgumentException("TOML parse failed: " + result.errors());
        }

        TomlArray modsArray = result.getArray("mods");
        if (modsArray == null || modsArray.isEmpty()) {
            throw new IllegalArgumentException("Manifest is missing the [[mods]] section, cannot determine mod ID");
        }
        TomlTable mod = modsArray.getTable(0);
        String modId = mod != null ? mod.getString("modId") : null;
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("[[mods]] is missing required field: modId");
        }

        ModMetadata.Builder builder = ModMetadata.builder(modId, loaderType)
                .version(mod != null ? mod.getString("version") : null)
                .license(result.getString("license"));

        if (mod != null) {
            builder.name(mod.getString("displayName"));
            builder.description(mod.getString("description"));
            builder.putExtra("modLoader", result.getString("modLoader"));
            builder.putExtra("loaderVersion", result.getString("loaderVersion"));
            builder.putExtra("logoFile", mod.getString("logoFile"));
            builder.putExtra("issueTrackerURL", result.getString("issueTrackerURL"));
            builder.putExtra("updateJSONURL", result.getString("updateJSONURL"));
            builder.putExtra("displayTest", result.getString("displayTest"));

            // Forge 系的 authors 为逗号分隔字符串
            String authors = mod.getString("authors");
            if (authors != null) {
                for (String author : authors.split(",")) {
                    builder.addAuthor(author.trim());
                }
            }
        }

        // [[dependencies.<modId>]]:键为声明方 Mod ID,值为依赖表数组
        TomlTable dependencies = result.getTable("dependencies");
        if (dependencies != null) {
            for (String owner : dependencies.keySet()) {
                TomlArray entries = dependencies.getArray(owner);
                if (entries == null) {
                    continue;
                }
                for (int i = 0; i < entries.size(); i++) {
                    TomlTable entry = entries.getTable(i);
                    if (entry == null) {
                        continue;
                    }
                    String depModId = entry.getString("modId");
                    if (depModId == null || depModId.isBlank()) {
                        continue;
                    }
                    Boolean mandatory = entry.getBoolean("mandatory");
                    boolean isMandatory = mandatory == null || mandatory;
                    builder.addDependency(new ModDependency(
                            depModId,
                            entry.getString("versionRange") != null ? entry.getString("versionRange") : "",
                            isMandatory ? DependencyKind.DEPENDS : DependencyKind.RECOMMENDS,
                            isMandatory,
                            OrderingType.fromToml(entry.getString("ordering")),
                            DependencySide.fromToml(entry.getString("side"))));
                }
            }
        }

        return builder.build();
    }
}
