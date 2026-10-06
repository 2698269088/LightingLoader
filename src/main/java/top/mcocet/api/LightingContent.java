package top.mcocet.api;

/**
 * 游戏内容登记门面——"全面接管方块/实体注册"的 Mod 侧入口。
 * <p>
 * Mod 在 RegisterEvent 阶段登记描述符;加载器在游戏启动后
 * ({@code BuiltInRegistries} 冻结前)由游戏侧接管层把描述符实例化为
 * 真实游戏对象并写入原版注册表,因此描述符延迟解析、无需 Mod 直接
 * 依赖任何游戏类。
 * <pre>{@code
 * LightingLoader.events().subscribe(RegisterEvent.class, event -> {
 *     LightingContent.registerBlock(BlockDescriptor.builder("examplemod:ruby_block")
 *             .hardness(3.0F).requiresTool(true).build());
 *     LightingContent.registerEntity(EntityDescriptor.builder("examplemod:ruby_golem")
 *             .category("creature").build());
 * });
 * }</pre>
 */
public final class LightingContent {

    /** 统一注册中心中方块内容的注册表名(映射到原版 BuiltInRegistries.BLOCK) */
    public static final String BLOCK_REGISTRY = "minecraft:block";

    /** 统一注册中心中实体类型内容的注册表名(映射到原版 BuiltInRegistries.ENTITY_TYPE) */
    public static final String ENTITY_TYPE_REGISTRY = "minecraft:entity_type";

    private LightingContent() {
    }

    /** 登记一个方块描述符(RegisterEvent 阶段调用;ID 重复会被注册中心冻结前语义拒绝) */
    public static void registerBlock(BlockDescriptor descriptor) {
        LightingLoader.registry().register(BLOCK_REGISTRY, descriptor.id(), descriptor);
    }

    /** 登记一个实体类型描述符(RegisterEvent 阶段调用) */
    public static void registerEntity(EntityDescriptor descriptor) {
        LightingLoader.registry().register(ENTITY_TYPE_REGISTRY, descriptor.id(), descriptor);
    }
}
