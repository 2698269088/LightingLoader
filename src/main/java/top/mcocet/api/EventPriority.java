package top.mcocet.api;

/**
 * 监听器优先级(Forge 语义):枚举声明顺序即调用顺序,
 * {@link #HIGHEST} 最先被调用,{@link #LOWEST} 最后;
 * 同一优先级内按注册顺序调用。
 */
public enum EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}
