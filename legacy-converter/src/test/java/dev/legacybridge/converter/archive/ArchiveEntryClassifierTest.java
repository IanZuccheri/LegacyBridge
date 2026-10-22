package dev.legacybridge.converter.archive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveEntryClassifierTest {
    @Test
    void recognizesBothCommonMixinConfigurationNamingConventions() {
        assertTrue(ArchiveEntryClassifier.isMixinConfiguration("mixins.example.json"));
        assertTrue(ArchiveEntryClassifier.isMixinConfiguration("example.mixins.json"));
        assertTrue(ArchiveEntryClassifier.isMixinConfiguration("example.mixin.json"));
        assertTrue(ArchiveEntryClassifier.isMixinConfiguration("config/MIXINS.EXAMPLE.JSON"));
    }

    @Test
    void doesNotTreatMixinRefmapsAsConfigurations() {
        assertFalse(ArchiveEntryClassifier.isMixinConfiguration("mixins.example.refmap.json"));
        assertFalse(ArchiveEntryClassifier.isMixinConfiguration("ordinary.json"));
    }
}
