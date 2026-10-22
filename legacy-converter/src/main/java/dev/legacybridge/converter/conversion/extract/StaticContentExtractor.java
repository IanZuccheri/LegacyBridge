package dev.legacybridge.converter.conversion.extract;

import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.operation.OperationControl;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static dev.legacybridge.converter.conversion.extract.LegacyApiNames.Refusal;

/**
 * A bounded abstract interpreter for one deliberately finite registration language.
 * This class never creates a class loader, invokes mod methods, or writes class bytes.
 * Any instruction, declaration or API outside the language rejects the entire mod.
 */
public final class StaticContentExtractor {
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String IP = ITEM + "$Properties";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String BP = "net/minecraft/block/AbstractBlock$Properties";
    private static final String BLOCK_ITEM = "net/minecraft/item/BlockItem";
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String SOUND_TYPE = "net/minecraft/block/SoundType";
    private static final String TAB = "net/minecraft/item/ItemGroup";
    private static final String RARITY = "net/minecraft/item/Rarity";
    private static final String SOUND = "net/minecraft/util/SoundEvent";
    private static final String RL = "net/minecraft/util/ResourceLocation";
    private static final String DR = "net/minecraftforge/registries/DeferredRegister";
    private static final String RO = "net/minecraftforge/fml/RegistryObject";
    private static final String FR = "net/minecraftforge/registries/ForgeRegistries";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String ENTRY = "net/minecraftforge/registries/IForgeRegistryEntry";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String MOD = "Lnet/minecraftforge/fml/common/Mod;";
    private static final Set<String> MATERIALS = Set.of("STONE", "WOOD", "METAL", "DIRT", "GLASS");
    private static final Set<String> SOUNDS = Set.of("STONE", "WOOD", "METAL", "GLASS", "GRASS", "GRAVEL", "SAND", "SNOW", "WOOL");
    private static final Set<String> TABS = Set.of("BUILDING_BLOCKS", "DECORATIONS", "REDSTONE", "TRANSPORTATION",
            "MISC", "FOOD", "TOOLS", "COMBAT", "BREWING", "MATERIALS");
    private static final Set<String> TYPES = Set.of(ITEM, IP, BLOCK, BP, BLOCK_ITEM, MATERIAL, SOUND_TYPE, TAB,
            RARITY, SOUND, RL, DR, RO, FR, REGISTRY, ENTRY, CONTEXT, BUS,
            "java/lang/Object", "java/lang/String", "java/util/function/Supplier");
    private static final int MAX_CLASSES = 128;
    private static final int MAX_METHODS = 2048;
    private static final int MAX_INSTRUCTIONS = 100_000;
    private static final int MAX_REGISTRATIONS = 4096;

    public ContentPlan extract(Map<String, byte[]> classBytes, MappingInputs inputs, String modId, String modVersion)
            throws SubsetRejectionException {
        try {
            if (!inputs.complete() && inputs.scope() != MappingInputs.Scope.SIMPLE_CONTENT_CONTRACT)
                throw new Refusal("Complete source mapping/API inputs or the reviewed finite simple-content contract are required.");
            if (!modId.matches("[a-z][a-z0-9_]{1,63}")) throw new Refusal("Invalid Forge mod ID.");
            return new Session(classBytes, inputs, modId, modVersion).extract();
        } catch (Refusal refused) {
            throw new SubsetRejectionException(refused.getMessage());
        } catch (IllegalArgumentException | IndexOutOfBoundsException | java.util.NoSuchElementException malformed) {
            throw new SubsetRejectionException("Malformed or unsupported class structure: " + malformed.getClass().getSimpleName());
        }
    }

    private static final class Session {
        final LegacyApiNames names;
        final String modId;
        final String version;
        final Map<String, Unit> units = new TreeMap<>();
        final Map<String, Object> statics = new HashMap<>();
        final Set<String> initialized = new HashSet<>();
        final Set<String> initializing = new HashSet<>();
        final Set<String> visiting = new HashSet<>();
        final Set<String> visited = new HashSet<>();
        final List<Registrar> registrars = new ArrayList<>();
        final Map<String, Registration> registrations = new LinkedHashMap<>();
        int instructions;
        int methods;
        int steps;
        String location = "class inventory";

        Session(Map<String, byte[]> bytes, MappingInputs inputs, String modId, String version) {
            this.names = new LegacyApiNames(inputs);
            this.modId = modId;
            this.version = version;
            if (bytes.isEmpty() || bytes.size() > MAX_CLASSES) fail("Class count must be 1.." + MAX_CLASSES);
            for (var entry : bytes.entrySet()) {
                OperationControl.checkpoint();
                if (entry.getValue().length > 4 * 1024 * 1024) fail("Class byte limit exceeded");
                ClassReader reader = new ClassReader(entry.getValue());
                String path = entry.getKey();
                if (!(path.equals(reader.getClassName()) || path.equals(reader.getClassName() + ".class"))) {
                    fail("Class entry name differs from declaration: " + path);
                }
                if (reader.getClassName().startsWith("java/") || reader.getClassName().startsWith("net/minecraft")
                        || reader.getClassName().startsWith("dev/legacybridge/runtime/")) fail("Reserved class namespace");
                if (units.put(reader.getClassName(), new Unit(reader.getClassName(), reader)) != null) fail("Duplicate class");
            }
            for (Unit unit : units.values()) parse(unit);
        }

        ContentPlan extract() {
            List<Unit> entrypoints = units.values().stream().filter(unit -> unit.modId != null).toList();
            if (entrypoints.size() != 1 || !modId.equals(entrypoints.get(0).modId)) fail("Exactly one matching @Mod entrypoint is required");
            Unit entrypoint = entrypoints.get(0);
            initialize(entrypoint.name);
            Method constructor = entrypoint.methods.get("<init>()V");
            if (constructor == null || (constructor.access & Opcodes.ACC_PUBLIC) == 0) fail("A public zero-argument @Mod constructor is required");
            run(constructor, Phase.CONSTRUCTOR, new ThisValue(entrypoint.name));
            for (Registrar registrar : registrars) if (registrar.attachments != 1) fail("Every DeferredRegister must attach exactly once to the mod event bus");
            var items = new ArrayList<ContentPlan.ItemDefinition>();
            var blocks = new ArrayList<ContentPlan.BlockDefinition>();
            var sounds = new ArrayList<ContentPlan.SoundDefinition>();
            for (Registration registration : registrations.values()) resolve(registration);
            for (Registration registration : registrations.values()) {
                Object value = unwrap(registration.resolved);
                switch (registration.registrar.kind) {
                    case "ITEMS" -> {
                        if (!(value instanceof ItemValue item)) fail("Item supplier returned a non-item");
                        ItemValue item = (ItemValue) value;
                        items.add(new ContentPlan.ItemDefinition(registration.name, item.block, item.properties));
                    }
                    case "BLOCKS" -> {
                        if (!(value instanceof BlockValue block)) fail("Block supplier returned a non-block");
                        blocks.add(new ContentPlan.BlockDefinition(registration.name, ((BlockValue) value).properties));
                    }
                    case "SOUND_EVENTS" -> {
                        if (!(value instanceof SoundValue sound)) fail("Sound supplier returned a non-sound");
                        sounds.add(new ContentPlan.SoundDefinition(registration.name, ((SoundValue) value).id));
                    }
                    default -> fail("Unsupported registry");
                }
            }
            for (Unit unit : units.values()) for (Method method : unit.methods.values()) {
                if (!visited.contains(method.key())) {
                    // An unused, empty utility constructor has no observable effects and is safely discardable.
                    if (isEmptyConstructor(method)) run(method, Phase.UTILITY, new ThisValue(unit.name));
                    else fail("Unaccounted executable method outside registration graph: " + method.key());
                }
            }
            if (registrations.isEmpty()) fail("No supported content registrations found");
            items.sort(java.util.Comparator.comparing(ContentPlan.ItemDefinition::name));
            blocks.sort(java.util.Comparator.comparing(ContentPlan.BlockDefinition::name));
            sounds.sort(java.util.Comparator.comparing(ContentPlan.SoundDefinition::name));
            return new ContentPlan(modId, version, items, blocks, sounds);
        }

        void parse(Unit unit) {
            OperationControl.checkpoint();
            location = unit.name;
            unit.reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public void visit(int version, int access, String name, String signature, String superclass, String[] interfaces) {
                    if (version < Opcodes.V1_8 || version > Opcodes.V17 || (access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_RECORD | Opcodes.ACC_ANNOTATION)) != 0)
                        fail("Only ordinary Java 8..17 registration holder classes are supported");
                    if (!"java/lang/Object".equals(superclass) || interfaces.length != 0) fail("Custom subclasses and interfaces are outside the simple-content subset");
                    checkSignature(signature);
                }
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!MOD.equals(descriptor) || !visible || unit.modId != null) fail("Unsupported class annotation: " + descriptor);
                    unit.modId = "";
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override public void visit(String name, Object value) {
                            if (!name.equals("value") || !(value instanceof String) || !unit.modId.isEmpty()) fail("Malformed @Mod annotation");
                            unit.modId = (String) value;
                        }
                        @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) { fail("Nested @Mod annotation"); return null; }
                        @Override public AnnotationVisitor visitArray(String name) { fail("Array @Mod annotation"); return null; }
                        @Override public void visitEnum(String name, String descriptor, String value) { fail("Enum @Mod annotation"); }
                    };
                }
                @Override public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath path, String descriptor, boolean visible) {
                    fail("Class type annotations are unsupported"); return null;
                }
                @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if ((access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) != (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)
                            || (access & Opcodes.ACC_VOLATILE) != 0) fail("Only static final registration/constant fields are supported: " + name);
                    checkDescriptor(descriptor);
                    checkSignature(signature);
                    String normalized = names.descriptor(descriptor);
                    if (!Set.of("Ljava/lang/String;", "I", "L" + RO + ";", "L" + DR + ";").contains(normalized)) fail("Unsupported stored field type: " + descriptor);
                    if (value != null && !(value instanceof String || value instanceof Integer)) fail("Unsupported field constant");
                    if (unit.fields.put(name, normalized) != null) fail("Duplicate field name");
                    if (value != null) statics.put(unit.name + "." + name, value);
                    return new FieldVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) { fail("Field annotations are unsupported"); return null; }
                        @Override public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath path, String descriptor, boolean visible) { fail("Field type annotations are unsupported"); return null; }
                    };
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (++methods > MAX_METHODS) fail("Method budget exceeded");
                    if ((access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNCHRONIZED)) != 0) fail("Native/abstract/synchronized behavior is unsupported");
                    if (exceptions != null && exceptions.length != 0) fail("Declared exception behavior is unsupported");
                    checkDescriptor(descriptor);
                    checkSignature(signature);
                    if (Type.getArgumentTypes(descriptor).length != 0) fail("Only zero-argument constructors, initializers and supplier helpers are supported");
                    if (!(name.equals("<init>") || name.equals("<clinit>")) && (access & Opcodes.ACC_STATIC) == 0) fail("Instance behavior is unsupported: " + name);
                    if ((name.equals("<init>") || name.equals("<clinit>")) && !descriptor.equals("()V")) fail("Invalid initializer descriptor");
                    if (name.equals("<init>") && (access & Opcodes.ACC_STATIC) != 0
                            || name.equals("<clinit>") && (access & Opcodes.ACC_STATIC) == 0) fail("Invalid initializer staticness");
                    Method method = new Method(unit.name, name, descriptor, access);
                    if (unit.methods.put(name + descriptor, method) != null) fail("Duplicate method signature");
                    return visitor(method);
                }
                @Override public void visitOuterClass(String owner, String name, String descriptor) { fail("Local/anonymous classes are unsupported"); }
                @Override public void visitPermittedSubclass(String permittedSubclass) { fail("Sealed types are unsupported"); }
                @Override public org.objectweb.asm.ModuleVisitor visitModule(String name, int access, String version) { fail("Modules are unsupported"); return null; }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        MethodVisitor visitor(Method method) {
            return new MethodVisitor(Opcodes.ASM9) {
                void add(Instruction instruction) {
                    OperationControl.checkpoint();
                    if (++instructions > MAX_INSTRUCTIONS) fail("Instruction budget exceeded");
                    method.code.add(instruction);
                }
                @Override public void visitInsn(int opcode) { add(new Instruction(opcode, null, null, null, null)); }
                @Override public void visitIntInsn(int opcode, int operand) { add(new Instruction(opcode, null, null, null, operand)); }
                @Override public void visitVarInsn(int opcode, int variable) { add(new Instruction(opcode, null, null, null, variable)); }
                @Override public void visitTypeInsn(int opcode, String type) { checkType(type); add(new Instruction(opcode, names.owner(type), null, null, null)); }
                @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                    checkType(owner); checkDescriptor(descriptor); add(new Instruction(opcode, owner, name, descriptor, null));
                }
                @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                    checkType(owner); checkDescriptor(descriptor);
                    if (itf != (opcode == Opcodes.INVOKEINTERFACE)) fail("Unexpected interface invocation encoding");
                    add(new Instruction(opcode, owner, name, descriptor, null));
                }
                @Override public void visitLdcInsn(Object value) {
                    if (!(value instanceof String || value instanceof Integer || value instanceof Float)) fail("Only string/int/float bytecode constants are supported");
                    if (value instanceof String string && string.length() > 4096) fail("String constant limit exceeded");
                    add(new Instruction(Opcodes.LDC, null, null, null, value));
                }
                @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                    if (!name.equals("get") || !descriptor.equals("()Ljava/util/function/Supplier;")
                            || bootstrap.getTag() != Opcodes.H_INVOKESTATIC || bootstrap.isInterface()
                            || !bootstrap.getOwner().equals("java/lang/invoke/LambdaMetafactory") || !bootstrap.getName().equals("metafactory")
                            || !bootstrap.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
                            || arguments.length != 3 || !Type.getMethodType("()Ljava/lang/Object;").equals(arguments[0])
                            || !(arguments[1] instanceof Handle handle) || handle.getTag() != Opcodes.H_INVOKESTATIC || handle.isInterface()
                            || !units.containsKey(handle.getOwner()) || Type.getArgumentTypes(handle.getDesc()).length != 0
                            || !(arguments[2] instanceof Type instantiated) || !instantiated.equals(Type.getMethodType(handle.getDesc()))) {
                        fail("Only capture-free local static Supplier lambdas are supported");
                    }
                    Handle handle = (Handle) arguments[1];
                    checkDescriptor(handle.getDesc());
                    add(new Instruction(Opcodes.INVOKEDYNAMIC, handle.getOwner(), handle.getName(), handle.getDesc(), null));
                }
                @Override public void visitJumpInsn(int opcode, Label label) { fail("Branches and loops are outside the declarative subset: " + method.key()); }
                @Override public void visitIincInsn(int variable, int increment) { fail("Arithmetic/local mutation is unsupported"); }
                @Override public void visitTableSwitchInsn(int min, int max, Label defaultLabel, Label... labels) { fail("Switch behavior is unsupported"); }
                @Override public void visitLookupSwitchInsn(Label defaultLabel, int[] keys, Label[] labels) { fail("Switch behavior is unsupported"); }
                @Override public void visitMultiANewArrayInsn(String descriptor, int dimensions) { fail("Arrays are unsupported"); }
                @Override public void visitTryCatchBlock(Label start, Label end, Label handler, String type) { fail("Exception handlers are unsupported"); }
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) { fail("Method annotations are unsupported"); return null; }
                @Override public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath path, String descriptor, boolean visible) { fail("Method type annotations are unsupported"); return null; }
                @Override public AnnotationVisitor visitInsnAnnotation(int typeRef, org.objectweb.asm.TypePath path, String descriptor, boolean visible) { fail("Instruction annotations are unsupported"); return null; }
                @Override public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) { fail("Parameter annotations are unsupported"); return null; }
                @Override public AnnotationVisitor visitAnnotationDefault() { fail("Annotation defaults are unsupported"); return null; }
            };
        }

        void checkSignature(String signature) {
            if (signature == null) return;
            if (signature.length() > 4096 || signature.chars().filter(character -> character == '<').count() > 32) fail("Generic signature budget exceeded");
            SignatureVisitor visitor = new SignatureVisitor(Opcodes.ASM9) {
                @Override public void visitClassType(String name) { checkType(name); }
            };
            new SignatureReader(signature).accept(visitor);
        }

        void checkDescriptor(String descriptor) {
            if (descriptor.length() > 4096) fail("Descriptor length budget exceeded");
            Type type = Type.getType(descriptor);
            if (type.getSort() == Type.METHOD) {
                for (Type argument : type.getArgumentTypes()) checkDescriptor(argument.getDescriptor());
                checkDescriptor(type.getReturnType().getDescriptor());
            } else if (type.getSort() == Type.OBJECT) checkType(type.getInternalName());
            else if (type.getSort() == Type.ARRAY) fail("Array descriptors are outside the declarative subset");
            else if (!Set.of(Type.VOID, Type.INT, Type.FLOAT, Type.BOOLEAN).contains(type.getSort())) fail("Unsupported primitive descriptor");
        }

        void checkType(String type) {
            if (type.length() > 4096) fail("Type name length budget exceeded");
            if (!units.containsKey(type) && !TYPES.contains(names.owner(type))) fail("Unsupported API/type: " + type);
        }

        void initialize(String owner) {
            if (initialized.contains(owner)) return;
            if (!initializing.add(owner)) fail("Cyclic static initialization is unsupported: " + owner);
            Unit unit = units.get(owner);
            if (unit == null) fail("Missing local class: " + owner);
            Method initializer = unit.methods.get("<clinit>()V");
            if (initializer != null) run(initializer, Phase.INITIALIZER, null);
            initialized.add(owner);
            initializing.remove(owner);
        }

        Object run(Method method, Phase phase, Object self) {
            String previous = location;
            location = method.key();
            if (visiting.size() >= 64 || !visiting.add(method.key())) fail("Recursive supplier/helper invocation is unsupported");
            visited.add(method.key());
            Deque<Object> stack = new ArrayDeque<>();
            Map<Integer, Object> locals = new HashMap<>();
            if (self != null) locals.put(0, self);
            boolean returned = false;
            Object result = VoidValue.VALUE;
            for (Instruction instruction : method.code) {
                OperationControl.checkpoint();
                if (++steps > MAX_INSTRUCTIONS * 4) fail("Evaluation budget exceeded");
                if (returned) fail("Instructions after method return are unsupported");
                int opcode = instruction.opcode;
                switch (opcode) {
                    case Opcodes.NOP -> { }
                    case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
                            Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5 -> stack.push(opcode - Opcodes.ICONST_0);
                    case Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 -> stack.push((float) (opcode - Opcodes.FCONST_0));
                    case Opcodes.BIPUSH, Opcodes.SIPUSH, Opcodes.LDC -> stack.push(instruction.value);
                    case Opcodes.ALOAD, Opcodes.ILOAD, Opcodes.FLOAD -> {
                        Object local = locals.get((Integer) instruction.value);
                        if (local == null) fail("Read of uninitialized local");
                        checkLocalType(opcode, local);
                        stack.push(local);
                    }
                    case Opcodes.ASTORE, Opcodes.ISTORE, Opcodes.FSTORE -> {
                        if ((Integer) instruction.value > 255) fail("Local variable budget exceeded");
                        Object local = stack.pop();
                        checkLocalType(opcode, local);
                        locals.put((Integer) instruction.value, local);
                    }
                    case Opcodes.DUP -> stack.push(stack.element());
                    case Opcodes.POP -> {
                        Object dropped = stack.pop();
                        if (!(unwrap(dropped) instanceof ItemBuilder || unwrap(dropped) instanceof BlockBuilder)) fail("Discarded behavior/result is unsupported");
                    }
                    case Opcodes.NEW -> {
                        if (!Set.of(ITEM, IP, BLOCK, BLOCK_ITEM, SOUND, RL).contains(instruction.owner)) fail("Unsupported object construction: " + instruction.owner);
                        stack.push(new Cell(instruction.owner));
                    }
                    case Opcodes.CHECKCAST -> {
                        Object value = stack.element();
                        if (!instruction.owner.equals(BLOCK) || !(unwrap(value) instanceof BlockReference)) fail("Only the checked RegistryObject<Block>.get cast is supported");
                    }
                    case Opcodes.GETSTATIC -> stack.push(readField(instruction));
                    case Opcodes.PUTSTATIC -> {
                        if (phase != Phase.INITIALIZER || !instruction.owner.equals(method.owner)) fail("Static writes outside own class initializer are unsupported");
                        String descriptor = units.get(method.owner).fields.get(instruction.name);
                        if (descriptor == null || !descriptor.equals(names.descriptor(instruction.descriptor))) fail("Undeclared static field write");
                        Object value = stack.pop();
                        boolean valid = descriptor.equals("L" + DR + ";") && value instanceof Registrar
                                || descriptor.equals("L" + RO + ";") && value instanceof Registration
                                || descriptor.equals("Ljava/lang/String;") && value instanceof String
                                || descriptor.equals("I") && value instanceof Integer;
                        if (!valid || statics.putIfAbsent(instruction.owner + "." + instruction.name, value) != null) fail("Incompatible or duplicate static assignment");
                    }
                    case Opcodes.INVOKEDYNAMIC -> {
                        if (phase != Phase.INITIALIZER) fail("Supplier creation is only supported in class initializers");
                        Method supplier = units.get(instruction.owner).methods.get(instruction.name + instruction.descriptor);
                        if (supplier == null || (supplier.access & Opcodes.ACC_STATIC) == 0) fail("Missing static supplier implementation");
                        stack.push(new SupplierValue(supplier));
                    }
                    case Opcodes.INVOKESTATIC, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE, Opcodes.INVOKESPECIAL -> invoke(instruction, stack, phase);
                    case Opcodes.RETURN -> {
                        if (!method.descriptor.equals("()V") || !stack.isEmpty()) fail("Invalid void return stack");
                        if (self instanceof ThisValue object && !object.initialized) fail("Constructor did not initialize Object superclass");
                        returned = true;
                    }
                    case Opcodes.ARETURN -> {
                        result = stack.pop();
                        if (Type.getReturnType(method.descriptor).getSort() != Type.OBJECT || !stack.isEmpty()) fail("Invalid reference return");
                        checkReturn(result, names.owner(Type.getReturnType(method.descriptor).getInternalName()));
                        returned = true;
                    }
                    default -> fail("Opcode " + opcode + " is outside the declarative subset");
                }
                if (stack.size() > 256) fail("Operand stack budget exceeded");
            }
            if (!returned) fail("Method has no supported return");
            visiting.remove(method.key());
            location = previous;
            return result;
        }

        Object readField(Instruction instruction) {
            if (units.containsKey(instruction.owner)) {
                if (!instruction.owner.equals(currentOwner()) || !initializing.contains(instruction.owner)) initialize(instruction.owner);
                if (!names.descriptor(instruction.descriptor).equals(units.get(instruction.owner).fields.get(instruction.name))) fail("Unknown local static field");
                Object value = statics.get(instruction.owner + "." + instruction.name);
                if (value == null) fail("Static field read before initialization");
                return value;
            }
            var member = names.member(instruction.owner, instruction.name, instruction.descriptor, true, true);
            String token = member.name();
            if (member.owner().equals(FR) && member.descriptor().equals("L" + REGISTRY + ";")
                    && Set.of("ITEMS", "BLOCKS", "SOUND_EVENTS").contains(token)) return new Token(FR, token);
            if (member.owner().equals(MATERIAL) && MATERIALS.contains(token)) return new Token(MATERIAL, token);
            if (member.owner().equals(SOUND_TYPE) && SOUNDS.contains(token)) return new Token(SOUND_TYPE, token);
            if (member.owner().equals(RARITY) && Set.of("COMMON", "UNCOMMON", "RARE", "EPIC").contains(token)) return new Token(RARITY, token);
            if (member.owner().equals(TAB) && token.startsWith("TAB_") && TABS.contains(token.substring(4))) return new Token(TAB, token.substring(4));
            fail("Static API field is unsupported: " + member);
            return null;
        }

        String currentOwner() { return location.substring(0, location.indexOf('.')); }

        void invoke(Instruction instruction, Deque<Object> stack, Phase phase) {
            Type[] argumentTypes = Type.getArgumentTypes(instruction.descriptor);
            Object[] args = new Object[argumentTypes.length];
            for (int i = args.length - 1; i >= 0; i--) args[i] = stack.pop();
            boolean isStatic = instruction.opcode == Opcodes.INVOKESTATIC;
            Object receiver = isStatic ? null : stack.pop();
            Object result;
            if (instruction.owner.equals("java/lang/Object") && instruction.name.equals("<init>")
                    && instruction.descriptor.equals("()V") && instruction.opcode == Opcodes.INVOKESPECIAL
                    && receiver instanceof ThisValue object && (phase == Phase.CONSTRUCTOR || phase == Phase.UTILITY)) {
                if (object.initialized) fail("Object superclass initialized twice");
                object.initialized = true;
                result = VoidValue.VALUE;
            } else if (units.containsKey(instruction.owner)) {
                if (!isStatic || phase != Phase.SUPPLIER) fail("Only static supplier helper calls are supported");
                initialize(instruction.owner);
                Method helper = units.get(instruction.owner).methods.get(instruction.name + instruction.descriptor);
                if (helper == null || (helper.access & Opcodes.ACC_STATIC) == 0) fail("Missing static helper");
                result = run(helper, phase, null);
            } else {
                var member = names.member(instruction.owner, instruction.name, instruction.descriptor, false, isStatic);
                if (member.name().equals("<init>") && instruction.opcode != Opcodes.INVOKESPECIAL) fail("Invalid constructor invocation");
                if (!member.name().equals("<init>") && instruction.opcode == Opcodes.INVOKESPECIAL) fail("Special non-constructor invocation is unsupported");
                result = api(member, receiver, args, phase);
            }
            if (Type.getReturnType(instruction.descriptor).getSort() != Type.VOID) {
                if (result == VoidValue.VALUE) fail("Missing invocation result");
                stack.push(result);
            } else if (result != VoidValue.VALUE) fail("Unexpected invocation result");
        }

        Object api(LegacyApiNames.Member member, Object receiver, Object[] args, Phase phase) {
            String owner = member.owner();
            String name = member.name();
            String descriptor = member.descriptor();
            if (owner.equals(DR) && name.equals("create")
                    && descriptor.equals("(L" + REGISTRY + ";Ljava/lang/String;)L" + DR + ";") && phase == Phase.INITIALIZER) {
                String kind = token(args[0], FR);
                if (!modId.equals(string(args[1]))) fail("DeferredRegister namespace differs from modId");
                Registrar registrar = new Registrar(kind);
                registrars.add(registrar);
                return registrar;
            }
            if (owner.equals(DR) && name.equals("register") && receiver instanceof Registrar registrar) {
                if (descriptor.equals("(Ljava/lang/String;Ljava/util/function/Supplier;)L" + RO + ";") && phase == Phase.INITIALIZER) {
                    String path = path(string(args[0]));
                    if (!(args[1] instanceof SupplierValue)) fail("Registry supplier must be a proven lambda");
                    Registration registration = new Registration(registrar, path, ((SupplierValue) args[1]).method);
                    if (registrations.size() >= MAX_REGISTRATIONS || registrations.putIfAbsent(registrar.kind + ":" + path, registration) != null) fail("Duplicate registry name or registration budget exceeded");
                    return registration;
                }
                if (descriptor.equals("(L" + BUS + ";)V") && phase == Phase.CONSTRUCTOR && args[0] == BusValue.VALUE) {
                    registrar.attachments++;
                    return VoidValue.VALUE;
                }
            }
            if (owner.equals(CONTEXT) && phase == Phase.CONSTRUCTOR) {
                if (name.equals("get") && descriptor.equals("()L" + CONTEXT + ";")) return ContextValue.VALUE;
                if (name.equals("getModEventBus") && descriptor.equals("()L" + BUS + ";") && receiver == ContextValue.VALUE) return BusValue.VALUE;
            }
            if (phase != Phase.SUPPLIER) fail("API call outside allowed registration lifecycle: " + member);
            if (owner.equals(RO) && name.equals("get") && (descriptor.equals("()L" + ENTRY + ";") || descriptor.equals("()Ljava/lang/Object;"))
                    && receiver instanceof Registration registration && registration.registrar.kind.equals("BLOCKS")) {
                if (!(unwrap(resolve(registration)) instanceof BlockValue)) fail("Block RegistryObject resolved incorrectly");
                return new BlockReference(modId + ":" + registration.name);
            }
            if (name.equals("<init>")) {
                if (!(receiver instanceof Cell cell) || !cell.type.equals(owner) || cell.value != null) fail("Invalid new-object initialization");
                Cell cell = (Cell) receiver;
                if (owner.equals(IP) && descriptor.equals("()V")) cell.value = new ItemBuilder();
                else if (owner.equals(ITEM) && descriptor.equals("(L" + IP + ";)V")) cell.value = new ItemValue(null, itemBuilder(args[0]).snapshot());
                else if (owner.equals(BLOCK) && descriptor.equals("(L" + BP + ";)V")) cell.value = new BlockValue(blockBuilder(args[0]).snapshot());
                else if (owner.equals(BLOCK_ITEM) && descriptor.equals("(L" + BLOCK + ";L" + IP + ";)V") && unwrap(args[0]) instanceof BlockReference block)
                    cell.value = new ItemValue(block.id, itemBuilder(args[1]).snapshot());
                else if (owner.equals(RL) && descriptor.equals("(Ljava/lang/String;Ljava/lang/String;)V")) cell.value = new ResourceValue(resource(string(args[0]) + ":" + string(args[1])));
                else if (owner.equals(RL) && descriptor.equals("(Ljava/lang/String;)V")) cell.value = new ResourceValue(resource(string(args[0])));
                else if (owner.equals(SOUND) && descriptor.equals("(L" + RL + ";)V") && unwrap(args[0]) instanceof ResourceValue resource)
                    cell.value = new SoundValue(resource.id);
                else fail("Unsupported constructor: " + member);
                return VoidValue.VALUE;
            }
            if (owner.equals(IP)) {
                ItemBuilder builder = itemBuilder(receiver);
                if (name.equals("stacksTo") && descriptor.equals("(I)L" + IP + ";")) {
                    if (builder.durability > 0) fail("Legacy stacksTo after durability would throw");
                    builder.stackSize = boundedInt(args[0], 1, 64);
                } else if ((name.equals("durability") || name.equals("defaultDurability")) && descriptor.equals("(I)L" + IP + ";")) {
                    int durability = boundedInt(args[0], 1, 1_000_000);
                    if (name.equals("durability") || builder.durability == 0) { builder.durability = durability; builder.stackSize = 1; }
                } else if (name.equals("fireResistant") && descriptor.equals("()L" + IP + ";")) builder.fireResistant = true;
                else if (name.equals("rarity") && descriptor.equals("(L" + RARITY + ";)L" + IP + ";")) builder.rarity = token(args[0], RARITY);
                else if (name.equals("tab") && descriptor.equals("(L" + TAB + ";)L" + IP + ";")) builder.tab = token(args[0], TAB);
                else fail("Unsupported item property: " + member);
                return receiver;
            }
            if (owner.equals(BP)) {
                if (name.equals("of") && descriptor.equals("(L" + MATERIAL + ";)L" + BP + ";")) return new BlockBuilder(token(args[0], MATERIAL));
                BlockBuilder builder = blockBuilder(receiver);
                if (name.equals("strength") && descriptor.equals("(FF)L" + BP + ";")) {
                    builder.hardness = boundedFloat(args[0]); builder.resistance = boundedFloat(args[1]);
                } else if (name.equals("strength") && descriptor.equals("(F)L" + BP + ";")) {
                    builder.hardness = boundedFloat(args[0]); builder.resistance = builder.hardness;
                } else if (name.equals("sound") && descriptor.equals("(L" + SOUND_TYPE + ";)L" + BP + ";")) builder.sound = token(args[0], SOUND_TYPE);
                else if (name.equals("requiresCorrectToolForDrops") && descriptor.equals("()L" + BP + ";")) builder.requiresTool = true;
                else if (name.equals("noOcclusion") && descriptor.equals("()L" + BP + ";")) builder.noOcclusion = true;
                else fail("Unsupported block property: " + member);
                return receiver;
            }
            fail("Unsupported API call: " + member);
            return null;
        }

        Object resolve(Registration registration) {
            if (registration.resolved != null) return registration.resolved;
            if (registration.resolving) fail("Cyclic registration supplier dependencies");
            registration.resolving = true;
            registration.resolved = run(registration.supplier, Phase.SUPPLIER, null);
            registration.resolving = false;
            return registration.resolved;
        }

        boolean isEmptyConstructor(Method method) {
            if (!method.name.equals("<init>") || !method.descriptor.equals("()V") || method.code.size() != 3) return false;
            Instruction first = method.code.get(0), second = method.code.get(1), third = method.code.get(2);
            return first.opcode == Opcodes.ALOAD && Integer.valueOf(0).equals(first.value)
                    && second.opcode == Opcodes.INVOKESPECIAL && "java/lang/Object".equals(second.owner)
                    && "<init>".equals(second.name) && "()V".equals(second.descriptor) && third.opcode == Opcodes.RETURN;
        }

        Object unwrap(Object value) {
            if (value instanceof Cell cell) {
                if (cell.value == null) fail("Uninitialized object used");
                return cell.value;
            }
            return value;
        }
        void checkLocalType(int opcode, Object value) {
            boolean integer = opcode == Opcodes.ILOAD || opcode == Opcodes.ISTORE;
            boolean floating = opcode == Opcodes.FLOAD || opcode == Opcodes.FSTORE;
            if (integer && !(value instanceof Integer) || floating && !(value instanceof Float)
                    || !integer && !floating && (value instanceof Integer || value instanceof Float)) fail("Local opcode has incompatible value type");
        }
        void checkReturn(Object value, String declaredType) {
            Object unwrapped = unwrap(value);
            boolean valid = unwrapped instanceof ItemValue && (declaredType.equals(ITEM) || declaredType.equals(BLOCK_ITEM))
                    || unwrapped instanceof BlockValue && declaredType.equals(BLOCK)
                    || unwrapped instanceof SoundValue && declaredType.equals(SOUND)
                    || unwrapped instanceof ItemBuilder && declaredType.equals(IP)
                    || unwrapped instanceof BlockBuilder && declaredType.equals(BP)
                    || unwrapped instanceof ResourceValue && declaredType.equals(RL)
                    || unwrapped instanceof String && declaredType.equals("java/lang/String");
            if (!valid) fail("Supplier/helper return does not match its declared type");
            if (declaredType.equals(BLOCK_ITEM) && unwrapped instanceof ItemValue item && item.block == null) fail("Plain Item cannot satisfy BlockItem return type");
        }
        ItemBuilder itemBuilder(Object value) { Object object = unwrap(value); if (!(object instanceof ItemBuilder)) fail("Expected Item.Properties"); return (ItemBuilder) object; }
        BlockBuilder blockBuilder(Object value) { Object object = unwrap(value); if (!(object instanceof BlockBuilder)) fail("Expected Block.Properties"); return (BlockBuilder) object; }
        String token(Object value, String owner) { if (!(value instanceof Token token) || !token.owner.equals(owner)) fail("Wrong enum/API constant type"); return ((Token) value).name; }
        String string(Object value) { if (!(value instanceof String)) fail("Expected constant string"); return (String) value; }
        int boundedInt(Object value, int min, int max) { if (!(value instanceof Integer number) || number < min || number > max) fail("Integer property is outside supported bounds"); return (Integer) value; }
        float boundedFloat(Object value) { if (!(value instanceof Float number) || !Float.isFinite(number) || number < 0 || number > 1_000_000) fail("Float property is outside supported bounds"); return (Float) value; }
        String path(String value) { if (value.length() > 256 || !value.matches("[a-z0-9_./-]+") || value.contains("..") || value.startsWith("/")) fail("Invalid registry/resource path"); return value; }
        String resource(String value) {
            if (!value.startsWith(modId + ":")) fail("Simple sound resource locations must retain the mod namespace");
            path(value.substring(modId.length() + 1)); return value;
        }
        void fail(String message) { throw new Refusal(location + ": " + message); }
    }

    private enum Phase { INITIALIZER, CONSTRUCTOR, SUPPLIER, UTILITY }
    private enum VoidValue { VALUE }
    private enum BusValue { VALUE }
    private enum ContextValue { VALUE }
    private static final class ThisValue { final String owner; boolean initialized; ThisValue(String owner) { this.owner = owner; } }
    private record Token(String owner, String name) { }
    private record SupplierValue(Method method) { }
    private record ResourceValue(String id) { }
    private record SoundValue(String id) { }
    private record BlockReference(String id) { }
    private record ItemValue(String block, ContentPlan.ItemProperties properties) { }
    private record BlockValue(ContentPlan.BlockProperties properties) { }
    private record Instruction(int opcode, String owner, String name, String descriptor, Object value) { }
    private static final class Cell { final String type; Object value; Cell(String type) { this.type = type; } }
    private static final class Registrar { final String kind; int attachments; Registrar(String kind) { this.kind = kind; } }
    private static final class Registration {
        final Registrar registrar; final String name; final Method supplier; boolean resolving; Object resolved;
        Registration(Registrar registrar, String name, Method supplier) { this.registrar = registrar; this.name = name; this.supplier = supplier; }
    }
    private static final class ItemBuilder {
        int stackSize = 64; int durability; boolean fireResistant; String rarity = "COMMON"; String tab;
        ContentPlan.ItemProperties snapshot() { return new ContentPlan.ItemProperties(stackSize, durability, fireResistant, rarity, tab); }
    }
    private static final class BlockBuilder {
        final String material; float hardness; float resistance; String sound = "STONE"; boolean requiresTool; boolean noOcclusion;
        BlockBuilder(String material) { this.material = material; }
        ContentPlan.BlockProperties snapshot() { return new ContentPlan.BlockProperties(material, hardness, resistance, sound, requiresTool, noOcclusion); }
    }
    private static final class Unit {
        final String name; final ClassReader reader; String modId;
        final Map<String, String> fields = new HashMap<>(); final Map<String, Method> methods = new LinkedHashMap<>();
        Unit(String name, ClassReader reader) { this.name = name; this.reader = reader; }
    }
    private static final class Method {
        final String owner; final String name; final String descriptor; final int access; final List<Instruction> code = new ArrayList<>();
        Method(String owner, String name, String descriptor, int access) { this.owner = owner; this.name = name; this.descriptor = descriptor; this.access = access; }
        String key() { return owner + "." + name + descriptor; }
    }
}
