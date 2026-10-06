package top.mcocet.lighting.bridge;

/**
 * 单个 Mod 的入口调度结果。
 *
 * @param modId     Mod ID
 * @param ecosystem 生态展示名
 * @param invoked   是否实际调用了至少一个入口
 * @param message   结果说明(日志文本)
 */
public record BridgeOutcome(String modId, String ecosystem, boolean invoked, String message) {
}
