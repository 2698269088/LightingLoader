package top.mcocet.lighting.installer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.mcocet.lighting.launch.GameEnvironment;
import top.mcocet.lighting.loader.LoaderConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 版本清单生成器——对应 NeoForge 安装器的"版本 JSON 写出"环节。
 * <p>
 * 真实发布形态中,启动器的版本清单需把 {@code mainClass} 指向
 * {@link top.mcocet.lighting.LightingBootstrap},并携带依赖库列表;
 * 本生成器把运行环境(GameEnvironment)与加载器版本合并为一份可直接使用的
 * {@code version-manifest.json},写入 gameDir,供启动器/整合包引用。
 */
public final class VersionManifestGenerator {

    /** 发布形态的入口由启动器清单指向本类 */
    public static final String MAIN_CLASS = "top.mcocet.lighting.LightingBootstrap";

    private VersionManifestGenerator() {
    }

    /**
     * 生成版本清单。
     *
     * @return 写出的清单文件路径
     */
    public static Path write(GameEnvironment environment, LoaderConfig config,
                             String loaderVersion, int modCount) throws IOException {
        String gameVersion = environment.gameVersion();
        if (gameVersion == null) {
            gameVersion = "unknown";
        }
        JsonObject root = new JsonObject();
        root.addProperty("id", gameVersion + "-lighting-" + loaderVersion);
        root.addProperty("inheritsFrom", gameVersion);
        root.addProperty("type", "release");
        root.addProperty("mainClass", MAIN_CLASS);

        JsonArray libraries = new JsonArray();
        for (Path library : environment.libraries()) {
            libraries.add(library.toAbsolutePath().toString());
        }
        root.add("libraries", libraries);

        JsonObject lighting = new JsonObject();
        lighting.addProperty("loaderVersion", loaderVersion);
        lighting.addProperty("modCount", modCount);
        lighting.addProperty("modsDirectory", config.modsDir().toString());
        root.add("lighting", lighting);

        Path out = config.gameDir().resolve("version-manifest.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n",
                StandardCharsets.UTF_8);
        return out;
    }
}
