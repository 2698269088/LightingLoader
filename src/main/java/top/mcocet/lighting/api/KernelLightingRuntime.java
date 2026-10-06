package top.mcocet.lighting.api;

import top.mcocet.api.Dist;
import top.mcocet.api.EventPriority;
import top.mcocet.api.LightingConfig;
import top.mcocet.api.LightingEventBus;
import top.mcocet.api.LightingMod;
import top.mcocet.api.LightingNetwork;
import top.mcocet.api.LightingRegistry;
import top.mcocet.api.LightingRuntime;
import top.mcocet.api.attachment.AttachmentSerializer;
import top.mcocet.api.attachment.AttachmentType;
import top.mcocet.api.attachment.LightingAttachments;
import top.mcocet.api.capability.CapabilityProvider;
import top.mcocet.api.capability.LightingCapabilities;
import top.mcocet.api.network.PayloadData;
import top.mcocet.lighting.attachment.AttachmentManager;
import top.mcocet.lighting.capability.CapabilityManager;
import top.mcocet.lighting.config.ConfigTracker;
import top.mcocet.lighting.event.EventBus;
import top.mcocet.lighting.loader.ModContainer;
import top.mcocet.lighting.network.NetworkPayloadRegistry;
import top.mcocet.lighting.registry.RegistryManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * {@link LightingRuntime} 的内核实现:把内核服务(事件总线、注册中心、
 * Mod 容器列表)适配为 top.mcocet.api 的对外视图,由启动入口安装到
 * LightingLoader 门面。
 */
public final class KernelLightingRuntime implements LightingRuntime {

    private final String loaderName;
    private final String loaderVersion;
    private final String gameVersion;
    private final Dist dist;
    private final List<LightingMod> mods;
    private final LightingEventBus busView;
    private final LightingRegistry registryView;
    private final LightingConfig configView;
    private final LightingNetwork networkView;
    private final LightingCapabilities capabilitiesView;
    private final LightingAttachments attachmentsView;

    public KernelLightingRuntime(String loaderName, String loaderVersion, String gameVersion, Dist dist,
                                 EventBus eventBus, RegistryManager registryManager,
                                 NetworkPayloadRegistry networkRegistry, CapabilityManager capabilityManager,
                                 AttachmentManager attachmentManager,
                                 ConfigTracker configTracker,
                                 List<ModContainer> containers) {
        this.loaderName = loaderName;
        this.loaderVersion = loaderVersion;
        this.gameVersion = gameVersion;
        this.dist = dist;
        this.mods = containers.stream().map(ModView::new).map(LightingMod.class::cast).toList();

        this.busView = new LightingEventBus() {
            @Override
            public <T> void subscribe(Class<T> eventType, EventPriority priority, Consumer<T> listener) {
                eventBus.register(eventType, priority, listener);
            }

            @Override
            public <T> T post(T event) {
                return eventBus.post(event);
            }
        };

        this.registryView = new LightingRegistry() {
            @Override
            public void register(String registryName, String id, Object value) {
                registryManager.register(registryName, id, value);
            }

            @Override
            public Object get(String registryName, String id) {
                return registryManager.get(registryName, id);
            }

            @Override
            public int size(String registryName) {
                return registryManager.size(registryName);
            }

            @Override
            public Set<String> registryNames() {
                return registryManager.registryNames();
            }

            @Override
            public boolean isFrozen() {
                return registryManager.isFrozen();
            }

            @Override
            public Map<String, Map<String, Object>> snapshot() {
                return registryManager.snapshot().registries();
            }
        };

        this.configView = (modId, key) -> configTracker.value(modId, key);

        this.networkView = new LightingNetwork() {
            @Override
            public void registerPayload(String id, boolean clientbound, boolean serverbound) {
                networkRegistry.registerPayload(id, clientbound, serverbound);
            }

            @Override
            public void registerPayload(String id, boolean clientbound, boolean serverbound, String protocol) {
                networkRegistry.registerPayload(id, clientbound, serverbound, protocol);
            }

            @Override
            public void registerHandler(String channel, Consumer<PayloadData> handler) {
                networkRegistry.registerHandler(channel, handler);
            }

            @Override
            public boolean isLocked() {
                return networkRegistry.isLocked();
            }

            @Override
            public int payloadCount() {
                return networkRegistry.payloadCount();
            }

            @Override
            public int handlerCount() {
                return networkRegistry.handlerCount();
            }
        };

        this.capabilitiesView = new LightingCapabilities() {
            @Override
            public <T> void register(Domain domain, String targetId, Class<T> capability,
                                     CapabilityProvider<T> provider) {
                capabilityManager.register(domain, targetId, capability, provider);
            }

            @Override
            public <T> T query(Domain domain, String targetId, Class<T> capability, Object context) {
                return capabilityManager.query(domain, targetId, capability, context);
            }

            @Override
            public <T> T queryAny(Domain domain, Class<T> capability, Object context) {
                return capabilityManager.queryAny(domain, capability, context);
            }

            @Override
            public int providerCount(Domain domain) {
                return capabilityManager.providerCount(domain);
            }

            @Override
            public boolean isLocked() {
                return capabilityManager.isLocked();
            }
        };

        this.attachmentsView = new LightingAttachments() {
            @Override
            public <T> AttachmentType<T> register(String id, AttachmentSerializer<T> serializer) {
                return attachmentManager.register(id, serializer);
            }

            @Override
            public <T> T get(String holderKey, AttachmentType<T> type) {
                return attachmentManager.get(holderKey, type);
            }

            @Override
            public <T> void set(String holderKey, AttachmentType<T> type, T value) {
                attachmentManager.set(holderKey, type, value);
            }

            @Override
            public <T> boolean has(String holderKey, AttachmentType<T> type) {
                return attachmentManager.has(holderKey, type);
            }

            @Override
            public <T> T remove(String holderKey, AttachmentType<T> type) {
                return attachmentManager.remove(holderKey, type);
            }

            @Override
            public int size(String holderKey) {
                return attachmentManager.size(holderKey);
            }

            @Override
            public int typeCount() {
                return attachmentManager.typeCount();
            }

            @Override
            public int holderCount() {
                return attachmentManager.holderCount();
            }

            @Override
            public Map<String, byte[]> exportHolder(String holderKey) {
                return attachmentManager.exportHolder(holderKey);
            }

            @Override
            public void importHolder(String holderKey, Map<String, byte[]> data) {
                attachmentManager.importHolder(holderKey, data);
            }

            @Override
            public boolean isLocked() {
                return attachmentManager.isLocked();
            }
        };
    }

    @Override
    public String loaderName() {
        return loaderName;
    }

    @Override
    public String loaderVersion() {
        return loaderVersion;
    }

    @Override
    public String gameVersion() {
        return gameVersion;
    }

    @Override
    public Dist dist() {
        return dist;
    }

    @Override
    public List<LightingMod> mods() {
        return mods;
    }

    @Override
    public LightingEventBus events() {
        return busView;
    }

    @Override
    public LightingRegistry registry() {
        return registryView;
    }

    @Override
    public LightingConfig config() {
        return configView;
    }

    @Override
    public LightingNetwork network() {
        return networkView;
    }

    @Override
    public LightingCapabilities capabilities() {
        return capabilitiesView;
    }

    @Override
    public LightingAttachments attachments() {
        return attachmentsView;
    }

    /** ModContainer 到 LightingMod 的只读适配 */
    private record ModView(ModContainer container) implements LightingMod {

        @Override
        public String id() {
            return container.id();
        }

        @Override
        public String name() {
            return container.metadata().name();
        }

        @Override
        public String version() {
            return container.metadata().version();
        }

        @Override
        public String loaderType() {
            return container.loaderType().name();
        }

        @Override
        public String environment() {
            return container.metadata().environment();
        }

        @Override
        public String jarPath() {
            return container.jarPath().toString();
        }
    }
}
