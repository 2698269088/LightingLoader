package top.mcocet.lighting.game.registry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import top.mcocet.api.BlockDescriptor;
import top.mcocet.api.EntityDescriptor;
import top.mcocet.api.LightingContent;
import top.mcocet.lighting.game.entity.DescriptorEntity;
import top.mcocet.lighting.handoff.RegistryTakeoverReport;
import top.mcocet.lighting.registry.RegistryManager;

/**
 * 原版注册表注入器——"全面接管方块/实体注册"的执行器。
 * <p>
 * 数据流:Mod(纯描述符) → {@link RegistryManager}(内存注册中心,已冻结)
 * → 本类(实例化真实游戏对象) → {@code BuiltInRegistries.BLOCK/ENTITY_TYPE}。
 * 调用时机:内核注入到 {@code BuiltInRegistries.bootStrap()} 的钩子在
 * {@code freeze()} 前回调,此时原版内容已装载、注册表尚未冻结——
 * 这是自定义条目写入原版注册表的唯一合法窗口。
 */
public final class VanillaRegistryInjector {

    private static final Logger LOGGER = LogManager.getLogger(VanillaRegistryInjector.class);

    private VanillaRegistryInjector() {
    }

    /** 把注册中心中已冻结的全部方块/实体描述符写入原版注册表 */
    public static RegistryTakeoverReport inject(RegistryManager manager) {
        int blocks = 0;
        int entities = 0;
        int failures = 0;

        for (Map.Entry<String, Object> entry : manager.entries(LightingContent.BLOCK_REGISTRY).entrySet()) {
            if (!(entry.getValue() instanceof BlockDescriptor descriptor)) {
                continue;
            }
            try {
                injectBlock(descriptor);
                blocks++;
            } catch (Throwable t) {
                failures++;
                LOGGER.warn("Failed to inject block {}: {}", entry.getKey(), t.toString());
            }
        }

        for (Map.Entry<String, Object> entry : manager.entries(LightingContent.ENTITY_TYPE_REGISTRY).entrySet()) {
            if (!(entry.getValue() instanceof EntityDescriptor descriptor)) {
                continue;
            }
            try {
                injectEntity(descriptor);
                entities++;
            } catch (Throwable t) {
                failures++;
                LOGGER.warn("Failed to inject entity type {}: {}", entry.getKey(), t.toString());
            }
        }

        return new RegistryTakeoverReport(blocks, entities, failures);
    }

    /** 单个方块:描述符 → Properties → Block → BuiltInRegistries.BLOCK */
    private static void injectBlock(BlockDescriptor descriptor) {
        Identifier id = Identifier.parse(descriptor.id());
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);

        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .setId(key)
                .strength(descriptor.hardness(), descriptor.resistance())
                .sound(soundType(descriptor.sound()))
                .mapColor(mapColor(descriptor.mapColor()));
        if (!descriptor.occludes()) {
            properties.noOcclusion();
        }
        if (descriptor.lightLevel() > 0) {
            int level = descriptor.lightLevel();
            properties.lightLevel(state -> level);
        }
        if (descriptor.requiresTool()) {
            properties.requiresCorrectToolForDrops();
        }
        if (descriptor.randomTicks()) {
            properties.randomTicks();
        }

        Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties));
    }

    /** 单个实体:描述符 → EntityType.Builder → EntityType → BuiltInRegistries.ENTITY_TYPE */
    private static void injectEntity(EntityDescriptor descriptor) {
        Identifier id = Identifier.parse(descriptor.id());
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);

        EntityType.Builder<DescriptorEntity> builder = EntityType.Builder
                .of(DescriptorEntity::new, mobCategory(descriptor.category()))
                .sized(descriptor.width(), descriptor.height())
                .clientTrackingRange(descriptor.trackingRange())
                .updateInterval(descriptor.updateInterval());
        if (descriptor.fireImmune()) {
            builder.fireImmune();
        }
        if (descriptor.noSave()) {
            builder.noSave();
        }
        if (descriptor.noSummon()) {
            builder.noSummon();
        }

        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    /** 描述符分类名 → MobCategory(按序列化名匹配,未知回退 MISC) */
    private static MobCategory mobCategory(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (MobCategory category : MobCategory.values()) {
            if (category.getName().equals(normalized) || category.getSerializedName().equals(normalized)) {
                return category;
            }
        }
        return MobCategory.MISC;
    }

    /** 描述符音效名 → SoundType(未知回退石质) */
    private static SoundType soundType(String name) {
        return switch (name == null ? "" : name) {
            case "wood" -> SoundType.WOOD;
            case "grass" -> SoundType.GRASS;
            case "gravel" -> SoundType.GRAVEL;
            case "metal" -> SoundType.METAL;
            case "glass" -> SoundType.GLASS;
            case "wool" -> SoundType.WOOL;
            case "sand" -> SoundType.SAND;
            case "snow" -> SoundType.SNOW;
            case "empty" -> SoundType.EMPTY;
            default -> SoundType.STONE;
        };
    }

    /** 描述符颜色名 → MapColor(未知回退石质) */
    private static MapColor mapColor(String name) {
        return switch (name == null ? "" : name) {
            case "wood" -> MapColor.WOOD;
            case "plant" -> MapColor.PLANT;
            case "sand" -> MapColor.SAND;
            case "wool" -> MapColor.WOOL;
            case "metal" -> MapColor.METAL;
            case "ice" -> MapColor.ICE;
            case "dirt" -> MapColor.DIRT;
            case "grass" -> MapColor.GRASS;
            case "water" -> MapColor.WATER;
            case "clay" -> MapColor.CLAY;
            case "gold" -> MapColor.GOLD;
            case "quartz" -> MapColor.QUARTZ;
            case "nether" -> MapColor.NETHER;
            case "snow" -> MapColor.SNOW;
            case "fire" -> MapColor.FIRE;
            default -> MapColor.STONE;
        };
    }
}
