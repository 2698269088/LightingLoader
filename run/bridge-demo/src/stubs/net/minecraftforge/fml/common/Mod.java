package net.minecraftforge.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 编译演示 Mod 用的 @Mod 注解桩:真实注解由用户组装 Forge API 时提供。
 * 本桩只用于生成与 Lighting Forge 桥接扫描匹配的注解描述符;
 * 编译后不打入 Mod jar,保持"生态 API 未组装"的探测结果真实。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {

    String value();
}
