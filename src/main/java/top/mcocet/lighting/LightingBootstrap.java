package top.mcocet.lighting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import top.mcocet.api.Dist;
import top.mcocet.api.LightingLoader;
import top.mcocet.lighting.api.KernelLightingRuntime;
import top.mcocet.lighting.coremod.CoreModLoader;
import top.mcocet.lighting.datagen.DataGenerator;
import top.mcocet.lighting.discovery.JarInJarScanner;
import top.mcocet.lighting.discovery.ModCandidate;
import top.mcocet.lighting.event.GameMainEnterEvent;
import top.mcocet.lighting.event.GatherDataEvent;
import top.mcocet.lighting.event.LoadCompleteEvent;
import top.mcocet.lighting.event.ModLoadedEvent;
import top.mcocet.lighting.gametest.GameTestRegistry;
import top.mcocet.lighting.handoff.GameSideBridge;
import top.mcocet.lighting.installer.VersionManifestGenerator;
import top.mcocet.lighting.launch.EcosystemAssembly;
import top.mcocet.lighting.launch.GameClassLoader;
import top.mcocet.lighting.launch.GameEnvironment;
import top.mcocet.lighting.launch.GameLauncher;
import top.mcocet.lighting.launch.LaunchArguments;
import top.mcocet.lighting.loader.LoadResult;
import top.mcocet.lighting.loader.LoaderConfig;
import top.mcocet.lighting.loader.LoaderType;
import top.mcocet.lighting.loader.ModContainer;
import top.mcocet.lighting.loader.ModLoader;
import top.mcocet.lighting.loading.FatalErrorReporter;
import top.mcocet.lighting.loading.LoadingProgress;
import top.mcocet.lighting.loading.ModLoadingException;
import top.mcocet.lighting.loading.ModLoadingIssue;
import top.mcocet.lighting.loading.ModLoadingPipeline;
import top.mcocet.lighting.loading.VersionChecker;
import top.mcocet.lighting.mixin.MixinModule;
import top.mcocet.lighting.mixin.MixinTransformer;
import top.mcocet.lighting.registry.RegistryManager;
import top.mcocet.lighting.transform.AttachmentHookTransformer;
import top.mcocet.lighting.transform.DistCleanerTransformer;
import top.mcocet.lighting.transform.LightingHooks;
import top.mcocet.lighting.transform.MainClassHookTransformer;
import top.mcocet.lighting.transform.PackSourceInjectTransformer;
import top.mcocet.lighting.transform.PayloadCodecTransformer;
import top.mcocet.lighting.transform.RuntimeEventHookTransformer;
import top.mcocet.lighting.transform.TitleScreenMenuTransformer;
import top.mcocet.lighting.transform.TransformerChain;
import top.mcocet.lighting.transform.VanillaRegistryHookTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lighting Loader 启动入口——M1 启动接管的执行序:
 * <ol>
 *   <li>Mod 发现与解析(三生态清单识别、依赖拓扑排序、版本范围校验)</li>
 *   <li>组装游戏侧类路径(本体 jar + 依赖库 + Mod jar)</li>
 *   <li>建立 ASM 转换管线与 {@link GameClassLoader}(类加载期改写游戏字节码)</li>
 *   <li>安装统一 API 门面(top.mcocet.api),并行构造 Mod 入口,注册表初始化并冻结
 *       (对应 NeoForge {@code gatherAndInitializeMods} 阶段序)</li>
 *   <li>发布收尾阶段事件(公共设置 → 客户端设置 → IMC → 加载完成,可取消并中止移交)</li>
 *   <li>dry-run 验证注入效果,或 {@code --launch} 反射调用游戏主类移交启动</li>
 * </ol>
 * 在真实发布形态中,启动器的版本清单会把 mainClass 指向本入口,
 * 因此玩家启动游戏时首先运行的就是这里。
 */
public final class LightingBootstrap {

    private static final Logger LOGGER = LogManager.getLogger(LightingBootstrap.class);

    /** 启动注入钩子是否已被执行(游戏主类进入) */
    private static volatile boolean gameMainEntered;

    private LightingBootstrap() {
    }

    public static void main(String[] args) throws IOException {
        // log4j2 关闭钩子:确保 JVM 退出前刷盘全部日志(对应 FML begin() 中的 LogManager::shutdown)
        Runtime.getRuntime().addShutdownHook(new Thread(LogManager::shutdown, "LightingLogShutdown"));

        LaunchArguments arguments = LaunchArguments.parse(args);
        LoaderConfig config = new LoaderConfig(arguments.gameDir(), arguments.modsDir());
        Dist dist = arguments.dist();

        LOGGER.info("Lighting Mod Loader Loading...");
        LOGGER.info("Game directory: {}", config.gameDir());
        LOGGER.info("Mods directory: {}", config.modsDir());
        LOGGER.info("Dist: {}", dist);

        // 1. Mod 发现(对应 FMLLoader 的早期扫描:先取得候选 Mod 以组装类路径)
        ModLoader loader = new ModLoader(config);
        loader.eventBus().register(ModLoadedEvent.class, event ->
                LOGGER.info("Loaded: {}", event.container()));
        loader.eventBus().register(LoadCompleteEvent.class, event ->
                LOGGER.info("Mod loading complete, took {} ms", event.elapsedMillis()));
        loader.eventBus().register(GameMainEnterEvent.class, event -> {
            gameMainEntered = true;
            LOGGER.info("Game main class entered (injected hook is active)");
        });
        ModLoadingPipeline pipeline = new ModLoadingPipeline(loader, LoadingProgress.console());
        List<ModCandidate> candidates = pipeline.discover();
        LightingHooks.bindEventBus(loader.eventBus());
        LightingHooks.bindNetworkRegistry(loader.networkManager());
        LightingHooks.bindAttachmentManager(loader.attachmentManager());
        LightingHooks.bindModResources(pipeline.resources());

        // 1b. Coremod 与 Mixin 接入层装配(建类加载器之前;对应 FML 早期 coremod/mixin 环节)
        CoreModLoader.Result coreMods = CoreModLoader.load(candidates, LightingBootstrap.class.getClassLoader());
        MixinModule.Result mixins = MixinModule.load(candidates);
        mixins.warnings().forEach(warning -> loader.addIssue(ModLoadingIssue.warning(warning)));

        // 1c. GameTest 扫描(收集 Mod jar 中的测试登记;执行端由游戏侧测试桥接入)
        GameTestRegistry gameTests = GameTestRegistry.scan(candidates);
        LOGGER.info("GameTests discovered: {}", gameTests.summary());

        // 2. 组装游戏侧运行环境(嵌套 jar 提取并入类路径,对应 JarJar/jar-in-jar 语义)
        List<Path> libraries = scanJars(arguments.librariesDir());
        // 生态 API 库(NeoForge universal/FML loader/bus 等组件)并入同一类加载空间,使上游 API 面可解析
        List<Path> ecosystem = EcosystemAssembly.resolve(config.gameDir(), selfClasspathRoot());
        List<Path> classpathLibraries = ecosystem.isEmpty()
                ? libraries
                : Stream.concat(libraries.stream(), ecosystem.stream()).toList();
        if (!ecosystem.isEmpty()) {
            LOGGER.info("Ecosystem API assembly: {} jar(s) [{}]",
                    ecosystem.size(), EcosystemAssembly.summary(ecosystem));
        }
        List<Path> modJars = candidates.stream().map(ModCandidate::jarPath).toList();
        List<Path> nestedJars = JarInJarScanner.extractAll(modJars,
                config.gameDir().resolve(".lighting").resolve("jij"));
        List<Path> classpathModJars = nestedJars.isEmpty()
                ? modJars
                : Stream.concat(modJars.stream(), nestedJars.stream()).toList();
        GameEnvironment environment = new GameEnvironment(
                config.gameDir(), arguments.gameJar(), selfClasspathRoot(), classpathLibraries, classpathModJars);
        LOGGER.info("Classpath assembled: gameJar={}, libraries={}, mods={} (+{} nested)",
                Files.isRegularFile(environment.gameJar()) ? environment.gameJar()
                        : "missing (specify with --gameJar=)",
                libraries.size(), modJars.size(), nestedJars.size());
        String versionWarning = environment.javaVersionWarning();
        if (versionWarning != null) {
            LOGGER.warn(versionWarning);
        }
        String gameVersion = environment.gameVersion();
        if (gameVersion == null) {
            gameVersion = "unknown";
        }
        final String resolvedGameVersion = gameVersion;
        final String loaderVersion = loaderVersion();

        // 2b. 版本清单生成(对应 NeoForge 安装器的版本 JSON 产出)
        try {
            Path manifest = VersionManifestGenerator.write(environment, config, loaderVersion, candidates.size());
            LOGGER.info("Version manifest written: {}", manifest);
        } catch (IOException e) {
            LOGGER.warn("Failed to write version manifest: {}", e.toString());
        }

        // 3. 转换管线 + 游戏类加载器(Mod 构造的前置条件)
        //    管线顺序:启动注入 → 菜单注入 → Mixin 接入位点 → 注册表接管 → 运行时事件锚点 → 网络载荷编解码 → 附件持久化 → 资源包挂载 → Dist 剥离 → coremod 提供的转换器
        TransformerChain chain = new TransformerChain()
                .add(new MainClassHookTransformer())
                .add(new TitleScreenMenuTransformer())
                .add(new MixinTransformer(mixins.registry()))
                .add(new VanillaRegistryHookTransformer())
                .add(new RuntimeEventHookTransformer())
                .add(new PayloadCodecTransformer())
                .add(new AttachmentHookTransformer())
                .add(new PackSourceInjectTransformer())
                .add(new DistCleanerTransformer(dist));
        coreMods.transformers().forEach(chain::add);
        GameClassLoader gameClassLoader = new GameClassLoader(
                environment.classpathUrls(), LightingBootstrap.class.getClassLoader(), chain);

        // 4. Mod 解析 → 构造 → 注册表初始化(致命问题一次性聚合上报并中止)
        LoadResult result;
        try {
            result = pipeline.initialize(candidates, gameClassLoader, mods -> {
                LightingLoader.install(new KernelLightingRuntime(
                        "Lighting", loaderVersion, resolvedGameVersion, dist,
                        loader.eventBus(), loader.registryManager(),
                        loader.networkManager(), loader.capabilityManager(),
                        loader.attachmentManager(), loader.configTracker(), mods));
                LOGGER.info("Lighting API installed: {} {} (game {}), {} mod(s) visible",
                        "Lighting", loaderVersion, resolvedGameVersion, mods.size());
            });
        } catch (ModLoadingException e) {
            LOGGER.error("========== Fatal mod loading issues ==========");
            e.issues().forEach(issue -> LOGGER.error("  - {}", issue.message()));
            LOGGER.error("Mod loading failed with {} fatal issue(s); the game will not start",
                    e.issues().size());
            try {
                Path report = FatalErrorReporter.write(config, loaderVersion, resolvedGameVersion, e.issues());
                LOGGER.error("Fatal report written to: {}", report);
            } catch (IOException reportError) {
                LOGGER.warn("Failed to write fatal report: {}", reportError.toString());
            }
            return;
        }
        if (dist == Dist.CLIENT) {
            applyModMenuData(gameClassLoader, result);
        } else {
            LOGGER.info("Skipped mod menu data on the dedicated server side");
        }
        printReport(result);

        // 4b. 游戏侧生命周期接管层绑定(注册表冻结前注入方块/实体;NBT/位置桥就绪自检)
        bindGameSideLifecycle(gameClassLoader, loader.registryManager());

        // 5. 收尾阶段:公共设置 → 分侧设置 → 注册事件 → IMC → 加载完成(可取消,取消则中止移交)
        if (!pipeline.finish(result.mods(), dist)) {
            LOGGER.warn("Launch aborted by a cancelled startup event; the game will not start");
            return;
        }

        // 5b. 数据生成(--datagen:收集 provider 并落盘后退出,对应 NeoForge 的 runData 任务)
        if (arguments.datagen()) {
            runDataGeneration(loader, result, config);
            LOGGER.info("Datagen mode: the game will not start");
            return;
        }

        // 5c. Mod 更新检查(异步守护线程,不阻塞启动)
        VersionChecker.checkAsync(config.gameDir(), result.mods());

        // 6. dry-run 验证(默认)或真实启动移交
        if (arguments.launch()) {
            LOGGER.info("Handing off to the game main class: {}", arguments.mainClass());
            try {
                new GameLauncher(gameClassLoader, arguments.mainClass())
                        .launch(arguments.gameArgs().toArray(String[]::new));
            } catch (Exception | LinkageError e) {
                LOGGER.warn("Game main flow ended early: {}", e.toString());
                LOGGER.warn("Handoff verified (injected hook fired: {}); running the full game "
                        + "requires a complete assets/natives/libraries environment", gameMainEntered);
            }
        } else {
            dryRun(environment, chain, gameClassLoader, arguments.mainClass(), candidates, dist);
        }
    }

    /** 不启动游戏,仅把主类转换结果落盘并校验,用于验证接管链路 */
    private static void dryRun(GameEnvironment environment, TransformerChain chain,
                               GameClassLoader gameClassLoader, String mainClass,
                               List<ModCandidate> candidates, Dist dist) throws IOException {
        LOGGER.info("========== Launch takeover dry-run ==========");
        byte[] raw = environment.readGameClass(mainClass);
        if (raw == null) {
            LOGGER.warn("Skipped: {} not found in the game jar (gameJar={})",
                    mainClass, environment.gameJar());
            return;
        }
        LOGGER.info("Main class: {}", mainClass);
        LOGGER.info("Original bytecode: {} bytes, hook injected: {}",
                raw.length, MainClassHookTransformer.isHookInjected(raw));

        byte[] transformed = chain.transform(mainClass, raw, gameClassLoader);
        LOGGER.info("Transformed bytecode: {} bytes, hook injected: {}",
                transformed.length, MainClassHookTransformer.isHookInjected(transformed));

        Path output = environment.gameDir().resolve("transformed").resolve("Main.class");
        Files.createDirectories(output.getParent());
        Files.write(output, transformed);
        LOGGER.info("Transformed result exported to: {}", output);

        try {
            Class<?> loaded = Class.forName(mainClass, false, gameClassLoader);
            LOGGER.info("Verification load via GameClassLoader passed: {} (loader: {})",
                    loaded.getName(), loaded.getClassLoader().getName());
        } catch (Throwable t) {
            LOGGER.warn("Verification load skipped ({}: {})", t.getClass().getSimpleName(), t.getMessage());
        }

        byte[] titleRaw = environment.readGameClass(TitleScreenMenuTransformer.TARGET_CLASS);
        if (titleRaw != null) {
            byte[] titleTransformed = chain.transform(
                    TitleScreenMenuTransformer.TARGET_CLASS, titleRaw, gameClassLoader);
            LOGGER.info("Main menu entry: {} {} -> {} bytes, menu hook injected: {}",
                    TitleScreenMenuTransformer.TARGET_CLASS, titleRaw.length, titleTransformed.length,
                    TitleScreenMenuTransformer.isMenuHookInjected(titleTransformed));
            Path titleOutput = environment.gameDir().resolve("transformed").resolve("TitleScreen.class");
            Files.write(titleOutput, titleTransformed);
            LOGGER.info("Transformed result exported to: {}", titleOutput);
        }

        byte[] registryRaw = environment.readGameClass(VanillaRegistryHookTransformer.TARGET_CLASS);
        if (registryRaw != null) {
            byte[] registryTransformed = chain.transform(
                    VanillaRegistryHookTransformer.TARGET_CLASS, registryRaw, gameClassLoader);
            LOGGER.info("Vanilla registry hooks: {} {} -> {} bytes, bootstrap hook injected: {}, ready hook injected: {}",
                    VanillaRegistryHookTransformer.TARGET_CLASS, registryRaw.length, registryTransformed.length,
                    VanillaRegistryHookTransformer.isBootstrapHookInjected(registryTransformed),
                    VanillaRegistryHookTransformer.isReadyHookInjected(registryTransformed));
            Path registryOutput = environment.gameDir().resolve("transformed").resolve("BuiltInRegistries.class");
            Files.write(registryOutput, registryTransformed);
            LOGGER.info("Transformed result exported to: {}", registryOutput);
        }

        byte[] runtimeServerRaw = environment.readGameClass(RuntimeEventHookTransformer.SERVER_CLASS);
        if (runtimeServerRaw != null) {
            byte[] runtimeServerTransformed = chain.transform(
                    RuntimeEventHookTransformer.SERVER_CLASS, runtimeServerRaw, gameClassLoader);
            LOGGER.info("Runtime event hooks (server): {} {} -> {} bytes, tick hook injected: {}",
                    RuntimeEventHookTransformer.SERVER_CLASS, runtimeServerRaw.length,
                    runtimeServerTransformed.length,
                    RuntimeEventHookTransformer.isServerTickHookInjected(runtimeServerTransformed));
            Path runtimeServerOutput = environment.gameDir().resolve("transformed").resolve("MinecraftServer.class");
            Files.write(runtimeServerOutput, runtimeServerTransformed);
            LOGGER.info("Transformed result exported to: {}", runtimeServerOutput);
        }

        byte[] runtimePlayerRaw = environment.readGameClass(RuntimeEventHookTransformer.PLAYER_CLASS);
        if (runtimePlayerRaw != null) {
            byte[] runtimePlayerTransformed = chain.transform(
                    RuntimeEventHookTransformer.PLAYER_CLASS, runtimePlayerRaw, gameClassLoader);
            LOGGER.info("Runtime event hooks (player): {} {} -> {} bytes, tick={}, damage={}",
                    RuntimeEventHookTransformer.PLAYER_CLASS, runtimePlayerRaw.length,
                    runtimePlayerTransformed.length,
                    RuntimeEventHookTransformer.isPlayerTickHookInjected(runtimePlayerTransformed),
                    RuntimeEventHookTransformer.isDamageHookInjected(runtimePlayerTransformed));
            Path runtimePlayerOutput = environment.gameDir().resolve("transformed").resolve("ServerPlayer.class");
            Files.write(runtimePlayerOutput, runtimePlayerTransformed);
            LOGGER.info("Transformed result exported to: {}", runtimePlayerOutput);
        }

        byte[] interactionRaw = environment.readGameClass(RuntimeEventHookTransformer.GAME_MODE_CLASS);
        if (interactionRaw != null) {
            byte[] interactionTransformed = chain.transform(
                    RuntimeEventHookTransformer.GAME_MODE_CLASS, interactionRaw, gameClassLoader);
            LOGGER.info("Runtime event hooks (interaction): {} {} -> {} bytes, block break veto: {}",
                    RuntimeEventHookTransformer.GAME_MODE_CLASS, interactionRaw.length,
                    interactionTransformed.length,
                    RuntimeEventHookTransformer.isBlockBreakHookInjected(interactionTransformed));
            Path interactionOutput = environment.gameDir()
                    .resolve("transformed").resolve("ServerPlayerGameMode.class");
            Files.write(interactionOutput, interactionTransformed);
            LOGGER.info("Transformed result exported to: {}", interactionOutput);
        }

        byte[] payloadPacketRaw = environment.readGameClass(PayloadCodecTransformer.SERVERBOUND_PACKET_CLASS);
        if (payloadPacketRaw != null) {
            byte[] payloadPacketTransformed = chain.transform(
                    PayloadCodecTransformer.SERVERBOUND_PACKET_CLASS, payloadPacketRaw, gameClassLoader);
            LOGGER.info("Payload codec hooks (serverbound): {} {} -> {} bytes, codec list hook: {}",
                    PayloadCodecTransformer.SERVERBOUND_PACKET_CLASS, payloadPacketRaw.length,
                    payloadPacketTransformed.length,
                    PayloadCodecTransformer.isPayloadCodecInjected(payloadPacketTransformed));
            Path payloadPacketOutput = environment.gameDir()
                    .resolve("transformed").resolve("ServerboundCustomPayloadPacket.class");
            Files.write(payloadPacketOutput, payloadPacketTransformed);
            LOGGER.info("Transformed result exported to: {}", payloadPacketOutput);
        }

        byte[] payloadListenerRaw = environment.readGameClass(PayloadCodecTransformer.SERVER_LISTENER_CLASS);
        if (payloadListenerRaw != null) {
            byte[] payloadListenerTransformed = chain.transform(
                    PayloadCodecTransformer.SERVER_LISTENER_CLASS, payloadListenerRaw, gameClassLoader);
            LOGGER.info("Payload codec hooks (listener): {} {} -> {} bytes, receive hook: {}",
                    PayloadCodecTransformer.SERVER_LISTENER_CLASS, payloadListenerRaw.length,
                    payloadListenerTransformed.length,
                    PayloadCodecTransformer.isServerboundHookInjected(payloadListenerTransformed));
            Path payloadListenerOutput = environment.gameDir()
                    .resolve("transformed").resolve("ServerCommonPacketListenerImpl.class");
            Files.write(payloadListenerOutput, payloadListenerTransformed);
            LOGGER.info("Transformed result exported to: {}", payloadListenerOutput);
        }

        byte[] entityRaw = environment.readGameClass(AttachmentHookTransformer.ENTITY_CLASS);
        if (entityRaw != null) {
            byte[] entityTransformed = chain.transform(
                    AttachmentHookTransformer.ENTITY_CLASS, entityRaw, gameClassLoader);
            LOGGER.info("Attachment persistence hooks: {} {} -> {} bytes, save={}, load={}",
                    AttachmentHookTransformer.ENTITY_CLASS, entityRaw.length,
                    entityTransformed.length,
                    AttachmentHookTransformer.isEntitySaveHookInjected(entityTransformed),
                    AttachmentHookTransformer.isEntityLoadHookInjected(entityTransformed));
            Path entityOutput = environment.gameDir().resolve("transformed").resolve("Entity.class");
            Files.write(entityOutput, entityTransformed);
            LOGGER.info("Transformed result exported to: {}", entityOutput);
        }

        byte[] packRepositoryRaw = environment.readGameClass(PackSourceInjectTransformer.TARGET_CLASS);
        if (packRepositoryRaw != null) {
            byte[] packRepositoryTransformed = chain.transform(
                    PackSourceInjectTransformer.TARGET_CLASS, packRepositoryRaw, gameClassLoader);
            LOGGER.info("Resource pack mount hooks: {} {} -> {} bytes, sources={}, select={}",
                    PackSourceInjectTransformer.TARGET_CLASS, packRepositoryRaw.length,
                    packRepositoryTransformed.length,
                    PackSourceInjectTransformer.isSourceHookInjected(packRepositoryTransformed),
                    PackSourceInjectTransformer.isSelectHookInjected(packRepositoryTransformed));
            Path packRepositoryOutput = environment.gameDir()
                    .resolve("transformed").resolve("PackRepository.class");
            Files.write(packRepositoryOutput, packRepositoryTransformed);
            LOGGER.info("Transformed result exported to: {}", packRepositoryOutput);
        }

        dryRunDistCleaner(environment, chain, gameClassLoader, candidates, dist);

        LOGGER.info("Pass --launch to hand off to the real game (requires a full libraries environment)");
        LOGGER.info("============================================");
    }

    /** dry-run:扫描 Mod jar 中带 @DistOnly 标注的类,演示 DistCleaner 剥离(报告 + 落盘) */
    private static void dryRunDistCleaner(GameEnvironment environment, TransformerChain chain,
                                          GameClassLoader gameClassLoader, List<ModCandidate> candidates,
                                          Dist dist) throws IOException {
        String sampleName = null;
        byte[] sampleBytes = null;
        search:
        for (ModCandidate candidate : candidates) {
            Path jarPath = candidate.jarPath();
            if (jarPath == null || !Files.isRegularFile(jarPath)) {
                continue;
            }
            try (ZipFile zip = new ZipFile(jarPath.toFile())) {
                for (ZipEntry entry : zip.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                    byte[] entryBytes;
                    try (InputStream in = zip.getInputStream(entry)) {
                        entryBytes = in.readAllBytes();
                    }
                    if (DistCleanerTransformer.isDistAnnotated(entryBytes)) {
                        sampleName = entry.getName().substring(0, entry.getName().length() - ".class".length())
                                .replace('/', '.');
                        sampleBytes = entryBytes;
                        break search;
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("DistCleaner demo scan skipped for {}: {}", jarPath.getFileName(), e.toString());
            }
        }
        if (sampleBytes == null) {
            LOGGER.info("DistCleaner demo skipped: no @DistOnly-annotated class found in mod jars");
            return;
        }
        byte[] stripped = chain.transform(sampleName, sampleBytes, gameClassLoader);
        LOGGER.info("DistCleaner demo: {} {} -> {} bytes, strip marker present: {} (active dist={})",
                sampleName, sampleBytes.length, stripped.length,
                DistCleanerTransformer.isStripMarked(stripped), dist);
        Path output = environment.gameDir().resolve("transformed").resolve("DistCleanerSample.class");
        Files.createDirectories(output.getParent());
        Files.write(output, stripped);
        LOGGER.info("Transformed result exported to: {}", output);
    }

    /** 加载器版本:优先读 jar 清单中的 Implementation-Version,开发期回退为构建版本号 */
    private static String loaderVersion() {
        String version = LightingBootstrap.class.getPackage().getImplementationVersion();
        return version != null ? version : "1.0-SNAPSHOT";
    }

    /** 加载器自身类路径根(jar 或 classes 目录):供游戏侧加载器加载 lighting.game 包 */
    private static Path selfClasspathRoot() {
        try {
            var source = LightingBootstrap.class.getProtectionDomain().getCodeSource();
            return source == null ? null : Path.of(source.getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }

    /** 把已加载 Mod 列表注入游戏侧菜单钩子(lighting.game 包由游戏侧加载器定义) */
    private static void applyModMenuData(GameClassLoader gameClassLoader, LoadResult result) {
        try {
            Class<?> hook = gameClassLoader.loadClass("top.mcocet.lighting.game.ModMenuHook");
            hook.getMethod("setLoadedMods", List.class).invoke(null, result.mods());
            LOGGER.info("Mod menu data registered: {} mods", result.mods().size());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Failed to register mod menu data (the mod list will be empty): {}", e.toString());
        }
    }

    /**
     * 把游戏侧生命周期接管层绑定到内核钩子(lighting.game 包由游戏侧
     * 加载器自身定义;实例在 {@code BuiltInRegistries} 冻结前被回调)。
     */
    private static void bindGameSideLifecycle(GameClassLoader gameClassLoader, RegistryManager registryManager) {
        try {
            Class<?> bridgeClass = gameClassLoader.loadClass("top.mcocet.lighting.game.GameSideLifecycle");
            GameSideBridge bridge = (GameSideBridge) bridgeClass
                    .getConstructor(RegistryManager.class)
                    .newInstance(registryManager);
            LightingHooks.bindGameSide(bridge);
            LOGGER.info("Game-side lifecycle bound: {}", bridgeClass.getName());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("Failed to bind the game-side lifecycle (registry takeover disabled): {}", e.toString());
        }
    }

    private static List<Path> scanJars(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .toList();
        }
    }

    /** 数据生成模式:发布收集事件,运行全部 provider 并落盘(对应 NeoForge 的 runData 任务) */
    private static void runDataGeneration(ModLoader loader, LoadResult result, LoaderConfig config) {
        DataGenerator generator = new DataGenerator(result.mods(), config.gameDir().resolve("generated"));
        loader.eventBus().post(new GatherDataEvent(result.mods(), generator));
        try {
            int files = generator.run();
            LOGGER.info("Data generation finished: {} provider(s), {} file(s) written to {}",
                    generator.providerCount(), files, generator.outputDir());
        } catch (IOException e) {
            LOGGER.error("Data generation failed: {}", e.toString());
        }
    }

    private static void printReport(LoadResult result) {
        LOGGER.info("========== Load report ==========");
        LOGGER.info("Successfully loaded: {} mods", result.mods().size());

        Map<LoaderType, Integer> counts = new TreeMap<>();
        for (ModContainer mod : result.mods()) {
            counts.merge(mod.loaderType(), 1, Integer::sum);
        }
        counts.forEach((type, count) -> LOGGER.info("  - {}: {}", type, count));

        if (!result.issues().isEmpty()) {
            LOGGER.warn("Issues ({}):", result.issues().size());
            result.issues().forEach(issue ->
                    LOGGER.warn("  - [{}] {}", issue.severity(), issue.message()));
        }
        LOGGER.info("=================================");
    }
}
