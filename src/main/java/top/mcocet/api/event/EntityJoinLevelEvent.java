package top.mcocet.api.event;

/**
 * 实体进入世界事件(注入点:{@code Level.addFreshEntity} 系列,
 * 锚点接入为增量任务)。
 *
 * @param entityDescription 实体描述(类型 + 名称)
 * @param dimension         目标维度标识
 */
public record EntityJoinLevelEvent(String entityDescription, String dimension) {
}
