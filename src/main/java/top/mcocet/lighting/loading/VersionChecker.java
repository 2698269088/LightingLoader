package top.mcocet.lighting.loading;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.lighting.loader.ModContainer;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Mod 更新检查——对应 FML 的 {@code VersionChecker}。
 * <p>
 * 对声明了 {@code updateJSONURL}(Forge 系)的 Mod,在后台守护线程中拉取
 * 更新描述 JSON(取 {@code version} 字段)并与本地版本比较;
 * 相对路径按 gameDir 解析,支持 {@code file:} 与 {@code http(s):} 两种来源。
 * 检查全程不阻塞启动,任何异常都只降级为调试日志。
 */
public final class VersionChecker {

    private static final Logger LOGGER = LogManager.getLogger(VersionChecker.class);
    private static final int TIMEOUT_MILLIS = 5000;

    private VersionChecker() {
    }

    /** 为全部声明了更新地址的 Mod 启动异步检查(守护线程,不阻塞启动) */
    public static void checkAsync(Path gameDir, List<ModContainer> mods) {
        int scheduled = 0;
        for (ModContainer mod : mods) {
            String target = mod.metadata().extras().get("updateJSONURL");
            if (target == null || target.isBlank()) {
                continue;
            }
            Thread thread = new Thread(() -> checkOne(gameDir, mod, target),
                    "LightingVersionCheck-" + mod.id());
            thread.setDaemon(true);
            thread.start();
            scheduled++;
        }
        if (scheduled > 0) {
            LOGGER.info("Update checks scheduled: {} mod(s)", scheduled);
        }
    }

    private static void checkOne(Path gameDir, ModContainer mod, String target) {
        try {
            URL url = resolve(gameDir, target);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);

            String json;
            try (InputStream in = connection.getInputStream()) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("version")) {
                LOGGER.debug("Update check for {}: response has no 'version' field ({})", mod.id(), url);
                return;
            }
            String latest = root.get("version").getAsString();
            String current = mod.metadata().version();
            if (VersionRange.compareVersions(latest, current) > 0) {
                LOGGER.info("Update available for {}: {} -> {} ({})", mod.id(), current, latest, url);
            } else {
                LOGGER.info("Up to date: {} ({}, latest {})", mod.id(), current, latest);
            }
        } catch (Exception e) {
            LOGGER.debug("Update check for {} failed: {}", mod.id(), e.toString());
        }
    }

    private static URL resolve(Path gameDir, String target) throws Exception {
        if (target.startsWith("http://") || target.startsWith("https://") || target.startsWith("file:")) {
            return URI.create(target).toURL();
        }
        // 相对路径:按 gameDir 解析(便于整合包内自带的更新描述)
        return gameDir.resolve(target).toUri().toURL();
    }
}
