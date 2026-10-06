package top.demo.capabilities;

import top.mcocet.api.LightingDeferredRegister;
import top.mcocet.api.LightingEntrypoint;
import top.mcocet.api.LightingLoader;
import top.mcocet.api.datagen.DataProvider;
import top.mcocet.api.datagen.DataWriter;
import top.mcocet.api.registry.DeferredBlock;
import top.mcocet.api.registry.DeferredEntityType;
import top.mcocet.api.registry.DeferredItem;
import top.mcocet.api.registry.RegistryBuilder;
import top.mcocet.lighting.event.DataPackRegistryEvent;
import top.mcocet.lighting.event.GatherDataEvent;
import top.mcocet.lighting.event.LoadCompleteEvent;
import top.mcocet.lighting.event.NewRegistryEvent;

import java.io.IOException;

/**
 * 能力补齐演示 Mod(随 demo-bridge-fabric.jar 打包):
 * 验证本轮补齐的 API 面——typed 延迟注册持有者家族、命名注册表创建
 * (NewRegistryEvent + RegistryBuilder)、世界数据注册表构建器
 * (DataPackRegistryEvent.dataPackRegistry)、数据生成 provider
 * (GatherDataEvent + DataProvider)。
 * 运行 dry-run 或 --datagen 时在日志中可见对应输出与落盘结果。
 */
public final class CapabilitiesDemoMod implements LightingEntrypoint {

    @Override
    public void onInitialize() {
        System.out.println("[CapabilitiesDemo] onInitialize via Lighting bridge");

        // 1) typed 延迟注册持有者家族:方块/物品/实体类型注册器
        LightingDeferredRegister blocks = LightingDeferredRegister.createBlocks("demo_bridge_fabric");
        DeferredBlock<String> ruby = blocks.registerBlock("capability_ruby", () -> "ruby-block");
        LightingDeferredRegister items = LightingDeferredRegister.createItems("demo_bridge_fabric");
        DeferredItem<String> rubyItem = items.registerItem("capability_ruby", () -> "ruby-item");
        LightingDeferredRegister entities = LightingDeferredRegister.createEntityTypes("demo_bridge_fabric");
        DeferredEntityType<String> golem = entities.registerEntityType("capability_golem", () -> "golem-type");
        System.out.println("[CapabilitiesDemo] deferred typed holders: block=" + ruby.id()
                + ", item=" + rubyItem.id() + ", entityType=" + golem.id());

        // 2) 命名注册表创建:NewRegistryEvent.create(name) 返回 RegistryBuilder
        LightingLoader.events().subscribe(NewRegistryEvent.class, event -> {
            RegistryBuilder<Object> spell = event.create("demo_bridge_fabric:spell");
            spell.create();
            System.out.println("[CapabilitiesDemo] NewRegistry created=" + spell.name()
                    + ", kind=" + spell.kind() + ", completed=" + spell.isCompleted());
        });

        // 3) 世界数据注册表声明:dataPackRegistry(name) 构建器(可补充同步描述)
        LightingLoader.events().subscribe(DataPackRegistryEvent.class, event -> {
            RegistryBuilder<Object> worldData = event.dataPackRegistry("demo_bridge_fabric:spell_data");
            worldData.sync(false);
            worldData.create();
            System.out.println("[CapabilitiesDemo] dataPackRegistry=" + worldData.name()
                    + ", synced=" + worldData.synced() + ", declared=" + event.declaredCount());
        });

        // 4) 数据生成 provider(--datagen 模式下由加载器统一运行并落盘)
        LightingLoader.events().subscribe(GatherDataEvent.class, event -> {
            event.generator().addProvider("demo_bridge_fabric", new LanguageProvider());
            System.out.println("[CapabilitiesDemo] data provider registered for gather event");
        });

        // 5) 加载完成时校验持有者解析状态(RegisterEvent 阶段已统一写入注册中心)
        LightingLoader.events().subscribe(LoadCompleteEvent.class, event ->
                System.out.println("[CapabilitiesDemo] load complete; deferred block=" + ruby.get()
                        + " (bound=" + ruby.isBound() + "), item=" + rubyItem.orElse("?")
                        + ", entityType=" + golem.get()));
    }

    /** 演示数据生成 provider:写出一个语言文件 JSON */
    private static final class LanguageProvider implements DataProvider {

        @Override
        public String name() {
            return "languages";
        }

        @Override
        public void run(DataWriter writer) throws IOException {
            writer.writeText("assets/demo_bridge_fabric/lang/en_us.json",
                    "{\n  \"demo_bridge_fabric.ruby\": \"Ruby\"\n}\n");
        }
    }
}
