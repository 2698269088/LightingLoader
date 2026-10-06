package top.mcocet.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 单向环境标记——对应 NeoForge 的 {@code @OnlyIn(Dist)}。
 * <p>
 * 标注的类或方法只在指定的运行侧有效:加载器在类加载期检测本注解,
 * 若当前运行侧与 {@link #value()} 不符,则剥离相应成员
 * (类级:删除全部方法与字段,替换为标记空壳;方法级:方法体重写为
 * 抛出 {@link UnsupportedOperationException}),防止仅客户端代码在
 * 专用服务端(或反向)上被执行。字段级剥离不受支持(与 NeoForge 语义一致)。
 * <p>
 * 本注解仅用于“确实无法跨侧共享”的入口(如直接引用客户端渲染类型的
 * 静态工厂);跨侧共享逻辑应经运行侧判断分支代替,而非依赖剥离。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DistOnly {

    /** 标注成员允许运行的运行侧 */
    Dist value();
}
