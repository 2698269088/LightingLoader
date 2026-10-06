package top.mcocet.lighting.loading;

import java.util.ArrayList;
import java.util.List;

/**
 * 版本范围表达式——移植 FML 依赖校验使用的 Maven 区间语义,并兼容 Fabric 风格运算符:
 * <ul>
 *   <li>Maven 区间:{@code [1.0,2.0)}、{@code [1.0,)}、{@code (,1.0]}、{@code [1.0]}(精确)</li>
 *   <li>比较运算符:{@code >=1.0}、{@code >1.0}、{@code <=2.0}、{@code <2.0}、{@code =1.0}</li>
 *   <li>多条件串联(空格或逗号):{@code >=1.0 <2.0}</li>
 *   <li>纯版本号表示精确匹配;空串、{@code *} 表示不限制</li>
 * </ul>
 * 版本比较按点分段数值比较(缺失段视为 0),如 {@code 26.1 < 26.1.2};
 * 含字母的段(预发布标签)小于纯数字段,如 {@code 1.0-rc1 < 1.0.0}。
 * 仅用于加载期依赖校验,追求够用与可预测。
 */
public final class VersionRange {

    private final List<Bound> bounds;
    private final String source;

    private VersionRange(List<Bound> bounds, String source) {
        this.bounds = bounds;
        this.source = source;
    }

    /** 解析版本范围表达式;表达式非法时抛 {@link IllegalArgumentException} */
    public static VersionRange parse(String expression) {
        String trimmed = expression == null ? "" : expression.trim();
        List<Bound> bounds = new ArrayList<>();
        if (trimmed.isEmpty() || trimmed.equals("*")) {
            return new VersionRange(List.of(), trimmed);
        }
        if (trimmed.startsWith("[") || trimmed.startsWith("(")) {
            parseMavenRange(trimmed, bounds);
        } else if (trimmed.indexOf(' ') >= 0 || trimmed.indexOf(',') >= 0) {
            for (String part : trimmed.split("[\\s,]+")) {
                parseComparison(part, bounds);
            }
        } else {
            parseComparison(trimmed, bounds);
        }
        return new VersionRange(List.copyOf(bounds), trimmed);
    }

    /** 判断给定版本是否落在范围内 */
    public boolean contains(String version) {
        if (bounds.isEmpty()) {
            return true;
        }
        if (version == null || version.isBlank()) {
            return false;
        }
        for (Bound bound : bounds) {
            if (!bound.test(compareVersions(version, bound.version()))) {
                return false;
            }
        }
        return true;
    }

    public String source() {
        return source;
    }

    /** 分段比较两个版本号:数值段按整数比较,缺失段视为 0,字母段小于数字段 */
    public static int compareVersions(String left, String right) {
        String[] leftParts = left.trim().split("[.\\-_+]");
        String[] rightParts = right.trim().split("[.\\-_+]");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            String leftSegment = i < leftParts.length ? leftParts[i] : "0";
            String rightSegment = i < rightParts.length ? rightParts[i] : "0";
            int comparison = compareSegment(leftSegment, rightSegment);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static void parseMavenRange(String expression, List<Bound> bounds) {
        char first = expression.charAt(0);
        char last = expression.charAt(expression.length() - 1);
        if (first != '[' && first != '(') {
            throw new IllegalArgumentException("Version range must start with '[' or '(': " + expression);
        }
        if (last != ']' && last != ')') {
            throw new IllegalArgumentException("Version range must end with ']' or ')': " + expression);
        }
        String body = expression.substring(1, expression.length() - 1);
        if (!body.contains(",")) {
            String version = body.trim();
            if (!version.isEmpty()) {
                bounds.add(new Bound("=", version));
            }
            return;
        }
        String[] parts = body.split(",", -1);
        String lower = parts[0].trim();
        String upper = parts.length > 1 ? parts[1].trim() : "";
        if (!lower.isEmpty()) {
            bounds.add(new Bound(first == '[' ? ">=" : ">", lower));
        }
        if (!upper.isEmpty()) {
            bounds.add(new Bound(last == ']' ? "<=" : "<", upper));
        }
    }

    private static void parseComparison(String part, List<Bound> bounds) {
        String text = part.trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.startsWith(">=")) {
            bounds.add(new Bound(">=", text.substring(2).trim()));
        } else if (text.startsWith("<=")) {
            bounds.add(new Bound("<=", text.substring(2).trim()));
        } else if (text.startsWith(">")) {
            bounds.add(new Bound(">", text.substring(1).trim()));
        } else if (text.startsWith("<")) {
            bounds.add(new Bound("<", text.substring(1).trim()));
        } else if (text.startsWith("=")) {
            bounds.add(new Bound("=", text.substring(1).trim()));
        } else if (text.contains("+")) {
            // Fabric 风格 "1.0+" 语义等同 ">=1.0"
            bounds.add(new Bound(">=", text.substring(0, text.indexOf('+')).trim()));
        } else {
            bounds.add(new Bound("=", text));
        }
    }

    private static int compareSegment(String left, String right) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);
        if (leftNumeric && rightNumeric) {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        }
        if (leftNumeric == rightNumeric) {
            return left.compareTo(right);
        }
        // 字母段(预发布标签)小于数字段:1.0-rc1 < 1.0.0
        return leftNumeric ? 1 : -1;
    }

    private static boolean isNumeric(String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private record Bound(String operator, String version) {

        boolean test(int comparison) {
            return switch (operator) {
                case ">=" -> comparison >= 0;
                case ">" -> comparison > 0;
                case "<=" -> comparison <= 0;
                case "<" -> comparison < 0;
                case "=" -> comparison == 0;
                default -> throw new IllegalArgumentException("Unknown operator: " + operator);
            };
        }
    }
}
