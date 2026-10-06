package top.mcocet.lighting.loading;

import java.util.List;

/**
 * 加载失败异常——移植自 NeoForge {@code ModLoadingException} 的聚合语义:
 * 不因单个问题立即退出,而是把全部致命问题收集完成后一次性抛出,
 * 异常消息即完整的问题清单,启动入口捕获后中止启动并原样上报。
 */
public final class ModLoadingException extends RuntimeException {

    private final List<ModLoadingIssue> issues;

    public ModLoadingException(List<ModLoadingIssue> issues) {
        super(buildMessage(issues));
        this.issues = List.copyOf(issues);
    }

    /** 触发加载失败的全部致命问题 */
    public List<ModLoadingIssue> issues() {
        return issues;
    }

    private static String buildMessage(List<ModLoadingIssue> issues) {
        StringBuilder builder = new StringBuilder("Mod loading failed with ")
                .append(issues.size()).append(" fatal issue(s):");
        for (ModLoadingIssue issue : issues) {
            builder.append("\n  - ").append(issue.message());
        }
        return builder.toString();
    }
}
