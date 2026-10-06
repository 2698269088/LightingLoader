package top.mcocet.api.attachment;

/**
 * 附件序列化契约:把附件值编码为不透明字节、再解码回来。
 * <p>
 * API 层不能引用游戏类型(跨类加载器约束),因此使用字节契约;
 * 游戏侧实现可以直接借助加载器的 NBT 桥
 * ({@code top.mcocet.lighting.game.nbt.NbtBridge#toCompressedBytes})
 * 把 {@code CompoundTag} 用作附件载体,或自定义任意编码。
 */
public interface AttachmentSerializer<T> {

    /** 序列化:值 → 字节 */
    byte[] write(T value);

    /** 反序列化:字节 → 值 */
    T read(byte[] data);
}
