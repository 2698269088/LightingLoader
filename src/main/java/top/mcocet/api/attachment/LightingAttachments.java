package top.mcocet.api.attachment;

import java.util.Map;

/**
 * 统一数据附件视图(NeoForge DataAttachments 语义):
 * 在任意持有键({@code holderKey})上读写 typed 附件值。
 * <p>
 * 与注册中心/能力视图的区别:附件是"跟随持有者的持久化数据"——
 * 实体附件由游戏侧桥在实体 NBT 保存/加载时自动读写
 * ({@code AttachmentHookTransformer} 注入 {@code Entity.save/load}),
 * 因此附件值跨存档保留。
 * <p>
 * 持有键约定:实体为 {@code entity:<uuid>};其他持有形态由 Mod
 * 自行定义并管理其持久化。
 */
public interface LightingAttachments {

    /**
     * 登记附件类型;同一 ID 重复登记抛异常。
     * 需在收尾阶段(能力/附件注册窗口)完成,之后注册锁定。
     */
    <T> AttachmentType<T> register(String id, AttachmentSerializer<T> serializer);

    /** 读取附件值;未设置时返回 {@code null} */
    <T> T get(String holderKey, AttachmentType<T> type);

    /** 设置附件值({@code null} 等于移除) */
    <T> void set(String holderKey, AttachmentType<T> type, T value);

    /** 是否存在附件值 */
    <T> boolean has(String holderKey, AttachmentType<T> type);

    /** 移除附件值并返回旧值;未设置时返回 {@code null} */
    <T> T remove(String holderKey, AttachmentType<T> type);

    /** 指定持有键上的附件数量 */
    int size(String holderKey);

    /** 已登记的附件类型总数 */
    int typeCount();

    /** 有附件数据的持有键总数 */
    int holderCount();

    /**
     * 导出持有键上的全部附件(typeId → 序列化字节),
     * 供游戏侧桥写入实体 NBT;无数据返回空映射。
     */
    Map<String, byte[]> exportHolder(String holderKey);

    /**
     * 从序列化字节导入持有键的附件(游戏侧桥在实体加载时调用);
     * 未登记类型的数据被忽略。
     */
    void importHolder(String holderKey, Map<String, byte[]> data);

    /** 注册是否已锁定(锁定后拒绝新类型登记) */
    boolean isLocked();
}
