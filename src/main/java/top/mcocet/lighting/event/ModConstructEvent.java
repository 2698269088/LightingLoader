package top.mcocet.lighting.event;

import top.mcocet.lighting.loader.ModContainer;

import java.util.List;

/**
 * Mod 构造完成事件——对应 FML 的 {@code FMLConstructModEvent}:
 * 全部 Mod 入口已构造/调用完成,注册表冻结序列即将开始。
 *
 * @param mods          已构造完成的 Mod 列表(只读)
 * @param elapsedMillis 从加载流程开始到构造完成的耗时
 */
public record ModConstructEvent(List<ModContainer> mods, long elapsedMillis) {
}
