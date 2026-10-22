package dev.legacybridge.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.legacybridge.runtime.compat.AdapterBindings;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.AdapterSelection;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Binding validation needs no initialized Minecraft registry and never loads supplied implementation names. */
class AdapterBindingsTest {
    private static final Gson JSON = new Gson();
    private static final SimpleContentPlan PLAN = new SimpleContentPlan("legacybridge_example", "1.0.0",
            List.of(new SimpleContentPlan.ItemDefinition("bridge_ingot", null,
                            new SimpleContentPlan.ItemProperties(32, 0, false, "COMMON", "MISC")),
                    new SimpleContentPlan.ItemDefinition("bridge_block", "legacybridge_example:bridge_block",
                            new SimpleContentPlan.ItemProperties(64, 0, false, "COMMON", "BUILDING_BLOCKS"))),
            List.of(new SimpleContentPlan.BlockDefinition("bridge_block",
                    new SimpleContentPlan.BlockProperties("STONE", 2, 6, "STONE", false, false))), List.of());

    @Test
    void acceptsExactCatalogBindingsForTheFixtureWithoutLoadingGameClasses() {
        assertDoesNotThrow(() -> AdapterBindings.verify(bytes(bindings()), PLAN));
        var empty = new SimpleContentPlan("legacybridge_example", "1.0.0", List.of(), List.of(), List.of());
        assertDoesNotThrow(() -> AdapterBindings.verify(bytes(bindings(empty)), empty));
    }

    @Test
    void readsOnlyTheExactModScopedResourceAndRejectsItsAbsence() {
        String expected = "META-INF/legacybridge/legacybridge_example/adapters.json";
        var loader = new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {
                assertEquals(expected, name);
                return new ByteArrayInputStream(bytes(bindings()));
            }
        };
        assertDoesNotThrow(() -> AdapterBindings.verify(loader, PLAN.modId(), PLAN));
        assertThrows(IOException.class, () -> AdapterBindings.verify(new ClassLoader(null) { }, PLAN.modId(), PLAN));
    }

    @Test
    void rejectsMissingExtraAndUnknownRootFields() {
        for (String field : List.of("schemaVersion", "runtimeVersion", "adapters")) {
            JsonObject altered = bindings();
            altered.remove(field);
            reject(altered);
        }
        JsonObject altered = bindings();
        altered.addProperty("execute", false);
        reject(altered);
    }

    @Test
    void rejectsMismatchedRuntimeAndSchemaVersions() {
        JsonObject altered = bindings();
        altered.addProperty("runtimeVersion", "0.1.0-SNAPSHOT");
        reject(altered);
        altered = bindings();
        altered.addProperty("schemaVersion", 2);
        reject(altered);
        altered = bindings();
        altered.addProperty("schemaVersion", "1");
        reject(altered);
    }

    @Test
    void rejectsUnknownAndUnneededAdaptersEvenIfTheClassLooksPlausible() {
        JsonObject altered = bindings();
        first(altered).addProperty("id", "entity.invented.v1");
        first(altered).addProperty("implementationClass", "java.lang.Runtime");
        reject(altered);
        altered = bindings();
        altered.getAsJsonArray("adapters").add(JSON.toJsonTree(AdapterCatalog.require(AdapterCatalog.SOUNDS).referenceTree()));
        reject(altered);
    }

    @Test
    void rejectsMissingRequiredAdapterAndDuplicateAdapterIdentity() {
        JsonObject altered = bindings();
        altered.getAsJsonArray("adapters").remove(0);
        reject(altered);
        altered = bindings();
        altered.getAsJsonArray("adapters").add(first(altered).deepCopy());
        reject(altered);
    }

    @Test
    void rejectsWrongRevisionOwnerMethodOrDescriptorInsteadOfReflectingSuppliedValues() {
        for (String field : List.of("implementationClass", "method", "descriptor")) {
            JsonObject altered = bindings();
            first(altered).addProperty(field, "untrusted.invalid");
            reject(altered);
        }
        JsonObject altered = bindings();
        first(altered).addProperty("revision", 2);
        reject(altered);
        altered = bindings();
        first(altered).addProperty("revision", "1");
        reject(altered);
    }

    @Test
    void rejectsMissingAndUnknownAdapterMembers() {
        for (String field : List.of("id", "revision", "implementationClass", "method", "descriptor")) {
            JsonObject altered = bindings();
            first(altered).remove(field);
            reject(altered);
        }
        JsonObject altered = bindings();
        first(altered).addProperty("optional", true);
        reject(altered);
    }

    @Test
    void rejectsDuplicateJsonKeysAtBothObjectLevelsAndTrailingDocuments() {
        String valid = JSON.toJson(bindings());
        reject(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"));
        reject(valid.replace("\"revision\":1", "\"revision\":1,\"revision\":1"));
        reject(valid + "{}");
    }

    @Test
    void rejectsMalformedEncodingOversizedInputAndWrongJsonTypes() {
        assertThrows(IOException.class, () -> AdapterBindings.verify(new byte[65_537], PLAN));
        assertThrows(IOException.class, () -> AdapterBindings.verify(new byte[]{(byte) 0xc3, 0x28}, PLAN));
        reject("[]");
        JsonObject altered = bindings();
        altered.addProperty("adapters", "not an array");
        reject(altered);
    }

    private static JsonObject bindings() { return bindings(PLAN); }

    private static JsonObject bindings(SimpleContentPlan plan) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", AdapterCatalog.SCHEMA_VERSION);
        root.addProperty("runtimeVersion", AdapterCatalog.RUNTIME_VERSION);
        JsonArray adapters = new JsonArray();
        AdapterSelection.required(!plan.items().isEmpty(), !plan.blocks().isEmpty(),
                plan.items().stream().anyMatch(item -> item.block() != null), !plan.sounds().isEmpty(),
                plan.items().stream().anyMatch(item -> item.properties().creativeTab() != null))
                .forEach(id -> adapters.add(JSON.toJsonTree(AdapterCatalog.require(id).referenceTree())));
        root.add("adapters", adapters);
        return root;
    }

    private static JsonObject first(JsonObject root) { return root.getAsJsonArray("adapters").get(0).getAsJsonObject(); }
    private static byte[] bytes(JsonObject root) { return JSON.toJson(root).getBytes(StandardCharsets.UTF_8); }
    private static void reject(JsonObject root) { assertThrows(IOException.class, () -> AdapterBindings.verify(bytes(root), PLAN)); }
    private static void reject(String text) { assertThrows(IOException.class, () -> AdapterBindings.verify(text.getBytes(StandardCharsets.UTF_8), PLAN)); }
}
