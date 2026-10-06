package top.mcocet.lighting.capability;

import top.mcocet.api.capability.CapabilityProvider;
import top.mcocet.api.capability.LightingCapabilities;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 能力注册中心——对应 NeoForge 的 {@code CapabilityRegistry} /
 * {@code RegisterCapabilitiesEvent},以 typed 契约实现
 * {@link LightingCapabilities}。
 * <p>
 * 语义:收尾阶段由 {@link top.mcocet.lighting.event.RegisterCapabilitiesEvent}
 * 发布后,Mod 为方块实体/物品堆/实体注册能力提供器;声明结束后查询
 * 能力接口实例。API 层({@code top.mcocet.api.capability})定义 typed 接口
 * (energy/item/fluid),内核只做登记与查询调度,不引用游戏类型。
 * <p>
 * 上下文实例为不透明对象(方块实体/物品堆/实体),由查询侧提供、
 * 提供器自行判定是否支持;全部方法线程安全。
 */
public final class CapabilityManager implements LightingCapabilities {

    /** 域 -> (目标标识 -> (能力接口 -> 提供器列表));保留注册顺序 */
    private final Map<Domain, Map<String, Map<Class<?>, List<CapabilityProvider<?>>>>> providers =
            new EnumMap<>(Domain.class);

    /** 已登记的能力接口(供日志与自检) */
    private final Set<Class<?>> capabilityTypes = new LinkedHashSet<>();

    private boolean locked;

    @Override
    public synchronized <T> void register(Domain domain, String targetId, Class<T> capability,
                                          CapabilityProvider<T> provider) {
        if (locked) {
            throw new IllegalStateException(
                    "Capability registry is locked; register during the capability event");
        }
        if (domain == null || targetId == null || capability == null || provider == null) {
            throw new IllegalArgumentException("domain/targetId/capability/provider must not be null");
        }
        providers.computeIfAbsent(domain, key -> new LinkedHashMap<>())
                .computeIfAbsent(targetId, key -> new LinkedHashMap<>())
                .computeIfAbsent(capability, key -> new ArrayList<>())
                .add(provider);
        capabilityTypes.add(capability);
    }

    @Override
    public synchronized <T> T query(Domain domain, String targetId, Class<T> capability, Object context) {
        Map<String, Map<Class<?>, List<CapabilityProvider<?>>>> byTarget = providers.get(domain);
        if (byTarget == null) {
            return null;
        }
        T result = tryProviders(byTarget.get(targetId), capability, context);
        if (result != null) {
            return result;
        }
        return tryProviders(byTarget.get("*"), capability, context);
    }

    @Override
    public synchronized <T> T queryAny(Domain domain, Class<T> capability, Object context) {
        Map<String, Map<Class<?>, List<CapabilityProvider<?>>>> byTarget = providers.get(domain);
        if (byTarget == null) {
            return null;
        }
        for (Map<Class<?>, List<CapabilityProvider<?>>> byCapability : byTarget.values()) {
            T result = tryProviders(byCapability, capability, context);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public synchronized int providerCount(Domain domain) {
        Map<String, Map<Class<?>, List<CapabilityProvider<?>>>> byTarget = providers.get(domain);
        if (byTarget == null) {
            return 0;
        }
        int count = 0;
        for (Map<Class<?>, List<CapabilityProvider<?>>> byCapability : byTarget.values()) {
            for (List<CapabilityProvider<?>> list : byCapability.values()) {
                count += list.size();
            }
        }
        return count;
    }

    @Override
    public synchronized boolean isLocked() {
        return locked;
    }

    /** 锁定能力注册(收尾阶段之后由加载管线调用,拒绝后续注册) */
    public synchronized void lock() {
        locked = true;
    }

    /** 已登记的能力接口全名(只读;供日志与自检) */
    public synchronized Set<String> capabilityNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> type : capabilityTypes) {
            names.add(type.getName());
        }
        return names;
    }

    /** 全部域合计的提供器总数 */
    public synchronized int totalProviderCount() {
        int count = 0;
        for (Domain domain : Domain.values()) {
            count += providerCount(domain);
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static <T> T tryProviders(Map<Class<?>, List<CapabilityProvider<?>>> byCapability,
                                      Class<T> capability, Object context) {
        if (byCapability == null) {
            return null;
        }
        List<CapabilityProvider<?>> list = byCapability.get(capability);
        if (list == null) {
            return null;
        }
        for (CapabilityProvider<?> provider : list) {
            Object instance = provider.get(context);
            if (instance != null) {
                return (T) instance;
            }
        }
        return null;
    }
}
