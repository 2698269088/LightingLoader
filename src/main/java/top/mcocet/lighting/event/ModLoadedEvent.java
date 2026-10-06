package top.mcocet.lighting.event;

import top.mcocet.lighting.loader.ModContainer;

/**
 * 单个 Mod 加载完成(入口点已调度)。
 */
public record ModLoadedEvent(ModContainer container) {
}
