package top.mcocet.api;

import top.mcocet.api.attachment.LightingAttachments;
import top.mcocet.api.capability.LightingCapabilities;

import java.util.List;

/**
 * Lighting 运行时服务接口:由加载器内核实现,并在启动阶段安装到
 * {@link LightingLoader} 门面。Mod 代码不直接实现或调用本接口。
 */
public interface LightingRuntime {

    String loaderName();

    String loaderVersion();

    String gameVersion();

    /** 当前运行侧:客户端或专用服务端(--dist=server,默认 CLIENT) */
    Dist dist();

    List<LightingMod> mods();

    LightingEventBus events();

    LightingRegistry registry();

    /** 统一配置视图(加载期已解析的配置项) */
    LightingConfig config();

    /** 统一网络视图(载荷登记与网络注册锁) */
    LightingNetwork network();

    /** 统一能力视图(typed 能力的注册与查询) */
    LightingCapabilities capabilities();

    /** 统一数据附件视图(附件类型登记与持有键读写) */
    LightingAttachments attachments();
}
