package top.mcocet.api.event;

/**
 * 实体离开世界事件(注入点:{@code ServerLevel.onEntityRemoved},
 * 锚点接入为增量任务)。
 *
 * @param entityDescription 实体描述(类型 + 名称)
 * @param dimension         原维度标识
 */
public record EntityLeaveLevelEvent(String entityDescription, String dimension) {
}
