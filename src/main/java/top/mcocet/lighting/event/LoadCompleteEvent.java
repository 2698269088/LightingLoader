package top.mcocet.lighting.event;

import top.mcocet.lighting.loader.ModContainer;

import java.util.List;

/**
 * 全部 Mod 加载完成,注册表即将/已经冻结,准备移交游戏主流程。
 */
public record LoadCompleteEvent(List<ModContainer> mods, long elapsedMillis) {
}
