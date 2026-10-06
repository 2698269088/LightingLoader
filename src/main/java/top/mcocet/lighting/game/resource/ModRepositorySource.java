package top.mcocet.lighting.game.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;

import top.mcocet.lighting.resource.ModResources;
import top.mcocet.lighting.transform.LightingHooks;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Mod 资源包来源:把内核资源扫描({@code ModResources})登记的全部 Mod jar
 * 包装为原版 {@link Pack}({@code assets/} 与 {@code data/} 由同一 jar 承载),
 * 经 {@code GameResourceBridge} 合并进任何新建的 PackRepository。
 * <p>
 * 元数据直接合成(描述 = Mod ID、兼容性 = COMPATIBLE、无特性请求),
 * 不依赖 Mod jar 内的 {@code pack.mcmeta};{@link KnownPack} 以
 * {@code lighting} 命名空间标记,供选中逻辑识别加载器合成的包。
 */
public final class ModRepositorySource implements RepositorySource {

    private static final Logger LOGGER = LogManager.getLogger(ModRepositorySource.class);

    /** 包 ID 前缀(识别加载器合成的 Mod 包) */
    static final String PACK_ID_PREFIX = "lighting_mod_";

    /** KnownPack 命名空间(标记来源) */
    static final String KNOWN_PACK_NAMESPACE = "lighting";

    /** 合成包的版本标记 */
    private static final String SYNTHETIC_VERSION = "1";

    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        ModResources resources = LightingHooks.modResources();
        if (resources == null || resources.packs().isEmpty()) {
            return;
        }
        for (ModResources.ModResourcePack modPack : resources.packs()) {
            try {
                consumer.accept(createPack(modPack));
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to expose resource pack of {}: {}", modPack.modId(), e.toString());
            }
        }
    }

    /** 合成单个 Mod 包(元数据直构,不解析 pack.mcmeta) */
    static Pack createPack(ModResources.ModResourcePack modPack) {
        PackLocationInfo location = new PackLocationInfo(
                PACK_ID_PREFIX + modPack.modId(),
                Component.literal(modPack.modId()),
                PackSource.create(component -> Component.literal("Lighting Mod ").append(component), true),
                Optional.of(new KnownPack(KNOWN_PACK_NAMESPACE, modPack.modId(), SYNTHETIC_VERSION)));
        Pack.Metadata metadata = new Pack.Metadata(
                Component.literal(modPack.modId()),
                PackCompatibility.COMPATIBLE,
                FeatureFlagSet.of(),
                List.of());
        PackSelectionConfig selection = new PackSelectionConfig(false, Pack.Position.TOP, false);
        return new Pack(location, new ModJarResourcesSupplier(modPack.jarPath()), metadata, selection);
    }

    /** 以 Mod jar 为来源的 PackResources 提供器(懒打开 ZipFile) */
    private record ModJarResourcesSupplier(Path jarPath) implements Pack.ResourcesSupplier {

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new ModJarPackResources(location, jarPath);
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            return new ModJarPackResources(location, jarPath);
        }
    }
}
