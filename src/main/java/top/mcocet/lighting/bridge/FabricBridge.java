package top.mcocet.lighting.bridge;

import top.mcocet.api.LightingEntrypoint;
import top.mcocet.lighting.loader.LoaderType;
import top.mcocet.lighting.loader.ModContainer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

/**
 * Fabric 生态桥:fabric.mod.json 的 entrypoints 按 Fabric 语义调度——
 * <ul>
 *   <li>{@code "cls::method" 引用}:调用指定方法(静态方法直接调用,
 *       实例方法先实例化);</li>
 *   <li>纯类名:实例化后调用 {@code onInitialize()}(ModInitializer 语义),
 *       本项目的 {@link LightingEntrypoint} 同样适用。</li>
 * </ul>
 * 入口类经游戏侧类加载器加载;类缺失(演示 jar 仅含元数据)或 Fabric API
 * 未组装时,记为失败说明而非中断。
 */
public final class FabricBridge implements EcosystemBridge {

    /** 调度时机为客户端游戏进程,main 与 client 入口都适用 */
    private static final List<String> ENTRY_TYPES = List.of("main", "client");

    @Override
    public LoaderType loaderType() {
        return LoaderType.FABRIC;
    }

    @Override
    public String displayName() {
        return "Fabric";
    }

    @Override
    public String apiProbeClass() {
        return "net.fabricmc.api.ModInitializer";
    }

    @Override
    public BridgeOutcome schedule(ModContainer mod, ClassLoader loader) {
        Map<String, List<String>> entrypoints = mod.metadata().entrypoints();
        int total = 0;
        int invoked = 0;
        StringBuilder failures = new StringBuilder();
        for (String type : ENTRY_TYPES) {
            for (String declaration : entrypoints.getOrDefault(type, List.of())) {
                total++;
                try {
                    invokeEntrypoint(declaration, loader);
                    invoked++;
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    if (!failures.isEmpty()) {
                        failures.append("; ");
                    }
                    failures.append(declaration).append(" -> ").append(e);
                }
            }
        }
        String message = invoked + "/" + total + " entrypoint(s) invoked";
        if (!failures.isEmpty()) {
            message += "; failed: " + failures;
        }
        return new BridgeOutcome(mod.id(), displayName(), invoked > 0, message);
    }

    private static void invokeEntrypoint(String declaration, ClassLoader loader)
            throws ReflectiveOperationException {
        int separator = declaration.indexOf("::");
        String className = separator >= 0 ? declaration.substring(0, separator) : declaration;
        String methodName = separator >= 0 ? declaration.substring(separator + 2) : null;

        Class<?> entryClass = Class.forName(className, false, loader);

        if (methodName != null) {
            Method method = entryClass.getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object target = Modifier.isStatic(method.getModifiers()) ? null : instantiate(entryClass);
            method.invoke(target);
            return;
        }

        Object instance = instantiate(entryClass);
        if (instance instanceof LightingEntrypoint entrypoint) {
            entrypoint.onInitialize();
            return;
        }
        Method method = entryClass.getDeclaredMethod("onInitialize");
        method.setAccessible(true);
        method.invoke(instance);
    }

    private static Object instantiate(Class<?> clazz) throws ReflectiveOperationException {
        var constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
