package dev.legacybridge.converter.mapping;

import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Uses locally supplied, checksum-verified real artifacts; never loads Minecraft classes. */
@EnabledIfSystemProperty(named = "legacybridge.verifyMappingEnvironment", matches = "true")
class MappingEnvironmentIntegrationTest {
    @Test
    void verifiesRealResourceLocationSymbolsAcrossPinnedForgeEnvironments() throws IOException {
        var inputs = new MappingInputsLoader().load(null);
        assertTrue(inputs.complete(), String.join("\n", inputs.diagnostics()));
        assertTrue(inputs.sources().size() >= 6, "Both mapping namespaces and API inventories need provenance");
        assertTrue(inputs.sources().stream().allMatch(source -> source.sha256().matches("[a-f0-9]{64}")));

        String sourceOwner = "net/minecraft/util/ResourceLocation";
        String targetOwner = "net/minecraft/resources/ResourceLocation";
        LegacySymbol owner = LegacySymbol.classSymbol(sourceOwner);
        LegacySymbol path = LegacySymbol.method(sourceOwner, "func_110623_a", "()Ljava/lang/String;");
        LegacySymbol namespace = LegacySymbol.field(sourceOwner, "field_110626_a", "Ljava/lang/String;");
        LegacySymbol codec = LegacySymbol.field(sourceOwner, "field_240908_a_", "Lcom/mojang/serialization/Codec;");
        LegacySymbol singleConstructor = LegacySymbol.constructor(sourceOwner, "(Ljava/lang/String;)V");
        LegacySymbol pairConstructor = LegacySymbol.constructor(sourceOwner, "(Ljava/lang/String;Ljava/lang/String;)V");
        LegacySymbol descriptor = LegacySymbol.descriptor("(L" + sourceOwner + ";)[L" + sourceOwner + ";");
        MappingDatabase database = new MappingDatabaseGenerator(inputs)
                .generate(List.of(owner, path, namespace, codec, singleConstructor, pairConstructor, descriptor));

        assertResolved(database.resolve(owner), new TargetSymbol(SymbolKind.CLASS, targetOwner, "", ""));
        assertResolved(database.resolve(path), new TargetSymbol(SymbolKind.METHOD, targetOwner, "m_135815_", "()Ljava/lang/String;"));
        assertResolved(database.resolve(codec), new TargetSymbol(SymbolKind.FIELD, targetOwner, "f_135803_", "Lcom/mojang/serialization/Codec;"));
        assertResolved(database.resolve(singleConstructor), new TargetSymbol(SymbolKind.CONSTRUCTOR, targetOwner, "<init>", "(Ljava/lang/String;)V"));
        assertResolved(database.resolve(pairConstructor), new TargetSymbol(SymbolKind.CONSTRUCTOR, targetOwner, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V"));
        assertResolved(database.resolve(descriptor), TargetSymbol.descriptor("(L" + targetOwner + ";)[L" + targetOwner + ";"));

        // These names do compose, but the target declaration is private: no usable mapping is approved.
        var sourceOfficialClass = inputs.legacyOfficial().getClass(targetOwner);
        var sourceOfficialField = sourceOfficialClass.getField("namespace");
        assertNotNull(sourceOfficialField);
        assertEquals("field_110626_a", inputs.legacySrg().getClass(sourceOfficialClass.getMapped())
                .getField(sourceOfficialField.getMapped()).getMapped());
        var targetOfficialClass = inputs.targetOfficial().getClass(targetOwner);
        var targetOfficialField = targetOfficialClass.getField("namespace");
        assertNotNull(targetOfficialField);
        assertEquals("f_135804_", inputs.targetSrg().getClass(targetOfficialClass.getMapped())
                .getField(targetOfficialField.getMapped()).getMapped());
        assertEquals(MappingStatus.MANUAL, database.resolve(namespace).status());
        assertNull(database.resolve(namespace).target());
        assertTrue(database.resolve(namespace).evidence().stream().anyMatch(line -> line.contains("visibility")));
        assertTrue(database.rules().stream().noneMatch(MappingRule::automaticRewriteAllowed));
        assertFalse(database.resolve(descriptor).automaticRewriteAllowed());
    }

    private static void assertResolved(MappingRule rule, TargetSymbol expected) {
        assertEquals(MappingStatus.RESOLVED, rule.status(), rule.evidence().toString());
        assertEquals(expected, rule.target());
        assertEquals(MappingConfidence.HIGH, rule.confidence());
        assertFalse(rule.automaticRewriteAllowed());
    }
}
