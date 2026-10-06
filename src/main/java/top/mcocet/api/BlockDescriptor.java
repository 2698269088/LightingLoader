package top.mcocet.api;

import java.util.Objects;

/**
 * 方块注册描述符:纯数据(零游戏依赖),由 Mod 在 RegisterEvent 阶段经
 * {@link LightingContent#registerBlock(BlockDescriptor)} 登记;加载器的
 * 游戏侧接管层会在原版注册表冻结前把它实例化为真实的 {@code Block}
 * 并注入 {@code BuiltInRegistries.BLOCK}。
 * <pre>{@code
 * LightingContent.registerBlock(BlockDescriptor.builder("examplemod:ruby_block")
 *         .hardness(3.0F).resistance(6.0F).lightLevel(4).requiresTool(true)
 *         .build());
 * }</pre>
 */
public final class BlockDescriptor {

    private final String id;
    private final float hardness;
    private final float resistance;
    private final int lightLevel;
    private final boolean requiresTool;
    private final String sound;
    private final String mapColor;
    private final boolean occludes;
    private final boolean randomTicks;

    private BlockDescriptor(Builder builder) {
        this.id = builder.id;
        this.hardness = builder.hardness;
        this.resistance = builder.resistance;
        this.lightLevel = builder.lightLevel;
        this.requiresTool = builder.requiresTool;
        this.sound = builder.sound;
        this.mapColor = builder.mapColor;
        this.occludes = builder.occludes;
        this.randomTicks = builder.randomTicks;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 全限定 ID(namespace:path) */
    public String id() {
        return id;
    }

    public float hardness() {
        return hardness;
    }

    public float resistance() {
        return resistance;
    }

    /** 发光等级 0-15 */
    public int lightLevel() {
        return lightLevel;
    }

    /** 是否需要正确工具才能掉落 */
    public boolean requiresTool() {
        return requiresTool;
    }

    /** 音效名:stone/wood/grass/gravel/metal/glass/wool/sand/snow/empty */
    public String sound() {
        return sound;
    }

    /** 地图颜色名:stone/wood/plant/sand/wool/metal/ice/dirt/grass/water/clay/gold/quartz/nether/snow/fire */
    public String mapColor() {
        return mapColor;
    }

    /** 是否遮挡视线(对应 noOcclusion 的反义) */
    public boolean occludes() {
        return occludes;
    }

    public boolean randomTicks() {
        return randomTicks;
    }

    /**
     * 方块描述符构建器;默认值与游戏内常规方块一致
     * (硬度 1.5 / 抗爆 6.0 / 石质音效与颜色 / 遮挡视线)。
     */
    public static final class Builder {

        private final String id;
        private float hardness = 1.5F;
        private float resistance = 6.0F;
        private int lightLevel;
        private boolean requiresTool;
        private String sound = "stone";
        private String mapColor = "stone";
        private boolean occludes = true;
        private boolean randomTicks;

        private Builder(String id) {
            String value = Objects.requireNonNull(id, "id");
            if (value.indexOf(':') < 0) {
                throw new IllegalArgumentException(
                        "Block id must be fully qualified (namespace:path): " + value);
            }
            this.id = value;
        }

        public Builder hardness(float hardness) {
            this.hardness = hardness;
            return this;
        }

        public Builder resistance(float resistance) {
            this.resistance = resistance;
            return this;
        }

        public Builder lightLevel(int lightLevel) {
            this.lightLevel = Math.max(0, Math.min(15, lightLevel));
            return this;
        }

        public Builder requiresTool(boolean requiresTool) {
            this.requiresTool = requiresTool;
            return this;
        }

        public Builder sound(String sound) {
            this.sound = Objects.requireNonNull(sound, "sound");
            return this;
        }

        public Builder mapColor(String mapColor) {
            this.mapColor = Objects.requireNonNull(mapColor, "mapColor");
            return this;
        }

        public Builder occludes(boolean occludes) {
            this.occludes = occludes;
            return this;
        }

        public Builder randomTicks(boolean randomTicks) {
            this.randomTicks = randomTicks;
            return this;
        }

        public BlockDescriptor build() {
            return new BlockDescriptor(this);
        }
    }
}
