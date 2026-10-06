package top.mcocet.api.attachment;

/**
 * 附件类型(NeoForge {@code AttachmentType} 语义):
 * 唯一标识 + 序列化契约。
 * <p>
 * 由 Mod 为具体数据形状创建(如 {@code examplemod:mana} 以 int 编码),
 * 经 {@code LightingAttachments.register} 登记后即可在任意持有键
 * ({@code holderKey})上读写;持有键由加载器侧约定
 * (实体为 {@code entity:<uuid>})。
 *
 * @param id         全局唯一标识(推荐 {@code namespace:path} 形式)
 * @param serializer 序列化契约(持久化时使用)
 */
public record AttachmentType<T>(String id, AttachmentSerializer<T> serializer) {

    public AttachmentType {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Attachment id must not be blank");
        }
        if (serializer == null) {
            throw new IllegalArgumentException("Attachment serializer must not be null");
        }
    }

    /** 创建附件类型 */
    public static <T> AttachmentType<T> create(String id, AttachmentSerializer<T> serializer) {
        return new AttachmentType<>(id, serializer);
    }
}
