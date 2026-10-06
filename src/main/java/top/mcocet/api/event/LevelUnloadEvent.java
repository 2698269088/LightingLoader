package top.mcocet.api.event;

/**
 * 维度卸载事件(通知型)。
 * <p>
 * 事件类型已就绪;派发锚点将在后续增量中接入。
 */
public record LevelUnloadEvent(String dimension) {
}
