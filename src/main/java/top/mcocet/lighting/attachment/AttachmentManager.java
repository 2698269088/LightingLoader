package top.mcocet.lighting.attachment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import top.mcocet.api.attachment.AttachmentSerializer;
import top.mcocet.api.attachment.AttachmentType;
import top.mcocet.api.attachment.LightingAttachments;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 数据附件注册中心——对应 NeoForge 的 {@code AttachmentRegistry},
 * 以字节序列化契约实现 {@link LightingAttachments}。
 * <p>
 * 语义:收尾阶段由 {@link top.mcocet.lighting.event.RegisterCapabilitiesEvent}
 * 发布后,Mod 登记附件类型(值形状由 {@link AttachmentSerializer} 编码为
 * 不透明字节);声明结束后注册锁定。实体附件由游戏侧桥
 * ({@code top.mcocet.lighting.game.attachment.AttachmentBridge})
 * 在 {@code Entity.save/load} 的注入锚点上自动持久化,持有键约定为
 * {@code entity:<uuid>},因此附件值跨存档保留。
 * <p>
 * 全部方法线程安全;序列化/反序列化失败仅记录警告并跳过该附件,
 * 不影响存档流程。
 */
public final class AttachmentManager implements LightingAttachments {

    private static final Logger LOGGER = LogManager.getLogger(AttachmentManager.class);

    /** 已登记的附件类型(id -> 类型);保留注册顺序 */
    private final Map<String, AttachmentType<?>> types = new LinkedHashMap<>();

    /** 持有键 -> (类型 id -> 附件值);保留写入顺序 */
    private final Map<String, Map<String, Object>> holders = new LinkedHashMap<>();

    private boolean locked;

    @Override
    public synchronized <T> AttachmentType<T> register(String id, AttachmentSerializer<T> serializer) {
        if (locked) {
            throw new IllegalStateException(
                    "Attachment registry is locked; register during the attachment event");
        }
        AttachmentType<T> type = AttachmentType.create(id, serializer);
        if (types.containsKey(id)) {
            throw new IllegalStateException("Attachment id is already registered: " + id);
        }
        types.put(id, type);
        return type;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T> T get(String holderKey, AttachmentType<T> type) {
        Map<String, Object> values = holders.get(holderKey);
        return values == null ? null : (T) values.get(type.id());
    }

    @Override
    public synchronized <T> void set(String holderKey, AttachmentType<T> type, T value) {
        if (value == null) {
            remove(holderKey, type);
            return;
        }
        holders.computeIfAbsent(holderKey, key -> new LinkedHashMap<>()).put(type.id(), value);
    }

    @Override
    public synchronized <T> boolean has(String holderKey, AttachmentType<T> type) {
        Map<String, Object> values = holders.get(holderKey);
        return values != null && values.containsKey(type.id());
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T> T remove(String holderKey, AttachmentType<T> type) {
        Map<String, Object> values = holders.get(holderKey);
        if (values == null) {
            return null;
        }
        Object removed = values.remove(type.id());
        if (values.isEmpty()) {
            holders.remove(holderKey);
        }
        return (T) removed;
    }

    @Override
    public synchronized int size(String holderKey) {
        Map<String, Object> values = holders.get(holderKey);
        return values == null ? 0 : values.size();
    }

    @Override
    public synchronized int typeCount() {
        return types.size();
    }

    @Override
    public synchronized int holderCount() {
        return holders.size();
    }

    @Override
    public synchronized Map<String, byte[]> exportHolder(String holderKey) {
        Map<String, Object> values = holders.get(holderKey);
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, byte[]> data = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            AttachmentType<?> type = types.get(entry.getKey());
            if (type == null) {
                continue;
            }
            try {
                byte[] bytes = writeValue(type, entry.getValue());
                if (bytes != null) {
                    data.put(entry.getKey(), bytes);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to serialize attachment {} for {}: {}",
                        entry.getKey(), holderKey, e.toString());
            }
        }
        return data;
    }

    @Override
    public synchronized void importHolder(String holderKey, Map<String, byte[]> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Map<String, Object> values = holders.computeIfAbsent(holderKey, key -> new LinkedHashMap<>());
        for (Map.Entry<String, byte[]> entry : data.entrySet()) {
            AttachmentType<?> type = types.get(entry.getKey());
            if (type == null) {
                continue;
            }
            try {
                Object value = readValue(type, entry.getValue());
                if (value != null) {
                    values.put(entry.getKey(), value);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to deserialize attachment {} for {}: {}",
                        entry.getKey(), holderKey, e.toString());
            }
        }
        if (values.isEmpty()) {
            holders.remove(holderKey);
        }
    }

    @Override
    public synchronized boolean isLocked() {
        return locked;
    }

    /** 锁定附件注册(收尾阶段之后由加载管线调用,拒绝后续注册) */
    public synchronized void lock() {
        locked = true;
    }

    /** 已登记的附件类型 ID(只读;供游戏侧持久化桥遍历) */
    public synchronized Set<String> typeIds() {
        return new LinkedHashSet<>(types.keySet());
    }

    @SuppressWarnings("unchecked")
    private static byte[] writeValue(AttachmentType<?> type, Object value) {
        return ((AttachmentType<Object>) type).serializer().write(value);
    }

    @SuppressWarnings("unchecked")
    private static Object readValue(AttachmentType<?> type, byte[] data) {
        return ((AttachmentType<Object>) type).serializer().read(data);
    }
}
