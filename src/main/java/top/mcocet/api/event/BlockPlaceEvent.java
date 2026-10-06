package top.mcocet.api.event;

/**
 * 方块放置事件(可取消)。
 * <p>
 * 事件类型与取消契约已就绪;派发锚点(放置成功路径)将在
 * 后续增量中接入,当前以监听注册可见性供 Mod 预适配。
 */
public final class BlockPlaceEvent implements CancellableEvent {

    private final String playerName;
    private final int x;
    private final int y;
    private final int z;
    private boolean cancelled;

    public BlockPlaceEvent(String playerName, int x, int y, int z) {
        this.playerName = playerName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String playerName() {
        return playerName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
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
