package top.mcocet.lighting.event;

import top.mcocet.lighting.discovery.ModCandidate;

import java.util.List;

/**
 * 发现阶段完成:mods 目录扫描与清单解析结束。
 */
public record DiscoveryFinishedEvent(List<ModCandidate> candidates) {
}
