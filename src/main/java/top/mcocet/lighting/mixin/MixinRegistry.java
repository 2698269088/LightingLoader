package top.mcocet.lighting.mixin;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin 配置注册表:汇聚全部 Mod 声明并解析成功的 Mixin 配置。
 * <p>
 * 这是 Mixin 接入层的完成态——配置发现、解析、校验(清单声明但文件缺失、
 * 结构非法都会在加载期给出告警)全部在加载器侧完成;
 * Mixin 引擎(org.spongepowered:mixin 服务实现)与其他生态 API 一样
 * 随发行组装,装配时从本注册表取用配置清单。
 */
public final class MixinRegistry {

    private final List<MixinConfig> configs = new ArrayList<>();

    void add(MixinConfig config) {
        configs.add(config);
    }

    /** 全部已注册的配置(只读) */
    public List<MixinConfig> configs() {
        return List.copyOf(configs);
    }

    /** 配置总数 */
    public int configCount() {
        return configs.size();
    }

    /** 全部配置声明的 mixin 类总数 */
    public int totalMixinClasses() {
        return configs.stream().mapToInt(config -> config.mixinClasses().size()).sum();
    }

    /** 声明了配置的 Mod 数量 */
    public int modCount() {
        return (int) configs.stream().map(MixinConfig::ownerModId).distinct().count();
    }
}
