package top.mcocet.api.event;

/**
 * 生物死亡事件(通知型)。
 * <p>
 * 事件类型已就绪;派发锚点({@code LivingEntity.die} 路径)
 * 将在后续增量中接入。
 */
public record LivingDeathEvent(String entityDescription, String damageSource) {
}
