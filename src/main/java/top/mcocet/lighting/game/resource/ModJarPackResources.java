package top.mcocet.lighting.game.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mod jar 资源包来源:把 Mod jar 的 {@code assets/} 与 {@code data/} 目录
 * 直接暴露为原版 {@link PackResources}(同包同时充当资源包与数据包)。
 * <p>
 * jar 以 {@link ZipFile} 懒打开——首次读取时才建立句柄,资源管理器
 * 关闭包时释放;{@code pack.mcmeta} 缺失无碍(元数据由
 * {@code GameResourceBridge} 直接合成,不经 getMetadataSection 解析)。
 * 由游戏侧类加载器定义,可自由引用 Minecraft 类型。
 */
public final class ModJarPackResources implements PackResources {

    private static final Logger LOGGER = LogManager.getLogger(ModJarPackResources.class);

    private final PackLocationInfo location;
    private final Path jarPath;

    private ZipFile zipFile;
    private boolean closed;

    public ModJarPackResources(PackLocationInfo location, Path jarPath) {
        this.location = location;
        this.jarPath = jarPath;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return resource(String.join("/", elements));
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        return resource(type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath());
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        String prefix = type.getDirectory() + "/" + namespace + "/" + (path.isEmpty() ? "" : path + "/");
        ZipFile zip = zip();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(prefix)) {
                continue;
            }
            Identifier id = Identifier.tryParse(namespace + ":" + name.substring(prefix.length()));
            if (id != null) {
                output.accept(id, IoSupplier.create(zip, entry));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> namespaces = new LinkedHashSet<>();
        String prefix = type.getDirectory() + "/";
        var entries = zip().entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (!name.startsWith(prefix)) {
                continue;
            }
            String rest = name.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                namespaces.add(rest.substring(0, slash));
            }
        }
        return namespaces;
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) {
        // 合成资源包没有 pack.mcmeta;元数据由 GameResourceBridge 构造 Pack 时直接提供
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    LOGGER.warn("Failed to close mod jar {}: {}", jarPath, e.toString());
                }
                zipFile = null;
            }
        }
    }

    /** 按 jar 内路径取资源;条目不存在返回 null(原版约定) */
    private IoSupplier<InputStream> resource(String path) {
        ZipFile zip = zip();
        ZipEntry entry = zip.getEntry(path);
        return entry == null ? null : IoSupplier.create(zip, entry);
    }

    private synchronized ZipFile zip() {
        if (closed) {
            throw new IllegalStateException("Pack resources already closed: " + jarPath);
        }
        if (zipFile == null) {
            try {
                zipFile = new ZipFile(jarPath.toFile());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open mod jar: " + jarPath, e);
            }
        }
        return zipFile;
    }
}
