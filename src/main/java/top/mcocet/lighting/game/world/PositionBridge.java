package top.mcocet.lighting.game.world;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * 位置处理桥——游戏侧统一坐标能力层:
 * 打包/还原、闭区间遍历、体积与距离计算。供注册接管层、
 * NBT 桥与上层模组系统共用。{@link #selfCheck()} 在注册表就绪
 * 钩子中执行全链路自检。
 */
public final class PositionBridge {

    private static final Logger LOGGER = LogManager.getLogger(PositionBridge.class);

    /** 单次 {@link #between} 遍历的最大体积,防御超大区域耗尽内存 */
    public static final long MAX_VOLUME = 4096L;

    private PositionBridge() {
    }

    /** 打包为单个 long(BlockPos 内部布局) */
    public static long pack(BlockPos pos) {
        return pos.asLong();
    }

    /** 由打包 long 还原 */
    public static BlockPos unpack(long packed) {
        return BlockPos.of(packed);
    }

    /** 两点之间的闭区间体积(按方块计数) */
    public static long volume(BlockPos from, BlockPos to) {
        long dx = Math.abs((long) to.getX() - from.getX()) + 1L;
        long dy = Math.abs((long) to.getY() - from.getY()) + 1L;
        long dz = Math.abs((long) to.getZ() - from.getZ()) + 1L;
        return dx * dy * dz;
    }

    /** 两点间欧氏距离的平方(整数坐标,避免开方) */
    public static double distanceSquared(BlockPos from, BlockPos to) {
        long dx = (long) to.getX() - from.getX();
        long dy = (long) to.getY() - from.getY();
        long dz = (long) to.getZ() - from.getZ();
        return (double) dx * dx + (double) dy * dy + (double) dz * dz;
    }

    /** 枚举两点间闭区间内的全部位置(自西向东、自下而上、自南向北) */
    public static List<BlockPos> between(BlockPos from, BlockPos to) {
        long volume = volume(from, to);
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException(
                    "Region volume " + volume + " exceeds the " + MAX_VOLUME + " limit");
        }
        List<BlockPos> positions = new ArrayList<>((int) volume);
        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    /** 以文本描述位置(用于日志与命令回声) */
    public static String describe(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    /**
     * 自检:打包/还原往返、距离平方、体积、闭区间枚举;
     * 任一步失败返回 false。
     */
    public static boolean selfCheck() {
        try {
            BlockPos origin = new BlockPos(96, -32, 512);
            if (!unpack(pack(origin)).equals(origin)) {
                return false;
            }
            if (distanceSquared(origin, new BlockPos(99, -28, 512)) != 25.0D) {
                return false;
            }
            if (volume(origin, new BlockPos(98, -29, 516)) != 60L) {
                return false;
            }
            if (between(origin, new BlockPos(97, -31, 513)).size() != 8) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Position bridge self-check threw: {}", t.toString());
            return false;
        }
    }
}
