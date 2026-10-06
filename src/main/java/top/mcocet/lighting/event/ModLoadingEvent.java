package top.mcocet.lighting.event;

import top.mcocet.lighting.discovery.ModCandidate;

/**
 * 即将加载某个 Mod 时发布;监听器可在此时做前置准备。
 *
 * @param candidate 待加载的候选 Mod
 * @param index     当前序号(从 1 开始)
 * @param total     总数
 */
public record ModLoadingEvent(ModCandidate candidate, int index, int total) {
}
