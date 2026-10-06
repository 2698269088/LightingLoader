package top.mcocet.api.event;

/**
 * 玩家 tick 事件(注入点:{@code ServerPlayer.tick})。
 *
 * @param playerName 玩家计分板名(账号名)
 */
public record PlayerTickEvent(TickEvent.Phase phase, String playerName) implements TickEvent {
}
