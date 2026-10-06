package top.demo.capabilities;

import top.mcocet.api.Dist;
import top.mcocet.api.DistOnly;

/**
 * 方法级 @DistOnly 演示:本方法仅在专用服务端有效。
 * 客户端运行时经 DistCleaner 把方法体重写为抛出
 * UnsupportedOperationException 的存根。
 */
public final class ServerOnlyDemo {

    /** 仅服务端可用的入口(客户端上调用会立即抛出定位异常) */
    @DistOnly(Dist.SERVER)
    public static String tickStats() {
        return "server tick stats ready";
    }
}
