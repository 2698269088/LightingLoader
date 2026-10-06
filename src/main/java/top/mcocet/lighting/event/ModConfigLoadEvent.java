package top.mcocet.lighting.event;

import java.nio.file.Path;

/**
 * Mod 配置加载完成事件——对应 FML 的 {@code ModConfigEvent.Loading}。
 * <p>
 * 配置加载阶段(构造之后、收尾阶段之前)对每个 Mod 的通用配置文件
 * ({@code config/<modid>-common.toml},缺失时自动生成默认文件)解析后发布;
 * Mod 可在监听器中读取自己的配置项,或在此触发配置相关的初始化。
 *
 * @param modId   配置所属的 Mod ID
 * @param file    配置文件路径
 * @param entries 解析出的配置项数量
 */
public record ModConfigLoadEvent(String modId, Path file, int entries) {
}
