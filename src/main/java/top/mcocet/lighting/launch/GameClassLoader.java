package top.mcocet.lighting.launch;

import top.mcocet.lighting.transform.TransformerChain;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 游戏侧类加载器:加载游戏本体、依赖库与全部 Mod 类,
 * 并在 {@code defineClass} 之前将字节码送入 {@link TransformerChain} 转换。
 * <p>
 * 父加载器为系统/应用加载器,因此 Lighting 自身与 JDK 类不会被本加载器
 * 重复加载;只有父找不到的类(游戏与 Mod 类)才会走到 {@link #findClass},
 * 在此触发转换管线——这正是"类加载期改写游戏"的落点。
 */
public final class GameClassLoader extends URLClassLoader {

    /**
     * 游戏侧 UI/钩子代码包前缀。该包内的类必须由本加载器定义,
     * 因为其引用游戏 GUI 类(如 Screen/Button),父加载器不可见。
     */
    public static final String GAME_SIDE_PACKAGE = "top.mcocet.lighting.game.";

    private final TransformerChain chain;

    public GameClassLoader(URL[] urls, ClassLoader parent, TransformerChain chain) {
        super("LightingGame", urls, parent);
        this.chain = chain;
    }

    public TransformerChain transformerChain() {
        return chain;
    }

    /**
     * 对游戏侧包({@link #GAME_SIDE_PACKAGE})打破父委托、自身优先加载,
     * 保证类里能直接使用游戏 API;其余类维持标准父委托。
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith(GAME_SIDE_PACKAGE)) {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
        return super.loadClass(name, resolve);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String resource = name.replace('.', '/') + ".class";
        // findResource 只搜索本加载器自己的 URL,不触发父委托,保证转换只作用于本级的类
        URL url = findResource(resource);
        if (url == null) {
            return super.findClass(name);
        }
        try (InputStream in = url.openStream()) {
            byte[] raw = in.readAllBytes();
            byte[] transformed = chain.transform(name, raw, this);
            return defineClass(name, transformed, 0, transformed.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}
