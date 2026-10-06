package top.mcocet.api.registry;

import java.util.Objects;

/**
 * 注册表构建器——对应 NeoForge 的 {@code RegistryBuilder<T>}。
 * <p>
 * 两个使用窗口:
 * <ul>
 *   <li>{@code NewRegistryEvent}:{@code create(name)} 新建命名注册表
 *       (静态注册表,注册表立即建立,随后经 RegisterEvent 写入条目);</li>
 *   <li>{@code DataPackRegistryEvent}:{@code dataPackRegistry(name)}
 *       声明世界数据注册表(随存档加载,可同步到客户端)。</li>
 * </ul>
 * {@code codec}/{@code networkCodec} 为不透明对象(游戏侧实现为
 * Minecraft 的 Codec),API 层仅做登记与透传,不解析内容;
 * {@link #create()} 提交构建结果(幂等;事件收尾时会自动提交未显式调用者)。
 *
 * @param <T> 注册值类型
 */
public final class RegistryBuilder<T> {

    /** 注册表种类 */
    public enum Kind {
        /** 静态注册表:加载期写入,不随存档变化 */
        STATIC,
        /** 世界数据注册表:随存档加载/同步 */
        WORLD
    }

    /** 提交端(内核注册中心实现) */
    @FunctionalInterface
    public interface Registrar {
        void register(String name, Kind kind, boolean synced, Object codec, Object networkCodec);
    }

    private final String name;
    private final Kind kind;
    private final Registrar registrar;
    private boolean synced;
    private Object codec;
    private Object networkCodec;
    private boolean completed;

    public RegistryBuilder(String name, Kind kind, Registrar registrar) {
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    /** 注册表名(如 {@code examplemod:spell}) */
    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    /** 序列化编解码器(不透明对象;游戏侧为 Codec) */
    public RegistryBuilder<T> codec(Object codec) {
        this.codec = codec;
        return this;
    }

    /** 网络同步编解码器(不透明对象;不设置时不可同步) */
    public RegistryBuilder<T> networkCodec(Object networkCodec) {
        this.networkCodec = networkCodec;
        return this;
    }

    /** 是否同步到客户端(需同时提供 networkCodec) */
    public RegistryBuilder<T> sync(boolean synced) {
        this.synced = synced;
        return this;
    }

    public boolean synced() {
        return synced;
    }

    public Object codec() {
        return codec;
    }

    public Object networkCodec() {
        return networkCodec;
    }

    /** 提交构建结果(幂等) */
    public void create() {
        if (!completed) {
            completed = true;
            registrar.register(name, kind, synced, codec, networkCodec);
        }
    }

    /** 是否已提交 */
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public String toString() {
        return "RegistryBuilder[" + kind + " " + name + (completed ? ", completed" : "") + "]";
    }
}
