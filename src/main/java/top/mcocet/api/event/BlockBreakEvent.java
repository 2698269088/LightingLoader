package top.mcocet.api.event;

/**
 * 方块破坏事件(可取消)——注入点:{@code ServerPlayerGameMode.destroyBlock}。
 * <p>
 * 取消语义为"强制否决":服务端在被否决时不会移除该方块
 * (注入的早退分支返回 false,与原版失败语义一致)。
 */
public final class BlockBreakEvent implements CancellableEvent {

    private final String playerName;
    private final int x;
    private final int y;
    private final int z;
    private boolean cancelled;

    public BlockBreakEvent(String playerName, int x, int y, int z) {
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
