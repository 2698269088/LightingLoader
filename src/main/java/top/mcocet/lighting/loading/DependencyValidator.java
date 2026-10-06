package top.mcocet.lighting.loading;

import top.mcocet.lighting.discovery.ModCandidate;
import top.mcocet.lighting.model.DependencyKind;
import top.mcocet.lighting.model.ModDependency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依赖校验器——对应 FML ModSorter 的依赖校验职责:
 * <ul>
 *   <li>强制依赖(DEPENDS)目标缺失 → 警告(平台/生态依赖允许在发行阶段随 libraries 组装补足)</li>
 *   <li>强制依赖目标已加载但版本范围不满足 → 致命问题(对应 ModLoadingException 聚合上报)</li>
 *   <li>CONFLICTS / BREAKS 声明命中已加载目标 → 致命问题</li>
 *   <li>非法版本范围表达式 → 警告(不阻断,校验跳过)</li>
 * </ul>
 */
public final class DependencyValidator {

    private DependencyValidator() {
    }

    /** 校验全部候选 Mod 的依赖声明,返回归集的问题条目 */
    public static List<ModLoadingIssue> validate(List<ModCandidate> candidates) {
        Map<String, ModCandidate> byId = new LinkedHashMap<>();
        for (ModCandidate candidate : candidates) {
            byId.put(candidate.metadata().id(), candidate);
        }

        List<ModLoadingIssue> issues = new ArrayList<>();
        for (ModCandidate candidate : candidates) {
            String selfId = candidate.metadata().id();
            for (ModDependency dependency : candidate.metadata().dependencies()) {
                validateDependency(selfId, dependency, byId, issues);
            }
        }
        return issues;
    }

    private static void validateDependency(String selfId, ModDependency dependency,
                                           Map<String, ModCandidate> byId, List<ModLoadingIssue> issues) {
        ModCandidate target = byId.get(dependency.modId());
        DependencyKind kind = dependency.kind();

        if (kind == DependencyKind.CONFLICTS || kind == DependencyKind.BREAKS) {
            if (target != null && matches(dependency.versionRange(), target.metadata().version())) {
                issues.add(ModLoadingIssue.error(
                        "Mod %s declares %s with %s, but it is loaded (%s)"
                                .formatted(selfId, kind, dependency.modId(), target.metadata().version())));
            }
            return;
        }

        if (!dependency.mandatory()) {
            return;
        }

        if (target == null) {
            issues.add(ModLoadingIssue.warning(
                    "Mod %s requires %s [%s], but it is not present (assemble it into libraries or add the mod)"
                            .formatted(selfId, dependency.modId(), displayRange(dependency.versionRange()))));
            return;
        }

        String range = dependency.versionRange();
        if (range == null || range.isBlank()) {
            return;
        }
        VersionRange parsed;
        try {
            parsed = VersionRange.parse(range);
        } catch (IllegalArgumentException e) {
            issues.add(ModLoadingIssue.warning(
                    "Mod %s declares an invalid version range '%s' for %s; the check was skipped"
                            .formatted(selfId, range, dependency.modId())));
            return;
        }
        String targetVersion = target.metadata().version();
        if (!parsed.contains(targetVersion)) {
            issues.add(ModLoadingIssue.error(
                    "Mod %s requires %s %s, but version %s was found (version range check failed)"
                            .formatted(selfId, dependency.modId(), range, targetVersion)));
        }
    }

    private static boolean matches(String range, String version) {
        if (range == null || range.isBlank()) {
            return true;
        }
        try {
            return VersionRange.parse(range).contains(version);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String displayRange(String range) {
        return range == null || range.isBlank() ? "*" : range;
    }
}
