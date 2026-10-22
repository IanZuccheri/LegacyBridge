package dev.legacybridge.converter.conversion;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import dev.legacybridge.converter.conversion.dependency.DependencyGraphVerifier;
import dev.legacybridge.converter.conversion.dependency.DependencyGraphVerifier.*;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Metadata bytes only: no JAR access, class loading, Forge startup or network access. */
class LifecycleMetadataTest {
    private static final String ID = "lifecycle_test";
    private static final String VERSION = "1.2.3";
    private final DependencyGraphVerifier parser = new DependencyGraphVerifier();

    @Test void sourceAndTargetGraphsPassSeparatelyAndOriginalBytesRemainUnchanged() throws Exception {
        byte[] original = metadata(baseDependencies()); byte[] untouched = original.clone();
        Map<String, Object> report = new TreeMap<>();
        LifecycleMetadata.verify(original, VERSION, false, report);
        byte[] converted = LifecycleMetadata.rewrite(original, ID, VERSION);
        LifecycleMetadata.verify(converted, VERSION, true, report);
        assertArrayEquals(untouched, original); assertFalse(Arrays.equals(original, converted));
        assertGraphStatus(report, "sourceDependencyGraph", "CLIENT", "PASSED_METADATA_ONLY");
        assertGraphStatus(report, "sourceDependencyGraph", "SERVER", "PASSED_METADATA_ONLY");
        assertGraphStatus(report, "targetDependencyGraph", "CLIENT", "PASSED_METADATA_ONLY");
        assertGraphStatus(report, "targetDependencyGraph", "SERVER", "PASSED_METADATA_ONLY");
        assertEquals(false, ((Map<?, ?>) report.get("targetDependencyGraph")).get("conversionAuthorized"));
    }

    @Test void targetRangesAreExactlyImplementedAdapterOutputsAndOnlyRuntimeEdgeIsAdded() throws Exception {
        byte[] original = metadata(baseDependencies()); byte[] converted = LifecycleMetadata.rewrite(original, ID, VERSION);
        var ranges = ModMetadataAdapter.targetRanges("1.16.5", "36.2.42");
        var target = parsed(converted); var source = parsed(original);
        assertEquals(ranges.get("loaderVersion"), target.loaderVersion());
        assertEquals(source.mods().get(0).modId(), target.mods().get(0).modId());
        assertEquals(VERSION, target.mods().get(0).version());
        assertEquals(source.mods().get(0).dependencies().size() + 1, target.mods().get(0).dependencies().size());
        for (String id : List.of("minecraft", "forge", "legacybridge_runtime")) assertEquals(ranges.get(id), dependency(target, id).versionRange());
        var runtime = dependency(target, "legacybridge_runtime");
        assertTrue(runtime.mandatory()); assertEquals(DependencySide.BOTH, runtime.side()); assertEquals(Ordering.AFTER, runtime.ordering());
    }

    @Test void preservesOptionalExternalDependencyAllComponentsAndMetadataIdentityFields() throws Exception {
        String optional = dependency("optional_library", false, "[2,3)", "CLIENT", "BEFORE");
        byte[] original = metadata(baseDependencies() + optional); byte[] converted = LifecycleMetadata.rewrite(original, ID, VERSION);
        assertEquals(dependency(parsed(original), "optional_library"), dependency(parsed(converted), "optional_library"));
        Config before = config(original), after = config(converted);
        assertEquals((Object) before.get("license"), (Object) after.get("license"));
        Config beforeMod = ((List<Config>) before.get("mods")).get(0), afterMod = ((List<Config>) after.get("mods")).get(0);
        for (String field : List.of("modId", "version", "displayName", "description", "authors")) assertEquals((Object) beforeMod.get(field), (Object) afterMod.get(field), field);
    }

    @Test void platformSideOrderingAndMandatoryFlagsAreNeverReplacedWithGenericDefaults() throws Exception {
        byte[] original = metadata(dependency("minecraft", true, "[1.16.5]", "CLIENT", "AFTER")
                + dependency("forge", false, "[36,37)", "SERVER", "BEFORE"));
        byte[] converted = LifecycleMetadata.rewrite(original, ID, VERSION);
        for (String id : List.of("minecraft", "forge")) {
            var before = dependency(parsed(original), id); var after = dependency(parsed(converted), id);
            assertEquals(before.mandatory(), after.mandatory()); assertEquals(before.side(), after.side()); assertEquals(before.ordering(), after.ordering());
        }
    }

    @Test void mandatoryMissingClientDependencyBlocksClientAndDoesNotPretendServerAlsoFailed() {
        byte[] original = metadata(baseDependencies() + dependency("client_library", true, "[1,2)", "CLIENT", "AFTER"));
        Map<String, Object> report = new TreeMap<>();
        var error = assertThrows(TransformationException.class, () -> LifecycleMetadata.verify(original, VERSION, false, report));
        assertTrue(error.getMessage().contains("MISSING_MANDATORY_DEPENDENCY"), error.getMessage());
        assertGraphStatus(report, "sourceDependencyGraph", "CLIENT", "BLOCKED");
        assertGraphStatus(report, "sourceDependencyGraph", "SERVER", "PASSED_METADATA_ONLY");
        assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(original, ID, VERSION));
    }

    @Test void mandatoryMissingServerDependencyBlocksServerAndStandaloneRewriteCannotEraseIt() {
        byte[] original = metadata(baseDependencies() + dependency("server_library", true, "[1,2)", "SERVER", "NONE"));
        Map<String, Object> report = new TreeMap<>();
        assertThrows(TransformationException.class, () -> LifecycleMetadata.verify(original, VERSION, false, report));
        assertGraphStatus(report, "sourceDependencyGraph", "CLIENT", "PASSED_METADATA_ONLY");
        assertGraphStatus(report, "sourceDependencyGraph", "SERVER", "BLOCKED");
        var error = assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(original, ID, VERSION));
        assertTrue(error.getMessage().contains("server_library"));
    }

    @Test void absentOptionalUnqualifiedVersionRangesRequireManualRuleInsteadOfBeingDropped() {
        for (String range : List.of("[1.0-beta,2)", "[1,2),[3,4)", "${library.version}")) {
            byte[] original = metadata(baseDependencies() + dependency("optional_library", false, range, "SERVER", "NONE"));
            Map<String, Object> report = new TreeMap<>();
            var error = assertThrows(TransformationException.class, () -> LifecycleMetadata.verify(original, VERSION, false, report));
            assertTrue(error.getMessage().contains("UNSUPPORTED_VERSION_RANGE"), error.getMessage());
            assertTrue(report.get("sourceDependencyMetadata").toString().contains(range));
            assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(original, ID, VERSION));
        }
    }

    @Test void sourceMismatchMustFailBeforePlatformRangeRewriteCouldMaskIt() {
        byte[] original = metadata(dependency("minecraft", true, "[1.18,1.19)", "BOTH", "NONE") + dependency("forge", true, "[36,37)", "BOTH", "NONE"));
        var error = assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(original, ID, VERSION));
        assertTrue(error.getMessage().contains("MANDATORY_VERSION_MISMATCH"), error.getMessage());
    }

    @Test void repeatedOptionalRowsAreRetainedAsSeparateDeclarations() throws Exception {
        String optional = dependency("optional_library", false, "[2,3)", "BOTH", "NONE");
        byte[] converted = LifecycleMetadata.rewrite(metadata(baseDependencies() + optional + optional), ID, VERSION);
        assertEquals(2, parsed(converted).mods().get(0).dependencies().stream().filter(dep -> dep.modId().equals("optional_library")).count());
    }

    @Test void unconstrainedOptionalRangeAndImplicitSideOrderingRemainImplicitInSerializedMetadata() throws Exception {
        String optional = "[[dependencies." + ID + "]]\nmodId=\"optional_library\"\nmandatory=false\n";
        byte[] converted = LifecycleMetadata.rewrite(metadata(baseDependencies() + optional), ID, VERSION);
        var value = dependency(parsed(converted), "optional_library"); assertEquals("", value.versionRange()); assertEquals(DependencySide.BOTH, value.side()); assertEquals(Ordering.NONE, value.ordering());
        Config groups = config(converted).get("dependencies"); List<Config> rows = groups.get(ID);
        Config row = rows.stream().filter(dep -> "optional_library".equals(dep.get("modId"))).findFirst().orElseThrow();
        assertFalse(row.contains("versionRange")); assertFalse(row.contains("ordering")); assertFalse(row.contains("side"));
    }

    @Test void exactBareNumericRangesRemainTheDocumentedConservativeGrammar() throws Exception {
        byte[] converted = LifecycleMetadata.rewrite(metadata(dependency("minecraft", true, "1.16.5", "BOTH", "NONE")
                + dependency("forge", true, "36.2.42", "BOTH", "NONE") + dependency("optional_library", false, "2.0", "BOTH", "AFTER")), ID, VERSION);
        assertEquals("2.0", dependency(parsed(converted), "optional_library").versionRange());
    }

    @Test void existingRuntimeDependencyAndReservedOwnerAreNotSilentlyMigrated() {
        byte[] existingRuntime = metadata(baseDependencies() + dependency("legacybridge_runtime", false, "", "BOTH", "NONE"));
        assertTrue(assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(existingRuntime, ID, VERSION)).getMessage().contains("RUNTIME_MIGRATION_REQUIRED"));
        byte[] original = metadata(baseDependencies());
        assertTrue(assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(original, "minecraft", VERSION)).getMessage().contains("INVALID_MOD_ID"));
        byte[] reserved = new String(original, StandardCharsets.UTF_8).replace(ID, "forge").getBytes(StandardCharsets.UTF_8);
        assertTrue(assertThrows(TransformationException.class, () -> LifecycleMetadata.verify(reserved, VERSION, false, new TreeMap<>())).getMessage().contains("RESERVED_MOD_ID"));
    }

    @Test void wrongOwnerWrongVersionMultipleModsAndMissingGroupsHaveExplicitRefusals() {
        byte[] original = metadata(baseDependencies());
        assertTrue(assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(original, "different_mod", VERSION)).getMessage().contains("MOD_ID_CHANGED"));
        assertTrue(assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(original, ID, "9.0")).getMessage().contains("VERSION_CHANGED"));
        byte[] multiple = (new String(original, StandardCharsets.UTF_8) + "\n[[mods]]\nmodId=\"second_mod\"\nversion=\"1\"\n").getBytes(StandardCharsets.UTF_8);
        assertTrue(assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(multiple, ID, VERSION)).getMessage().contains("SINGLE_MOD_REQUIRED"));
        assertTrue(assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(metadata(""), ID, VERSION)).getMessage().contains("MISSING_MINECRAFT_CONTRACT"));
    }

    @Test void nullMalformedAndOversizedArgumentsNeverBecomeNullPointerSuccessOrPartialMetadata() {
        assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(null, ID, VERSION));
        assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(metadata(baseDependencies()), null, VERSION));
        assertThrows(TransformationException.class, () -> LifecycleMetadata.verify(metadata(baseDependencies()), null, false, new TreeMap<>()));
        assertThrows(TransformationException.class, () -> LifecycleMetadata.verify(metadata(baseDependencies()), VERSION, false, null));
        assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(new byte[]{(byte) 0xc3, 0x28}, ID, VERSION));
        assertThrows(TransformationException.class, () -> LifecycleMetadata.rewrite(new byte[DependencyGraphVerifier.MAX_METADATA_BYTES + 1], ID, VERSION));
    }

    @Test void targetVerificationRejectsWrongLoaderOrMissingRuntimeContract() throws Exception {
        byte[] converted = LifecycleMetadata.rewrite(metadata(baseDependencies()), ID, VERSION);
        String target = new String(converted, StandardCharsets.UTF_8);
        String wrongLoader = target.replace(ModMetadataAdapter.targetRanges("1.16.5", "36.2.42").get("loaderVersion"), "[36,37)");
        assertTrue(assertThrows(TransformationException.class, () -> LifecycleMetadata.verify(wrongLoader.getBytes(StandardCharsets.UTF_8), VERSION, true, new TreeMap<>())).getMessage().contains("LOADER_CONTRACT_UNSUPPORTED"));
        byte[] missingRuntime = (header("[47,48)") + dependency("minecraft", true, "[1.20.1]", "BOTH", "NONE") + dependency("forge", true, "[47.4.23]", "BOTH", "NONE")).getBytes(StandardCharsets.UTF_8);
        assertTrue(assertThrows(TransformationException.class, () -> LifecycleMetadata.verify(missingRuntime, VERSION, true, new TreeMap<>())).getMessage().contains("RUNTIME_CONTRACT_CHANGED"));
    }

    @Test void unresolvedManifestVersionMustBeExplicitAndRetainedExactly() throws Exception {
        byte[] original = new String(metadata(baseDependencies()), StandardCharsets.UTF_8).replace("version=\"" + VERSION + "\"", "version=\"${file.jarVersion}\"").getBytes(StandardCharsets.UTF_8);
        byte[] converted = LifecycleMetadata.rewrite(original, ID, VERSION); assertEquals(VERSION, parsed(converted).mods().get(0).version());
    }

    private ParsedMetadata parsed(byte[] bytes) { var value = parser.parseMetadata("converted-mod", bytes, Optional.of(VERSION)); assertTrue(value.valid(), value.toString()); return value; }
    private static Dependency dependency(ParsedMetadata metadata, String id) { return metadata.mods().get(0).dependencies().stream().filter(dep -> dep.modId().equals(id)).findFirst().orElseThrow(); }
    private static byte[] metadata(String dependencies) { return (header("[36,37)") + dependencies).getBytes(StandardCharsets.UTF_8); }
    private static String baseDependencies() { return dependency("minecraft", true, "[1.16.5,1.17)", "BOTH", "NONE") + dependency("forge", true, "[36.2.42,37)", "BOTH", "NONE"); }
    private static String header(String loader) { return "modLoader=\"javafml\"\nloaderVersion=\"" + loader + "\"\nlicense=\"All Rights Reserved\"\n[[mods]]\nmodId=\"" + ID + "\"\nversion=\"" + VERSION + "\"\ndisplayName=\"Lifecycle test\"\ndescription=\"Preserved description\"\nauthors=\"Fixture author\"\n"; }
    private static String dependency(String id, boolean mandatory, String range, String side, String ordering) { return "\n[[dependencies." + ID + "]]\nmodId=\"" + id + "\"\nmandatory=" + mandatory + "\n" + (range.isEmpty() ? "" : "versionRange=\"" + range + "\"\n") + "side=\"" + side + "\"\nordering=\"" + ordering + "\"\n"; }
    private static Config config(byte[] bytes) { return new TomlParser().parse(new StringReader(new String(bytes, StandardCharsets.UTF_8))); }
    private static void assertGraphStatus(Map<String, Object> report, String graph, String side, String expected) {
        Map<?, ?> result = (Map<?, ?>) report.get(graph); assertNotNull(result, report.toString());
        Map<?, ?> sides = (Map<?, ?>) result.get("sides"); assertEquals(expected, ((Map<?, ?>) sides.get(side)).get("status"));
    }
}
