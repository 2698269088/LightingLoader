package top.mcocet.api.event;

/**
 * Tick 事件族标记:START/END 两相分别在一次 tick 的主体执行前后派发。
 * <p>
 * 派发点(均经字节码注入,锚点已在 26.1.2 字节码层面核实):
 * <ul>
 *   <li>{@link ServerTickEvent}:{@code MinecraftServer.tickServer}</li>
 *   <li>{@link LevelTickEvent}:{@code ServerLevel.tick}</li>
 *   <li>{@link PlayerTickEvent}:{@code ServerPlayer.tick}</li>
 *   <li>{@link ClientTickEvent}:{@code Minecraft.tick}(客户端)</li>
 * </ul>
 */
public interface TickEvent {

    /** tick 相位 */
    enum Phase {
        /** tick 主体执行前 */
        START,
        /** tick 主体执行后 */
        END
    }

    /** 当前相位 */
    Phase phase();
}
