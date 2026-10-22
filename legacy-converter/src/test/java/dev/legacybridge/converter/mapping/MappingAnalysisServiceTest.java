package dev.legacybridge.converter.mapping;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.testutil.TestJarBuilder;
import net.minecraftforge.srgutils.IMappingBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class MappingAnalysisServiceTest {
    @TempDir
    Path temporary;

    @Test
    void retainsDescriptorsFromDeclarationsThatHaveNoMemberInstruction() throws Exception {
        ClassWriter writer = startClass("sample/Declarations");
        writer.visitField(Opcodes.ACC_PUBLIC, "items", "[[Lexternal/Field;", null, null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "convert",
                "(Lexternal/Argument;)Lexternal/Result;", null, null).visitEnd();
        writer.visitEnd();
        Path input = TestJarBuilder.write(temporary.resolve("declarations.jar"),
                Map.of("sample/Declarations.class", writer.toByteArray()));

        MappingReport report = new MappingAnalysisService().analyze(input, incompleteInputs());

        assertNotNull(entry(report, LegacySymbol.descriptor("[[Lexternal/Field;")));
        assertNotNull(entry(report, LegacySymbol.descriptor("(Lexternal/Argument;)Lexternal/Result;")));
        assertNotNull(entry(report, LegacySymbol.classSymbol("external/Argument")));
        assertNotNull(entry(report, LegacySymbol.classSymbol("external/Result")));
        assertTrue(report.entries().stream().noneMatch(e -> e.rule().legacy().kind() == SymbolKind.METHOD));
        assertFalse(report.referencesTruncated());
    }

    @Test
    void observesEveryClassAndReferencesBeyondTheAnalyzerDetailSample() throws Exception {
        Map<String, byte[]> classes = new TreeMap<>();
        for (int classIndex = 0; classIndex < 2; classIndex++) {
            String owner = "sample/Many" + classIndex;
            ClassWriter writer = startClass(owner);
            for (int field = 0; field < 270; field++) {
                writer.visitField(Opcodes.ACC_PUBLIC, "field" + field,
                        "Lexternal/Type" + classIndex + "_" + field + ";", null, null).visitEnd();
            }
            writer.visitEnd();
            classes.put(owner + ".class", writer.toByteArray());
        }
        Path input = TestJarBuilder.write(temporary.resolve("many-references.jar"), classes);

        MappingReport report = new MappingAnalysisService().analyze(input, incompleteInputs());

        assertEquals(2, report.readableClassCount());
        assertEquals(540, report.entries().stream().filter(e -> e.rule().legacy().kind() == SymbolKind.CLASS).count());
        assertEquals(1080, report.entries().size());
        assertEquals(List.of("sample/Many0.class"), entry(report, LegacySymbol.classSymbol("external/Type0_269")).classEntries());
        assertEquals(List.of("sample/Many1.class"), entry(report, LegacySymbol.classSymbol("external/Type1_269")).classEntries());
        assertFalse(report.referencesTruncated());
    }

    @Test
    void neverRunsStaticInitializersOrChangesInputBytes() throws Exception {
        String property = "legacybridge.mapping.fixture.initialized";
        String beforeProperty = System.getProperty(property);
        ClassWriter writer = startClass("sample/Initializer");
        writer.visitField(Opcodes.ACC_PUBLIC, "reference", "Lexternal/Type;", null, null).visitEnd();
        var initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn(property);
        initializer.visitLdcInsn(String.valueOf(beforeProperty) + "-executed-by-fixture");
        initializer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        initializer.visitInsn(Opcodes.POP);
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(0, 0);
        initializer.visitEnd();
        writer.visitEnd();
        Path input = TestJarBuilder.write(temporary.resolve("initializer.jar"),
                Map.of("sample/Initializer.class", writer.toByteArray()));
        byte[] before = Files.readAllBytes(input);

        MappingReport report = new MappingAnalysisService().analyze(input, incompleteInputs());

        assertEquals(beforeProperty, System.getProperty(property));
        assertArrayEquals(before, Files.readAllBytes(input));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(before)), report.input().sha256());
        assertEquals(false, report.toTree().get("bytecodeModified"));
        assertEquals("NOT_IMPLEMENTED", report.toTree().get("conversionStatus"));
        assertTrue(report.entries().stream().noneMatch(e -> e.rule().automaticRewriteAllowed()));
    }

    @Test
    void incompleteInputsAndUnreadableClassesAreExplicitlyPartial() throws Exception {
        ClassWriter writer = startClass("sample/Readable");
        writer.visitField(Opcodes.ACC_PUBLIC, "value", "Lexternal/Type;", null, null).visitEnd();
        writer.visitEnd();
        Path input = TestJarBuilder.write(temporary.resolve("partial.jar"), Map.of(
                "sample/Readable.class", writer.toByteArray(), "sample/Unreadable.class", new byte[] {1, 2, 3, 4}));

        MappingReport report = new MappingAnalysisService().analyze(input, incompleteInputs());

        assertFalse(report.sourcesComplete());
        assertEquals(1, report.readableClassCount());
        assertEquals(1, report.unreadableClassCount());
        assertEquals("PARTIAL", report.toTree().get("analysisStatus"));
        assertTrue(report.requiresReview());
        assertTrue(report.diagnostics().stream().anyMatch(d -> d.startsWith("MISSING_INPUT:")));
        assertTrue(report.diagnostics().stream().anyMatch(d -> d.contains("Unreadable classes")));
        assertTrue(report.entries().stream().allMatch(e -> e.rule().status() == MappingStatus.MANUAL));
        assertTrue(report.entries().stream().allMatch(e -> e.rule().target() == null));
    }

    @Test
    void excludesModDefinedOwnersAndKeepsExternalCallsWithoutAuthorizingRewrites() throws Exception {
        ClassWriter caller = startClass("sample/Caller");
        caller.visitField(Opcodes.ACC_PUBLIC, "local", "Lsample/Local;", null, null).visitEnd();
        var method = caller.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()V", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "sample/Local", "work", "()V", false);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "external/Service", "touch", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        caller.visitEnd();
        ClassWriter local = startClass("sample/Local");
        local.visitEnd();
        Path input = TestJarBuilder.write(temporary.resolve("local.jar"), Map.of(
                "sample/Caller.class", caller.toByteArray(), "sample/Local.class", local.toByteArray()));

        MappingReport report = new MappingAnalysisService().analyze(input, incompleteInputs());

        assertEquals(2, report.readableClassCount());
        assertTrue(report.entries().stream().noneMatch(e -> e.rule().legacy().owner().startsWith("sample/")));
        assertNotNull(entry(report, LegacySymbol.method("external/Service", "touch", "()V")));
        MappingRule descriptor = entry(report, LegacySymbol.descriptor("()V")).rule();
        assertEquals(MappingStatus.RESOLVED, descriptor.status());
        assertEquals(MappingConfidence.HIGH, descriptor.confidence());
        assertTrue(report.entries().stream().noneMatch(e -> e.rule().automaticRewriteAllowed()));
    }

    private static MappingReport.Entry entry(MappingReport report, LegacySymbol symbol) {
        return report.entries().stream().filter(e -> e.rule().legacy().equals(symbol)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing mapping report symbol: " + symbol));
    }

    private static ClassWriter startClass(String owner) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, owner, null, "java/lang/Object", null);
        return writer;
    }

    private static MappingInputs incompleteInputs() {
        var empty = IMappingBuilder.create("source", "target").build().getMap("source", "target");
        return new MappingInputs(empty, empty, empty, empty, ApiIndex.empty(), ApiIndex.empty(),
                List.of(), List.of("MISSING_INPUT: synthetic mapping fixture has no platform mapping sources"), false);
    }
}
