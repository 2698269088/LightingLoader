package top.mcocet.api.event;

/**
 * 维度加载事件(通知型)。
 * <p>
 * 事件类型已就绪;派发锚点({@code ServerLevel} 构造/加载路径)
 * 将在后续增量中接入。
 */
public record LevelLoadEvent(String dimension) {
}
