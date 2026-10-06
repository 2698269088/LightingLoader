package top.mcocet.lighting.event;

/**
 * 游戏主类 main 方法被进入时发布。
 * <p>
 * 该事件由被 ASM 注入到 {@code net.minecraft.client.main.Main#main} 开头的
 * 钩子调用触发,是"加载器已完成 Mod 装载、正式移交游戏"的时间锚点。
 */
public record GameMainEnterEvent(long timestampMillis) {
}
