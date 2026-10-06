package top.mcocet.lighting.bridge;

import top.mcocet.lighting.loader.LoaderType;
import top.mcocet.lighting.loader.ModContainer;

/**
 * 语言适配器选择——对应 FML 的 {@code ILanguageLoader} 语义:
 * 依据清单声明选择把 Mod 交给哪个入口调度实现。
 * <p>
 * 映射关系:
 * <ul>
 *   <li>Fabric 清单 → {@code fabric} 适配器({@link FabricBridge})</li>
 *   <li>Forge/NeoForge 清单,{@code modLoader="javafml"} → {@code javafml} 适配器
 *       ({@link AnnotationModBridge})</li>
 *   <li>其余语言(如 {@code lowcodefml})为已声明但本构建不支持的语言,
 *       调度时跳过并给出明确告警</li>
 * </ul>
 */
public final class LanguageAdapters {

    private LanguageAdapters() {
    }

    /**
     * 一个 Mod 的适配器选择结果。
     *
     * @param adapterId  适配器标识(fabric / javafml / 其它语言名)
     * @param bridge     对应生态的入口桥接实现
     * @param supported  本构建是否支持该语言
     */
    public record Selection(String adapterId, EcosystemBridge bridge, boolean supported) {
    }

    /** 依据清单声明为 Mod 选择语言适配器 */
    public static Selection select(ModContainer mod, EcosystemBridge fabricBridge,
                                   EcosystemBridge javafmlBridge) {
        if (mod.loaderType() == LoaderType.FABRIC) {
            return new Selection("fabric", fabricBridge, true);
        }
        // Forge 系:modLoader 字段声明语言,javafml 为默认
        String modLoader = mod.metadata().extras().getOrDefault("modLoader", "javafml");
        if ("javafml".equals(modLoader)) {
            return new Selection("javafml", javafmlBridge, true);
        }
        return new Selection(modLoader, javafmlBridge, false);
    }
}
