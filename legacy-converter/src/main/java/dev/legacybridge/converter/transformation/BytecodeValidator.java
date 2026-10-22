package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStoppedException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.objectweb.asm.util.CheckClassAdapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline ASM validation, never definition or execution of mod classes.
 * The basic form checks class-file structure and stack/dataflow categories without requiring an
 * obsolete Forge classpath. The hierarchy-aware form additionally checks reference assignability.
 * Neither substitutes for JVM linkage, access checks, constructor initialization verification, or
 * actual Forge launch tests. Unknown hierarchy required by the strict form is a hard failure.
 *
 * <p>Uses the real ASM 9.9.1 CheckClassAdapter dataflow constructor. Its static verify method is
 * deliberately not used because the default SimpleVerifier can load referenced classes.</p>
 */
public final class BytecodeValidator {
    private BytecodeValidator() { }

    public static void validate(byte[] bytecode) {
        validateInternal(bytecode, null);
    }

    public static void validate(byte[] bytecode, Map<String, byte[]> hierarchyClasses) {
        if (hierarchyClasses == null) throw new TransformationException("Offline hierarchy must not be null");
        validateInternal(bytecode, hierarchyClasses);
    }

    private static void validateInternal(byte[] bytecode, Map<String, byte[]> supplied) {
        OperationControl.checkpoint();
        String owner = "<unreadable class>";
        try {
            if (bytecode == null || bytecode.length < 8 || (bytecode[0] & 255) != 0xCA
                    || (bytecode[1] & 255) != 0xFE || (bytecode[2] & 255) != 0xBA || (bytecode[3] & 255) != 0xBE)
                throw new IllegalArgumentException("Missing or invalid CAFEBABE class-file header");
            ClassReader reader = new ClassReader(bytecode);
            owner = reader.getClassName();
            // ASM 9.9.1 activates its CheckFrameAnalyzer only when the direct delegate is a
            // ClassWriter that does not recompute frames/maxima. A ClassNode delegate alone
            // merely re-infers dataflow and would silently ignore malformed StackMap tables.
            reader.accept(new CheckClassAdapter(new ClassWriter(0), true), ClassReader.EXPAND_FRAMES);
            OperationControl.checkpoint();
            ClassNode node = new ClassNode(Opcodes.ASM9);
            reader.accept(node, ClassReader.EXPAND_FRAMES);
            duplicates(node);
            ClassHierarchy hierarchy = null;
            if (supplied != null) {
                var allClasses = new HashMap<String, byte[]>(supplied);
                allClasses.remove(owner + ".class");
                allClasses.put(owner, bytecode);
                hierarchy = new ClassHierarchy(allClasses);
            }
            for (MethodNode method : node.methods) {
                OperationControl.checkpoint();
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
                BasicVerifier verifier = hierarchy == null ? new BasicVerifier() : new OfflineVerifier(node, hierarchy);
                Frame<BasicValue>[] frames = new Analyzer<BasicValue>(verifier) {
                    @Override protected void newControlFlowEdge(int from, int to) { OperationControl.checkpoint(); }
                    @Override protected boolean newControlFlowExceptionEdge(int from, int to) {
                        OperationControl.checkpoint(); return true;
                    }
                }.analyze(node.name, method);
                validateStackMaps(node, method, frames, hierarchy);
            }
        } catch (OperationStoppedException stopped) {
            throw stopped;
        } catch (TransformationException exception) {
            throw exception;
        } catch (RuntimeException | AnalyzerException exception) {
            OperationStoppedException.rethrowIfPresent(exception);
            throw new TransformationException("Invalid bytecode in " + owner + ": " + exception.getMessage());
        }
    }

    private static void duplicates(ClassNode node) {
        Set<String> fields = new HashSet<>();
        node.fields.forEach(field -> {
            if (!fields.add(field.name + "\u0000" + field.desc)) {
                throw new TransformationException("Duplicate field in " + node.name + ": " + field.name + field.desc);
            }
        });
        Set<String> methods = new HashSet<>();
        node.methods.forEach(method -> {
            if (!methods.add(method.name + method.desc)) {
                throw new TransformationException("Duplicate method in " + node.name + ": " + method.name + method.desc);
            }
        });
        if (new HashSet<>(node.interfaces).size() != node.interfaces.size()) {
            throw new TransformationException("Duplicate interface in " + node.name);
        }
    }

    private static void validateStackMaps(ClassNode owner, MethodNode method, Frame<BasicValue>[] inferred,
                                          ClassHierarchy hierarchy) {
        if ((owner.version & 0xFFFF) < Opcodes.V1_7) return;
        Set<LabelNode> targets = new HashSet<>();
        for (AbstractInsnNode instruction : method.instructions) {
            OperationControl.checkpoint();
            if (instruction instanceof JumpInsnNode jump) targets.add(jump.label);
            if (instruction instanceof TableSwitchInsnNode table) {
                targets.add(table.dflt);
                targets.addAll(table.labels);
            }
            if (instruction instanceof LookupSwitchInsnNode lookup) {
                targets.add(lookup.dflt);
                targets.addAll(lookup.labels);
            }
        }
        method.tryCatchBlocks.forEach(block -> targets.add(block.handler));
        AbstractInsnNode firstOpcode = firstOpcode(method.instructions.getFirst());
        for (LabelNode target : targets) {
            if (firstOpcode(target) == firstOpcode) continue; // Offset zero has the implicit initial frame.
            boolean found = false;
            for (AbstractInsnNode next = target.getNext(); next != null && next.getOpcode() < 0; next = next.getNext()) {
                if (next instanceof FrameNode) found = true;
            }
            if (!found) throw new TransformationException("Missing StackMap frame at branch/handler in "
                    + owner.name + "." + method.name + method.desc);
        }
        for (int index = 0; index < method.instructions.size(); index++) {
            if (!(method.instructions.get(index) instanceof FrameNode declared) || inferred[index] == null) continue;
            Frame<BasicValue> expected = inferred[index];
            List<Object> stack = declared.stack == null ? List.of() : declared.stack;
            if (stack.size() != expected.getStackSize()) {
                throw new TransformationException("StackMap stack size mismatch in " + owner.name + "." + method.name);
            }
            for (int slot = 0; slot < stack.size(); slot++) {
                checkFrameType(stack.get(slot), expected.getStack(slot), false, owner.name, method.name, hierarchy);
            }
            int slot = 0;
            for (Object local : declared.local == null ? List.of() : declared.local) {
                if (slot >= expected.getLocals()) {
                    throw new TransformationException("StackMap locals exceed max_locals in " + owner.name + "." + method.name);
                }
                checkFrameType(local, expected.getLocal(slot), true, owner.name, method.name, hierarchy);
                slot += local.equals(Opcodes.LONG) || local.equals(Opcodes.DOUBLE) ? 2 : 1;
            }
        }
    }

    private static AbstractInsnNode firstOpcode(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }

    private static void checkFrameType(Object declared, BasicValue inferred, boolean local, String owner,
                                       String method, ClassHierarchy hierarchy) {
        // A frame may intentionally discard an unused local with TOP. Constructor-uninitialized
        // labels are structural-only here; ASM BasicVerifier does not model initialization state.
        if (local && declared.equals(Opcodes.TOP)) return;
        boolean valid;
        if (declared.equals(Opcodes.INTEGER)) valid = BasicValue.INT_VALUE.equals(inferred);
        else if (declared.equals(Opcodes.FLOAT)) valid = BasicValue.FLOAT_VALUE.equals(inferred);
        else if (declared.equals(Opcodes.LONG)) valid = BasicValue.LONG_VALUE.equals(inferred);
        else if (declared.equals(Opcodes.DOUBLE)) valid = BasicValue.DOUBLE_VALUE.equals(inferred);
        else if (declared.equals(Opcodes.TOP)) valid = BasicValue.UNINITIALIZED_VALUE.equals(inferred);
        else {
            valid = inferred.isReference();
            if (valid && hierarchy != null && declared instanceof String name
                    && !inferred.getType().equals(Type.getObjectType("null"))) {
                valid = hierarchy.isAssignable(Type.getObjectType(name), inferred.getType());
            }
        }
        if (!valid) throw new TransformationException("StackMap type mismatch in " + owner + "." + method);
    }

    private static final class OfflineVerifier extends SimpleVerifier {
        private final ClassHierarchy hierarchy;

        private OfflineVerifier(ClassNode node, ClassHierarchy hierarchy) {
            super(Opcodes.ASM9, Type.getObjectType(node.name),
                    node.superName == null ? null : Type.getObjectType(node.superName),
                    node.interfaces.stream().map(Type::getObjectType).toList(),
                    (node.access & Opcodes.ACC_INTERFACE) != 0);
            this.hierarchy = hierarchy;
        }

        @Override
        protected boolean isInterface(Type type) { return hierarchy.isInterface(type); }

        @Override
        protected Type getSuperClass(Type type) { return hierarchy.superType(type); }

        @Override
        protected boolean isAssignableFrom(Type target, Type source) { return hierarchy.isAssignable(target, source); }

        @Override
        protected boolean isSubTypeOf(BasicValue value, BasicValue expected) {
            Type actualType = value.getType();
            Type expectedType = expected.getType();
            if (actualType == null || expectedType == null) return actualType == expectedType;
            if (actualType.equals(expectedType)) return true;
            if ((expectedType.getSort() == Type.OBJECT || expectedType.getSort() == Type.ARRAY)
                    && actualType.equals(NULL_TYPE)) return true;
            return hierarchy.isAssignable(expectedType, actualType);
        }

        @Override
        protected Class<?> getClass(Type type) {
            throw new TransformationException("Classloading is forbidden during offline verification: " + type);
        }
    }
}
