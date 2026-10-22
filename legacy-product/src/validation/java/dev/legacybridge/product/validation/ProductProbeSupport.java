package dev.legacybridge.product.validation;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.logging.LogUtils;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.ConversionRequest;
import dev.legacybridge.converter.service.EngineJob;
import dev.legacybridge.converter.service.EngineOutcome;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.product.controller.ConversionController;
import dev.legacybridge.runtime.LegacyBridgeRuntime;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.ClassReader;

/** Production-classpath assertions and bounded background orchestration, using no legacy ClassLoader. */
final class ProductProbeSupport {
    static final String SOURCE_SHA = "00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947";
    static final String OUTPUT_SHA = "541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb";
    private static final ThreadPoolExecutor COORDINATOR = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "legacybridge-product-probe");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private ProductProbeSupport() { }

    static boolean enabled(String side) {
        return Boolean.getBoolean("legacybridge.product.probe")
                && !Boolean.getBoolean("legacybridge.product.p4")
                && !Boolean.getBoolean("legacybridge.product.p5")
                && side.equals(System.getProperty("legacybridge.product.probe.side", side));
    }

    static long deadline() {
        int seconds = Integer.getInteger("legacybridge.product.probe.timeoutSeconds", 90);
        require(seconds >= 30 && seconds <= 300, "Probe timeout must be 30..300 seconds");
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    }

    static Path gameDir() { return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize(); }

    static Path input() { return selected("input", "legacybridge/input/legacybridge-example-1.16.5-1.0.0.jar"); }

    static Path bundle() { return selected("bundle", "legacybridge/cache/mapping-data"); }

    private static Path selected(String property, String defaultPath) {
        Path relative = Path.of(System.getProperty("legacybridge.product.probe." + property, defaultPath));
        require(!relative.isAbsolute(), "Probe paths must be relative to the isolated game directory");
        Path path = gameDir().resolve(relative).normalize();
        require(path.startsWith(gameDir().resolve("legacybridge")) && !path.equals(gameDir().resolve("legacybridge")),
                "Probe path leaves its owned legacybridge directory");
        return path;
    }

    static CompletableFuture<String> run(String side, long deadline, boolean verifyGuiOutputs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                verifyPackaging();
                return exerciseService(side, deadline, verifyGuiOutputs);
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, COORDINATOR);
    }

    static void close() { COORDINATOR.shutdownNow(); }

    static void verifyPackaging() {
        var mods = ModList.get().getMods();
        require(mods.stream().filter(info -> info.getModId().equals("legacybridge_runtime")).count() == 1,
                "Runtime identity must occur exactly once");
        require(ModList.get().getModContainerById("legacybridge_runtime").orElseThrow()
                .getModInfo().getVersion().toString().equals("0.1.1-SNAPSHOT"), "Wrong runtime version");
        require(AdapterCatalog.RUNTIME_VERSION.equals("0.1.1-SNAPSHOT"), "Adapter ABI version changed");
        Module owner = LegacyBridgeRuntime.class.getModule();
        require(owner.isNamed() && owner.getName().equals("legacybridge_runtime"), "Runtime is not the ordinary Forge mod module");
        for (Class<?> type : List.of(LegacyEngineService.class, AdapterCatalog.class, ConversionController.class)) {
            require(type.getModule() == owner, "Own implementation split into unexpected module: " + type.getName());
            require(type.getClassLoader() == LegacyBridgeRuntime.class.getClassLoader(), "Own implementation classloader mismatch");
        }
        require(owner.getLayer() != ModuleLayer.boot(), "Public mod unexpectedly resides in the bootstrap layer");
        require(IMappingFile.class.getModule() != owner, "SRG Utils was flattened into the public mod");
        require(IMappingFile.class.getModule().getName().equals("net.minecraftforge.srgutils"), "Unexpected SRG Utils module");
        requireVersion(IMappingFile.class, "0.6.2");
        requireVersion(ClassReader.class, "9.9.1");
        requireVersion(Config.class, "3.6.4");
        requireVersion(TomlParser.class, "3.6.4");
        require(ClassReader.class.getModule() != owner && Config.class.getModule() != owner,
                "Provided ASM/NightConfig must not be copied into LegacyBridge");
        require(org.slf4j.Logger.class.getModule() != owner, "Logging API was copied into LegacyBridge");
        var fixtureFile = ModList.get().getModFileById(LegacyBridgeProductProbe.FIXTURE_ID).getFile().getFilePath();
        require(!fixtureFile.toAbsolutePath().normalize().equals(input()), "Original input was discovered as a Forge mod");
        require(!fixtureFile.toAbsolutePath().normalize().startsWith(gameDir().resolve("legacybridge/input")),
                "Forge unexpectedly discovered the input directory");
        LogUtils.getLogger().info("LEGACYBRIDGE_PRODUCT_PACKAGING_OK modModule={} loader={} srgModule={} srgVersion=0.6.2 asmVersion=9.9.1 nightConfig=3.6.4 hostLogging={}",
                owner.getName(), LegacyEngineService.class.getClassLoader().getName(),
                IMappingFile.class.getModule().getName(), LogUtils.getLogger().getClass().getName());
    }

    private static void requireVersion(Class<?> type, String expected) {
        String actual = type.getModule().getDescriptor().rawVersion()
                .orElseGet(() -> type.getPackage().getImplementationVersion());
        require(expected.equals(actual), "Unexpected library version for " + type.getName() + ": " + actual);
    }

    private static String exerciseService(String side, long deadline, boolean verifyGuiOutputs) throws Exception {
        require(!Thread.currentThread().getName().equals("Render thread"), "Probe orchestration ran on render thread");
        Path input = input();
        require(input.equals(input.toRealPath()), "Probe original is an aliased path");
        require(bundle().equals(bundle().toRealPath()), "Probe bundle is an aliased path");
        String original = sha256(input);
        require(original.equals(SOURCE_SHA), "Source fixture does not match the validated original");
        List<String> loadedBefore = loadedMods();
        if (verifyGuiOutputs) verifyGuiArtifacts();
        Path output = selected("output", "legacybridge/staging/probe-converted.jar");
        require(!Files.exists(output, LinkOption.NOFOLLOW_LINKS), "Probe never overwrites a staged result");
        Files.createDirectories(output.getParent());
        require(output.getParent().equals(output.getParent().toRealPath()), "Probe staging directory is aliased");
        Path reports = gameDir().resolve("legacybridge/reports/probe-" + side);
        Files.createDirectories(reports.getParent());
        require(reports.getParent().equals(reports.getParent().toRealPath()), "Probe reports directory is aliased");
        Files.createDirectory(reports);
        var writer = new CanonicalReportWriter();
        try (var engine = new LegacyEngineService()) {
            var analysis = await(engine.analyze(input), deadline);
            require(analysis.modId().equals(LegacyBridgeProductProbe.FIXTURE_ID), "Analysis changed the mod ID");
            require(analysis.modVersion().equals("1.0.0") && analysis.input().sha256().equals(original), "Analysis identity/hash mismatch");
            writer.writeAtomically(reports.resolve("analysis.json"), analysis, false, input);
            Path refusedOutput = output.resolveSibling("refused-without-mappings.jar");
            require(!Files.exists(refusedOutput), "Negative probe output already exists");
            var refusedJob = engine.convert(new ConversionRequest(input, refusedOutput,
                    dev.legacybridge.converter.service.MappingData.unavailable(), Optional.of(original)));
            long remaining = deadline - System.nanoTime();
            require(remaining > 0, "Probe deadline expired before negative conversion");
            var refused = refusedJob.completion().toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS);
            require(refused.status() == EngineOutcome.Status.FAILED
                    && refused.error().orElseThrow().code().equals("MAPPING_DATA_REQUIRED"),
                    "Missing data must produce the same explicit packaged-service error");
            require(!Files.exists(refusedOutput), "Missing data published a partial JAR");
            var data = dev.legacybridge.converter.service.MappingData.bundle(bundle());
            var mappings = await(engine.mappings(input, data), deadline);
            require(mappings.sourcesComplete() && mappings.unreadableClassCount() == 0, "Mapping inputs are incomplete");
            writer.writeJsonAtomically(reports.resolve("mappings.json"), writer.toJsonTree(mappings.toTree()), false, input);
            var request = new ConversionRequest(input, output, data, Optional.of(original));
            var plan = await(engine.plan(request), deadline);
            require(plan.convertible() && plan.candidateSha256().orElseThrow().equals(OUTPUT_SHA), "Plan did not reproduce the proven candidate");
            require(!Files.exists(output), "Planning published an output");
            writer.writeJsonAtomically(reports.resolve("plan.json"), writer.toJsonTree(plan.report()), false, input);
            var result = await(engine.convert(request), deadline);
            require(result.converted() && Files.isRegularFile(output), "Conversion did not publish a complete result");
            require(sha256(output).equals(OUTPUT_SHA), "Packaged conversion differs from the proven output");
            writer.writeJsonAtomically(reports.resolve("conversion.json"), writer.toJsonTree(result.report()), false, input);
        }
        require(sha256(input).equals(original), "Conversion modified the original");
        require(loadedBefore.equals(loadedMods()), "Static operations changed the loaded-mod set");
        return "analyze=mappings=plan=convert=passed sourceSha=" + original + " outputSha=" + OUTPUT_SHA
                + " loadedModsUnchanged=true missingDataRefusal=passed guiArtifacts=" + verifyGuiOutputs;
    }

    private static <T> T await(EngineJob<T> job, long deadline) throws Exception {
        long remaining = deadline - System.nanoTime();
        require(remaining > 0, "Probe deadline expired");
        EngineOutcome<T> outcome;
        try { outcome = job.completion().toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS); }
        catch (Exception failure) { job.cancel(); throw failure; }
        require(outcome.status() == EngineOutcome.Status.COMPLETED,
                "Engine operation failed: " + outcome.error().map(error -> error.code() + ": " + error.message()).orElse(outcome.status().name()));
        return outcome.value().orElseThrow();
    }

    private static List<String> loadedMods() {
        return ModList.get().getMods().stream().map(info -> info.getModId() + ":" + info.getVersion()).sorted().toList();
    }

    private static void verifyGuiArtifacts() throws Exception {
        Path staging = gameDir().resolve("legacybridge/staging");
        try (var files = Files.walk(staging, 2)) {
            List<Path> candidates = files.filter(path -> path.getFileName().toString().equals("converted.jar"))
                    .limit(3).toList();
            require(candidates.size() == 1, "GUI did not create exactly one staged conversion");
            require(sha256(candidates.get(0)).equals(OUTPUT_SHA), "GUI conversion bytes differ from the baseline");
        }
        for (String name : List.of("analysis.json", "conversion.json")) {
            try (var files = Files.walk(gameDir().resolve("legacybridge/reports"), 2)) {
                require(files.filter(path -> path.getFileName().toString().equals(name)).limit(2).count() == 1,
                        "GUI did not create exactly one " + name);
            }
        }
    }

    static String sha256(Path path) throws Exception {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Expected a regular probe file: " + path);
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int count; (count = input.read(buffer)) != -1;) {
                require((total += count) <= (64L << 20), "Probe file exceeds 64 MiB");
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
