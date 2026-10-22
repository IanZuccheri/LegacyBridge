package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

/**
 * Reference/primitive dataflow plus a deliberately restricted uninitialized-object language.
 * JVMS 4.10.1.9: initialize aliases together after the one permitted invokespecial. Unlike
 * BasicVerifier, NEW and uninitializedThis are not treated as ordinary usable object values.
 * Branches/handlers and local storage of uninitialized references are explicitly unsupported.
 */
final class InitializationVerifier extends SimpleVerifier {
    private final ClassNode owner;
    private final MethodNode method;
    private final LinkageHierarchy hierarchy;
    private final boolean ownDelegation;
    private final Object thisToken = new Object();

    private InitializationVerifier(ClassNode owner, MethodNode method, LinkageHierarchy hierarchy, boolean ownDelegation) {
        super(Opcodes.ASM9, Type.getObjectType(owner.name), owner.superName == null ? null : Type.getObjectType(owner.superName),
                owner.interfaces.stream().map(Type::getObjectType).toList(), (owner.access & Opcodes.ACC_INTERFACE) != 0);
        this.owner = owner; this.method = method; this.hierarchy = hierarchy;
        this.ownDelegation = ownDelegation;
    }

    static Frame<BasicValue>[] analyze(ClassNode owner, MethodNode method, LinkageHierarchy hierarchy) throws AnalyzerException {
        return analyze(owner, method, hierarchy, false);
    }
    static Frame<BasicValue>[] analyze(ClassNode owner, MethodNode method, LinkageHierarchy hierarchy, boolean ownDelegation) throws AnalyzerException {
        InitializationVerifier interpreter = new InitializationVerifier(owner, method, hierarchy, ownDelegation);
        Frame<BasicValue>[] frames = new Analyzer<BasicValue>(interpreter) {
            @Override protected Frame<BasicValue> newFrame(int locals, int stack) { return interpreter.new CheckedFrame(locals, stack); }
            @Override protected Frame<BasicValue> newFrame(Frame<? extends BasicValue> frame) { return interpreter.new CheckedFrame(frame); }
            @Override protected void newControlFlowEdge(int from, int to) { OperationControl.checkpoint(); }
            @Override protected boolean newControlFlowExceptionEdge(int from, int to) { OperationControl.checkpoint(); return true; }
        }.analyze(owner.name, method);
        for (var block : method.tryCatchBlocks) {
            int start = method.instructions.indexOf(block.start), end = method.instructions.indexOf(block.end);
            for (int index = start; index < end; index++) {
                OperationControl.checkpoint();
                if (frames[index] != null && uninitialized(frames[index]))
                    LinkageHierarchy.fail("UNINITIALIZED_HANDLER_UNSUPPORTED", owner.name + "." + method.name);
            }
        }
        return frames;
    }

    @Override public BasicValue newParameterValue(boolean instance, int local, Type type) {
        if (instance && local == 0) return method.name.equals("<init>")
                ? new Uninitialized(type, thisToken, true) : new ThisValue(type, thisToken);
        return super.newParameterValue(instance, local, type);
    }

    @Override public BasicValue newValue(Type type) { return referenceValue(super.newValue(type)); }

    @Override public BasicValue newOperation(AbstractInsnNode instruction) throws AnalyzerException {
        OperationControl.checkpoint();
        if (instruction.getOpcode() == Opcodes.NEW)
            return new Uninitialized(Type.getObjectType(((TypeInsnNode) instruction).desc), instruction, false);
        return super.newOperation(instruction);
    }

    @Override public BasicValue copyOperation(AbstractInsnNode instruction, BasicValue value) throws AnalyzerException {
        if (value instanceof Uninitialized uninitialized) {
            boolean allowed = instruction.getOpcode() == Opcodes.DUP && !uninitialized.isThis
                    || instruction.getOpcode() == Opcodes.ALOAD && uninitialized.isThis && ((VarInsnNode) instruction).var == 0;
            if (!allowed) reject(instruction, "UNINITIALIZED_ALIAS_UNSUPPORTED");
        }
        return super.copyOperation(instruction, value);
    }

    @Override public BasicValue unaryOperation(AbstractInsnNode instruction, BasicValue value) throws AnalyzerException {
        initialized(instruction, value); return super.unaryOperation(instruction, value);
    }
    @Override public BasicValue binaryOperation(AbstractInsnNode instruction, BasicValue first, BasicValue second) throws AnalyzerException {
        initialized(instruction, first); initialized(instruction, second); return super.binaryOperation(instruction, first, second);
    }
    @Override public BasicValue ternaryOperation(AbstractInsnNode instruction, BasicValue first, BasicValue second, BasicValue third) throws AnalyzerException {
        initialized(instruction, first); initialized(instruction, second); initialized(instruction, third);
        return super.ternaryOperation(instruction, first, second, third);
    }

    @Override public BasicValue naryOperation(AbstractInsnNode instruction, List<? extends BasicValue> values) throws AnalyzerException {
        boolean constructor = instruction instanceof MethodInsnNode call && call.name.equals("<init>");
        for (int index = 0; index < values.size(); index++) {
            if (constructor && index == 0) {
                if (!(values.get(0) instanceof Uninitialized)) reject(instruction, "CONSTRUCTOR_RECEIVER_ALREADY_INITIALIZED");
            } else initialized(instruction, values.get(index));
        }
        return super.naryOperation(instruction, values);
    }

    @Override public void returnOperation(AbstractInsnNode instruction, BasicValue value, BasicValue expected) throws AnalyzerException {
        initialized(instruction, value); super.returnOperation(instruction, value, expected);
    }

    @Override public BasicValue merge(BasicValue first, BasicValue second) {
        if (first instanceof Uninitialized || second instanceof Uninitialized) {
            if (first.equals(second) && second.equals(first)) return first;
            LinkageHierarchy.fail("UNINITIALIZED_MERGE_UNSUPPORTED", owner.name + "." + method.name);
        }
        if (first instanceof ThisValue && second instanceof ThisValue && first.equals(second)) return first;
        if (first instanceof ThisValue) first = new ReferenceValue(first.getType());
        if (second instanceof ThisValue) second = new ReferenceValue(second.getType());
        return referenceValue(super.merge(first, second));
    }

    @Override protected boolean isInterface(Type type) { return type.getSort() == Type.OBJECT && hierarchy.isInterface(type.getInternalName()); }
    @Override protected Type getSuperClass(Type type) {
        if (type.getSort() == Type.ARRAY) return Type.getObjectType("java/lang/Object");
        String parent = hierarchy.type(type.getInternalName()).superName(); return parent == null ? null : Type.getObjectType(parent);
    }
    @Override protected boolean isAssignableFrom(Type target, Type actual) { return hierarchy.assignable(target, actual); }
    @Override protected boolean isSubTypeOf(BasicValue value, BasicValue expected) { return hierarchy.assignable(expected.getType(), value.getType()); }
    @Override protected Class<?> getClass(Type type) { throw new TransformationException("CLASSLOADING_FORBIDDEN: " + type); }

    static boolean provenThis(BasicValue value) { return value instanceof ThisValue; }

    private final class CheckedFrame extends Frame<BasicValue> {
        CheckedFrame(int locals, int stack) { super(locals, stack); }
        CheckedFrame(Frame<? extends BasicValue> frame) { super(frame); }

        @Override public void execute(AbstractInsnNode instruction, Interpreter<BasicValue> interpreter) throws AnalyzerException {
            OperationControl.checkpoint();
            int opcode = instruction.getOpcode();
            if (method.name.equals("<init>") && instruction instanceof VarInsnNode variable && variable.var == 0
                    && opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) reject(instruction, "CONSTRUCTOR_LOCAL_ZERO_REWRITE_UNSUPPORTED");
            if ((instruction instanceof JumpInsnNode || instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode)
                    && uninitialized(this)) reject(instruction, "UNINITIALIZED_CONTROL_FLOW_UNSUPPORTED");
            if ((opcode == Opcodes.POP || opcode == Opcodes.POP2 || opcode == Opcodes.SWAP) && stackHasUninitialized(this))
                reject(instruction, "UNINITIALIZED_STACK_MANIPULATION_UNSUPPORTED");
            if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN || opcode == Opcodes.ATHROW)
                    && uninitialized(this)) reject(instruction, "UNINITIALIZED_AT_EXIT");
            Uninitialized initializing = null;
            if (instruction instanceof MethodInsnNode call && call.name.equals("<init>")) {
                int receiver = getStackSize() - Type.getArgumentTypes(call.desc).length - 1;
                if (receiver < 0 || !(getStack(receiver) instanceof Uninitialized)) reject(instruction, "INVALID_CONSTRUCTOR_RECEIVER");
                initializing = (Uninitialized) getStack(receiver);
                if (call.getOpcode() != Opcodes.INVOKESPECIAL || call.itf) reject(instruction, "INVALID_CONSTRUCTOR_OPCODE");
                if (initializing.isThis) {
                    if (!method.name.equals("<init>") || !(call.owner.equals(owner.superName) || ownDelegation && call.owner.equals(owner.name)))
                        reject(instruction, "CONSTRUCTOR_DELEGATION_UNSUPPORTED");
                } else if (!initializing.getType().getInternalName().equals(call.owner)) reject(instruction, "NEW_CONSTRUCTOR_OWNER_MISMATCH");
            }
            super.execute(instruction, interpreter);
            if (initializing != null) {
                BasicValue initialized = initializing.isThis ? new ThisValue(initializing.getType(), thisToken) : new ReferenceValue(initializing.getType());
                for (int index = 0; index < getLocals(); index++) if (initializing.equals(getLocal(index))) setLocal(index, initialized);
                for (int index = 0; index < getStackSize(); index++) if (initializing.equals(getStack(index))) setStack(index, initialized);
            }
        }
    }

    private static boolean uninitialized(Frame<BasicValue> frame) {
        for (int index = 0; index < frame.getLocals(); index++) if (frame.getLocal(index) instanceof Uninitialized) return true;
        return stackHasUninitialized(frame);
    }
    private static boolean stackHasUninitialized(Frame<BasicValue> frame) {
        for (int index = 0; index < frame.getStackSize(); index++) if (frame.getStack(index) instanceof Uninitialized) return true;
        return false;
    }
    private void initialized(AbstractInsnNode instruction, BasicValue value) throws AnalyzerException { if (value instanceof Uninitialized) reject(instruction, "UNINITIALIZED_REFERENCE_USE"); }
    private void reject(AbstractInsnNode instruction, String code) throws AnalyzerException { throw new AnalyzerException(instruction, code + ": " + owner.name + "." + method.name); }

    private static BasicValue referenceValue(BasicValue value) {
        if (value == null || value.getType() == null || !LinkageHierarchy.reference(value.getType()) || value instanceof ReferenceValue) return value;
        return new ReferenceValue(value.getType());
    }

    /** Symmetric equality matters: Frame.merge compares the new value to the previous marker. */
    private static class ReferenceValue extends BasicValue {
        ReferenceValue(Type type) { super(type); }
        @Override public boolean equals(Object other) { return other != null && other.getClass() == ReferenceValue.class && getType().equals(((ReferenceValue) other).getType()); }
        @Override public int hashCode() { return getType().hashCode(); }
    }

    private static final class Uninitialized extends BasicValue {
        private final Object token;
        private final boolean isThis;
        Uninitialized(Type type, Object token, boolean isThis) { super(type); this.token = token; this.isThis = isThis; }
        @Override public boolean equals(Object other) { return other instanceof Uninitialized value && token == value.token; }
        @Override public int hashCode() { return System.identityHashCode(token); }
    }
    private static final class ThisValue extends ReferenceValue {
        private final Object token;
        ThisValue(Type type, Object token) { super(type); this.token = token; }
        @Override public boolean equals(Object other) { return other instanceof ThisValue value && token == value.token; }
        @Override public int hashCode() { return System.identityHashCode(token); }
    }
}
