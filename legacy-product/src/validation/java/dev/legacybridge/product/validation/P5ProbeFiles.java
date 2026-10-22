package dev.legacybridge.product.validation;

import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.ConversionRequest;
import dev.legacybridge.converter.service.EngineOutcome;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.product.controller.InstallationShutdownBarrier;
import dev.legacybridge.product.install.InstallSelection;
import dev.legacybridge.product.install.InstallSession;
import dev.legacybridge.product.install.TransactionResult;
import dev.legacybridge.product.install.TransactionalInstaller;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static dev.legacybridge.product.validation.ProductProbeSupport.require;

/** Separate QA-only bounded worker. All original/installed JAR handling is inert static I/O. */
final class P5ProbeFiles {
    record Prepared(Path original, String originalSha) { }
    record Update(Prepared source, Path updatedOriginal, String updatedOriginalSha, Path staged,
                  String stagedSha, TransactionResult interrupted, Path previousInstalled) { }
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "legacybridge-p5-probe-files"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private P5ProbeFiles() { }

    static CompletableFuture<Prepared> prepare() {
        return submit(() -> {
            require(Boolean.getBoolean("legacybridge.product.p5"), "P5 probe needs its explicit QA flag");
            Path original = ProductProbeSupport.input();
            require(original.equals(original.toRealPath()), "Original fixture is aliased");
            require(ProductProbeSupport.sha256(original).equals(ProductProbeSupport.SOURCE_SHA), "Original fixture differs from the proven baseline");
            require(installed().isEmpty(), "Fresh P5 test must have no installed converted fixture");
            return new Prepared(original, ProductProbeSupport.SOURCE_SHA);
        });
    }

    static CompletableFuture<String> verifyInstalled(Prepared prepared, TransactionResult result, String sha) {
        return submit(() -> {
            unchanged(prepared);
            require(result.status() == TransactionResult.Status.COMMITTED && result.safeToClose(), "Installation is not committed");
            require(result.entries().size() == 1 && result.entries().get(0).modId().equals(LegacyBridgeProductProbe.FIXTURE_ID), "Wrong committed identity");
            var files = installed();
            Path target = result.entries().get(0).installed().orElseThrow();
            require(files.size() == 1 && files.get(0).equals(target) && ProductProbeSupport.sha256(target).equals(sha), "Committed filesystem state/hash differs");
            require(Files.isRegularFile(result.transaction().resolve("plan-v2.json"), LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(result.transaction().resolve("checkpoint-committed--1.json"), LinkOption.NOFOLLOW_LINKS), "V2 committed journal is missing");
            return "transaction=" + result.transaction().getFileName() + " installed=" + target.getFileName() + " outputSha=" + sha;
        });
    }

    static CompletableFuture<String> verifyRemoved(Prepared prepared, TransactionResult restored) {
        return submit(() -> {
            unchanged(prepared);
            require(restored.status() == TransactionResult.Status.RESTORED && restored.safeToClose(), "Restoration was not completed");
            require(installed().isEmpty(), "Restored add-only transaction left an installed fixture");
            return "restored=" + restored.transaction().getFileName() + " installedFixtureCount=0 original=unchanged";
        });
    }

    static CompletableFuture<Update> prepareInterruptedUpdate(Prepared prepared, TransactionResult previous, InstallSession session) {
        return submit(() -> {
            unchanged(prepared);
            require(previous.safeToClose() && previous.entries().size() == 1, "Previous managed baseline must be committed");
            Path old = previous.entries().get(0).installed().orElseThrow();
            require(ProductProbeSupport.sha256(old).equals(ProductProbeSupport.OUTPUT_SHA), "Previous installed baseline changed");
            Path input = Files.createDirectory(prepared.original().getParent().resolve("qa-updates"));
            Path updated = input.resolve("legacybridge-example-1.16.5-1.0.1.jar");
            versionClone(prepared.original(), updated);
            String sourceSha = ProductProbeSupport.sha256(updated);
            Path stage = Files.createDirectory(ProductProbeSupport.gameDir().resolve("legacybridge/staging/p5-update-" + UUID.randomUUID()));
            Path output = stage.resolve("converted.jar");
            try (var engine = new LegacyEngineService()) {
                var analyzed = engine.analyze(updated).completion().toCompletableFuture().get(45, TimeUnit.SECONDS);
                require(analyzed.status() == EngineOutcome.Status.COMPLETED && analyzed.value().orElseThrow().modVersion().equals("1.0.1"), "Static version-clone analysis failed");
                var converted = engine.convert(new ConversionRequest(updated, output, MappingData.bundle(ProductProbeSupport.bundle())))
                        .completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
                require(converted.status() == EngineOutcome.Status.COMPLETED && converted.value().orElseThrow().converted(), "Packaged engine refused the version-only fixture clone");
                Path reports = Files.createDirectory(ProductProbeSupport.gameDir().resolve("legacybridge/reports/p5-update-" + UUID.randomUUID()));
                var writer = new CanonicalReportWriter();
                writer.writeAtomically(reports.resolve("analysis.json"), analyzed.value().orElseThrow(), false, updated);
                writer.writeJsonAtomically(reports.resolve("conversion.json"), writer.toJsonTree(converted.value().orElseThrow().report()), false, updated);
            }
            String outputSha = ProductProbeSupport.sha256(output);
            require(!outputSha.equals(ProductProbeSupport.OUTPUT_SHA), "Version update unexpectedly produced old metadata bytes");
            TransactionalInstaller installer = interruptedBeforePublish();
            TransactionResult partial;
            try (var lease = InstallationShutdownBarrier.acquire()) {
                partial = installer.apply(ProductProbeSupport.gameDir(), List.of(new InstallSelection(updated, sourceSha,
                        output, outputSha, Optional.of(previous.transaction()))), session);
            }
            require(partial.status() == TransactionResult.Status.PARTIAL && partial.error().isPresent(), "QA boundary did not produce a real partial update");
            require(partial.entries().size() == 1 && partial.entries().get(0).state() == TransactionResult.EntryState.RETIRED,
                    "Expected old version retired before deliberately interrupted publication");
            require(installed().isEmpty() && !Files.exists(old, LinkOption.NOFOLLOW_LINKS), "Interrupted update did not stop at the expected disk boundary");
            Path backup = partial.transaction().resolve("backup-0.jar"), retired = partial.transaction().resolve("retired-0.jar");
            require(ProductProbeSupport.sha256(backup).equals(ProductProbeSupport.OUTPUT_SHA)
                    && ProductProbeSupport.sha256(retired).equals(ProductProbeSupport.OUTPUT_SHA)
                    && !Files.isSameFile(backup, retired), "Managed update lacks an independent intact backup");
            unchanged(prepared);
            return new Update(prepared, updated, sourceSha, output, outputSha, partial, old);
        });
    }

    static CompletableFuture<String> verifyUpdateRestored(Update update, TransactionResult result) {
        return submit(() -> {
            unchanged(update.source());
            require(result.transaction().equals(update.interrupted().transaction()) && result.status() == TransactionResult.Status.RESTORED
                    && result.safeToClose(), "Updated transaction was not restored");
            var current = installed();
            require(current.size() == 1 && current.get(0).equals(update.previousInstalled())
                    && ProductProbeSupport.sha256(current.get(0)).equals(ProductProbeSupport.OUTPUT_SHA), "Restore did not recover the exact original managed installation");
            require(ProductProbeSupport.sha256(update.updatedOriginal()).equals(update.updatedOriginalSha())
                    && ProductProbeSupport.sha256(update.staged()).equals(update.stagedSha()), "Update source/staging was mutated");
            return "updateVersion=1.0.1 resumedThenRestored=1.0.0 backupIndependent=true sourceAndStaging=unchanged";
        });
    }

    private static TransactionalInstaller interruptedBeforePublish() throws Exception {
        // Explicitly authorized QA reflection into OUR existing failure seam, never original/mod code.
        // Do not add opens/exports or a production flag if the real ModLauncher layer refuses access.
        Class<?> hook = Class.forName("dev.legacybridge.product.install.TransactionalInstaller$Hook", false, TransactionalInstaller.class.getClassLoader());
        Object observer = Proxy.newProxyInstance(hook.getClassLoader(), new Class<?>[] { hook }, (proxy, method, arguments) -> {
            if (method.getName().equals("reached")) {
                if (((Enum<?>) arguments[0]).name().equals("BEFORE_PUBLISH")) throw new IOException("P5 QA injected before publication, after managed retirement");
                return null;
            }
            return switch (method.getName()) {
                case "toString" -> "P5-QA-only-boundary-observer";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
        var constructor = TransactionalInstaller.class.getDeclaredConstructor(hook);
        require(constructor.trySetAccessible(), "Production module refuses the separate QA boundary seam; do not change product opens");
        return constructor.newInstance(observer);
    }

    private static void versionClone(Path source, Path target) throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipFile zip = new ZipFile(source.toFile())) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                require(entries.size() < 128 && entry.getSize() <= (1 << 20), "Unexpected audited fixture shape");
                if (entry.isDirectory()) continue;
                try (var stream = zip.getInputStream(entry)) {
                    byte[] bytes = stream.readNBytes((1 << 20) + 1); require(bytes.length <= (1 << 20), "Fixture entry limit"); entries.put(entry.getName(), bytes);
                }
            }
        }
        replaceMetadata(entries, "META-INF/mods.toml", "version=\"1.0.0\"", "version=\"1.0.1\"");
        replaceMetadata(entries, "META-INF/MANIFEST.MF", "Implementation-Version: 1.0.0", "Implementation-Version: 1.0.1");
        try (var output = new ZipOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            for (var entry : entries.entrySet()) { ZipEntry name = new ZipEntry(entry.getKey()); name.setTime(0); output.putNextEntry(name); output.write(entry.getValue()); output.closeEntry(); }
        }
    }

    private static void replaceMetadata(Map<String, byte[]> entries, String path, String before, String after) {
        String text = new String(entries.get(path), StandardCharsets.UTF_8);
        require(text.indexOf(before) >= 0 && text.indexOf(before) == text.lastIndexOf(before), "Expected one proven version token in " + path);
        entries.put(path, text.replace(before, after).getBytes(StandardCharsets.UTF_8));
    }

    private static void unchanged(Prepared prepared) throws Exception { require(ProductProbeSupport.sha256(prepared.original()).equals(prepared.originalSha()), "Original path/bytes changed"); }
    private static List<Path> installed() throws IOException {
        try (var files = Files.list(ProductProbeSupport.gameDir().resolve("mods"))) {
            return files.filter(path -> path.getFileName().toString().startsWith("legacybridge-legacybridge_example-")).limit(5).toList();
        }
    }
    private static <T> CompletableFuture<T> submit(Work<T> work) {
        return CompletableFuture.supplyAsync(() -> { try { return work.run(); } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); } }, WORKER);
    }
    static void close() { WORKER.shutdown(); }
    @FunctionalInterface private interface Work<T> { T run() throws Exception; }
}
