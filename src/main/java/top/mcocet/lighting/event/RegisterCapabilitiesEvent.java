package top.mcocet.lighting.event;

import top.mcocet.lighting.attachment.AttachmentManager;
import top.mcocet.lighting.capability.CapabilityManager;
import top.mcocet.lighting.loader.ModContainer;

import java.util.List;

/**
 * 能力与附件注册事件——对应 NeoForge 的 {@code RegisterCapabilitiesEvent}。
 * <p>
 * 在收尾阶段的"Registration events"发布(对应 FML {@code RegistrationEvents.init}):
 * Mod 在此为方块实体/物品堆/实体注册 typed 能力提供器
 * (energy/item/fluid,见 {@code top.mcocet.api.capability}),
 * 并登记数据附件类型(见 {@code top.mcocet.api.attachment})。
 * 事件结束后能力/附件注册锁定,查询走统一的
 * {@code LightingCapabilities} 与 {@code LightingAttachments} 视图。
 */
public final class RegisterCapabilitiesEvent {

    private final List<ModContainer> mods;
    private final CapabilityManager capabilities;
    private final AttachmentManager attachments;

    public RegisterCapabilitiesEvent(List<ModContainer> mods, CapabilityManager capabilities,
                                     AttachmentManager attachments) {
        this.mods = mods;
        this.capabilities = capabilities;
        this.attachments = attachments;
    }

    /** 当前已加载的全部 Mod */
    public List<ModContainer> mods() {
        return mods;
    }

    /** 能力注册中心(注册 typed 能力提供器) */
    public CapabilityManager capabilities() {
        return capabilities;
    }

    /** 数据附件注册中心(登记附件类型;注册窗口结束即锁定) */
    public AttachmentManager attachments() {
        return attachments;
    }
}
