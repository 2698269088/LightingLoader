package top.mcocet.lighting.event;

import top.mcocet.lighting.loader.ModContainer;

import java.util.List;

/**
 * 跨 Mod 通信处理事件——对应 FML 的 {@code InterModProcessEvent}:
 * 在全部 Mod 完成入队后统一处理已登记的消息;并行派发阶段。
 */
public record InterModProcessEvent(List<ModContainer> mods) {
}
