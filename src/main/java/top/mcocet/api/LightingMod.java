package top.mcocet.api;

/**
 * 加载器视角下一个 Mod 的只读视图。
 */
public interface LightingMod {

    String id();

    String name();

    String version();

    /** 来源生态:FABRIC / FORGE / NEOFORGE */
    String loaderType();

    /** Fabric 语义的运行环境:* / client / server */
    String environment();

    /** 对应 jar 文件的路径 */
    String jarPath();
}
