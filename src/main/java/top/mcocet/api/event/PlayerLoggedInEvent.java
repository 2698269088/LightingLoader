package top.mcocet.api.event;

/**
 * 玩家登录完成事件(注入点:{@code PlayerList.placeNewPlayer} 返回前)。
 *
 * @param playerName 玩家计分板名(账号名)
 * @param dimension  登录时所在维度标识
 */
public record PlayerLoggedInEvent(String playerName, String dimension) {
}
