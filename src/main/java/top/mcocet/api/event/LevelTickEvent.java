package top.mcocet.api.event;

/**
 * 维度 tick 事件(注入点:{@code ServerLevel.tick})。
 *
 * @param dimension 维度标识(如 {@code minecraft:overworld})
 */
public record LevelTickEvent(TickEvent.Phase phase, String dimension) implements TickEvent {
}
