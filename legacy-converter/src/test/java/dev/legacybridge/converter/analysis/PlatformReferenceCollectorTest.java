package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.bytecode.BytecodeDefinitionIndex;
import dev.legacybridge.converter.bytecode.BytecodeReference;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformReferenceCollectorTest {
    @Test
    void mojangAndMinecraftNamespacesShareTheMinecraftReferenceSummary() {
        PlatformReferenceCollector collector = new PlatformReferenceCollector();
        collector.accept(type("com/mojang/blaze3d/matrix/MatrixStack"));
        collector.accept(type("net/minecraft/world/World"));
        collector.accept(type("net/minecraftforge/common/MinecraftForge"));

        var minecraft = collector.minecraft(BytecodeDefinitionIndex.EMPTY);
        var forge = collector.forge(BytecodeDefinitionIndex.EMPTY);

        assertEquals(2, minecraft.occurrenceCount());
        assertEquals(2, minecraft.references().size());
        assertTrue(minecraft.externalityComplete());
        assertEquals(1, forge.occurrenceCount());
    }

    @Test
    void bundledMojangOwnerIsNotReportedAsExternal() {
        PlatformReferenceCollector collector = new PlatformReferenceCollector();
        collector.accept(type("com/mojang/math/Matrix4f"));

        var definitions = new BytecodeDefinitionIndex(
                1,
                Set.of("com/mojang/math/Matrix4f"),
                false
        );
        var summary = collector.minecraft(definitions);

        assertEquals(1, summary.occurrenceCount());
        assertTrue(summary.references().isEmpty());
        assertFalse(summary.externalityComplete());
    }

    @Test
    void retainedPlatformReferencesAreBoundedWhileCountsRemainExact() {
        PlatformReferenceCollector collector = new PlatformReferenceCollector();
        String longOwner = "com/mojang/" + "verylong/".repeat(300);
        collector.accept(type(longOwner));
        for (int index = 0; index < PlatformReferenceCollector.SAMPLE_ATTEMPT_LIMIT; index++) {
            collector.accept(type("net/minecraft/generated/Type" + index));
        }

        var summary = collector.minecraft(BytecodeDefinitionIndex.EMPTY);

        assertEquals(PlatformReferenceCollector.SAMPLE_ATTEMPT_LIMIT + 1L,
                summary.occurrenceCount());
        assertTrue(summary.referencesTruncated());
        assertTrue(summary.references().stream()
                .allMatch(reference -> reference.owner().length()
                        <= BytecodeReference.RETAINED_OWNER_CHARS));
    }

    private static BytecodeReference type(String owner) {
        return BytecodeReference.type(owner, "L" + owner + ";");
    }
}
