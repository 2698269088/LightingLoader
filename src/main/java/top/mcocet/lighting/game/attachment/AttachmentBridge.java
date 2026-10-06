package top.mcocet.lighting.game.attachment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import top.mcocet.lighting.attachment.AttachmentManager;
import top.mcocet.lighting.transform.LightingHooks;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体数据附件持久化桥:在 {@code Entity.save/load} 的原版路径上读写
 * 加载器附件数据。
 * <p>
 * 由 {@code AttachmentHookTransformer} 注入两处字节码调用本类:
 * <ul>
 *   <li>{@code Entity.save(ValueOutput)} 返回 true 前——
 *       {@link #onEntitySave} 把该实体(持有键 {@code entity:<uuid>})上
 *       全部附件写入独立的 {@code LightingAttachments} 子节点;</li>
 *   <li>{@code Entity.load(ValueInput)} 返回前——{@link #onEntityLoad}
 *       按内核已登记的类型逐个读回并导入注册中心
 *       ({@code ValueInput} 无法枚举键,只能遍历已知类型)。</li>
 * </ul>
 * 用原生 {@code putIntArray/getIntArray} 承载不透明字节:内核编译类路径
 * 不含 DataFixerUpper,不能引用 {@code Codec};无附件数据时不写任何节点,
 * 保存路径上的任何失败只记录警告,绝不中断游戏存档。
 */
public final class AttachmentBridge {

    private static final Logger LOGGER = LogManager.getLogger(AttachmentBridge.class);

    /** 实体数据中的附件子节点名 */
    public static final String TAG_KEY = "LightingAttachments";

    private AttachmentBridge() {
    }

    /** 实体保存锚点(注入 {@code Entity.save} 的 IRETURN 前,仅在返回 true 时调用) */
    public static void onEntitySave(Entity entity, ValueOutput output) {
        AttachmentManager manager = LightingHooks.attachmentManager();
        if (manager == null || manager.typeCount() == 0) {
            return;
        }
        try {
            Map<String, byte[]> data = manager.exportHolder(holderKey(entity));
            if (data.isEmpty()) {
                return;
            }
            ValueOutput child = output.child(TAG_KEY);
            for (Map.Entry<String, byte[]> entry : data.entrySet()) {
                child.putIntArray(entry.getKey(), packBytes(entry.getValue()));
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to persist attachments of {}: {}", entity.getUUID(), e.toString());
        }
    }

    /** 实体加载锚点(注入 {@code Entity.load} 的 RETURN 前) */
    public static void onEntityLoad(Entity entity, ValueInput input) {
        AttachmentManager manager = LightingHooks.attachmentManager();
        if (manager == null || manager.typeCount() == 0) {
            return;
        }
        try {
            ValueInput child = input.childOrEmpty(TAG_KEY);
            Map<String, byte[]> data = new LinkedHashMap<>();
            for (String typeId : manager.typeIds()) {
                child.getIntArray(typeId).ifPresent(packed -> {
                    byte[] bytes = unpackBytes(packed);
                    if (bytes != null) {
                        data.put(typeId, bytes);
                    }
                });
            }
            if (!data.isEmpty()) {
                manager.importHolder(holderKey(entity), data);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to load attachments of {}: {}", entity.getUUID(), e.toString());
        }
    }

    /** 持有键约定:实体为 {@code entity:<uuid>} */
    private static String holderKey(Entity entity) {
        return "entity:" + entity.getUUID();
    }

    /** 打包为 int 数组:{@code [length, 每 4 字节一个小端 int]} */
    private static int[] packBytes(byte[] bytes) {
        int[] packed = new int[1 + (bytes.length + 3) / 4];
        packed[0] = bytes.length;
        for (int i = 0; i < bytes.length; i++) {
            packed[1 + i / 4] |= (bytes[i] & 0xFF) << (8 * (i % 4));
        }
        return packed;
    }

    /** 解包;长度或容量不合法(数据损坏)时返回 null,跳过该附件 */
    private static byte[] unpackBytes(int[] packed) {
        if (packed.length == 0) {
            return null;
        }
        int length = packed[0];
        if (length < 0 || length > (packed.length - 1) * 4) {
            return null;
        }
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) ((packed[1 + i / 4] >>> (8 * (i % 4))) & 0xFF);
        }
        return bytes;
    }
}
