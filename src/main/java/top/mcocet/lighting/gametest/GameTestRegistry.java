package top.mcocet.lighting.gametest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import top.mcocet.lighting.discovery.ModCandidate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * GameTest 登记表——对应 NeoForge 的 {@code GameTestRegistry}:
 * 发现阶段以字节级扫描(不加载类)收集 Mod jar 中
 * {@code @GameTestHolder} 类内 {@code @GameTest} 方法的执行意图,
 * 登记为 {@link GameTestEntry};游戏侧测试桥就绪后以其为输入,
 * 把测试注册进游戏内测试框架。
 * <p>
 * 当前为骨架:扫描 + 登记 + 报告;非标注类经常量池预过滤零成本放行。
 */
public final class GameTestRegistry {

    private static final Logger LOGGER = LogManager.getLogger(GameTestRegistry.class);

    private static final String HOLDER_DESC = "Ltop/mcocet/api/gametest/GameTestHolder;";
    private static final String TEST_DESC = "Ltop/mcocet/api/gametest/GameTest;";
    private static final String PRE_FILTER_ANCHOR = "Ltop/mcocet/api/gametest/GameTest";

    /** 单个测试的登记信息 */
    public record GameTestEntry(String modId, String className, String methodName,
                                String template, int timeoutTicks, String batch) {
    }

    private final List<GameTestEntry> tests = new ArrayList<>();

    private GameTestRegistry() {
    }

    /** 扫描全部 Mod jar,收集 @GameTestHolder/@GameTest 登记信息 */
    public static GameTestRegistry scan(List<ModCandidate> candidates) {
        GameTestRegistry registry = new GameTestRegistry();
        for (ModCandidate candidate : candidates) {
            Path jar = candidate.jarPath();
            if (jar == null || !Files.isRegularFile(jar)) {
                continue;
            }
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry entry : zip.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                    byte[] bytes;
                    try (InputStream in = zip.getInputStream(entry)) {
                        bytes = in.readAllBytes();
                    }
                    scanClass(bytes, registry.tests);
                }
            } catch (IOException e) {
                LOGGER.warn("GameTest scan skipped for {}: {}", jar.getFileName(), e.toString());
            }
        }
        return registry;
    }

    private static void scanClass(byte[] classBytes, List<GameTestEntry> sink) {
        if (!containsAscii(classBytes, PRE_FILTER_ANCHOR)) {
            return; // 预过滤:无标注的类零成本放行
        }
        ClassReader reader = new ClassReader(classBytes);
        String className = reader.getClassName().replace('/', '.');
        String[] holder = {null};
        List<String[]> found = new ArrayList<>(); // [methodName, template, timeoutTicks, batch]
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (HOLDER_DESC.equals(descriptor)) {
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if ("value".equals(name) && value instanceof String modId) {
                                holder[0] = modId;
                            }
                        }
                    };
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean testMethod;
                    private String template = "";
                    private int timeoutTicks = 100;
                    private String batch = "";

                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                        if (!TEST_DESC.equals(annotationDescriptor)) {
                            return null;
                        }
                        testMethod = true;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String attribute, Object value) {
                                switch (attribute) {
                                    case "template" -> template = (String) value;
                                    case "timeoutTicks" -> timeoutTicks = (Integer) value;
                                    case "batch" -> batch = (String) value;
                                    default -> {
                                    }
                                }
                            }
                        };
                    }

                    @Override
                    public void visitEnd() {
                        if (testMethod) {
                            found.add(new String[]{name, template, Integer.toString(timeoutTicks), batch});
                        }
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        // 类级 @GameTestHolder 缺失时全部丢弃
        // (方法注解可能先于类级注解被访问,故统一在遍历结束后判定)
        if (holder[0] == null) {
            return;
        }
        for (String[] probe : found) {
            sink.add(new GameTestEntry(holder[0], className, probe[0], probe[1],
                    Integer.parseInt(probe[2]), probe[3]));
        }
    }

    public List<GameTestEntry> tests() {
        return List.copyOf(tests);
    }

    public int size() {
        return tests.size();
    }

    /** 汇总描述(供启动日志与 dry-run 使用) */
    public String summary() {
        long classes = tests.stream().map(GameTestEntry::className).distinct().count();
        return tests.size() + " test(s) from " + classes + " class(es)";
    }

    private static boolean containsAscii(byte[] haystack, String needle) {
        byte[] pattern = needle.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= haystack.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (haystack[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
