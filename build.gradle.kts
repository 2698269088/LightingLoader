plugins {
    id("java")
}

group = "top.mcocet"

java {
    // Minecraft 26.1.2 需要 Java 25(本机 JAVA_HOME 为 GraalVM JDK 25)
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "top.mcocet.lighting.LightingBootstrap"
        // 供运行时读取加载器版本(LightingBootstrap#loaderVersion)
        attributes["Implementation-Version"] = provider { project.version.toString() }
    }
}

// 发布用 fat jar:并入 gson/tomlj 等运行时依赖,使加载器可独立启动(不含 Minecraft 本体)
val fatJar by tasks.registering(Jar::class) {
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "top.mcocet.lighting.LightingBootstrap"
        attributes["Implementation-Version"] = provider { project.version.toString() }
    }
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    }
}

tasks.assemble {
    dependsOn(fatJar)
}
version = "1.0-SNAPSHOT"

repositories {
    // 国内网络环境下直连 Maven Central 易超时,优先走阿里云镜像
    maven(url = "https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    // 元数据解析:fabric.mod.json(JSON)与 mods.toml(TOML)
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.tomlj:tomlj:1.1.1")

    // 日志:log4j2——Minecraft 生态的标准日志实现,与游戏本体日志体系一致
    implementation("org.apache.logging.log4j:log4j-api:2.26.1")
    implementation("org.apache.logging.log4j:log4j-core:2.26.1")

    // ASM:类加载期的字节码转换引擎(启动接管与 Mixin 注入的基础)
    implementation(platform("org.ow2.asm:asm-bom:9.8"))
    implementation("org.ow2.asm:asm")
    implementation("org.ow2.asm:asm-tree")
    implementation("org.ow2.asm:asm-commons")

    // Minecraft 26.1.2 本体:仅编译期引用,不随加载器分发(遵循 Mojang 版权约束)
    compileOnly(files("lib/Minecraft-26.1.2.jar"))
    // 编译期:Minecraft 依赖的独立库(仅供编译,运行时由启动器 libraries 提供,不分发)
    compileOnly(files("lib/brigadier-1.0.500.jar"))

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // 测试中验证转换后字节码的合法性
    testImplementation("org.ow2.asm:asm-util")
    // Gradle 9 起需显式声明 JUnit Platform Launcher
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}