package top.mcocet.lighting.game.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * 客户端网络桥:把自定义载荷发往当前服务端连接。
 * <p>
 * 客户端类经游戏侧类加载器定义,仅在客户端分布加载;
 * 服务端进程不会触达本类。
 */
public final class ClientNetworkBridge {

    private ClientNetworkBridge() {
    }

    /** 客户端 → 服务端发送自定义载荷;尚未连接服务器时返回 false */
    public static boolean sendToServer(String channel, byte[] data) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        connection.send(new ServerboundCustomPayloadPacket(new LightingPayload(channel, data)));
        return true;
    }
}
