package dev.legacybridge.converter.report;

import dev.legacybridge.converter.bytecode.ArchiveReferenceInventory;
import dev.legacybridge.converter.bytecode.BytecodeDefinitionIndex;
import dev.legacybridge.converter.bytecode.BytecodeReference;
import dev.legacybridge.converter.bytecode.BytecodeReferenceInventory;
import dev.legacybridge.converter.bytecode.BytecodeReferenceKind;
import dev.legacybridge.converter.bytecode.ClassReferenceDetail;
import dev.legacybridge.converter.content.ContentCategory;
import dev.legacybridge.converter.content.ContentCategorySummary;
import dev.legacybridge.converter.content.ContentInventory;
import dev.legacybridge.converter.content.ContentNamespaceSummary;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackagedReportSchemaTest {
    private static final String SCHEMA_PATH =
            "/META-INF/legacybridge/compatibility-report-v2.schema.json";

    @Test
    void comprehensiveCanonicalReportConformsToPackagedSchemaAndRootParity() throws Exception {
        Map<String, Object> schema = readSchema();
        Map<String, Object> output = canonicalReportTree();

        Map<String, Object> declaredProperties = JsonSchemaTestSupport.objectValue(
                schema.get("properties"));
        Set<String> required = schemaRequiredKeys(schema);
        assertEquals(declaredProperties.keySet(), output.keySet(),
                "writer and schema root properties drifted");
        assertEquals(required, output.keySet(),
                "every canonical root property must remain required");

        JsonSchemaTestSupport.validate(output, schema);
    }

    @Test
    void schemaRejectsWrongReferenceKindAndUnknownContentCategory() throws Exception {
        Map<String, Object> schema = readSchema();
        Map<String, Object> wrongReference = canonicalReportTree();
        Map<String, Object> classReferences = objectAt(wrongReference, "classReferences");
        Map<String, Object> firstClass = objectAt(listAt(classReferences, "classes").get(0));
        Map<String, Object> firstMethod = objectAt(listAt(firstClass, "methods").get(0));
        firstMethod.put("kind", "FIELD");

        assertThrows(AssertionError.class, () -> JsonSchemaTestSupport.validate(wrongReference, schema));

        Map<String, Object> unknownCategory = canonicalReportTree();
        Map<String, Object> categories = objectAt(objectAt(unknownCategory, "content"), "categories");
        categories.put("UNKNOWN_CATEGORY", Map.of(
                "entryCount", java.math.BigDecimal.ONE,
                "samples", List.of("unknown"),
                "samplesTruncated", false
        ));
        assertThrows(AssertionError.class, () -> JsonSchemaTestSupport.validate(unknownCategory, schema));
    }

    private static Map<String, Object> canonicalReportTree() {
        String json = new CanonicalReportWriter().toJson(comprehensiveReport());
        return JsonSchemaTestSupport.objectValue(JsonSchemaTestSupport.parse(json));
    }

    private static CompatibilityReport comprehensiveReport() {
        Set<BytecodeReference> allKinds = Set.of(
                BytecodeReference.hierarchy(BytecodeReferenceKind.SUPERCLASS,
                        "net/minecraft/tileentity/TileEntity"),
                BytecodeReference.hierarchy(BytecodeReferenceKind.INTERFACE,
                        "net/minecraftforge/common/capabilities/ICapabilityProvider"),
                BytecodeReference.member(BytecodeReferenceKind.FIELD,
                        "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                        "Lnet/minecraftforge/eventbus/api/IEventBus;"),
                BytecodeReference.member(BytecodeReferenceKind.METHOD,
                        "net/minecraftforge/fml/network/simple/SimpleChannel", "sendToServer",
                        "(Ljava/lang/Object;)V"),
                BytecodeReference.type("net/minecraft/world/World", "Lnet/minecraft/world/World;")
        );
        EnumMap<BytecodeReferenceKind, Long> counts = new EnumMap<>(BytecodeReferenceKind.class);
        for (BytecodeReferenceKind kind : BytecodeReferenceKind.values()) counts.put(kind, 1L);
        BytecodeReferenceInventory references = new BytecodeReferenceInventory(
                allKinds.size(), counts, allKinds, false);
        ArchiveReferenceInventory classReferences = new ArchiveReferenceInventory(
                references,
                new BytecodeDefinitionIndex(0, Set.of(), false),
                List.of(new ClassReferenceDetail("example/Test.class", "example/Test", references)),
                false
        );

        EnumMap<ContentCategory, ContentCategorySummary> categories =
                new EnumMap<>(ContentCategory.class);
        for (ContentCategory category : ContentCategory.values()) {
            categories.put(category, new ContentCategorySummary(
                    1, List.of("sample/" + category.name().toLowerCase(java.util.Locale.ROOT)), false));
        }
        ContentInventory content = new ContentInventory(
                ContentCategory.values().length,
                1,
                1,
                new ContentNamespaceSummary(1, List.of("example"), false),
                new ContentNamespaceSummary(1, List.of("example"), false),
                categories
        );

        TreeMap<String, String> manifestAttributes = new TreeMap<>();
        for (String attribute : ManifestSummary.ALLOWED_ATTRIBUTES) {
            manifestAttributes.put(attribute, "value-for-" + attribute);
        }

        LegacyAnalysisSummary analysis = new LegacyAnalysisSummary(
                "example",
                "1.0.0",
                "1.16.5",
                "36.x",
                1,
                new ReferenceSummary(allKinds.size(), List.copyOf(allKinds), false, true),
                new ReferenceSummary(1, List.of(BytecodeReference.member(
                        BytecodeReferenceKind.METHOD,
                        "net/minecraftforge/eventbus/api/IEventBus",
                        "register",
                        "(Ljava/lang/Object;)V"
                )), false, true),
                List.of(
                        new ThirdPartyDependency("jei", "DECLARED_MOD", false, "[7,)"),
                        new ThirdPartyDependency("com.example.library", "INFERRED_PACKAGE", false, "")
                ),
                false,
                List.of(new StaticArtifact(
                        "mixins.example.json", true, 1,
                        Map.of("mixins", List.of("ExampleMixin")), List.of(), false, false)),
                List.of(new StaticArtifact(
                        "META-INF/accesstransformer.cfg", true, 1,
                        Map.of("rules", List.of("public net.minecraft.world.World field")),
                        List.of(), false, false)),
                List.of(new StaticArtifact(
                        "META-INF/coremods.json", true, 1,
                        Map.of("declarations", List.of("example=example.js")),
                        List.of(), false, false)),
                List.of(new UnsupportedFeature(
                        "MIXINS", 1, List.of("mixins.example.json"), false)),
                42,
                Complexity.COMPLEX,
                List.of("example", "net.minecraft"),
                false,
                classReferences,
                content,
                new ManifestSummary(true, true, manifestAttributes, false)
        );

        List<NamespaceUsage> namespaces = NamespaceUsage.NAMESPACES.stream()
                .map(namespace -> new NamespaceUsage(
                        namespace, 1, 1,
                        List.of("definition/" + namespace),
                        List.of("reference/" + namespace),
                        false, false))
                .toList();
        return new CompatibilityReport(
                CompatibilityReport.SCHEMA_VERSION,
                analysis,
                new ToolInfo("LegacyBridge", "test", "policy-v2"),
                new InputFingerprint("fixture.jar", 123, "a".repeat(64)),
                new ArchiveSummary(5, 123, false, false, 1, false,
                        List.of("mixins.example.json")),
                new MetadataSummary(
                        true, true, "javafml", "[36,)",
                        List.of(new ModDeclaration("example", "1.0.0", "Example")),
                        List.of(new DependencyDeclaration(
                                "example", "minecraft", true, true,
                                "[1.16.5,1.17)", "NONE", "BOTH"))
                ),
                new BytecodeSummary(1, 0, 0, 0, new TreeMap<>(Map.of(52, 1)), namespaces),
                List.of(new FeatureMarker("MIXIN_CONFIGURATION", List.of("mixins.example.json"))),
                List.of(new Finding(
                        "LB-T001", Severity.WARNING, "test", "fixture finding",
                        List.of("fixture evidence"))),
                new Assessment(
                        AnalysisStatus.COMPLETE,
                        SourceProfile.FORGE_1_16_5_CANDIDATE,
                        ConversionStatus.NOT_IMPLEMENTED,
                        ExecutionStatus.NOT_TESTED
                )
        );
    }

    private static Map<String, Object> readSchema() throws Exception {
        try (InputStream input = PackagedReportSchemaTest.class.getResourceAsStream(SCHEMA_PATH)) {
            assertNotNull(input, "Missing packaged report schema");
            return JsonSchemaTestSupport.objectValue(JsonSchemaTestSupport.parse(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    private static Set<String> schemaRequiredKeys(Map<String, Object> schema) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Object value : JsonSchemaTestSupport.listValue(schema.get("required"))) {
            keys.add(String.valueOf(value));
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectAt(Map<String, Object> object, String key) {
        return objectAt(object.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectAt(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listAt(Map<String, Object> object, String key) {
        return (List<Object>) object.get(key);
    }
}
