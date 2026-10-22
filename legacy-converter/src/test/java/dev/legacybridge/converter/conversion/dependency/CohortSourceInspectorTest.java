package dev.legacybridge.converter.conversion.dependency;

import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.converter.testutil.TestJarBuilder;
import net.minecraftforge.srgutils.IMappingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Deliberately inert synthetic bytecode. No fixture is ever put on a classloader. */
class CohortSourceInspectorTest {
    @TempDir Path directory;
    private static final String LIB = "authored/library/Library", USER = "authored/consumer/Consumer";
    private final CohortSourceInspector inspector = new CohortSourceInspector();

    @Test void sourceClosureAndCallsAreVerifiedPerOwnerWithoutPublishingOrExecuting() throws Exception {
        var request = fixture("valid");
        var before = Files.list(directory); List<Path> files;
        try (before) { files = before.sorted().toList(); }
        assertNull(System.getProperty("legacybridge.cohort.must.not.execute"));
        var result = inspector.inspect(request, inputs());
        assertTrue(result.sourceLinkagePassed(), json(result));
        assertEquals(2, result.artifacts().size());
        assertEquals(2, result.linkage().size());
        result.linkage().values().forEach(s -> assertEquals(1, s.ownedClassCount()));
        assertTrue(result.crossArtifactReferences().stream().anyMatch(r -> r.reference().name().equals("score") && r.providerArtifact().equals("library")));
        assertTrue(result.crossArtifactReferences().stream().anyMatch(r -> r.reference().name().equals("offset")));
        var order = result.graph().orElseThrow().sides().get(DependencyGraphVerifier.Side.SERVER).artifactOrder();
        assertTrue(order.indexOf("library") < order.indexOf("consumer"));
        assertTrue(order.containsAll(List.of("platform:forge", "platform:minecraft")));
        assertEquals(false, result.toTree().get("publicationAllowed"));
        assertEquals("NOT_RUN", result.toTree().get("targetLinkage"));
        assertNull(System.getProperty("legacybridge.cohort.must.not.execute"));
        for (var a : request.artifacts()) assertEquals(a.expectedSha256(), sha(Files.readAllBytes(a.input())));
        try (var after = Files.list(directory)) { assertEquals(files, after.sorted().toList()); }
        var reversed = new ArrayList<>(request.artifacts()); Collections.reverse(reversed);
        assertEquals(json(result), json(inspector.inspect(new CohortInspectionRequest(reversed, request.mappingData()), inputs())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "version", "method", "private", "staticness", "unknown", "split", "duplicate_class",
            "optional", "no_edge", "client", "cycle", "duplicate_mod", "manifest", "nested", "native", "hash", "bad_code", "corrupt"})
    void rejectsInvalidWholeCohort(String variant) throws Exception {
        var result = inspector.inspect(fixture(variant), inputs());
        assertFalse(result.sourceLinkagePassed(), variant + json(result));
        assertFalse(result.diagnostics().isEmpty());
        assertEquals(false, result.toTree().get("conversionAuthorized"));
        assertEquals("BLOCKED", result.toTree().get("status"));
    }

    @Test void wrongMemberContractCannotPassOnVersionAlone() throws Exception {
        var result = inspector.inspect(fixture("method"), inputs());
        assertTrue(result.graph().orElseThrow().passed());
        assertEquals("SOURCE_LINKAGE_REJECTED", result.diagnostics().get(0).error().code());
        assertTrue(result.diagnostics().get(0).error().message().contains("MISSING_METHOD"), json(result));
    }
    @Test void exactInputAliasIsRejected() throws Exception {
        var selected = fixture("valid").artifacts().get(0);
        var request = new CohortInspectionRequest(List.of(selected, new CohortInspectionRequest.Artifact("alias", selected.input(), selected.expectedSha256())), MappingData.unavailable());
        var result = inspector.inspect(request, inputs());
        assertEquals("ARTIFACT_ALIAS", result.diagnostics().get(0).error().code());
    }
    @Test void separateHardLinkNameDoesNotBecomeAnotherArtifact() throws Exception {
        var selected = fixture("valid").artifacts().stream().filter(a -> a.id().equals("library")).findFirst().orElseThrow();
        Path link = Files.createLink(directory.resolve("linked.jar"), selected.input());
        var request = new CohortInspectionRequest(List.of(selected, new CohortInspectionRequest.Artifact("linked", link, selected.expectedSha256())), MappingData.unavailable());
        assertEquals("ARTIFACT_ALIAS", inspector.inspect(request, inputs()).diagnostics().get(0).error().code());
    }
    @Test void unusedConstantPoolCannotBorrowAnUndeclaredSelectedProvider() throws Exception {
        var request = fixture("unused_no_edge");
        var result = inspector.inspect(request, inputs());
        assertTrue(result.graph().orElseThrow().passed());
        assertFalse(result.sourceLinkagePassed());
        assertEquals("SOURCE_LINKAGE_REJECTED", result.diagnostics().get(0).error().code());
        assertTrue(result.diagnostics().get(0).error().message().contains("MISSING_CLASS"), json(result));
    }
    @Test void cancelledInspectionHasNoApprovedOrPartialResult() throws Exception {
        var request = fixture("valid"); var data = inputs();
        var control = new OperationControl(OperationLimits.defaults(), p -> { });
        control.requestCancel();
        try (var scope = control.enter()) { assertThrows(OperationStoppedException.class, () -> inspector.inspect(request, data)); }
    }
    @Test void refusesTooManyOrRepeatedLabelsAndUnpinnedSelections() throws Exception {
        var selected = fixture("valid").artifacts().get(0);
        assertThrows(IllegalArgumentException.class, () -> new CohortInspectionRequest(List.of(selected, selected), MappingData.unavailable()));
        assertThrows(IllegalArgumentException.class, () -> new CohortInspectionRequest.Artifact("x", selected.input(), ""));
        assertThrows(IllegalArgumentException.class, () -> new CohortInspectionRequest(Collections.nCopies(5, selected), MappingData.unavailable()));
    }
    @Test void incompleteOrChangedPlatformDataNeverPasses() throws Exception {
        var data = inputs(); var request = fixture("valid");
        var incomplete = new MappingInputs(data.legacyOfficial(), data.targetOfficial(), data.legacySrg(), data.targetSrg(), data.legacyApi(), data.targetApi(), data.sources(), List.of(), false);
        assertFalse(inspector.inspect(request, incomplete).sourceLinkagePassed());
        var changed = new MappingInputs(data.legacyOfficial(), data.targetOfficial(), data.legacySrg(), data.targetSrg(), data.legacyApi(), ApiIndex.empty(), data.sources(), List.of(), true);
        assertEquals("FORGE_DECLARATION_MISMATCH", inspector.inspect(request, changed).diagnostics().get(0).error().code());
    }

    private CohortInspectionRequest fixture(String variant) throws Exception {
        String libName = variant.equals("split") ? "authored/consumer/Library" : variant.equals("duplicate_class") ? USER : LIB;
        var libEntries = new TreeMap<String, byte[]>();
        var consumerEntries = new TreeMap<String, byte[]>();
        libEntries.put(libName + ".class", library(libName, variant));
        consumerEntries.put(USER + ".class", consumer(variant.equals("unknown") ? "unknown/Api" : libName, variant));
        libEntries.put("META-INF/mods.toml", metadata("librarymod", variant.equals("cycle") ? dependency("librarymod", "consumermod", true, "BOTH", "AFTER", "[1.0.0]") : "").getBytes(StandardCharsets.UTF_8));
        String consumerId = variant.equals("duplicate_mod") ? "librarymod" : "consumermod";
        String dep = variant.equals("no_edge") || variant.equals("unused_no_edge") ? "" : dependency(consumerId, "librarymod", !variant.equals("optional"), variant.equals("client") ? "CLIENT" : "BOTH", "AFTER", variant.equals("version") ? "[2.0.0]" : "[1.0.0]");
        consumerEntries.put("META-INF/mods.toml", metadata(consumerId, dep).getBytes(StandardCharsets.UTF_8));
        if (variant.equals("manifest")) consumerEntries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nClass-Path: other.jar\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        if (variant.equals("nested")) consumerEntries.put("libraries/hidden.jar", new byte[1]);
        if (variant.equals("native")) consumerEntries.put("native/hidden.dll", new byte[1]);
        var lib = TestJarBuilder.write(directory.resolve("library.jar"), libEntries);
        var user = TestJarBuilder.write(directory.resolve("consumer.jar"), consumerEntries);
        if (variant.equals("corrupt")) Files.write(user, Arrays.copyOf(Files.readAllBytes(user), 12));
        var selected = new ArrayList<CohortInspectionRequest.Artifact>();
        if (!variant.equals("missing")) selected.add(new CohortInspectionRequest.Artifact("library", lib, sha(Files.readAllBytes(lib))));
        selected.add(new CohortInspectionRequest.Artifact("consumer", user, variant.equals("hash") ? "0".repeat(64) : sha(Files.readAllBytes(user))));
        return new CohortInspectionRequest(selected, MappingData.unavailable());
    }
    private static byte[] library(String owner, String variant) {
        var cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, owner, null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "offset", "I", null, null).visitEnd();
        var m = cw.visitMethod((variant.equals("private") ? ACC_PRIVATE : ACC_PUBLIC) | (variant.equals("staticness") ? 0 : ACC_STATIC), variant.equals("method") ? "renamed" : "score", "(I)I", null, null);
        m.visitCode(); m.visitVarInsn(ILOAD, variant.equals("staticness") ? 1 : 0); m.visitInsn(ICONST_3); m.visitInsn(IMUL); m.visitInsn(IRETURN); m.visitMaxs(0, 0); m.visitEnd();
        m = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode(); m.visitLdcInsn("legacybridge.cohort.must.not.execute"); m.visitLdcInsn("executed");
        m.visitMethodInsn(INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        m.visitInsn(POP); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd(); cw.visitEnd(); return cw.toByteArray();
    }
    private static byte[] consumer(String provider, String variant) {
        var cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, USER, null, "java/lang/Object", null);
        if (variant.equals("unused_no_edge")) { cw.newClass(provider); cw.visitEnd(); return cw.toByteArray(); }
        var m = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "evaluate", "(I)I", null, null);
        m.visitCode(); m.visitVarInsn(ILOAD, 0); m.visitMethodInsn(INVOKESTATIC, provider, "score", "(I)I", false);
        m.visitFieldInsn(GETSTATIC, provider, "offset", "I"); m.visitInsn(IADD);
        m.visitInsn(variant.equals("bad_code") ? ARETURN : IRETURN); m.visitMaxs(0, 0); m.visitEnd(); cw.visitEnd(); return cw.toByteArray();
    }
    private static String metadata(String id, String extra) {
        return "modLoader=\"javafml\"\nloaderVersion=\"[36,)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"" + id + "\"\nversion=\"1.0.0\"\ndisplayName=\"Authored inert fixture\"\ndescription=\"Not executed\"\n"
                + dependency(id, "minecraft", true, "BOTH", "NONE", "[1.16.5]") + dependency(id, "forge", true, "BOTH", "NONE", "[36.2.42]") + extra;
    }
    private static String dependency(String owner, String id, boolean mandatory, String side, String order, String range) {
        return "[[dependencies." + owner + "]]\nmodId=\"" + id + "\"\nmandatory=" + mandatory + "\nversionRange=\"" + range + "\"\nordering=\"" + order + "\"\nside=\"" + side + "\"\n";
    }
    private static MappingInputs inputs() throws Exception {
        var mapping = IMappingFile.load(new ByteArrayInputStream("fixture.Api -> a:\n".getBytes(StandardCharsets.UTF_8)));
        var mod = new ApiIndex.ApiClass("net/minecraftforge/fml/common/Mod", ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT | ACC_ANNOTATION, "java/lang/Object", List.of("java/lang/annotation/Annotation"), List.of(new ApiIndex.ApiMember("value", "()Ljava/lang/String;", ACC_PUBLIC | ACC_ABSTRACT)), List.of());
        var api = new ApiIndex(Map.of(mod.name(), mod));
        return new MappingInputs(mapping, mapping, mapping, mapping, api, api, List.of(new MappingSource("synthetic-test-only", "0".repeat(64), "NOT_A_VERIFIED_PLATFORM")), List.of(), true);
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String json(CohortSourceInspector.Result result) { return new CanonicalReportWriter().toJsonTree(result.toTree()); }
}
