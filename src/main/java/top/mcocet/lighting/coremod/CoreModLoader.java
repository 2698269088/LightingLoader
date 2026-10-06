package top.mcocet.lighting.coremod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.lighting.discovery.ModCandidate;
import top.mcocet.lighting.transform.ClassTransformer;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 类核心模组加载器——对应 FML 的 coremod 发现/实例化环节。
 * <p>
 * 在每个 Mod jar 内查找 {@code META-INF/lighting_coremods.json}
 * (JSON 字符串数组,声明实现 {@link LightingCoreMod} 的类名),
 * 在建立游戏类加载器之前用独立 URLClassLoader(父为加载器自身)实例化,
 * 收集其提供的转换器,加入统一转换管线。
 */
public final class CoreModLoader {

    private static final Logger LOGGER = LogManager.getLogger(CoreModLoader.class);

    private static final String MANIFEST_ENTRY = "META-INF/lighting_coremods.json";

    /** 加载结果:全部转换器 + 核心模组数量 */
    public record Result(List<ClassTransformer> transformers, int coreMods) {
    }

    private CoreModLoader() {
    }

    /** 扫描并实例化全部候选 Mod 声明的核心模组(在建类加载器之前调用) */
    public static Result load(List<ModCandidate> candidates, ClassLoader parent) {
        List<ClassTransformer> transformers = new ArrayList<>();
        int coreMods = 0;
        for (ModCandidate candidate : candidates) {
            List<String> classNames = declaredCoreMods(candidate);
            if (classNames.isEmpty()) {
                continue;
            }
            URLClassLoader loader = null;
            for (String className : classNames) {
                try {
                    if (loader == null) {
                        loader = new URLClassLoader(
                                new URL[]{candidate.jarPath().toUri().toURL()}, parent);
                    }
                    Class<?> type = Class.forName(className, true, loader);
                    if (!LightingCoreMod.class.isAssignableFrom(type)) {
                        LOGGER.warn("Coremod class {} in {} does not implement LightingCoreMod; skipped",
                                className, candidate.metadata().id());
                        continue;
                    }
                    LightingCoreMod coreMod = (LightingCoreMod) type.getDeclaredConstructor().newInstance();
                    List<ClassTransformer> provided = coreMod.transformers();
                    transformers.addAll(provided);
                    coreMods++;
                    LOGGER.info("Coremod loaded: {} ({}, {} transformer(s))",
                            coreMod.name(), candidate.metadata().id(), provided.size());
                } catch (Exception e) {
                    LOGGER.warn("Failed to load coremod {} of {}: {}",
                            className, candidate.metadata().id(), e.toString());
                }
            }
        }
        if (coreMods > 0) {
            LOGGER.info("Coremod loading finished: {} coremod(s), {} transformer(s)", coreMods, transformers.size());
        }
        return new Result(transformers, coreMods);
    }

    /** 读取 jar 内声明的核心模组类名 */
    private static List<String> declaredCoreMods(ModCandidate candidate) {
        try (ZipFile zip = new ZipFile(candidate.jarPath().toFile())) {
            ZipEntry entry = zip.getEntry(MANIFEST_ENTRY);
            if (entry == null) {
                return List.of();
            }
            String json;
            try (InputStream in = zip.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            List<String> classNames = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                classNames.add(element.getAsString());
            }
            return classNames;
        } catch (Exception e) {
            LOGGER.warn("Invalid coremod manifest in {}: {}", candidate.metadata().id(), e.toString());
            return List.of();
        }
    }
}
