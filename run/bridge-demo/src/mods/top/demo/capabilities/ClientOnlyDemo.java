package top.demo.capabilities;

import top.mcocet.api.Dist;
import top.mcocet.api.DistOnly;

/**
 * 类级 @DistOnly 演示:整个类仅在客户端有效。
 * 专用服务端(dedicated server)运行时经 DistCleaner 转换为空壳
 * (删除全部方法与字段,仅留标记字段);
 * dry-run 的 DistCleaner demo 段会扫描到本类并展示剥离结果。
 */
@DistOnly(Dist.CLIENT)
public final class ClientOnlyDemo {

    /** 仅客户端可用的入口(服务端上整个类已被剥离) */
    public static String rendererInfo() {
        return "client renderer ready";
    }
}
