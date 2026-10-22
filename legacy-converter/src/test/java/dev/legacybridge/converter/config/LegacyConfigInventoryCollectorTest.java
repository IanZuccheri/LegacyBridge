package dev.legacybridge.converter.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyConfigInventoryCollectorTest {
    @Test
    void recognizesOnlySupportedStaticArtifacts() {
        LegacyConfigInventoryCollector collector = new LegacyConfigInventoryCollector();

        assertTrue(collector.requiresBytes("META-INF/MANIFEST.MF"));
        assertTrue(collector.requiresBytes("META-INF/accesstransformer.cfg"));
        assertTrue(collector.requiresBytes("META-INF/coremods.json"));
        assertTrue(collector.requiresBytes("mixins.example.json"));
        assertTrue(collector.requiresBytes("config/example.mixins.json"));
        assertTrue(collector.requiresBytes("config/example.mixin.json"));
        assertFalse(collector.requiresBytes("mixins.example.refmap.json"));
        assertFalse(collector.requiresBytes("meta-inf/manifest.mf"));
        assertFalse(collector.requiresBytes("META-INF/AccessTransformer.cfg"));
        assertFalse(collector.requiresBytes("meta-inf/coremods.json"));
        assertFalse(collector.requiresBytes("data/example/recipes/item.json"));
    }

    @Test
    void parsesMixinConfigAndKeepsDeterministicBoundedFacts() {
        String json = """
                {
                  "required": true,
                  "package": "example.mixin",
                  "plugin": "example.mixin.Plugin",
                  "compatibilityLevel": "JAVA_8",
                  "mixins": ["ZMixin", "AMixin", "AMixin"],
                  "client": ["client.ScreenMixin"],
                  "server": ["server.ServerMixin"]
                }
                """;
        LegacyConfigInventoryCollector collector = new LegacyConfigInventoryCollector();
        collector.accept("mixins.example.json", utf8(json));

        LegacyConfigInventory inventory = collector.snapshot();
        MixinConfigurationSummary mixin = inventory.mixins().get(0);
        assertTrue(mixin.parsed());
        assertEquals("example.mixin", mixin.mixinPackage());
        assertEquals("example.mixin.Plugin", mixin.plugin());
        assertEquals(Boolean.TRUE, mixin.required());
        assertEquals("JAVA_8", mixin.compatibilityLevel());
        assertEquals(3, mixin.declaredMixinCount());
        assertEquals(List.of("AMixin", "ZMixin"), mixin.mixins());
        assertEquals(List.of("client.ScreenMixin"), mixin.client());
        assertEquals(List.of("server.ServerMixin"), mixin.server());
        assertFalse(mixin.partial());
        assertTrue(inventory.diagnostics().isEmpty());
    }

    @Test
    void countsAccessTransformerRulesWithoutResolvingSymbols() {
        String rules = """
                # comment
                public-f   net.minecraft.entity.Entity field_70165_t # inline comment

                protected  net.minecraft.world.World field_72986_A
                public-f net.minecraft.entity.Entity field_70165_t
                """;
        LegacyConfigInventoryCollector collector = new LegacyConfigInventoryCollector();
        collector.accept("META-INF/accesstransformer.cfg", utf8(rules));

        AccessTransformerSummary summary = collector.snapshot().accessTransformers().get(0);
        assertTrue(summary.parsed());
        assertEquals(3, summary.ruleCount());
        assertEquals(List.of(
                "protected net.minecraft.world.World field_72986_A",
                "public-f net.minecraft.entity.Entity field_70165_t"
        ), summary.normalizedRuleSamples());
        assertFalse(summary.samplesTruncated());
    }

    @Test
    void capturesCoremodNamesAndScriptPathsButDoesNotFollowThem() {
        LegacyConfigInventoryCollector collector = new LegacyConfigInventoryCollector();
        collector.accept("META-INF/coremods.json", utf8("""
                {
                  "zeta": "coremods/does-not-exist.js",
                  "alpha": "coremods/alpha.js",
                  "bad": {"script": "must-not-be-interpreted.js"}
                }
                """));

        CoremodConfigurationSummary summary = collector.snapshot().coremods().get(0);
        assertTrue(summary.parsed());
        assertEquals(3, summary.declaredCoremodCount());
        assertEquals(List.of(
                new CoremodDeclaration("alpha", "coremods/alpha.js"),
                new CoremodDeclaration("bad", null),
                new CoremodDeclaration("zeta", "coremods/does-not-exist.js")
        ), summary.coremods());
        assertTrue(summary.partial());
        assertTrue(codes(collector.snapshot()).contains("LB-CFG204"));
    }

    @Test
    void retainsOnlyAllowlistedManifestMainAttributes() {
        String manifest = """
                Manifest-Version: 1.0
                MixinConfigs: z.mixins.json, a.mixins.json
                FMLCorePlugin: example.CorePlugin
                FMLCorePluginContainsFMLMod: true
                ForceLoadAsMod: true
                ContainedDeps: dependency.jar
                Class-Path: external.jar
                TweakClass: example.Tweaker
                Automatic-Module-Name: example.mod
                Implementation-Title: Example
                Implementation-Version: 1.2.3
                Secret-Attribute: must-not-be-retained
                
                """;
        LegacyConfigInventoryCollector collector = new LegacyConfigInventoryCollector();
        collector.accept("META-INF/MANIFEST.MF", utf8(manifest));

        ManifestAttributesSummary summary = collector.snapshot().manifest();
        assertTrue(summary.present());
        assertTrue(summary.parsed());
        assertEquals(10, summary.attributes().size());
        assertFalse(summary.attributes().containsKey("Secret-Attribute"));
        assertEquals(List.of("a.mixins.json", "z.mixins.json"), summary.declaredMixinConfigurationPaths());
    }

    @Test
    void marksExcessiveManifestMixinDeclarationsPartialInsteadOfSilentlyDroppingThem() {
        String declarations = java.util.stream.IntStream.rangeClosed(
                        0,
                        LegacyConfigInventoryCollector.MAX_DECLARATION_SAMPLES
                )
                .mapToObj(index -> "m" + index)
                .collect(java.util.stream.Collectors.joining(","));
        LegacyConfigInventoryCollector collector = new LegacyConfigInventoryCollector();
        collector.accept("META-INF/MANIFEST.MF", utf8(
                "Manifest-Version: 1.0\r\n" + foldedManifestAttribute("MixinConfigs", declarations)
                        + "\r\n"
        ));

        LegacyConfigInventory inventory = collector.snapshot();
        assertEquals(LegacyConfigInventoryCollector.MAX_DECLARATION_SAMPLES,
                inventory.manifest().declaredMixinConfigurationPaths().size());
        assertTrue(inventory.manifest().valuesTruncated());
        assertTrue(codes(inventory).contains("LB-CFG303"));
    }

    @Test
    void reportsMalformedAndPartialJsonWithoutThrowingOrConstructingTypes() {
        LegacyConfigInventoryCollector collector = new LegacyConfigInventoryCollector();
        collector.accept("mixins.broken.json", utf8("{\"mixins\":[\"Okay\", 7],"));
        collector.accept("broken.mixin.json", utf8("[]"));
        collector.accept("partial.mixins.json", utf8("""
                {"package": 7, "required": "yes", "mixins": ["Okay", null]}
                """));
        collector.accept("META-INF/coremods.json", utf8("[\"not-an-object\"]"));

        LegacyConfigInventory inventory = collector.snapshot();
        assertEquals(3, inventory.mixins().size());
        assertFalse(inventory.mixins().get(0).parsed());
        MixinConfigurationSummary partial = inventory.mixins().stream()
                .filter(item -> item.path().equals("partial.mixins.json"))
                .findFirst().orElseThrow();
        assertTrue(partial.parsed());
        assertTrue(partial.partial());
        assertNull(partial.mixinPackage());
        assertNull(partial.required());
        assertEquals(Set.of("LB-CFG101", "LB-CFG102", "LB-CFG103", "LB-CFG104", "LB-CFG202"), codes(inventory));
    }

    @Test
    void truncatesDeclarationSamplesAndReportsLimits() {
        StringBuilder json = new StringBuilder("{\"mixins\":[");
        for (int index = LegacyConfigInventoryCollector.MAX_DECLARATION_SAMPLES; index >= 0; index--) {
            if (json.charAt(json.length() - 1) != '[') {
                json.append(',');
            }
            json.append('"').append(String.format("Mixin%03d", index)).append('"');
        }
        json.append("]}");
        LegacyConfigInventoryCollector collector = new LegacyConfigInventoryCollector();
        collector.accept("bounded.mixins.json", utf8(json.toString()));

        MixinConfigurationSummary summary = collector.snapshot().mixins().get(0);
        assertEquals(LegacyConfigInventoryCollector.MAX_DECLARATION_SAMPLES + 1, summary.declaredMixinCount());
        assertEquals(LegacyConfigInventoryCollector.MAX_DECLARATION_SAMPLES, summary.mixins().size());
        assertEquals("Mixin000", summary.mixins().get(0));
        assertTrue(summary.samplesTruncated());
        assertTrue(codes(collector.snapshot()).contains("LB-CFG105"));
    }

    @Test
    void rejectsOversizedAndInvalidUtf8ArtifactsAsDiagnostics() {
        LegacyConfigInventoryCollector collector = new LegacyConfigInventoryCollector();
        byte[] oversized = new byte[LegacyConfigInventoryCollector.MAX_ARTIFACT_BYTES + 1];
        Arrays.fill(oversized, (byte) ' ');
        collector.accept("oversized.mixins.json", oversized);
        collector.accept("invalid.mixins.json", new byte[]{'{', '"', (byte) 0xc3, '"', ':', '1', '}'});

        LegacyConfigInventory inventory = collector.snapshot();
        assertEquals(Set.of("LB-CFG001", "LB-CFG002"), codes(inventory));
        assertTrue(inventory.mixins().stream().noneMatch(MixinConfigurationSummary::parsed));
    }

    @Test
    void validatesCollectorArgumentsAndReturnsImmutableSnapshots() {
        LegacyConfigInventoryCollector collector = new LegacyConfigInventoryCollector();
        assertThrows(NullPointerException.class, () -> collector.requiresBytes(null));
        assertThrows(NullPointerException.class, () -> collector.accept(null, new byte[0]));
        assertThrows(NullPointerException.class, () -> collector.accept("mixins.x.json", null));
        assertThrows(IllegalArgumentException.class, () -> collector.accept(" ", new byte[0]));

        LegacyConfigInventory snapshot = collector.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.mixins().add(
                MixinConfigurationSummary.unreadable("x")
        ));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.manifest().attributes().put("x", "y"));
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String foldedManifestAttribute(String name, String value) {
        StringBuilder result = new StringBuilder(name).append(": ");
        int offset = 0;
        int capacity = 70 - result.length();
        while (offset < value.length()) {
            int end = Math.min(value.length(), offset + capacity);
            result.append(value, offset, end).append("\r\n");
            offset = end;
            if (offset < value.length()) {
                result.append(' ');
                capacity = 69;
            }
        }
        return result.toString();
    }

    private static Set<String> codes(LegacyConfigInventory inventory) {
        return inventory.diagnostics().stream()
                .map(ArtifactDiagnostic::code)
                .collect(Collectors.toSet());
    }
}
