package top.mcocet.api;

import java.util.Map;
import java.util.Set;

/**
 * 统一注册中心视图:加载期可写、加载完成冻结。
 * 冻结后任何 {@link #register} 调用都会抛出 {@link IllegalStateException}。
 */
public interface LightingRegistry {

    void register(String registryName, String id, Object value);

    Object get(String registryName, String id);

    int size(String registryName);

    Set<String> registryNames();

    boolean isFrozen();

    /**
     * 冻结时点的注册表快照(只读深拷贝):
     * 注册表名 -> (条目 ID -> 值);对应 NeoForge 的 RegistrySnapshot。
     */
    Map<String, Map<String, Object>> snapshot();
}
