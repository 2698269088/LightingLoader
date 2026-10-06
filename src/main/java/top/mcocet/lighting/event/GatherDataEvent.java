package top.mcocet.lighting.event;

import top.mcocet.lighting.datagen.DataGenerator;
import top.mcocet.lighting.loader.ModContainer;

import java.util.List;

/**
 * 数据生成收集事件——对应 NeoForge 的 {@code GatherDataEvent}:
 * 在 {@code --datagen} 模式下、注册表冻结与收尾阶段完成后发布;
 * Mod 监听本事件并调用
 * {@link DataGenerator#addProvider(String, top.mcocet.api.datagen.DataProvider)}
 * 登记自己的数据生成器,随后由加载器统一执行并落盘。
 */
public final class GatherDataEvent {

    private final List<ModContainer> mods;
    private final DataGenerator generator;

    public GatherDataEvent(List<ModContainer> mods, DataGenerator generator) {
        this.mods = mods;
        this.generator = generator;
    }

    /** 当前已加载的全部 Mod */
    public List<ModContainer> mods() {
        return mods;
    }

    /** 数据生成编排器(登记 provider) */
    public DataGenerator generator() {
        return generator;
    }
}
