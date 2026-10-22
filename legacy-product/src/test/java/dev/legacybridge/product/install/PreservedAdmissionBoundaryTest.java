package dev.legacybridge.product.install;

import java.nio.file.*;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

class PreservedAdmissionBoundaryTest {
    @Test @EnabledIfSystemProperty(named="legacybridge.admissionCandidate",matches=".+")
    void independentReadbackDoesNotPromoteOldInstallerOrJournalToAcceptPreservedBodies() throws Exception {
        Path candidate=Path.of(System.getProperty("legacybridge.admissionCandidate")); byte[] before=Files.readAllBytes(candidate);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(before));
        assertEquals("78de0e2fd62ed83315518a1980ae0738b354531736a5eea73195d332aecc186d",hash);
        var failure=assertThrows(java.io.IOException.class,()->StagedArtifactValidator.validate(candidate,hash));
        assertTrue(failure.getMessage().contains("profile marker"),failure.getMessage());
        assertArrayEquals(before,Files.readAllBytes(candidate));
    }
}
