package top.mcocet.api.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GameTest 类标记——对应 NeoForge 的 {@code @GameTestHolder}:
 * 标注的类中所有 {@link GameTest} 方法会被加载器在发现阶段扫描登记,
 * 供测试模式下游戏内测试框架执行。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GameTestHolder {

    /** 所属 Mod ID */
    String value();
}
