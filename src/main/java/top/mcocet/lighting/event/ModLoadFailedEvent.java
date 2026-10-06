package top.mcocet.lighting.event;

import top.mcocet.lighting.discovery.ModCandidate;

/**
 * 单个 Mod 加载失败;失败不中止整体流程,由加载器汇总报告。
 */
public record ModLoadFailedEvent(ModCandidate candidate, String error) {
}
