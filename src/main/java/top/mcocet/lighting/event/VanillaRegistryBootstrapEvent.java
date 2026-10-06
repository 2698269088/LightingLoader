package top.mcocet.lighting.event;

/**
 * 原版注册表冻结前的接管窗口开启时发布。
 * <p>
 * 该事件由注入到 {@code BuiltInRegistries.bootStrap()} 的钩子在
 * {@code createContents()} 之后、{@code freeze()} 之前触发——
 * 这是自定义方块/实体条目写入原版注册表的最后窗口。
 */
public record VanillaRegistryBootstrapEvent(long timestampMillis) {
}
