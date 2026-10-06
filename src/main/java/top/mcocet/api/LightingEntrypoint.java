package top.mcocet.api;

/**
 * 统一入口点接口:任意生态的 Mod 都可通过实现本接口,
 * 让加载器在入口调度阶段调用 {@link #onInitialize()}——
 * 不必绑定 Fabric 的 ModInitializer 或 Forge 的 @Mod 构造约定。
 */
public interface LightingEntrypoint {

    void onInitialize();
}
