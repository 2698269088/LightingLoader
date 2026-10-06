package top.mcocet.lighting.loading;

/**
 * 加载问题条目——移植自 NeoForge {@code ModLoadingIssue} 的"先收集、后统一上报"模型。
 * <p>
 * 加载过程中不做即抛即停:所有问题先收集成列表,全部阶段结束后统一呈现,
 * 让玩家在一次日志里看到所有需要修复的问题。
 * 警告不阻断启动;致命问题聚合进 {@link ModLoadingException},在解析阶段结束时抛出。
 *
 * @param severity 问题级别
 * @param message  问题描述(英文,面向日志)
 */
public record ModLoadingIssue(Severity severity, String message) {

    public enum Severity {
        /** 不阻断启动:缺失的生态/平台依赖、未知清单细节等 */
        WARNING,
        /** 阻断启动:已加载目标上的版本范围冲突、违反 CONFLICTS/BREAKS 声明等 */
        ERROR
    }

    public static ModLoadingIssue warning(String message) {
        return new ModLoadingIssue(Severity.WARNING, message);
    }

    public static ModLoadingIssue error(String message) {
        return new ModLoadingIssue(Severity.ERROR, message);
    }

    /** 是否为致命问题 */
    public boolean fatal() {
        return severity == Severity.ERROR;
    }
}
