package top.mcocet.api.datagen;

import java.io.IOException;

/**
 * 数据生成器——对应 NeoForge 的 {@code DataProvider}。
 * <p>
 * Mod 在 {@code GatherDataEvent} 中向生成器登记本接口实现;
 * 数据生成模式下加载器为每个 provider 分配独立输出根
 * ({@code <output>/<modId>/<provider 名>})并调用 {@link #run(DataWriter)},
 * provider 经 {@link DataWriter} 写出配方/战利品表/模型/语言等 JSON 文件。
 */
public interface DataProvider {

    /** provider 名(用作输出目录名;建议使用小写字母数字加连字符) */
    String name();

    /** 生成全部数据文件(异常向上传播,终止数据生成并记录) */
    void run(DataWriter writer) throws IOException;
}
