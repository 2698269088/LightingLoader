package top.mcocet.lighting.event;

import top.mcocet.lighting.handoff.RegistryTakeoverReport;

/**
 * 原版注册表冻结并通过校验后发布(游戏真正"注册表就绪"的时间锚点)。
 *
 * @param report         冻结前接管统计(方块/实体/失败数);未发生接管时为空报告
 * @param takeoverMillis 接管注入耗时(毫秒);未发生接管时为 0
 */
public record VanillaRegistryReadyEvent(RegistryTakeoverReport report, long takeoverMillis) {
}
