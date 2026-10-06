package top.mcocet.lighting.transform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import top.mcocet.api.Dist;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * DistCleaner——对应 NeoForge 的 {@code RuntimeDistCleaner}:
 * 类加载期剥离与当前运行侧不符的 {@code @DistOnly} 标注成员,
 * 让“仅客户端”代码在专用服务端(及反向)上无法被加载执行:
 * <ul>
 *   <li>类级标注不符:删除类的全部方法与字段,留下仅含合成标记字段的空壳
 *       (触碰该类的引用方会得到明确的加载期失败,而非隐性的半可用状态);</li>
 *   <li>方法级标注不符:方法体重写为抛出 {@link UnsupportedOperationException}
 *       (调用立即失败并给出定位信息);</li>
 * </ul>
 * 非标注类经字节级预过滤(常量池描述符扫描)零成本放行;字段级剥离不受支持
 * (与 NeoForge 语义一致)。转换在类加载期发生,重复转换是幂等的。
 */
public final class DistCleanerTransformer implements ClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger(DistCleanerTransformer.class);

    /** DistOnly 注解描述符(同时作为字节级预过滤锚点) */
    public static final String ANNOTATION_DESC = "Ltop/mcocet/api/DistOnly;";

    /** 注解值枚举的描述符 */
    private static final String DIST_DESC = "Ltop/mcocet/api/Dist;";

    /** 被剥离方法抛出消息的前缀(供 dry-run 与测试识别剥离痕迹) */
    public static final String STRIP_MESSAGE_PREFIX = "[Lighting DistCleaner] ";

    /** 类级剥离后遗留的合成标记字段名 */
    public static final String STRIPPED_FIELD = "__lighting$distStripped";

    private static final String UNSUPPORTED_OWNER = "java/lang/UnsupportedOperationException";

    private final Dist activeDist;

    public DistCleanerTransformer(Dist activeDist) {
        this.activeDist = activeDist;
    }

    /** 当前激活的运行侧 */
    public Dist activeDist() {
        return activeDist;
    }

    @Override
    public byte[] transform(String className, byte[] classBytes, ClassLoader classLoader) {
        if (!isDistAnnotated(classBytes)) {
            return classBytes;
        }
        try {
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(classBytes).accept(node, 0);

            if (needsStrip(node.visibleAnnotations)) {
                if (node.methods.isEmpty() && hasStrippedMarker(node)) {
                    return classBytes; // 幂等:该分支已剥离
                }
                int removed = node.methods.size();
                node.methods.clear();
                node.fields.clear();
                int markerAccess = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC
                        | ((node.access & Opcodes.ACC_INTERFACE) != 0
                                ? (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)
                                : Opcodes.ACC_PRIVATE);
                node.fields.add(new FieldNode(markerAccess, STRIPPED_FIELD, "Z", null, null));
                LOGGER.info("DistCleaner stripped class {} ({} method(s) removed; active dist: {})",
                        node.name, removed, activeDist);
                return write(node, classBytes, classLoader);
            }

            int stripped = 0;
            for (MethodNode method : node.methods) {
                if (needsStrip(method.visibleAnnotations)
                        && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                    stripMethodBody(node.name.replace('/', '.'), method);
                    stripped++;
                }
            }
            if (stripped == 0) {
                return classBytes;
            }
            LOGGER.info("DistCleaner stripped {} method body(ies) of {} (active dist: {})",
                    stripped, node.name, activeDist);
            return write(node, classBytes, classLoader);
        } catch (RuntimeException e) {
            LOGGER.error("DistCleaner failed for {}: {}", className, e.toString());
            return classBytes;
        }
    }

    private static byte[] write(ClassNode node, byte[] original, ClassLoader classLoader) {
        ClassWriter writer = new FrameSafeClassWriter(new ClassReader(original), classLoader);
        node.accept(writer);
        return writer.toByteArray();
    }

    /** 注解列表中存在 DistOnly 且其值与激活运行侧不符时为 true */
    private boolean needsStrip(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (!ANNOTATION_DESC.equals(annotation.desc)) {
                continue;
            }
            Dist value = distValue(annotation);
            return value != null && value != activeDist;
        }
        return false;
    }

    /** 读取 DistOnly 注解的 value() 枚举值(不加载注解类,纯字节解析) */
    private static Dist distValue(AnnotationNode annotation) {
        List<Object> values = annotation.values;
        if (values == null) {
            return null;
        }
        for (int i = 0; i + 1 < values.size(); i += 2) {
            Object value = values.get(i + 1);
            if ("value".equals(values.get(i)) && value instanceof String[] enumValue
                    && enumValue.length == 2 && DIST_DESC.equals(enumValue[0])) {
                try {
                    return Dist.valueOf(enumValue[1]);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 替换方法体:把原指令整体改写为抛出 UnsupportedOperationException 的存根 */
    private static void stripMethodBody(String owner, MethodNode method) {
        InsnList replacement = new InsnList();
        replacement.add(new TypeInsnNode(Opcodes.NEW, UNSUPPORTED_OWNER));
        replacement.add(new InsnNode(Opcodes.DUP));
        replacement.add(new LdcInsnNode(STRIP_MESSAGE_PREFIX + owner + "." + method.name
                + " is dist-only (disabled on this side)"));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, UNSUPPORTED_OWNER, "<init>",
                "(Ljava/lang/String;)V", false));
        replacement.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.clear();
        method.instructions.add(replacement);
        method.tryCatchBlocks = List.of();
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
    }

    private static boolean hasStrippedMarker(ClassNode node) {
        for (FieldNode field : node.fields) {
            if (STRIPPED_FIELD.equals(field.name)) {
                return true;
            }
        }
        return false;
    }

    /** 快速预过滤:类字节常量池中是否含 DistOnly 注解描述符(供 dry-run 与测试使用) */
    public static boolean isDistAnnotated(byte[] classBytes) {
        return containsAscii(classBytes, ANNOTATION_DESC);
    }

    /** 检查类字节是否含剥离痕迹(方法体替换消息或类级空壳标记;供 dry-run 与测试使用) */
    public static boolean isStripMarked(byte[] classBytes) {
        return containsAscii(classBytes, STRIP_MESSAGE_PREFIX)
                || containsAscii(classBytes, STRIPPED_FIELD);
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
