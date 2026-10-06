package top.mcocet.lighting.event;

import top.mcocet.lighting.loader.ModContainer;

import java.util.List;

/**
 * 注册事件——对应 FML 的 {@code RegisterEvent}:
 * 在注册表冻结前发布,Mod 通过 {@code LightingLoader.registry()} 在此登记条目;
 * 事件结束后注册表进入冻结状态,越界注册将抛出 {@link IllegalStateException}。
 */
public record RegisterEvent(List<ModContainer> mods) {
}
