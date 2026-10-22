package dev.legacybridge.converter.mapping.portable;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.mapping.LegacySymbol;
import dev.legacybridge.converter.mapping.MappingDatabaseGenerator;
import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingSource;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.operation.OperationStoppedException;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import net.minecraftforge.srgutils.IMappingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PortableMappingBundleTest {
    @TempDir Path temporary;

    @Test
    void roundTripsAllConsumedSymbolsAndDeclarationsWithoutSourcePathsOrClassfiles() throws Exception {
        MappingInputs original = inputs();
        Path directory = PortableMappingBundle.export(original, temporary.resolve("bundle"));
        MappingInputs loaded = PortableMappingBundle.load(directory);
        assertTrue(loaded.complete());
        assertEquals(original.sources(), loaded.sources());
        assertEquals(original.diagnostics(), loaded.diagnostics());
        assertEquals(original.legacyApi().classes(), loaded.legacyApi().classes());
        assertEquals(original.targetApi().classes(), loaded.targetApi().classes());
        assertEquals("a", loaded.legacyOfficial().getClass("fixture/Api").getMapped());
        assertEquals("I", loaded.legacyOfficial().getClass("fixture/Api").getField("count").getDescriptor());
        assertNull(loaded.legacySrg().getClass("a").getField("b").getDescriptor());
        assertEquals("func_123_run", loaded.legacySrg().getClass("a").getMethod("c", "()V").getMapped());
        assertEquals("<init>", loaded.legacyOfficial().getClass("fixture/Api").getMethod("<init>", "()V").getMapped());
        List<LegacySymbol> symbols = List.of(LegacySymbol.classSymbol("fixture/Api"));
        assertEquals(new MappingDatabaseGenerator(original).generate(symbols).resolve(symbols.get(0)),
                new MappingDatabaseGenerator(loaded).generate(symbols).resolve(symbols.get(0)));
        String manifest = Files.readString(directory.resolve("manifest.json"));
        assertFalse(manifest.contains(temporary.toString()));
        assertTrue(manifest.contains(PortableMappingBundle.DISTRIBUTION_POLICY));
        try (var files = Files.list(directory)) {
            assertEquals(7, files.count());
        }
    }

    @Test
    void exportsDeterministicBytesIndependentOfDestination() throws Exception {
        Path first = PortableMappingBundle.export(inputs(), temporary.resolve("first"));
        Path second = PortableMappingBundle.export(inputs(), temporary.resolve("second"));
        for (String file : names()) assertArrayEquals(Files.readAllBytes(first.resolve(file)), Files.readAllBytes(second.resolve(file)));
    }

    @Test
    void everyManifestObjectUsesLexicalKeysRegardlessOfJvmMapIterationOrder() throws Exception {
        Path directory = bundle();
        Object parsed = BoundedJsonParser.parse(Files.readString(directory.resolve("manifest.json")));
        assertSortedObjectKeys(parsed);
    }

    @Test
    void neverOverwritesExistingDirectoryOrFile() throws Exception {
        Path target = Files.createDirectory(temporary.resolve("existing"));
        Files.writeString(target.resolve("owned-by-user.txt"), "preserve");
        assertThrows(IOException.class, () -> PortableMappingBundle.export(inputs(), target));
        assertEquals("preserve", Files.readString(target.resolve("owned-by-user.txt")));
        Path file = Files.writeString(temporary.resolve("file"), "preserve");
        assertThrows(IOException.class, () -> PortableMappingBundle.export(inputs(), file));
        assertEquals("preserve", Files.readString(file));
    }

    @Test
    void refusesIncompleteExportWithoutCreatingOutput() throws Exception {
        MappingInputs original = inputs();
        MappingInputs incomplete = new MappingInputs(original.legacyOfficial(), original.targetOfficial(),
                original.legacySrg(), original.targetSrg(), original.legacyApi(), original.targetApi(),
                original.sources(), original.diagnostics(), false);
        Path target = temporary.resolve("incomplete");
        assertThrows(IOException.class, () -> PortableMappingBundle.export(incomplete, target));
        assertFalse(Files.exists(target));
    }

    @Test
    void refusesMissingFilesAndUnexpectedEntries() throws Exception {
        Path bundle = bundle();
        Files.delete(bundle.resolve("legacy-api.lbapi"));
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
        Path complete = PortableMappingBundle.export(inputs(), temporary.resolve("complete"));
        Files.writeString(complete.resolve("surprise.class"), "not a class");
        assertThrows(IOException.class, () -> PortableMappingBundle.load(complete));
    }

    @Test
    void refusesPayloadCorruptionBeforeParsing() throws Exception {
        Path bundle = bundle();
        Path payload = bundle.resolve("legacy-api.lbapi");
        byte[] bytes = Files.readAllBytes(payload);
        bytes[bytes.length - 1] ^= 1;
        Files.write(payload, bytes);
        assertTrue(assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle)).getMessage().contains("SHA-256 mismatch"));
    }

    @Test
    void refusesPayloadLengthChanges() throws Exception {
        Path bundle = bundle();
        Files.write(bundle.resolve("legacy-api.lbapi"), new byte[]{1}, StandardOpenOption.APPEND);
        assertTrue(assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle)).getMessage().contains("size mismatch"));
    }

    @Test
    void refusesDuplicateJsonKeysUnknownFieldsAndInvalidUtf8() throws Exception {
        Path bundle = bundle();
        Path manifest = bundle.resolve("manifest.json");
        String original = Files.readString(manifest);
        Files.writeString(manifest, "{\"schemaVersion\":1," + original.substring(original.indexOf('{') + 1));
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
        Files.writeString(manifest, original);
        Map<String, Object> tree = manifest(bundle);
        tree.put("unknown", true);
        manifest(bundle, tree);
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
        Files.write(manifest, new byte[]{(byte) 0xc3, (byte) 0x28});
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
    }

    @Test
    void refusesSchemaProfileNamespaceBaselineAndDistributionDrift() throws Exception {
        Path bundle = bundle();
        Map<String, Object> pristine = manifest(bundle);
        for (String field : List.of("schemaVersion", "profile", "complete", "distributionPolicy")) {
            Map<String, Object> changed = new HashMap<>(pristine);
            changed.put(field, "wrong");
            manifest(bundle, changed);
            assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle), field);
        }
        for (String field : List.of("baselines", "namespaces")) {
            Map<String, Object> changed = new HashMap<>(pristine);
            changed.put(field, Map.of("incorrect", "value"));
            manifest(bundle, changed);
            assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle), field);
        }
    }

    @Test
    void refusesUnsafeAndDuplicateManifestFileNames() throws Exception {
        Path bundle = bundle();
        Map<String, Object> pristine = manifest(bundle);
        List<Object> files = new ArrayList<>((List<?>) pristine.get("files"));
        Map<String, Object> first = new HashMap<>(castMap(files.get(0)));
        first.put("name", "../outside.lbmap");
        files.set(0, first);
        Map<String, Object> changed = new HashMap<>(pristine);
        changed.put("files", files);
        manifest(bundle, changed);
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
        files = new ArrayList<>((List<?>) pristine.get("files"));
        files.set(1, files.get(0));
        changed.put("files", files);
        manifest(bundle, changed);
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
    }

    @Test
    void refusesMissingOrDuplicatedSourceProvenance() throws Exception {
        Path bundle = bundle();
        Map<String, Object> tree = manifest(bundle);
        List<Object> sources = new ArrayList<>((List<?>) tree.get("sources"));
        sources.remove(0);
        tree.put("sources", sources);
        manifest(bundle, tree);
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
        sources.add(sources.get(0));
        manifest(bundle, tree);
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
    }

    @Test
    void refusesOversizedFilesBeforeAllocation() throws Exception {
        Path bundle = bundle();
        try (FileChannel channel = FileChannel.open(bundle.resolve("legacy-api.lbapi"), StandardOpenOption.WRITE)) {
            channel.position(PortableMappingBundle.MAX_FILE_BYTES);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
    }

    @Test
    void refusesOversizedManifestBeforeParsing() throws Exception {
        Path bundle = bundle();
        try (FileChannel channel = FileChannel.open(bundle.resolve("manifest.json"), StandardOpenOption.WRITE)) {
            channel.position(PortableMappingBundle.MAX_MANIFEST_BYTES);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
    }

    @Test
    void refusesTraversalPathsAndAbsentParent() throws Exception {
        Path bundle = bundle();
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle.resolve("..")));
        assertThrows(IOException.class, () -> PortableMappingBundle.export(inputs(), temporary.resolve("missing/child")));
    }

    @Test
    void cancellationDuringExportCleansOnlyOwnedTemporaryAndDoesNotPublish() throws Exception {
        AtomicReference<OperationControl> reference = new AtomicReference<>();
        OperationControl control = new OperationControl(OperationLimits.defaults(), progress -> {
            if (progress.stage() == OperationStage.SERIALIZATION && progress.completed() == 1) reference.get().requestCancel();
        });
        reference.set(control);
        Path target = temporary.resolve("cancelled");
        try (var ignored = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> PortableMappingBundle.export(inputs(), target));
        }
        assertFalse(Files.exists(target));
        try (var children = Files.list(temporary)) { assertEquals(0, children.count()); }
    }

    @Test
    void publicationBoundaryRejectsLateCancellation() throws Exception {
        AtomicReference<OperationControl> reference = new AtomicReference<>();
        AtomicBoolean refused = new AtomicBoolean();
        OperationControl control = new OperationControl(OperationLimits.defaults(), progress -> {
            if (progress.stage() == OperationStage.PUBLICATION) refused.set(!reference.get().requestCancel());
        });
        reference.set(control);
        Path target = temporary.resolve("published");
        try (var ignored = control.enter()) { PortableMappingBundle.export(inputs(), target); }
        assertTrue(refused.get());
        assertTrue(PortableMappingBundle.load(target).complete());
    }

    @Test
    void unscopedThreadInterruptionStopsBeforeWriting() throws Exception {
        Path target = temporary.resolve("interrupted");
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class, () -> PortableMappingBundle.export(inputs(), target)); }
        finally { Thread.interrupted(); }
        assertFalse(Files.exists(target));
    }

    @Test
    void malformedExportCleansTemporaryAfterSomePayloadsWereWritten() throws Exception {
        MappingInputs original = inputs();
        ApiIndex.ApiClass badType = new ApiIndex.ApiClass("bad/Api", 1, "java/lang/Object", List.of(),
                List.of(new ApiIndex.ApiMember("run", "not-a-descriptor", 1)), List.of());
        MappingInputs malformed = new MappingInputs(original.legacyOfficial(), original.targetOfficial(), original.legacySrg(),
                original.targetSrg(), new ApiIndex(Map.of(badType.name(), badType)), original.targetApi(),
                original.sources(), original.diagnostics(), true);
        assertThrows(IOException.class, () -> PortableMappingBundle.export(malformed, temporary.resolve("bad")));
        try (var children = Files.list(temporary)) { assertEquals(0, children.count()); }
    }

    @Test
    void rejectsLinksWithoutFollowingThemWhenPlatformSupportsCreation() throws Exception {
        Path bundle = bundle();
        Path alias = temporary.resolve("alias");
        try { Files.createSymbolicLink(alias, bundle); }
        catch (IOException | UnsupportedOperationException unavailable) {
            assumeTrue(false, "Native symbolic-link creation unavailable: " + unavailable.getClass().getSimpleName());
        }
        assertThrows(IOException.class, () -> PortableMappingBundle.load(alias));
    }

    @Test
    void rehashedInvalidMagicCountDuplicateClassAndTrailingBytesStillFail() throws Exception {
        Path bundle = bundle();
        Path api = bundle.resolve("legacy-api.lbapi");
        byte[] valid = Files.readAllBytes(api);
        byte[] magic = valid.clone();
        magic[0] = 0;
        replacePayload(bundle, "legacy-api.lbapi", magic);
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
        byte[] oversizedCount = valid.clone();
        ByteBuffer.wrap(oversizedCount).putInt(8, Integer.MAX_VALUE);
        replacePayload(bundle, "legacy-api.lbapi", oversizedCount);
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
        byte[] classBytes = Arrays.copyOfRange(valid, 12, valid.length);
        ByteArrayOutputStream doubled = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(doubled)) {
            out.writeInt(PortableIndexCodec.API_MAGIC);
            out.writeInt(1);
            out.writeInt(2);
            out.write(classBytes);
            out.write(classBytes);
        }
        replacePayload(bundle, "legacy-api.lbapi", doubled.toByteArray());
        assertTrue(assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle)).getMessage().contains("Duplicate"));
        replacePayload(bundle, "legacy-api.lbapi", Arrays.copyOf(valid, valid.length + 1));
        assertTrue(assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle)).getMessage().contains("Trailing"));
    }

    @Test
    void rehashedInvalidDescriptorAndUtf8AreRejectedAsData() throws Exception {
        Path bundle = bundle();
        byte[] valid = Files.readAllBytes(bundle.resolve("legacy-api.lbapi"));
        byte[] malformed = valid.clone();
        int descriptor = new String(malformed, StandardCharsets.UTF_8).indexOf("()V");
        assertTrue(descriptor >= 0);
        malformed[descriptor + 2] = 'Q';
        replacePayload(bundle, "legacy-api.lbapi", malformed);
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
        malformed = valid.clone();
        malformed[16] = (byte) 0xff;
        replacePayload(bundle, "legacy-api.lbapi", malformed);
        assertThrows(IOException.class, () -> PortableMappingBundle.load(bundle));
    }

    @Test
    void globalRecordBudgetIsEnforcedBeforeAllocation() throws Exception {
        PortableIndexCodec.Budget budget = new PortableIndexCodec.Budget();
        budget.consume(PortableIndexCodec.MAX_RECORDS);
        assertThrows(IOException.class, () -> budget.consume(1));
    }

    private Path bundle() throws Exception { return PortableMappingBundle.export(inputs(), temporary.resolve("bundle")); }

    static MappingInputs inputs() throws IOException {
        IMappingFile official = mapping("fixture.Api -> a:\n    int count -> b\n    void run() -> c\n    void <init>() -> <init>\n");
        IMappingFile srg = mapping("a fixture/Api\n\tb field_123_count\n\tc ()V func_123_run\n");
        ApiIndex.ApiClass api = new ApiIndex.ApiClass("fixture/Api", 1, "java/lang/Object", List.of("fixture/Marker"),
                List.of(new ApiIndex.ApiMember("run", "()V", 1), new ApiIndex.ApiMember("<init>", "()V", 1)),
                List.of(new ApiIndex.ApiMember("count", "I", 2)));
        List<MappingSource> sources = new ArrayList<>();
        for (String side : List.of("legacy", "target")) {
            for (String kind : List.of("clientMappings", "serverMappings", "srg", "apiJars[0]")) {
                sources.add(new MappingSource(side + "." + kind, "0".repeat(64), "LOCAL_PROFILE"));
            }
        }
        ApiIndex index = new ApiIndex(Map.of(api.name(), api));
        return new MappingInputs(official, official, srg, srg, index, index, sources,
                List.of("LOCAL_PROFILE: synthetic declarations; no game classes"), true);
    }

    private static IMappingFile mapping(String text) throws IOException {
        return IMappingFile.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> names() {
        List<String> names = new ArrayList<>(PortableMappingBundle.FILES);
        names.add("manifest.json");
        return names;
    }

    private static void assertSortedObjectKeys(Object value) {
        if (value instanceof Map<?, ?> object) {
            List<String> actual = object.keySet().stream().map(key -> (String) key).toList();
            assertEquals(actual.stream().sorted().toList(), actual, "Every nested manifest object must use stable key order");
            object.values().forEach(PortableMappingBundleTest::assertSortedObjectKeys);
        } else if (value instanceof List<?> list) {
            list.forEach(PortableMappingBundleTest::assertSortedObjectKeys);
        }
    }

    private static Map<String, Object> manifest(Path bundle) throws Exception {
        return castMap(BoundedJsonParser.parse(Files.readString(bundle.resolve("manifest.json"))));
    }

    private static void manifest(Path bundle, Map<String, Object> tree) throws IOException {
        Files.writeString(bundle.resolve("manifest.json"), new CanonicalReportWriter().toJsonTree(tree));
    }

    private static Map<String, Object> castMap(Object value) {
        assertInstanceOf(Map.class, value);
        Map<String, Object> result = new HashMap<>();
        ((Map<?, ?>) value).forEach((key, item) -> result.put((String) key, item));
        return result;
    }

    private static void replacePayload(Path bundle, String name, byte[] bytes) throws Exception {
        Files.write(bundle.resolve(name), bytes);
        Map<String, Object> manifest = manifest(bundle);
        List<Object> files = new ArrayList<>();
        for (Object value : (List<?>) manifest.get("files")) {
            Map<String, Object> entry = castMap(value);
            if (name.equals(entry.get("name"))) {
                entry.put("bytes", bytes.length);
                entry.put("sha256", PortableMappingBundle.hash(bundle.resolve(name), PortableMappingBundle.MAX_FILE_BYTES));
            }
            files.add(entry);
        }
        manifest.put("files", files);
        manifest(bundle, manifest);
    }
}
