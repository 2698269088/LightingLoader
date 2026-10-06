package top.mcocet.api.network;

/**
 * 自定义载荷数据:通道标识 + 原始字节内容 + 来源描述。
 * <p>
 * Lighting 的网络通道以"通道名 + 不透明字节"为最小契约
 * (与 FML 的 raw channel 心智一致);需要结构化编解码的 Mod
 * 可自行在字节层之上叠加序列化。
 *
 * @param channel 通道标识(如 {@code examplemod:ping},须先经
 *                {@code LightingNetwork.registerPayload} 登记)
 * @param data    原始载荷字节(接收时按到达顺序原样传递)
 * @param origin  来源描述(如玩家名 / "server" / "client"),供处理侧区分上下文
 */
public record PayloadData(String channel, byte[] data, String origin) {
}
