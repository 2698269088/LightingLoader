package top.mcocet.api;

import top.mcocet.api.network.PayloadData;

import java.util.function.Consumer;

/**
 * 统一网络视图:登记自定义载荷/处理器并查询网络注册锁状态。
 * <p>
 * 语义与 FML 的网络注册锁一致:只能在加载期(加载完成事件之前)
 * 登记载荷与处理器;收尾阶段上锁后,任何登记都会抛出
 * {@link IllegalStateException}。
 * <p>
 * 通道契约:载荷以"通道名 + 原始字节"收发。游戏侧网络桥把原版
 * 自定义载荷包(单一 {@code lighting:channel} 类型)按通道名分发;
 * 需要结构化编解码的 Mod 在字节层之上自行叠加序列化。
 */
public interface LightingNetwork {

    /**
     * 登记一个自定义载荷(加载期有效;协议阶段默认为 {@code PLAY})。
     *
     * @param id          载荷标识(如 {@code examplemod:ping})
     * @param clientbound 客户端可接收
     * @param serverbound 服务端可接收
     */
    void registerPayload(String id, boolean clientbound, boolean serverbound);

    /**
     * 登记一个自定义载荷并显式指定协议阶段。
     *
     * @param id          载荷标识
     * @param clientbound 客户端可接收
     * @param serverbound 服务端可接收
     * @param protocol    协议阶段:{@code PLAY} 或 {@code CONFIGURATION}
     */
    void registerPayload(String id, boolean clientbound, boolean serverbound, String protocol);

    /**
     * 为已登记的通道注册接收处理器(加载期有效)。
     * 服务端收到 serverbound 载荷、客户端收到 clientbound 载荷时,
     * 按通道名精确匹配投递。
     *
     * @throws IllegalArgumentException 通道未登记时抛出
     * @throws IllegalStateException    网络已上锁时抛出
     */
    void registerHandler(String channel, Consumer<PayloadData> handler);

    /** 网络注册是否已上锁 */
    boolean isLocked();

    /** 已登记的载荷数量 */
    int payloadCount();

    /** 已注册的通道处理器数量 */
    int handlerCount();
}
