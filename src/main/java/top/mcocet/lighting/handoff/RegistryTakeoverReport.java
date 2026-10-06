package top.mcocet.lighting.handoff;

/**
 * 原版注册表接管统计:游戏侧在冻结前窗口完成注入后回报给内核。
 *
 * @param blocks   成功注入的方块数量
 * @param entities 成功注入的实体类型数量
 * @param failures 注入失败的条目数量
 */
public record RegistryTakeoverReport(int blocks, int entities, int failures) {

    /** 成功注入的条目总数 */
    public int total() {
        return blocks + entities;
    }
}
