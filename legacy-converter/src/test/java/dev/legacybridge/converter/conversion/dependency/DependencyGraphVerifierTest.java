package dev.legacybridge.converter.conversion.dependency;

import static dev.legacybridge.converter.conversion.dependency.DependencyGraphVerifier.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.operation.OperationStoppedException;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DependencyGraphVerifierTest {
    private final DependencyGraphVerifier verifier = new DependencyGraphVerifier();

    @Test void exactSourceAndTargetPlatformDependenciesResolveSeparately() {
        var source = mod("source", "1.0", dep("minecraft", true, "[1.16.5,1.17)", DependencySide.BOTH, Ordering.NONE), dep("forge", true, "[36.2,37)", DependencySide.BOTH, Ordering.NONE));
        assertTrue(verify(Platform.source(), List.of(), List.of(source)).passed());
        var target = mod("target", "1.0", dep("minecraft", true, "[1.20.1]", DependencySide.BOTH, Ordering.NONE), dep("forge", true, "[47.4.23]", DependencySide.BOTH, Ordering.NONE),
                dep("legacybridge_runtime", true, ModMetadataAdapter.targetRanges("1.16.5", "36.2.42").get("legacybridge_runtime"), DependencySide.BOTH, Ordering.AFTER));
        var result = verify(Platform.target(), List.of(), List.of(target)); assertTrue(result.passed(), result.toString());
        assertBefore(result, "legacybridge_runtime", "target"); assertEquals(false, result.toTree().get("conversionAuthorized"));
        assertCode("MANDATORY_VERSION_MISMATCH", verify(Platform.target(), List.of(), List.of(source)));
    }

    @Test void absentMandatoryBlocksWithoutDroppingDeclaration() {
        var owner = mod("owner", "1.0", dep("missing", true, "[2,3)", DependencySide.BOTH, Ordering.AFTER));
        var result = verify(Platform.target(), List.of(), List.of(owner)); assertCode("MISSING_MANDATORY_DEPENDENCY", result);
        assertEquals(owner.dependencies(), result.declarations().get(0).selected().get(0).dependencies());
        assertEquals(1, result.sides().get(Side.CLIENT).dependencies().size()); assertTrue(result.sides().get(Side.CLIENT).artifactOrder().isEmpty());
    }

    @Test void explicitSelectedCohortAndInstalledInboundRequirementsAreBothChecked() {
        var consumer = mod("consumer", "1.0", dep("library", true, "[2,3)", DependencySide.BOTH, Ordering.AFTER));
        var result = verify(Platform.target(), List.of(consumer), List.of(mod("library", "2.5")));
        assertTrue(result.passed(), result.toString()); assertBefore(result, "library", "consumer");
        assertCode("MANDATORY_VERSION_MISMATCH", verify(Platform.target(), List.of(consumer), List.of(mod("library", "1.5"))));
    }

    @Test void optionalAbsentIsRetainedButPresentWrongVersionBlocks() {
        var owner = mod("owner", "1", dep("optional", false, "[2,3)", DependencySide.BOTH, Ordering.BEFORE));
        var absent = verify(Platform.target(), List.of(), List.of(owner)); assertTrue(absent.passed());
        var evaluated = absent.sides().get(Side.CLIENT).dependencies().get(0); assertTrue(evaluated.availableVersion().isEmpty()); assertFalse(evaluated.orderingEdgeAdded());
        assertCode("OPTIONAL_VERSION_MISMATCH", verify(Platform.target(), List.of(mod("optional", "1")), List.of(owner)));
    }

    @Test void dependencySideControlsPresenceAndVersionNotImplicitModPresence() {
        var owner = mod("owner", "1", dep("clientlib", true, "[2]", DependencySide.CLIENT, Ordering.NONE), dep("serverlib", true, "[3]", DependencySide.SERVER, Ordering.NONE));
        var result = verifier.verify(List.of(
                new Installation(Side.CLIENT, List.of(mod("clientlib", "2")), List.of(owner), Platform.target()),
                new Installation(Side.SERVER, List.of(mod("serverlib", "3")), List.of(owner), Platform.target())));
        assertTrue(result.passed(), result.toString());
        assertEquals(2, result.sides().get(Side.SERVER).dependencies().size());
        assertEquals(1, result.sides().get(Side.SERVER).dependencies().stream().filter(EvaluatedDependency::versionCheckApplies).count());
        var missing = verifier.verify(List.of(new Installation(Side.CLIENT, List.of(), List.of(owner), Platform.target())));
        assertCode("MISSING_MANDATORY_DEPENDENCY", missing);
    }

    @Test void orderingStillAppliesOnTheOtherPhysicalSideForBothForgeVersions() {
        var first = mod("first", "1", dep("second", false, "[99]", DependencySide.CLIENT, Ordering.BEFORE));
        var second = mod("second", "1", dep("first", false, "[99]", DependencySide.CLIENT, Ordering.BEFORE));
        for (Platform platform : List.of(Platform.source(), Platform.target())) {
            var result = verifier.verify(List.of(new Installation(Side.SERVER, List.of(first), List.of(second), platform)));
            assertCode("ORDERING_CYCLE", result);
            assertTrue(result.sides().get(Side.SERVER).dependencies().stream().noneMatch(EvaluatedDependency::versionCheckApplies));
            assertTrue(result.sides().get(Side.SERVER).dependencies().stream().allMatch(EvaluatedDependency::orderingEdgeAdded));
        }
    }

    @Test void directAndTransitiveBeforeAfterOrdersAreDeterministic() {
        var first = mod("first", "1", dep("second", true, "[1]", DependencySide.BOTH, Ordering.BEFORE));
        var second = mod("second", "1", dep("third", true, "[1]", DependencySide.BOTH, Ordering.BEFORE));
        var third = mod("third", "1");
        var a = verify(Platform.target(), List.of(third, second), List.of(first));
        var b = verify(Platform.target(), List.of(second, third), List.of(first));
        assertTrue(a.passed()); assertBefore(a, "first", "second"); assertBefore(a, "second", "third");
        assertEquals(a.sides().get(Side.CLIENT).modOrder(), b.sides().get(Side.CLIENT).modOrder());
    }

    @Test void duplicateModIdNeverSelectsNewestOrReplacesInstalled() {
        assertCode("DUPLICATE_MOD_ID", verify(Platform.target(), List.of(new Mod("installed", "same", "1", List.of())), List.of(new Mod("selected", "same", "2", List.of()))));
        assertCode("RESERVED_MOD_ID", verify(Platform.target(), List.of(), List.of(mod("minecraft", "1.20.1"))));
    }

    @Test void multiModArtifactIsOneOrderingNodeAndInternalOrderingIsIgnored() {
        var a = new Mod("one-jar", 0, "first", "1", List.of(dep("second", true, "[1]", DependencySide.BOTH, Ordering.BEFORE)));
        var b = new Mod("one-jar", 1, "second", "1", List.of(dep("first", true, "[1]", DependencySide.BOTH, Ordering.BEFORE)));
        var result = verify(Platform.target(), List.of(), List.of(a, b)); assertTrue(result.passed(), result.toString());
        assertEquals(1, result.sides().get(Side.CLIENT).artifactOrder().stream().filter("one-jar"::equals).count());
        assertTrue(result.sides().get(Side.CLIENT).dependencies().stream().noneMatch(EvaluatedDependency::orderingEdgeAdded));
    }

    @Test void artifactGroupingDetectsCycleEvenWhenPerModGraphIsAcyclic() {
        var a0 = new Mod("jar-a", 0, "alpha", "1", List.of(dep("gamma", true, "[1]", DependencySide.BOTH, Ordering.BEFORE)));
        var a1 = new Mod("jar-a", 1, "beta", "1", List.of());
        var b0 = new Mod("jar-b", 0, "gamma", "1", List.of());
        var b1 = new Mod("jar-b", 1, "delta", "1", List.of(dep("alpha", true, "[1]", DependencySide.BOTH, Ordering.BEFORE)));
        for (Platform platform : List.of(Platform.source(), Platform.target())) assertCode("ORDERING_CYCLE", verify(platform, List.of(a0, a1), List.of(b0, b1)));
    }

    @Test void sourceSecondaryIdOrderingIsRefusedAsDifferentTargetContract() {
        var a = new Mod("multi", 0, "first", "1", List.of()); var b = new Mod("multi", 1, "second", "1", List.of());
        var consumer = mod("consumer", "1", dep("second", false, "[1]", DependencySide.BOTH, Ordering.AFTER));
        assertTrue(verify(Platform.source(), List.of(a, b), List.of(consumer)).passed());
        assertCode("ORDERING_CONTRACT_DIVERGENCE", verify(Platform.target(), List.of(a, b), List.of(consumer)));
    }

    @Test void artifactIndicesAndOriginsMustBeExplicitAndComplete() {
        assertCode("INCOMPLETE_ARTIFACT_DECLARATIONS", verify(Platform.target(), List.of(), List.of(new Mod("multi", 1, "secondary", "1", List.of()))));
        assertCode("ARTIFACT_ORIGIN_CONFLICT", verify(Platform.target(), List.of(new Mod("same-jar", 0, "first", "1", List.of())), List.of(new Mod("same-jar", 1, "second", "1", List.of()))));
    }

    @Test void unverifiedPlatformRuntimeAndUnknownRangesFailClosed() {
        assertCode("UNVERIFIED_PLATFORM", verify(new Platform("1.20.1", "47.4.24", Optional.of(AdapterCatalog.RUNTIME_VERSION)), List.of(), List.of(mod("owner", "1"))));
        assertCode("UNVERIFIED_PLATFORM", verify(new Platform("1.20.1", "47.4.23", Optional.of("0.0.0")), List.of(), List.of(mod("owner", "1"))));
        assertCode("UNSUPPORTED_VERSION_RANGE", verify(Platform.target(), List.of(), List.of(mod("owner", "1", dep("absent", false, "[1,2),[3,4)", DependencySide.SERVER, Ordering.NONE)))));
        assertCode("VERSION_COMPARISON_UNSUPPORTED", verify(Platform.target(), List.of(mod("library", "1.5-beta")), List.of(mod("owner", "1", dep("library", true, "[1,2)", DependencySide.BOTH, Ordering.NONE)))));
        assertTrue(verify(Platform.target(), List.of(), List.of(mod("owner", "1", dep("legacybridge_runtime", true, "[" + AdapterCatalog.RUNTIME_VERSION + "]", DependencySide.BOTH, Ordering.NONE)))).passed());
    }

    @Test void emptyRangeIsUnconstrainedButNeverInventsMissingMandatoryDependency() {
        var owner = mod("owner", "1", dep("library", true, "", DependencySide.BOTH, Ordering.NONE));
        assertTrue(verify(Platform.target(), List.of(mod("library", "1-beta")), List.of(owner)).passed());
        assertCode("MISSING_MANDATORY_DEPENDENCY", verify(Platform.target(), List.of(), List.of(owner)));
    }

    @Test void metadataParserRetainsDeclarationOrderEveryDependencyAndManifestVersion() {
        String toml = header() + "[[mods]]\nmodId=\"zeta\"\nversion=\"${file.jarVersion}\"\n[[mods]]\nmodId=\"alpha\"\nversion=\"2\"\n"
                + "[[dependencies.zeta]]\nmodId=\"alpha\"\nmandatory=true\nversionRange=\"[2,3)\"\nordering=\"AFTER\"\nside=\"CLIENT\"\n"
                + "[[dependencies.zeta]]\nmodId=\"optional\"\nmandatory=false\nside=\"SERVER\"\n";
        var parsed = verifier.parseMetadata("owned.jar", toml.getBytes(StandardCharsets.UTF_8), Optional.of("1.2.3"));
        assertTrue(parsed.valid(), parsed.toString()); assertEquals(List.of("zeta", "alpha"), parsed.mods().stream().map(Mod::modId).toList());
        assertEquals(0, parsed.mods().get(0).declarationIndex()); assertEquals(1, parsed.mods().get(1).declarationIndex());
        assertEquals("1.2.3", parsed.mods().get(0).version()); assertEquals(2, parsed.mods().get(0).dependencies().size());
        assertEquals(DependencySide.CLIENT, parsed.mods().get(0).dependencies().get(0).side());
        assertEquals("[2,3)", parsed.mods().get(0).dependencies().get(0).versionRange());
        assertFalse(verifier.parseMetadata("owned.jar", toml.getBytes(StandardCharsets.UTF_8), Optional.empty()).valid());
    }

    @Test void parserRejectsMalformedTruncatedOrForeignOwnerMetadataWithoutPartialSuccess() {
        assertFalse(verifier.parseMetadata("bad", new byte[]{(byte) 0xc3, 0x28}, Optional.empty()).valid());
        assertFalse(verifier.parseMetadata("bad", (header() + "[[mods]]\nmodId=\"owner\"\nversion=\"1\"\n[[dependencies.other]]\nmodId=\"missing\"\nmandatory=true\n").getBytes(StandardCharsets.UTF_8), Optional.empty()).valid());
        assertFalse(verifier.parseMetadata("bad", (header() + "[[mods]]\nmodId=\"owner\"\nversion=\"" + "1".repeat(1100) + "\"\n").getBytes(StandardCharsets.UTF_8), Optional.empty()).valid());
        assertFalse(verifier.parseMetadata("bad", new byte[MAX_METADATA_BYTES + 1], Optional.empty()).valid());
        assertFalse(verifier.parseMetadata("bad", (header() + "[[mods]]\nmodId=\"owner\"\nversion=123\n").getBytes(StandardCharsets.UTF_8), Optional.empty()).valid());
        assertFalse(verifier.parseMetadata("bad", (header() + "[[mods]]\nmodId=\"owner\"\nversion=\"1\"\n[[dependencies.owner]]\nmodId=\"library\"\nmandatory=false\nunknown=\"discarded\"\n").getBytes(StandardCharsets.UTF_8), Optional.empty()).valid());
    }

    @Test void repeatedDependencyDeclarationsRemainInResult() {
        var dependency = dep("library", true, "[1,2)", DependencySide.BOTH, Ordering.NONE);
        var owner = mod("owner", "1", dependency, dependency);
        var result = verify(Platform.target(), List.of(mod("library", "1")), List.of(owner)); assertTrue(result.passed());
        assertEquals(2, result.sides().get(Side.CLIENT).dependencies().size()); assertEquals(2, result.declarations().get(0).selected().get(0).dependencies().size());
    }

    @Test void boundsAndCancellationDoNotReturnPartialApproval() {
        List<Mod> many = new ArrayList<>(); for (int index = 0; index <= MAX_MODS; index++) many.add(mod("mod_" + index, "1"));
        assertCode("GRAPH_LIMIT", verify(Platform.target(), List.of(), many));
        assertFalse(verifier.verify(List.of()).passed());
        var install = new Installation(Side.CLIENT, List.of(), List.of(mod("owner", "1")), Platform.target());
        assertFalse(verifier.verify(List.of(install, install)).passed());
        var control = new OperationControl(OperationLimits.defaults(), ignored -> { }); control.requestCancel();
        try (var ignored = control.enter()) { assertThrows(OperationStoppedException.class, () -> verifier.verify(List.of(install))); }
    }

    private Result verify(Platform platform, List<Mod> installed, List<Mod> selected) { return verifier.verify(List.of(new Installation(Side.CLIENT, installed, selected, platform))); }
    private static Mod mod(String id, String version, Dependency... dependencies) { return new Mod("jar-" + id, id, version, List.of(dependencies)); }
    private static Dependency dep(String id, boolean mandatory, String range, DependencySide side, Ordering ordering) { return new Dependency(id, mandatory, range, side, ordering); }
    private static void assertBefore(Result result, String first, String second) { var order = result.sides().get(Side.CLIENT).modOrder(); assertTrue(order.indexOf(first) >= 0 && order.indexOf(first) < order.indexOf(second), order.toString()); }
    private static void assertCode(String code, Result result) {
        assertFalse(result.passed(), result.toString());
        assertTrue(result.diagnostics().stream().anyMatch(error -> error.code().equals(code)) || result.sides().values().stream().flatMap(side -> side.diagnostics().stream()).anyMatch(error -> error.code().equals(code)), result.toString());
    }
    private static String header() { return "modLoader=\"javafml\"\nloaderVersion=\"[36,)\"\nlicense=\"All Rights Reserved\"\n"; }
}
