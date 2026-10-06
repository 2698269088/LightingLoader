package top.mcocet.lighting.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 统一注册中心。
 * <p>
 * 三种生态的注册 API 语义不同(Fabric 直接调用 {@code Registry.register},
 * Forge/NeoForge 用 {@code DeferredRegister} 绑定到特定生命周期事件),
 * 统一内核采用"加载期可写、加载完成冻结"的两阶段模型:
 * 无论 Mod 使用哪家 API 注册,最终都汇聚到本中心,由加载器在适当时机冻结,
 * 保证注册时机的一致性与可审计性。
 * <p>
 * 骨架阶段为内存实现;M4/M5 将把注册内容桥接到 Minecraft 真实注册表。
 */
public final class RegistryManager {

    private final Map<String, Map<String, Object>> registries = new LinkedHashMap<>();
    private final Set<String> worldRegistries = new java.util.LinkedHashSet<>();
    private final Map<String, WorldRegistryInfo> worldRegistryInfos = new LinkedHashMap<>();
    private RegistrySnapshot snapshot;
    private boolean frozen;

    /** 世界数据注册表声明信息:名称 + 是否同步 + 序列化/网络编解码器(不透明对象,可为 null) */
    public record WorldRegistryInfo(String name, boolean synced, Object codec, Object networkCodec) {
    }

    /**
     * 注册一个条目。仅允许在冻结前调用。
     *
     * @param registryName 注册表名(如 minecraft:item、minecraft:block)
     * @param id           条目 ID(如 examplemod:ruby)
     * @param value        注册对象(骨架阶段为占位对象,M4 起为游戏内真实对象)
     */
    public synchronized void register(String registryName, String id, Object value) {
        if (frozen) {
            throw new IllegalStateException(
                    "Registry [%s] is frozen, cannot register %s; all registrations must happen during mod loading"
                            .formatted(registryName, id));
        }
        registries.computeIfAbsent(registryName, key -> new LinkedHashMap<>()).put(id, value);
    }

    public synchronized Object get(String registryName, String id) {
        Map<String, Object> registry = registries.get(registryName);
        return registry != null ? registry.get(id) : null;
    }

    public synchronized int size(String registryName) {
        Map<String, Object> registry = registries.get(registryName);
        return registry != null ? registry.size() : 0;
    }

    /** 单个注册表的条目快照(只读;供游戏侧接管层遍历描述符) */
    public synchronized Map<String, Object> entries(String registryName) {
        Map<String, Object> registry = registries.get(registryName);
        if (registry == null || registry.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(registry));
    }

    public synchronized Set<String> registryNames() {
        return Collections.unmodifiableSet(registries.keySet());
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    /**
     * 移除一个条目(仅冻结前;对应 ModifyRegistriesEvent 的整表调整语义)。
     *
     * @return 是否确实移除了条目
     */
    public synchronized boolean remove(String registryName, String id) {
        requireMutable("remove");
        Map<String, Object> registry = registries.get(registryName);
        return registry != null && registry.remove(id) != null;
    }

    /** 移除整个注册表(仅冻结前) */
    public synchronized boolean removeRegistry(String registryName) {
        requireMutable("remove registry");
        return registries.remove(registryName) != null;
    }

    /**
     * 确保命名注册表存在(不存在时新建空表;仅冻结前,幂等)。
     * 对应 NewRegistryEvent.create(name) 的“建立注册表”语义。
     */
    public synchronized void ensureRegistry(String registryName) {
        requireMutable("create registry " + registryName);
        registries.computeIfAbsent(registryName, key -> new LinkedHashMap<>());
    }

    /**
     * 声明一个随世界存档加载的数据注册表
     * (由 DataPackRegistryEvent 调用,仅冻结前有效)。
     */
    public synchronized void declareWorldRegistry(String name) {
        declareWorldRegistry(name, false, null, null);
    }

    /**
     * 声明一个随世界存档加载的数据注册表,并登记同步与编解码器描述
     * (由 DataPackRegistryEvent 的构建器提交,仅冻结前有效)。
     *
     * @param synced       是否同步到客户端
     * @param codec        序列化编解码器(不透明对象,可为 null)
     * @param networkCodec 网络同步编解码器(不透明对象,可为 null)
     */
    public synchronized void declareWorldRegistry(String name, boolean synced, Object codec, Object networkCodec) {
        requireMutable("declare world-data registry");
        worldRegistries.add(name);
        worldRegistryInfos.put(name, new WorldRegistryInfo(name, synced, codec, networkCodec));
    }

    /** 世界数据注册表的声明信息;未声明时返回 null */
    public synchronized WorldRegistryInfo worldRegistryInfo(String name) {
        return worldRegistryInfos.get(name);
    }

    /** 已声明的世界数据注册表(只读) */
    public synchronized Set<String> worldRegistries() {
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(worldRegistries));
    }

    private void requireMutable(String action) {
        if (frozen) {
            throw new IllegalStateException(
                    "Registries are frozen, cannot %s; modifications must happen during mod loading".formatted(action));
        }
    }

    /**
     * 当前注册表快照:冻结时自动生成(对应 RegistrySnapshot);
     * 尚未冻结时按当前内容即时生成。
     */
    public synchronized RegistrySnapshot snapshot() {
        if (snapshot != null) {
            return snapshot;
        }
        return new RegistrySnapshot(registries);
    }

    /** 冻结所有注册表;由加载器在 Mod 加载完成后调用 */
    public synchronized void freeze() {
        snapshot = new RegistrySnapshot(registries);
        frozen = true;
    }
}
