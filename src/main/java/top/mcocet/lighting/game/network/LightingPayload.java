package top.mcocet.lighting.game.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Lighting 通用网络载荷:通道名 + 不透明字节。
 * <p>
 * 与 FML 的 raw channel 心智一致——全部 Mod 通道共享同一个网络类型
 * ({@code lighting:channel}),消息路由由通道名字段完成;需要结构化
 * 编解码的 Mod 可在字节层之上自行叠加序列化。
 * <p>
 * 本类型经 {@code PayloadCodecTransformer} 追加到原版自定义载荷
 * 解码列表(两个方向、PLAY 与 CONFIGURATION 阶段的全部 codec),
 * 从而完成真实的线协议注册。
 */
public record LightingPayload(String channel, byte[] data) implements CustomPacketPayload {

    /** 载荷类型标识(单类型 + 通道路由) */
    public static final CustomPacketPayload.Type<LightingPayload> TYPE =
            CustomPacketPayload.createType("lighting:channel");

    /** 线格式:UTF 通道名 + 长度前缀字节组 */
    public static final StreamCodec<FriendlyByteBuf, LightingPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.channel());
                buffer.writeByteArray(payload.data());
            },
            buffer -> new LightingPayload(buffer.readUtf(), buffer.readByteArray()));

    @Override
    public CustomPacketPayload.Type<LightingPayload> type() {
        return TYPE;
    }
}
