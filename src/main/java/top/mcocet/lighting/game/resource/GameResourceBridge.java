package top.mcocet.lighting.game.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;

import top.mcocet.lighting.resource.ModResources;
import top.mcocet.lighting.transform.LightingHooks;

import java.util.Arrays;

/**
 * 资源/数据包挂载桥:把 Mod jar 接入原版包仓库(PackRepository)。
 * <p>
 * 由 {@code PackSourceInjectTransformer} 注入三处字节码调用本类:
 * <ul>
 *   <li>{@code PackRepository.<init>} 入口——{@link #expandRepositorySources}
 *       把 {@link ModRepositorySource} 合并进来源数组,使 Mod 包出现在
 *       可用包列表(客户端资源包与数据包同一 jar 承载);</li>
 *   <li>{@code PackRepository.reload} 与 {@code setSelected} 的 RETURN 前——
 *       {@link #selectModPacks} 把全部 Mod 包补充进选中列表(尾部 =
 *       最高优先级,Mod 资源覆盖原版),使资源/数据真实生效。</li>
 * </ul>
 * 无任何 Mod 资源时完全不介入;选中补充是幂等的(已选中的包不会重复添加)。
 * 服务端数据必须加载、客户端资源包默认启用(与 NeoForge 语义一致)。
 */
public final class GameResourceBridge {

    private static final Logger LOGGER = LogManager.getLogger(GameResourceBridge.class);

    private GameResourceBridge() {
    }

    /** 合并来源数组(PackRepository 构造器入口);无 Mod 资源时原样返回 */
    public static RepositorySource[] expandRepositorySources(RepositorySource[] sources) {
        if (sources == null) {
            return null;
        }
        ModResources resources = LightingHooks.modResources();
        if (resources == null || resources.packs().isEmpty()) {
            return sources;
        }
        for (RepositorySource source : sources) {
            if (source instanceof ModRepositorySource) {
                return sources;
            }
        }
        RepositorySource[] expanded = Arrays.copyOf(sources, sources.length + 1);
        expanded[sources.length] = new ModRepositorySource();
        LOGGER.info("Mod resource repository source attached ({} mod pack(s) available)",
                resources.packs().size());
        return expanded;
    }

    /** 自动选中全部 Mod 包(reload/setSelected 返回前调用;幂等) */
    public static void selectModPacks(PackRepository repository) {
        if (repository == null) {
            return;
        }
        ModResources resources = LightingHooks.modResources();
        if (resources == null || resources.packs().isEmpty()) {
            return;
        }
        for (ModResources.ModResourcePack modPack : resources.packs()) {
            String id = ModRepositorySource.PACK_ID_PREFIX + modPack.modId();
            try {
                if (repository.isAvailable(id)) {
                    repository.addPack(id);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to select mod pack {}: {}", id, e.toString());
            }
        }
    }
}
