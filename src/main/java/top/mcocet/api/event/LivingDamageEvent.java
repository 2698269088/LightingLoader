package top.mcocet.api.event;

/**
 * 生物受伤事件(可取消,可修改伤害量)。
 * <p>
 * 事件类型与取消契约已就绪;派发锚点({@code LivingEntity.hurtServer}
 * 路径)将在后续增量中接入。
 */
public final class LivingDamageEvent implements CancellableEvent {

    private final String entityDescription;
    private final String damageSource;
    private float amount;
    private boolean cancelled;

    public LivingDamageEvent(String entityDescription, String damageSource, float amount) {
        this.entityDescription = entityDescription;
        this.damageSource = damageSource;
        this.amount = amount;
    }

    public String entityDescription() {
        return entityDescription;
    }

    public String damageSource() {
        return damageSource;
    }

    public float amount() {
        return amount;
    }

    /** 修改本事件的伤害量(破坏性监听器可据此加强/减免) */
    public void setAmount(float amount) {
        this.amount = amount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
