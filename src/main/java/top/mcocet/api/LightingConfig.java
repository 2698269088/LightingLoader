package top.mcocet.api;

import java.util.Optional;

/**
 * 统一配置视图:按 Mod ID 查询加载期已解析的配置项。
 * <p>
 * 配置在构造之后由加载器统一加载(缺失时生成默认文件),
 * Mod 可在 {@code ModConfigLoadEvent} 监听器中读取;查询键为
 * 扁平化路径(如 {@code enabled} 或 {@code section.key})。
 */
public interface LightingConfig {

    /** 查询某个 Mod 的配置项 */
    Optional<String> get(String modId, String key);
}
