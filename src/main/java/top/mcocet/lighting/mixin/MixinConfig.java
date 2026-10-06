package top.mcocet.lighting.mixin;

import java.util.List;

/**
 * 单个 Mixin 配置的解析结果(对应 Mixin 生态的 config JSON)。
 *
 * @param ownerModId   声明该配置的 Mod ID
 * @param configName   jar 内配置文件名(如 examplemod.mixins.json)
 * @param packageName  配置声明的 mixin 包名(可为空)
 * @param compatibilityLevel 兼容级别字段(如 JAVA_25,可为空)
 * @param refmap       refmap 名(可为空)
 * @param mixinClasses 全部声明的 mixin 类名(已按 package 补全为全限定名)
 */
public record MixinConfig(String ownerModId, String configName, String packageName,
                          String compatibilityLevel, String refmap, List<String> mixinClasses) {

    public MixinConfig {
        mixinClasses = List.copyOf(mixinClasses);
    }

    public String summary() {
        return "%s (%s): %d mixin class(es), compatibility=%s".formatted(
                configName, ownerModId, mixinClasses.size(),
                compatibilityLevel != null ? compatibilityLevel : "DEFAULT");
    }
}
