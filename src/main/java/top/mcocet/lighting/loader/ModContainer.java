package top.mcocet.lighting.loader;

import top.mcocet.lighting.discovery.ModCandidate;
import top.mcocet.lighting.model.ModMetadata;

import java.nio.file.Path;

/**
 * 已加载的 Mod 容器。
 * <p>
 * 骨架阶段仅承载元数据与状态;后续里程碑在此扩展各生态的运行时信息:
 * <ul>
 *   <li>M3:Mixin 配置句柄</li>
 *   <li>M4/M5:Fabric 侧 / Forge 侧的入口点处理器与专属类加载器视图</li>
 *   <li>M6:跨生态互操作的服务句柄</li>
 * </ul>
 */
public final class ModContainer {

    public enum State {
        DISCOVERED,
        LOADED,
        FAILED
    }

    private final ModMetadata metadata;
    private final Path jarPath;
    private State state = State.DISCOVERED;
    private long loadTimeMillis;

    public ModContainer(ModCandidate candidate) {
        this.metadata = candidate.metadata();
        this.jarPath = candidate.jarPath();
    }

    public ModMetadata metadata() {
        return metadata;
    }

    public String id() {
        return metadata.id();
    }

    public Path jarPath() {
        return jarPath;
    }

    public LoaderType loaderType() {
        return metadata.loaderType();
    }

    public State state() {
        return state;
    }

    public long loadTimeMillis() {
        return loadTimeMillis;
    }

    public void markLoaded(long loadTimeMillis) {
        this.state = State.LOADED;
        this.loadTimeMillis = loadTimeMillis;
    }

    public void markFailed() {
        this.state = State.FAILED;
    }

    @Override
    public String toString() {
        return metadata + " <" + jarPath.getFileName() + ">";
    }
}
