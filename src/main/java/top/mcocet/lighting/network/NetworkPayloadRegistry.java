package top.mcocet.lighting.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.mcocet.api.network.PayloadData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 网络载荷注册中心——对应 FML 的 {@code NetworkRegistry}。
 * <p>
 * 语义与 FML 的网络注册锁一致:Mod 在加载期(收尾阶段的 LoadComplete 之前)
 * 登记自定义载荷与接收处理器;加载完成收尾时 {@link #lock() 上锁},
 * 此后任何注册都会抛出 {@link IllegalStateException},
 * 保证"网络协议在玩家进入游戏前已固定"。
 * <p>
 * 载荷契约:"通道名 + 原始字节"。游戏侧网络桥在两类原版自定义载荷包
 * ({@code Serverbound/ClientboundCustomPayloadPacket})的解码列表中追加
 * 单一 {@code lighting:channel} 类型,收到的载荷经 {@link #dispatch} 投递
 * 到通道处理器。
 */
public final class NetworkPayloadRegistry {

    private static final Logger LOGGER = LogManager.getLogger(NetworkPayloadRegistry.class);

    /** 载荷方向声明 */
    public enum Direction {
        /** 仅客户端收到的自定义载荷 */
        CLIENTBOUND,
        /** 仅服务端收到的自定义载荷 */
        SERVERBOUND,
        /** 双向自定义载荷 */
        BIDIRECTIONAL
    }

    /** 协议阶段:PLAY(游玩)或 CONFIGURATION(进入世界前的配置阶段) */
    public enum Protocol {
        PLAY,
        CONFIGURATION;

        public static Protocol parse(String name) {
            for (Protocol protocol : values()) {
                if (protocol.name().equalsIgnoreCase(name)) {
                    return protocol;
                }
            }
            throw new IllegalArgumentException(
                    "Unknown protocol '%s' (expected PLAY or CONFIGURATION)".formatted(name));
        }
    }

    /** 已登记的载荷:id -> (方向, 协议阶段) */
    public record PayloadRegistration(String id, Direction direction, Protocol protocol) {
    }

    private final Map<String, PayloadRegistration> payloads = new LinkedHashMap<>();
    private final Map<String, Consumer<PayloadData>> handlers = new LinkedHashMap<>();
    private boolean locked;

    /** 登记一个自定义载荷(默认 PLAY 协议;上锁前可调用) */
    public synchronized void registerPayload(String id, boolean clientbound, boolean serverbound) {
        registerPayload(id, clientbound, serverbound, "PLAY");
    }

    /** 登记一个自定义载荷并显式指定协议阶段(上锁前可调用) */
    public synchronized void registerPayload(String id, boolean clientbound, boolean serverbound, String protocolName) {
        if (locked) {
            throw new IllegalStateException(
                    "Network registry is locked, cannot register payload %s; register during mod loading".formatted(id));
        }
        Direction direction;
        if (clientbound && serverbound) {
            direction = Direction.BIDIRECTIONAL;
        } else if (clientbound) {
            direction = Direction.CLIENTBOUND;
        } else if (serverbound) {
            direction = Direction.SERVERBOUND;
        } else {
            throw new IllegalArgumentException("Payload %s must declare at least one direction".formatted(id));
        }
        Protocol protocol = Protocol.parse(protocolName);
        PayloadRegistration previous = payloads.putIfAbsent(id, new PayloadRegistration(id, direction, protocol));
        if (previous != null) {
            LOGGER.warn("Duplicate payload registration ignored: {} (already {}/{})",
                    id, previous.direction(), previous.protocol());
        }
    }

    /** 为已登记的通道注册接收处理器(上锁前可调用) */
    public synchronized void registerHandler(String channel, Consumer<PayloadData> handler) {
        if (locked) {
            throw new IllegalStateException(
                    "Network registry is locked, cannot register handler for %s; register during mod loading"
                            .formatted(channel));
        }
        if (!payloads.containsKey(channel)) {
            throw new IllegalArgumentException(
                    "Channel %s is not a registered payload; call registerPayload first".formatted(channel));
        }
        Consumer<PayloadData> previous = handlers.putIfAbsent(channel, handler);
        if (previous != null) {
            LOGGER.warn("Duplicate handler registration ignored: {}", channel);
        }
    }

    /**
     * 投递收到的载荷到对应通道处理器(由游戏侧网络桥调用)。
     *
     * @return true 表示存在处理器并已投递(或投递中异常已记录);false 表示无处理器
     */
    public synchronized boolean dispatch(PayloadData data) {
        Consumer<PayloadData> handler = handlers.get(data.channel());
        if (handler == null) {
            return false;
        }
        try {
            handler.accept(data);
        } catch (RuntimeException e) {
            LOGGER.error("Payload handler for {} failed: {}", data.channel(), e.toString());
        }
        return true;
    }

    /**
     * 上锁网络注册——对应 FML {@code load()} 末段的 "Network registry lock" 任务。
     *
     * @return 锁定时已登记的载荷数量
     */
    public synchronized int lock() {
        locked = true;
        return payloads.size();
    }

    public synchronized boolean isLocked() {
        return locked;
    }

    public synchronized int payloadCount() {
        return payloads.size();
    }

    public synchronized int handlerCount() {
        return handlers.size();
    }

    public synchronized Set<String> payloadIds() {
        return Set.copyOf(payloads.keySet());
    }

    /** 载荷快照(只读;供游戏侧网络桥登记与日志) */
    public synchronized Map<String, PayloadRegistration> specs() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(payloads));
    }
}
