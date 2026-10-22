package dev.legacybridge.product.validation;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.service.EngineOutcome;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.product.controller.ConversionController;
import dev.legacybridge.product.controller.InventoryKind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static dev.legacybridge.product.validation.ProductProbeSupport.require;

/** Bounded QA I/O coordinator. No method returning a future does its I/O on the caller thread. */
final class P4ProbeFiles {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "legacybridge-p4-probe-files");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private P4ProbeFiles() { }

    static CompletableFuture<P4FixturePreflight.Prepared> prepare() {
        return submit(() -> {
            var fixtures = P4FixturePreflight.prepare();
            try (var engine = new LegacyEngineService()) {
                var outcome = engine.inventory(fixtures.original().getParent()).completion().toCompletableFuture().get(45, TimeUnit.SECONDS);
                require(outcome.status() == EngineOutcome.Status.COMPLETED, "P4 static inventory failed");
                var inventory = outcome.value().orElseThrow();
                require(!inventory.truncated(), "Unexpected truncation of bounded P4 fixture inventory");
                Map<String, InventoryKind> expected = Map.of(
                        fixtures.original().getFileName().toString(), InventoryKind.CANDIDATE,
                        P4FixturePreflight.CORRUPT, InventoryKind.INVALID,
                        P4FixturePreflight.UNSUPPORTED, InventoryKind.CANDIDATE,
                        P4FixturePreflight.MULTIPLE, InventoryKind.CANDIDATE,
                        P4FixturePreflight.AMBIGUOUS, InventoryKind.INCOMPATIBLE,
                        P4FixturePreflight.TARGET, InventoryKind.INCOMPATIBLE,
                        P4FixturePreflight.DUPLICATE_A, InventoryKind.DUPLICATE,
                        P4FixturePreflight.DUPLICATE_B, InventoryKind.DUPLICATE);
                for (var entry : expected.entrySet()) {
                    var row = inventory.entries().stream().filter(value -> value.path().getFileName().toString().equals(entry.getKey()))
                            .findFirst().orElseThrow();
                    require(ConversionController.classify(row) == entry.getValue(), "Unexpected inventory category: " + entry.getKey());
                }
                var multiple = inventory.entries().stream().filter(value -> value.path().getFileName().toString()
                        .equals(P4FixturePreflight.MULTIPLE)).findFirst().orElseThrow();
                require(multiple.mods().size() == 2, "Multiple mod declarations were not inventoried");
            }
            fixtures.verifyUnchanged();
            return fixtures;
        });
    }

    static CompletableFuture<String> verifyStaged(P4FixturePreflight.Prepared fixtures) {
        return submit(() -> {
            fixtures.verifyUnchanged();
            require(installedFixtures().isEmpty(), "A conversion installed content before explicit Apply");
            List<Path> staged = named(ProductProbeSupport.gameDir().resolve("legacybridge/staging"), "converted.jar");
            require(staged.size() == 1, "Expected one positive staged output and no unsupported output");
            require(ProductProbeSupport.sha256(staged.get(0)).equals(ProductProbeSupport.OUTPUT_SHA), "P4 conversion changed baseline output");
            List<Path> conversions = named(ProductProbeSupport.gameDir().resolve("legacybridge/reports"), "conversion.json");
            require(conversions.size() == 2, "Expected independent positive and unsupported conversion reports");
            Set<Object> statuses = conversions.stream().map(path -> {
                try { return object(path).get("status"); }
                catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
            }).collect(java.util.stream.Collectors.toSet());
            require(statuses.equals(Set.of("CONVERTED", "ABORTED")), "Per-job conversion reports do not distinguish success and refusal");
            require(named(ProductProbeSupport.gameDir().resolve("legacybridge/reports"), "analysis.json").size() == 2,
                    "Each queued conversion must retain its own compatibility analysis");
            require(!named(ProductProbeSupport.gameDir().resolve("legacybridge/reports"), "transformation-log.json").isEmpty(),
                    "The successful job has no separate transformation log");
            return "sources=8 observedCategories=4 multipleIds=2 staged=1 converted=1 blocked=1 reports=separate";
        });
    }

    static CompletableFuture<String> verifyNotInstalled() {
        return submit(() -> { require(installedFixtures().isEmpty(), "Declined Apply unexpectedly installed a mod"); return "declinedApply=noWrites"; });
    }

    static CompletableFuture<String> verifyApplied(P4FixturePreflight.Prepared fixtures) {
        return submit(() -> {
            fixtures.verifyUnchanged();
            List<Path> installed = installedFixtures();
            require(installed.size() == 1 && ProductProbeSupport.sha256(installed.get(0)).equals(ProductProbeSupport.OUTPUT_SHA),
                    "Apply did not install exactly the verified baseline JAR");
            var receipts = named(ProductProbeSupport.gameDir().resolve("legacybridge/transactions"), "committed.json");
            require(receipts.size() == 1 && "PENDING_RESTART".equals(object(receipts.get(0)).get("state")),
                    "Apply lacks its independent pending-restart receipt");
            return "installed=" + installed.get(0).getFileName() + " outputSha=" + ProductProbeSupport.OUTPUT_SHA
                    + " receipt=PENDING_RESTART originalHashes=unchanged";
        });
    }

    static CompletableFuture<String> verifyRelaunch() {
        return submit(() -> {
            var installed = installedFixtures();
            require(installed.size() == 1 && ProductProbeSupport.sha256(installed.get(0)).equals(ProductProbeSupport.OUTPUT_SHA),
                    "Relaunch installed artifact differs from verified apply");
            return "activationFile=" + installed.get(0).getFileName() + " outputSha=" + ProductProbeSupport.OUTPUT_SHA;
        });
    }

    static CompletableFuture<String> verifyAfterClose(P4FixturePreflight.Prepared fixtures) {
        return submit(() -> {
            fixtures.verifyUnchanged();
            List<Path> staged = named(ProductProbeSupport.gameDir().resolve("legacybridge/staging"), "converted.jar");
            require(staged.size() >= 1 && staged.size() <= 2, "Unexpected publications after close/cancel");
            for (Path output : staged) require(ProductProbeSupport.sha256(output).equals(ProductProbeSupport.OUTPUT_SHA),
                    "Close/cancel left a published nonbaseline or partial JAR");
            require(installedFixtures().size() == 1, "Continuing/cancelling conversion installed another artifact");
            return "postClosePublished=" + staged.size() + " publishedHashes=verified originalHashes=unchanged";
        });
    }

    private static List<Path> installedFixtures() throws Exception {
        Path mods = ProductProbeSupport.gameDir().resolve("mods");
        try (var entries = Files.list(mods)) {
            return entries.filter(path -> path.getFileName().toString().startsWith("legacybridge-legacybridge_example-"))
                    .limit(3).toList();
        }
    }

    private static List<Path> named(Path directory, String name) throws Exception {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        require(directory.equals(directory.toRealPath()), "Aliased QA evidence directory");
        try (var files = Files.walk(directory, 2)) {
            return files.limit(256).filter(path -> path.getFileName().toString().equals(name)).toList();
        }
    }

    private static Map<?, ?> object(Path path) throws Exception {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= (2L << 20), "Oversized/irregular QA JSON evidence");
        Object value = BoundedJsonParser.parse(Files.readString(path, StandardCharsets.UTF_8));
        require(value instanceof Map<?, ?>, "Expected structured JSON evidence");
        return (Map<?, ?>) value;
    }

    private static <T> CompletableFuture<T> submit(Work<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try { return work.run(); }
            catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, WORKER);
    }

    static void close() { WORKER.shutdownNow(); }
    @FunctionalInterface private interface Work<T> { T run() throws Exception; }
}
