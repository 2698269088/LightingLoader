package top.mcocet.lighting.model;

import top.mcocet.lighting.loader.LoaderType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一元数据模型:三种加载器的清单(fabric.mod.json / mods.toml / neoforge.mods.toml)
 * 解析后都归一化为本对象,供加载器内核统一处理。
 */
public final class ModMetadata {

    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final LoaderType loaderType;
    private final String license;
    private final String environment;
    private final List<String> authors;
    private final List<String> mixinConfigs;
    private final Map<String, List<String>> entrypoints;
    private final List<ModDependency> dependencies;
    private final Map<String, String> extras;

    private ModMetadata(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.loaderType = builder.loaderType;
        this.license = builder.license;
        this.environment = builder.environment;
        this.authors = List.copyOf(builder.authors);
        this.mixinConfigs = List.copyOf(builder.mixinConfigs);
        Map<String, List<String>> eps = new LinkedHashMap<>();
        builder.entrypoints.forEach((k, v) -> eps.put(k, List.copyOf(v)));
        this.entrypoints = Collections.unmodifiableMap(eps);
        this.dependencies = List.copyOf(builder.dependencies);
        this.extras = Collections.unmodifiableMap(new LinkedHashMap<>(builder.extras));
    }

    public String id() {
        return id;
    }

    /** 展示名;清单未提供时回退为 Mod ID */
    public String name() {
        return name != null ? name : id;
    }

    public String version() {
        return version;
    }

    public String description() {
        return description != null ? description : "";
    }

    public LoaderType loaderType() {
        return loaderType;
    }

    public String license() {
        return license != null ? license : "";
    }

    /** Fabric 语义的 environment: "*" / "client" / "server";Forge 系默认 "*" */
    public String environment() {
        return environment != null ? environment : "*";
    }

    public List<String> authors() {
        return authors;
    }

    public List<String> mixinConfigs() {
        return mixinConfigs;
    }

    /** Fabric 风格入口点表:类型(main/client/server/...) -> 入口点声明列表 */
    public Map<String, List<String>> entrypoints() {
        return entrypoints;
    }

    public List<ModDependency> dependencies() {
        return dependencies;
    }

    /** 加载器特有字段(如 Forge 系的 modLoader、logoFile) */
    public Map<String, String> extras() {
        return extras;
    }

    @Override
    public String toString() {
        return "%s[%s v%s, %s]".formatted(id, name(), version, loaderType);
    }

    public static Builder builder(String id, LoaderType loaderType) {
        return new Builder(id, loaderType);
    }

    /** 元数据构造器 */
    public static final class Builder {
        private final String id;
        private final LoaderType loaderType;
        private String name;
        private String version = "0.0.0";
        private String description;
        private String license;
        private String environment;
        private final List<String> authors = new ArrayList<>();
        private final List<String> mixinConfigs = new ArrayList<>();
        private final Map<String, List<String>> entrypoints = new LinkedHashMap<>();
        private final List<ModDependency> dependencies = new ArrayList<>();
        private final Map<String, String> extras = new LinkedHashMap<>();

        private Builder(String id, LoaderType loaderType) {
            this.id = id;
            this.loaderType = loaderType;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            if (version != null && !version.isBlank()) {
                this.version = version;
            }
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder license(String license) {
            this.license = license;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder addAuthor(String author) {
            if (author != null && !author.isBlank()) {
                this.authors.add(author);
            }
            return this;
        }

        public Builder addMixinConfig(String config) {
            if (config != null && !config.isBlank()) {
                this.mixinConfigs.add(config);
            }
            return this;
        }

        public Builder addEntrypoint(String type, String value) {
            this.entrypoints.computeIfAbsent(type, k -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder addDependency(ModDependency dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        public Builder putExtra(String key, String value) {
            if (value != null) {
                this.extras.put(key, value);
            }
            return this;
        }

        public ModMetadata build() {
            return new ModMetadata(this);
        }
    }
}
