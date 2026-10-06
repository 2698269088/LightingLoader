package top.mcocet.lighting.game.nbt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;

/**
 * NBT 处理桥——游戏侧统一 NBT 能力层:
 * <ul>
 *   <li>位置打包:BlockPos 与单个 long 的无损互转;</li>
 *   <li>UUID 打包:与 NBT int[] 表示互转;</li>
 *   <li>二进制序列化:与原版 region/level.dat 相同的压缩编码往返;</li>
 *   <li>SNBT 解析:命令风格文本 → CompoundTag。</li>
 * </ul>
 * {@link #selfCheck()} 在注册表就绪钩子中执行全链路自检。
 */
public final class NbtBridge {

    private static final Logger LOGGER = LogManager.getLogger(NbtBridge.class);

    /** 标准字段名:经本桥持久化的描述符数据共用 */
    public static final String FIELD_OWNER = "owner";
    public static final String FIELD_COUNTER = "counter";
    public static final String FIELD_POS = "pos";
    public static final String FIELD_UUID = "uuid";

    private NbtBridge() {
    }

    /** 位置打包为单个 long(与 BlockPos 内部 21 位 xyz 布局一致) */
    public static long packPos(BlockPos pos) {
        return pos.asLong();
    }

    /** 由打包 long 还原位置 */
    public static BlockPos unpackPos(long packed) {
        return BlockPos.of(packed);
    }

    /** UUID 打包为 4 个 int(高位在前,便于 NBT int[] 存储) */
    public static int[] toIntArray(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
    }

    /** 由 4 个 int 还原 UUID */
    public static UUID fromIntArray(int[] array) {
        if (array == null || array.length != 4) {
            throw new IllegalArgumentException(
                    "UUID array must have exactly 4 ints, got " + (array == null ? "null" : array.length));
        }
        long most = ((long) array[0] << 32) | (array[1] & 0xFFFFFFFFL);
        long least = ((long) array[2] << 32) | (array[3] & 0xFFFFFFFFL);
        return new UUID(most, least);
    }

    /** 压缩二进制 NBT 序列化(与原版存档相同的编码) */
    public static byte[] toCompressedBytes(CompoundTag tag) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        return out.toByteArray();
    }

    /** 压缩二进制 NBT 反序列化 */
    public static CompoundTag fromCompressedBytes(byte[] bytes) throws IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
    }

    /** 解析 SNBT 文本为 CompoundTag(命令风格 {@code {key:value}} 语法) */
    public static CompoundTag parseSnbt(String snbt) throws CommandSyntaxException {
        return TagParser.parseCompoundFully(snbt);
    }

    /**
     * 自检:覆盖"位置打包 → 字段写入 → 压缩二进制往返 → SNBT 解析 →
     * 字段还原"全链路;任一步失败返回 false。
     */
    public static boolean selfCheck() {
        try {
            BlockPos origin = new BlockPos(123, 64, -456);
            UUID uuid = UUID.fromString("f7de1b9c-9d3a-4b0e-8f5c-2a1d4e6f8a0b");

            CompoundTag tag = new CompoundTag();
            tag.putString(FIELD_OWNER, "lighting");
            tag.putInt(FIELD_COUNTER, 42);
            tag.putLong(FIELD_POS, packPos(origin));
            tag.putIntArray(FIELD_UUID, toIntArray(uuid));

            CompoundTag restored = fromCompressedBytes(toCompressedBytes(tag));
            if (!"lighting".equals(restored.getStringOr(FIELD_OWNER, ""))
                    || restored.getIntOr(FIELD_COUNTER, 0) != 42
                    || !unpackPos(restored.getLongOr(FIELD_POS, 0L)).equals(origin)) {
                return false;
            }
            int[] uuidArray = restored.getIntArray(FIELD_UUID).orElse(null);
            if (uuidArray == null || !fromIntArray(uuidArray).equals(uuid)) {
                return false;
            }

            CompoundTag fromSnbt = parseSnbt("{owner:\"lighting\",counter:42}");
            return fromSnbt.getIntOr(FIELD_COUNTER, 0) == 42;
        } catch (Throwable t) {
            LOGGER.warn("Nbt bridge self-check threw: {}", t.toString());
            return false;
        }
    }
}
