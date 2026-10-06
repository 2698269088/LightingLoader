package top.mcocet.lighting.event;

import top.mcocet.lighting.loader.ModContainer;

import java.util.List;

/**
 * 跨 Mod 通信入队事件——对应 FML 的 {@code InterModEnqueueEvent}:
 * 各 Mod 在此把要发送给其它 Mod 的消息登记到消息列表;并行派发阶段。
 */
public record InterModEnqueueEvent(List<ModContainer> mods) {
}
