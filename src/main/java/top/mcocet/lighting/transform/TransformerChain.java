package top.mcocet.lighting.transform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 转换管线:按注册顺序串联所有 {@link ClassTransformer},由 {@code GameClassLoader}
 * 在 defineClass 之前统一调用。
 * <p>
 * 顺序敏感(与 Fabric Loader 的语义一致):先前转换器的输出是后续转换器的输入。
 * 启动流程改写器应最先注册,随后才是 Mod 的 Mixin 转换器(M3)。
 */
public final class TransformerChain {

    private final List<ClassTransformer> transformers = new CopyOnWriteArrayList<>();

    public TransformerChain add(ClassTransformer transformer) {
        transformers.add(transformer);
        return this;
    }

    /** 依次应用全部转换器 */
    public byte[] transform(String className, byte[] classBytes, ClassLoader classLoader) {
        byte[] current = classBytes;
        for (ClassTransformer transformer : transformers) {
            current = transformer.transform(className, current, classLoader);
        }
        return current;
    }

    public List<ClassTransformer> transformers() {
        return List.copyOf(transformers);
    }
}
