package top.mcocet.lighting.game.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import top.mcocet.api.network.PayloadData;
import top.mcocet.lighting.network.NetworkPayloadRegistry;
import top.mcocet.lighting.transform.LightingHooks;

import java.util.List;

/**
 * 服务端网络桥:自定义载荷的原版解码列表扩展与收发派发。
 * <p>
 * 由 {@code PayloadCodecTransformer} 注入三处字节码调用本类:
 * <ul>
 *   <li>{@code Serverbound/ClientboundCustomPayloadPacket} 的 {@code <clinit>}
 *       中、每个原版 {@code CustomPacketPayload.codec(...)} 调用之前——
 *       {@link #appendModdedPayloadTypes} 把 {@code lighting:channel} 类型
 *       追加进解码列表,使 modded 载荷能被真实解出(而非丢弃为
 *       {@code DiscardedPayload});</li>
 *   <li>{@code ServerCommonPacketListenerImpl.handleCustomPayload} 入口——
 *       {@link #onServerboundPayload} 把到达的载荷交内核注册中心派发。</li>
 * </ul>
 * 客户端方向由 {@link #onClientboundPayload} 经
 * {@code ClientCommonPacketListenerImpl.handleCustomPayload} 注入点处理;
 * 发送侧入口见 {@link #sendToPlayer} 与 {@link ClientNetworkBridge}。
 */
public final class GameNetworkBridge {

    private static final Logger LOGGER = LogManager.getLogger(GameNetworkBridge.class);

    private GameNetworkBridge() {
    }

    /**
     * 把内核注册中心中登记的通道载荷类型追加到原版解码列表。
     * 由载荷包类的 {@code <clinit>} 注入调用(幂等:已追加则直接返回);
     * 无任何登记载荷或注册中心未绑定时保持原版列表不变。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void appendModdedPayloadTypes(List list) {
        NetworkPayloadRegistry registry = LightingHooks.networkRegistry();
        if (registry == null || list == null || registry.payloadCount() == 0) {
            return;
        }
        for (Object element : list) {
            if (element instanceof CustomPacketPayload.TypeAndCodec<?, ?> codec
                    && LightingPayload.TYPE.equals(codec.type())) {
                return;
            }
        }
        list.add(new CustomPacketPayload.TypeAndCodec<>(LightingPayload.TYPE, LightingPayload.STREAM_CODEC));
        LOGGER.info("Modded payload type registered on the vanilla codec list ({} channel(s) available)",
                registry.payloadCount());
    }

    /**
     * 服务端收到自定义载荷(锚点:{@code ServerCommonPacketListenerImpl.handleCustomPayload})。
     * 仅处理 {@link LightingPayload};玩家来源经 {@code ServerGamePacketListenerImpl.player}
     * 归属,未注册 handler 的通道记录警告并丢弃。
     */
    public static void onServerboundPayload(ServerCommonPacketListenerImpl listener,
                                            ServerboundCustomPayloadPacket packet) {
        if (!(packet.payload() instanceof LightingPayload payload)) {
            return;
        }
        NetworkPayloadRegistry registry = LightingHooks.networkRegistry();
        if (registry == null) {
            return;
        }
        String origin = "client";
        if (listener instanceof ServerGamePacketListenerImpl game && game.player != null) {
            origin = game.player.getScoreboardName();
        }
        if (!registry.dispatch(new PayloadData(payload.channel(), payload.data(), origin))) {
            LOGGER.warn("Dropped payload on channel {}: no handler registered", payload.channel());
        }
    }

    /**
     * 客户端收到自定义载荷(锚点:{@code ClientCommonPacketListenerImpl.handleCustomPayload},
     * 覆盖 PLAY 与 CONFIGURATION 两个阶段)。仅处理 {@link LightingPayload}。
     */
    public static void onClientboundPayload(ClientboundCustomPayloadPacket packet) {
        if (!(packet.payload() instanceof LightingPayload payload)) {
            return;
        }
        NetworkPayloadRegistry registry = LightingHooks.networkRegistry();
        if (registry == null) {
            return;
        }
        if (!registry.dispatch(new PayloadData(payload.channel(), payload.data(), "server"))) {
            LOGGER.warn("Dropped payload on channel {}: no handler registered", payload.channel());
        }
    }

    /** 服务端 → 单玩家发送自定义载荷;连接尚未建立时返回 false */
    public static boolean sendToPlayer(ServerPlayer player, String channel, byte[] data) {
        if (player.connection == null) {
            return false;
        }
        player.connection.send(new ClientboundCustomPayloadPacket(new LightingPayload(channel, data)));
        return true;
    }
}
