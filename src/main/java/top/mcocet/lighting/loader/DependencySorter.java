package top.mcocet.lighting.loader;

import top.mcocet.lighting.discovery.ModCandidate;
import top.mcocet.lighting.model.ModDependency;
import top.mcocet.lighting.model.OrderingType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 依赖排序器:基于强制依赖(DEPENDS)与排序声明(BEFORE/AFTER)
 * 对候选 Mod 做拓扑排序(Kahn 算法)。
 * <p>
 * 规则:
 * <ul>
 *   <li>A 强制依赖 B 且 B 在集合中 → B 先于 A</li>
 *   <li>A 对 B 声明 ordering=AFTER → B 先于 A(与依赖同向)</li>
 *   <li>A 对 B 声明 ordering=BEFORE → A 先于 B</li>
 * </ul>
 * 指向集合外 Mod(minecraft、neoforge 等平台依赖)的边被忽略;
 * 存在环时保留原顺序追加剩余项并记入警告,保证加载永不因排序失败而中断。
 */
public final class DependencySorter {

    private DependencySorter() {
    }

    public static List<ModCandidate> sort(List<ModCandidate> candidates, List<String> warnings) {
        Map<String, ModCandidate> byId = new LinkedHashMap<>();
        for (ModCandidate candidate : candidates) {
            byId.put(candidate.metadata().id(), candidate);
        }

        // 邻接表:edge(from -> to) 表示 from 必须先于 to 加载
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        byId.keySet().forEach(id -> edges.put(id, new LinkedHashSet<>()));
        for (ModCandidate candidate : candidates) {
            String self = candidate.metadata().id();
            for (ModDependency dependency : candidate.metadata().dependencies()) {
                if (!byId.containsKey(dependency.modId())) {
                    continue;
                }
                if (dependency.kind() == top.mcocet.lighting.model.DependencyKind.DEPENDS
                        || dependency.ordering() == OrderingType.AFTER) {
                    edges.get(dependency.modId()).add(self);
                }
                if (dependency.ordering() == OrderingType.BEFORE) {
                    edges.get(self).add(dependency.modId());
                }
            }
        }

        // Kahn 拓扑排序
        Map<String, Integer> inDegree = new HashMap<>();
        byId.keySet().forEach(id -> inDegree.put(id, 0));
        edges.values().forEach(targets -> targets.forEach(target -> inDegree.merge(target, 1, Integer::sum)));

        Deque<String> ready = new ArrayDeque<>();
        inDegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });

        List<ModCandidate> ordered = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            visited.add(id);
            ordered.add(byId.get(id));
            for (String target : edges.get(id)) {
                if (inDegree.merge(target, -1, Integer::sum) == 0) {
                    ready.add(target);
                }
            }
        }

        if (visited.size() < byId.size()) {
            List<String> cycled = byId.keySet().stream()
                    .filter(id -> !visited.contains(id))
                    .toList();
            warnings.add("Circular dependency detected; appending remaining mods in original order: " + String.join(", ", cycled));
            cycled.forEach(id -> ordered.add(byId.get(id)));
        }
        return ordered;
    }
}
