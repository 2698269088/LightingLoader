package top.mcocet.api.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GameTest 方法标记——对应 NeoForge 的 {@code @GameTest}:
 * 位于 {@link GameTestHolder} 类中的本标注方法会被登记为游戏内测试。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {

    /** 测试结构模板(相对命名空间,如 {@code gametest.empty};空表示使用默认空结构) */
    String template() default "";

    /** 超时 tick 数 */
    int timeoutTicks() default 100;

    /** 批次名(同批次测试按序执行;空表示默认批次) */
    String batch() default "";
}
