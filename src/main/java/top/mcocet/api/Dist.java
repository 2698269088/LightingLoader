package top.mcocet.api;

/**
 * 运行侧标记——对应 {@code net.neoforged.api.distmarker.Dist} / FML 的
 * {@code FMLEnvironment.dist}:区分当前进程是客户端还是专用服务端。
 * <p>
 * 由启动参数 {@code --dist=server} 选择(默认 CLIENT),
 * 加载器据此派发客户端设置或服务端设置阶段事件。
 */
public enum Dist {
    /** 客户端进程(集成服务器随客户端启动) */
    CLIENT,
    /** 专用服务器进程 */
    SERVER
}
