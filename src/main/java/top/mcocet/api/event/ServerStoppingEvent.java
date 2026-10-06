package top.mcocet.api.event;

/**
 * 服务端停止事件(通知型)。
 * <p>
 * 事件类型已就绪;派发锚点({@code MinecraftServer.stopServer} 路径)
 * 将在后续增量中接入。
 *
 * @param timestampMillis 事件时间戳
 */
public record ServerStoppingEvent(long timestampMillis) {
}
