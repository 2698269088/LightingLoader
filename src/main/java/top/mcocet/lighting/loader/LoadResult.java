package top.mcocet.lighting.loader;

import top.mcocet.lighting.loading.ModLoadingIssue;

import java.util.List;

/**
 * 加载结果:统一 Mod 容器列表 + 全部加载问题。
 *
 * @param mods   已构造完成的 Mod 容器(拓扑序)
 * @param issues 加载过程中收集的问题条目(致命问题已由 ModLoadingException 另行聚合)
 */
public record LoadResult(List<ModContainer> mods, List<ModLoadingIssue> issues) {
}
