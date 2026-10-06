package top.mcocet.api;

import java.util.Objects;

/**
 * 实体注册描述符:纯数据(零游戏依赖),由 Mod 在 RegisterEvent 阶段经
 * {@link LightingContent#registerEntity(EntityDescriptor)} 登记;加载器的
 * 游戏侧接管层会在原版注册表冻结前用描述符构建真实的 {@code EntityType}
 * 并注入 {@code BuiltInRegistries.ENTITY_TYPE}。
 * <p>
 * 描述符实体的持久化字段(owner/counter)由统一 NBT 桥管理,见游戏侧
 * {@code top.mcocet.lighting.game.entity.DescriptorEntity}。
 */
public final class EntityDescriptor {

    private final String id;
    private final float width;
    private final float height;
    private final String category;
    private final boolean fireImmune;
    private final boolean noSave;
    private final boolean noSummon;
    private final int trackingRange;
    private final int updateInterval;

    private EntityDescriptor(Builder builder) {
        this.id = builder.id;
        this.width = builder.width;
        this.height = builder.height;
        this.category = builder.category;
        this.fireImmune = builder.fireImmune;
        this.noSave = builder.noSave;
        this.noSummon = builder.noSummon;
        this.trackingRange = builder.trackingRange;
        this.updateInterval = builder.updateInterval;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 全限定 ID(namespace:path) */
    public String id() {
        return id;
    }

    /** 碰撞箱宽度(格) */
    public float width() {
        return width;
    }

    /** 碰撞箱高度(格) */
    public float height() {
        return height;
    }

    /** 生成分类:monster/creature/ambient/axolotls/water_creature/water_ambient/underground_water_creature/misc */
    public String category() {
        return category;
    }

    public boolean fireImmune() {
        return fireImmune;
    }

    public boolean noSave() {
        return noSave;
    }

    public boolean noSummon() {
        return noSummon;
    }

    /** 客户端追踪距离(区块) */
    public int trackingRange() {
        return trackingRange;
    }

    /** 同步更新间隔(刻) */
    public int updateInterval() {
        return updateInterval;
    }

    /** 实体描述符构建器;默认值与原版常规生物一致(0.6×1.8 碰撞箱 / misc 分类)。 */
    public static final class Builder {

        private final String id;
        private float width = 0.6F;
        private float height = 1.8F;
        private String category = "misc";
        private boolean fireImmune;
        private boolean noSave;
        private boolean noSummon;
        private int trackingRange = 5;
        private int updateInterval = 3;

        private Builder(String id) {
            String value = Objects.requireNonNull(id, "id");
            if (value.indexOf(':') < 0) {
                throw new IllegalArgumentException(
                        "Entity id must be fully qualified (namespace:path): " + value);
            }
            this.id = value;
        }

        public Builder width(float width) {
            this.width = width;
            return this;
        }

        public Builder height(float height) {
            this.height = height;
            return this;
        }

        public Builder category(String category) {
            this.category = Objects.requireNonNull(category, "category");
            return this;
        }

        public Builder fireImmune(boolean fireImmune) {
            this.fireImmune = fireImmune;
            return this;
        }

        public Builder noSave(boolean noSave) {
            this.noSave = noSave;
            return this;
        }

        public Builder noSummon(boolean noSummon) {
            this.noSummon = noSummon;
            return this;
        }

        public Builder trackingRange(int trackingRange) {
            this.trackingRange = Math.max(1, trackingRange);
            return this;
        }

        public Builder updateInterval(int updateInterval) {
            this.updateInterval = Math.max(1, updateInterval);
            return this;
        }

        public EntityDescriptor build() {
            return new EntityDescriptor(this);
        }
    }
}
