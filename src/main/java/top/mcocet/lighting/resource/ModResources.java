package top.mcocet.lighting.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.lighting.loader.ModContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mod 资源扫描——对应 {@code ResourcePackLoader} 与 {@code LanguageHook} 的加载侧职责。
 * <p>
 * 扫描每个 Mod jar 的三类资源:
 * <ul>
 *   <li>客户端资源包来源({@code assets/} 下存在文件)——登记为可挂载的 Mod 资源包;</li>
 *   <li>内置语言表:{@code assets/<ns>/lang/en_us.json} 的键值合并(供游戏侧
 *       {@code LanguageHook} 对应物取用);</li>
 *   <li>数据包来源({@code data/} 下存在文件)——登记为可挂载的 Mod 数据包。</li>
 * </ul>
 * 资源包/数据包在接入真实游戏环境后由游戏侧的包仓库(PackRepository)消费。
 */
public final class ModResources {

    private static final Logger LOGGER = LogManager.getLogger(ModResources.class);

    /** 单个 Mod 的资源概况 */
    public record ModResourcePack(String modId, Path jarPath, boolean clientAssets, boolean serverData) {
    }

    private final List<ModResourcePack> packs = new ArrayList<>();
    private final Map<String, String> languageTable = new LinkedHashMap<>();

    /**
     * 扫描全部 Mod jar(由加载管线经 {@code runInitTask("Data file scanning")} 调用)。
     *
     * @return 具有资源或数据内容的 Mod 资源包数量
     */
    public int scan(List<ModContainer> mods) {
        packs.clear();
        languageTable.clear();
        int languageKeys = 0;
        for (ModContainer mod : mods) {
            try (ZipFile zip = new ZipFile(mod.jarPath().toFile())) {
                boolean hasAssets = false;
                boolean hasData = false;
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("assets/") && !entry.isDirectory()) {
                        hasAssets = true;
                    } else if (name.startsWith("data/") && !entry.isDirectory()) {
                        hasData = true;
                    }
                }
                if (hasAssets || hasData) {
                    packs.add(new ModResourcePack(mod.id(), mod.jarPath(), hasAssets, hasData));
                }
                if (hasAssets) {
                    languageKeys += mergeLanguage(zip, mod);
                }
            } catch (Exception e) {
                LOGGER.warn("Resource scan failed for {}: {}", mod.id(), e.toString());
            }
        }
        LOGGER.info("Resource scan finished: {} mod pack source(s), {} en_us key(s) merged",
                packs.size(), languageKeys);
        return packs.size();
    }

    /** 合并 jar 内全部 {@code assets/<ns>/lang/en_us.json} 的键值 */
    private int mergeLanguage(ZipFile zip, ModContainer mod) {
        int merged = 0;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith("assets/") || !name.endsWith("/lang/en_us.json")) {
                continue;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                for (String key : root.keySet()) {
                    languageTable.put(key, root.get(key).getAsString());
                    merged++;
                }
            } catch (Exception e) {
                LOGGER.warn("Invalid language file {} in {}: {}", name, mod.id(), e.toString());
            }
        }
        return merged;
    }

    /** 当前登记的全部 Mod 资源包(只读) */
    public List<ModResourcePack> packs() {
        return List.copyOf(packs);
    }

    /** 合并后的内置语言表(只读) */
    public Map<String, String> languageTable() {
        return Collections.unmodifiableMap(languageTable);
    }
}
