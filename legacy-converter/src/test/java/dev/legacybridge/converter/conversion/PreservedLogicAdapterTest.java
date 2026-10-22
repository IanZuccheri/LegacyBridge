package dev.legacybridge.converter.conversion;

import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import dev.legacybridge.runtime.compat.logic.PreservedLogicAdapter;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreservedLogicAdapterTest {
    @Test void onlyObjectTypeComponentsChange() {
        assertEquals("([[Ltarget/Thing;IJ)Ltarget/Thing;", PreservedLogicAdapter.remapDescriptor("([[Lold/Thing;IJ)Lold/Thing;", Map.of("old/Thing", "target/Thing")));
        assertEquals("(I)V", PreservedLogicAdapter.remapDescriptor("(I)V", Map.of()));
    }
    @Test void invalidDescriptorsAndNonTypesRefuse() {
        for (String descriptor : new String[]{"", "V", "[V", "(V)V", "(I)", "(I)VX", "Lthing", "(Ljava//Broken;)V", "[".repeat(256)+"I"})
            assertThrows(UnsupportedCompatibilityException.class, () -> PreservedLogicAdapter.remapDescriptor(descriptor, Map.of()), descriptor);
        assertThrows(UnsupportedCompatibilityException.class, () -> PreservedLogicAdapter.remapDescriptor("LOld;", Map.of("Old", "I;J")));
    }
    @Test void serializedProfileBindingsHaveTheirOwnImplementedOperation() {
        assertEquals(TransformationBindings.forLogic().bindingTree(), PreservedLogicAdapter.bindingTree());
    }
    @Test void newMarkerDoesNotChangeOldProfileManifest() throws Exception {
        byte[] old = ModMetadataAdapter.targetManifest("1.0.0");
        assertEquals("logic-preserving-v1", new Manifest(new ByteArrayInputStream(PreservedLogicAdapter.targetManifest("1.0.0"))).getMainAttributes().getValue("LegacyBridge-Profile"));
        assertArrayEquals(old, ModMetadataAdapter.targetManifest("1.0.0"));
        assertEquals("simple-content-v1", new Manifest(new ByteArrayInputStream(old)).getMainAttributes().getValue("LegacyBridge-Profile"));
    }
}
