package top.demo.fabric;

import top.mcocet.api.BlockDescriptor;
import top.mcocet.api.EntityDescriptor;
import top.mcocet.api.EventPriority;
import top.mcocet.api.LightingContent;
import top.mcocet.api.LightingDeferredRegister;
import top.mcocet.api.LightingEntrypoint;
import top.mcocet.api.LightingLoader;
import top.mcocet.lighting.capability.CapabilityManager;
import top.mcocet.lighting.event.ClientSetupEvent;
import top.mcocet.lighting.event.CommonSetupEvent;
import top.mcocet.lighting.event.DataPackRegistryEvent;
import top.mcocet.lighting.event.DedicatedServerSetupEvent;
import top.mcocet.lighting.event.LoadCompleteEvent;
import top.mcocet.lighting.event.ModConfigLoadEvent;
import top.mcocet.lighting.event.ModifyRegistriesEvent;
import top.mcocet.lighting.event.RegisterCapabilitiesEvent;
import top.mcocet.lighting.event.RegisterEvent;

/**
 * 演示用 Fabric 生态 Mod:实现 Lighting 统一入口点接口,
 * 由 Lighting 的 Fabric 桥接按纯类名入口实例化并调用 onInitialize(),
 * 同时演示加载器的全部新能力(配置/网络锁/延迟注册/注册表修改/数据包声明/
 * 能力系统/快照)。
 */
public final class FabricDemoMod implements LightingEntrypoint {

    /** 能力注册中心句柄:Registration events 阶段捕获,供加载完成时查询 */
    private CapabilityManager capabilities;

    @Override
    public void onInitialize() {
        System.out.println("[DemoFabricMod] onInitialize invoked via Lighting bridge");
        System.out.println("[DemoFabricMod] Lighting available=" + LightingLoader.isAvailable()
                + ", loader=" + LightingLoader.loaderName() + " " + LightingLoader.loaderVersion()
                + ", game=" + LightingLoader.gameVersion() + ", dist=" + LightingLoader.dist());
        System.out.println("[DemoFabricMod] visible mods=" + LightingLoader.mods().size()
                + ", registry frozen=" + LightingLoader.registry().isFrozen());

        // 延迟注册器(DeferredRegister 语义):构造期登记,RegisterEvent 阶段由内核统一写入
        LightingDeferredRegister blocks = LightingDeferredRegister.create("demo:blocks", "demo_bridge_fabric");
        blocks.register("ruby_deferred", () -> "ruby-deferred");

        // Forge 风格事件总线:HIGHEST 最先派发,LOWEST 最后;取消则跳过剩余监听器
        LightingLoader.events().subscribe(CommonSetupEvent.class, EventPriority.HIGHEST, event -> {
            System.out.println("[DemoFabricMod] common setup (HIGHEST), mods=" + event.mods().size());
            // 网络载荷登记(加载完成前有效,收尾阶段上锁)
            LightingLoader.network().registerPayload("demo_bridge_fabric:ping", true, true);
            System.out.println("[DemoFabricMod] payload registered, count="
                    + LightingLoader.network().payloadCount());
        });
        LightingLoader.events().subscribe(CommonSetupEvent.class, EventPriority.LOWEST, event ->
                System.out.println("[DemoFabricMod] common setup (LOWEST)"));
        LightingLoader.events().subscribe(ClientSetupEvent.class, event ->
                System.out.println("[DemoFabricMod] client setup (NORMAL)"));
        LightingLoader.events().subscribe(DedicatedServerSetupEvent.class, event ->
                System.out.println("[DemoFabricMod] dedicated server setup (SERVER dist run)"));

        // 配置读取:配置文件由加载器在构造后生成/解析
        LightingLoader.events().subscribe(ModConfigLoadEvent.class, event ->
                System.out.println("[DemoFabricMod] config loaded: " + event.file().getFileName()
                        + ", entries=" + event.entries()
                        + ", enabled=" + LightingLoader.config().get(event.modId(), "enabled").orElse("?")));

        // 注册表语义(NeoForge 序列):RegisterEvent 在冻结前发布,可安全登记条目
        LightingLoader.events().subscribe(RegisterEvent.class, event -> {
            LightingLoader.registry().register("demo:blocks", "demo_bridge_fabric:ruby", "ruby");
            LightingLoader.registry().register("demo:blocks", "demo_bridge_fabric:junk", "junk");
            // 游戏内容描述符:由游戏侧接管层在 BuiltInRegistries 冻结前实例化为真实方块/实体
            LightingContent.registerBlock(BlockDescriptor.builder("demo_bridge_fabric:ruby_block")
                    .hardness(3.0F).resistance(9.0F).lightLevel(4).requiresTool(true).build());
            LightingContent.registerBlock(BlockDescriptor.builder("demo_bridge_fabric:glow_crystal")
                    .hardness(0.6F).lightLevel(12).sound("glass").mapColor("quartz").occludes(false).build());
            LightingContent.registerEntity(EntityDescriptor.builder("demo_bridge_fabric:ruby_golem")
                    .width(0.9F).height(1.6F).category("monster").fireImmune(true).build());
            System.out.println("[DemoFabricMod] registered during RegisterEvent, blocks="
                    + LightingLoader.registry().size("demo:blocks"));
        });
        // 世界数据注册表声明(随存档加载的数据注册表)
        LightingLoader.events().subscribe(DataPackRegistryEvent.class, event ->
                event.declare("demo_bridge_fabric:world_data"));
        // ModifyRegistries:冻结前移除占位条目
        LightingLoader.events().subscribe(ModifyRegistriesEvent.class, event -> {
            boolean removed = event.registry().remove("demo:blocks", "demo_bridge_fabric:junk");
            System.out.println("[DemoFabricMod] ModifyRegistries removed junk=" + removed);
        });
        // 能力注册(收尾 Registration events 阶段)
        LightingLoader.events().subscribe(RegisterCapabilitiesEvent.class, event -> {
            event.capabilities().registerCapability("demo:energy");
            event.capabilities().registerProvider("demo_bridge_fabric:ruby", "demo:energy", "energy:50");
            capabilities = event.capabilities();
        });
        LightingLoader.events().subscribe(LoadCompleteEvent.class, event ->
                System.out.println("[DemoFabricMod] loading complete; registry frozen="
                        + LightingLoader.registry().isFrozen()
                        + ", blocks=" + LightingLoader.registry().size("demo:blocks")
                        + ", deferred=" + LightingLoader.registry().get("demo:blocks", "demo_bridge_fabric:ruby_deferred")
                        + ", takeover=" + LightingLoader.registry().size(LightingContent.BLOCK_REGISTRY)
                        + " block(s)/" + LightingLoader.registry().size(LightingContent.ENTITY_TYPE_REGISTRY)
                        + " entity type(s)"
                        + ", snapshot=" + LightingLoader.registry().snapshot().size() + " registries"
                        + ", capability=" + (capabilities == null ? "?"
                        : capabilities.query("demo_bridge_fabric:ruby", "demo:energy").orElse("missing"))
                        + ", networkPayloads=" + LightingLoader.network().payloadCount()));
    }
}
