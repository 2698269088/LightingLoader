package top.mcocet.lighting.launch;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 游戏启动器:把控制权从 Lighting 移交给游戏主类。
 * <p>
 * 标准做法(与 Forge/Fabric 的 launch 阶段一致):
 * <ol>
 *   <li>将线程上下文类加载器切换为 {@link GameClassLoader},
 *       使游戏与 Mod 运行期间的所有惰性类加载都经过转换管线;</li>
 *   <li>反射调用主类 {@code main(String[])}(26.1.2 起类名不混淆,
 *       直接按官方名解析)。</li>
 * </ol>
 * 调用返回即游戏主流程结束(正常退出);异常包装后向上抛出。
 */
public final class GameLauncher {

    private final GameClassLoader classLoader;
    private final String mainClassName;

    public GameLauncher(GameClassLoader classLoader, String mainClassName) {
        this.classLoader = classLoader;
        this.mainClassName = mainClassName;
    }

    public void launch(String[] gameArgs) {
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            Class<?> mainClass = Class.forName(mainClassName, true, classLoader);
            Method main = mainClass.getMethod("main", String[].class);
            main.invoke(null, (Object) gameArgs);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Game main class not found: " + mainClassName
                    + " (check --gameJar and --mainClass)", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Game main class is missing main(String[]): " + mainClassName, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new IllegalStateException("Game main flow exited abnormally: " + cause, cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access the game main class: " + mainClassName, e);
        }
    }
}
