package top.mcocet.lighting.game.entity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 描述符实体——由 Mod 登记的 {@code EntityDescriptor} 实例化而来的通用实体,
 * 是"实体注册全面接管"的落点类型。
 * <p>
 * 本类位于 {@code top.mcocet.lighting.game.} 包,由 {@code GameClassLoader}
 * 自身优先定义,因此可直接实现游戏侧契约;持久化字段经由统一的
 * {@code ValueInput}/{@code ValueOutput} 桥读写(对应 NBT 处理层)。
 */
public class DescriptorEntity extends Entity {

    /** 构建本实体的描述符 ID(namespace:path;随存档持久化) */
    private String descriptorId = "";

    /** 存活刻数(演示性持久化字段,验证 ValueInput/ValueOutput 桥) */
    private int ticksAlive;

    public DescriptorEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setDescriptorId(String descriptorId) {
        this.descriptorId = descriptorId == null ? "" : descriptorId;
    }

    public String descriptorId() {
        return descriptorId;
    }

    public int ticksAlive() {
        return ticksAlive;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 描述符实体没有默认同步字段;为上层模组系统保留扩展点
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.descriptorId = input.getStringOr("descriptor_id", "");
        this.ticksAlive = input.getIntOr("ticks_alive", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("descriptor_id", descriptorId);
        output.putInt("ticks_alive", ticksAlive);
    }

    @Override
    public void tick() {
        super.tick();
        ticksAlive++;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // 描述符实体默认不可伤害;行为扩展由上层模组系统后续提供
        return false;
    }
}
