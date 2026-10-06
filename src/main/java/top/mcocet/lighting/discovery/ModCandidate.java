package top.mcocet.lighting.discovery;

import top.mcocet.lighting.loader.LoaderType;
import top.mcocet.lighting.model.ModMetadata;

import java.nio.file.Path;
import java.util.List;

/**
 * 发现阶段的候选 Mod:尚未加载,只完成了清单识别与解析。
 *
 * @param jarPath           Mod Jar 文件路径
 * @param metadata          解析后的统一元数据
 * @param availableLoaders  该 Jar 内实际携带的全部清单所属加载器(正常情况下只有一个)
 */
public record ModCandidate(Path jarPath, ModMetadata metadata, List<LoaderType> availableLoaders) {

    /** 在多清单 Jar 中未被选为主的其它加载器形态 */
    public List<LoaderType> alternativeLoaders() {
        return availableLoaders.stream()
                .filter(type -> type != metadata.loaderType())
                .toList();
    }
}
