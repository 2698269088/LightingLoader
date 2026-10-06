package top.mcocet.api.event;

/** 客户端主循环 tick 事件(注入点:{@code Minecraft.tick},仅客户端) */
public record ClientTickEvent(TickEvent.Phase phase) implements TickEvent {
}
