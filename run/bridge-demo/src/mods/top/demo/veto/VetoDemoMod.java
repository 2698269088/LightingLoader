package top.demo.veto;

import top.mcocet.api.LightingEntrypoint;
import top.mcocet.api.LightingLoader;
import top.mcocet.lighting.event.CommonSetupEvent;

/**
 * 可选演示 Mod:故意取消 CommonSetupEvent,验证"事件取消 → 中止移交"的
 * 接管语义。仅在 build-demo.ps1 使用 -IncludeVeto 时打包进 run/mods,
 * 用于验证取消路径后请删除 demo-veto-fabric.jar。
 */
public final class VetoDemoMod implements LightingEntrypoint {

    @Override
    public void onInitialize() {
        LightingLoader.events().subscribe(CommonSetupEvent.class, event -> {
            System.out.println("[VetoDemoMod] cancelling common setup on purpose");
            event.cancel();
        });
    }
}
