package top.mcocet.api.event;

/**
 * 玩家登出事件(注入点:{@code PlayerList.remove} 返回前)。
 *
 * @param playerName 玩家计分板名(账号名)
 */
public record PlayerLoggedOutEvent(String playerName) {
}
