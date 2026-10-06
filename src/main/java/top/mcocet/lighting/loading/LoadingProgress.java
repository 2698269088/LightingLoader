package top.mcocet.lighting.loading;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 加载进度监听器——对应 FML 早期的 {@code DisplayWindow} / 进度屏抽象。
 * <p>
 * 骨架阶段以日志实现默认输出;图形化加载屏在接入真实游戏环境后
 * 实现同一接口即可(每次 {@code stage} 调用同步一条"第 i / N 阶段"进度)。
 */
public interface LoadingProgress {

    /**
     * 报告一个已完成的加载阶段。
     *
     * @param name  阶段名(英文,面向日志/界面)
     * @param index 当前阶段序号(从 1 起)
     * @param total 总阶段数
     */
    void stage(String name, int index, int total);

    /** 默认实现:输出到日志 */
    static LoadingProgress console() {
        return new Console();
    }

    final class Console implements LoadingProgress {

        // 日志名保持可读的 LoadingProgress,同时落在 log4j2 配置放行的 top.mcocet.lighting 包内
        private static final Logger LOGGER = LogManager.getLogger("top.mcocet.lighting.LoadingProgress");

        @Override
        public void stage(String name, int index, int total) {
            LOGGER.info("Loading progress [{}/{}]: {}", index, total, name);
        }
    }
}
