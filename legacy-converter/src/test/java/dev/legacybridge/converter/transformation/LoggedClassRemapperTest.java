package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.mapping.LegacySymbol;
import dev.legacybridge.converter.mapping.MappingCategory;
import dev.legacybridge.converter.mapping.TargetSymbol;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.stream.StreamSupport;

import static dev.legacybridge.converter.transformation.MappingResolverTest.*;
import static org.junit.jupiter.api.Assertions.*;

class LoggedClassRemapperTest {
    @Test void remapsClassMemberDescriptorsArraysAnnotationsHandlesAndDebugWithoutLoadingCode() {
        ClassNode original = debugFixture();
        MappingResolver resolver = resolver(classRule("mod/Old", "mod/New"),
                verified(LegacySymbol.method("mod/Old", "oldValue", "()Lmod/Old;"),
                        TargetSymbol.method("mod/New", "newValue", "()Lmod/New;"), MappingCategory.DESCRIPTOR_CHANGE),
                verified(LegacySymbol.field("mod/Old", "oldField", "[Lmod/Old;"),
                        TargetSymbol.field("mod/New", "newField", "[Lmod/New;"), MappingCategory.DESCRIPTOR_CHANGE));
        TransformationLog log = new TransformationLog();
        ClassNode target = LoggedClassRemapper.remap(original, resolver, log);
        assertEquals("mod/New", target.name);
        assertEquals("Old.java", target.sourceFile);
        assertEquals(original.sourceDebug, target.sourceDebug);
        assertEquals("mod/Old", original.name, "Input tree is not mutated");
        assertEquals("newField", target.fields.get(0).name);
        assertEquals("[Lmod/New;", target.fields.get(0).desc);
        assertEquals("Ljava/util/List<Lmod/New;>;", target.fields.get(1).signature);
        assertEquals("Lmod/New;", target.visibleAnnotations.get(0).desc);
        MethodNode method = target.methods.stream().filter(candidate -> candidate.name.equals("newValue")).findFirst().orElseThrow();
        assertEquals("()Lmod/New;", method.desc);
        assertEquals("Lmod/New;", method.localVariables.get(0).desc);
        assertTrue(StreamSupport.stream(method.instructions.spliterator(), false)
                .anyMatch(node -> node instanceof LineNumberNode line && line.line == 17));
        MethodNode references = target.methods.stream().filter(candidate -> candidate.name.equals("references")).findFirst().orElseThrow();
        var nodes = StreamSupport.stream(references.instructions.spliterator(), false).toList();
        FieldInsnNode field = (FieldInsnNode) nodes.stream().filter(FieldInsnNode.class::isInstance).findFirst().orElseThrow();
        assertEquals("mod/New", field.owner);
        assertEquals("newField", field.name);
        assertEquals("[Lmod/New;", field.desc);
        Handle handle = (Handle) nodes.stream().filter(LdcInsnNode.class::isInstance).map(LdcInsnNode.class::cast)
                .map(node -> node.cst).filter(Handle.class::isInstance).findFirst().orElseThrow();
        assertEquals("mod/New", handle.getOwner());
        assertEquals("newValue", handle.getName());
        assertEquals("()Lmod/New;", handle.getDesc());
        assertTrue(log.entries().stream().map(TransformationLog.Entry::kind).toList().containsAll(List.of(
                TransformationLog.Kind.CLASS_REMAP, TransformationLog.Kind.METHOD_REMAP,
                TransformationLog.Kind.FIELD_REMAP, TransformationLog.Kind.DESCRIPTOR_REMAP)));
        assertTrue(log.entries().stream().allMatch(entry -> entry.location().contains("Old.java") && !entry.adapterId().isBlank()));
        ClassWriter writer = new ClassWriter(0);
        target.accept(writer);
        assertEquals("mod/New", new ClassReader(writer.toByteArray()).getClassName());
    }

    @Test void descriptorsAreAnExplicitIdempotentLaterStageThatDoesNotRenameDeclarations() {
        MappingResolver resolver = resolver(classRule("mod/Old", "mod/New"));
        DescriptorTransformer stage = new DescriptorTransformer(resolver);
        ClassNode original = debugFixture();
        TransformationLog descriptorLog = new TransformationLog();
        ClassNode descriptorsOnly = stage.transform(original, descriptorLog);
        assertEquals("mod/Old", descriptorsOnly.name);
        assertEquals("oldField", descriptorsOnly.fields.get(0).name);
        assertEquals("[Lmod/New;", descriptorsOnly.fields.get(0).desc);
        assertTrue(descriptorLog.entries().stream().allMatch(entry -> entry.kind() == TransformationLog.Kind.DESCRIPTOR_REMAP));
        ClassNode remapped = LoggedClassRemapper.remap(original, resolver, new TransformationLog());
        TransformationLog secondLog = new TransformationLog();
        ClassNode repeated = stage.transform(remapped, secondLog);
        assertEquals("mod/New", repeated.name);
        assertEquals(remapped.methods.get(0).desc, repeated.methods.get(0).desc);
        assertTrue(secondLog.entries().isEmpty());
    }

    @Test void protectedClassesCannotBePassedDirectlyToDescriptorStage() {
        ClassNode source = debugFixture();
        source.name = "net/minecraft/ForgedMod";
        assertThrows(TransformationException.class,
                () -> new DescriptorTransformer(resolver()).transform(source, new TransformationLog()));
    }

    @Test void residualExternalDescriptorChangeCannotBypassExactMemberConfidenceInLaterStage() {
        ClassNode source = debugFixture();
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "external", "()V", null, null);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "external/Library", "accept", "(Lmod/Old;)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        source.methods.add(method);
        var stage = new DescriptorTransformer(resolver(classRule("mod/Old", "mod/New")));
        assertThrows(TransformationException.class, () -> stage.transform(source, new TransformationLog()));
    }

    @Test void unsupportedInvokedynamicSamRenameAbortsInsteadOfLeavingTheBootstrapInconsistent() {
        ClassNode source = debugFixture();
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "lambda", "()V", null, null);
        method.instructions.add(new InvokeDynamicInsnNode("run", "()Lmod/Task;",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                Type.getMethodType("()V"), new Handle(Opcodes.H_INVOKESTATIC, "mod/Old", "references", "()V", false), Type.getMethodType("()V")));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        source.methods.add(method);
        MappingResolver resolver = resolver(verified(LegacySymbol.method("mod/Task", "run", "()V"),
                TargetSymbol.method("mod/Task", "execute", "()V"), MappingCategory.PURE_RENAME));
        assertThrows(TransformationException.class, () -> LoggedClassRemapper.remap(source, resolver, new TransformationLog()));
    }

    @Test void noRulesLeaveTheTreeAndLogUnchanged() {
        ClassNode source = debugFixture();
        TransformationLog log = new TransformationLog();
        ClassNode output = LoggedClassRemapper.remap(source, resolver(), log);
        assertEquals(source.name, output.name);
        assertEquals(source.fields.get(0).desc, output.fields.get(0).desc);
        assertEquals(source.methods.get(0).desc, output.methods.get(0).desc);
        assertTrue(log.entries().isEmpty());
    }

    private static ClassNode debugFixture() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "mod/Old", null, "java/lang/Object", null);
        writer.visitSource("Old.java", "SMAP\nOld.java\nJava\n*S Java\n*F\n+ 1 Old.java\nmod/Old.java\n*L\n1#1,30:1\n*E\n");
        writer.visitAnnotation("Lmod/Old;", true).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "oldField", "[Lmod/Old;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "values", "Ljava/util/List;", "Ljava/util/List<Lmod/Old;>;", null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "oldValue", "()Lmod/Old;", null, null);
        method.visitCode();
        Label begin = new Label(), end = new Label();
        method.visitLabel(begin);
        method.visitLineNumber(17, begin);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLabel(end);
        method.visitLocalVariable("this", "Lmod/Old;", null, begin, end, 0);
        method.visitMaxs(1, 1);
        method.visitEnd();
        method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "references", "()V", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "mod/Old", "oldField", "[Lmod/Old;");
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(new Handle(Opcodes.H_INVOKEVIRTUAL, "mod/Old", "oldValue", "()Lmod/Old;", false));
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(Type.getType("[Lmod/Old;"));
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        ClassNode result = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(result, 0);
        return result;
    }
}
