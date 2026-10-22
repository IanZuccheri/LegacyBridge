package dev.legacybridge.converter.archive;

import dev.legacybridge.converter.testutil.TestJarBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeJarReaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void inventoriesAndHashesWithoutExtracting() throws Exception {
        Path input = TestJarBuilder.write(
                temporaryDirectory.resolve("fixture.jar"),
                Map.of("META-INF/mods.toml", "modLoader=\"javafml\"".getBytes(StandardCharsets.UTF_8))
        );
        List<String> visited = new ArrayList<>();

        ArchiveScan scan = new SafeJarReader().scan(input, new JarEntryVisitor() {
            @Override
            public boolean requiresBytes(String entryName) {
                return entryName.endsWith("mods.toml");
            }

            @Override
            public void visit(String entryName, byte[] contents) {
                visited.add(entryName + ":" + contents.length);
            }
        });

        assertEquals(1, scan.archive().entryCount());
        assertEquals(List.of("META-INF/mods.toml:19"), visited);
        assertTrue(scan.input().sha256().matches("[0-9a-f]{64}"));
        assertEquals("fixture.jar", scan.input().fileName());
    }

    @Test
    void rejectsTraversalEntry() throws Exception {
        Path input = TestJarBuilder.write(
                temporaryDirectory.resolve("traversal.jar"),
                Map.of("../outside.class", new byte[]{1, 2, 3})
        );

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, noOpVisitor())
        );

        assertEquals("PATH_TRAVERSAL", exception.reasonCode());
    }

    @Test
    void rejectsEntryNamesWithTerminalControlCharacters() throws Exception {
        Path input = TestJarBuilder.write(
                temporaryDirectory.resolve("control-name.jar"),
                Map.of("unsafe\nname.class", new byte[]{1, 2, 3})
        );

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, noOpVisitor())
        );

        assertEquals("UNSAFE_ENTRY_NAME", exception.reasonCode());
    }

    @Test
    void rejectsMalformedArchiveAndNonJarExtension() throws Exception {
        Path malformed = temporaryDirectory.resolve("broken.jar");
        Files.writeString(malformed, "not a zip", StandardCharsets.UTF_8);
        InputRejectedException malformedError = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(malformed, noOpVisitor())
        );
        assertEquals("MALFORMED_ZIP", malformedError.reasonCode());

        Path wrongExtension = temporaryDirectory.resolve("archive.zip");
        TestJarBuilder.write(wrongExtension, Map.of("value.txt", new byte[]{1}));
        InputRejectedException extensionError = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(wrongExtension, noOpVisitor())
        );
        assertEquals("INPUT_NOT_JAR", extensionError.reasonCode());
    }

    @Test
    void specialEntrySamplesAreBoundedPerRiskKindWithoutLosingTotalCount() throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        for (int index = 0; index < 10; index++) {
            entries.put("META-INF/services/example.Service" + index, new byte[0]);
        }
        entries.put("META-INF/coremods.json", "{}".getBytes(StandardCharsets.UTF_8));
        Path input = TestJarBuilder.write(temporaryDirectory.resolve("specials.jar"), entries);

        ArchiveScan scan = new SafeJarReader().scan(input, noOpVisitor());

        assertEquals(11, scan.archive().specialEntryCount());
        assertEquals(ArchivePolicy.MAX_SPECIAL_ENTRY_SAMPLES_PER_KIND + 1,
                scan.archive().specialEntries().size());
        assertTrue(scan.archive().specialEntries().contains("META-INF/coremods.json"));
        assertTrue(scan.archive().specialEntriesTruncated());
        assertEquals(10L, scan.archive().specialEntryCounts().get("SERVICE_PROVIDER"));
        assertEquals(1L, scan.archive().specialEntryCounts().get("COREMOD"));
    }

    @Test
    void rejectsExcessiveCentralDirectoryCountBeforeOpeningZipFile() throws Exception {
        ByteBuffer fakeEndRecord = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        fakeEndRecord.putInt(0x06054b50);
        fakeEndRecord.putShort((short) 0);
        fakeEndRecord.putShort((short) 0);
        fakeEndRecord.putShort((short) (ArchivePolicy.MAX_ENTRY_COUNT + 1));
        fakeEndRecord.putShort((short) (ArchivePolicy.MAX_ENTRY_COUNT + 1));
        fakeEndRecord.putInt(0);
        fakeEndRecord.putInt(0);
        fakeEndRecord.putShort((short) 0);
        Path input = temporaryDirectory.resolve("too-many.jar");
        Files.write(input, fakeEndRecord.array());

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, noOpVisitor())
        );

        assertEquals("ENTRY_COUNT_LIMIT", exception.reasonCode());
    }

    @Test
    void rejectsExcessiveCentralDirectoryByteBudgetBeforeOpeningZipFile() throws Exception {
        ByteBuffer fakeEndRecord = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        fakeEndRecord.putInt(0x06054b50);
        fakeEndRecord.putShort((short) 0);
        fakeEndRecord.putShort((short) 0);
        fakeEndRecord.putShort((short) 0);
        fakeEndRecord.putShort((short) 0);
        fakeEndRecord.putInt((int) (ArchivePolicy.MAX_CENTRAL_DIRECTORY_BYTES + 1));
        fakeEndRecord.putInt(0);
        fakeEndRecord.putShort((short) 0);
        Path input = temporaryDirectory.resolve("central-directory-too-large.jar");
        Files.write(input, fakeEndRecord.array());

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, noOpVisitor())
        );

        assertEquals("CENTRAL_DIRECTORY_LIMIT", exception.reasonCode());
    }

    @Test
    void rejectsTooManyMixinConfigurationCandidates() throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        for (int index = 0; index <= ArchivePolicy.MAX_MIXIN_CONFIGURATION_COUNT; index++) {
            entries.put("mixins.fixture" + String.format("%03d", index) + ".json", "{}".getBytes(StandardCharsets.UTF_8));
        }
        Path input = TestJarBuilder.write(temporaryDirectory.resolve("many-mixins.jar"), entries);

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, noOpVisitor())
        );

        assertEquals("MIXIN_CONFIG_COUNT_LIMIT", exception.reasonCode());
    }

    @Test
    void rejectsAggregateEntryNameBudget() throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        for (int index = 0; index < 1_000; index++) {
            entries.put("entry" + String.format("%04d", index) + "-" + "x".repeat(4_050), new byte[0]);
        }
        Path input = TestJarBuilder.write(temporaryDirectory.resolve("long-names.jar"), entries);

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, noOpVisitor())
        );

        assertEquals("ENTRY_NAME_BUDGET", exception.reasonCode());
    }

    @Test
    void rejectsCentralDirectoryCountThatDisagreesWithParsedHeaders() throws Exception {
        Path input = TestJarBuilder.write(
                temporaryDirectory.resolve("count-mismatch.jar"),
                Map.of("value.txt", new byte[]{1})
        );
        byte[] bytes = Files.readAllBytes(input);
        int eocd = findSignatureBackward(bytes, 0x06054b50);
        ByteBuffer view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        view.putShort(eocd + 8, (short) 2);
        view.putShort(eocd + 10, (short) 2);
        Files.write(input, bytes);

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, noOpVisitor())
        );

        assertEquals("CENTRAL_DIRECTORY_MISMATCH", exception.reasonCode());
    }

    @Test
    void rejectsSelfExtractingPrefixOutsideStrictJarSubset() throws Exception {
        Path original = TestJarBuilder.write(
                temporaryDirectory.resolve("original.jar"),
                Map.of("value.txt", new byte[]{1})
        );
        byte[] jar = Files.readAllBytes(original);
        byte[] prefixed = new byte[jar.length + 4];
        prefixed[0] = 'S';
        prefixed[1] = 'T';
        prefixed[2] = 'U';
        prefixed[3] = 'B';
        System.arraycopy(jar, 0, prefixed, 4, jar.length);
        Path input = temporaryDirectory.resolve("prefixed.jar");
        Files.write(input, prefixed);

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, noOpVisitor())
        );

        assertEquals("NON_CANONICAL_ZIP_LAYOUT", exception.reasonCode());
    }

    @Test
    void rejectsCentralEntryWhoseStartingDiskIsNotZero() throws Exception {
        Path input = TestJarBuilder.write(
                temporaryDirectory.resolve("split-entry.jar"),
                Map.of("value.txt", new byte[]{1})
        );
        byte[] bytes = Files.readAllBytes(input);
        int centralHeader = findSignatureForward(bytes, 0x02014b50);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(centralHeader + 34, (short) 1);
        Files.write(input, bytes);

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, noOpVisitor())
        );

        assertEquals("MULTI_DISK_ZIP", exception.reasonCode());
    }

    @Test
    void acceptsCanonicalMandatoryZip64EndRecords() throws Exception {
        Path input = temporaryDirectory.resolve("empty-zip64.jar");
        Files.write(input, emptyZip64(true));

        ArchiveScan scan = new SafeJarReader().scan(input, noOpVisitor());

        assertEquals(0, scan.archive().entryCount());
    }

    @Test
    void rejectsOptionalZip64LayoutOutsideStrictSubset() throws Exception {
        Path input = temporaryDirectory.resolve("optional-zip64.jar");
        Files.write(input, emptyZip64(false));

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, noOpVisitor())
        );

        assertEquals("NON_CANONICAL_ZIP_LAYOUT", exception.reasonCode());
    }

    @Test
    void detectsOriginalMutationWhileAnalyzingStableSnapshot() throws Exception {
        Path input = TestJarBuilder.write(
                temporaryDirectory.resolve("mutable.jar"),
                Map.of("value.txt", new byte[]{1})
        );

        InputRejectedException exception = assertThrows(
                InputRejectedException.class,
                () -> new SafeJarReader().scan(input, new JarEntryVisitor() {
                    @Override
                    public void visitEntry(String entryName, boolean directory) throws java.io.IOException {
                        Files.writeString(input, "changed", StandardCharsets.UTF_8);
                    }

                    @Override
                    public boolean requiresBytes(String entryName) {
                        return false;
                    }

                    @Override
                    public void visit(String entryName, byte[] contents) {
                    }
                })
        );

        assertEquals("INPUT_CHANGED", exception.reasonCode());
    }

    private static int findSignatureBackward(byte[] bytes, int signature) {
        for (int index = bytes.length - 4; index >= 0; index--) {
            if (ByteBuffer.wrap(bytes, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() == signature) {
                return index;
            }
        }
        throw new AssertionError("ZIP signature not found");
    }

    private static byte[] emptyZip64(boolean useClassicSentinels) {
        ByteBuffer buffer = ByteBuffer.allocate(98).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0x06064b50);
        buffer.putLong(44);
        buffer.putShort((short) 45);
        buffer.putShort((short) 45);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putInt(0x07064b50);
        buffer.putInt(0);
        buffer.putLong(0);
        buffer.putInt(1);
        buffer.putInt(0x06054b50);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) (useClassicSentinels ? 0xffff : 0));
        buffer.putShort((short) (useClassicSentinels ? 0xffff : 0));
        buffer.putInt(useClassicSentinels ? 0xffffffff : 0);
        buffer.putInt(useClassicSentinels ? 0xffffffff : 0);
        buffer.putShort((short) 0);
        return buffer.array();
    }

    private static int findSignatureForward(byte[] bytes, int signature) {
        for (int index = 0; index <= bytes.length - 4; index++) {
            if (ByteBuffer.wrap(bytes, index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() == signature) {
                return index;
            }
        }
        throw new AssertionError("ZIP signature not found");
    }

    private static JarEntryVisitor noOpVisitor() {
        return new JarEntryVisitor() {
            @Override
            public boolean requiresBytes(String entryName) {
                return false;
            }

            @Override
            public void visit(String entryName, byte[] contents) {
            }
        };
    }
}
