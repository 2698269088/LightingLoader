package top.mcocet.api;

import top.mcocet.api.registry.DeferredBlock;
import top.mcocet.api.registry.DeferredEntityType;
import top.mcocet.api.registry.DeferredHolder;
import top.mcocet.api.registry.DeferredItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * 延迟注册器——对应 NeoForge 的 {@code DeferredRegister}。
 * <p>
 * 语义:Mod 构造期创建注册器并登记条目(此时注册表尚未开放),
 * 条目在加载器的 RegisterEvent 阶段统一写入注册中心
 * (由内核调用 {@link #flushAll(EntrySink)},先于冻结)。
 * 登记返回 typed 持有者({@link DeferredHolder} 家族),
 * {@code get()} 在写入后即可解析出注册值。
 * <pre>{@code
 * LightingDeferredRegister items = LightingDeferredRegister.createItems("examplemod");
 * DeferredItem<Object> ruby = items.registerItem("ruby", () -> new RubyItem(...));
 * // 兼容形态(不特化):
 * LightingDeferredRegister blocks = LightingDeferredRegister.create("minecraft:block", "examplemod");
 * blocks.register("ruby_block", () -> new RubyBlock(...));
 * }</pre>
 */
public final class LightingDeferredRegister {

    /** 内核侧的写入端:RegistryManager.register(registryName, id, value) */
    @FunctionalInterface
    public interface EntrySink {
        void accept(String registryName, String id, Object value);
    }

    private static final List<LightingDeferredRegister> PENDING = new CopyOnWriteArrayList<>();

    private final String registryName;
    private final String modId;
    private final Map<String, DeferredHolder<?>> entries = new LinkedHashMap<>();
    private boolean flushed;

    private LightingDeferredRegister(String registryName, String modId) {
        this.registryName = Objects.requireNonNull(registryName, "registryName");
        this.modId = Objects.requireNonNull(modId, "modId");
    }

    /** 创建并登记一个延迟注册器(构造期调用;同一注册表可创建多个) */
    public static LightingDeferredRegister create(String registryName, String modId) {
        LightingDeferredRegister register = new LightingDeferredRegister(registryName, modId);
        PENDING.add(register);
        return register;
    }

    /** 方块注册表({@code minecraft:block})上的延迟注册器 */
    public static LightingDeferredRegister createBlocks(String modId) {
        return create(DeferredBlock.REGISTRY_NAME, modId);
    }

    /** 物品注册表({@code minecraft:item})上的延迟注册器 */
    public static LightingDeferredRegister createItems(String modId) {
        return create(DeferredItem.REGISTRY_NAME, modId);
    }

    /** 实体类型注册表({@code minecraft:entity_type})上的延迟注册器 */
    public static LightingDeferredRegister createEntityTypes(String modId) {
        return create(DeferredEntityType.REGISTRY_NAME, modId);
    }

    /**
     * 登记一个未特化的延迟条目;返回句柄可在写入后解析出注册值。
     * id 不含冒号时自动以 modId 作为命名空间补全(与 NeoForge DeferredRegister 语义一致),
     * 因此写入注册中心的键与查询用的全限定 ID 保持一致。
     */
    public DeferredEntry register(String id, Supplier<Object> value) {
        String qualified = qualify(id);
        DeferredEntry entry = new DeferredEntry(registryName, qualified, value);
        putEntry(qualified, entry);
        return entry;
    }

    /** 登记一个 typed 延迟持有者 */
    public <T> DeferredHolder<T> registerHolder(String id, Supplier<T> supplier) {
        String qualified = qualify(id);
        DeferredHolder<T> holder = DeferredHolder.create(registryName, qualified, supplier);
        putEntry(qualified, holder);
        return holder;
    }

    /** 登记一个 typed 方块持有者 */
    public <T> DeferredBlock<T> registerBlock(String id, Supplier<T> supplier) {
        String qualified = qualify(id);
        DeferredBlock<T> holder = DeferredBlock.create(registryName, qualified, supplier);
        putEntry(qualified, holder);
        return holder;
    }

    /** 登记一个 typed 物品持有者 */
    public <T> DeferredItem<T> registerItem(String id, Supplier<T> supplier) {
        String qualified = qualify(id);
        DeferredItem<T> holder = DeferredItem.create(registryName, qualified, supplier);
        putEntry(qualified, holder);
        return holder;
    }

    /** 登记一个 typed 实体类型持有者 */
    public <T> DeferredEntityType<T> registerEntityType(String id, Supplier<T> supplier) {
        String qualified = qualify(id);
        DeferredEntityType<T> holder = DeferredEntityType.create(registryName, qualified, supplier);
        putEntry(qualified, holder);
        return holder;
    }

    /** 校验并写入条目表(已刷写或重复 ID 时抛异常) */
    private void putEntry(String qualified, DeferredHolder<?> holder) {
        if (flushed) {
            throw new IllegalStateException(
                    "DeferredRegister for [%s] is already flushed, cannot register %s".formatted(registryName, qualified));
        }
        DeferredHolder<?> previous = entries.putIfAbsent(qualified, holder);
        if (previous != null) {
            throw new IllegalStateException("Duplicate deferred registration [%s] %s".formatted(registryName, qualified));
        }
    }

    /** 补全命名空间:不含冒号的 id 以 modId 为命名空间 */
    private String qualify(String id) {
        return id.indexOf(':') >= 0 ? id : modId + ":" + id;
    }

    /**
     * 内核在 RegisterEvent 阶段调用:把全部待处理注册器写入注册中心。
     *
     * @return 实际写入的条目数量
     */
    public static int flushAll(EntrySink sink) {
        List<LightingDeferredRegister> pending = new ArrayList<>(PENDING);
        PENDING.clear();
        int count = 0;
        for (LightingDeferredRegister register : pending) {
            count += register.flush(sink);
        }
        return count;
    }

    private int flush(EntrySink sink) {
        flushed = true;
        for (DeferredHolder<?> holder : entries.values()) {
            sink.accept(registryName, holder.id(), holder.get());
        }
        return entries.size();
    }

    public String registryName() {
        return registryName;
    }

    public String modId() {
        return modId;
    }

    /**
     * 未特化延迟条目句柄({@link DeferredHolder} 的兼容形态)。
     * 特化登记请使用 {@code registerHolder/registerBlock/registerItem/registerEntityType}。
     */
    public static final class DeferredEntry extends DeferredHolder<Object> {

        private DeferredEntry(String registryName, String id, Supplier<Object> supplier) {
            super(registryName, id, supplier);
        }

        /** 是否已解析出注册值(写入注册中心之后为 true) */
        public boolean isPresent() {
            return isBound();
        }
    }
}
