package top.mcocet.api.event;

/** 服务端主循环 tick 事件(注入点:{@code MinecraftServer.tickServer}) */
public record ServerTickEvent(TickEvent.Phase phase) implements TickEvent {
}
