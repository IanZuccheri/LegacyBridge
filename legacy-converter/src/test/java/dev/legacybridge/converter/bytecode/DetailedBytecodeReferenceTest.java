package dev.legacybridge.converter.bytecode;

import dev.legacybridge.converter.namespace.NamespaceSummary;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailedBytecodeReferenceTest {
    private final BytecodeScanner scanner = new BytecodeScanner();

    @Test
    void emitsCompleteMemberAndTypeOccurrencesWithoutLoadingClasses() {
        List<BytecodeReference> emitted = new ArrayList<>();

        ClassBytecodeScan scan = scanner.scan(
                "fixture/Child.class",
                referenceCarrier(),
                emitted::add
        );

        assertEquals(emitted.size(), scan.references().occurrences());
        assertReference(
                emitted,
                BytecodeReference.hierarchy(BytecodeReferenceKind.SUPERCLASS, "fixture/Base")
        );
        assertReference(
                emitted,
                BytecodeReference.hierarchy(BytecodeReferenceKind.INTERFACE, "api/Contract")
        );
        assertReference(
                emitted,
                BytecodeReference.member(
                        BytecodeReferenceKind.FIELD,
                        "net/minecraftforge/common/MinecraftForge",
                        "EVENT_BUS",
                        "Lnet/minecraftforge/eventbus/api/IEventBus;"
                )
        );
        assertReference(
                emitted,
                BytecodeReference.member(
                        BytecodeReferenceKind.METHOD,
                        "net/minecraft/world/World",
                        "getDayTime",
                        "()J"
                )
        );
        assertReference(
                emitted,
                BytecodeReference.member(
                        BytecodeReferenceKind.METHOD,
                        "bootstrap/Factory",
                        "bootstrap",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                                + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"
                )
        );
        assertReference(
                emitted,
                BytecodeReference.member(
                        BytecodeReferenceKind.FIELD,
                        "bootstrap/Constants",
                        "FLAG",
                        "Z"
                )
        );
        assertType(emitted, "generic/Bound");
        assertType(emitted, "annotation/Marker");
        assertType(emitted, "annotation/Value");
        assertType(emitted, "net/minecraft/world/World");
        assertType(emitted, "net/minecraftforge/eventbus/api/IEventBus");
        assertType(emitted, "thirdparty/Widget");
        assertType(emitted, "dynamic/Value");
        assertType(emitted, "dynamic/Input");
        assertTrue(scan.references().occurrences(BytecodeReferenceKind.TYPE) > 0);
        assertFalse(scan.references().truncated());
    }

    @Test
    void aggregateIndexesDefinitionsAndFiltersRetainedExternalOwners() {
        BytecodeScanAggregate aggregate = scanner.scanAll(Map.of(
                "fixture/Child.class", referenceCarrier(),
                "fixture/Base.class", emptyClass("fixture/Base")
        ));

        ArchiveReferenceInventory inventory = aggregate.referenceInventory();
        assertEquals(Set.of("fixture/Base", "fixture/Child"), inventory.definitions().owners());
        assertEquals(2, inventory.classDetails().size());
        assertFalse(inventory.classDetailsTruncated());
        assertTrue(inventory.definitionIndexComplete());
        assertTrue(inventory.referenceSamplesComplete());
        assertTrue(inventory.externalSamplesComplete());
        assertTrue(inventory.references().samples().stream()
                .anyMatch(reference -> reference.owner().equals("fixture/Base")));
        assertFalse(inventory.externalSamples().stream()
                .anyMatch(reference -> reference.owner().equals("fixture/Base")));
        assertTrue(inventory.externalSamples().stream()
                .anyMatch(reference -> reference.owner().equals("net/minecraft/world/World")));
    }

    @Test
    void mergingIsOrderIndependentAndKeepsExactCountsWhenSamplesAreBounded() {
        List<BytecodeReference> references = new ArrayList<>();
        for (int index = 0; index < 512; index++) {
            references.add(BytecodeReference.member(
                    BytecodeReferenceKind.METHOD,
                    "external/Owner" + String.format(Locale.ROOT, "%04d", index),
                    "call",
                    "()V"
            ));
        }

        BytecodeReferenceInventory.Accumulator forward = BytecodeReferenceInventory.accumulator();
        references.forEach(forward::record);
        Collections.reverse(references);
        BytecodeReferenceInventory.Accumulator reverse = BytecodeReferenceInventory.accumulator();
        references.forEach(reverse::record);

        BytecodeReferenceInventory inventory = forward.snapshot();
        assertEquals(inventory, reverse.snapshot());
        assertEquals(512, inventory.occurrences());
        assertEquals(512, inventory.occurrences(BytecodeReferenceKind.METHOD));
        assertEquals(BytecodeReferenceInventory.RETAINED_REFERENCE_LIMIT, inventory.samples().size());
        assertTrue(inventory.truncated());
        assertEquals("external/Owner0000", inventory.samples().iterator().next().owner());
    }

    @Test
    void hierarchyReferencesOutrankOrdinarySamples() {
        List<BytecodeReference> references = new ArrayList<>();
        for (int index = 0; index < 512; index++) {
            references.add(BytecodeReference.type(
                    "aaa/Type" + String.format(Locale.ROOT, "%04d", index),
                    ""
            ));
        }
        references.add(BytecodeReference.hierarchy(BytecodeReferenceKind.SUPERCLASS, "zzz/Base"));
        references.add(BytecodeReference.hierarchy(BytecodeReferenceKind.INTERFACE, "zzz/Contract"));

        BytecodeReferenceInventory.Accumulator accumulator = BytecodeReferenceInventory.accumulator();
        references.forEach(accumulator::record);
        BytecodeReferenceInventory inventory = accumulator.snapshot();

        assertTrue(inventory.samples().contains(
                BytecodeReference.hierarchy(BytecodeReferenceKind.SUPERCLASS, "zzz/Base")));
        assertTrue(inventory.samples().contains(
                BytecodeReference.hierarchy(BytecodeReferenceKind.INTERFACE, "zzz/Contract")));
        assertTrue(inventory.truncated());
    }

    @Test
    void boundsLongRetainedReferencesAndStopsSamplingWork() {
        String descriptor = "(" + "Lvery/long/Type;".repeat(700) + ")V";
        BytecodeReference reference = BytecodeReference.member(
                BytecodeReferenceKind.METHOD,
                "external/Owner",
                "call",
                descriptor
        );
        BytecodeReferenceInventory.Accumulator accumulator = BytecodeReferenceInventory.accumulator();
        for (int index = 0; index < 5_000; index++) {
            accumulator.record(reference);
        }

        BytecodeReferenceInventory inventory = accumulator.snapshot();
        assertEquals(5_000, inventory.occurrences());
        assertEquals(1, inventory.samples().size());
        assertTrue(inventory.truncated());
        assertTrue(inventory.samples().iterator().next().descriptor().length()
                <= BytecodeReference.RETAINED_DESCRIPTOR_CHARS);
    }

    @Test
    void archiveClassDetailsAreBoundedAndExplicitlyMarked() {
        List<ClassBytecodeScan> scans = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            String suffix = String.format(Locale.ROOT, "%04d", index);
            scans.add(new ClassBytecodeScan(
                    "fixture/Entry" + suffix + ".class",
                    "fixture/Entry" + suffix,
                    0,
                    52,
                    false,
                    Opcodes.ACC_PUBLIC,
                    BytecodeFeatureCounts.EMPTY,
                    NamespaceSummary.EMPTY,
                    BytecodeReferenceInventory.EMPTY
            ));
        }

        BytecodeScanAccumulator forward = new BytecodeScanAccumulator();
        scans.forEach(forward::accept);
        Collections.reverse(scans);
        BytecodeScanAccumulator reverse = new BytecodeScanAccumulator();
        scans.forEach(reverse::accept);

        ArchiveReferenceInventory inventory = forward.snapshot().referenceInventory();
        assertEquals(inventory, reverse.snapshot().referenceInventory());
        assertEquals(300, inventory.definitions().occurrences());
        assertEquals(300, inventory.definitions().owners().size());
        assertEquals(ArchiveReferenceInventory.RETAINED_CLASS_DETAIL_LIMIT, inventory.classDetails().size());
        assertTrue(inventory.classDetailsTruncated());
        assertEquals("fixture/Entry0000.class", inventory.classDetails().get(0).entryName());
        assertEquals("fixture/Entry0255.class", inventory.classDetails().get(255).entryName());
    }

    private static void assertReference(List<BytecodeReference> references, BytecodeReference expected) {
        assertTrue(references.contains(expected), () -> "Missing reference: " + expected);
    }

    private static void assertType(List<BytecodeReference> references, String owner) {
        assertTrue(
                references.stream().anyMatch(reference ->
                        reference.kind() == BytecodeReferenceKind.TYPE && reference.owner().equals(owner)),
                () -> "Missing type reference: " + owner
        );
    }

    private static byte[] emptyClass(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] referenceCarrier() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                "fixture/Child",
                "<T:Lgeneric/Bound;>Lfixture/Base;Lgeneric/Marker<TT;>;",
                "fixture/Base",
                new String[]{"api/Contract"}
        );
        AnnotationVisitor annotation = writer.visitAnnotation("Lannotation/Marker;", true);
        annotation.visit("value", Type.getType("Lannotation/Value;"));
        annotation.visitEnd();
        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "world",
                "Lnet/minecraft/world/World;",
                null,
                null
        ).visitEnd();

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "exercise",
                "(Lmethod/Input;)Lmethod/Output;",
                null,
                new String[]{"method/Failure"}
        );
        method.visitCode();
        method.visitFieldInsn(
                Opcodes.GETSTATIC,
                "net/minecraftforge/common/MinecraftForge",
                "EVENT_BUS",
                "Lnet/minecraftforge/eventbus/api/IEventBus;"
        );
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/World",
                "getDayTime",
                "()J",
                false
        );
        method.visitInsn(Opcodes.POP2);
        method.visitTypeInsn(Opcodes.NEW, "thirdparty/Widget");
        method.visitInsn(Opcodes.POP);

        Handle bootstrap = new Handle(
                Opcodes.H_INVOKESTATIC,
                "bootstrap/Factory",
                "bootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false
        );
        Handle fieldHandle = new Handle(
                Opcodes.H_GETSTATIC,
                "bootstrap/Constants",
                "FLAG",
                "Z",
                false
        );
        method.visitInvokeDynamicInsn("run", "(Lindy/Input;)Lindy/Output;", bootstrap, fieldHandle);
        method.visitInsn(Opcodes.POP);

        Handle dynamicBootstrap = new Handle(
                Opcodes.H_INVOKESTATIC,
                "dynamic/Factory",
                "make",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)"
                        + "Ljava/lang/Object;",
                false
        );
        method.visitLdcInsn(new ConstantDynamic(
                "VALUE",
                "Ldynamic/Value;",
                dynamicBootstrap,
                Type.getType("Ldynamic/Input;")
        ));
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
