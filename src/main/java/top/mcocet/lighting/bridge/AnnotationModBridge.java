package top.mcocet.lighting.bridge;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import top.mcocet.lighting.loader.LoaderType;
import top.mcocet.lighting.loader.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Forge 系({@code @Mod} 注解)生态桥:用 ASM 扫描 Mod jar,定位带指定
 * 注解的主类,反射实例化——实例化即入口,对应 FML 的 mod 构造语义。
 * <p>
 * 26.x 世代 Forge 与 NeoForge 的 {@code @Mod} 注解位于各自包名,由构造
 * 参数区分。构造器解析顺序:{@code ()} 无参 → {@code (IEventBus)} 单参;
 * 后者在生态库(FML loader/bus,经
 * {@link top.mcocet.lighting.launch.EcosystemAssembly} 装配)就绪时,
 * 桥会创建真实事件总线实例注入,覆盖 {@code DeferredRegister.register(bus)}
 * 一类的典型 NeoForge mod 构造链;其余 FML 注入形态记入失败说明。
 * <p>
 * 扫描阶段同时读取注解声明值:{@code value}(modid,与 Mod 元数据核对)
 * 与 {@code dist}(运行侧声明,记入备注供观测)。
 */
public final class AnnotationModBridge implements EcosystemBridge {

    private static final String EVENT_BUS_CLASS = "net.neoforged.bus.api.IEventBus";
    private static final String BUS_BUILDER_CLASS = "net.neoforged.bus.api.BusBuilder";

    private final LoaderType loaderType;
    private final String displayName;
    private final String annotationDescriptor;
    private final String apiProbeClass;

    /**
     * @param annotationDescriptor 注解描述符,如 {@code Lnet/neoforged/fml/common/Mod;}
     * @param apiProbeClass        生态 API 标志类全名
     */
    public AnnotationModBridge(LoaderType loaderType, String displayName,
                               String annotationDescriptor, String apiProbeClass) {
        this.loaderType = loaderType;
        this.displayName = displayName;
        this.annotationDescriptor = annotationDescriptor;
        this.apiProbeClass = apiProbeClass;
    }

    @Override
    public LoaderType loaderType() {
        return loaderType;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String apiProbeClass() {
        return apiProbeClass;
    }

    @Override
    public BridgeOutcome schedule(ModContainer mod, ClassLoader loader) {
        List<AnnotatedModClass> modClasses;
        try {
            modClasses = findAnnotatedClasses(mod.jarPath());
        } catch (IOException e) {
            return new BridgeOutcome(mod.id(), displayName, false, "jar scan failed: " + e);
        }
        if (modClasses.isEmpty()) {
            return new BridgeOutcome(mod.id(), displayName, false,
                    "@Mod entry class not found in jar (metadata-only jar?)");
        }

        int invoked = 0;
        List<String> notes = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (AnnotatedModClass info : modClasses) {
            if (info.declaredId() != null && !info.declaredId().equals(mod.id())) {
                notes.add(info.className() + " declares @Mod(\"" + info.declaredId()
                        + "\") but the mod metadata id is " + mod.id());
            }
            if (!info.dists().isEmpty()) {
                notes.add(info.className() + " declares dist=" + info.dists());
            }
            try {
                Class<?> modClass = Class.forName(info.className(), false, loader);
                invoked += construct(modClass, loader, info, failures);
            } catch (ReflectiveOperationException | LinkageError e) {
                failures.add(info.className() + " (" + e + ")");
            }
        }
        String message = invoked + "/" + modClasses.size() + " @Mod class(es) constructed";
        if (!notes.isEmpty()) {
            message += "; notes: " + String.join(", ", notes);
        }
        if (!failures.isEmpty()) {
            message += "; failed: " + String.join(", ", failures);
        }
        return new BridgeOutcome(mod.id(), displayName, invoked > 0, message);
    }

    /** 构造单个 @Mod 类:优先无参构造,其次注入真实事件总线的单参构造 */
    private int construct(Class<?> modClass, ClassLoader loader, AnnotatedModClass info,
                          List<String> failures) throws ReflectiveOperationException {
        try {
            Constructor<?> noArg = modClass.getDeclaredConstructor();
            noArg.setAccessible(true);
            noArg.newInstance();
            return 1;
        } catch (NoSuchMethodException ignored) {
            // 继续尝试 FML 风格的事件总线注入构造
        }
        Constructor<?> busConstructor = findBusConstructor(modClass, loader);
        if (busConstructor == null) {
            failures.add(info.className() + " (constructor requires FML injection)");
            return 0;
        }
        Object bus = createEventBus(loader);
        if (bus == null) {
            failures.add(info.className()
                    + " (IEventBus constructor requires the event bus library in .lighting/ecosystem)");
            return 0;
        }
        busConstructor.setAccessible(true);
        busConstructor.newInstance(bus);
        return 1;
    }

    /** 定位单参 {@code (IEventBus)} 构造器;总线段不可加载时返回 null */
    private static Constructor<?> findBusConstructor(Class<?> modClass, ClassLoader loader) {
        Class<?> busInterface = loadClass(EVENT_BUS_CLASS, loader);
        if (busInterface == null) {
            return null;
        }
        try {
            return modClass.getDeclaredConstructor(busInterface);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** 用生态库中的 BusBuilder 创建真实事件总线实例;不可用时返回 null */
    private static Object createEventBus(ClassLoader loader) {
        Class<?> builderClass = loadClass(BUS_BUILDER_CLASS, loader);
        if (builderClass == null) {
            return null;
        }
        try {
            Object builder = builderClass.getMethod("builder").invoke(null);
            return builderClass.getMethod("build").invoke(builder);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Class<?> loadClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** 扫描 jar 内全部类文件,收集带目标注解的类名与注解声明值(不加载类) */
    private List<AnnotatedModClass> findAnnotatedClasses(Path jarPath) throws IOException {
        List<AnnotatedModClass> result = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(in);
                    ModAnnotationReader[] annotationReader = new ModAnnotationReader[1];
                    AnnotatedModClass[] hit = new AnnotatedModClass[1];
                    reader.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!annotationDescriptor.equals(descriptor)) {
                                return null;
                            }
                            ModAnnotationReader modReader = new ModAnnotationReader();
                            annotationReader[0] = modReader;
                            return modReader;
                        }

                        @Override
                        public void visitEnd() {
                            // 注解内容在 visitEnd 前已读取完毕,此处统一落结果
                            if (annotationReader[0] != null) {
                                hit[0] = new AnnotatedModClass(
                                        reader.getClassName().replace('/', '.'),
                                        annotationReader[0].declaredId(),
                                        annotationReader[0].dists());
                            }
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (hit[0] != null && !result.contains(hit[0])) {
                        result.add(hit[0]);
                    }
                }
            }
        }
        result.sort(null);
        return result;
    }

    /** {@code @Mod} 声明值读取器:{@code value} 与 {@code dist} 数组 */
    private static final class ModAnnotationReader extends AnnotationVisitor {

        private String declaredId;
        private final List<String> dists = new ArrayList<>();

        ModAnnotationReader() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(String name, Object value) {
            if ("value".equals(name) && value instanceof String id) {
                declaredId = id;
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if (!"dist".equals(name)) {
                return null;
            }
            // 枚举数组元素经 visitEnum 传入(name 为 null)
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visitEnum(String ignored, String descriptor, String value) {
                    dists.add(value);
                }
            };
        }

        String declaredId() {
            return declaredId;
        }

        List<String> dists() {
            return List.copyOf(dists);
        }
    }

    /** 扫描结果:类名 + 注解声明值 */
    private record AnnotatedModClass(String className, String declaredId, List<String> dists)
            implements Comparable<AnnotatedModClass> {

        @Override
        public int compareTo(AnnotatedModClass other) {
            return className.compareTo(other.className);
        }
    }
}
