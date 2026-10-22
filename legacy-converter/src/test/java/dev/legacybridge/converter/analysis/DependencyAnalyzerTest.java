package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.bytecode.ArchiveReferenceInventory;
import dev.legacybridge.converter.bytecode.BytecodeDefinitionIndex;
import dev.legacybridge.converter.bytecode.BytecodeReference;
import dev.legacybridge.converter.bytecode.BytecodeReferenceInventory;
import dev.legacybridge.converter.bytecode.BytecodeReferenceKind;
import dev.legacybridge.converter.report.MetadataSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyAnalyzerTest {
    @Test
    void mojangLibrariesArePlatformReferencesRatherThanThirdPartyDependencies() {
        BytecodeReference mojang = type("com/mojang/blaze3d/matrix/MatrixStack");
        BytecodeReference external = type("org/example/library/Widget");
        ArchiveReferenceInventory inventory = new ArchiveReferenceInventory(
                new BytecodeReferenceInventory(
                        2,
                        Map.of(BytecodeReferenceKind.TYPE, 2L),
                        Set.of(mojang, external),
                        false
                ),
                BytecodeDefinitionIndex.EMPTY,
                List.of(),
                false
        );

        DependencyAnalyzer.Result result = new DependencyAnalyzer().analyze(
                MetadataSummary.missing(),
                inventory
        );

        assertEquals(1, result.dependencies().size());
        assertEquals("org.example.library", result.dependencies().get(0).identifier());
        assertFalse(result.truncated());
    }

    @Test
    void inventoriesManifestDependenciesWithoutResolvingThem() {
        DependencyAnalyzer.Result result = new DependencyAnalyzer().analyze(
                MetadataSummary.missing(),
                ArchiveReferenceInventory.EMPTY,
                Map.of(
                        "Class-Path", "libraries/helper.jar https://example.invalid/api.jar",
                        "ContainedDeps", "embedded-a.jar,embedded-b.jar"
                )
        );

        assertEquals(4, result.dependencies().size());
        assertTrue(result.dependencies().stream().anyMatch(value ->
                value.source().equals("MANIFEST_CLASS_PATH")
                        && value.identifier().equals("libraries/helper.jar")));
        assertTrue(result.dependencies().stream().anyMatch(value ->
                value.source().equals("MANIFEST_CONTAINED_DEPS")
                        && value.identifier().equals("embedded-b.jar")));
        assertFalse(result.truncated());
    }

    private static BytecodeReference type(String owner) {
        return BytecodeReference.type(owner, "L" + owner + ";");
    }
}
