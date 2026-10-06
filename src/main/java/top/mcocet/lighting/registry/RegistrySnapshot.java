package top.mcocet.lighting.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册表快照——对应 NeoForge 的 {@code RegistrySnapshot}。
 * <p>
 * 在冻结时点对全部注册表做深拷贝:冻结后内容不再变化,
 * 快照即为"进入游戏前已固定的注册表状态",供网络同步(一致性校验)
 * 与调试查看使用;游戏侧的真实注册表在 M4/M5 接入时以本快照为基准填充。
 */
public final class RegistrySnapshot {

    private final Map<String, Map<String, Object>> registries;
    private final int totalEntries;

    RegistrySnapshot(Map<String, Map<String, Object>> registries) {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        int entries = 0;
        for (Map.Entry<String, Map<String, Object>> registry : registries.entrySet()) {
            Map<String, Object> inner = new LinkedHashMap<>(registry.getValue());
            copy.put(registry.getKey(), Collections.unmodifiableMap(inner));
            entries += inner.size();
        }
        this.registries = Collections.unmodifiableMap(copy);
        this.totalEntries = entries;
    }

    /** 全部注册表的只读深拷贝:注册表名 -> (条目 ID -> 值) */
    public Map<String, Map<String, Object>> registries() {
        return registries;
    }

    public int registryCount() {
        return registries.size();
    }

    public int totalEntries() {
        return totalEntries;
    }

    /** 摘要文本,用于日志(如 "6 registries, 12 entries") */
    public String summary() {
        return registryCount() + " registries, " + totalEntries + " entries";
    }
}
