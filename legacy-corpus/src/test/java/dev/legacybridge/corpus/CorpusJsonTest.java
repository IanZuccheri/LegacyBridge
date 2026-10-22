package dev.legacybridge.corpus;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusJsonTest {
    @TempDir Path temporary;

    @Test void canonicalNestedObjectOrderAndCreateNewPreserveExistingBytes() throws IOException {
        var nested = new LinkedHashMap<String, Object>(); nested.put("z", 2); nested.put("a", 1);
        Path file = temporary.resolve("record.json");
        CorpusJson.writeCanonicalCreateNew(file, Map.of("nested", nested));
        byte[] before = Files.readAllBytes(file);
        assertTrue(new String(before, StandardCharsets.UTF_8).indexOf("\"a\"") < new String(before, StandardCharsets.UTF_8).indexOf("\"z\""));
        assertEquals("1", ((Map<?, ?>) CorpusJson.read(file).get("nested")).get("a").toString());
        assertThrows(FileAlreadyExistsException.class, () -> CorpusJson.writeCanonicalCreateNew(file, Map.of("different", true)));
        assertArrayEquals(before, Files.readAllBytes(file));
        try (var entries = Files.list(temporary)) { assertEquals(1, entries.count()); }
    }

    @Test void rejectsMalformedUtf8RatherThanReplacementCharacters() throws IOException {
        Path file = temporary.resolve("malformed.json");
        Files.write(file, new byte[] {'{', '"', 'x', '"', ':', '"', (byte) 0xc3, (byte) 0x28, '"', '}'});
        assertThrows(IOException.class, () -> CorpusJson.read(file));
    }

    @Test void rejectsDuplicateKeysNonObjectRootsAndOverBudgetFiles() throws IOException {
        Path duplicate = temporary.resolve("duplicate.json"); Files.writeString(duplicate, "{\"x\":1,\"x\":2}");
        Path array = temporary.resolve("array.json"); Files.writeString(array, "[]");
        Path large = temporary.resolve("large.json"); Files.write(large, new byte[CorpusJson.MAX_BYTES + 1]);
        assertThrows(IOException.class, () -> CorpusJson.read(duplicate));
        assertThrows(IOException.class, () -> CorpusJson.read(array));
        assertThrows(IOException.class, () -> CorpusJson.read(large));
    }

    @Test void refusesCyclicAndUnsupportedValuesBeforePublishingAnything() throws IOException {
        Map<String, Object> cyclic = new LinkedHashMap<>(); cyclic.put("self", cyclic);
        assertThrows(IOException.class, () -> CorpusJson.canonicalJson(cyclic));
        assertThrows(IOException.class, () -> CorpusJson.canonicalJson(Map.of("notJson", Path.of("unused"))));
        assertThrows(IOException.class, () -> CorpusJson.canonicalJson(Map.of("notFinite", Double.NaN)));
        assertThrows(IOException.class, () -> CorpusJson.canonicalJson(Map.of("tooLong", "a".repeat(16_385))));
        List<Object> nodes = new ArrayList<>(); for (int i = 0; i < 20_001; i++) nodes.add(true);
        assertThrows(IOException.class, () -> CorpusJson.canonicalJson(Map.of("tooMany", nodes)));
    }

    @Test void refusesMissingParentAndDirectoryAsFile() throws IOException {
        assertThrows(IOException.class, () -> CorpusJson.writeCanonicalCreateNew(temporary.resolve("missing/result.json"), Map.of("x", 1)));
        assertThrows(IOException.class, () -> CorpusJson.read(temporary));
        assertFalse(Files.exists(temporary.resolve("missing")));
    }
}
