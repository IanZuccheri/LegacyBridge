package dev.legacybridge.converter.mapping.builtin;

import dev.legacybridge.converter.conversion.SimpleContentConverter;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Development-only real-artifact comparison, not a runtime cache dependency or execution of legacy classes. */
@EnabledIfSystemProperty(named = "legacybridge.verifyMappingEnvironment", matches = "true")
class EmbeddedSimpleContentEnvironmentTest {
    @TempDir Path temporary;

    @Test void everyRetainedDeclarationAndAliasMatchesThePinnedRealSourceInputs() throws Exception {
        var full = fullInputs();
        assertTrue(full.complete(), full.diagnostics().toString());
        var embedded = EmbeddedSimpleContentData.load();
        for (var provenance : embedded.sources()) {
            if (!provenance.id().startsWith("legacy.")) continue;
            assertTrue(full.sources().stream().anyMatch(actual -> actual.id().equals(provenance.id())
                    && actual.sha256().equals(provenance.sha256())), "Source artifact drift: " + provenance.id());
        }
        for (var declaration : embedded.legacyApi().classes().values()) {
            var actual = full.legacyApi().getClass(declaration.name());
            assertNotNull(actual, declaration.name());
            assertEquals(actual.access(), declaration.access(), declaration.name());
            assertEquals(actual.superName(), declaration.superName(), declaration.name());
            assertEquals(actual.interfaces(), declaration.interfaces(), declaration.name());
            assertTrue(actual.methods().containsAll(declaration.methods()), declaration.name() + " method declaration drift");
            assertTrue(actual.fields().containsAll(declaration.fields()), declaration.name() + " field declaration drift");
        }
        for (var definition : embedded.legacyOfficial().getClasses()) {
            var actual = full.legacyOfficial().getClass(definition.getOriginal());
            if (definition.getOriginal().startsWith("net/minecraftforge/")) {
                assertNull(actual, "Forge identifiers must not be inferred through Mojang mappings");
                assertEquals(definition.getOriginal(), definition.getMapped());
                definition.getMethods().forEach(method -> assertEquals(method.getOriginal(), method.getMapped()));
                definition.getFields().forEach(field -> assertEquals(field.getOriginal(), field.getMapped()));
                continue;
            }
            assertNotNull(actual, definition.getOriginal());
            var srg = full.legacySrg().getClass(actual.getMapped());
            assertNotNull(srg, definition.getOriginal());
            assertEquals(srg.getMapped(), definition.getMapped());
            for (var method : definition.getMethods()) {
                var actualMethod = actual.getMethod(method.getOriginal(), method.getDescriptor());
                assertNotNull(actualMethod, definition.getOriginal() + "." + method.getOriginal() + method.getDescriptor());
                assertEquals(srg.remapMethod(actualMethod.getMapped(), actualMethod.getMappedDescriptor()), method.getMapped());
            }
            for (var field : definition.getFields()) {
                var actualField = actual.getField(field.getOriginal());
                assertNotNull(actualField, definition.getOriginal() + "." + field.getOriginal());
                assertEquals(actualField.getDescriptor(), field.getDescriptor());
                assertEquals(srg.remapField(actualField.getMapped()), field.getMapped());
            }
        }
    }

    @Test void realFixtureConversionRemainsByteIdenticalToTheExistingFullEnvironmentPath() throws Exception {
        Path fixture = Path.of(System.getProperty("legacybridge.fixtureRoot"), "simple-content-1.16.5", "build", "libs",
                "legacybridge-example-1.16.5-1.0.0.jar");
        assertTrue(Files.isRegularFile(fixture), "Build the pinned original fixture before this explicit development validation");
        byte[] original = Files.readAllBytes(fixture);
        var converter = new SimpleContentConverter();
        Path first = temporary.resolve("embedded.jar"), second = temporary.resolve("full.jar");
        var embedded = converter.convert(fixture, first, EmbeddedSimpleContentData.load());
        var full = converter.convert(fixture, second, fullInputs());
        assertTrue(embedded.converted(), embedded.report().toString());
        assertTrue(full.converted(), full.report().toString());
        assertArrayEquals(Files.readAllBytes(second), Files.readAllBytes(first));
        assertArrayEquals(original, Files.readAllBytes(fixture));
        for (String field : List.of("adapterBindings", "transformations", "transformationLog", "bytecodeValidation",
                "preservedResources", "itemCount", "blockCount", "soundCount", "runtimeVersion", "targetForge"))
            assertEquals(full.report().get(field), embedded.report().get(field), field);
        assertEquals(false, embedded.report().get("bytecodeExecuted"));
        assertEquals(false, ((java.util.Map<?, ?>) embedded.report().get("bytecodeValidation")).get("jvmLinkageVerified"));
    }

    private static MappingInputs fullInputs() throws IOException {
        String selected = System.getProperty("legacybridge.logicMappingProfile", "");
        Path profile = selected.isBlank() ? Path.of(System.getProperty("legacybridge.fixtureRoot")).getParent()
                .resolve("build/logic-preserving-v1/mapping-profile.properties") : Path.of(selected);
        // This explicit development profile includes the separate source eventbus declaration JAR.
        // The player's embedded provider does not call this loader or discover these paths.
        return new MappingInputsLoader().load(profile);
    }
}
